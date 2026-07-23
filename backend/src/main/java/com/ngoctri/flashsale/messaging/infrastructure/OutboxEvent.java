package com.ngoctri.flashsale.messaging.infrastructure;

import java.util.UUID;

record OutboxEvent(UUID id, long aggregateId, String payload) {
}
