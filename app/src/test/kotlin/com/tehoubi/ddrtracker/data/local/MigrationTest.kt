package com.tehoubi.ddrtracker.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Builds a version-1 database from the exported schema, then opens it with
 * the current Room database. Room validates every table against the entities
 * after migrating, so a migration that drifts from them fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun createVersion1(name: String) {
        val schema = JSONObject(File("schemas/${DdrDatabase::class.qualifiedName}/1.json").readText())
            .getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (k in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                """
                INSERT INTO plays (id, song_key, song_title, artist, style, difficulty, level, score, ex_score,
                    grade, lamp, marvelous, perfect, great, good, ok, miss, max_combo, fast, slow, failed,
                    played_at, source, scan_confidence, note, updated_at, deleted)
                VALUES ('p1', 'paranoia', 'PARANOiA', NULL, 'SINGLE', 'EXPERT', 14, 951230, 1200,
                    'AA_PLUS', 'CLEAR', 300, 80, 10, 2, 30, 3, 200, NULL, NULL, 0,
                    1776654000000, 'MANUAL', NULL, NULL, 1776654000000, 0)
                """.trimIndent()
            )
            db.version = 1
        }
    }

    @Test
    fun `1 to 2 keeps plays and adds sessions`() = runTest {
        createVersion1("migration-test.db")
        val db = Room.databaseBuilder(context, DdrDatabase::class.java, "migration-test.db")
            .addMigrations(DdrDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val play = db.playDao().getById("p1")!!
            assertEquals(951_230, play.score)
            assertNull(play.sessionId)
            assertEquals(emptyList(), db.sessionDao().observeAll().first())
        } finally {
            db.close()
        }
    }
}
