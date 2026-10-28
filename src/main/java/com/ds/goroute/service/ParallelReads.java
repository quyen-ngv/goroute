package com.ds.goroute.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Runs a request's independent reads at the same time and hands back their results in order.
 * The last read runs on the calling thread, so n reads cost n - 1 pool threads.
 *
 * <p>Only for reads that need no shared transaction: each read opens its own connection (and
 * its own {@code @Transactional} when it calls a service). A read already running on the pool
 * runs its own reads one after another, so nested fan-out can never wait on itself.
 *
 * <p>Inside a transaction everything runs on the caller, one after another. The caller already
 * holds a connection there; if every pooled connection were held by callers waiting on forked
 * reads, the forks could never get one and the requests would stall until the pool timeout.
 */
@Component
public class ParallelReads {

    public static final String EXECUTOR = "requestReadExecutor";
    public static final String THREAD_PREFIX = "goroute-read-";

    private final ThreadPoolTaskExecutor executor;

    public ParallelReads(@Qualifier(EXECUTOR) ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    /** Runs every read on the caller, one after another: for unit tests. */
    public static ParallelReads sequential() {
        return new ParallelReads(null);
    }

    public <T> List<T> all(List<Supplier<T>> reads) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return sequentially(reads);
        }
        return run(reads);
    }

    /**
     * Like {@link #all} for work that never touches the database (search index reads), which
     * may therefore fork inside a transaction too: it cannot hold or wait for a connection.
     */
    public <T> List<T> allInMemory(List<Supplier<T>> reads) {
        return run(reads);
    }

    private static <T> List<T> sequentially(List<Supplier<T>> reads) {
        List<T> results = new ArrayList<>(reads.size());
        reads.forEach(read -> results.add(read.get()));
        return results;
    }

    private <T> List<T> run(List<Supplier<T>> reads) {
        if (executor == null || reads.size() <= 1
                || Thread.currentThread().getName().startsWith(THREAD_PREFIX)) {
            return sequentially(reads);
        }
        List<CompletableFuture<T>> forked = new ArrayList<>(reads.size() - 1);
        for (Supplier<T> read : reads.subList(0, reads.size() - 1)) {
            forked.add(fork(read));
        }
        T last = reads.get(reads.size() - 1).get();
        List<T> results = new ArrayList<>(reads.size());
        for (CompletableFuture<T> future : forked) {
            results.add(join(future));
        }
        results.add(last);
        return results;
    }

    private <T> CompletableFuture<T> fork(Supplier<T> read) {
        try {
            return CompletableFuture.supplyAsync(read, executor);
        } catch (TaskRejectedException rejected) {
            // The executor is shutting down: read here instead.
            return CompletableFuture.completedFuture(read.get());
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw wrapped;
        }
    }
}
