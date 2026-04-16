package com.ds.goroute.config.common;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Value("${thread.executor.concurrency}")
    String threadExecutorConcurrency;


    @Bean("cacheThreadPool")
    public Executor getExecutor() {
        return Executors.newCachedThreadPool();
    }
}