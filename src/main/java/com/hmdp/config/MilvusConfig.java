package com.hmdp.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MilvusConfig {

    @Resource
    private MilvusProperties milvusProperties;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("Init Milvus client, host={}, port={}",
                milvusProperties.getHost(), milvusProperties.getPort());

        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(10, TimeUnit.SECONDS)
                .build();

        return new MilvusServiceClient(connectParam);
    }
}