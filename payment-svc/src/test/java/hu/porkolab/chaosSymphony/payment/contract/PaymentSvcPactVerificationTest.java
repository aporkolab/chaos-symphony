package hu.porkolab.chaosSymphony.payment.contract;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.porkolab.chaosSymphony.common.EnvelopeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;


@Provider("payment-svc")
@au.com.dius.pact.provider.junitsupport.loader.PactFolder("../orchestrator/target/pacts")
@DisplayName("Payment Service Provider Verification")
public class PaymentSvcPactVerificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    
    private String currentOrderId;
    private String currentPaymentId;
    private double currentAmount;
    private String currentCurrency;
    private String failureReason;

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    @DisplayName("Verify Pact contract interaction")
    void pactVerificationTestTemplate(PactVerificationContext context) {
        if (context != null) {
            context.verifyInteraction();
        }
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        if (context != null) {
            context.setTarget(new MessageTestTarget());
        }
        
        currentOrderId = UUID.randomUUID().toString();
        currentPaymentId = UUID.randomUUID().toString();
        currentAmount = 123.45;
        currentCurrency = "USD";
        failureReason = null;
    }

    
    

    @State("a valid order exists")
    void setupValidOrder() {
        currentOrderId = "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46";
        currentAmount = 123.45;
        currentCurrency = "USD";
    }

    @State("payment processing succeeds")
    void setupPaymentSuccess() {
        currentPaymentId = UUID.randomUUID().toString();
        failureReason = null;
    }

    @State("payment processing fails")
    void setupPaymentFailure() {
        failureReason = "Card declined - insufficient funds";
    }

    
    

    
    @PactVerifyProvider("A payment requested event")
    public MessageAndMetadata verifyPaymentRequestedMessage() throws Exception {
        String orderId = "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46";
        String eventId = "f8b5c2d1-3e4f-5a6b-7c8d-9e0f1a2b3c4d";
        
        
        String paymentPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("amount", 123.45)
                .put("currency", "USD")
                .toString();
        
        
        String envelopedMessage = EnvelopeHelper.envelope(
                orderId, 
                eventId, 
                "PaymentRequested", 
                paymentPayload
        );
        
        return new MessageAndMetadata(
            envelopedMessage.getBytes(),
            Map.of(
                "content-type", "application/json",
                "kafka-topic", "payment.request"
            )
        );
    }

    
    @PactVerifyProvider("A payment completed event")
    public MessageAndMetadata verifyPaymentCompletedMessage() throws Exception {
        String orderId = "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46";
        String eventId = UUID.randomUUID().toString();
        
        
        String resultPayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("paymentId", currentPaymentId)
                .put("status", "CHARGED")
                .put("amount", currentAmount)
                .put("currency", currentCurrency)
                .toString();
        
        String envelopedMessage = EnvelopeHelper.envelope(
                orderId,
                eventId,
                "PaymentCompleted",
                resultPayload
        );
        
        return new MessageAndMetadata(
            envelopedMessage.getBytes(),
            Map.of(
                "content-type", "application/json",
                "kafka-topic", "payment.result"
            )
        );
    }

    
    @PactVerifyProvider("A payment failed event")
    public MessageAndMetadata verifyPaymentFailedMessage() throws Exception {
        String orderId = "e7a4f431-b2e3-4b43-8a24-8e2b1d3a0e46";
        String eventId = UUID.randomUUID().toString();
        
        
        String failurePayload = objectMapper.createObjectNode()
                .put("orderId", orderId)
                .put("status", "FAILED")
                .put("reason", failureReason != null ? failureReason : "Payment processing failed")
                .toString();
        
        String envelopedMessage = EnvelopeHelper.envelope(
                orderId,
                eventId,
                "PaymentFailed",
                failurePayload
        );
        
        return new MessageAndMetadata(
            envelopedMessage.getBytes(),
            Map.of(
                "content-type", "application/json",
                "kafka-topic", "payment.result"
            )
        );
    }
}
