package hu.porkolab.chaosSymphony.common.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class KafkaGracefulShutdownTest {

    @Mock
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Mock
    private MessageListenerContainer container1;

    @Mock
    private MessageListenerContainer container2;

    private KafkaGracefulShutdown gracefulShutdown;

    @BeforeEach
    void setUp() {
        gracefulShutdown = new KafkaGracefulShutdown(kafkaListenerEndpointRegistry);
    }

    @Test
    @DisplayName("Should stop all running containers on shutdown")
    void onShutdown_shouldStopAllRunningContainers() {
        
        when(container1.getListenerId()).thenReturn("listener-1");
        when(container1.isRunning()).thenReturn(true, false);
        
        when(container2.getListenerId()).thenReturn("listener-2");
        when(container2.isRunning()).thenReturn(true, false);
        
        Collection<MessageListenerContainer> containers = List.of(container1, container2);
        when(kafkaListenerEndpointRegistry.getAllListenerContainers()).thenReturn(containers);

        
        gracefulShutdown.onShutdown();

        
        verify(container1).stop(any());
        verify(container2).stop(any());
    }

    @Test
    @DisplayName("Should handle empty container list gracefully")
    void onShutdown_withNoContainers_shouldNotThrow() {
        
        when(kafkaListenerEndpointRegistry.getAllListenerContainers())
                .thenReturn(Collections.emptyList());

        
        gracefulShutdown.onShutdown();

        
        verify(kafkaListenerEndpointRegistry).getAllListenerContainers();
    }

    @Test
    @DisplayName("Should skip already stopped containers")
    void onShutdown_withStoppedContainer_shouldSkipIt() {
        
        when(container1.getListenerId()).thenReturn("listener-1");
        when(container1.isRunning()).thenReturn(false);
        
        when(kafkaListenerEndpointRegistry.getAllListenerContainers())
                .thenReturn(List.of(container1));

        
        gracefulShutdown.onShutdown();

        
        verify(container1, never()).stop(any());
    }

    @Test
    @DisplayName("Should handle mixed running and stopped containers")
    void onShutdown_withMixedContainers_shouldOnlyStopRunning() {
        
        when(container1.getListenerId()).thenReturn("running-listener");
        when(container1.isRunning()).thenReturn(true, false);
        
        when(container2.getListenerId()).thenReturn("stopped-listener");
        when(container2.isRunning()).thenReturn(false);
        
        when(kafkaListenerEndpointRegistry.getAllListenerContainers())
                .thenReturn(List.of(container1, container2));

        
        gracefulShutdown.onShutdown();

        
        verify(container1).stop(any());
        verify(container2, never()).stop(any());
    }
}
