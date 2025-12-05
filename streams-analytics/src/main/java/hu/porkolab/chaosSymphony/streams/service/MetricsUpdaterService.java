package hu.porkolab.chaosSymphony.streams.service;

import hu.porkolab.chaosSymphony.streams.TopologyConfig;
import hu.porkolab.chaosSymphony.streams.config.MetricsConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MetricsUpdaterService {

    private final KafkaStreams kafkaStreams;
    private final MetricsConfig metricsConfig;

    @Scheduled(fixedRate = 10000) 
    public void updateMetrics() {
        if (kafkaStreams == null || kafkaStreams.state() != KafkaStreams.State.RUNNING) {
            return;
        }

        try {
            ReadOnlyWindowStore<String, Long> store1h = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(TopologyConfig.STATUS_COUNT_STORE_1H, QueryableStoreTypes.windowStore()));

            ReadOnlyWindowStore<String, Long> store6h = kafkaStreams.store(
                    StoreQueryParameters.fromNameAndType(TopologyConfig.STATUS_COUNT_STORE_6H, QueryableStoreTypes.windowStore()));

            long now = Instant.now().toEpochMilli();

            
            long successCount1h = fetchCount(store1h, "CHARGED", now);
            long failureCount1h = fetchCount(store1h, "CHARGE_FAILED", now);
            long total1h = successCount1h + failureCount1h;
            long burnRate1h = (total1h == 0) ? 0 : (failureCount1h * 100) / total1h;
            metricsConfig.updateSloBurnRate1h(burnRate1h);

            
            long successCount6h = fetchCount(store6h, "CHARGED", now);
            long failureCount6h = fetchCount(store6h, "CHARGE_FAILED", now);
            long total6h = successCount6h + failureCount6h;
            long burnRate6h = (total6h == 0) ? 0 : (failureCount6h * 100) / total6h;
            metricsConfig.updateSloBurnRate6h(burnRate6h);

        } catch (Exception e) {
            
        }
    }

    private long fetchCount(ReadOnlyWindowStore<String, Long> store, String key, long time) {
        try (var iterator = store.fetch(key, Instant.ofEpochMilli(time - 3600 * 1000), Instant.ofEpochMilli(time))) {
            if (iterator.hasNext()) {
                return iterator.next().value;
            }
        }
        return 0;
    }
}
