package com.tehoubi.ddrtracker.data.backup

import android.content.Context
import android.net.Uri
import com.tehoubi.ddrtracker.domain.repository.PlayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes backups through Storage Access Framework URIs. */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plays: PlayRepository,
    private val clock: Clock,
) {
    data class RestoreResult(val restored: Int, val skipped: Int)

    suspend fun exportCsv(uri: Uri): Int = write(uri) { BackupCodec.csv(it) }

    suspend fun exportBackup(uri: Uri): Int = write(uri) { BackupCodec.encode(it, Instant.now(clock)) }

    /** Restoring is idempotent: plays keep their ids, so a second restore just overwrites. */
    suspend fun restore(uri: Uri): RestoreResult {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw BackupFormatException("Couldn't open the file")
        }
        val (decoded, skipped) = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        plays.savePlays(decoded).getOrThrow()
        return RestoreResult(decoded.size, skipped)
    }

    private suspend fun write(uri: Uri, render: (List<com.tehoubi.ddrtracker.domain.model.Play>) -> String): Int {
        val all = plays.observePlays().first()
        val text = withContext(Dispatchers.Default) { render(all) }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: throw IllegalStateException("Couldn't write the file")
        }
        return all.size
    }
}
