package ru.ogbozoyan.core.web.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ogbozoyan.core.configuration.ai.AiRagAdvisorFactory
import ru.ogbozoyan.core.service.Tools
import ru.ogbozoyan.core.web.dto.ApiRequest
import ru.ogbozoyan.core.web.dto.DecisionResponse
import ru.ogbozoyan.core.web.dto.ModeratorResponse
import java.util.stream.Collectors

@RestController
@CrossOrigin(origins = ["*"])
@Tag(name = "Test Controller", description = "API")
class TestController(
    @Qualifier("ollamaClient") private val ollamaChat: ChatClient,
    @Qualifier("simpleOllamaChatClient") private val chatClient: ChatClient,
    private val aiRagAdvisorFactory: AiRagAdvisorFactory,
    private val tools: Tools,
    @Value("classpath:/prompts/censor-message-ru.st") private val censorMessageSt: Resource,
    @Value("classpath:/prompts/post-moderator-message-ru.st") private val moderatorMessageSt: Resource,
    var vectorStore: VectorStore
) {

    private val log = LoggerFactory.getLogger(CoreController::class.java)
    private val MODERATION_ROUNDS: Int = 5

    @PostMapping(
        "/api/v1/test/query",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(summary = "Ask question to llm and save chat", description = "Returns the result")
    @ResponseStatus(HttpStatus.OK)
    fun testQuery(@RequestBody request: ApiRequest): ResponseEntity<String> {

        if (!censorValidation(request)) {
            return ResponseEntity.badRequest().body("ERROR: Content did not pass censor validation.")
        }

        var attempt = 0
        var assistantResponse: String?

        while (attempt < MODERATION_ROUNDS) {
            attempt++

            assistantResponse = callAssistant(request)

            if (!assistantResponse.isNullOrBlank()) {
                if (postModeratorValidation(request, assistantResponse)) {
                    return ResponseEntity.ok(assistantResponse)
                } else {
                    log.warn("Attempt #$attempt: Assistant response failed moderation. Retrying...")
                }
            } else {
                log.warn("Attempt #$attempt: Assistant returned a blank response. Retrying...")
            }
        }

        log.error("All $MODERATION_ROUNDS attempts failed. Returning an error response.")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("ERROR: Assistant failed to provide a valid answer.")
    }

    private fun censorValidation(request: ApiRequest): Boolean {

        val documents = vectorStore.similaritySearch(request.question)
            ?.stream()
            ?.map { obj: Document -> obj.text }
            ?.collect(Collectors.joining(System.lineSeparator()))

        val censorResponse = chatClient.prompt()
            .messages(listOf(SystemMessage(censorMessageSt), UserMessage(request.question)))
            .system { spec ->
                spec.text(censorMessageSt)
                spec.param("QUESTION", request.question)
                documents?.let {
                    spec.param("DOCUMENT", it)
                }
            }
            .call()
            .entity(DecisionResponse::class.java)

        return (censorResponse != null) && (censorResponse.confidence >= 80 && censorResponse.decision == "ДА")
    }

    private fun callAssistant(request: ApiRequest) =
        ollamaChat.prompt(Prompt(listOf(UserMessage(request.question))))
            .advisors { advisorSpec ->
                advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, request.conversationId)
                advisorSpec.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 3)
            }
            .advisors(aiRagAdvisorFactory.retrievalAugmentationAdvisor(request.conversationId.toString()))
            .tools(tools)
            .call()
            .content()

    private fun postModeratorValidation(request: ApiRequest, assistantResponse: String): Boolean {

        val moderatorResponse = chatClient.prompt()
            .messages(
                listOf(
                    SystemMessage(moderatorMessageSt),
                    UserMessage(request.question),
                    AssistantMessage(assistantResponse)
                )
            )
            .system { spec ->
                spec.text(censorMessageSt)
            }
            .call()
            .entity(ModeratorResponse::class.java)

        return (moderatorResponse != null) && (moderatorResponse.isApproved).also {
            log.debug(
                "ModeratorResponse: {}",
                moderatorResponse.answer
            )
        }
    }

}