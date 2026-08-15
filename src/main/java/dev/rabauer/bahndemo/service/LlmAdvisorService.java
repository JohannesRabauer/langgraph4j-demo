package dev.rabauer.bahndemo.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.rabauer.bahndemo.client.dto.JourneyDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Active only when bahn.advisor.enabled=true (see AdvisorConfig for the matching gated ChatModel
 * bean). Mutually exclusive with RuleBasedAdvisorService via Spring's conditional bean registration
 * on the same AdvisorService interface, so AdvisorNode never has to choose between them.
 *
 * TODO(stream): build a prompt from delaySeconds + alternatives, call chatModel.chat(prompt), return it.
 */
@Service
@ConditionalOnProperty(prefix = "bahn.advisor", name = "enabled", havingValue = "true")
public class LlmAdvisorService implements AdvisorService {

    private final ChatModel chatModel;

    public LlmAdvisorService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String recommend(int delaySeconds, List<JourneyDto> alternatives) {
        return "TODO llm recommendation";
    }
}
