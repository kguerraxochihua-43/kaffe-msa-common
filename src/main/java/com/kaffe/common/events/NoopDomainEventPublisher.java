package com.kaffe.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopDomainEventPublisher.class);

    @Override
    public void publish(String exchange, String routingKey, DomainEventEnvelope envelope) {
        log.debug("Domain event publisher disabled. Event {} was not published to {}:{}",
                envelope.eventId(), exchange, routingKey);
    }
}
