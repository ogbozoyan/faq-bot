package ru.ogbozoyan.core.configuration.ai

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.ai.vectorstore.pgvector.PgVectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.stereotype.Component
import ru.ogbozoyan.core.configuration.MOCK_CONVERSATION_ID


@Component
class AiRagAdvisorFactory(
    @Value("\${app.advisor.rag.order}") private val RAG_ADVISOR_ORDER: Int,
    @Value("\${app.advisor.rag.enabled}") private val IS_ENABLED: Boolean,
    @Value("\${app.advisor.rag.default-user-text-advice}") private val DEFAULT_USER_TEXT_ADVISE: String,
    @Value("\${app.advisor.rag.default-user-text-advice-ru}") private val DEFAULT_USER_TEXT_ADVISE_RU: String,
    private val vectorStore: PgVectorStore,
    private val chatClientBuilder: ChatClient.Builder,
) {

    fun retrievalAugmentationAdvisor(conversationId: String?): RetrievalAugmentationAdvisor =
        RetrievalAugmentationAdvisor.builder()
            .documentRetriever(
                VectorStoreDocumentRetriever.builder()
                    .vectorStore(vectorStore)
                    .similarityThreshold(0.5)
                    .topK(3)
                    .filterExpression(
                        FilterExpressionBuilder()
                            .eq(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId ?: MOCK_CONVERSATION_ID)
                            .build()
                    )
                    .build()
            )
            .queryAugmenter(
                ContextualQueryAugmenter
                    .builder()
                    .allowEmptyContext(true)
                    .build()
            )
//            .queryTransformers(
//                CompressionQueryTransformer.builder()
//                    .chatClientBuilder(chatClientBuilder.build().mutate())
//                    .build()
//            )
            .order(HIGHEST_PRECEDENCE + RAG_ADVISOR_ORDER)
            .build()


    fun questionAnswerAdvisor(conversationId: String?): QuestionAnswerAdvisor =
        QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(
                SearchRequest.builder()
                    .topK(3)
                    .filterExpression(
                        FilterExpressionBuilder()
                            .eq(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId ?: MOCK_CONVERSATION_ID)
                            .build()
                    )
                    .similarityThreshold(0.5)
                    .build()
            )
            .userTextAdvise(DEFAULT_USER_TEXT_ADVISE_RU)
            .order(HIGHEST_PRECEDENCE + RAG_ADVISOR_ORDER)
            .build()

    fun enabled(): Boolean = IS_ENABLED
}