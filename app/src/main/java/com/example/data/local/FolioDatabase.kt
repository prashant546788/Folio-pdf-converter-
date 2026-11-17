package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ConvertedPdfEntity::class], version = 3, exportSchema = false)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun convertedPdfDao(): ConvertedPdfDao

    companion object {
        @Volatile
        private var INSTANCE: FolioDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN isPasswordProtected INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN allowPrinting INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN allowCopying INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN allowModifying INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN securitySummary TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN driveFileId TEXT")
                db.execSQL("ALTER TABLE converted_pdfs ADD COLUMN lastSyncedAtMs INTEGER")
            }
        }

        fun getInstance(context: Context): FolioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FolioDatabase::class.java,
                    "folio_pdf_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
