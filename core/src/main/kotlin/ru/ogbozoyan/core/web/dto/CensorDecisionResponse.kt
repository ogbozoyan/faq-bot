package ru.ogbozoyan.core.web.dto

data class DecisionResponse(
    val decision: String,
    val reason: String?,
    val confidence: Int,
    val flags: List<String>

) {
    init {
        require(decision in listOf("ДА", "НЕТ")) { "decision должен быть либо 'ДА', либо 'НЕТ'" }
        require(confidence in 0..100) { "confidence должен быть в диапазоне 0-100" }
        if (decision == "ДА") {
            require(reason == null) { "reason должен быть null, если decision = 'ДА'" }
        } else {
            require(!reason.isNullOrBlank()) { "reason не может быть пустым, если decision = 'НЕТ'" }
        }
    }
}

