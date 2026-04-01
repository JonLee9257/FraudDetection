package com.fraud.detection.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraud.detection.model.FraudAlert;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class FraudAlertKafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, FraudAlert> fraudAlertConsumerFactory(
            ObjectMapper kafkaObjectMapper,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            FraudDetectionProperties fraudDetectionProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, fraudDetectionProperties.kafka().alertsListenerConsumerGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        JsonDeserializer<FraudAlert> valueDeserializer = new JsonDeserializer<>(FraudAlert.class, kafkaObjectMapper, false);
        valueDeserializer.addTrustedPackages("com.fraud.detection.model");
        valueDeserializer.ignoreTypeHeaders();

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudAlert> fraudAlertKafkaListenerContainerFactory(
            ConsumerFactory<String, FraudAlert> fraudAlertConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, FraudAlert> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fraudAlertConsumerFactory);
        return factory;
    }
}
