package com.mobility.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
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
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

// infrastructure/kafka/KafkaConfig.java
@Configuration
public class KafkaConfig {

    public static final String TOPIC_DEMAND_CREATED   = "demand.created";
    public static final String TOPIC_DEMAND_CANCELLED = "demand.cancelled";
    public static final String TOPIC_DEMAND_EXPIRED   = "demand.expired";
    public static final String TOPIC_DEMAND_UPDATED   = "demand.updated";  // outbound to drivers

    public static final String GROUP_DEMAND_SERVICE   = "demand-service-group";

    @Bean
    public NewTopic demandCreatedTopic() {
        // 6 partitions — one per corridor initially, scales horizontally
        return TopicBuilder.name(TOPIC_DEMAND_CREATED)
                .partitions(6).replicas(2)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000") // 24h
                .build();
    }

    @Bean
    public NewTopic demandCancelledTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_CANCELLED)
                .partitions(6).replicas(2).build();
    }

    @Bean
    public NewTopic demandExpiredTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_EXPIRED)
                .partitions(3).replicas(2).build();
    }

    @Bean
    public NewTopic demandUpdatedTopic() {
        return TopicBuilder.name(TOPIC_DEMAND_UPDATED)
                .partitions(6).replicas(2).build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props) {
        Map<String, Object> config = new HashMap<>(props.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Idempotent producer — exactly-once semantics for demand counters
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties props) {
        Map<String, Object> config = new HashMap<>(props.buildConsumerProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobility.*");
        // Start from latest — we only care about real-time events
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // Manual commit — we commit only after Redis write succeeds
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // 3 concurrent consumers per partition group
        factory.setConcurrency(3);
        // Dead letter topic on 3 consecutive failures
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate(producerFactory(null))),
                new FixedBackOff(1000L, 2)));
        return factory;
    }
}
