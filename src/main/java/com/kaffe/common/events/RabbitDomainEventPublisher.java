package com.kaffe.common.events;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class RabbitDomainEventPublisher implements DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String exchange, String routingKey, DomainEventEnvelope envelope) {
        rabbitTemplate.convertAndSend(exchange, routingKey, envelope, message -> {
            message.getMessageProperties().setMessageId(envelope.eventId());
            message.getMessageProperties().setType(envelope.eventType());
            message.getMessageProperties().setHeader("eventId", envelope.eventId());
            message.getMessageProperties().setHeader("eventType", envelope.eventType());
            message.getMessageProperties().setHeader("sourceService", envelope.sourceService());
            message.getMessageProperties().setHeader("schemaVersion", envelope.schemaVersion());
            message.getMessageProperties().setHeader("traceId", envelope.traceId());
            return message;
        });
    }
}
