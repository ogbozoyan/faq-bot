package ru.ogbozoyan.core.service.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.ogbozoyan.core.configuration.MOCK_CONVERSATION_ID
import ru.ogbozoyan.core.configuration.ai.AiRagAdvisorFactory
import ru.ogbozoyan.core.service.Tools
import ru.ogbozoyan.core.web.dto.ApiRequest

@Service
class AssistantService(
    @Qualifier("chatClient") private val chatClient: ChatClient,
    private val aiRagAdvisorFactory: AiRagAdvisorFactory,
    private val tools: Tools,
    @Value("\${app.rounds.assistant-max-rounds}")
    private val ASSISTANT_MAX_ROUNDS: Int,
    private val moderatorService: ModeratorService
) {

    private val log = LoggerFactory.getLogger(AssistantService::class.java)

    fun call(request: ApiRequest): String? {

        var userMessage: String = request.question

        for (assistantAttempt in 1..ASSISTANT_MAX_ROUNDS) {
            val assistantResponse = callAssistant(userMessage, request.conversationId.toString())
            if (assistantResponse.isNullOrBlank()) {
                log.debug("Assistant attempt #$assistantAttempt: assistant returned an empty response. Retrying...")
                continue
            }

            val moderatorResponse = moderatorService.moderatorValidation(request, assistantResponse)
            if (moderatorResponse.isApproved) {
                return assistantResponse
            }

            val moderatorRejection = "${moderatorResponse.answer} - ${moderatorResponse.rejectionReason}"
            userMessage =
                """
                Модератор отклонил предыдущий ответ: 
                '$assistantResponse'
                По причине '${moderatorRejection}'.
                Подумай как улучшить ответ и попробуй еще раз.
                Вопрос: '${request.question}'
                """

            log.debug(
                buildString {
                    append("Assistant attempt #")
                    append(assistantAttempt)
                    append(": response not approved, moderator answer and rejection reason: ")
                    append(moderatorRejection)
                    append(". Re-run and adjust prompt. Adjusted prompt: ")
                    append(userMessage.replace("\n", " "))
                })
        }

        log.error("All attempts failed: No approved answer after multiple rounds. Returning an error.")
        return ""
    }


    private fun callAssistant(question: String, conversationId: String = MOCK_CONVERSATION_ID): String? {
        log.debug("USER MESSAGE REQUEST: $question conversationId: $conversationId")
        return chatClient
            .prompt()
            .user(question)
            .advisors { advisorSpec ->
                advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                advisorSpec.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 3)
            }
            .advisors(aiRagAdvisorFactory.retrievalAugmentationAdvisor(conversationId))
            .tools(tools)
            .call()
            .content()
    }


}

