package tech.vartaai.whatsappcrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.vartaai.whatsappcrm.entity.AutoReplyRule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutoReplyRuleRepository extends JpaRepository<AutoReplyRule, UUID> {
    List<AutoReplyRule> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

    List<AutoReplyRule> findByClient_IdAndActiveTrueOrderByCreatedAtAsc(UUID clientId);

    Optional<AutoReplyRule> findByClient_IdAndRuleTypeAndActiveTrue(UUID clientId, AutoReplyRule.RuleType ruleType);
}
