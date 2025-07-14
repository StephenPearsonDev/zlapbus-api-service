package dev.stephenpearson.ztm_tracker_api_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean("pollerExecutor")
    public TaskExecutor pollerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //1 should be enough for getting data from redis
        executor.setCorePoolSize(1);        
        executor.setMaxPoolSize(2);        
        executor.setQueueCapacity(10);     
        executor.setThreadNamePrefix("vehicle-poller-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}