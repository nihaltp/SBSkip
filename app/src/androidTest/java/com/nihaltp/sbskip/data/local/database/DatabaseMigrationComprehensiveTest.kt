package com.nihaltp.sbskip.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.model.DownloadQueueEntity
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import com.nihaltp.sbskip.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationComprehensiveTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    }

    @Test
    fun migrateFromVersion4ToVersion8() {
        val dbName = "migration_4_to_8_test.db"
        createVersion4Database(context, dbName)

        val db = openWithMigrations(dbName)

        // Verify download_queue table has all columns up to v8
        val columns = db.query(SimpleSQLiteQuery("PRAGMA table_info(download_queue)"))
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
                "url",
                "title",
                "localFileUri",
                "mediaType",
                "thumbnailUrl",
                "durationSeconds",
                "status",
                "createdAtEpochMillis",
                "updatedAtEpochMillis",
                "errorMessage",
                "outputPath",
                "outputDurationSeconds",
                "convertVideoToAudio",
                "deleteOriginalVideo",
                "audioOutputDirUri",
                "videoOutputDirUri",
                "relativePath",
                "overwrite",
                "bypassDurationCheck",
                "noSuffix",
            )

        assertEquals(expectedColumns, columnNames)

        // Verify downloaded_videos table exists
        val tables = tableNames(db)
        assertTrue("Expected the downloaded_videos table to exist, got $tables", tables.contains("downloaded_videos"))

        db.close()
    }

    @Test
    fun migrateFromVersion5ToVersion8() {
        val dbName = "migration_5_to_8_test.db"
        createVersion5Database(context, dbName)

        val db = openWithMigrations(dbName)

        val columns = db.query(SimpleSQLiteQuery("PRAGMA table_info(download_queue)"))
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
                "url",
                "title",
                "localFileUri",
                "mediaType",
                "thumbnailUrl",
                "durationSeconds",
                "status",
                "createdAtEpochMillis",
                "updatedAtEpochMillis",
                "errorMessage",
                "outputPath",
                "outputDurationSeconds",
                "convertVideoToAudio",
                "deleteOriginalVideo",
                "audioOutputDirUri",
                "videoOutputDirUri",
                "relativePath",
                "overwrite",
                "bypassDurationCheck",
                "noSuffix",
            )

        assertEquals(expectedColumns, columnNames)

        db.close()
    }

    @Test
    fun migrateFromVersion6ToVersion8() {
        val dbName = "migration_6_to_8_test.db"
        createVersion6Database(context, dbName)

        val db = openWithMigrations(dbName)

        val columns = db.query(SimpleSQLiteQuery("PRAGMA table_info(download_queue)"))
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
                "url",
                "title",
                "localFileUri",
                "mediaType",
                "thumbnailUrl",
                "durationSeconds",
                "status",
                "createdAtEpochMillis",
                "updatedAtEpochMillis",
                "errorMessage",
                "outputPath",
                "outputDurationSeconds",
                "convertVideoToAudio",
                "deleteOriginalVideo",
                "audioOutputDirUri",
                "videoOutputDirUri",
                "relativePath",
                "overwrite",
                "bypassDurationCheck",
                "noSuffix",
            )

        assertEquals(expectedColumns, columnNames)

        db.close()
    }

    @Test
    fun migrateFromVersion7ToVersion8() {
        val dbName = "migration_7_to_8_test.db"
        createVersion7Database(context, dbName)

        val db = openWithMigrations(dbName)

        val columns = db.query(SimpleSQLiteQuery("PRAGMA table_info(download_queue)"))
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
                "url",
                "title",
                "localFileUri",
                "mediaType",
                "thumbnailUrl",
                "durationSeconds",
                "status",
                "createdAtEpochMillis",
                "updatedAtEpochMillis",
                "errorMessage",
                "outputPath",
                "outputDurationSeconds",
                "convertVideoToAudio",
                "deleteOriginalVideo",
                "audioOutputDirUri",
                "videoOutputDirUri",
                "relativePath",
                "overwrite",
                "bypassDurationCheck",
                "noSuffix",
            )

        assertEquals(expectedColumns, columnNames)

        // Verify downloaded_videos table
        val tables = tableNames(db)
        assertTrue("Expected the downloaded_videos table to exist, got $tables", tables.contains("downloaded_videos"))

        db.close()
    }

    @Test
    fun downloadedVideosTableHasCorrectSchema() =
        runBlocking {
            val dbName = "migration_downloaded_videos_schema_test.db"
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

            // Verify unique index exists
            val indexes = db.query(SimpleSQLiteQuery("PRAGMA index_list(downloaded_videos)"))
            val indexNames = mutableSetOf<String>()
            try {
                while (indexes.moveToNext()) {
                    indexNames.add(indexes.getString(1))
                }
            } finally {
                indexes.close()
            }
            assertTrue("Expected unique index on videoId", indexNames.contains("index_downloaded_videos_videoId"))

            db.close()
        }

    @Test
    fun downloadQueueEntityRoundTripAfterFullMigration() =
        runBlocking {
            val dbName = "migration_roundtrip_full_test.db"
            createVersion4Database(context, dbName)

            val db = openWithMigrations(dbName)

            val entity =
                DownloadQueueEntity(
                    id = 0,
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Test Video",
                    localFileUri = "content://test/uri",
                    mediaType = MediaType.VIDEO.name,
                    thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
                    durationSeconds = 300L,
                    status = DownloadQueueStatus.COMPLETED,
                    createdAtEpochMillis = 1000L,
                    updatedAtEpochMillis = 2000L,
                    errorMessage = null,
                    outputPath = "content://test/output",
                    outputDurationSeconds = 300L,
                    convertVideoToAudio = false,
                    deleteOriginalVideo = true,
                    audioOutputDirUri = "",
                    videoOutputDirUri = "",
                    relativePath = "Movies/SB Skip/test.mp4",
                )

            val insertedId = db.downloadQueueDao().insert(entity)
            assertTrue("Insert should return a valid row ID", insertedId > 0)

            val retrieved = db.downloadQueueDao().findById(insertedId)
            assertEquals(entity.url, retrieved?.url)
            assertEquals(entity.title, retrieved?.title)
            assertEquals(entity.mediaType, retrieved?.mediaType)

            db.close()
        }

    @Test
    fun downloadedVideoEntityRoundTripAfterMigration() =
        runBlocking {
            val dbName = "migration_downloaded_video_roundtrip_test.db"
            createVersion7Database(context, dbName)

            val db = openWithMigrations(dbName)

            val entity =
                DownloadedVideoEntity(
                    videoId = "dQw4w9WgXcQ",
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Test Video",
                    author = "Test Author",
                    thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
                    durationSeconds = 300L,
                    fileSize = 1024L,
                    mediaType = "video",
                )

            val insertedId = db.downloadedVideoDao().insert(entity)
            assertTrue("Insert should return a valid row ID", insertedId > 0)

            val cursor =
                db.query(
                    SimpleSQLiteQuery(
                        "SELECT videoId, title, author, mediaType FROM downloaded_videos WHERE id = ?",
                        arrayOf(insertedId.toString()),
                    ),
                )
            try {
                assertEquals(true, cursor.moveToFirst())
                assertEquals("dQw4w9WgXcQ", cursor.getString(0))
                assertEquals("Test Video", cursor.getString(1))
                assertEquals("Test Author", cursor.getString(2))
                assertEquals("video", cursor.getString(3))
            } finally {
                cursor.close()
            }

            db.close()
        }

    @Test
    fun migrationPreservesExistingData() =
        runBlocking {
            val dbName = "migration_preserve_data_test.db"
            createVersion4Database(context, dbName)

            // Insert data into v4 schema
            val callback =
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(DOWNLOAD_QUEUE_V4_SCHEMA)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                }
            val helper =
                FrameworkSQLiteOpenHelperFactory().create(
                    SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
                )
            helper.writableDatabase.use { db ->
                db.execSQL(
                    "INSERT INTO download_queue (url, title, localFileUri, mediaType, status, " +
                        "createdAtEpochMillis, updatedAtEpochMillis) " +
                        "VALUES ('https://youtube.com/watch?v=test1', 'Video 1', 'content://test/1', " +
                        "'VIDEO', 'COMPLETED', 1000, 2000)",
                )
                db.execSQL(
                    "INSERT INTO download_queue (url, title, localFileUri, mediaType, status, " +
                        "createdAtEpochMillis, updatedAtEpochMillis) " +
                        "VALUES ('https://youtube.com/watch?v=test2', 'Video 2', 'content://test/2', " +
                        "'AUDIO', 'QUEUED', 3000, 4000)",
                )
            }

            // Now open with migrations
            val db = openWithMigrations(dbName)

            // Verify data was preserved
            val queue = db.downloadQueueDao().observeQueue().first()
            assertEquals(2, queue.size)
            assertEquals("Video 1", queue[0].title)
            assertEquals("Video 2", queue[1].title)
            assertEquals("VIDEO", queue[0].mediaType)
            assertEquals("AUDIO", queue[1].mediaType)

            // Verify new columns have default values
            assertEquals(0L, queue[0].outputDurationSeconds)
            assertEquals(false, queue[0].convertVideoToAudio)
            assertEquals(true, queue[0].deleteOriginalVideo)
            assertEquals("", queue[0].relativePath)

            db.close()
        }

    /**
     * Opens the database with Room so that the migrations of the app are executed.
     */
    private fun openWithMigrations(dbName: String): SBSkipDatabase {
        val db =
            Room.databaseBuilder(context, SBSkipDatabase::class.java, dbName)
                .addMigrations(*SBSkipDatabaseMigrations.ALL)
                .build()
        db.openHelper.writableDatabase
        return db
    }

    /**
     * Creates a version 4 database with the original download queue schema.
     */
    private fun createVersion4Database(
        context: Context,
        dbName: String,
    ) {
        context.deleteDatabase(dbName)
        val callback =
            object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(DOWNLOAD_QUEUE_V4_SCHEMA)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
            )
        helper.writableDatabase.use { it.execSQL("PRAGMA user_version = 4") }
    }

    /**
     * Creates a version 5 database (after MIGRATION_4_5).
     */
    private fun createVersion5Database(
        context: Context,
        dbName: String,
    ) {
        context.deleteDatabase(dbName)
        val callback =
            object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(DOWNLOAD_QUEUE_V5_SCHEMA)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
            )
        helper.writableDatabase.use { it.execSQL("PRAGMA user_version = 5") }
    }

    /**
     * Creates a version 6 database (after MIGRATION_5_6).
     */
    private fun createVersion6Database(
        context: Context,
        dbName: String,
    ) {
        context.deleteDatabase(dbName)
        val callback =
            object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(DOWNLOAD_QUEUE_V6_SCHEMA)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
            )
        helper.writableDatabase.use { it.execSQL("PRAGMA user_version = 6") }
    }

    /**
     * Creates a version 7 database (after MIGRATION_6_7).
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
                SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
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

    companion object {
        /** Version 4 schema (initial) */
        const val DOWNLOAD_QUEUE_V4_SCHEMA = """
            CREATE TABLE IF NOT EXISTS \`download_queue\` (
                \`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                \`url\` TEXT NOT NULL,
                \`title\` TEXT NOT NULL,
                \`localFileUri\` TEXT NOT NULL,
                \`mediaType\` TEXT NOT NULL,
                \`thumbnailUrl\` TEXT,
                \`durationSeconds\` INTEGER,
                \`status\` TEXT NOT NULL,
                \`createdAtEpochMillis\` INTEGER NOT NULL,
                \`updatedAtEpochMillis\` INTEGER NOT NULL,
                \`errorMessage\` TEXT,
                \`outputPath\` TEXT,
                \`convertVideoToAudio\` INTEGER NOT NULL,
                \`deleteOriginalVideo\` INTEGER NOT NULL,
                \`audioOutputDirUri\` TEXT,
                \`videoOutputDirUri\` TEXT
            )
        """

        /** Version 5 schema (after adding relativePath) */
        const val DOWNLOAD_QUEUE_V5_SCHEMA = """
            CREATE TABLE IF NOT EXISTS \`download_queue\` (
                \`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                \`url\` TEXT NOT NULL,
                \`title\` TEXT NOT NULL,
                \`localFileUri\` TEXT NOT NULL,
                \`mediaType\` TEXT NOT NULL,
                \`thumbnailUrl\` TEXT,
                \`durationSeconds\` INTEGER,
                \`status\` TEXT NOT NULL,
                \`createdAtEpochMillis\` INTEGER NOT NULL,
                \`updatedAtEpochMillis\` INTEGER NOT NULL,
                \`errorMessage\` TEXT,
                \`outputPath\` TEXT,
                \`convertVideoToAudio\` INTEGER NOT NULL,
                \`deleteOriginalVideo\` INTEGER NOT NULL,
                \`audioOutputDirUri\` TEXT,
                \`videoOutputDirUri\` TEXT,
                \`relativePath\` TEXT
            )
        """

        /** Version 6 schema (after adding videoOutputDirUri) */
        const val DOWNLOAD_QUEUE_V6_SCHEMA = """
            CREATE TABLE IF NOT EXISTS \`download_queue\` (
                \`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                \`url\` TEXT NOT NULL,
                \`title\` TEXT NOT NULL,
                \`localFileUri\` TEXT NOT NULL,
                \`mediaType\` TEXT NOT NULL,
                \`thumbnailUrl\` TEXT,
                \`durationSeconds\` INTEGER,
                \`status\` TEXT NOT NULL,
                \`createdAtEpochMillis\` INTEGER NOT NULL,
                \`updatedAtEpochMillis\` INTEGER NOT NULL,
                \`errorMessage\` TEXT,
                \`outputPath\` TEXT,
                \`convertVideoToAudio\` INTEGER NOT NULL,
                \`deleteOriginalVideo\` INTEGER NOT NULL,
                \`audioOutputDirUri\` TEXT,
                \`videoOutputDirUri\` TEXT,
                \`relativePath\` TEXT
            )
        """

        /** Version 7 schema (after adding outputDurationSeconds) */
        const val DOWNLOAD_QUEUE_V7_SCHEMA = """
            CREATE TABLE IF NOT EXISTS \`download_queue\` (
                \`id\` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                \`url\` TEXT NOT NULL,
                \`title\` TEXT NOT NULL,
                \`localFileUri\` TEXT NOT NULL,
                \`mediaType\` TEXT NOT NULL,
                \`thumbnailUrl\` TEXT,
                \`durationSeconds\` INTEGER,
                \`status\` TEXT NOT NULL,
                \`createdAtEpochMillis\` INTEGER NOT NULL,
                \`updatedAtEpochMillis\` INTEGER NOT NULL,
                \`errorMessage\` TEXT,
                \`outputPath\` TEXT,
                \`outputDurationSeconds\` INTEGER,
                \`convertVideoToAudio\` INTEGER NOT NULL,
                \`deleteOriginalVideo\` INTEGER NOT NULL,
                \`audioOutputDirUri\` TEXT,
                \`videoOutputDirUri\` TEXT,
                \`relativePath\` TEXT
            )
        """
    }
}
