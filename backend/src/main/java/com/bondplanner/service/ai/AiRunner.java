package com.bondplanner.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRunner {

    private final AiExecutor executor;

    @Async
    public void run(Long analysisId) {
        executor.execute(analysisId);
    }
}
