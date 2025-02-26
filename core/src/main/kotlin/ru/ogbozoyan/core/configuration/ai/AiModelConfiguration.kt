package ru.ogbozoyan.core.configuration.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor.HIGHEST_PRECEDENCE
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.jdbc.core.JdbcTemplate
import ru.ogbozoyan.core.PgVectorChatMemory
import ru.ogbozoyan.core.PgVectorChatMemoryConfig
import ru.ogbozoyan.core.configuration.MOCK_CONVERSATION_ID


@Configuration
class AiModelConfiguration(
    private val chatClientBuilder: ChatClient.Builder,
    @Value("\${app.assistant.prompt.system-message-ru}") private val systemMessageRu: String,
    @Value("\${app.advisor.chat-memory.prompt.default-system-advise-text-ru}") private val DEFAULT_SYSTEM_TEXT_ADVISE_RU: String,
    @Value("\${app.advisor.logger.order}") private val LOGGER_ADVISOR_ORDER: Int,
    @Value("\${app.advisor.chat-memory.order}") private val CHAT_MEMORY_ADVISOR_ORDER: Int,
    @Value("\${app.advisor.chat-memory.memory-size}") private val CHAT_MEMORY_SIZE: Int
) {

    private val chatMemoryOrder = HIGHEST_PRECEDENCE + CHAT_MEMORY_ADVISOR_ORDER
    private val loggingOrder = Ordered.HIGHEST_PRECEDENCE + LOGGER_ADVISOR_ORDER

    @Bean
    fun cleanChatClient(): ChatClient = chatClientBuilder
        .clone()
        .defaultAdvisors(
            SimpleLoggerAdvisor(
                loggingOrder
            )
        )
        .build()

    @Bean
    fun chatClient(jdbcTemplate: JdbcTemplate): ChatClient {

        val simpleLoggerAdvisor = SimpleLoggerAdvisor(
            loggingOrder
        )

        val pgVectorChatMemory = PgVectorChatMemory.create(
            PgVectorChatMemoryConfig.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withInitializeSchema(true)
                .build()
        )

        val chatMemoryAdvisor =
            PromptChatMemoryAdvisor(
                pgVectorChatMemory,
                MOCK_CONVERSATION_ID,
                CHAT_MEMORY_SIZE,
                DEFAULT_SYSTEM_TEXT_ADVISE_RU,
                chatMemoryOrder
            )

        return chatClientBuilder.clone()
            .defaultSystem(systemMessageRu)
            .defaultAdvisors(
                simpleLoggerAdvisor,
                chatMemoryAdvisor,
            )
            .build()
    }

}

