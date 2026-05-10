package com.example.ordermgmt.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ASYNC CONFIGURATION
 *
 * CONCEPT: Configures the thread pool used by @Async methods
 *
 * INTERVIEW POINTS:
 *
 * Without this config, Spring uses SimpleAsyncTaskExecutor Which:
 *      _ creates new thread per task (no reuse)
 *      _ no pooling
 *      _ no queue control
 *      _ dangerous in production (OutOfMemoryError under heavy load)
 *
 * ✅ What this config improves
 *
 * | Feature                     | Why useful                |
 * | --------------------------- | ------------------------- |
 * | Thread pool                 | Reuse threads efficiently |
 * | Queue capacity              | Prevent unlimited threads |
 * | Named threads               | Easier debugging          |
 * | Rejection policy            | Back-pressure handling    |
 * | MDC propagation             | Keep request trace IDs    |
 * | SecurityContext propagation | Preserve logged-in user   |
 * | Graceful shutdown           | Finish tasks safely       |
 * | Async exception handling    | Prevent silent failures   |
 *
 * ThreadPoolTaskExecutor:
 *    corePoolSize = threads always alive (even idle)
 *    maxPoolSize = max threads when queue is full
 *    queueCapacity = tasks queued when all core threads busy
 *
 * Flow when task arrives:
 *    1. Core thread free? --> assign immediately
 *    2. Core threads busy? --> add to queue
 *    3. Queue full? --> create new thread (up to max)
 *    4. Max threads reached?  --> RejectedExecutionHandler fires
 *
 * CallerRunsPolicy:
 *    When pool and queue both full, the CALLER thread runs the task.
 *    This provides natural back-pressure -- caller slows down.
 *    No task loss. Correct for production
 *
 * CRITICAL PROBLEMS IN ASYNC:
 *  1. SecurityContext is ThreadLocal -- lost in new thread
 *     Solution: TaskDecorator copies SecurityContext to async thread
 *
 *  2. MDC (correlation ID) is ThreadLocal -- lost in new thread
 *     Solution: TaskDecorator copies MDC map to async thread
 *
 *  3. @Transactional does NOT propagate to @Async thread
 *     Transaction is bound to ThreadLocal -- new thread has none
 *     Solution: manage @Transactional separately in async method
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AppProperties appProperties;

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        AppProperties.Async asyncProps = appProperties.getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // min threads always running (even when idle)
        executor.setCorePoolSize(asyncProps.getCorePoolSize());

        // max threads created when queue is full
        executor.setMaxPoolSize(asyncProps.getQueueCapacity());

        // queue tasks when all core threads are busy
        executor.setQueueCapacity(asyncProps.getQueueCapacity());

        // thread name prefix -- visible in logs, thread dumps, profilers
        // Without naming: pool-1-thread-1 (impossible to diagnose)
        // With naming:    order-async-1   (immediately identifiable)
        executor.setThreadNamePrefix(
                asyncProps.getThreadNamePrefix());

        // when pool + queue both full
        // CallerRunsPolicy = caller thread executes task (back-pressure)
        // AbortPolicy      = throws RejectedExecutionException (default)
        // DiscardPolicy    = silently drops task (data loss)
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy());

        // wait for running tasks to finish on shutdown (graceful)
        // without this, tasks are killed on app shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true); // ✔ graceful shutdown

        // max seconds to wait for running tasks on shutdown
        executor.setAwaitTerminationSeconds(60);

        // TaskDecorator: runs AROUND each async task
        // Used to propagate ThreadLocal values to the async thread
        executor.setTaskDecorator(runnable -> {
            // capture SecurityContext from caller thread
            SecurityContext securityContext =
                    SecurityContextHolder.getContext();  // Needed when async tasks require authenticated user info.

            // capture MDC from caller thread
            Map<String, String> mdcContext =
                    MDC.getCopyOfContextMap();

            return () -> {
                try {
                    // set SecurityContext in async thread
                    SecurityContextHolder.setContext(securityContext);

                    // set MDC in async thread
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }

                    // run actual task
                    runnable.run();

                } finally {
                    // ALWAYS clean up ThreadLocal in async thread
                    SecurityContextHolder.clearContext();
                    MDC.clear();
                }
            };
        });

        executor.initialize();
        log.info("Async ThreadPoolTaskExecutor initialized: " +
                        "core={}, max={}, queue={}",
                asyncProps.getCorePoolSize(),
                asyncProps.getMaxPoolSize(),
                asyncProps.getQueueCapacity());

        return executor;
    }

    /**
     * ASYNC UNCAUGHT EXCEPTION HANDLER
     *
     * Handles exceptions thrown from @Async VOID methods.
     *
     * INTERVIEW POINT:
     * @Async void methods -- exceptions are SILENTLY SWALLOWED
     * by default. They are passed to this handler.
     * Without this, exceptions disappear without any trace.
     *
     * @Async CompletableFuture -- handle via .exceptionally()
     * This handler does NOT apply to CompletableFuture methods.
     */
    @Override
    public AsyncUncaughtExceptionHandler
    getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error(
                    "Uncaught exception in @Async method: {}.{}() | " +
                            "Exception: {} | Message: {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex
            );
            // In production: send alert, write to dead letter queue, etc.
        };
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}

