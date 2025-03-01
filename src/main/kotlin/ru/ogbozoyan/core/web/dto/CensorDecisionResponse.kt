package ru.ogbozoyan.core.web.dto

data class DecisionResponse(
    val decision: String,
    val reason: String? = null,
    val flags: List<String> = emptyList()
) {
    private val allowedFlags = setOf("restricted", "invalid-query", "outdated", "informal", "allowed")

    init {
        require(decision in listOf("ДА", "НЕТ")) { "decision должен быть либо 'ДА', либо 'НЕТ'" }

        if (decision == "ДА") {
            require(reason == null || "informal" in flags) {
                "reason должен быть null, если decision = 'ДА', за исключением неформальных вопросов"
            }
        } else {
            require(!reason.isNullOrBlank()) { "reason не может быть пустым, если decision = 'НЕТ'" }
        }
        require(flags.all { it in allowedFlags }) {
            "Некорректный флаг в списке flags: допустимые значения $allowedFlags"
        }
    }
}

