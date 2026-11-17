package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrWordBox(
    val text: String,
    val boundingBox: Rect
)

data class OcrLineBox(
    val text: String,
    val boundingBox: Rect,
    val words: List<OcrWordBox>
)

data class OcrPageResult(
    val fullText: String,
    val lines: List<OcrLineBox>
)

class OcrProcessor {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): OcrPageResult = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText: Text = recognizer.process(image).awaitResult()
            val lines = buildList {
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val lineRect = line.boundingBox ?: continue
                        val words = line.elements.mapNotNull { element ->
                            val rect = element.boundingBox ?: return@mapNotNull null
                            if (element.text.isBlank()) null
                            else OcrWordBox(text = element.text, boundingBox = rect)
                        }
                        if (line.text.isNotBlank()) {
                            add(
                                OcrLineBox(
                                    text = line.text,
                                    boundingBox = lineRect,
                                    words = words
                                )
                            )
                        }
                    }
                }
            }
            OcrPageResult(
                fullText = visionText.text.trim(),
                lines = lines
            )
        } catch (_: Exception) {
            OcrPageResult(fullText = "", lines = emptyList())
        }
    }
}
