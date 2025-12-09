package hu.porkolab.chaosSymphony.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Properties;

@SpringBootApplication
@EnableScheduling

public class StreamsAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamsAnalyticsApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    public KafkaStreams kafkaStreams(Topology topology) {
        Properties p = new Properties();
        p.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-analytics-v2");
        p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:29092");
        p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes$StringSerde");
        p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.Serdes$StringSerde");
        p.put("auto.offset.reset", "earliest");
        KafkaStreams streams = new KafkaStreams(topology, p);
        streams.start();
        return streams;
    }
}
