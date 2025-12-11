package hu.porkolab.chaosSymphony.streams.service;

import hu.porkolab.chaosSymphony.streams.config.MetricsConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsUpdaterServiceTest {

    @Mock MetricsConfig metricsConfig;
    @Mock KafkaStreams kafkaStreams;
    @Mock ReadOnlyWindowStore<String, Long> store1h;
    @Mock ReadOnlyWindowStore<String, Long> store6h;

    private MetricsUpdaterService service;

    @BeforeEach
    void setup() {
        service = new MetricsUpdaterService(kafkaStreams, metricsConfig);
    }

    @Test
    void shouldDoNothingWhenKafkaStreamsIsNotRunning() {
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.NOT_RUNNING);

        service.updateMetrics();

        verifyNoInteractions(metricsConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateMetricsWithCounts() {
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(any(StoreQueryParameters.class)))
            .thenReturn(store1h)
            .thenReturn(store6h);

        
        WindowStoreIterator<Long> chargedIter1h = createIterator(100L);
        WindowStoreIterator<Long> failedIter1h = createIterator(10L);
        WindowStoreIterator<Long> chargedIter6h = createIterator(500L);
        WindowStoreIterator<Long> failedIter6h = createIterator(50L);

        when(store1h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(chargedIter1h);
        when(store1h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(failedIter1h);
        when(store6h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(chargedIter6h);
        when(store6h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(failedIter6h);

        service.updateMetrics();

        
        verify(metricsConfig).updateSloBurnRate1h(9L);
        
        verify(metricsConfig).updateSloBurnRate6h(9L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleZeroTotalCount() {
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(any(StoreQueryParameters.class)))
            .thenReturn(store1h)
            .thenReturn(store6h);

        WindowStoreIterator<Long> emptyIter1 = createEmptyIterator();
        WindowStoreIterator<Long> emptyIter2 = createEmptyIterator();
        WindowStoreIterator<Long> emptyIter3 = createEmptyIterator();
        WindowStoreIterator<Long> emptyIter4 = createEmptyIterator();

        when(store1h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter1);
        when(store1h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter2);
        when(store6h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter3);
        when(store6h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter4);

        service.updateMetrics();

        verify(metricsConfig).updateSloBurnRate1h(0L);
        verify(metricsConfig).updateSloBurnRate6h(0L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleStoreException() {
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(any(StoreQueryParameters.class)))
            .thenThrow(new RuntimeException("Store not available"));

        service.updateMetrics();

        verifyNoInteractions(metricsConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCalculate100PercentBurnRate() {
        when(kafkaStreams.state()).thenReturn(KafkaStreams.State.RUNNING);
        when(kafkaStreams.store(any(StoreQueryParameters.class)))
            .thenReturn(store1h)
            .thenReturn(store6h);

        
        WindowStoreIterator<Long> emptyIter1 = createEmptyIterator();
        WindowStoreIterator<Long> failedIter1h = createIterator(100L);
        WindowStoreIterator<Long> emptyIter2 = createEmptyIterator();
        WindowStoreIterator<Long> failedIter6h = createIterator(100L);

        when(store1h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter1);
        when(store1h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(failedIter1h);
        when(store6h.fetch(eq("CHARGED"), any(Instant.class), any(Instant.class))).thenReturn(emptyIter2);
        when(store6h.fetch(eq("CHARGE_FAILED"), any(Instant.class), any(Instant.class))).thenReturn(failedIter6h);

        service.updateMetrics();

        verify(metricsConfig).updateSloBurnRate1h(100L);
        verify(metricsConfig).updateSloBurnRate6h(100L);
    }

    @SuppressWarnings("unchecked")
    private WindowStoreIterator<Long> createIterator(Long value) {
        WindowStoreIterator<Long> iter = mock(WindowStoreIterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.next()).thenReturn(KeyValue.pair(0L, value));
        return iter;
    }

    @SuppressWarnings("unchecked")
    private WindowStoreIterator<Long> createEmptyIterator() {
        WindowStoreIterator<Long> iter = mock(WindowStoreIterator.class);
        when(iter.hasNext()).thenReturn(false);
        return iter;
    }
}
