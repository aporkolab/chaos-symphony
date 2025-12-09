package hu.porkolab.chaosSymphony.orchestrator.saga;

import hu.porkolab.chaosSymphony.orchestrator.kafka.CompensationProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaOrchestratorStartSagaTest {

    @Test
    void startSaga_shouldStoreAndReturnStartedSaga() {
        
        SagaRepository repo = mock(SagaRepository.class);
        CompensationProducer producer = mock(CompensationProducer.class);
        var orchestrator = new SagaOrchestrator(repo, producer, new SimpleMeterRegistry());

        when(repo.save(any(SagaInstance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        
        SagaInstance saga = orchestrator.startSaga("ORD-1");

        
        assertThat(saga.getOrderId()).isEqualTo("ORD-1");
        assertThat(saga.getState()).isEqualTo(SagaState.STARTED);

        verify(repo, times(1)).save(any(SagaInstance.class));
    }
}
