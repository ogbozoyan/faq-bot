package ru.ogbozoyan.core.service

import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.time.LocalDateTime


@Component
class Tools {

    @Tool(description = "Функция отвечает за полученние текущей даты")
    fun getCurrentDateTime(): String {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString()
    }

    @Tool(description = "Тестовая функция")
    fun getCurrentDateTime2(): String {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString()
    }
}