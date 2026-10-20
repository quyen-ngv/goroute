package com.ds.goroute.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Declaring any {@link Executor} bean switches off Spring Boot's auto-configured
 * {@code applicationTaskExecutor} (it is {@code @ConditionalOnMissingBean(Executor.class)}).
 * With several {@code Executor} beans and none of them primary, an unqualified
 * {@code @Async} could not resolve one and silently fell back to
 * {@code SimpleAsyncTaskExecutor}, which starts a brand new thread per invocation and
 * applies no backpressure at all. {@link #getAsyncExecutor()} below closes that gap: every
 * unqualified {@code @Async} now runs on the bounded pool.
 *
 * <p>All pools here use {@code CallerRunsPolicy} so a full queue slows the caller down
 * instead of dropping work, which is what the unbounded fallback effectively guaranteed.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * The pool every {@code @Async} without an explicit qualifier runs on.
     */
    @Bean(name = "applicationTaskExecutor")
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(24);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("goroute-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Application async executor initialized: core=8, max=24, queue=500");
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    /**
     * Without this, an exception thrown by a {@code void @Async} method is lost.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable error, Method method, Object... params) -> {
            log.error("Uncaught exception in async method {}.{}",
                    method.getDeclaringClass().getSimpleName(), method.getName(), error);
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(error, method, params);
        };
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        // Back-pressure rather than a silent drop: a notification that is late is
        // recoverable, one that was thrown away is not.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Notification executor initialized: core=5, max=10, queue=100");
        return executor;
    }

    /**
     * Telling an author's followers about a new post (FollowerFanoutListener). Its own small pool so
     * that one author with ten thousand followers walks them chunk by chunk without taking threads
     * from trip, chat and booking notifications.
     *
     * <p>Never CallerRuns: the caller is the after-commit hook of a check-in or trip update, and
     * running a fan-out there would hold that HTTP request for the whole walk. A full queue drops
     * the fan-out with an error log instead; the post itself is already saved.
     */
    @Bean(name = "followerFanoutExecutor")
    public Executor followerFanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("follower-fanout-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.error("FOLLOWER_FANOUT_REJECTED queue full ({} queued); a post's followers were not told",
                        pool.getQueue().size()));
        executor.initialize();
        log.info("Follower fan-out executor initialized: core=1, max=2, queue=500");
        return executor;
    }

    @Bean(name = "placeImportJobExecutor")
    public Executor placeImportJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("place-import-");
        // Admin all-users runs must apply backpressure instead of silently
        // dropping a user's import task when the queue is full.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("Place import job executor initialized: core=2, max=4, queue=50");
        return executor;
    }
}
