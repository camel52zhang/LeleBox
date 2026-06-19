package com.fongmi.android.tv.utils;

import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Task {

    // 根据 CPU 核心数动态调整线程池大小
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT, 4));
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int LARGE_POOL_SIZE = Math.max(10, CPU_COUNT * 4);
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(
            createThreadPool(CORE_POOL_SIZE, MAX_POOL_SIZE, "task-pool"));
    private static final ListeningExecutorService largeExecutor = MoreExecutors.listeningDecorator(
            createFixedThreadPool(LARGE_POOL_SIZE, "large-pool"));
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            createThreadFactory("scheduler", Process.THREAD_PRIORITY_BACKGROUND));

    public static ListeningExecutorService executor() {
        return executor;
    }

    public static ListeningExecutorService largeExecutor() {
        return largeExecutor;
    }

    public static ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public static Future<?> submitLarge(Runnable task) {
        return largeExecutor.submit(task);
    }

    public static void execute(Runnable task) {
        executor.execute(task);
    }

    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduler.schedule(task, delay, unit);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess) {
        return callback(onSuccess, null);
    }

    public static <T> FutureCallback<T> callback(Consumer<T> onSuccess, @Nullable Consumer<Throwable> onFailure) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(@NonNull Throwable error) {
                if (onFailure != null) onFailure.accept(error);
            }
        };
    }

    /**
     * 创建动态大小的线程池，适合执行短时间的任务
     */
    private static ExecutorService createThreadPool(int coreSize, int maxSize, String name) {
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                createThreadFactory(name, Process.THREAD_PRIORITY_BACKGROUND),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建固定大小的线程池，适合执行大量并发任务
     */
    private static ExecutorService createFixedThreadPool(int size, String name) {
        return new ThreadPoolExecutor(
                size,
                size,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128),
                createThreadFactory(name, Process.THREAD_PRIORITY_BACKGROUND),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建线程工厂，设置线程名和优先级
     */
    private static ThreadFactory createThreadFactory(String name, int priority) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = new Thread(r, name + "-" + count.getAndIncrement());
                thread.setPriority(priority);
                return thread;
            }
        };
    }
}
