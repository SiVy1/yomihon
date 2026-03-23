package eu.kanade.tachiyomi.ui.reader.loader

internal object TextChunker {

    private const val DEFAULT_TARGET_CHARS = 2_000
    private const val MIN_CHUNK_CHARS = 700

    fun chunk(
        rawText: String,
        targetChars: Int = DEFAULT_TARGET_CHARS,
        minChunkChars: Int = MIN_CHUNK_CHARS,
    ): List<String> {
        val cleanedText = rawText.replace("\r\n", "\n").trim()
        if (cleanedText.isBlank()) return emptyList()

        val paragraphs = cleanedText
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flushChunk(force: Boolean = false) {
            val content = current.toString().trim()
            if (content.isBlank()) return
            if (!force && content.length < minChunkChars && chunks.isNotEmpty()) {
                chunks[chunks.lastIndex] = chunks.last() + "\n\n" + content
            } else {
                chunks.add(content)
            }
            current.clear()
        }

        paragraphs.forEach { paragraph ->
            val paragraphWithBreak = if (current.isEmpty()) paragraph else "\n\n$paragraph"
            if (current.length + paragraphWithBreak.length > targetChars && current.isNotEmpty()) {
                flushChunk()
            }
            current.append(paragraphWithBreak)
        }

        flushChunk(force = true)
        return chunks
    }
}
