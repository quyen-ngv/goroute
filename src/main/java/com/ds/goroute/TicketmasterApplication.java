package com.ds.goroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.util.TimeZone;

@SpringBootApplication(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan("com.ds.goroute")
public class TicketmasterApplication {

    public static void main(String[] args) {
        // Every TIMESTAMP column holds a UTC wall clock. Pin the JVM (and with it the JDBC session)
        // to UTC so LocalDateTime.now() and NOW() agree on any host, including dev machines.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TicketmasterApplication.class, args);
    }

}
