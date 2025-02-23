package ru.ogbozoyan.core.web.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.ByteArrayResource
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.ogbozoyan.core.model.ContentTypeEnum
import ru.ogbozoyan.core.service.ingestion.IngestionEvent
import java.util.*


@RestController
@CrossOrigin(origins = ["*"])
@Tag(name = "Core API controller", description = "API")
class CoreController(
    val publisher: ApplicationEventPublisher
) : CoreApi {

    private val log = LoggerFactory.getLogger(CoreController::class.java)

    override fun embedFile(
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

}