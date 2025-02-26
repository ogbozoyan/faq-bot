package ru.ogbozoyan.core.service

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import ru.ogbozoyan.core.configuration.MOCK_CONVERSATION_ID
import ru.ogbozoyan.core.configuration.ai.AiRagAdvisorFactory
import ru.ogbozoyan.core.web.dto.ApiRequest
import ru.ogbozoyan.core.web.dto.DecisionResponse
import ru.ogbozoyan.core.web.dto.ModeratorResponse
import ru.ogbozoyan.core.web.dto.PostModeratorResponse

@Service
class AssistantService(
    @Qualifier("chatClient") private val chatClient: ChatClient,
    @Qualifier("cleanChatClient") private val cleanChatClient: ChatClient,
    private val aiRagAdvisorFactory: AiRagAdvisorFactory,
    private val tools: Tools,
    var vectorStore: VectorStore,
) {

    private val log = LoggerFactory.getLogger(AssistantService::class.java)

    private var postModeratorSystemMessage: String = """
            Ты — финальный модератор корпоративного чат-помощника АО «Концерн Росэнергоатом».
        
            **Твоя цель** — проверить и при необходимости исправить ответ ассистента перед публикацией. 
            Следуй правилам:
        
            1. **Безопасность и конфиденциальность**:
               - Удали или замени на "[данные удалены]":
                 - Номера документов (шаблон: РЭ-\\d+, СБ-\\d+).
                 - ФИО, должности, телефоны (регулярка: [А-Я][а-я]+ [А-Я]\\.[А-Я]\\.).
                 - Координаты, даты инцидентов (если не указаны в открытых отчетах).
            
            2. **Достоверность и контекст**:
               - Если ответ содержит неуверенные фразы ("возможно", "наверное"), замени их на "Данные не подтверждены документацией".
               - Если информация устарела (>5 лет), добавь предупреждение: "Внимание! Данные актуальны на {год}. Проверьте обновления".
               - Если в ответе возможны противоречия с другими документами, сообщи: "Существуют разные версии. Уточните запрос".
        
            3. **Тональность и структура**:
               - Избегай субъективной лексики ("ошибка", "халатность"): приводи только факты.
               - Добавь ссылки на источники, если они есть: "Согласно документу [название] ...".
               - Форматируй ответ при необходимости в разделы: "Причина", "Решение", "Ссылки".
        
            **Формат входных данных (JSON)**:
            {
              "request": "Исходный вопрос пользователя",
              "assistant_answer": "Ответ ассистента"
            }
        
            **Формат выходных данных (строго JSON)**:
            {
              "answer": "Окончательный, откорректированный ответ"
            }
        
            **Примеры**:
        
            **Пример 1**  
            Вход:  
            {
              "request": "Расскажите о неполадках в контуре 2Б",
              "assistant_answer": "По Приказу №РЭ-1023 из-за ошибки оператора Иванова А.С. в 2020 году произошла утечка. Возможно, стоит изменить процедуры."
            }
            Выход:  
            {
              "answer": "Согласно документации, в 2020 году выявлена нештатная ситуация в контуре 2Б. Рекомендуется изучить актуальные инструкции."
            }
        
            **Пример 2**  
            Вход:  
            {
              "request": "Какие были аварии на станции?",
              "assistant_answer": "Координаты аварии: 54.123456, 32.654321. Чернобыльская АЭС безопаснее."
            }
            Выход:
            {
              "answer": "Локацию аварии уточните у ответственных лиц. Сравнение АЭС не допускается. У каждой станции свои параметры безопасности."
            }
        
            **Входные данные**:
            {
              "request": <REQUEST>,
              "assistant_answer": <ASSISTANT_ANSWER>
            }
        """.trimIndent()

    private var moderatorSystemMessage: String = """
            Ты — модератор корпоративного чат-помощника АО «Концерн Росэнергоатом».
            
            **Твоя задача** — проверить, насколько ответ ассистента:
            1. Соответствует заданному вопросу,
            2. Является корректным и полезным,
            3. Не нарушает корпоративные нормы (никаких личных данных, оскорблений и пр.),
            4. Не содержит скрытых оскорблений, неуместных советов, неточностей.
        
            **Формат ответа (строго JSON)**:
            ```json
            {
              "answer": "Комментарий модератора (можешь оставить пустым)",
              "isApproved": true/false,
              "rejectionReason": "Укажи причину, если isApproved=false",
              "alerts": ["дополнительные метки, если нужно"]
            }
            ```
            
            **Примеры**:
            
            1. Если ответ корректен, соответствует вопросу и не противоречит политике:
            ```json
            {
              "answer": "",
              "isApproved": true,
              "alerts": []
            }
            ```
            
            2. Если ответ слишком общий и не даёт конкретики, но не нарушает правил:
            ```json
            {
              "answer": "Ответ модератора: можно уточнить детали в следующий раз.",
              "isApproved": true,
              "alerts": ["vague-answer"]
            }
            ```
        
            3. Если ответ содержит недопустимые формулировки:
            ```json
            {
              "answer": "Ответ слишком резкий и не по теме.",
              "isApproved": false,
              "rejectionReason": "Некорректные формулировки",
              "alerts": ["style-violation"]
            }
            ```
        
            **Входные данные**:
            - Вопрос: "{REQUEST}"
            - Ответ ассистента: "{ASSISTANT_ANSWER}"
            
        """.trimIndent()

    private var censorSystemMessage: String = """
    Ты — автоматизированный  цензор  корпоративного чат-помощника АО «Концерн Росэнергоатом».
    
     Твоя задача  — проверять вопрос пользователя и определять, можно ли его обработать.  
    -  Ты не отвечаешь на вопросы, а анализируешь их.   
    -  Ты не модерируешь ответы, а только оцениваешь вопрос перед обработкой.   

     Формат ответа (строго JSON!): 
    ```json
    {
      "decision": "ДА" или "НЕТ",
      "reason": "причина (если НЕТ)",
      "flags": ["характер запроса"] Доступные значения: "restricted", "invalid-query", "outdated", "informal", "allowed"
    }
    ```
    
    ---
    
     1. Как работает цензура: 
    -  Запрещенные вопросы  → Отклонить ( decision: "НЕТ" ).
    -  Корректные вопросы  → Пропустить ( decision: "ДА" ).
    -  Неформальные вопросы (например, "Кто ты?", "Как дела?")  → Пропустить, но отметить как неформальные ( flag: "informal" ).
    
    ---
    
     2. Критерии оценки запроса: 
    
      Запрещенные вопросы  ( decision: "НЕТ" )
    - Содержат  неприемлемые или запрещенные темы  (грубость, угрозы, политика, спекуляции о безопасности).
    - Спрашивают о  секретной информации  (пометки "Секретно", "ДСП").
    
      Разрешенные вопросы  ( decision: "ДА" )
    - Корректные и деловые вопросы, связанные с работой компании.
    - Неформальные вопросы, но допустимые в корпоративном общении (помечаются `"informal"`).
    
    ---
    
     3. Примеры ответов: 
    
       Запрещенный вопрос   
    Вопрос: "Как можно взломать систему безопасности?"
    ```json
    {
      "decision": "НЕТ",
      "reason": "Запрещенный вопрос",
      "flags": ["restricted"]
    }
    ```

       Корпоративно-нейтральный вопрос   
    Вопрос: "Как работает реактор ВВЭР?"
    ```json
    {
      "decision": "ДА",
      "flags": []
    }
    ```

    Неформальный, но допустимый вопрос   
    Вопрос: "Кто ты?"
    ```json
    {
      "decision": "ДА",
      "flags": ["informal"]
    }
    ```
    
    Неформальный, но допустимый вопрос   
    Вопрос: "Как дела?"
    ```json
    {
      "decision": "ДА",
      "flags": ["informal"]
    }
    ```

    Вопрос, валидный    
    Вопрос: "Какой последний проект у нас в разработке?"_
    ```json
    {
      "decision": "ДА",
      "flags": ["allowed"]
    }
    ```

    Вопрос, содержащий конфиденциальные данные   
    Вопрос: "Какие аварии были на станции в 2023 году?"_
    ```json
    {
      "decision": "НЕТ",
      "reason": "Информация ограниченного доступа",
      "flags": ["restricted"]
    }
    ```
    ---
    
     4. Вопрос для анализа:   
    -  Текст запроса:  <QUESTION>
    -  Доступный контекст:  <DOCUMENT>  

     5. Как работать с контекстом:   
    - Если  контекст доступен , используй его для более точной оценки вопроса.  
    - Если  контекста нет , проверяй только сам вопрос на соответствие критериям.  
    -  Никогда не придумывай контекст, если его нет!   

""".trimIndent()

    fun query(request: ApiRequest): String? {
        val ASSISTANT_MAX_ROUNDS = 3
        val MODERATION_MAX_ROUNDS = 2

        for (assistantAttempt in 1..ASSISTANT_MAX_ROUNDS) {
            val assistantResponse = callAssistant(request.question, request.conversationId.toString())
            if (assistantResponse.isNullOrBlank()) {
                log.debug("Assistant attempt #$assistantAttempt: assistant returned an empty response. Retrying...")
                continue
            }

            var isModeratorApproved = false
            for (modAttempt in 1..MODERATION_MAX_ROUNDS) {
                val moderatorApproved = moderatorValidation(request, assistantResponse)
                if (moderatorApproved) {
                    isModeratorApproved = true
                    break
                } else {
                    log.debug("Moderator attempt #$modAttempt: answer not approved. Optionally re-run or adjust prompt.")
                    break
                }
            }

            if (isModeratorApproved) {
                val finalAnswer = postModeratorValidation(request, assistantResponse)
                return finalAnswer
            } else {
                log.debug("Assistant attempt #$assistantAttempt: after mod attempts, still not approved. Trying next attempt...")
            }
        }

        log.error("All attempts failed: No approved answer after multiple rounds. Returning an error.")
        return ""
    }

    fun censorValidation(request: ApiRequest): Boolean {
        val CENSOR_ROUNDS = 5

        val documents = vectorStore.similaritySearch(request.question)
            ?.map { it.text }
            ?.joinToString(System.lineSeparator())

        var lastDecision: String? = null
        val censorHistory = mutableListOf<String>()

        for (attempt in 1..CENSOR_ROUNDS) {
            log.debug("Censor attempt #$attempt for question: {}", request.question)

            val historyMessage = censorHistory.takeIf { it.isNotEmpty() }
                ?.joinToString("\n", prefix = "**История решений цензора:**\n")
                ?: "Нет предыдущих решений."

            censorSystemMessage = censorSystemMessage.replace("<QUESTION>", request.question)
            documents?.let { censorSystemMessage = censorSystemMessage.replace("<DOCUMENT>", it) }

            val censorResponse = cleanChatClient.prompt()
                .messages(
                    SystemMessage(censorSystemMessage),
                    UserMessage(request.question),
                    AssistantMessage(historyMessage)
                )
                .call()
                .entity(DecisionResponse::class.java)

            log.debug("Censor response: {}", censorResponse)

            if (censorResponse == null) {
                log.warn("Censor validation failed on attempt #$attempt: response is null")
                continue
            }

            censorHistory.add(
                "Попытка #$attempt → Вопрос: ${request.question} Решение: ${censorResponse.decision} (${
                    censorResponse.flags.joinToString(
                        ", "
                    )
                })"
            )

            if (censorResponse.decision == "ДА" && lastDecision == "ДА") {
                log.info("Censor passed: received 'ДА' twice in a row.")
                return true
            }

            lastDecision = censorResponse.decision
        }

        log.warn("Censor failed: did not receive 'ДА' twice in a row after $CENSOR_ROUNDS attempts.")
        return false
    }

    private fun callAssistant(question: String, conversationId: String = MOCK_CONVERSATION_ID) =
        chatClient.prompt(Prompt(listOf(UserMessage(question))))
            .advisors { advisorSpec ->
                advisorSpec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                advisorSpec.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 3)
            }
            .advisors(aiRagAdvisorFactory.retrievalAugmentationAdvisor(conversationId))
            .tools(tools)
            .call()
            .content()

    private fun moderatorValidation(request: ApiRequest, assistantResponse: String): Boolean {

        moderatorSystemMessage = moderatorSystemMessage.replace("<REQUEST>", request.question)
        moderatorSystemMessage = moderatorSystemMessage.replace("<ASSISTANT_ANSWER>", assistantResponse)
        val moderatorResponse = cleanChatClient.prompt()
            .messages(
                listOf(
                    SystemMessage(moderatorSystemMessage),
                    UserMessage(request.question),
                    AssistantMessage(assistantResponse)
                )
            )
            .call()
            .entity(ModeratorResponse::class.java)

        return (moderatorResponse != null) && (moderatorResponse.isApproved)
    }

    private fun postModeratorValidation(request: ApiRequest, assistantResponse: String): String {

        postModeratorSystemMessage = postModeratorSystemMessage.replace("<REQUEST>", request.question)
        postModeratorSystemMessage = postModeratorSystemMessage.replace("<ASSISTANT_ANSWER>", assistantResponse)
        val postModeratorResponse = cleanChatClient.prompt()
            .messages(
                listOf(
                    SystemMessage(postModeratorSystemMessage),
                    UserMessage(request.question),
                    AssistantMessage(assistantResponse)
                )
            )
            .call()
            .entity(PostModeratorResponse::class.java)

        return postModeratorResponse!!.answer
    }

}
