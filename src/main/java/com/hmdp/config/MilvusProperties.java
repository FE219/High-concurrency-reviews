package com.hmdp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.ai.vectorstore.milvus.client")
public class MilvusProperties {

    private String host;
    private int port;
    private String faqCollection;
    private String blogCollection;

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

    public String getFaqCollection() {
        return faqCollection;
    }

    public void setFaqCollection(String faqCollection) {
        this.faqCollection = faqCollection;
    }

    public String getBlogCollection() {
        return blogCollection;
    }

    public void setBlogCollection(String blogCollection) {
        this.blogCollection = blogCollection;
    }
}