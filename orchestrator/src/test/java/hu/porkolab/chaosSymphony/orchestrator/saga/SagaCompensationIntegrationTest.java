package hu.porkolab.chaosSymphony.orchestrator.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import hu.porkolab.chaosSymphony.orchestrator.kafka.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "payment.result",
                "inventory.result",
                "shipping.result",
                "payment.refund",
                "inventory.release",
                "order.cancel"
        },
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:0",
                "port=0"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SagaCompensationIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaRepository sagaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> compensationConsumer;

    @BeforeEach
    void setUp() {
        
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-compensation-consumer",
                "true",
                embeddedKafka
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        compensationConsumer = consumerFactory.createConsumer();
        compensationConsumer.subscribe(Collections.singletonList("payment.refund"));
    }

    @Test
    @DisplayName("Full saga should complete successfully without compensation")
    void fullSaga_happyPath_shouldCompleteWithoutCompensation() throws Exception {
        
        String orderId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();
        String reservationId = UUID.randomUUID().toString();
        String shippingId = UUID.randomUUID().toString();

        
        sagaOrchestrator.startSaga(orderId);

        
        String paymentSuccessPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("paymentId", paymentId)
                .put("status", "CHARGED")
                .toString();
        String paymentEnvelope = EnvelopeHelper.envelope(orderId, UUID.randomUUID().toString(), 
                "PaymentResult", paymentSuccessPayload);
        kafkaTemplate.send("payment.result", orderId, paymentEnvelope);

        
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
            return saga != null && saga.getState() == SagaState.PAYMENT_COMPLETED;
        });

        
        String inventorySuccessPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("reservationId", reservationId)
                .put("status", "RESERVED")
                .toString();
        String inventoryEnvelope = EnvelopeHelper.envelope(orderId, UUID.randomUUID().toString(),
                "InventoryResult", inventorySuccessPayload);
        kafkaTemplate.send("inventory.result", orderId, inventoryEnvelope);

        
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
            return saga != null && saga.getState() == SagaState.INVENTORY_RESERVED;
        });

        
        String shippingSuccessPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("shippingId", shippingId)
                .put("status", "SHIPPED")
                .toString();
        String shippingEnvelope = EnvelopeHelper.envelope(orderId, UUID.randomUUID().toString(),
                "ShippingResult", shippingSuccessPayload);
        kafkaTemplate.send("shipping.result", orderId, shippingEnvelope);

        
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
            return saga != null && saga.getState() == SagaState.COMPLETED;
        });

        SagaInstance completedSaga = sagaRepository.findById(orderId).orElseThrow();
        assertThat(completedSaga.getPaymentId()).isEqualTo(paymentId);
        assertThat(completedSaga.getInventoryReservationId()).isEqualTo(reservationId);
        assertThat(completedSaga.getShippingId()).isEqualTo(shippingId);
        assertThat(completedSaga.getFailureReason()).isNull();

        
        ConsumerRecords<String, String> records = compensationConsumer.poll(Duration.ofSeconds(2));
        assertThat(records.count()).isZero();
    }

    @Test
    @DisplayName("Inventory failure should trigger payment refund compensation")
    void inventoryFailure_shouldTriggerPaymentRefundCompensation() throws Exception {
        
        String orderId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();

        
        sagaOrchestrator.startSaga(orderId);
        sagaOrchestrator.onPaymentCompleted(orderId, paymentId);

        
        String inventoryFailurePayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("status", "OUT_OF_STOCK")
                .put("reason", "Item unavailable")
                .toString();
        String inventoryEnvelope = EnvelopeHelper.envelope(orderId, UUID.randomUUID().toString(),
                "InventoryResult", inventoryFailurePayload);
        kafkaTemplate.send("inventory.result", orderId, inventoryEnvelope);

        
        await().atMost(5, TimeUnit.SECONDS).until(() -> {
            SagaInstance saga = sagaRepository.findById(orderId).orElse(null);
            return saga != null && saga.getState() == SagaState.COMPENSATING;
        });

        
        ConsumerRecords<String, String> records = 
                KafkaTestUtils.getRecords(compensationConsumer, Duration.ofSeconds(5));
        
        assertThat(records.count()).isGreaterThan(0);
        
        
        var refundRecord = records.iterator().next();
        assertThat(refundRecord.key()).isEqualTo(orderId);
        
        var refundEnvelope = EnvelopeHelper.parse(refundRecord.value());
        assertThat(refundEnvelope.getType()).isEqualTo("PaymentRefundRequested");
    }

    @Test
    @DisplayName("Saga state should survive repository operations")
    void sagaStatePersistence_shouldSurviveRepositoryOperations() {
        
        String orderId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();

        
        SagaInstance created = sagaOrchestrator.startSaga(orderId);
        assertThat(created.getState()).isEqualTo(SagaState.STARTED);

        sagaOrchestrator.onPaymentCompleted(orderId, paymentId);

        
        SagaInstance reloaded = sagaRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(SagaState.PAYMENT_COMPLETED);
        assertThat(reloaded.getPaymentId()).isEqualTo(paymentId);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
