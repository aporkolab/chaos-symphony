package hu.porkolab.chaosSymphony.dlq.api;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
@Import(TestConfig.class)
class DlqControllerTest {

    @Autowired MockMvc mvc;

    @MockBean KafkaTemplate<String, String> template;
    @MockBean ProducerFactory<String, String> pf;
    @MockBean AdminClient admin;

    @Test
    void listTopics() throws Exception {

        ListTopicsResult ltr = mock(ListTopicsResult.class);
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(ltr);

        var fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Set.of("A", "X.DLT", "Z.DLT"));
        when(ltr.names()).thenReturn(fut);

        mvc.perform(get("/api/dlq/topics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("X.DLT"))
            .andExpect(jsonPath("$[1]").value("Z.DLT"));
    }

    @Test
    void testCount() throws Exception {

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        TopicPartitionInfo part = new TopicPartitionInfo(0, null, null, List.of());
        TopicDescription desc = new TopicDescription("A", false, List.of(part));

        var fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of("A", desc));
        when(dtr.all()).thenReturn(fut);

        try (MockedConstruction<KafkaConsumer> mc =
                 mockConstruction(KafkaConsumer.class, (c, ctx) -> {

                     ConsumerRecord<String,String> rc =
                         new ConsumerRecord<>("A",0,0,"k","v");

                     ConsumerRecords<String,String> batch =
                         new ConsumerRecords<>(Map.of(new TopicPartition("A",0), List.of(rc)));

                     ConsumerRecords<String,String> empty =
                         new ConsumerRecords<>(Collections.emptyMap());

                     when(c.poll(any(Duration.class)))
                         .thenReturn(batch)
                         .thenReturn(empty);
                 })) {

            mvc.perform(get("/api/dlq/A/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
        }
    }

    @Test
    void testPeek() throws Exception {

        DescribeTopicsResult dtr = mock(DescribeTopicsResult.class);
        when(admin.describeTopics(any(Collection.class))).thenReturn(dtr);

        TopicPartitionInfo part = new TopicPartitionInfo(0, null, null, List.of());
        TopicDescription desc = new TopicDescription("P", false, List.of(part));

        var fut = mock(KafkaFuture.class);
        when(fut.get()).thenReturn(Map.of("P", desc));
        when(dtr.all()).thenReturn(fut);

        try (MockedConstruction<KafkaConsumer> mc =
                 mockConstruction(KafkaConsumer.class, (c, ctx) -> {

                     ConsumerRecord<String,String> r1 = new ConsumerRecord<>("P",0,0,"k1","v1");
                     ConsumerRecord<String,String> r2 = new ConsumerRecord<>("P",0,1,"k2","v2");

                     ConsumerRecords<String,String> batch =
                         new ConsumerRecords<>(Map.of(new TopicPartition("P",0), List.of(r1,r2)));

                     ConsumerRecords<String,String> empty =
                         new ConsumerRecords<>(Collections.emptyMap());

                     when(c.poll(any(Duration.class)))
                         .thenReturn(batch)
                         .thenReturn(empty);
                 })) {

            mvc.perform(get("/api/dlq/P/peek?n=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("v1"))
                .andExpect(jsonPath("$[1]").value("v2"));
        }
    }

    @Test
    void testPurge() throws Exception {

        try (MockedConstruction<KafkaConsumer> mc =
                 mockConstruction(KafkaConsumer.class, (c, ctx) -> {

                     ConsumerRecord<String,String> r =
                         new ConsumerRecord<>("DEL",0,0,"k","v");

                     ConsumerRecords<String,String> batch =
                         new ConsumerRecords<>(Map.of(
                             new TopicPartition("DEL",0), List.of(r)
                         ));

                     ConsumerRecords<String,String> empty =
                         new ConsumerRecords<>(Collections.emptyMap());

                     when(c.poll(any(Duration.class)))
                         .thenReturn(batch)
                         .thenReturn(empty);
                 })) {

            mvc.perform(delete("/api/dlq/DEL"))
                .andExpect(status().isOk())
                .andExpect(content().string("Purged 1 records from DEL"));
        }
    }
}
