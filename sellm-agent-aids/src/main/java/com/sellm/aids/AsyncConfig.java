package com.sellm.aids;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 文生素材异步执行配置(Spec §5.3 长任务异步范式)。
 * 生产用线程池 TaskExecutor;测试可用 @Primary SyncTaskExecutor 覆盖为同步,使任务在 POST 返回前已达终态。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("assetTaskExecutor")
    public TaskExecutor assetTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("asset-gen-");
        executor.initialize();
        return executor;
    }
}
