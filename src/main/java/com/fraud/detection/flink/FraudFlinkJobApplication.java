package com.fraud.detection.flink;

import com.fraud.detection.config.FraudDetectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Headless Spring Boot entry point that loads {@code application.yml} and runs the Flink job.
 * Kafka bootstrap servers come from {@code spring.kafka.bootstrap-servers}; everything else from {@code fraud.*}.
 */
@SpringBootApplication(scanBasePackages = "com.fraud.detection")
@EnableConfigurationProperties(FraudDetectionProperties.class)
public class FraudFlinkJobApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(FraudFlinkJobApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext ctx = app.run(args);
        try {
            FraudDetectionProperties fraud = ctx.getBean(FraudDetectionProperties.class);
            KafkaProperties kafkaProperties = ctx.getBean(KafkaProperties.class);
            String bootstrapServers = String.join(",", kafkaProperties.getBootstrapServers());
            Thread.currentThread().setContextClassLoader(FraudFlinkJobApplication.class.getClassLoader());
            FraudVelocityFlinkJob.execute(fraud, bootstrapServers);
        } finally {
            ctx.close();
        }
    }
}
