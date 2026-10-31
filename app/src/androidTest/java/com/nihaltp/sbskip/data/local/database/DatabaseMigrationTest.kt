package com.nihaltp.sbskip.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the migration from database version 7 to version 8 creates the downloaded videos
 * table with the expected columns.
 *
 * The version 7 database is created by hand (it only contains the download queue table) and then
 * opened with Room, so that the real migration of the app is executed.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    }

    @Test
    fun migrateFromVersion7ToVersion8CreatesDownloadedVideosTable() {
        val dbName = "migration_test.db"
        createVersion7Database(context, dbName)

        val db = openWithMigrations(dbName)

        val tables = tableNames(db)
        assertTrue(
            "Expected the downloaded_videos table to exist after the migration, got $tables",
            tables.contains("downloaded_videos"),
        )

        db.close()
    }

    @Test
    fun downloadedVideosTableHasExpectedColumns() {
        val dbName = "migration_test_columns.db"
        createVersion7Database(context, dbName)

        val db = openWithMigrations(dbName)

        val columns = db.query(SimpleSQLiteQuery("PRAGMA table_info(downloaded_videos)"))
        val columnNames = mutableSetOf<String>()
        try {
            while (columns.moveToNext()) {
                columnNames.add(columns.getString(1))
            }
        } finally {
            columns.close()
        }

        val expectedColumns =
            setOf(
                "id",
                "videoId",
                "url",
                "title",
                "author",
                "thumbnailUrl",
                "durationSeconds",
                "fileSize",
                "mediaType",
                "needsEnrichment",
            )

        assertEquals(expectedColumns, columnNames)

        db.close()
    }

    @Test
    fun downloadedVideoEntityRoundTripAfterMigration() =
        runBlocking {
            val dbName = "migration_test_roundtrip.db"
            createVersion7Database(context, dbName)

            val db = openWithMigrations(dbName)

            val entity =
                DownloadedVideoEntity(
                    videoId = "dQw4w9WgXcQ",
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Test Video",
                    author = "Test Author",
                    thumbnailUrl = null,
                    durationSeconds = 180L,
                    fileSize = 1024L,
                    mediaType = "video",
                )

            val insertedId = db.downloadedVideoDao().insert(entity)
            assertTrue("Insert should return a valid row ID", insertedId > 0)

            val cursor =
                db.query(
                    SimpleSQLiteQuery(
                        "SELECT videoId, title, mediaType FROM downloaded_videos WHERE id = ?",
                        arrayOf(insertedId.toString()),
                    ),
                )
            try {
                assertEquals(true, cursor.moveToFirst())
                assertEquals("dQw4w9WgXcQ", cursor.getString(0))
                assertEquals("Test Video", cursor.getString(1))
                assertEquals("video", cursor.getString(2))
            } finally {
                cursor.close()
            }

            db.close()
        }

    /**
     * Opens the database with Room so that the migrations of the app are executed and the
     * resulting schema is validated against the exported Room schema.
     */
    private fun openWithMigrations(dbName: String): SBSkipDatabase {
        val db =
            Room.databaseBuilder(context, SBSkipDatabase::class.java, dbName)
                .addMigrations(*SBSkipDatabaseMigrations.ALL)
                .build()
        // Opening the database triggers the migration from version 7 to version 8.
        db.openHelper.writableDatabase
        return db
    }

    /**
     * Creates a version 7 database that only contains the download queue table, so that opening it
     * with Room has to run the 7 -> 8 migration.
     */
    private fun createVersion7Database(
        context: Context,
        dbName: String,
    ) {
        context.deleteDatabase(dbName)
        val callback =
            object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(DOWNLOAD_QUEUE_V7_SCHEMA)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(dbName)
                    .callback(callback)
                    .build(),
            )
        helper.writableDatabase.use { it.execSQL("PRAGMA user_version = 7") }
    }

    private fun tableNames(db: SBSkipDatabase): Set<String> {
        val cursor = db.query(SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table'"))
        val names = mutableSetOf<String>()
        try {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0))
            }
        } finally {
            cursor.close()
        }
        return names
    }

    private companion object {
        /**
         * The download queue table as it looked in database version 7. It is identical to the
         * current schema, because the 7 -> 8 migration only adds the downloaded videos table.
         */
        const val DOWNLOAD_QUEUE_V7_SCHEMA =
            """
            CREATE TABLE IF NOT EXISTS `download_queue` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `url` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `localFileUri` TEXT NOT NULL,
                `mediaType` TEXT NOT NULL,
                `thumbnailUrl` TEXT,
                `durationSeconds` INTEGER,
                `status` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `errorMessage` TEXT,
                `outputPath` TEXT,
                `outputDurationSeconds` INTEGER,
                `convertVideoToAudio` INTEGER NOT NULL,
                `deleteOriginalVideo` INTEGER NOT NULL,
                `audioOutputDirUri` TEXT,
                `videoOutputDirUri` TEXT,
                `relativePath` TEXT
            )
            """
    }
}
