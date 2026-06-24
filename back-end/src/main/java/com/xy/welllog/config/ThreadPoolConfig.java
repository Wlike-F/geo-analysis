package com.xy.welllog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean("logFileExecutor")
    public Executor logFileExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据服务器性能配置，此处为保守值
        executor.setCorePoolSize(4);
        // 最大线程数：不宜过大，防止CPU和内存被打满
        executor.setMaxPoolSize(10);
        // 队列大小：允许积压的文件解析任务数
        executor.setQueueCapacity(50);
        // 线程池中名称前缀
        executor.setThreadNamePrefix("log-parse-");
        // 拒绝策略：由调用线程（提交任务的线程）处理该任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
