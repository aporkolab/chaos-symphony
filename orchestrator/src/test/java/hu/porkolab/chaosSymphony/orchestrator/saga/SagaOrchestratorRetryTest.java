package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SagaOrchestratorRetryTest {

    private SagaRepository repo;
    private CompensationProducer producer;
    private SagaOrchestrator orchestrator;

    @BeforeEach
    void init() {
        repo = mock(SagaRepository.class);
        producer = mock(CompensationProducer.class);

        orchestrator = new SagaOrchestrator(
            repo,
            producer,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void retry_shouldTriggerShippingFailed() {
        SagaInstance saga = SagaInstance.builder()
            .orderId("o1")
            .state(SagaState.COMPENSATING)
            .failureReason("shipping timeout")
            .retryCount(0)
            .build();

        when(repo.findStuckSagas(anyList(), any())).thenReturn(List.of(saga));
        when(repo.findById("o1")).thenReturn(Optional.of(saga));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        orchestrator.retryStuckCompensations();

        
        assertThat(saga.getRetryCount()).isEqualTo(1);

        
        verify(producer).requestOrderCancellation(eq("o1"),
            contains("Retry compensation attempt"));

        
        verify(producer, never()).requestInventoryRelease(any(), any(), any());
        verify(producer, never()).requestPaymentRefund(any(), any(), any());
    }

    @Test
    void retry_shouldTriggerInventoryFailed() {
        SagaInstance saga = SagaInstance.builder()
            .orderId("o2")
            .state(SagaState.COMPENSATING)
            .retryCount(0)
            .failureReason("inventory issue")
            .paymentId("p1")
            .build();

        when(repo.findStuckSagas(any(), any())).thenReturn(List.of(saga));
        when(repo.findById("o2")).thenReturn(Optional.of(saga));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        orchestrator.retryStuckCompensations();

        verify(producer, never()).requestInventoryRelease(any(), any(), any());
        verify(producer).requestPaymentRefund(eq("o2"), eq("p1"), anyString());
        verify(producer).requestOrderCancellation(eq("o2"), anyString());
    }

    @Test
    void retry_shouldTriggerPaymentFailed() {
        SagaInstance saga = SagaInstance.builder()
            .orderId("o3")
            .state(SagaState.COMPENSATING)
            .retryCount(0)
            .failureReason("random failure")
            .build();

        when(repo.findStuckSagas(any(), any())).thenReturn(List.of(saga));
        when(repo.findById("o3")).thenReturn(Optional.of(saga));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        orchestrator.retryStuckCompensations();

        verify(producer).requestOrderCancellation(eq("o3"), anyString()); 
    }
}
