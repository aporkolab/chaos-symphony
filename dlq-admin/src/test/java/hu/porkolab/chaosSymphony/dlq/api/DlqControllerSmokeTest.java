package hu.porkolab.chaosSymphony.dlq.api;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DlqController.class)
@AutoConfigureMockMvc(addFilters = false)
class DlqControllerSmokeTest {

    @Autowired MockMvc mvc;

    @MockBean KafkaTemplate<String, String> template;
    @MockBean ProducerFactory<String, String> pf;

    private AdminClient mockAdmin() {
        return mock(AdminClient.class);
    }

    @Test
    void listTopics() throws Exception {
        AdminClient admin = mockAdmin();

        ListTopicsResult ltr = mock(ListTopicsResult.class);
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(ltr);

        @SuppressWarnings("unchecked")
        KafkaFuture<Set<String>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Set.of("A", "X.DLT", "Z.DLT"));
        when(ltr.names()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class)) {
            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(get("/api/dlq/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("X.DLT"))
                .andExpect(jsonPath("$[1]").value("Z.DLT"));
        }
    }

    @Test
    void testCount() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        TopicPartitionInfo part = new TopicPartitionInfo(0, null, List.of(), List.of());
        TopicDescription desc = new TopicDescription("A", false, List.of(part));

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of("A", desc));
        when(dtr.all()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class);
             MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class, (c, ctx) -> {
                 ConsumerRecord<String, String> rc = new ConsumerRecord<>("A", 0, 0, "k", "v");
                 ConsumerRecords<String, String> batch = new ConsumerRecords<>(
                     Map.of(new TopicPartition("A", 0), List.of(rc)));
                 ConsumerRecords<String, String> empty = new ConsumerRecords<>(Collections.emptyMap());

                 when(c.poll(any(Duration.class))).thenReturn(batch).thenReturn(empty);
             })) {

            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(get("/api/dlq/A/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
        }
    }

    @Test
    void testPeek() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        TopicPartitionInfo part = new TopicPartitionInfo(0, null, List.of(), List.of());
        TopicDescription desc = new TopicDescription("P", false, List.of(part));

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of("P", desc));
        when(dtr.all()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class);
             MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class, (c, ctx) -> {
                 ConsumerRecord<String, String> r1 = new ConsumerRecord<>("P", 0, 0, "k1", "v1");
                 ConsumerRecord<String, String> r2 = new ConsumerRecord<>("P", 0, 1, "k2", "v2");
                 ConsumerRecords<String, String> batch = new ConsumerRecords<>(
                     Map.of(new TopicPartition("P", 0), List.of(r1, r2)));
                 ConsumerRecords<String, String> empty = new ConsumerRecords<>(Collections.emptyMap());

                 when(c.poll(any(Duration.class))).thenReturn(batch).thenReturn(empty);
             })) {

            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(get("/api/dlq/P/peek?n=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("v1"))
                .andExpect(jsonPath("$[1]").value("v2"));
        }
    }

    @Test
    void testPurge() throws Exception {
        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class, (c, ctx) -> {
            TopicPartition tp = new TopicPartition("DEL", 0);
            ConsumerRecord<String, String> r = new ConsumerRecord<>("DEL", 0, 0, "k", "v");
            ConsumerRecords<String, String> batch = new ConsumerRecords<>(Map.of(tp, List.of(r)));
            ConsumerRecords<String, String> empty = new ConsumerRecords<>(Collections.emptyMap());

            
            
            
            when(c.poll(any(Duration.class)))
                .thenReturn(empty)
                .thenReturn(batch)
                .thenReturn(empty);

            when(c.assignment()).thenReturn(Set.of(tp));
        })) {

            mvc.perform(delete("/api/dlq/DEL"))
                .andExpect(status().isOk())
                .andExpect(content().string("Purged 1 records from DEL"));
        }
    }

    @Test
    void testReplay_notDltTopic_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/dlq/regular-topic/replay"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Not a DLT topic"));
    }

    @Test
    void testReplay_emptyTopic_returnsZero() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of()); 
        when(dtr.all()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class)) {
            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(post("/api/dlq/orders.DLT/replay"))
                .andExpect(status().isOk())
                .andExpect(content().string("Replayed 0 records from orders.DLT to orders"));
        }
    }

    @Test
    void testReplay_withMessages_replaysAll() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        TopicPartitionInfo part = new TopicPartitionInfo(0, null, List.of(), List.of());
        TopicDescription desc = new TopicDescription("orders.DLT", false, List.of(part));

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of("orders.DLT", desc));
        when(dtr.all()).thenReturn(fut);

        
        java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> sendFuture =
            java.util.concurrent.CompletableFuture.completedFuture(null);
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(sendFuture);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class);
             MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class, (c, ctx) -> {
                 TopicPartition tp = new TopicPartition("orders.DLT", 0);

                 
                 @SuppressWarnings("unchecked")
                 ConsumerRecord<String, String> r1 = mock(ConsumerRecord.class);
                 when(r1.key()).thenReturn("k1");
                 when(r1.value()).thenReturn("v1");
                 when(r1.timestamp()).thenReturn(System.currentTimeMillis());
                 when(r1.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());

                 @SuppressWarnings("unchecked")
                 ConsumerRecord<String, String> r2 = mock(ConsumerRecord.class);
                 when(r2.key()).thenReturn("k2");
                 when(r2.value()).thenReturn("v2");
                 when(r2.timestamp()).thenReturn(System.currentTimeMillis());
                 when(r2.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());

                 ConsumerRecords<String, String> batch = new ConsumerRecords<>(Map.of(tp, List.of(r1, r2)));
                 ConsumerRecords<String, String> empty = new ConsumerRecords<>(Collections.emptyMap());

                 when(c.poll(any(Duration.class))).thenReturn(batch).thenReturn(empty);
             })) {

            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(post("/api/dlq/orders.DLT/replay"))
                .andExpect(status().isOk())
                .andExpect(content().string("Replayed 2 records from orders.DLT to orders"));
        }
    }

    @Test
    void testReplayRange_notDltTopic_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/dlq/regular-topic/replay-range")
                .param("fromOffset", "0")
                .param("toOffset", "10"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Not a DLT topic"));
    }

    @Test
    void testReplayRange_withMessages_replaysRange() throws Exception {
        
        java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> sendFuture =
            java.util.concurrent.CompletableFuture.completedFuture(null);
        when(template.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(sendFuture);

        try (MockedConstruction<KafkaConsumer> mc = mockConstruction(KafkaConsumer.class, (c, ctx) -> {
            TopicPartition tp = new TopicPartition("events.DLT", 0);
            long now = System.currentTimeMillis();

            
            @SuppressWarnings("unchecked")
            ConsumerRecord<String, String> r1 = mock(ConsumerRecord.class);
            when(r1.offset()).thenReturn(5L);
            when(r1.key()).thenReturn("k1");
            when(r1.value()).thenReturn("v1");
            when(r1.timestamp()).thenReturn(now);
            when(r1.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());

            @SuppressWarnings("unchecked")
            ConsumerRecord<String, String> r2 = mock(ConsumerRecord.class);
            when(r2.offset()).thenReturn(6L);
            when(r2.key()).thenReturn("k2");
            when(r2.value()).thenReturn("v2");
            when(r2.timestamp()).thenReturn(now);
            when(r2.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());

            @SuppressWarnings("unchecked")
            ConsumerRecord<String, String> r3 = mock(ConsumerRecord.class);
            when(r3.offset()).thenReturn(11L); 
            when(r3.key()).thenReturn("k3");
            when(r3.value()).thenReturn("v3");
            when(r3.timestamp()).thenReturn(now);
            when(r3.headers()).thenReturn(new org.apache.kafka.common.header.internals.RecordHeaders());

            ConsumerRecords<String, String> batch = new ConsumerRecords<>(Map.of(tp, List.of(r1, r2, r3)));
            ConsumerRecords<String, String> empty = new ConsumerRecords<>(Collections.emptyMap());

            when(c.poll(any(Duration.class))).thenReturn(batch).thenReturn(empty);
        })) {

            mvc.perform(post("/api/dlq/events.DLT/replay-range")
                    .param("fromOffset", "5")
                    .param("toOffset", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string("Replayed 2 records from events.DLT to events"));
        }
    }

    @Test
    void testCount_topicNotFound_returnsZero() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of()); 
        when(dtr.all()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class)) {
            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(get("/api/dlq/nonexistent/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
        }
    }

    @Test
    void testPeek_topicNotFound_returnsEmptyList() throws Exception {
        AdminClient admin = mockAdmin();

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of()); 
        when(dtr.all()).thenReturn(fut);

        try (MockedStatic<AdminClient> ms = mockStatic(AdminClient.class)) {
            ms.when(() -> AdminClient.create(any(Properties.class))).thenReturn(admin);

            mvc.perform(get("/api/dlq/nonexistent/peek"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }
}
