package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.ExtractedNoteResult
import com.example.model.ExtractedNoteSection
import com.example.model.NoteAiModelTier
import com.example.model.NoteBatchItem
import com.example.model.NoteExtractionStyle
import com.example.model.NoteSourceKind
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "NotesAiRepository"
private const val MAX_INLINE_BYTES = 18L * 1024L * 1024L

class NotesAiRepository(private val context: Context) {

    private val noteSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Concise descriptive title for the extracted notes"),
                "summary" to Schema.string(description = "2-4 sentence overview of the video or PDF document"),
                "keyTakeaways" to Schema.array(
                    items = Schema.string(),
                    description = "Top 3 to 7 high-signal takeaways"
                ),
                "sections" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "heading" to Schema.string(description = "Section or topic heading"),
                            "reference" to Schema.string(description = "Timestamp range (e.g. 02:15-05:40) or page number (e.g. Page 3)"),
                            "bullets" to Schema.array(
                                items = Schema.string(),
                                description = "Detailed structured note bullet points for this section"
                            )
                        )
                    ),
                    description = "Chronological or thematic note sections"
                ),
                "actionItems" to Schema.array(
                    items = Schema.string(),
                    description = "Actionable follow-ups, study questions, or tasks"
                )
            ),
            optionalProperties = listOf("actionItems")
        )
    }

    private val standardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = NoteAiModelTier.STANDARD.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = noteSchema
            }
        )
    }

    private val proDeepModel by lazy {
        Firebase.ai.generativeModel(
            modelName = NoteAiModelTier.PRO_DEEP.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = noteSchema
            }
        )
    }

    suspend fun inspectNoteUris(uris: List<Uri>): List<NoteBatchItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        uris.map { uri ->
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = "Media"
            var sizeBytes = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            val lowerName = displayName.lowercase()
            val lowerMime = mimeType.lowercase()
            val kind = if (
                lowerMime.startsWith("video/") ||
                lowerName.endsWith(".mp4") ||
                lowerName.endsWith(".mov") ||
                lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".webm")
            ) {
                NoteSourceKind.VIDEO
            } else {
                NoteSourceKind.PDF
            }

            val resolvedMime = when {
                kind == NoteSourceKind.VIDEO && !lowerMime.startsWith("video/") -> "video/mp4"
                kind == NoteSourceKind.PDF && lowerMime != "application/pdf" -> "application/pdf"
                else -> mimeType
            }

            NoteBatchItem(
                uri = uri,
                displayName = displayName,
                mimeType = resolvedMime,
                sizeBytes = sizeBytes,
                kind = kind
            )
        }
    }

    suspend fun extractNotes(
        item: NoteBatchItem,
        style: NoteExtractionStyle,
        modelTier: NoteAiModelTier
    ): ExtractedNoteResult {
        val model = when (modelTier) {
            NoteAiModelTier.STANDARD -> standardModel
            NoteAiModelTier.PRO_DEEP -> proDeepModel
        }

        val promptText = buildString {
            append("Extract thorough, accurate structured notes from this ")
            append(if (item.kind == NoteSourceKind.VIDEO) "video" else "PDF document")
            append(" titled \"").append(item.displayName).append("\".\n")
            append("Style requirement: ").append(style.promptInstruction).append("\n")
            append("Include exact timestamps (for video) or page references (for PDF) in each section's reference field.")
        }

        val payloadContent = withContext(Dispatchers.IO) {
            buildMultimodalContent(item, promptText)
        }

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(payloadContent)
            }
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini returned an empty response.")
            parseStructuredNotes(jsonText, fallbackTitle = item.displayName.substringBeforeLast('.'))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Note extraction timed out for ${item.displayName}", e)
            throw IllegalStateException("Extraction timed out after 60 seconds. Try a shorter file or retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Note extraction failed for ${item.displayName}", e)
            throw e
        }
    }

    private fun buildMultimodalContent(
        item: NoteBatchItem,
        promptText: String
    ) = content {
        val rawBytes = readBytesIfWithinLimit(item)
        if (rawBytes != null) {
            inlineData(rawBytes, item.mimeType)
            text(promptText)
        } else if (item.kind == NoteSourceKind.VIDEO) {
            val frames = extractVideoKeyframes(item)
            if (frames.isEmpty()) {
                throw IllegalStateException("Unable to read video frames from ${item.displayName}.")
            }
            text("$promptText\n(File exceeded 18 MB inline limit; sampled ${frames.size} evenly-spaced keyframes from the video.)")
            frames.forEach { frame ->
                image(frame)
            }
        } else {
            val pages = renderPdfSamplePages(item)
            if (pages.isEmpty()) {
                throw IllegalStateException("Unable to read PDF pages from ${item.displayName}.")
            }
            text("$promptText\n(PDF exceeded 18 MB inline limit; rendered ${pages.size} key pages for analysis.)")
            pages.forEach { pageBmp ->
                image(pageBmp)
            }
        }
    }

    private fun readBytesIfWithinLimit(item: NoteBatchItem): ByteArray? {
        if (item.sizeBytes > MAX_INLINE_BYTES) return null

        val stream = when {
            item.localFilePath != null -> {
                val file = File(item.localFilePath)
                if (!file.exists() || file.length() > MAX_INLINE_BYTES) return null
                file.inputStream()
            }
            item.uri != null -> context.contentResolver.openInputStream(item.uri)
            else -> null
        } ?: return null

        return stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_INLINE_BYTES) {
                    return null
                }
                output.write(buffer, 0, read)
            }
            val bytes = output.toByteArray()
            if (bytes.isEmpty()) null else bytes
        }
    }

    private fun extractVideoKeyframes(item: NoteBatchItem): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            when {
                item.localFilePath != null -> retriever.setDataSource(item.localFilePath)
                item.uri != null -> retriever.setDataSource(context, item.uri)
                else -> return emptyList()
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val sampleCount = 8
            if (durationMs > 0L) {
                for (i in 0 until sampleCount) {
                    val timeUs = ((durationMs * (i + 0.5)) / sampleCount).toLong() * 1000L
                    val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        frames.add(scaleDownBitmap(bmp, 960))
                    }
                }
            } else {
                retriever.frameAtTime?.let { frames.add(scaleDownBitmap(it, 960)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting video keyframes", e)
        } finally {
            retriever.release()
        }
        return frames
    }

    private fun renderPdfSamplePages(item: NoteBatchItem): List<Bitmap> {
        val pfd: ParcelFileDescriptor? = when {
            item.localFilePath != null -> ParcelFileDescriptor.open(
                File(item.localFilePath),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            item.uri != null -> context.contentResolver.openFileDescriptor(item.uri, "r")
            else -> null
        }
        val bitmaps = mutableListOf<Bitmap>()
        pfd?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount.coerceAtMost(10)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val targetW = 900
                        val targetH = (targetW * (page.height.toFloat() / page.width.coerceAtLeast(1))).toInt()
                            .coerceIn(400, 1400)
                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bmp)
                    }
                }
            }
        }
        return bitmaps
    }

    private fun scaleDownBitmap(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / longEdge.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled != src) src.recycle()
        return scaled
    }

    private fun parseStructuredNotes(
        jsonString: String,
        fallbackTitle: String
    ): ExtractedNoteResult {
        val json = JSONObject(jsonString)
        val title = json.optString("title").ifBlank { fallbackTitle }
        val summary = json.optString("summary")

        val takeaways = mutableListOf<String>()
        val takeawaysArr = json.optJSONArray("keyTakeaways")
        if (takeawaysArr != null) {
            for (i in 0 until takeawaysArr.length()) {
                val item = takeawaysArr.optString(i)
                if (item.isNotBlank()) takeaways.add(item)
            }
        }

        val sections = mutableListOf<ExtractedNoteSection>()
        val sectionsArr = json.optJSONArray("sections")
        if (sectionsArr != null) {
            for (i in 0 until sectionsArr.length()) {
                val obj = sectionsArr.optJSONObject(i) ?: continue
                val heading = obj.optString("heading").ifBlank { "Section ${i + 1}" }
                val reference = obj.optString("reference")
                val bulletsArr = obj.optJSONArray("bullets")
                val bullets = mutableListOf<String>()
                if (bulletsArr != null) {
                    for (j in 0 until bulletsArr.length()) {
                        val b = bulletsArr.optString(j)
                        if (b.isNotBlank()) bullets.add(b)
                    }
                }
                sections.add(ExtractedNoteSection(heading = heading, reference = reference, bullets = bullets))
            }
        }

        val actionItems = mutableListOf<String>()
        val actionsArr = json.optJSONArray("actionItems")
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                val act = actionsArr.optString(i)
                if (act.isNotBlank()) actionItems.add(act)
            }
        }

        return ExtractedNoteResult(
            title = title,
            summary = summary,
            keyTakeaways = takeaways,
            sections = sections,
            actionItems = actionItems
        )
    }
}
