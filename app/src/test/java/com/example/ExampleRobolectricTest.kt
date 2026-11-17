package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.ai.PdfPagePayload
import com.example.data.ai.PdfTranslationAiRepository
import com.example.data.pdf.DocumentContentParser
import com.example.data.pdf.OcrProcessor
import com.example.data.pdf.PdfConversionEngine
import com.example.data.pdf.PdfMetadataEngine
import com.example.data.pdf.PdfPrintEngine
import com.example.data.pdf.PdfSecurityEngine
import com.example.data.pdf.PdfToolboxEngine
import com.example.data.pdf.PdfWatermarkEngine
import com.example.data.pdf.QrAndScannerEngine
import com.example.model.ConversionSettings
import com.example.model.PdfEncryptionStrength
import com.example.model.PdfFontFamily
import com.example.model.PdfFontSizePreset
import com.example.model.PdfLineSpacingPreset
import com.example.model.PdfMetadataConfig
import com.example.model.PdfPageTheme
import com.example.model.PdfSecurityConfig
import com.example.model.PdfTextAlignment
import com.example.model.PdfTextColorPreset
import com.example.model.PdfTranslationResult
import com.example.model.PdfTranslationTone
import com.example.model.PdfWatermarkConfig
import com.example.model.PrintConfig
import com.example.model.PrintLayoutTool
import com.example.model.PrintOrientationMode
import com.example.model.PrintPageSubset
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.QrColorPreset
import com.example.model.QrContentType
import com.example.model.SourceKind
import com.example.model.TranslatedPdfPage
import com.example.model.TranslationLanguageCatalog
import com.example.model.WatermarkColorPreset
import com.example.model.WatermarkPlacement
import com.example.model.WatermarkTextSize
import com.example.model.WatermarkType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Folio PDF", appName)
    }

    @Test
    fun `detects supported image and document source kinds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = DocumentContentParser(context)

        assertEquals(SourceKind.IMAGE, parser.detectSourceKind("scan.jpg", "image/jpeg"))
        assertEquals(SourceKind.MARKDOWN, parser.detectSourceKind("notes.md", "text/markdown"))
        assertEquals(SourceKind.CSV, parser.detectSourceKind("budget.csv", "text/csv"))
        assertEquals(SourceKind.HTML, parser.detectSourceKind("summary.html", "text/html"))
        assertEquals(
            SourceKind.DOCX,
            parser.detectSourceKind(
                "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertEquals(SourceKind.PDF, parser.detectSourceKind("archive.pdf", "application/pdf"))
    }

    @Test
    fun `sanitizes pdf output filenames safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfConversionEngine(context, DocumentContentParser(context), OcrProcessor())

        val sanitized = engine.sanitizeFileName("Q4 Report: Final/2026?")
        assertTrue(sanitized.isNotBlank())
        assertTrue(!sanitized.contains('/'))
        assertTrue(!sanitized.contains('?'))
        assertEquals("Q4_Report_Final_2026", sanitized)
    }

    @Test
    fun `parses pdf page range expressions with bounds clamping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val toolbox = PdfToolboxEngine(context, OcrProcessor())

        assertEquals(listOf(0, 1, 2, 3, 4), toolbox.parsePageRange("all", 5))
        assertEquals(listOf(0, 1, 2, 4), toolbox.parsePageRange("1-3, 5", 5))
        assertEquals(listOf(1, 2, 3), toolbox.parsePageRange("2-10", 4))
        assertEquals(emptyList<Int>(), toolbox.parsePageRange("1-3", 0))
    }

    @Test
    fun `encrypts pdf with password and enforces print and copy permissions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempPdf = File(context.cacheDir, "security_test_128.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(tempPdf)
        }

        val secEngine = PdfSecurityEngine(context)
        val config = PdfSecurityConfig(
            enabled = true,
            userPassword = "open-user-123",
            ownerPassword = "master-owner-456",
            allowPrinting = false,
            allowCopying = false,
            allowModifying = false,
            allowAnnotations = false,
            strength = PdfEncryptionStrength.AES_128
        )
        val encryptedSize = secEngine.applyProtectionInPlace(tempPdf.absolutePath, config)
        assertTrue(encryptedSize > 0L)
        assertTrue(secEngine.needsPasswordToOpen(tempPdf.absolutePath))

        assertTrue(secEngine.verifyPasswordAndInspect(tempPdf.absolutePath, "wrong-pwd").isFailure)

        val inspection = secEngine.verifyPasswordAndInspect(tempPdf.absolutePath, "open-user-123").getOrThrow()
        assertTrue(inspection.isEncrypted)
        assertFalse(inspection.allowPrinting)
        assertFalse(inspection.allowCopying)
        assertFalse(inspection.allowModifying)
        assertFalse(inspection.allowAnnotations)

        tempPdf.delete()
    }

    @Test
    fun `encrypts pdf with 256-bit AES and permission restrictions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempPdf = File(context.cacheDir, "security_test_256.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(tempPdf)
        }

        val secEngine = PdfSecurityEngine(context)
        val config = PdfSecurityConfig(
            enabled = true,
            userPassword = "",
            ownerPassword = "owner-only-789",
            allowPrinting = false,
            allowCopying = false,
            allowModifying = false,
            allowAnnotations = true,
            strength = PdfEncryptionStrength.AES_256
        )
        secEngine.applyProtectionInPlace(tempPdf.absolutePath, config)
        assertFalse(secEngine.needsPasswordToOpen(tempPdf.absolutePath))

        val inspection = secEngine.verifyPasswordAndInspect(tempPdf.absolutePath, "").getOrThrow()
        assertTrue(inspection.isEncrypted)
        assertFalse(inspection.allowPrinting)
        assertFalse(inspection.allowCopying)
        assertFalse(inspection.allowModifying)
        assertTrue(inspection.allowAnnotations)

        tempPdf.delete()
    }

    @Test
    fun `computes booklet imposition slots and N-up grouping accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val printEngine = PdfPrintEngine(context)

        // 5-page booklet pads to 8 slots across 4 2-up sheets:
        // Front 1: [blank(-1), 0], Back 1: [1, blank(-1)], Front 2: [blank(-1), 2], Back 2: [3, 4]
        val bookletPlan = printEngine.computeSheetPlan(
            totalPages = 5,
            config = PrintConfig(
                layoutTool = PrintLayoutTool.BOOKLET,
                pagesPerSheet = PrintPagesPerSheet.TWO,
                paperSize = PrintPaperSize.A4,
                orientation = PrintOrientationMode.AUTO
            )
        )
        assertEquals(4, bookletPlan.sheets.size)
        assertEquals(listOf(-1, 0), bookletPlan.sheets[0].pageSlots)
        assertEquals(listOf(1, -1), bookletPlan.sheets[1].pageSlots)
        assertEquals(listOf(-1, 2), bookletPlan.sheets[2].pageSlots)
        assertEquals(listOf(3, 4), bookletPlan.sheets[3].pageSlots)
        // AUTO + 2-up resolves to landscape A4 (842 x 595 pt)
        assertTrue(bookletPlan.sheetWidthPt > bookletPlan.sheetHeightPt)

        // 4-up N-Up with 6 pages -> 2 sheets ([0,1,2,3], [4,5])
        val nUpPlan = printEngine.computeSheetPlan(
            totalPages = 6,
            config = PrintConfig(
                layoutTool = PrintLayoutTool.N_UP,
                pagesPerSheet = PrintPagesPerSheet.FOUR,
                orientation = PrintOrientationMode.PORTRAIT
            )
        )
        assertEquals(2, nUpPlan.sheets.size)
        assertEquals(listOf(0, 1, 2, 3), nUpPlan.sheets[0].pageSlots)
        assertEquals(listOf(4, 5), nUpPlan.sheets[1].pageSlots)
        assertTrue(nUpPlan.sheetHeightPt > nUpPlan.sheetWidthPt)
    }

    @Test
    fun `filters odd, even, and custom page ranges in print plan`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val printEngine = PdfPrintEngine(context)

        val oddPlan = printEngine.computeSheetPlan(
            totalPages = 6,
            config = PrintConfig(pageSubset = PrintPageSubset.ODD_ONLY)
        )
        assertEquals(3, oddPlan.selectedPageCount)
        assertEquals(listOf(0, 2, 4), oddPlan.sheets.flatMap { it.pageSlots })

        val evenPlan = printEngine.computeSheetPlan(
            totalPages = 6,
            config = PrintConfig(pageSubset = PrintPageSubset.EVEN_ONLY)
        )
        assertEquals(3, evenPlan.selectedPageCount)
        assertEquals(listOf(1, 3, 5), evenPlan.sheets.flatMap { it.pageSlots })

        val customPlan = printEngine.computeSheetPlan(
            totalPages = 8,
            config = PrintConfig(pageSubset = PrintPageSubset.CUSTOM, customPageRange = "2-4, 6")
        )
        assertEquals(4, customPlan.selectedPageCount)
        assertEquals(listOf(1, 2, 3, 5), customPlan.sheets.flatMap { it.pageSlots })
    }

    @Test
    fun `computes 4-in-1 and 6-in-1 page converter sheet plans accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val printEngine = PdfPrintEngine(context)

        val ninePageFourInOne = printEngine.computeSheetPlan(
            totalPages = 9,
            config = PrintConfig(
                layoutTool = PrintLayoutTool.N_UP,
                pagesPerSheet = PrintPagesPerSheet.FOUR,
                orientation = PrintOrientationMode.AUTO
            )
        )
        assertEquals(3, ninePageFourInOne.sheets.size)
        assertEquals(listOf(0, 1, 2, 3), ninePageFourInOne.sheets[0].pageSlots)
        assertEquals(listOf(4, 5, 6, 7), ninePageFourInOne.sheets[1].pageSlots)
        assertEquals(listOf(8), ninePageFourInOne.sheets[2].pageSlots)
        // 4-in-1 (2x2 grid) uses portrait orientation in AUTO mode
        assertTrue(ninePageFourInOne.sheetHeightPt > ninePageFourInOne.sheetWidthPt)

        val sevenPageSixInOne = printEngine.computeSheetPlan(
            totalPages = 7,
            config = PrintConfig(
                layoutTool = PrintLayoutTool.N_UP,
                pagesPerSheet = PrintPagesPerSheet.SIX,
                orientation = PrintOrientationMode.AUTO
            )
        )
        assertEquals(2, sevenPageSixInOne.sheets.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), sevenPageSixInOne.sheets[0].pageSlots)
        assertEquals(listOf(6), sevenPageSixInOne.sheets[1].pageSlots)
        assertTrue(sevenPageSixInOne.sheetWidthPt > sevenPageSixInOne.sheetHeightPt)
    }

    @Test
    fun `embeds and reads PDF metadata round-trip and preserves under encryption`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempPdf = File(context.cacheDir, "metadata_test.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(tempPdf)
        }

        val metaEngine = PdfMetadataEngine(context)
        val inputMeta = PdfMetadataConfig(
            title = "Annual Summary",
            author = "Ada Lovelace",
            subject = "Mathematics & Computing",
            keywords = "algorithm, analytical, notes",
            creator = "Folio Studio"
        )
        assertEquals(listOf("algorithm", "analytical", "notes"), inputMeta.parsedKeywordList())

        metaEngine.applyMetadataInPlace(tempPdf.absolutePath, inputMeta)

        val readResult = metaEngine.readMetadata(uri = null, localFilePath = tempPdf.absolutePath).getOrThrow()
        assertFalse(readResult.requiresPassword)
        assertEquals("Annual Summary", readResult.metadata.title)
        assertEquals("Ada Lovelace", readResult.metadata.author)
        assertEquals("Mathematics & Computing", readResult.metadata.subject)
        assertEquals("algorithm, analytical, notes", readResult.metadata.keywords)
        assertEquals("Folio Studio", readResult.metadata.creator)

        val secEngine = PdfSecurityEngine(context)
        secEngine.applyProtectionInPlace(
            filePath = tempPdf.absolutePath,
            security = PdfSecurityConfig(
                enabled = true,
                userPassword = "secret",
                allowPrinting = false
            )
        )
        val lockedRead = metaEngine.readMetadata(uri = null, localFilePath = tempPdf.absolutePath, password = "secret").getOrThrow()
        assertFalse(lockedRead.requiresPassword)
        assertEquals("Annual Summary", lockedRead.metadata.title)

        tempPdf.delete()
    }

    @Test
    fun `builds QR payloads and renders styled QR bitmap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val qrEngine = QrAndScannerEngine(context)

        assertEquals(
            "WIFI:T:WPA;S:StudyHub;P:pass123;;",
            qrEngine.buildQrPayload(QrContentType.WIFI, "StudyHub", "pass123", wifiSecurity = "WPA")
        )
        assertEquals(
            "mailto:hello@example.com?subject=Notes",
            qrEngine.buildQrPayload(QrContentType.EMAIL, "hello@example.com", "Notes")
        )
        val vcard = qrEngine.buildQrPayload(QrContentType.VCARD, "Grace Hopper", "+15550199", "grace@navy.mil")
        assertTrue(vcard.contains("FN:Grace Hopper"))
        assertTrue(vcard.contains("TEL:+15550199"))

        val bmp = qrEngine.generateQrBitmap(
            payload = "https://foliopdf.app",
            sizePx = 320,
            preset = QrColorPreset.FOREST,
            caption = "Scan Me"
        )
        assertTrue(bmp.width == 320 && bmp.height > 320)
        bmp.recycle()
    }

    @Test
    fun `persists custom PDF fonts colors page themes and parses markdown blocks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = com.example.data.local.CloudTokenStore(context)
        val parser = DocumentContentParser(context)

        val customSettings = ConversionSettings(
            fontFamily = PdfFontFamily.EDITORIAL_SERIF,
            fontSize = PdfFontSizePreset.COMFORT,
            lineSpacing = PdfLineSpacingPreset.RELAXED,
            textAlignment = PdfTextAlignment.CENTER,
            textColor = PdfTextColorPreset.MIDNIGHT_NAVY,
            pageTheme = PdfPageTheme.WARM_CREAM,
            includeHeaderTitle = false,
            showWatermark = true,
            watermarkText = "CONFIDENTIAL"
        )
        store.saveSettings(customSettings)

        val restored = com.example.data.local.CloudTokenStore(context).settingsFlow.first()
        assertEquals(PdfFontFamily.EDITORIAL_SERIF, restored.fontFamily)
        assertEquals(PdfFontSizePreset.COMFORT, restored.fontSize)
        assertEquals(PdfLineSpacingPreset.RELAXED, restored.lineSpacing)
        assertEquals(PdfTextAlignment.CENTER, restored.textAlignment)
        assertEquals(PdfTextColorPreset.MIDNIGHT_NAVY, restored.textColor)
        assertEquals(PdfPageTheme.WARM_CREAM, restored.pageTheme)
        assertFalse(restored.includeHeaderTitle)
        assertTrue(restored.showWatermark)
        assertEquals("CONFIDENTIAL", restored.watermarkText)

        val blocks = parser.parseMarkdown(
            "# Chapter One\n\nParagraph text.\n\n- First bullet\n> Key quote"
        )
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Heading && it.text == "Chapter One" })
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Bullet && it.text == "First bullet" })
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Quote && it.text == "Key quote" })
    }

    @Test
    fun `stamps text copyright watermark onto selective PDF pages`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourcePdf = File(context.cacheDir, "wm_source_3p.pdf")
        PDDocument().use { doc ->
            repeat(3) { doc.addPage(PDPage()) }
            doc.save(sourcePdf)
        }

        val wmEngine = PdfWatermarkEngine(context)
        val config = PdfWatermarkConfig(
            enabled = true,
            type = WatermarkType.TEXT,
            text = "© 2026 Folio Copyright",
            placement = WatermarkPlacement.DIAGONAL_CENTER,
            color = WatermarkColorPreset.CRIMSON,
            textSize = WatermarkTextSize.LARGE,
            opacity = 0.35f,
            rotationDegrees = -35f,
            pageRangeExpr = "1, 3"
        )
        val generated = wmEngine.watermarkExistingPdfToNewFile(
            uri = null,
            localFilePath = sourcePdf.absolutePath,
            existingPassword = null,
            outputBaseName = "wm_out_3p",
            watermark = config
        )

        val outFile = File(generated.filePath)
        assertTrue(outFile.exists())
        assertEquals(3, generated.pageCount)

        PDDocument.load(outFile).use { doc ->
            val stripper = PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = 1
            val page1Compact = stripper.getText(doc).replace(Regex("\\s+"), "")
            assertTrue("Expected copyright watermark on page 1, got: $page1Compact", page1Compact.contains("©2026FolioCopyright"))

            stripper.startPage = 2
            stripper.endPage = 2
            val page2Compact = stripper.getText(doc).replace(Regex("\\s+"), "")
            assertFalse("Did not expect Folio watermark on page 2", page2Compact.contains("Folio"))
            assertFalse("Did not expect Copyright watermark on page 2", page2Compact.contains("Copyright"))

            stripper.startPage = 3
            stripper.endPage = 3
            val page3Compact = stripper.getText(doc).replace(Regex("\\s+"), "")
            assertTrue("Expected copyright watermark on page 3, got: $page3Compact", page3Compact.contains("©2026FolioCopyright"))
        }

        sourcePdf.delete()
        outFile.delete()
    }

    @Test
    fun `stamps transparent tiled image watermark onto PDF pages in place`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourcePdf = File(context.cacheDir, "wm_img_target.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(sourcePdf)
        }
        val initialSize = sourcePdf.length()

        val logoFile = File(context.cacheDir, "wm_logo.png")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.argb(180, 29, 78, 216))
        FileOutputStream(logoFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val wmEngine = PdfWatermarkEngine(context)
        val newSize = wmEngine.applyWatermarkInPlace(
            filePath = sourcePdf.absolutePath,
            watermark = PdfWatermarkConfig(
                enabled = true,
                type = WatermarkType.IMAGE,
                imageUriString = Uri.fromFile(logoFile).toString(),
                imageDisplayName = "wm_logo.png",
                placement = WatermarkPlacement.TILED_GRID,
                imageWidthFraction = 0.28f,
                opacity = 0.30f
            )
        )

        assertTrue(newSize > initialSize)
        PDDocument.load(sourcePdf).use { doc ->
            val page = doc.getPage(0)
            assertTrue(page.resources.xObjectNames.iterator().hasNext())
        }

        sourcePdf.delete()
        logoFile.delete()
    }

    @Test
    fun `watermarks password-protected PDF only with valid password`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lockedPdf = File(context.cacheDir, "wm_locked_src.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(lockedPdf)
        }
        val secEngine = PdfSecurityEngine(context)
        secEngine.applyProtectionInPlace(
            filePath = lockedPdf.absolutePath,
            security = PdfSecurityConfig(enabled = true, userPassword = "wm-secret")
        )

        val wmEngine = PdfWatermarkEngine(context)
        val config = PdfWatermarkConfig(enabled = true, text = "CONFIDENTIAL")

        val wrongResult = runCatching {
            wmEngine.watermarkExistingPdfToNewFile(
                uri = null,
                localFilePath = lockedPdf.absolutePath,
                existingPassword = "wrong",
                outputBaseName = "wm_locked_fail",
                watermark = config
            )
        }
        assertTrue(wrongResult.isFailure)

        val okGenerated = wmEngine.watermarkExistingPdfToNewFile(
            uri = null,
            localFilePath = lockedPdf.absolutePath,
            existingPassword = "wm-secret",
            outputBaseName = "wm_locked_ok",
            watermark = config
        )
        val outFile = File(okGenerated.filePath)
        assertTrue(outFile.exists())
        assertTrue(okGenerated.fullText.contains("CONFIDENTIAL"))

        lockedPdf.delete()
        outFile.delete()
    }

    @Test
    fun `parses multilingual translation JSON builds instructions and formats output`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PdfTranslationAiRepository(context)

        assertEquals(listOf(0, 2, 3), repo.parsePageRange("1, 3-4", 5))

        val prompt = repo.buildBatchInstruction(
            documentTitle = "Quarterly Report",
            sourceLanguage = TranslationLanguageCatalog.AUTO_DETECT,
            targetLanguage = "Japanese",
            tone = PdfTranslationTone.BILINGUAL,
            batch = listOf(
                PdfPagePayload(pageNumber = 1, extractedText = "Revenue increased by 18%."),
                PdfPagePayload(pageNumber = 2, extractedText = "")
            )
        )
        assertTrue(prompt.contains("Auto-detect"))
        assertTrue(prompt.contains("Japanese"))
        assertTrue(prompt.contains("bilingual", ignoreCase = true))
        assertTrue(prompt.contains("PAGE 1"))
        assertTrue(prompt.contains("Scanned page image provided"))

        val sampleJson = """
            {
              "detectedSourceLanguage": "English",
              "translatedTitle": "四半期報告書",
              "pages": [
                {
                  "pageNumber": 1,
                  "heading": "財務概要",
                  "translatedText": "売上高は前年比18%増加しました。\n- 新規顧客の獲得\n- 海外展開の加速"
                }
              ]
            }
        """.trimIndent()
        val parsed = repo.parseTranslationBatchJson(
            jsonString = sampleJson,
            fallbackSourceLanguage = "Auto-Detect",
            fallbackTitle = "Fallback",
            expectedPages = listOf(1)
        )
        assertEquals("English", parsed.detectedSourceLanguage)
        assertEquals("四半期報告書", parsed.translatedTitle)
        assertEquals(1, parsed.pages.size)

        val result = PdfTranslationResult(
            detectedSourceLanguage = parsed.detectedSourceLanguage,
            targetLanguage = "Japanese",
            translatedTitle = parsed.translatedTitle,
            tone = PdfTranslationTone.NATURAL,
            pages = parsed.pages + TranslatedPdfPage(
                pageNumber = 2,
                heading = "الملخص",
                translatedText = "مرحبا بالعالم — Hola Mundo"
            )
        )
        val md = result.toMarkdown()
        val plain = result.toPlainText()
        assertTrue(md.contains("四半期報告書"))
        assertTrue(md.contains("مرحبا بالعالم"))
        assertTrue(plain.contains("Hola Mundo"))

        val parser = DocumentContentParser(context)
        val blocks = parser.parseMarkdown(md)
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Heading && it.text == "四半期報告書" })
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Bullet && it.text.contains("新規顧客の獲得") })
        assertTrue(blocks.any { it is com.example.data.pdf.DocBlock.Paragraph && it.text.contains("مرحبا بالعالم") })

        val engine = PdfConversionEngine(context, parser, OcrProcessor())
        assertTrue(engine.sanitizeFileName("四半期報告書").isNotBlank())
    }
}


