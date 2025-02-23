package ru.ogbozoyan.core.web.dto

data class ModeratorResponse(
    val answer: String,
    val isApproved: Boolean,
    val rejectionReason: String?,
    val alerts: List<String>
) {
    init {
        if (!isApproved) {
            require(!rejectionReason.isNullOrBlank()) { "rejectionReason должен быть указан, если isApproved = false" }
        } else {
            require(rejectionReason == null) { "rejectionReason должен быть null, если isApproved = true" }
        }
    }
}