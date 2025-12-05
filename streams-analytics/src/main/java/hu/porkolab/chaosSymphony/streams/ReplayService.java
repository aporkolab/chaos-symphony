package hu.porkolab.chaosSymphony.streams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplayService {

    private final AdminClient adminClient;

    public void replayFrom(String consumerGroupId, Duration duration) throws ExecutionException, InterruptedException {
        long replayFromTimestamp = Instant.now().minus(duration).toEpochMilli();
        log.info("Replaying consumer group '{}' from timestamp {}", consumerGroupId, replayFromTimestamp);

        // 1. Get all partitions for the topics this consumer group is subscribed to
        Map<TopicPartition, OffsetAndMetadata> currentOffsets = adminClient
                .listConsumerGroupOffsets(consumerGroupId)
                .partitionsToOffsetAndMetadata()
                .get();

        if (currentOffsets.isEmpty()) {
            log.warn("Consumer group '{}' has no offsets or does not exist.", consumerGroupId);
            return;
        }

        // 2. Find the offsets for each partition at the desired timestamp
        Map<TopicPartition, OffsetSpec> offsetsForTimes = currentOffsets.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.forTimestamp(replayFromTimestamp)));

        Map<TopicPartition, OffsetAndMetadata> newOffsets = adminClient
                .listOffsets(offsetsForTimes)
                .all()
                .get()
                .entrySet().stream()
                .filter(entry -> entry.getValue().offset() != -1) // Filter out partitions with no offset at that time
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new OffsetAndMetadata(entry.getValue().offset())
                ));

        if (newOffsets.isEmpty()) {
            log.warn("No offsets found for any partitions at the requested replay time for consumer group '{}'.", consumerGroupId);
            return;
        }

        // 3. Alter the consumer group offsets
        adminClient.alterConsumerGroupOffsets(consumerGroupId, newOffsets).all().get();
        log.info("Successfully reset offsets for consumer group '{}' to timestamp {}. Partitions affected: {}",
                consumerGroupId, replayFromTimestamp, newOffsets.keySet());
    }
}
