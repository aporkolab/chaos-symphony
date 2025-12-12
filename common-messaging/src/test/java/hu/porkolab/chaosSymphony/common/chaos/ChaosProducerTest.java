package hu.porkolab.chaosSymphony.common.chaos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChaosProducer Tests")
class ChaosProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ChaosProducer chaosProducer;

    @Nested
    @DisplayName("Normal Send Tests (No Chaos)")
    class NormalSendTests {

        @BeforeEach
        void setUp() {
            
            ChaosRules noRules = new ChaosRules(Map.of());
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> noRules);
        }

        @Test
        @DisplayName("Should send message normally when no chaos rules")
        void send_noRules_shouldSendNormally() {
            
            String topic = "test-topic";
            String key = "test-key";
            String type = "TestType";
            String message = "{\"data\": \"test\"}";

            
            chaosProducer.send(topic, key, type, message);

            
            verify(kafkaTemplate).send(topic, key, message);
        }

        @Test
        @DisplayName("Should send exactly once when no duplicate rule")
        void send_noDupRule_shouldSendOnce() {
            
            String topic = "test-topic";
            String key = "test-key";
            String type = "TestType";
            String message = "{\"data\": \"test\"}";

            
            chaosProducer.send(topic, key, type, message);

            
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Drop Tests")
    class DropTests {

        @Test
        @DisplayName("Should throw ChaosDropException when drop probability is 1.0")
        void send_withDropProbabilityOne_shouldDrop() {
            
            ChaosRules.Rule dropRule = new ChaosRules.Rule(1.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", dropRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            org.junit.jupiter.api.Assertions.assertThrows(
                ChaosProducer.ChaosDropException.class,
                () -> chaosProducer.send("test-topic", "key", "type", "message")
            );

            
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should not drop message when drop probability is 0.0")
        void send_withDropProbabilityZero_shouldNotDrop() {
            
            ChaosRules.Rule noDropRule = new ChaosRules.Rule(0.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", noDropRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            chaosProducer.send("test-topic", "key", "type", "message");

            
            verify(kafkaTemplate).send("test-topic", "key", "message");
        }
    }

    @Nested
    @DisplayName("Duplicate Tests")
    class DuplicateTests {

        @Test
        @DisplayName("Should duplicate message when dup probability is 1.0")
        void send_withDupProbabilityOne_shouldDuplicate() {
            
            ChaosRules.Rule dupRule = new ChaosRules.Rule(0.0, 1.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", dupRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            chaosProducer.send("test-topic", "key", "type", "message");

            
            verify(kafkaTemplate, times(2)).send("test-topic", "key", "message");
        }

        @Test
        @DisplayName("Should not duplicate message when dup probability is 0.0")
        void send_withDupProbabilityZero_shouldNotDuplicate() {
            
            ChaosRules.Rule noDupRule = new ChaosRules.Rule(0.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", noDupRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            chaosProducer.send("test-topic", "key", "type", "message");

            
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Corruption Tests")
    class CorruptionTests {

        @Test
        @DisplayName("Should corrupt message when corrupt probability is 1.0")
        void send_withCorruptProbabilityOne_shouldCorrupt() {
            
            ChaosRules.Rule corruptRule = new ChaosRules.Rule(0.0, 0.0, 0, 1.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", corruptRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);
            String originalMessage = "0123456789"; 

            
            chaosProducer.send("test-topic", "key", "type", originalMessage);

            
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("test-topic"), eq("key"), messageCaptor.capture());
            
            String sentMessage = messageCaptor.getValue();
            assertThat(sentMessage.length()).isLessThan(originalMessage.length());
            assertThat(sentMessage).isEqualTo("01234"); 
        }

        @Test
        @DisplayName("Should not corrupt message when corrupt probability is 0.0")
        void send_withCorruptProbabilityZero_shouldNotCorrupt() {
            
            ChaosRules.Rule noCorruptRule = new ChaosRules.Rule(0.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", noCorruptRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);
            String originalMessage = "0123456789";

            
            chaosProducer.send("test-topic", "key", "type", originalMessage);

            
            verify(kafkaTemplate).send("test-topic", "key", originalMessage);
        }

        @Test
        @DisplayName("Should handle single character message corruption")
        void send_withSingleCharMessage_shouldCorruptToOneChar() {
            
            ChaosRules.Rule corruptRule = new ChaosRules.Rule(0.0, 0.0, 0, 1.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", corruptRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            chaosProducer.send("test-topic", "key", "type", "x");

            
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("test-topic"), eq("key"), messageCaptor.capture());
            assertThat(messageCaptor.getValue().length()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Delay Tests")
    class DelayTests {

        @Test
        @DisplayName("Should not significantly delay when maxDelayMs is 0")
        void send_withZeroDelay_shouldNotDelay() {
            
            ChaosRules.Rule noDelayRule = new ChaosRules.Rule(0.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:test-topic", noDelayRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            long startTime = System.currentTimeMillis();

            
            chaosProducer.send("test-topic", "key", "type", "message");

            
            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isLessThan(100); 
        }
    }

    @Nested
    @DisplayName("Rule Resolution Tests")
    class RuleResolutionTests {

        @Test
        @DisplayName("Should use topic-specific rule and throw exception")
        void send_withTopicRule_shouldUseTopicRule() {
            
            ChaosRules.Rule topicDropRule = new ChaosRules.Rule(1.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:specific-topic", topicDropRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            org.junit.jupiter.api.Assertions.assertThrows(
                ChaosProducer.ChaosDropException.class,
                () -> chaosProducer.send("specific-topic", "key", "type", "message")
            );

            
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should fallback to type rule when no topic rule and throw exception")
        void send_withTypeRuleOnly_shouldUseTypeRule() {
            
            ChaosRules.Rule typeDropRule = new ChaosRules.Rule(1.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("type:SpecificType", typeDropRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            org.junit.jupiter.api.Assertions.assertThrows(
                ChaosProducer.ChaosDropException.class,
                () -> chaosProducer.send("any-topic", "key", "SpecificType", "message")
            );

            
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should use default rule when no matching rules")
        void send_withNoMatchingRules_shouldSendNormally() {
            
            ChaosRules.Rule unrelatedRule = new ChaosRules.Rule(1.0, 0.0, 0, 0.0);
            ChaosRules rules = new ChaosRules(Map.of("topic:other-topic", unrelatedRule));
            chaosProducer = new ChaosProducer(kafkaTemplate, () -> rules);

            
            chaosProducer.send("test-topic", "key", "type", "message");

            
            verify(kafkaTemplate).send("test-topic", "key", "message");
        }
    }

    @Nested
    @DisplayName("Supplier Behavior Tests")
    class SupplierBehaviorTests {

        @Test
        @DisplayName("Should call supplier for each send")
        void send_multipleMessages_shouldCallSupplierEachTime() {
            
            Supplier<ChaosRules> supplierSpy = mock(Supplier.class);
            when(supplierSpy.get()).thenReturn(new ChaosRules(Map.of()));
            chaosProducer = new ChaosProducer(kafkaTemplate, supplierSpy);

            
            chaosProducer.send("topic", "key1", "type", "msg1");
            chaosProducer.send("topic", "key2", "type", "msg2");

            
            verify(supplierSpy, atLeast(4)).get();
        }
    }
}
