package ru.ogbozoyan.core.service.ingestion

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY
import org.springframework.ai.document.Document
import org.springframework.ai.reader.ExtractedTextFormatter
import org.springframework.ai.reader.TextReader
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.charset.Charset
import java.util.*


@Service
class IngestionService(
    private val vectorStore: VectorStore
) {

    private val log: Logger = LoggerFactory.getLogger(IngestionService::class.java)

    suspend fun saveNewPDFAsync(pdf: Resource, fName: String?, chatId: UUID?) {
        val fileName = fName ?: "${UUID.randomUUID()}.pdf"
        val textSplitter = tokenTextSplitter()

        try {
            log.info("Loading {} Reference PDF into Vector Store", fileName)
            val config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .withPageExtractedTextFormatter(ExtractedTextFormatter.builder().build())
                .build()

            val pagePdfDocumentReader = PagePdfDocumentReader(pdf, config)
            val documents = pagePdfDocumentReader.get()

            enrichWithFileName(documents, fileName, chatId)

            vectorStore.add(textSplitter.apply(documents))
            log.info("Successfully loaded Vector Store by {}", fileName)

        } catch (e: Throwable) {
            log.error(
                "Error while loading PDF {} into Vector Store. Exception: {} - Message: {}",
                fileName,
                e::class.simpleName,
                e.message
            )
            log.debug("Stack trace: ", e)
            throw e
        }
    }

    suspend fun saveNewTextAsync(txt: Resource, fName: String?, chatId: UUID? = null) {

        val fileName = fName ?: "${UUID.randomUUID()}.txt"
        val textSplitter = tokenTextSplitter()

        try {
            log.info("Loading {} .txt/md files as Documents", fileName)
            val textReader = TextReader(txt)
            textReader.charset = Charset.defaultCharset()
            val documents = textReader.get()

            enrichWithFileName(documents, fileName, chatId)

            log.info("Creating and storing Embeddings from Documents")
            vectorStore.accept(textSplitter.split(documents))
            log.info("Successfully loaded Vector Store by {} .txt/md files", fileName)

        } catch (e: Throwable) {
            log.error(
                "Error while loading text {} into Vector Store. Exception: {} - Message: {}",
                fileName,
                e::class.simpleName,
                e.message
            )
            log.debug("Stack trace: ", e)
            throw e

        }
    }

    private fun enrichWithFileName(
        documents: List<Document>,
        fileName: String,
        chatId: UUID? = null
    ): List<Document> {

        for (document: Document in documents) {
            document.metadata["file_name"] = fileName
            chatId?.let { document.metadata[CHAT_MEMORY_CONVERSATION_ID_KEY] = it }
        }
        return documents
    }

    private fun tokenTextSplitter(): TokenTextSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(100)
            .withMinChunkSizeChars(50)
            .build()

}
