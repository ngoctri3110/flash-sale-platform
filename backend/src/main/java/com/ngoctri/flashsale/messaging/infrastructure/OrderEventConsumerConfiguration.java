package com.ngoctri.flashsale.messaging.infrastructure;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.slf4j.LoggerFactory;

@Configuration
class OrderEventConsumerConfiguration {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(OrderEventConsumerConfiguration.class);

    @Bean
    DefaultErrorHandler orderEventErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
        errorHandler.addNotRetryableExceptions(UnsupportedOrderEventException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                LOGGER.warn("Retrying Order event at delivery attempt {}", deliveryAttempt, exception));
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> orderEventsKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler orderEventErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(orderEventErrorHandler);
        return factory;
    }
}
