package com.kaffe.common.events;

import java.time.OffsetDateTime;
import java.util.Map;

public record DomainEventEnvelope(
        String eventId,
        String eventType,
        String sourceService,
        Integer schemaVersion,
        OffsetDateTime occurredAt,
        String traceId,
        String aggregateType,
        String aggregateId,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {
}
