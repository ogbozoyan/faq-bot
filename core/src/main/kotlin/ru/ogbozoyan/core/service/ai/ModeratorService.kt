package ru.ogbozoyan.core.service.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.ogbozoyan.core.web.dto.ApiRequest
import ru.ogbozoyan.core.web.dto.ModeratorResponse

@Service
class ModeratorService(
    @Qualifier("cleanChatClient") private val cleanChatClient: ChatClient,
    @Value("\${app.prompt.moderator-ru}")
    private var moderatorSystemMessage: String,
) {
    private val log = LoggerFactory.getLogger(ModeratorService::class.java)

    fun moderatorValidation(request: ApiRequest, assistantResponse: String): ModeratorResponse {
        log.debug("MODERATOR VALIDATION REQUEST: {} assistant response: {}", request.question, assistantResponse)
        return cleanChatClient.prompt()
            .system(
                moderatorSystemMessage
                    .replace("<REQUEST>", request.question)
                    .replace("<ASSISTANT_ANSWER>", assistantResponse)
            )
            .user(request.question)
            .messages(
                AssistantMessage(assistantResponse)
            )
            .call()
            .entity(ModeratorResponse::class.java)!!
    }
}