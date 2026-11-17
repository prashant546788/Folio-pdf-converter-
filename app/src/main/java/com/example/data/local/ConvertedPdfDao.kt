package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConvertedPdfDao {
    @Query("SELECT * FROM converted_pdfs ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ConvertedPdfEntity>>

    @Query("SELECT * FROM converted_pdfs ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<ConvertedPdfEntity>

    @Query("SELECT * FROM converted_pdfs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConvertedPdfEntity?

    @Query("SELECT * FROM converted_pdfs WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileId(driveFileId: String): ConvertedPdfEntity?

    @Query("SELECT * FROM converted_pdfs WHERE fileName = :fileName LIMIT 1")
    suspend fun getByFileName(fileName: String): ConvertedPdfEntity?

    @Query("SELECT * FROM converted_pdfs WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ConvertedPdfEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConvertedPdfEntity): Long

    @Query(
        "UPDATE converted_pdfs SET lastUploadedProvider = :providerName, lastUploadedUrl = :remoteUrl WHERE id = :id"
    )
    suspend fun updateCloudSync(id: Long, providerName: String, remoteUrl: String?)

    @Query(
        "UPDATE converted_pdfs SET lastUploadedProvider = :providerName, lastUploadedUrl = :remoteUrl, driveFileId = :driveFileId, lastSyncedAtMs = :syncedAtMs WHERE id = :id"
    )
    suspend fun updateDriveSync(
        id: Long,
        providerName: String,
        remoteUrl: String?,
        driveFileId: String?,
        syncedAtMs: Long
    )

    @Query("DELETE FROM converted_pdfs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
