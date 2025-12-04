package hu.porkolab.chaosSymphony.dlq.api;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/dlq")
public class DlqController {

    private static final Logger log = LoggerFactory.getLogger(DlqController.class);
	private final KafkaTemplate<String, String> template;
	private final String bootstrap;

	public DlqController(KafkaTemplate<String, String> template,
			ProducerFactory<String, String> pf) {
		this.template = template;
		Object bs = pf.getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

		
		String raw = (bs == null) ? "" : bs.toString();
		
		if (raw.startsWith("[") && raw.endsWith("]")) {
			raw = raw.substring(1, raw.length() - 1);
		}
		
		raw = raw.replaceAll("\\s+", "");
		
		this.bootstrap = raw.isBlank() ? "127.0.0.1:29092" : raw;
	}

	

	private Properties consumerProps(String groupId) {
		Properties p = new Properties();
		p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
		return p;
	}

	private void seekBeginning(KafkaConsumer<String, String> c, String topic) {
		c.subscribe(Collections.singletonList(topic));
		c.poll(Duration.ofMillis(0)); 
		c.seekToBeginning(c.assignment()); 
	}

	

	@GetMapping("/topics")
	public ResponseEntity<List<String>> listDlqTopics() {
		Properties adminProps = new Properties();
		adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		try (var admin = AdminClient.create(adminProps)) {
			var names = admin.listTopics(new ListTopicsOptions().listInternal(false)).names().get();
			
			var dltTopics = names.stream()
					.filter(n -> n.endsWith("-dlt") || n.endsWith("-DLT") || n.endsWith(".DLT") || n.endsWith(".dlt"))
					.sorted()
					.collect(Collectors.toList());
			log.info("Found {} DLT topics: {}", dltTopics.size(), dltTopics);
			log.info("All Kafka topics: {}", names);
			return ResponseEntity.ok(dltTopics);
		} catch (Exception e) {
			log.error("Failed to list DLT topics from Kafka", e);
			return ResponseEntity.ok(java.util.Collections.emptyList());
		}
	}

	@PostMapping("/{topic}/replay")
	public ResponseEntity<String> replay(@PathVariable("topic") String dltTopic) throws Exception {
		String original;
		if (dltTopic.endsWith("-dlt") || dltTopic.endsWith("-DLT")) {
			original = dltTopic.substring(0, dltTopic.length() - 4);
		} else if (dltTopic.endsWith(".DLT") || dltTopic.endsWith(".dlt")) {
			original = dltTopic.substring(0, dltTopic.length() - 4);
		} else {
			return ResponseEntity.badRequest().body("Not a DLT topic");
		}

		
		Properties adminProps = new Properties();
		adminProps.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		var tps = new ArrayList<org.apache.kafka.common.TopicPartition>();
		try (var admin = org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
			var info = admin.describeTopics(Collections.singletonList(dltTopic)).all().get().get(dltTopic);
			if (info == null) {
				return ResponseEntity.ok("Replayed 0 records from " + dltTopic + " to " + original);
			}
			info.partitions().forEach(p -> tps.add(new org.apache.kafka.common.TopicPartition(dltTopic, p.partition())));
		}

		long replayed = 0;
		String gid = "dlq-replay-" + java.util.UUID.randomUUID();

		try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(consumerProps(gid))) {
			
			consumer.assign(tps);
			consumer.seekToBeginning(tps);

			while (true) {
				var recs = consumer.poll(java.time.Duration.ofMillis(600));
				if (recs.isEmpty())
					break;

				for (var rec : recs) {
					
					var pr = new org.apache.kafka.clients.producer.ProducerRecord<>(
							original, null, rec.timestamp(), rec.key(), rec.value(), rec.headers());
					try {
						template.send(pr).get();
						replayed++;
					} catch (Exception e) {
						log.error("Failed to replay message from DLT {} to topic {}. Record: {}",
                                dltTopic, original, rec, e);
					}
				}
			}
		}

