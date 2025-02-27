package ru.ogbozoyan.core.web.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import ru.ogbozoyan.core.model.ContentTypeEnum
import ru.ogbozoyan.core.service.ai.AssistantService
import ru.ogbozoyan.core.service.ai.CensorService
import ru.ogbozoyan.core.service.ingestion.IngestionEvent
import ru.ogbozoyan.core.web.dto.ApiRequest
import java.util.*


@RestController
@CrossOrigin(origins = ["*"])
@Tag(name = "Core API controller", description = "API")
class CoreController(
    val publisher: ApplicationEventPublisher,
    val assistantService: AssistantService,
    val censorService: CensorService,
) {

    private val log = LoggerFactory.getLogger(CoreController::class.java)

    @PostMapping(
        "/api/v1/embed-file",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Send file to embed and save for specific chat", description = "Returns the result")
    fun embedFile(
        @RequestPart("file", required = true) file: MultipartFile,
        @RequestParam("type", required = true) type: ContentTypeEnum,
        @RequestParam("chatId", required = true) chatId: UUID,
    ) {
        return try {
            log.info("Event triggered via REST endpoint for file: ${file.originalFilename} with type: $type")
            val byteArrayResource = ByteArrayResource(file.bytes)
            publisher.publishEvent(IngestionEvent(byteArrayResource, type, file.originalFilename, chatId))
        } catch (e: Exception) {
            log.error("Error triggering event: {}", e.message)
        }
    }

    @PostMapping(
        "/api/v1/query",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(summary = "Ask question to llm and save chat", description = "Returns the result")
    @ResponseStatus(HttpStatus.OK)
    fun query(@RequestBody request: ApiRequest): ResponseEntity<String> {

        if (!censorService.censorValidation(request)) {
            return ResponseEntity.badRequest()
                .body("ERROR: Content did not pass censor validation.")
        }

        return assistantService.call(request).let {
            if (!it.isNullOrBlank()) {
                ResponseEntity.ok(it)
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("ERROR: Assistant failed to provide a valid answer.")
            }
        }

    }
}