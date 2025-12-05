package hu.porkolab.chaosSymphony.streams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class TopologyConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String STATUS_COUNT_STORE_1H = "status-count-store-1h";
    public static final String STATUS_COUNT_STORE_6H = "status-count-store-6h";

    @Bean
    public Topology topology() {
        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> paymentResults = builder.stream("payment.result", Consumed.with(Serdes.String(), Serdes.String()));

        KGroupedStream<String, String> groupedByStatus = paymentResults
                .mapValues(TopologyConfig::statusFromEnvelope)
                .groupBy((key, status) -> status, Grouped.with(Serdes.String(), Serdes.String()));

        // Non-windowed count for existing tests
        groupedByStatus
                .count()
                .toStream()
                .to("analytics.payment.status.count", Produced.with(Serdes.String(), Serdes.Long()));

        // 1-hour windowed count
        groupedByStatus
                .windowedBy(TimeWindows.of(Duration.ofHours(1)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(STATUS_COUNT_STORE_1H)
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long()));

        // 6-hour windowed count
        groupedByStatus
                .windowedBy(TimeWindows.of(Duration.ofHours(6)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(STATUS_COUNT_STORE_6H)
                        .withKeySerde(Serdes.String()).withValueSerde(Serdes.Long()));

        return builder.build();
    }

    private static String statusFromEnvelope(String json) {
        try {
            // This logic assumes the original envelope structure from the spec
            JsonNode payload = MAPPER.readTree(json).path("payload");
            if (payload.isTextual()) {
                payload = MAPPER.readTree(payload.asText());
            }
            return payload.path("status").asText("UNKNOWN");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
