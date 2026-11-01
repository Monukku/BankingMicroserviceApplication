package com.rewabank.notifications.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Deserializes bank.* event values into a {@code Map<String,Object>}, tolerant of
 * BOTH serialization shapes present in the system:
 *
 * <ul>
 *   <li>a JSON <b>object</b> {@code {...}} — direct {@code KafkaTemplate<String,Object>}
 *       producers (e.g. accounts {@code AccountEventProducer});</li>
 *   <li>a JSON <b>string</b> {@code "{...}"} — the accounts Outbox double-encodes the
 *       payload (it stores the event as a String, which the JSON serializer then wraps
 *       again), so on the wire {@code __TypeId__ = java.lang.String}.</li>
 * </ul>
 *
 * Keeping the output a {@code Map} means the {@code @KafkaListener} methods stay
 * unchanged. Deliberately throws {@link SerializationException} on unparseable or
 * non-object payloads so the configured {@code ErrorHandlingDeserializer} routes them
 * to the DLQ instead of poison-looping the consumer.
 */
public class EventMapDeserializer implements Deserializer<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    @Override
    public Map<String, Object> deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(data);
            // Unwrap the Outbox's double-encoding: a JSON string whose text is the
            // actual JSON object.
            if (node.isTextual()) {
                node = MAPPER.readTree(node.asText());
            }
            if (!node.isObject()) {
                throw new SerializationException(
                        "Expected a JSON object event on topic '" + topic
                                + "' but got " + node.getNodeType());
            }
            return MAPPER.convertValue(node, MAP_TYPE);
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new SerializationException(
                    "Failed to deserialize event on topic '" + topic + "'", e);
        }
    }
}
