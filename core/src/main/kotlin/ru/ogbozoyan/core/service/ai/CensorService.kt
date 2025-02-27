package ru.ogbozoyan.core.service.ai

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.ogbozoyan.core.web.dto.ApiRequest
import ru.ogbozoyan.core.web.dto.DecisionResponse

@Service
class CensorService(
    @Qualifier("cleanChatClient") private val cleanChatClient: ChatClient,
    @Value("\${app.prompt.censor-ru}")
    private var censorSystemMessage: String,
    var vectorStore: VectorStore,
    @Value("\${app.rounds.censor-max-rounds}")
    private val CENSOR_ROUNDS: Int,
) {
    private val log = LoggerFactory.getLogger(CensorService::class.java)

    fun censorValidation(request: ApiRequest): Boolean {

        val documents = vectorStore.similaritySearch(request.question)
            ?.map { it.text }
            ?.joinToString(System.lineSeparator())
        val censorHistory = mutableListOf<String>()

        for (attempt in 1..CENSOR_ROUNDS) {
            log.debug("Censor attempt #$attempt for question: {}", request.question)

            val historyMessage = censorHistory.takeIf { it.isNotEmpty() }
                ?.joinToString("\n", prefix = "**История решений цензора:**\n")
                ?: "Нет предыдущих решений."


            val censorResponse = callCensor(request, historyMessage, documents)
            if (censorResponse == null) {
                log.warn("Censor validation failed on attempt #$attempt: response is null")
                continue
            }

            log.debug("Censor response: {}", censorResponse)
            if (censorResponse.decision == "ДА") {
                log.info("Censor passed: received 'ДА'")
                return true
            }
            censorHistory += buildString {
                append("Попытка #")
                append(attempt)
                append(" → Вопрос: ")
                append(request.question)
                append(" Решение: ")
                append(censorResponse.decision)
                append(" (")
                append(
                    censorResponse.flags.joinToString(
                        ", "
                    )
                )
                append(")")
            }
        }

        log.warn("Censor failed: did not receive 'ДА' in $CENSOR_ROUNDS attempts.")
        return false
    }


    private fun callCensor(request: ApiRequest, historyMessage: String, documents: String?): DecisionResponse? {
        log.debug("CENSOR REQUEST: {} history: {} context: {}", request.question, historyMessage, documents)
        return cleanChatClient.prompt()
            .system(
                censorSystemMessage
                    .replace("<QUESTION>", request.question)
                    .let {
                        if (documents != null) it.replace("<DOCUMENT>", documents) else it.replace("<DOCUMENT>", "")
                    })
            .user(request.question)
            .messages(AssistantMessage(historyMessage))
            .call()
            .entity(DecisionResponse::class.java)
    }
}