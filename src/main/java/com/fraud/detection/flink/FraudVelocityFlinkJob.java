package com.fraud.detection.flink;

import com.fraud.detection.config.FraudDetectionProperties;
import com.fraud.detection.config.KafkaConfig;
import com.fraud.detection.model.FraudAlert;
import com.fraud.detection.model.TransactionEvent;
import com.fraud.detection.flink.serialization.FraudAlertJsonSerializationSchema;
import com.fraud.detection.flink.serialization.TransactionEventKafkaDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.configuration.DeploymentOptions;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

/**
 * Flink job: read inbound topic, key by {@code userId}, velocity check, write alerts topic.
 * <p>
 * Configuration: {@code fraud.*} and {@code spring.kafka.bootstrap-servers} in {@code application.yml}.
 * Run: {@code mvn -q exec:java -Dexec.mainClass=com.fraud.detection.flink.FraudFlinkJobApplication}
 */
public final class FraudVelocityFlinkJob {

    private static final Duration MAX_OUT_OF_ORDERNESS = Duration.ofSeconds(5);

    public static void execute(FraudDetectionProperties fraud, String bootstrapServers) throws Exception {
        FraudDetectionProperties.Kafka kafka = fraud.kafka();
        FraudDetectionProperties.Velocity velocity = fraud.velocity();
        long windowMs = velocity.windowSeconds() * 1000;

        // Spring Boot fat JAR: JobMaster must see nested BOOT-INF/lib JARs (flink-core, etc.); add URLs to
        // pipeline.classpaths — see FlinkSpringBootClasspath. Also use app ClassLoader (below).
        Configuration flinkConfig = new Configuration();
        flinkConfig.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first");
        FlinkSpringBootClasspath.augment(flinkConfig);
        StreamExecutionEnvironment env = createLocalEnvironmentWithAppClassLoader(flinkConfig);
        env.setParallelism(fraud.flink().parallelism());

        KafkaSource<TransactionEvent> kafkaSource = KafkaSource.<TransactionEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(KafkaConfig.TOPIC_TRANSACTIONS_INBOUND)
                .setGroupId(kafka.consumerGroupId())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new TransactionEventKafkaDeserializationSchema())
                .build();

        KafkaSink<FraudAlert> kafkaSink = KafkaSink.<FraudAlert>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<FraudAlert>builder()
                                .setTopic(KafkaConfig.TOPIC_FRAUD_ALERTS)
                                .setValueSerializationSchema(new FraudAlertJsonSerializationSchema())
                                .build())
                .build();

        WatermarkStrategy<TransactionEvent> watermarkStrategy =
                WatermarkStrategy.<TransactionEvent>forBoundedOutOfOrderness(MAX_OUT_OF_ORDERNESS)
                        .withTimestampAssigner((event, recordTimestamp) ->
                                event.timestamp() != null ? event.timestamp().toEpochMilli() : recordTimestamp);

        env.fromSource(kafkaSource, watermarkStrategy, "transactions-inbound-source")
                .name("kafka-transactions-inbound")
                .uid("kafka-transactions-inbound")
                .keyBy(TransactionEvent::userId)
                .process(new VelocityKeyedProcessFunction(windowMs, velocity.maxTransactionsPerWindow()))
                .name("velocity-check")
                .uid("velocity-check")
                .sinkTo(kafkaSink)
                .name("kafka-fraud-alerts")
                .uid("kafka-fraud-alerts");

        env.execute("Fraud velocity detection");
    }

    /**
     * Same embedded local cluster as {@link org.apache.flink.streaming.api.environment.LocalStreamEnvironment},
     * but with an explicit user {@link ClassLoader} so Spring Boot / PropertiesLauncher can resolve Flink API
     * classes during JobMaster deserialization.
     */
    private static StreamExecutionEnvironment createLocalEnvironmentWithAppClassLoader(Configuration base) {
        if (!StreamExecutionEnvironment.areExplicitEnvironmentsAllowed()) {
            throw new IllegalStateException(
                    "Flink local environment is not allowed in this context (test/client factory active).");
        }
        Configuration cfg = new Configuration();
        cfg.addAll(base);
        cfg.set(DeploymentOptions.TARGET, "local");
        cfg.set(DeploymentOptions.ATTACHED, true);
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = FraudVelocityFlinkJob.class.getClassLoader();
        }
        return new StreamExecutionEnvironment(cfg, cl);
    }

    /**
     * @deprecated Prefer {@link FraudFlinkJobApplication} so {@code application.yml} is loaded.
     */
    @Deprecated
    public static void main(String[] args) throws Exception {
        FraudFlinkJobApplication.main(args);
    }

    private FraudVelocityFlinkJob() {
    }
}
