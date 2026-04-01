package com.fraud.detection.flink.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fraud.detection.model.TransactionEvent;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;

/**
 * Deserializes JSON produced by the Spring {@code JsonSerializer} for {@link TransactionEvent}.
 */
public class TransactionEventKafkaDeserializationSchema implements KafkaRecordDeserializationSchema<TransactionEvent> {

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
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<TransactionEvent> out) throws IOException {
        byte[] value = record.value();
        if (value == null || value.length == 0) {
            return;
        }
        out.collect(mapper().readValue(value, TransactionEvent.class));
    }

    @Override
    public TypeInformation<TransactionEvent> getProducedType() {
        return TypeInformation.of(TransactionEvent.class);
    }
}
