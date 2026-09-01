package com.kaffe.common.rabbit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kaffe.rabbit")
public class KaffeRabbitProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 5672;
    private String username = "kaffe";
    private String password = "kaffe";
    private String virtualHost = "/";
    private String exchange = "kaffe.events";
    private String deadLetterExchange = "kaffe.events.dlx";
    private long publisherConfirmTimeoutMillis = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public long getPublisherConfirmTimeoutMillis() {
        return publisherConfirmTimeoutMillis;
    }

    public void setPublisherConfirmTimeoutMillis(long publisherConfirmTimeoutMillis) {
        this.publisherConfirmTimeoutMillis = publisherConfirmTimeoutMillis;
    }
}
