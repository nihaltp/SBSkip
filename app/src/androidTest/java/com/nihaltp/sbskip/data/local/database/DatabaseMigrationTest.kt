package com.nihaltp.sbskip.data.local.database

import androidx.room.Room
import androidx.room.migration.MigrationTestHelper
import androidx.room.testing.Frameworks
import androidx.sqlite.db.simple.SimpleSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nihaltp.sbskip.model.DownloadedVideoEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val migrationTestHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SBSkipDatabase::class.java.canonicalName,
            Frameworks.newDatabaseHelperHelper(InstrumentationRegistry.getInstrumentation().context),
        )

    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    }

    @Test
    fun migrateFromVersion7ToVersion8CreatesDownloadedVideosTable() {
        val db =
            Room.databaseBuilder(
                context,
                SBSkipDatabase::class.java,
                "migration_test.db",
            )
                .addMigrations()
                .build()

        migrationTestHelper.runMigrationsAndValidate(db, 8, true)

        db.close()
    }

    @Test
    fun downloadedVideosTableHasExpectedColumns() {
        val db =
            Room.databaseBuilder(
                context,
                SBSkipDatabase::class.java,
                "migration_test_columns.db",
            )
                .addMigrations()
                .build()

        migrationTestHelper.runMigrationsAndValidate(db, 8, true)

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
                "thumbnailUrl",
                "durationSeconds",
                "downloadedAtEpochMillis",
                "outputPath",
                "mediaType",
            )

        assertEquals(expectedColumns, columnNames)

        db.close()
    }

    @Test
    fun downloadedVideoEntityRoundTripAfterMigration() =
        runBlocking {
            val db =
                Room.databaseBuilder(
                    context,
                    SBSkipDatabase::class.java,
                    "migration_test_roundtrip.db",
                )
                    .addMigrations()
                    .build()

            migrationTestHelper.runMigrationsAndValidate(db, 8, true)

            val entity =
                DownloadedVideoEntity(
                    videoId = "dQw4w9WgXcQ",
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    title = "Test Video",
                    thumbnailUrl = null,
                    durationSeconds = 180L,
                    downloadedAtEpochMillis = System.currentTimeMillis(),
                    outputPath = "/sdcard/Download/video.m4a",
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
}
