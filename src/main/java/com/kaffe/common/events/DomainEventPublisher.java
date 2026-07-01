package com.kaffe.common.events;

public interface DomainEventPublisher {
    void publish(String exchange, String routingKey, DomainEventEnvelope envelope);
}
