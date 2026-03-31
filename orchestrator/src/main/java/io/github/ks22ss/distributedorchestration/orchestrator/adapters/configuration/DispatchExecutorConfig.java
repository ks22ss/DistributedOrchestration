package io.github.ks22ss.distributedorchestration.orchestrator.adapters.configuration;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DispatchExecutorConfig {

    @Bean(name = "dispatchExecutor")
    public Executor dispatchExecutor(
            @Value("${orchestration.dispatch.parallelism:8}") int parallelism,
            @Value("${orchestration.dispatch.parallel-queue-capacity:256}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(
                runnable -> {
                    Map<String, String> context = MDC.getCopyOfContextMap();
                    return () -> {
                        if (context != null) {
                            MDC.setContextMap(context);
                        }
                        try {
                            runnable.run();
                        } finally {
                            MDC.clear();
                        }
                    };
                });
        executor.initialize();
        return executor;
    }
}

