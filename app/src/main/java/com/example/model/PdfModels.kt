package com.example.model

import android.net.Uri
import java.util.UUID

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("US Letter", 612, 792),
    FIT_TO_IMAGE("Fit Content", 595, 842)
}

enum class PageOrientation(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class MarginOption(val label: String, val points: Int) {
    NONE("None", 0),
    NARROW("Narrow", 20),
    STANDARD("Standard", 40)
}

enum class QualityPreset(val label: String, val targetDpi: Int, val maxPixelEdge: Int) {
    BALANCED("Balanced (150 DPI)", 150, 1600),
    HIGH("High (220 DPI)", 220, 2200),
    PRINT("Print (300 DPI)", 300, 2800)
}

enum class SourceKind(val badge: String) {
    IMAGE("IMG"),
    TEXT("TXT"),
    MARKDOWN("MD"),
    CSV("CSV"),
    HTML("HTML"),
    DOCX("DOCX"),
    PDF("PDF")
}

data class QueuedSourceItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sourceKind: SourceKind,
    val rotationDegrees: Int = 0
)

enum class PdfFontFamily(
    val label: String,
    val description: String
) {
    CLASSIC_SANS("Inter Sans", "Clean modern sans-serif"),
    EDITORIAL_SERIF("Merriweather", "Warm book serif"),
    LITERARY_LORA("Lora", "Academic & editorial serif"),
    DISPLAY_PLAYFAIR("Playfair", "High-contrast serif"),
    MONO_CODE("JetBrains Mono", "Fixed-width technical")
}

enum class PdfFontSizePreset(
    val label: String,
    val bodySp: Float,
    val h1Sp: Float,
    val h2Sp: Float,
    val h3Sp: Float
) {
    COMPACT("Compact (9pt)", 9f, 15.5f, 12.5f, 11f),
    STANDARD("Standard (10.5pt)", 10.5f, 18f, 14.5f, 12.5f),
    COMFORT("Large (12.5pt)", 12.5f, 21f, 16.5f, 14f),
    EXTRA_LARGE("XL (14.5pt)", 14.5f, 24f, 19f, 16f)
}

enum class PdfLineSpacingPreset(
    val label: String,
    val multiplier: Float
) {
    TIGHT("Compact (1.05×)", 1.05f),
    BALANCED("Balanced (1.2×)", 1.20f),
    RELAXED("Airy (1.45×)", 1.45f)
}

