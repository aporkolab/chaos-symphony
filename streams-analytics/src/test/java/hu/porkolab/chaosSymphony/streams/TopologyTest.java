package hu.porkolab.chaosSymphony.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyConfigTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, Long> outputTopic;

    @BeforeEach
    void setup() {
        TopologyConfig config = new TopologyConfig();
        Topology topology = config.topology();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(
            "payment.result",
            Serdes.String().serializer(),
            Serdes.String().serializer()
        );

        outputTopic = testDriver.createOutputTopic(
            "analytics.payment.status.count",
            Serdes.String().deserializer(),
            Serdes.Long().deserializer()
        );
    }

    @AfterEach
    void teardown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldExtractStatusFromNestedPayload() {
        String json = "{\"payload\":\"{\\\"status\\\":\\\"CHARGED\\\"}\"}";
        inputTopic.pipeInput("key1", json);

        var records = outputTopic.readKeyValuesToList();
        assertThat(records).anyMatch(r -> r.key.equals("CHARGED") && r.value == 1L);
    }

    @Test
    void shouldExtractStatusFromDirectPayload() {
        String json = "{\"payload\":{\"status\":\"CHARGE_FAILED\"}}";
        inputTopic.pipeInput("key2", json);

        var records = outputTopic.readKeyValuesToList();
        assertThat(records).anyMatch(r -> r.key.equals("CHARGE_FAILED") && r.value == 1L);
    }

    @Test
    void shouldReturnUnknownForInvalidJson() {
        inputTopic.pipeInput("key3", "not-valid-json");

        var records = outputTopic.readKeyValuesToList();
        assertThat(records).anyMatch(r -> r.key.equals("UNKNOWN") && r.value == 1L);
    }

    @Test
    void shouldReturnUnknownForMissingStatus() {
        String json = "{\"payload\":{\"other\":\"field\"}}";
        inputTopic.pipeInput("key4", json);

        var records = outputTopic.readKeyValuesToList();
        assertThat(records).anyMatch(r -> r.key.equals("UNKNOWN") && r.value == 1L);
    }

    @Test
    void shouldCountMultipleMessagesPerStatus() {
        String charged1 = "{\"payload\":{\"status\":\"CHARGED\"}}";
        String charged2 = "{\"payload\":{\"status\":\"CHARGED\"}}";
        String failed = "{\"payload\":{\"status\":\"CHARGE_FAILED\"}}";

        inputTopic.pipeInput("k1", charged1);
        inputTopic.pipeInput("k2", charged2);
        inputTopic.pipeInput("k3", failed);

        var records = outputTopic.readKeyValuesToList();

        // Last CHARGED count should be 2
        long chargedCount = records.stream()
            .filter(r -> "CHARGED".equals(r.key))
            .mapToLong(r -> r.value)
            .max()
            .orElse(0);
        assertThat(chargedCount).isEqualTo(2);

        // CHARGE_FAILED count should be 1
        long failedCount = records.stream()
            .filter(r -> "CHARGE_FAILED".equals(r.key))
            .mapToLong(r -> r.value)
            .max()
            .orElse(0);
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    void shouldHaveWindowedStores() {
        assertThat(testDriver.getWindowStore(TopologyConfig.STATUS_COUNT_STORE_1H)).isNotNull();
        assertThat(testDriver.getWindowStore(TopologyConfig.STATUS_COUNT_STORE_6H)).isNotNull();
    }
}
