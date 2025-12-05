package hu.porkolab.chaosSymphony.streams;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock AdminClient adminClient;
    @Mock ListConsumerGroupOffsetsResult listOffsetsResult;
    @Mock ListOffsetsResult listOffsetsForTimesResult;
    @Mock AlterConsumerGroupOffsetsResult alterOffsetsResult;

    private ReplayService replayService;

    @BeforeEach
    void setup() {
        replayService = new ReplayService(adminClient);
    }

    @Test
    void shouldReplayFromDuration() throws Exception {
        String groupId = "test-group";
        TopicPartition tp = new TopicPartition("test-topic", 0);

        // Current offsets
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> currentOffsetsFuture = mock(KafkaFuture.class);
        when(currentOffsetsFuture.get()).thenReturn(Map.of(tp, new OffsetAndMetadata(100)));
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(currentOffsetsFuture);
        when(adminClient.listConsumerGroupOffsets(groupId)).thenReturn(listOffsetsResult);

        // Offsets for timestamp
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> timestampOffsetsFuture = mock(KafkaFuture.class);
        ListOffsetsResult.ListOffsetsResultInfo offsetInfo = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        when(offsetInfo.offset()).thenReturn(50L);
        when(timestampOffsetsFuture.get()).thenReturn(Map.of(tp, offsetInfo));
        when(listOffsetsForTimesResult.all()).thenReturn(timestampOffsetsFuture);
        when(adminClient.listOffsets(any())).thenReturn(listOffsetsForTimesResult);

        // Alter offsets
        @SuppressWarnings("unchecked")
        KafkaFuture<Void> alterFuture = mock(KafkaFuture.class);
        when(alterFuture.get()).thenReturn(null);
        when(alterOffsetsResult.all()).thenReturn(alterFuture);
        when(adminClient.alterConsumerGroupOffsets(eq(groupId), any())).thenReturn(alterOffsetsResult);

        // Execute
        replayService.replayFrom(groupId, Duration.ofHours(1));

        // Verify alter was called with correct offset
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<TopicPartition, OffsetAndMetadata>> captor = ArgumentCaptor.forClass(Map.class);
        verify(adminClient).alterConsumerGroupOffsets(eq(groupId), captor.capture());

        Map<TopicPartition, OffsetAndMetadata> newOffsets = captor.getValue();
        assertThat(newOffsets).containsKey(tp);
        assertThat(newOffsets.get(tp).offset()).isEqualTo(50L);
    }

    @Test
    void shouldNotAlterWhenNoCurrentOffsets() throws Exception {
        String groupId = "empty-group";

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> emptyFuture = mock(KafkaFuture.class);
        when(emptyFuture.get()).thenReturn(Map.of());
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(emptyFuture);
        when(adminClient.listConsumerGroupOffsets(groupId)).thenReturn(listOffsetsResult);

        replayService.replayFrom(groupId, Duration.ofHours(1));

        verify(adminClient, never()).alterConsumerGroupOffsets(any(), any());
    }

    @Test
    void shouldNotAlterWhenNoOffsetsAtTimestamp() throws Exception {
        String groupId = "test-group";
        TopicPartition tp = new TopicPartition("test-topic", 0);

        // Current offsets exist
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> currentOffsetsFuture = mock(KafkaFuture.class);
        when(currentOffsetsFuture.get()).thenReturn(Map.of(tp, new OffsetAndMetadata(100)));
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(currentOffsetsFuture);
        when(adminClient.listConsumerGroupOffsets(groupId)).thenReturn(listOffsetsResult);

        // But no offsets at timestamp (returns -1)
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> timestampOffsetsFuture = mock(KafkaFuture.class);
        ListOffsetsResult.ListOffsetsResultInfo offsetInfo = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        when(offsetInfo.offset()).thenReturn(-1L);
        when(timestampOffsetsFuture.get()).thenReturn(Map.of(tp, offsetInfo));
        when(listOffsetsForTimesResult.all()).thenReturn(timestampOffsetsFuture);
        when(adminClient.listOffsets(any())).thenReturn(listOffsetsForTimesResult);

        replayService.replayFrom(groupId, Duration.ofHours(1));

        verify(adminClient, never()).alterConsumerGroupOffsets(any(), any());
    }

    @Test
    void shouldHandleMultiplePartitions() throws Exception {
        String groupId = "multi-partition-group";
        TopicPartition tp0 = new TopicPartition("test-topic", 0);
        TopicPartition tp1 = new TopicPartition("test-topic", 1);

        // Current offsets for both partitions
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> currentOffsetsFuture = mock(KafkaFuture.class);
        when(currentOffsetsFuture.get()).thenReturn(Map.of(
            tp0, new OffsetAndMetadata(100),
            tp1, new OffsetAndMetadata(200)
        ));
        when(listOffsetsResult.partitionsToOffsetAndMetadata()).thenReturn(currentOffsetsFuture);
        when(adminClient.listConsumerGroupOffsets(groupId)).thenReturn(listOffsetsResult);

        // Timestamp offsets
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> timestampOffsetsFuture = mock(KafkaFuture.class);
        ListOffsetsResult.ListOffsetsResultInfo info0 = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        ListOffsetsResult.ListOffsetsResultInfo info1 = mock(ListOffsetsResult.ListOffsetsResultInfo.class);
        when(info0.offset()).thenReturn(50L);
        when(info1.offset()).thenReturn(150L);
        when(timestampOffsetsFuture.get()).thenReturn(Map.of(tp0, info0, tp1, info1));
        when(listOffsetsForTimesResult.all()).thenReturn(timestampOffsetsFuture);
        when(adminClient.listOffsets(any())).thenReturn(listOffsetsForTimesResult);

        // Alter
        @SuppressWarnings("unchecked")
        KafkaFuture<Void> alterFuture = mock(KafkaFuture.class);
        when(alterFuture.get()).thenReturn(null);
        when(alterOffsetsResult.all()).thenReturn(alterFuture);
        when(adminClient.alterConsumerGroupOffsets(eq(groupId), any())).thenReturn(alterOffsetsResult);

        replayService.replayFrom(groupId, Duration.ofMinutes(30));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<TopicPartition, OffsetAndMetadata>> captor = ArgumentCaptor.forClass(Map.class);
        verify(adminClient).alterConsumerGroupOffsets(eq(groupId), captor.capture());

        Map<TopicPartition, OffsetAndMetadata> newOffsets = captor.getValue();
        assertThat(newOffsets).hasSize(2);
        assertThat(newOffsets.get(tp0).offset()).isEqualTo(50L);
        assertThat(newOffsets.get(tp1).offset()).isEqualTo(150L);
    }
}
