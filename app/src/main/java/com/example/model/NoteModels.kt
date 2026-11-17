package com.example.model

import android.net.Uri
import java.util.UUID

enum class NoteExtractionStyle(val label: String, val promptInstruction: String) {
    STUDY_GUIDE(
        label = "Study Guide",
        promptInstruction = "Organize comprehensive study notes with clear concepts, definitions, key takeaways, and review questions."
    ),
    EXECUTIVE_BRIEF(
        label = "Executive Brief",
        promptInstruction = "Produce a crisp executive summary highlighting core thesis, metrics, insights, and strategic takeaways."
    ),
    MEETING_MINUTES(
        label = "Meeting Notes",
        promptInstruction = "Extract structured discussion topics, decisions made, owners, and concrete action items."
    ),
    LECTURE_OUTLINE(
        label = "Topic Outline",
        promptInstruction = "Break down the content chronologically by timestamp or page number with concise bullet points."
    )
}

enum class NoteAiModelTier(
    val modelId: String,
    val label: String,
    val isPaidTier: Boolean
) {
    STANDARD(
        modelId = "gemini-3.8-flash",
        label = "Standard · Gemini 3.8 Flash",
        isPaidTier = false
    ),
    PRO_DEEP(
        modelId = "gemini-4-argon",
        label = "Pro Deep · Gemini 4 Argon",
        isPaidTier = true
    )
}

enum class NoteSourceKind(val badge: String) {
    VIDEO("VID"),
    PDF("PDF")
}

enum class NoteBatchStatus {
    QUEUED,
    ANALYZING,
    COMPLETED,
    FAILED
}

data class ExtractedNoteSection(
    val heading: String,
    val reference: String,
    val bullets: List<String>
)

data class ExtractedNoteResult(
    val title: String,
    val summary: String,
    val keyTakeaways: List<String>,
    val sections: List<ExtractedNoteSection>,
    val actionItems: List<String>
) {
    fun toMarkdown(): String = buildString {
        append("# ").append(title).append("\n\n")
        append("## Summary\n").append(summary).append("\n\n")
        if (keyTakeaways.isNotEmpty()) {
            append("## Key Takeaways\n")
            keyTakeaways.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        sections.forEach { section ->
            append("## ").append(section.heading)
            if (section.reference.isNotBlank()) {
                append(" (").append(section.reference).append(')')
            }
            append('\n')
            section.bullets.forEach { bullet ->
                append("- ").append(bullet).append('\n')
            }
            append('\n')
        }
        if (actionItems.isNotEmpty()) {
            append("## Action Items & Next Steps\n")
            actionItems.forEach { append("- ").append(it).append('\n') }
        }
    }.trim()
}

data class NoteBatchItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri?,
    val localFilePath: String? = null,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: NoteSourceKind,
    val status: NoteBatchStatus = NoteBatchStatus.QUEUED,
    val progressText: String = "Queued",
    val result: ExtractedNoteResult? = null,
    val exportedPdfId: Long? = null,
    val errorMessage: String? = null
)
