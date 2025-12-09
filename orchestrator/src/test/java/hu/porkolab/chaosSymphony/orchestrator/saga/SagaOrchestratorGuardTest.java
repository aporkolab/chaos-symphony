package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SagaOrchestratorGuardTest {

    @Test
    void markCompensated_shouldIgnoreWhenSagaMissing() {
        SagaRepository repo = mock(SagaRepository.class);
        CompensationProducer prod = mock(CompensationProducer.class);
        SagaOrchestrator orchestrator = new SagaOrchestrator(repo, prod, new SimpleMeterRegistry());

        when(repo.findById("X")).thenReturn(Optional.empty());

        orchestrator.markCompensated("X");

        verify(repo, never()).save(any());
    }
    @Test
    void onInventoryReserved_shouldIgnoreWhenSagaNotInPaymentCompletedState() {
        String orderId = "ORD";
        SagaRepository repo = mock(SagaRepository.class);
        CompensationProducer prod = mock(CompensationProducer.class);
        var orchestrator = new SagaOrchestrator(repo, prod, new SimpleMeterRegistry());

        SagaInstance saga = SagaInstance.builder()
            .orderId(orderId)
            .state(SagaState.STARTED) 
            .build();

        when(repo.findById(orderId)).thenReturn(Optional.of(saga));

        orchestrator.onInventoryReserved(orderId, "INV-1");

        assertThat(saga.getInventoryReservationId()).isEqualTo("INV-1");
        assertThat(saga.getState()).isEqualTo(SagaState.INVENTORY_RESERVED);

    }
    @Test
    void onShippingCompleted_shouldIgnoreWhenSagaNotInInventoryReservedState() {
        SagaRepository repo = mock(SagaRepository.class);
        CompensationProducer prod = mock(CompensationProducer.class);
        var orchestrator = new SagaOrchestrator(repo, prod, new SimpleMeterRegistry());

        SagaInstance saga = SagaInstance.builder()
            .orderId("ORD")
            .state(SagaState.PAYMENT_COMPLETED) 
            .build();

        when(repo.findById("ORD")).thenReturn(Optional.of(saga));

        orchestrator.onShippingCompleted("ORD", "SHIP-1");

        assertThat(saga.getShippingId()).isEqualTo("SHIP-1");
        assertThat(saga.getState()).isEqualTo(SagaState.COMPLETED);

    }

}
