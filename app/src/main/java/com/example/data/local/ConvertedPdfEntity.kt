package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "converted_pdfs")
data class ConvertedPdfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
    val sourceSummary: String,
    val ocrEnabled: Boolean,
    val ocrText: String,
    val pageTextsJson: String = "[]",
    val batchId: String? = null,
    val lastUploadedProvider: String? = null,
    val lastUploadedUrl: String? = null,
    val isPasswordProtected: Boolean = false,
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = true,
    val allowModifying: Boolean = false,
    val securitySummary: String? = null,
    val driveFileId: String? = null,
    val lastSyncedAtMs: Long? = null
)
