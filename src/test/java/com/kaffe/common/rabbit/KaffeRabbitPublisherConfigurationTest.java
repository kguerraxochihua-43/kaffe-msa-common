package com.kaffe.common.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class KaffeRabbitPublisherConfigurationTest {

    @Test
    void enablesCorrelatedConfirmsReturnsAndMandatoryRouting() {
        KaffeRabbitAutoConfiguration configuration = new KaffeRabbitAutoConfiguration();
        KaffeRabbitProperties properties = new KaffeRabbitProperties();
        CachingConnectionFactory factory = (CachingConnectionFactory)
                configuration.kaffeRabbitConnectionFactory(properties);
        try {
            RabbitTemplate template = configuration.rabbitTemplate(
                    factory,
                    configuration.kaffeRabbitMessageConverter(new ObjectMapper()));

            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isPublisherReturns()).isTrue();
            assertThat(template.isMandatoryFor(new Message(new byte[0]))).isTrue();
        } finally {
            factory.destroy();
        }
    }
}
