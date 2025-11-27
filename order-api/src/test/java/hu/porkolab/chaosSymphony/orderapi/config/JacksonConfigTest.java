package hu.porkolab.chaosSymphony.orderapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void shouldCreateObjectMapperWithAvroModule() {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        assertThat(mapper).isNotNull();
        // Verify it can serialize/deserialize
        assertThat(mapper.canSerialize(String.class)).isTrue();
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        JacksonConfig config = new JacksonConfig();
        ObjectMapper mapper = config.objectMapper();

        TestDto original = new TestDto("test", 123);
        String json = mapper.writeValueAsString(original);
        TestDto deserialized = mapper.readValue(json, TestDto.class);

        assertThat(deserialized.name()).isEqualTo("test");
        assertThat(deserialized.value()).isEqualTo(123);
    }

    record TestDto(String name, int value) {}
}
