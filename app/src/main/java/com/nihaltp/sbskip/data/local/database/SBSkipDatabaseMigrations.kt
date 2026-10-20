package com.nihaltp.sbskip.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * All migrations of the app database, in the order they have to be applied.
 *
 * They live in their own object so that they can be shared between the dependency injection
 * module and the migration tests.
 */
object SBSkipDatabaseMigrations {
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN relativePath TEXT")
            }
        }

    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN videoOutputDirUri TEXT")
            }
        }

    val MIGRATION_6_7 =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_queue ADD COLUMN outputDurationSeconds INTEGER")
            }
        }

    val MIGRATION_7_8 =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS downloaded_videos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        videoId TEXT,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT,
                        thumbnailUrl TEXT,
                        durationSeconds INTEGER,
                        outputPath TEXT,
                        fileSize INTEGER,
                        mediaType TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    /** All migrations, ordered by the version they upgrade to. */
    val ALL = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
}
