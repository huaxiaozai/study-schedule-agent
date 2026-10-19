package com.studyplanner.executor;

import com.studyplanner.business.AgentAction;
import com.studyplanner.business.AgentActionRepository;
import com.studyplanner.enums.ActionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 动作失败审计服务。
 * 独立事务（REQUIRES_NEW）记录 FAILED 状态，保证 ActionExecutor 主事务回滚后，
 * agent_action 中的失败记录仍能持久化，便于排障与重试判断。
 */
@Service
public class ActionAuditService {

    private final AgentActionRepository agentActionRepository;

    public ActionAuditService(AgentActionRepository agentActionRepository) {
        this.agentActionRepository = agentActionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, ActionBatchRequest request, String errorCode) {
        for (ActionCommand a : request.getActions()) {
            AgentAction action = agentActionRepository
                    .findByUserIdAndOperationKey(userId, a.getOperationKey())
                    .orElseGet(() -> {
                        AgentAction x = new AgentAction();
                        x.setUserId(userId);
                        x.setOperationKey(a.getOperationKey());
                        x.setType(a.getType());
                        return x;
                    });
            action.setStatus(ActionStatus.FAILED);
            action.setBaseRevision(request.getBaseRevision());
            action.setAppliedRevision(request.getBaseRevision());
            action.setErrorCode(errorCode);
            action.setAfterJson(null);
            agentActionRepository.save(action);
        }
    }
}
