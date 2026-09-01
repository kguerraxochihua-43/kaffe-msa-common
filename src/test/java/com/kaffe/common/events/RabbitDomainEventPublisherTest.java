package com.kaffe.common.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RabbitDomainEventPublisherTest {

    private static final DomainEventEnvelope EVENT = new DomainEventEnvelope(
            "28c50b0b-f81f-49eb-89dd-33f3278c72e4",
            "orders.payment_requested",
            "kaffe-msa-orders",
            1,
            OffsetDateTime.parse("2026-09-01T12:00:00-06:00"),
            "trace-1",
            "order",
            "141",
            Map.of("orderId", 141),
            Map.of()
    );

    @Test
    void returnsOnlyAfterRabbitAcknowledgesThePublish() {
        RabbitTemplate template = confirmingTemplate(true, null);
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(template, 1_000);

        assertThatCode(() -> publisher.publish("kaffe.events", EVENT.eventType(), EVENT))
                .doesNotThrowAnyException();
    }

    @Test
    void failsClosedWhenRabbitRejectsThePublish() {
        RabbitTemplate template = confirmingTemplate(false, "broker unavailable");
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(template, 1_000);

        assertThatThrownBy(() -> publisher.publish("kaffe.events", EVENT.eventType(), EVENT))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("rejected event");
    }

    @Test
    void failsClosedWhenRabbitDoesNotConfirmInTime() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(template, 250);

        assertThatThrownBy(() -> publisher.publish("kaffe.events", EVENT.eventType(), EVENT))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("did not confirm");
    }

    @Test
    void failsClosedWhenTheExchangeCannotRouteTheEvent() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0]), 312, "NO_ROUTE", "kaffe.events", EVENT.eventType()));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(
                eq("kaffe.events"),
                eq(EVENT.eventType()),
                eq(EVENT),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(template, 1_000);

        assertThatThrownBy(() -> publisher.publish("kaffe.events", EVENT.eventType(), EVENT))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("could not route event");
    }

    @Test
    void usesANewBrokerCorrelationForEveryRetryWhileKeepingTheLogicalEventStable() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        List<String> correlationIds = new ArrayList<>();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlationIds.add(correlation.getId());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(
                eq("kaffe.events"),
                eq(EVENT.eventType()),
                eq(EVENT),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
        RabbitDomainEventPublisher publisher = new RabbitDomainEventPublisher(template, 1_000);

        publisher.publish("kaffe.events", EVENT.eventType(), EVENT);
        publisher.publish("kaffe.events", EVENT.eventType(), EVENT);

        assertThat(correlationIds).hasSize(2);
        assertThat(correlationIds.get(0)).startsWith(EVENT.eventId() + ":");
        assertThat(correlationIds.get(1)).startsWith(EVENT.eventId() + ":");
        assertThat(correlationIds.get(0)).isNotEqualTo(correlationIds.get(1));
    }

    private RabbitTemplate confirmingTemplate(boolean ack, String reason) {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(template).convertAndSend(
                eq("kaffe.events"),
                eq(EVENT.eventType()),
                eq(EVENT),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));
        return template;
    }
}