enum class PdfTextAlignment(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

enum class PdfTextColorPreset(
    val label: String,
    val bodyArgb: Int,
    val headingArgb: Int,
    val mutedArgb: Int
) {
    CHARCOAL("Charcoal Ink", 0xFF222428.toInt(), 0xFF18191C.toInt(), 0xFF6B7280.toInt()),
    MIDNIGHT_NAVY("Midnight Blue", 0xFF1E293B.toInt(), 0xFF1D4ED8.toInt(), 0xFF64748B.toInt()),
    FOREST_INK("Forest Green", 0xFF1F2937.toInt(), 0xFF115E59.toInt(), 0xFF4B5563.toInt()),
    BURGUNDY_INK("Burgundy", 0xFF292524.toInt(), 0xFF881337.toInt(), 0xFF78716C.toInt()),
    SEPIA_INK("Warm Sepia", 0xFF43302B.toInt(), 0xFF7C2D12.toInt(), 0xFF78716C.toInt()),
    SNOW_INK("Snow Light", 0xFFF3F4F6.toInt(), 0xFF60A5FA.toInt(), 0xFF9CA3AF.toInt())
}

enum class PdfPageTheme(
    val label: String,
    val bgArgb: Int,
    val ruleArgb: Int,
    val tableHeaderArgb: Int,
    val tableStripeArgb: Int
) {
    PURE_WHITE("Crisp White", 0xFFFFFFFF.toInt(), 0xFFD7D1C7.toInt(), 0xFFEDE7DD.toInt(), 0xFFF7F4EF.toInt()),
    WARM_CREAM("Warm Ivory", 0xFFFAF6EE.toInt(), 0xFFE2D9C8.toInt(), 0xFFEDE4D3.toInt(), 0xFFF4EFE4.toInt()),
    SOFT_SLATE("Cool Slate", 0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt(), 0xFFE2E8F0.toInt(), 0xFFF8FAFC.toInt()),
    DARK_OBSIDIAN("Dark Obsidian", 0xFF181A1F.toInt(), 0xFF334155.toInt(), 0xFF242832.toInt(), 0xFF1F232C.toInt())
}

enum class ViewerThemeMode(val label: String) {
    ORIGINAL("Original"),
    SEPIA("Sepia"),
    NIGHT("Night Invert")
}

data class ConversionSettings(
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: PageOrientation = PageOrientation.AUTO,
    val margin: MarginOption = MarginOption.NARROW,
    val quality: QualityPreset = QualityPreset.HIGH,
    val enableOcr: Boolean = true,
    val addPageNumbers: Boolean = true,
    val fontFamily: PdfFontFamily = PdfFontFamily.CLASSIC_SANS,
    val fontSize: PdfFontSizePreset = PdfFontSizePreset.STANDARD,
    val lineSpacing: PdfLineSpacingPreset = PdfLineSpacingPreset.BALANCED,
    val textAlignment: PdfTextAlignment = PdfTextAlignment.LEFT,
    val textColor: PdfTextColorPreset = PdfTextColorPreset.CHARCOAL,
    val pageTheme: PdfPageTheme = PdfPageTheme.PURE_WHITE,
    val includeHeaderTitle: Boolean = true,
    val showWatermark: Boolean = false,
    val watermarkText: String = ""
)

enum class BatchStatus {
    QUEUED,
    RUNNING_OCR,
    CONVERTING,
    UPLOADING,
    COMPLETED,
    FAILED
}

data class BatchItemState(
    val id: String = UUID.randomUUID().toString(),
    val sourceItem: QueuedSourceItem,
    val outputFileName: String,
    val status: BatchStatus = BatchStatus.QUEUED,
    val progressMessage: String = "Ready",
    val resultDocumentId: Long? = null,
    val pageCount: Int = 0,
    val outputSizeBytes: Long = 0L,
    val ocrCharCount: Int = 0,
    val uploadedProvider: CloudProvider? = null,
    val errorMessage: String? = null
)

enum class CloudProvider(
    val displayName: String,
    val subtitle: String,
    val folderLabel: String
) {
    GOOGLE_DRIVE(
        displayName = "Google Drive",
        subtitle = "Save directly to your Drive workspace",
        folderLabel = "My Drive / FolioPDF"
    ),
    DROPBOX(
        displayName = "Dropbox",
        subtitle = "Sync PDFs to your Dropbox cloud folder",
        folderLabel = "Apps / FolioPDF"
    ),
    ONEDRIVE(
        displayName = "OneDrive",
        subtitle = "Upload directly to Microsoft OneDrive",
        folderLabel = "Files / FolioPDF"
    )
}

data class CloudAccountState(
    val provider: CloudProvider,
    val isConnected: Boolean = false,
    val accountEmail: String? = null,
    val accountName: String? = null,
    val expiresAtMs: Long = 0L,
    val isConfigured: Boolean = true,
    val configHint: String? = null
)

data class CloudUploadRecord(
    val id: String = UUID.randomUUID().toString(),
    val provider: CloudProvider,
    val fileName: String,
    val remotePathOrLink: String?,
    val remoteFileId: String? = null,
    val uploadedAtMs: Long,
    val success: Boolean,
    val message: String
)

data class RemoteDrivePdfItem(
    val driveFileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedTimeMs: Long,
    val md5Checksum: String? = null,
    val webViewLink: String? = null,
    val pageCountHint: Int = 0,
    val ocrEnabledHint: Boolean = false,
    val isPasswordProtectedHint: Boolean = false,
    val securitySummaryHint: String? = null,
    val localDocumentId: Long? = null
) {
    val isSyncedLocally: Boolean
        get() = localDocumentId != null
}

enum class VaultFilter(val label: String) {
    ALL("All PDFs"),
    OCR("OCR Searchable"),
    PROTECTED("Protected"),
    BATCH("Batch Exports"),
    CLOUD("Cloud Synced")
}

enum class PdfEncryptionStrength(val label: String, val keyBits: Int) {
    AES_128("128-bit Standard", 128),
    AES_256("256-bit Strong AES", 256)
}

data class PdfSecurityConfig(
    val enabled: Boolean = false,
    val userPassword: String = "",
    val ownerPassword: String = "",
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowModifying: Boolean = false,
    val allowAnnotations: Boolean = true,
    val strength: PdfEncryptionStrength = PdfEncryptionStrength.AES_128
) {
    fun hasAnyRestrictionOrPassword(): Boolean {
        return enabled && (
            userPassword.isNotEmpty() ||
                ownerPassword.isNotEmpty() ||
                !allowPrinting ||
                !allowCopying ||
                !allowModifying ||
                !allowAnnotations
            )
    }

    fun buildSummary(): String {
        val parts = mutableListOf<String>()
        if (userPassword.isNotEmpty()) parts.add("Password")
        if (!allowPrinting) parts.add("No Print")
        if (!allowCopying) parts.add("No Copy")
        if (!allowModifying) parts.add("No Edit")
        if (!allowAnnotations) parts.add("No Annotate")
        if (parts.isEmpty() && enabled) parts.add("Owner Lock")
        return parts.joinToString(" · ")
    }
}

data class PdfMetadataConfig(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "Folio PDF"
) {
    fun hasAnyCustomMetadata(): Boolean {
        return title.isNotBlank() ||
            author.isNotBlank() ||
            subject.isNotBlank() ||
            keywords.isNotBlank() ||
            (creator.isNotBlank() && creator != "Folio PDF")
    }

    fun parsedKeywordList(): List<String> {
        return keywords.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun buildSummary(): String {
        val parts = mutableListOf<String>()
        if (title.isNotBlank()) parts.add(title.take(22))
        if (author.isNotBlank()) parts.add("By ${author.take(18)}")
        if (subject.isNotBlank()) parts.add("Subject")
        val kwCount = parsedKeywordList().size
        if (kwCount > 0) parts.add("$kwCount tag${if (kwCount == 1) "" else "s"}")
        return parts.joinToString(" · ")
    }
}

enum class PrintPaperSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("US Letter", 612, 792),
    LEGAL("US Legal", 612, 1008),
    A5("A5", 420, 595),
    PHOTO_4X6("4×6 Photo", 288, 432)
}

enum class PrintOrientationMode(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PrintColorMode(val label: String) {
    COLOR("Full Color"),
    GRAYSCALE("Grayscale")
}

enum class PrintPagesPerSheet(val count: Int, val label: String) {
    ONE(1, "1-up"),
    TWO(2, "2-up"),
    FOUR(4, "4-up"),
    SIX(6, "6-up"),
    NINE(9, "9-up")
}

enum class PrintLayoutTool(val label: String, val subtitle: String) {
    STANDARD("Standard", "Normal single-page print"),
    N_UP("N-Up Grid", "2, 4, 6, or 9 pages per sheet"),
    BOOKLET("Booklet", "Folded saddle-stitch layout"),
    POSTER_FIT("Edge-to-Edge", "Full-bleed borderless fit")
}

enum class PrintPageSubset(val label: String) {
    ALL("All Pages"),
    ODD_ONLY("Odd Only"),
    EVEN_ONLY("Even Only"),
    CUSTOM("Custom Range")
}

enum class PrintMarginPreset(val label: String, val points: Int) {
    NONE("None", 0),
    NARROW("Narrow", 18),
    NORMAL("Normal", 36),
    WIDE("Wide", 54)
}

enum class PrintScaleMode(val label: String) {
    FIT_PAGE("Fit to Page"),
    FILL_PAGE("Fill Sheet"),
    ACTUAL_SIZE("Actual Size (100%)")
}

data class PrintConfig(
    val paperSize: PrintPaperSize = PrintPaperSize.A4,
    val orientation: PrintOrientationMode = PrintOrientationMode.PORTRAIT,
    val colorMode: PrintColorMode = PrintColorMode.COLOR,
    val pagesPerSheet: PrintPagesPerSheet = PrintPagesPerSheet.ONE,
    val layoutTool: PrintLayoutTool = PrintLayoutTool.STANDARD,
    val pageSubset: PrintPageSubset = PrintPageSubset.ALL,
    val customPageRange: String = "all",
    val marginPreset: PrintMarginPreset = PrintMarginPreset.NARROW,
    val scaleMode: PrintScaleMode = PrintScaleMode.FIT_PAGE,
    val copies: Int = 1,
    val dpi: Int = 300,
    val showPageBorders: Boolean = false,
    val showHeaderFooter: Boolean = false
)

enum class QrContentType(val label: String) {
    TEXT_URL("Text / URL"),
    WIFI("Wi-Fi"),
    EMAIL("Email"),
    PHONE("Phone"),
    VCARD("Contact Card")
}

enum class QrColorPreset(val label: String, val fgArgb: Int, val bgArgb: Int) {
    CLASSIC("Classic Noir", 0xFF141619.toInt(), 0xFFFFFFFF.toInt()),
    COBALT("Cobalt Ink", 0xFF1E3A8A.toInt(), 0xFFF8FAFC.toInt()),
    FOREST("Botanical", 0xFF115E59.toInt(), 0xFFF7FEE7.toInt()),
    BURGUNDY("Burgundy", 0xFF881337.toInt(), 0xFFFFF1F2.toInt())
}

data class ScannedBarcodeItem(
    val id: String = UUID.randomUUID().toString(),
    val rawValue: String,
    val displayValue: String,
    val formatLabel: String,
    val typeLabel: String,
    val isUrl: Boolean,
    val scannedAtMs: Long = System.currentTimeMillis()
)

data class ScannedDocPage(
    val id: String = UUID.randomUUID().toString(),
    val imageUri: Uri
)

enum class WatermarkType(val label: String) {
    TEXT("Text Stamp"),
    IMAGE("Image / Logo")
}

enum class WatermarkPlacement(val label: String) {
    DIAGONAL_CENTER("Diagonal"),
    CENTER("Center"),
    TILED_GRID("Tiled 3×3"),
    TOP_HEADER("Top Header"),
    BOTTOM_FOOTER("Bottom Footer"),
    BOTTOM_RIGHT("Corner")
}

enum class WatermarkColorPreset(val label: String, val argb: Int) {
    CHARCOAL("Charcoal", 0xFF1F2937.toInt()),
    CRIMSON("Crimson", 0xFFB91C1C.toInt()),
    ROYAL_NAVY("Navy", 0xFF1D4ED8.toInt()),
    TEAL("Teal", 0xFF0F766E.toInt()),
    SLATE_GRAY("Slate", 0xFF64748B.toInt()),
    WHITE_LIGHT("Light Ink", 0xFFE5E7EB.toInt())
}

enum class WatermarkTextSize(val label: String, val pt: Float) {
    SMALL("Small", 24f),
    MEDIUM("Medium", 38f),
    LARGE("Large", 54f),
    EXTRA_LARGE("XL", 72f)
}

data class PdfWatermarkConfig(
    val enabled: Boolean = false,
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "© CONFIDENTIAL",
    val imageUriString: String? = null,
    val imageDisplayName: String = "",
    val placement: WatermarkPlacement = WatermarkPlacement.DIAGONAL_CENTER,
    val color: WatermarkColorPreset = WatermarkColorPreset.CHARCOAL,
    val textSize: WatermarkTextSize = WatermarkTextSize.LARGE,
    val imageWidthFraction: Float = 0.36f,
    val opacity: Float = 0.28f,
    val rotationDegrees: Float = -35f,
    val pageRangeExpr: String = "all"
) {
    fun isReadyToApply(): Boolean {
        return when (type) {
            WatermarkType.TEXT -> text.isNotBlank()
            WatermarkType.IMAGE -> !imageUriString.isNullOrBlank()
        }
    }

    fun isActive(): Boolean = enabled && isReadyToApply()

    fun buildSummary(): String {
        if (!enabled) return "Disabled"
        val pct = (opacity.coerceIn(0.05f, 1f) * 100).toInt()
        return when (type) {
            WatermarkType.TEXT -> {
                val preview = text.trim().ifBlank { "Text Stamp" }.take(22)
                "Text: \"$preview\" · ${placement.label} · ${pct}%"
            }
            WatermarkType.IMAGE -> {
                val imgName = imageDisplayName.ifBlank { "Image Stamp" }.take(20)
                "Image: $imgName · ${placement.label} · ${pct}%"
            }
        }
    }
}

enum class PdfTranslationTone(
    val label: String,
    val instruction: String
) {
    NATURAL(
        label = "Natural",
        instruction = "Translate naturally and accurately while preserving the document structure, headings, lists, and key details."
    ),
    FORMAL(
        label = "Formal",
        instruction = "Use a polished, formal business and academic register suitable for official reports or legal/professional documents."
    ),
    SIMPLE(
        label = "Simple",
        instruction = "Use clear, plain, beginner-friendly language that is easy to understand."
    ),
    BILINGUAL(
        label = "Bilingual",
        instruction = "Provide a bilingual output for every section: include the original source paragraph/sentence first, immediately followed by its accurate translation."
    )
}

object TranslationLanguageCatalog {
    const val AUTO_DETECT = "Auto-Detect"

    val popularLanguages: List<String> = listOf(
        "English",
        "Spanish",
        "French",
        "German",
        "Arabic",
        "Hindi",
        "Bengali",
        "Chinese (Simplified)",
        "Chinese (Traditional)",
        "Japanese",
        "Korean",
        "Portuguese",
        "Russian",
        "Italian",
        "Turkish",
        "Urdu",
        "Tamil",
        "Telugu",
        "Marathi",
        "Vietnamese",
        "Indonesian",
        "Thai",
        "Persian (Farsi)",
        "Dutch",
        "Polish",
        "Ukrainian",
        "Malay",
        "Swahili"
    )
}

data class TranslatedPdfPage(
    val pageNumber: Int,
    val heading: String,
    val translatedText: String
)

data class PdfTranslationResult(
    val detectedSourceLanguage: String,
    val targetLanguage: String,
    val translatedTitle: String,
    val tone: PdfTranslationTone,
    val pages: List<TranslatedPdfPage>
) {
    fun toPlainText(): String = buildString {
        append(translatedTitle).append("\n")
        append(detectedSourceLanguage).append(" → ").append(targetLanguage)
        append(" (").append(tone.label).append(")\n\n")
        pages.forEach { page ->
            append("--- Page ").append(page.pageNumber)
            if (page.heading.isNotBlank()) {
                append(": ").append(page.heading)
            }
            append(" ---\n")
            append(page.translatedText.trim()).append("\n\n")
        }
    }.trim()

    fun toMarkdown(): String = buildString {
        append("# ").append(translatedTitle.ifBlank { "Translated Document" }).append("\n\n")
        append("> ").append(detectedSourceLanguage).append(" → ").append(targetLanguage)
            .append(" · ").append(tone.label).append(" Translation\n\n")
        pages.forEachIndexed { idx, page ->
            val headingText = page.heading.ifBlank { "Page ${page.pageNumber}" }
            append("## Page ").append(page.pageNumber)
            if (!headingText.equals("Page ${page.pageNumber}", ignoreCase = true)) {
                append(" · ").append(headingText)
            }
            append("\n\n")
            append(page.translatedText.trim()).append("\n\n")
            if (idx < pages.lastIndex) {
                append("---\n\n")
            }
        }
    }.trim()
}

