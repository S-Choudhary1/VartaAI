package tech.vartaai.whatsappcrm.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.vartaai.whatsappcrm.dto.AutoReplyRuleRequest;
import tech.vartaai.whatsappcrm.dto.AutoReplyRuleResponse;
import tech.vartaai.whatsappcrm.dto.AutoReplyFeatureFlagResponse;
import tech.vartaai.whatsappcrm.dto.SendMessageRequest;
import tech.vartaai.whatsappcrm.entity.AutoReplyRule;
import tech.vartaai.whatsappcrm.entity.Client;
import tech.vartaai.whatsappcrm.entity.Template;
import tech.vartaai.whatsappcrm.repository.AutoReplyRuleRepository;
import tech.vartaai.whatsappcrm.repository.ClientRepository;
import tech.vartaai.whatsappcrm.repository.TemplateRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class AutoReplyService {

    private final AutoReplyRuleRepository autoReplyRuleRepository;
    private final ClientRepository clientRepository;
    private final TemplateRepository templateRepository;
    private final MessageService messageService;

    public AutoReplyService(AutoReplyRuleRepository autoReplyRuleRepository,
                            ClientRepository clientRepository,
                            TemplateRepository templateRepository,
                            MessageService messageService) {
        this.autoReplyRuleRepository = autoReplyRuleRepository;
        this.clientRepository = clientRepository;
        this.templateRepository = templateRepository;
        this.messageService = messageService;
    }

    @Transactional
    public AutoReplyRuleResponse createRule(AutoReplyRuleRequest request, UUID clientId) {
        AutoReplyRule rule = new AutoReplyRule();
        applyRequest(rule, request, clientId);
        AutoReplyRule saved = autoReplyRuleRepository.save(rule);
        return toResponse(saved);
    }

    public List<AutoReplyRuleResponse> getAllRules(UUID clientId) {
        return autoReplyRuleRepository.findByClient_IdOrderByCreatedAtDesc(clientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AutoReplyRuleResponse getRuleById(UUID id, UUID clientId) {
        Objects.requireNonNull(id, "Rule id is required");
        return toResponse(getRuleEntity(id, clientId));
    }

    @Transactional
    public AutoReplyRuleResponse updateRule(UUID id, AutoReplyRuleRequest request, UUID clientId) {
        Objects.requireNonNull(id, "Rule id is required");
        AutoReplyRule existing = getRuleEntity(id, clientId);
        applyRequest(existing, request, clientId);
        AutoReplyRule saved = autoReplyRuleRepository.save(Objects.requireNonNull(existing));
        return toResponse(saved);
    }

    @Transactional
    public AutoReplyRuleResponse updateStatus(UUID id, boolean active, UUID clientId) {
        Objects.requireNonNull(id, "Rule id is required");
        AutoReplyRule existing = getRuleEntity(id, clientId);
        if (active && existing.getRuleType() == AutoReplyRule.RuleType.DEFAULT) {
            validateSingleActiveDefault(clientId, id);
        }
        existing.setActive(active);
        AutoReplyRule saved = autoReplyRuleRepository.save(existing);
        return toResponse(saved);
    }

    @Transactional
    public void deleteRule(UUID id, UUID clientId) {
        Objects.requireNonNull(id, "Rule id is required");
        AutoReplyRule existing = getRuleEntity(id, clientId);
        autoReplyRuleRepository.delete(Objects.requireNonNull(existing));
    }

    public AutoReplyFeatureFlagResponse getFeatureFlag(UUID clientId) {
        Client client = getClient(clientId);
        return new AutoReplyFeatureFlagResponse(client.getId(), client.isAutoReplyEnabled());
    }

    @Transactional
    public AutoReplyFeatureFlagResponse updateFeatureFlag(UUID clientId, boolean enabled) {
        Client client = getClient(clientId);
        client.setAutoReplyEnabled(enabled);
        Client saved = clientRepository.save(client);
        return new AutoReplyFeatureFlagResponse(saved.getId(), saved.isAutoReplyEnabled());
    }

    public void processIncoming(Client client, String toPhone, String type, JsonNode msg) {
        if (client == null || client.getId() == null) return;
        if (toPhone == null || toPhone.isBlank()) return;
        if (!client.isAutoReplyEnabled()) {
            log.debug("AUTO_REPLY_DISABLED clientId={} phone={}", client.getId(), toPhone);
            return;
        }

        List<AutoReplyRule> activeRules =
                autoReplyRuleRepository.findByClient_IdAndActiveTrueOrderByCreatedAtAsc(client.getId());
        if (activeRules.isEmpty()) return;

        AutoReplyRule defaultRule = null;
        AutoReplyRule matched = null;

        for (AutoReplyRule rule : activeRules) {
            if (rule.getRuleType() == AutoReplyRule.RuleType.DEFAULT) {
                if (defaultRule == null) defaultRule = rule;
                continue;
            }
            if (matches(rule, type, msg)) {
                matched = rule;
                break;
            }
        }

        if (matched == null) matched = defaultRule;
        if (matched == null) {
            log.info("AUTO_REPLY_NO_MATCH clientId={} phone={} type={}", client.getId(), toPhone, type);
            return;
        }

        dispatchRule(client, toPhone, matched);
    }

    private void dispatchRule(Client client, String toPhone, AutoReplyRule matched) {
        SendMessageRequest request = new SendMessageRequest(
                toPhone,
                matched.getResponseType() == AutoReplyRule.ResponseType.TEXT ? matched.getResponseText() : null,
                null,
                matched.getResponseType() == AutoReplyRule.ResponseType.TEMPLATE && matched.getTemplate() != null
                        ? matched.getTemplate().getId()
                        : null,
                Map.of(),
                "META",
                null
        );
        messageService.sendMessage(request, client.getId());
        log.info("AUTO_REPLY_SENT clientId={} phone={} ruleId={} ruleType={}",
                client.getId(), toPhone, matched.getId(), matched.getRuleType());
    }

    private boolean matches(AutoReplyRule rule, String type, JsonNode msg) {
        String ruleValue = normalizeRuleValue(rule);
        if (ruleValue == null || ruleValue.isBlank()) return false;

        switch (rule.getRuleType()) {
            case TEXT_EXACT:
                if (!"text".equals(type)) return false;
                String text = normalizeText(msg.path("text").path("body").asText(null));
                return ruleValue.equals(text);

            case BUTTON_PAYLOAD:
                if (!"button".equals(type)) return false;
                String payload = normalizeRaw(msg.path("button").path("payload").asText(null));
                return ruleValue.equals(payload);

            case INTERACTIVE_REPLY_ID:
                if (!"interactive".equals(type)) return false;
                JsonNode interactive = msg.path("interactive");
                String interactiveType = interactive.path("type").asText("");
                if ("button_reply".equals(interactiveType)) {
                    return ruleValue.equals(normalizeRaw(interactive.path("button_reply").path("id").asText(null)));
                }
                if ("list_reply".equals(interactiveType)) {
                    return ruleValue.equals(normalizeRaw(interactive.path("list_reply").path("id").asText(null)));
                }
                return false;

            case DEFAULT:
                return true;

            default:
                return false;
        }
    }

    private void applyRequest(AutoReplyRule rule, AutoReplyRuleRequest request, UUID clientId) {
        validateRequest(request, clientId, rule.getId());

        Client client = new Client();
        client.setId(clientId);
        rule.setClient(client);
        rule.setName(request.getName().trim());
        rule.setRuleType(request.getRuleType());
        rule.setResponseType(request.getResponseType());
        rule.setActive(request.isActive());

        if (request.getRuleType() == AutoReplyRule.RuleType.DEFAULT) {
            rule.setMatchValue(null);
        } else if (request.getRuleType() == AutoReplyRule.RuleType.TEXT_EXACT) {
            rule.setMatchValue(normalizeText(request.getMatchValue()));
        } else {
            rule.setMatchValue(normalizeRaw(request.getMatchValue()));
        }

        if (request.getResponseType() == AutoReplyRule.ResponseType.TEXT) {
            rule.setResponseText(request.getResponseText().trim());
            rule.setTemplate(null);
        } else {
            rule.setResponseText(null);
            UUID templateId = Objects.requireNonNull(request.getTemplateId(), "Template id is required");
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("Template not found"));
            if (!template.getClient().getId().equals(clientId)) {
                throw new RuntimeException("Template not found");
            }
            rule.setTemplate(template);
        }
    }

    private void validateRequest(AutoReplyRuleRequest request, UUID clientId, UUID currentRuleId) {
        if (request.getRuleType() == AutoReplyRule.RuleType.DEFAULT) {
            if (request.isActive()) {
                validateSingleActiveDefault(clientId, currentRuleId);
            }
        } else if (request.getMatchValue() == null || request.getMatchValue().isBlank()) {
            throw new RuntimeException("Match value is required for this rule type");
        }

        if (request.getResponseType() == AutoReplyRule.ResponseType.TEXT) {
            if (request.getResponseText() == null || request.getResponseText().isBlank()) {
                throw new RuntimeException("Response text is required for TEXT response type");
            }
        } else if (request.getTemplateId() == null) {
            throw new RuntimeException("Template id is required for TEMPLATE response type");
        }
    }

    private void validateSingleActiveDefault(UUID clientId, UUID currentRuleId) {
        autoReplyRuleRepository.findByClient_IdAndRuleTypeAndActiveTrue(
                        clientId, AutoReplyRule.RuleType.DEFAULT)
                .ifPresent(existing -> {
                    if (currentRuleId == null || !existing.getId().equals(currentRuleId)) {
                        throw new RuntimeException("Only one active DEFAULT auto-reply rule is allowed per client");
                    }
                });
    }

    private String normalizeRuleValue(AutoReplyRule rule) {
        if (rule.getRuleType() == AutoReplyRule.RuleType.TEXT_EXACT) {
            return normalizeText(rule.getMatchValue());
        }
        return normalizeRaw(rule.getMatchValue());
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRaw(String value) {
        if (value == null) return null;
        return value.trim();
    }

    private AutoReplyRule getRuleEntity(UUID id, UUID clientId) {
        Objects.requireNonNull(id, "Rule id is required");
        AutoReplyRule rule = autoReplyRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auto-reply rule not found"));
        if (!rule.getClient().getId().equals(clientId)) {
            throw new RuntimeException("Auto-reply rule not found");
        }
        return rule;
    }

    private AutoReplyRuleResponse toResponse(AutoReplyRule rule) {
        return new AutoReplyRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getRuleType(),
                rule.getMatchValue(),
                rule.getResponseType(),
                rule.getResponseText(),
                rule.getTemplate() != null ? rule.getTemplate().getId() : null,
                rule.isActive(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private Client getClient(UUID clientId) {
        Objects.requireNonNull(clientId, "Client id is required");
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));
    }
}
