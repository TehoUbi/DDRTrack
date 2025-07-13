package com.tehoubi.ddrtracker.data.remote

import com.tehoubi.ddrtracker.core.api.ApiError
import com.tehoubi.ddrtracker.core.api.ChartSummary
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.HealthResponse
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.PlayerProfile
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.api.RegisterRequest
import com.tehoubi.ddrtracker.core.api.RegisterResponse
import com.tehoubi.ddrtracker.core.api.ScoreSubmission
import com.tehoubi.ddrtracker.core.api.SubmitScoresRequest
import com.tehoubi.ddrtracker.core.api.SubmitScoresResponse
import com.tehoubi.ddrtracker.core.api.UpdatePlayerRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** An HTTP error from the leaderboard service, with its message. */
class ApiException(val status: Int, message: String) : IOException(message)

/**
 * Typed client for the leaderboard service. The base URL and token are
 * passed per call because both live in settings and can change at runtime.
 */
@Singleton
class LeaderboardClient @Inject constructor() {

    internal var http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    suspend fun health(base: String): HealthResponse = get(base, "health", null)

    suspend fun register(base: String, displayName: String): RegisterResponse =
        send(base, "v1/players", "POST", null, RegisterRequest(displayName))

    suspend fun me(base: String, token: String): PlayerProfile = get(base, "v1/players/me", token)

    suspend fun rename(base: String, token: String, displayName: String): PlayerSummary =
        send(base, "v1/players/me", "PATCH", token, UpdatePlayerRequest(displayName))

    suspend fun search(base: String, query: String): List<PlayerSummary> =
        get(base, "v1/players/search?q=${encode(query)}", null)

    suspend fun submit(base: String, token: String, scores: List<ScoreSubmission>): SubmitScoresResponse =
        send(base, "v1/scores", "POST", token, SubmitScoresRequest(scores))

    /** True if removed, false if the server never had it. */
    suspend fun deleteScore(base: String, token: String, clientId: String): Boolean {
        val response = execute(request(base, "v1/scores/${encode(clientId)}", token).delete().build())
        response.use {
            return when (it.code) {
                204 -> true
                404 -> false
                else -> throw error(it.code, it.body?.string())
            }
        }
    }

    suspend fun leaderboard(base: String, token: String?, songKey: String, style: String, difficulty: String, limit: Int = 50): Leaderboard =
        get(base, "v1/charts/${encode(songKey)}/$style/$difficulty/leaderboard?limit=$limit", token)

    suspend fun compare(base: String, token: String, rivalId: String): Comparison =
        get(base, "v1/players/me/compare/${encode(rivalId)}", token)

    suspend fun popular(base: String, limit: Int = 20): List<ChartSummary> = get(base, "v1/charts/popular?limit=$limit", null)

    // --- plumbing -----------------------------------------------------------

    private suspend inline fun <reified T> get(base: String, path: String, token: String?): T =
        call(request(base, path, token).get().build(), serializer())

    private suspend inline fun <reified B, reified T> send(base: String, path: String, method: String, token: String?, body: B): T {
        val payload = json.encodeToString(serializer<B>(), body).toRequestBody(jsonType)
        return call(request(base, path, token).method(method, payload).build(), serializer())
    }

    private fun request(base: String, path: String, token: String?): Request.Builder {
        val url = resolve(base, path)
        return Request.Builder().url(url).header("Accept", "application/json").apply {
            token?.let { header("Authorization", "Bearer $it") }
        }
    }

    private suspend fun <T> call(request: Request, deserializer: KSerializer<T>): T {
        execute(request).use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw error(response.code, body)
            return json.decodeFromString(deserializer, body)
        }
    }

    private suspend fun execute(request: Request) = withContext(Dispatchers.IO) { http.newCall(request).execute() }

    private fun error(code: Int, body: String?): ApiException {
        val message = body?.let { runCatching { json.decodeFromString(ApiError.serializer(), it).error }.getOrNull() }
        return ApiException(code, message ?: "Server returned $code")
    }

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        /** Joins a user-entered base URL (with or without a trailing slash) and a path. */
        fun resolve(base: String, path: String): HttpUrl {
            val root = normalizeBaseUrl(base) ?: throw IllegalArgumentException("Not a valid server URL: $base")
            return "$root/$path".toHttpUrlOrNull() ?: throw IllegalArgumentException("Bad path: $path")
        }

        /** "192.168.1.5:8080/" -> "http://192.168.1.5:8080", or null if unusable. */
        fun normalizeBaseUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()?.toString()?.trimEnd('/')
        }
    }
}
