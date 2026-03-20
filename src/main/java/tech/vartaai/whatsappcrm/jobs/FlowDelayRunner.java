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

        List<FlowExecution> timedOut = flowExecutionRepository.findTimedOutExecutions(now);
        if (!timedOut.isEmpty()) {
            log.info("FLOW_TIMEOUT_CHECK found={} timedOut executions to process", timedOut.size());
        }
        for (FlowExecution exec : timedOut) {
            try {
                log.info("FLOW_TIMEOUT_PROCESS execId={} contactId={} currentNode={} resumeAfter={}",
                        exec.getId(), exec.getContactId(), exec.getCurrentNodeId(), exec.getResumeAfter());
                flowEngineService.handleTimedOutExecution(exec);
            } catch (Exception e) {
                log.error("FLOW_TIMEOUT_ERROR execId={} err={}", exec.getId(), e.getMessage(), e);
            }
        }
    }
}
