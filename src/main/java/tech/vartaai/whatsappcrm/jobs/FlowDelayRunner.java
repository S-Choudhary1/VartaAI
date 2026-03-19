package tech.vartaai.whatsappcrm.jobs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.vartaai.whatsappcrm.entity.FlowExecution;
import tech.vartaai.whatsappcrm.repository.FlowExecutionRepository;
import tech.vartaai.whatsappcrm.service.FlowEngineService;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Slf4j
public class FlowDelayRunner {

    private final FlowExecutionRepository flowExecutionRepository;
    private final FlowEngineService flowEngineService;

    public FlowDelayRunner(FlowExecutionRepository flowExecutionRepository,
                           FlowEngineService flowEngineService) {
        this.flowExecutionRepository = flowExecutionRepository;
        this.flowEngineService = flowEngineService;
    }

    @Scheduled(fixedDelay = 15000)
    public void processTimeouts() {
        OffsetDateTime now = OffsetDateTime.now();

        // Process WAITING executions that have timed out
        List<FlowExecution> timedOut = flowExecutionRepository.findTimedOutExecutions(now);
        for (FlowExecution exec : timedOut) {
            try {
                log.info("FLOW_TIMEOUT execId={}", exec.getId());
                flowEngineService.handleTimedOutExecution(exec);
            } catch (Exception e) {
                log.error("FLOW_TIMEOUT_ERROR execId={} err={}", exec.getId(), e.getMessage());
            }
        }
    }
}
