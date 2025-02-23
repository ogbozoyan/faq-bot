package ru.ogbozoyan.core.web.controller

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.multipart.MultipartFile
import ru.ogbozoyan.core.model.ContentTypeEnum
import java.util.*

interface CoreApi {

    @PostMapping(
        "/api/v1/embed-file",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Send file to embed and save for specific chat", description = "Returns the result")
    fun embedFile(file: MultipartFile, type: ContentTypeEnum, chatId: UUID)

}