package com.ngoctri.flashsale.messaging.infrastructure;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox.publisher", name = "enabled", havingValue = "true")
public class OrderCreatedOutboxPublisher {

    private static final String TOPIC = "order-events.v1";

    private final JdbcOutboxEventStore outboxEventStore;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final Duration retryDelay;
    private final Duration leaseDuration;

    OrderCreatedOutboxPublisher(
            JdbcOutboxEventStore outboxEventStore,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.outbox.publisher.batch-size}") int batchSize,
            @Value("${app.outbox.publisher.retry-delay}") Duration retryDelay,
            @Value("${app.outbox.publisher.lease-duration}") Duration leaseDuration) {
        this.outboxEventStore = outboxEventStore;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
        this.leaseDuration = leaseDuration;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.retry-delay}")
    public void publishPendingEvents() {
        var leaseOwner = UUID.randomUUID();
        for (var event : outboxEventStore.claimPending(batchSize, leaseOwner, leaseDuration)) {
            try {
                kafkaTemplate.send(TOPIC, Long.toString(event.aggregateId()), event.payload())
                        .get(10, TimeUnit.SECONDS);
                outboxEventStore.markPublished(event, leaseOwner);
            } catch (Exception exception) {
                outboxEventStore.recordFailure(event, leaseOwner, exception, retryDelay);
            }
        }
    }
}
