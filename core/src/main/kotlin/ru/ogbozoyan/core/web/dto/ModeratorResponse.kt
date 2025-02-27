package ru.ogbozoyan.core.web.dto

data class ModeratorResponse(
    val answer: String,
    val isApproved: Boolean,
    val rejectionReason: String?,
    val alerts: List<String>
) {
    init {
        if (!isApproved) {
            require(!rejectionReason.isNullOrBlank()) { "rejectionReason must be filled, if isApproved = false" }
        } else {
            require(rejectionReason.isNullOrBlank()) { "rejectionReason must be null, if isApproved = true" }
        }
    }
}