		return ResponseEntity.ok("Replayed " + replayed + " records from " + dltTopic + " to " + original);
	}

	@DeleteMapping("/{topic}")
	public ResponseEntity<String> purge(@PathVariable("topic") String topic) {
		long purged = 0;
		String gid = "dlq-purge-" + UUID.randomUUID();
		try (var c = new KafkaConsumer<String, String>(consumerProps(gid))) {
			seekBeginning(c, topic);
			while (true) {
				var recs = c.poll(Duration.ofSeconds(1));
				if (recs.isEmpty())
					break;
				purged += recs.count();
			}
		}
		return ResponseEntity.ok("Purged " + purged + " records from " + topic);
	}

	@GetMapping("/{topic}/count")
	public ResponseEntity<Long> count(@PathVariable String topic) {
		Properties adminProps = new Properties();
		adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		try (var admin = AdminClient.create(adminProps)) {
			var desc = admin.describeTopics(Collections.singletonList(topic)).all().get();
			var info = desc.get(topic);
			if (info == null) {
				log.warn("Topic {} not found", topic);
				return ResponseEntity.ok(0L);
			}

			var tps = info.partitions().stream()
					.map(p -> new org.apache.kafka.common.TopicPartition(topic, p.partition()))
					.toList();

			long n = 0;
			try (var c = new KafkaConsumer<String, String>(consumerProps("dlq-count-" + UUID.randomUUID()))) {
				c.assign(tps);
				c.seekToBeginning(tps);
				while (true) {
					var recs = c.poll(Duration.ofMillis(400));
					if (recs.isEmpty())
						break;
					n += recs.count();
				}
			}
			log.debug("Topic {} has {} messages", topic, n);
			return ResponseEntity.ok(n);
		} catch (Exception e) {
			log.error("Failed to count messages in topic {}", topic, e);
			return ResponseEntity.ok(0L);
		}
	}

	@GetMapping("/{topic}/peek")
	public ResponseEntity<List<String>> peek(@PathVariable String topic, @RequestParam(defaultValue = "10") int n) {
		Properties adminProps = new Properties();
		adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
		try (var admin = AdminClient.create(adminProps)) {
			var desc = admin.describeTopics(Collections.singletonList(topic)).all().get();
			var info = desc.get(topic);
			if (info == null) {
				log.warn("Topic {} not found for peek", topic);
				return ResponseEntity.ok(List.of());
			}

			var tps = info.partitions().stream()
					.map(p -> new org.apache.kafka.common.TopicPartition(topic, p.partition()))
					.toList();

			var out = new ArrayList<String>(n);
			try (var c = new KafkaConsumer<String, String>(consumerProps("dlq-peek-" + UUID.randomUUID()))) {
				c.assign(tps);
				c.seekToBeginning(tps);
				while (out.size() < n) {
					var recs = c.poll(Duration.ofMillis(400));
					if (recs.isEmpty())
						break;
					recs.forEach(r -> {
						if (out.size() < n)
							out.add(String.valueOf(r.value()));
					});
				}
			}
			log.debug("Peeked {} messages from topic {}", out.size(), topic);
			return ResponseEntity.ok(out);
		} catch (Exception e) {
			log.error("Failed to peek messages from topic {}", topic, e);
			return ResponseEntity.ok(List.of());
		}
	}

	@PostMapping("/{topic}/replay-range")
	public ResponseEntity<String> replayRange(@PathVariable String topic,
			@RequestParam long fromOffset, @RequestParam long toOffset) throws Exception {

		String original;
		if (topic.endsWith("-dlt") || topic.endsWith("-DLT")) {
			original = topic.substring(0, topic.length() - 4);
		} else if (topic.endsWith(".DLT") || topic.endsWith(".dlt")) {
			original = topic.substring(0, topic.length() - 4);
		} else {
			return ResponseEntity.badRequest().body("Not a DLT topic");
		}
		var tp = new org.apache.kafka.common.TopicPartition(topic, 0); 

		long replayed = 0;
		try (var c = new KafkaConsumer<String, String>(consumerProps("dlq-range-" + UUID.randomUUID()))) {
			c.assign(java.util.List.of(tp));
			c.seek(tp, fromOffset);
			while (true) {
				var recs = c.poll(java.time.Duration.ofMillis(600));
				if (recs.isEmpty())
					break;
				for (var r : recs) {
					if (r.offset() > toOffset)
						break;
					var pr = new org.apache.kafka.clients.producer.ProducerRecord<>(original, null, r.timestamp(), r.key(),
							r.value(), r.headers());
					template.send(pr).get();
					replayed++;
				}
				if (recs.records(tp).stream().anyMatch(x -> x.offset() > toOffset))
					break;
			}
		}
		return ResponseEntity.ok("Replayed " + replayed + " records from " + topic + " to " + original);
	}
}
