package com.kaffe.common.rabbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaffe.common.events.DomainEventPublisher;
import com.kaffe.common.events.NoopDomainEventPublisher;
import com.kaffe.common.events.RabbitDomainEventPublisher;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(KaffeRabbitProperties.class)
public class KaffeRabbitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public ConnectionFactory kaffeRabbitConnectionFactory(KaffeRabbitProperties properties) {
        CachingConnectionFactory factory = new CachingConnectionFactory(properties.getHost(), properties.getPort());
        factory.setUsername(properties.getUsername());
        factory.setPassword(properties.getPassword());
        factory.setVirtualHost(properties.getVirtualHost());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public MessageConverter kaffeRabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public DirectExchange kaffeEventsExchange(KaffeRabbitProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public DirectExchange kaffeDeadLetterExchange(KaffeRabbitProperties properties) {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "true")
    public DomainEventPublisher rabbitDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        return new RabbitDomainEventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.rabbit", name = "enabled", havingValue = "false", matchIfMissing = true)
    public DomainEventPublisher noopDomainEventPublisher() {
        return new NoopDomainEventPublisher();
    }
}
