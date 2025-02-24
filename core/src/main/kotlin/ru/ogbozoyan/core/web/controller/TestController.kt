package ru.ogbozoyan.core.web.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.ogbozoyan.core.service.AssistantService
import ru.ogbozoyan.core.web.dto.ApiRequest

@RestController
@CrossOrigin(origins = ["*"])
@Tag(name = "Test Controller", description = "API")
class TestController(

    var assistantService: AssistantService
) {

    @PostMapping(
        "/api/v1/test/query",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(summary = "Ask question to llm and save chat", description = "Returns the result")
    @ResponseStatus(HttpStatus.OK)
    fun testQuery(@RequestBody request: ApiRequest): ResponseEntity<String> {

        if (!assistantService.censorValidation(request)) {
            return ResponseEntity.badRequest()
                .body("ERROR: Content did not pass censor validation.")
        }

        return assistantService.query(request).let {
            if (!it.isNullOrBlank()) {
                ResponseEntity.ok(it)
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("ERROR: Assistant failed to provide a valid answer.")
            }
        }

    }


}