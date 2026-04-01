package com.fraud.detection.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fraud.detection.model.FraudAlert;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * Serializes {@link FraudAlert} to JSON for Kafka sink (matches Spring/Jackson conventions).
 */
public class FraudAlertJsonSerializationSchema implements SerializationSchema<FraudAlert> {

    private static final long serialVersionUID = 1L;

    private transient ObjectMapper mapper;

    private ObjectMapper mapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
        return mapper;
    }

    @Override
    public byte[] serialize(FraudAlert element) {
        try {
            return mapper().writeValueAsBytes(element);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize FraudAlert", e);
        }
    }
}
