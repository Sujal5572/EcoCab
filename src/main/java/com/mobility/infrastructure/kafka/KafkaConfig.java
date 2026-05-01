package com.mobility.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * FIX: Removed top-level @ConditionalOnProperty from the class.
 *      The KafkaTemplate and consumer factory beans are still conditional,
 *      but the topic name constants are always available.
 *
 *      The entire class is only active when spring.kafka.enabled=true.
 *      When disabled, KafkaAutoConfiguration is excluded in application.yml,
 *      so no Kafka beans are created — and DemandEventProducer handles
 *      the null KafkaTemplate gracefully via @Autowired(required=false).
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    public static final String TOPIC_DEMAND_CREATED   = "demand.created";
    public static final String TOPIC_DEMAND_CANCELLED = "demand.cancelled";
    public static final String TOPIC_DEMAND_EXPIRED   = "demand.expired";
    public static final String TOPIC_DEMAND_UPDATED   = "demand.updated";
    public static final String GROUP_DEMAND_SERVICE   = "demand-service-group";

    @Bean
    public NewTopic demandCreatedTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_CREATED)
                .partitions(6).replicas(1)                            // FIX: replicas=1 for local single-broker
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }

    @Bean
    public NewTopic demandCancelledTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_CANCELLED)
                .partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic demandExpiredTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_EXPIRED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic demandUpdatedTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_UPDATED)
                .partitions(6).replicas(1).build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props) {
        Map<String, Object> config = new HashMap<>(props.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties props) {
        Map<String, Object> config = new HashMap<>(props.buildConsumerProperties(null));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobility.*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf,
            KafkaTemplate<String, Object> kt) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kt),
                new FixedBackOff(1000L, 2)));
        return factory;
    }
}