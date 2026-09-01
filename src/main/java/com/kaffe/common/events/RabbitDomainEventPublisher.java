package com.kaffe.common.events;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RabbitDomainEventPublisher implements DomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMillis;

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, 10_000);
    }

    public RabbitDomainEventPublisher(RabbitTemplate rabbitTemplate, long confirmTimeoutMillis) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMillis = Math.max(250, confirmTimeoutMillis);
    }

    @Override
    public void publish(String exchange, String routingKey, DomainEventEnvelope envelope) {
        CorrelationData correlation = new CorrelationData(envelope.eventId());
        rabbitTemplate.convertAndSend(exchange, routingKey, envelope, message -> {
            message.getMessageProperties().setMessageId(envelope.eventId());
            message.getMessageProperties().setType(envelope.eventType());
            message.getMessageProperties().setHeader("eventId", envelope.eventId());
            message.getMessageProperties().setHeader("eventType", envelope.eventType());
            message.getMessageProperties().setHeader("sourceService", envelope.sourceService());
            message.getMessageProperties().setHeader("schemaVersion", envelope.schemaVersion());
            message.getMessageProperties().setHeader("traceId", envelope.traceId());
            return message;
        }, correlation);
        awaitBrokerConfirmation(exchange, routingKey, envelope, correlation);
    }

    private void awaitBrokerConfirmation(
            String exchange,
            String routingKey,
            DomainEventEnvelope envelope,
            CorrelationData correlation
    ) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new AmqpException("RabbitMQ rejected event " + envelope.eventId()
                        + ": " + safeReason(confirm.getReason()));
            }
            if (correlation.getReturned() != null) {
                throw new AmqpException("RabbitMQ could not route event " + envelope.eventId()
                        + " to " + exchange + "/" + routingKey);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while confirming RabbitMQ event "
                    + envelope.eventId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("RabbitMQ did not confirm event " + envelope.eventId(), ex);
        }
    }

    private String safeReason(String value) {
        if (value == null || value.isBlank()) return "no reason supplied";
        String normalized = value.replaceAll("[\\r\\n\\t]", " ");
        return normalized.substring(0, Math.min(normalized.length(), 160));
    }
}
