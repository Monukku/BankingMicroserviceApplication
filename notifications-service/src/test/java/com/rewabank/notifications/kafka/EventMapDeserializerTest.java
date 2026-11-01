package com.rewabank.notifications.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventMapDeserializerTest {

    private final EventMapDeserializer deser = new EventMapDeserializer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializes_plainJsonObject_toMap() throws Exception {
        // Shape from direct KafkaTemplate<String,Object> producers (AccountEventProducer)
        byte[] data = mapper.writeValueAsBytes(
                Map.of("eventType", "ACCOUNT_CREATED", "email", "a@b.com"));

        Map<String, Object> m = deser.deserialize("bank.account.created", data);

        assertThat(m)
                .containsEntry("eventType", "ACCOUNT_CREATED")
                .containsEntry("email", "a@b.com");
    }

    @Test
    void deserializes_outboxDoubleEncodedJsonString_toMap() throws Exception {
        // Shape from the accounts Outbox: value is a JSON *string* whose text is
        // itself the JSON object (__TypeId__ = java.lang.String on the wire).
        String innerJson =
                "{\"eventType\":\"ACCOUNT_CREATED\",\"accountNumber\":\"490759880943\"}";
        byte[] data = mapper.writeValueAsBytes(innerJson);   // -> "\"{...}\""

        Map<String, Object> m = deser.deserialize("bank.account.created", data);

        assertThat(m)
                .containsEntry("eventType", "ACCOUNT_CREATED")
                .containsEntry("accountNumber", "490759880943");
    }

    @Test
    void nullOrEmpty_returnsNull() {
        assertThat(deser.deserialize("t", null)).isNull();
        assertThat(deser.deserialize("t", new byte[0])).isNull();
    }

    @Test
    void garbage_throwsSerializationException() {
        // Must throw so ErrorHandlingDeserializer routes it to DLQ (no poison loop)
        assertThatThrownBy(() -> deser.deserialize("t", "not-json".getBytes()))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void nonObjectJson_throwsSerializationException() {
        assertThatThrownBy(() -> deser.deserialize("t", "123".getBytes()))
                .isInstanceOf(SerializationException.class);
    }
}
