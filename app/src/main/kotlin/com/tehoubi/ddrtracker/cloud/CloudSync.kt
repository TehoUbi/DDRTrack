package com.tehoubi.ddrtracker.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.tehoubi.ddrtracker.data.OfflineFirstPlayRepository
import com.tehoubi.ddrtracker.data.local.DdrDatabase
import com.tehoubi.ddrtracker.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CloudStatus {
    /** This build has no Firebase project (no google-services.json). */
    data object Unavailable : CloudStatus
    data object Off : CloudStatus
    data object Syncing : CloudStatus
    data class Synced(val atMillis: Long, val pushed: Int, val pulled: Int) : CloudStatus
    data class Failed(val message: String) : CloudStatus
}

/**
 * Backs the play log up to Firestore under an anonymous Firebase account and
 * restores it on other devices. Room stays the source of truth; this pushes
 * dirty rows and pulls rows changed elsewhere since the last pull.
 */
@Singleton
class CloudSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DdrDatabase,
    private val repository: OfflineFirstPlayRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    val isAvailable: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val _status = MutableStateFlow<CloudStatus>(CloudStatus.Off)
    val status: StateFlow<CloudStatus> = _status

    private val mutex = Mutex()

    fun currentUserId(): String? = if (isAvailable) FirebaseAuth.getInstance().currentUser?.uid else null

    /** Pushes then pulls. Returns false if it should be retried later. */
    suspend fun sync(): Boolean = mutex.withLock {
        if (!isAvailable) { _status.value = CloudStatus.Unavailable; return true }
        val s = settings.current()
        if (!s.cloudSync) { _status.value = CloudStatus.Off; return true }
        _status.value = CloudStatus.Syncing
        return runCatching {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
            val user = FirebaseFirestore.getInstance().collection("users").document(uid)
            val pushed = push(user)
            val pulled = pull(user, s.cloudPulledAt)
            pushSessions(user)
            user.set(
                mapOf(
                    "displayName" to s.account?.displayName,
                    "playCount" to db.playDao().count().toLong(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            _status.value = CloudStatus.Synced(clock.millis(), pushed, pulled)
            settings.setCloudSyncedAt(clock.millis())
        }.onFailure { _status.value = CloudStatus.Failed(it.message ?: it.javaClass.simpleName) }.isSuccess
    }

    private suspend fun push(user: com.google.firebase.firestore.DocumentReference): Int {
        val dao = db.playDao()
        val firestore = user.firestore
        var pushed = 0
        while (true) {
            // Firestore caps a batch at 500 writes.
            val rows = dao.cloudDirty(limit = 400)
            if (rows.isEmpty()) break
            val batch = firestore.batch()
            rows.forEach { batch.set(user.collection("plays").document(it.id), it.toDocument()) }
            batch.commit().await()
            rows.forEach { dao.markCloudClean(it.id, it.updatedAt) }
            pushed += rows.size
            if (rows.size < 400) break
        }
        return pushed
    }

    private suspend fun pull(user: com.google.firebase.firestore.DocumentReference, since: Long): Int {
        var cursor = since
        var applied = 0
        while (true) {
            val page = user.collection("plays")
                .whereGreaterThan("updatedAt", cursor)
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .limit(500)
                .get().await()
            if (page.isEmpty) break
            val rows = page.documents.mapNotNull { doc -> doc.data?.let { playFromDocument(doc.id, it) } }
            applied += repository.mergeFromCloud(rows)
            cursor = rows.maxOfOrNull { it.updatedAt } ?: break
            settings.setCloudPulledAt(cursor)
            if (page.size() < 500) break
        }
        return applied
    }

    /** Sessions are derived, so the remote set is replaced to match. */
    private suspend fun pushSessions(user: com.google.firebase.firestore.DocumentReference) {
        val sessions = db.sessionDao().getAll()
        val collection = user.collection("sessions")
        val remoteIds = collection.get().await().documents.map { it.id }.toSet()
        val localIds = sessions.map { it.id }.toSet()
        val writes = sessions.map { s -> { b: com.google.firebase.firestore.WriteBatch -> b.set(collection.document(s.id), s.toDocument()) } } +
            (remoteIds - localIds).map { id -> { b: com.google.firebase.firestore.WriteBatch -> b.delete(collection.document(id)) } }
        writes.chunked(400).forEach { chunk ->
            val batch = user.firestore.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }
}
