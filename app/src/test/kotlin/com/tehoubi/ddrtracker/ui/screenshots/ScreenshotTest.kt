package com.tehoubi.ddrtracker.ui.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tehoubi.ddrtracker.core.api.ComparedChart
import com.tehoubi.ddrtracker.core.api.Comparison
import com.tehoubi.ddrtracker.core.api.Leaderboard
import com.tehoubi.ddrtracker.core.api.LeaderboardEntry
import com.tehoubi.ddrtracker.core.api.PlayerSummary
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.ScanValidator
import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.OcrLine
import com.tehoubi.ddrtracker.core.ocr.OcrPage
import com.tehoubi.ddrtracker.core.stats.ProgressStats
import com.tehoubi.ddrtracker.core.stats.bestFirst
import com.tehoubi.ddrtracker.data.UploadCounts
import com.tehoubi.ddrtracker.data.settings.LeaderboardAccount
import com.tehoubi.ddrtracker.data.settings.Settings
import com.tehoubi.ddrtracker.domain.model.PlaySource
import com.tehoubi.ddrtracker.ui.chart.BoardState
import com.tehoubi.ddrtracker.ui.chart.ChartContent
import com.tehoubi.ddrtracker.ui.chart.ChartUiState
import com.tehoubi.ddrtracker.ui.compare.CompareContent
import com.tehoubi.ddrtracker.ui.compare.CompareUiState
import com.tehoubi.ddrtracker.ui.history.HistoryContent
import com.tehoubi.ddrtracker.ui.history.HistoryGroup
import com.tehoubi.ddrtracker.ui.history.HistoryUiState
import com.tehoubi.ddrtracker.ui.home.HomeContent
import com.tehoubi.ddrtracker.ui.home.HomeUiState
import com.tehoubi.ddrtracker.ui.play.PlayDetailContent
import com.tehoubi.ddrtracker.ui.play.PlayDetailUiState
import com.tehoubi.ddrtracker.ui.progress.LevelLamps
import com.tehoubi.ddrtracker.ui.progress.ProgressContent
import com.tehoubi.ddrtracker.ui.progress.ProgressUiState
import com.tehoubi.ddrtracker.ui.review.PlayForm
import com.tehoubi.ddrtracker.ui.review.ReviewContent
import com.tehoubi.ddrtracker.ui.review.ReviewMode
import com.tehoubi.ddrtracker.ui.review.ReviewUiState
import com.tehoubi.ddrtracker.ui.review.check
import com.tehoubi.ddrtracker.ui.settings.SettingsActions
import com.tehoubi.ddrtracker.ui.settings.SettingsContent
import com.tehoubi.ddrtracker.ui.settings.SettingsUiState
import com.tehoubi.ddrtracker.ui.theme.DDRTrackerTheme
import com.tehoubi.ddrtracker.core.model.ClearLamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Renders each screen with sample data. `./gradlew :app:recordRoborazziDebug`
 * refreshes the images in src/test/screenshots; `verifyRoborazziDebug` fails
 * on any visual change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    init { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    private val plays = SampleData.plays
    private val stats = ProgressStats(plays, ZoneOffset.UTC)

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { DDRTrackerTheme(darkTheme = true) { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun home() = shoot("home") {
        val last = SampleData.sessions.first()
        HomeContent(
            HomeUiState(
                isLoading = false,
                overview = stats.overview(LocalDate.parse("2026-05-17")),
                lastSession = last,
                lastSessionPlays = plays.filter { it.sessionId == last.id },
                recent = plays.take(12),
                newBestIds = stats.newBestIds(),
            ),
            {}, {}, {}, {},
        )
    }

    @Test
    fun homeEmpty() = shoot("home-empty") { HomeContent(HomeUiState(isLoading = false), {}, {}, {}, {}) }

    @Test
    fun history() = shoot("history") {
        val byId = SampleData.sessions.associateBy { it.id }
        val groups = plays.take(14).groupBy { it.sessionId }.map { (id, list) -> HistoryGroup(byId[id], list) }
        HistoryContent(HistoryUiState(groups = groups, total = plays.size, shown = plays.size, bestIds = stats.newBestIds(), isLoading = false), {}, {}, {})
    }

    @Test
    fun progress() = shoot("progress") {
        val weeks = stats.weekly()
        val lamps = stats.personalBests.values.groupBy { it.level }.map { (level, list) ->
            LevelLamps(
                level,
                list.count { it.lamp == ClearLamp.FAILED },
                list.count { it.lamp == ClearLamp.CLEAR },
                list.count { it.lamp == ClearLamp.FULL_COMBO || it.lamp == ClearLamp.GREAT_FULL_COMBO },
                list.count { it.lamp >= ClearLamp.PERFECT_FULL_COMBO },
            )
        }.sortedByDescending { it.level }
        ProgressContent(
            ProgressUiState(
                isLoading = false, playCount = plays.size, weeks = weeks, comfortHistory = stats.comfortLevelHistory(),
                comfortLevel = stats.comfortLevel(), levels = stats.levels(), levelLamps = lamps,
                recentExRatio = weeks.takeLast(4).mapNotNull { it.averageExRatio }.average(), fastShare = stats.fastShare(),
                sessions = SampleData.sessions.take(5),
            )
        )
    }

    @Test
    fun playDetail() = shoot("play-detail") {
        val play = plays.first { it.songTitle == "PARANOiA" }
        val history = plays.filter { it.chartKey == play.chartKey }.sortedBy { it.playedAt }
        PlayDetailContent(
            PlayDetailUiState(
                isLoading = false, play = play, best = history.sortedWith(bestFirst).first(),
                attempt = history.indexOf(play) + 1, attempts = history.size, deltaToPreviousBest = 4_210,
            ),
            {}, {}, {}, {},
        )
    }

    @Test
    fun chart() = shoot("chart") {
        val key = plays.first { it.songTitle == "MAX 300" }.chartKey
        val progress = stats.chart(key)!!
        val entries = listOf("Kaito" to 994_120, "Tehoubi" to progress.best.score, "Mika" to 951_300, "Ren" to 902_880)
            .sortedByDescending { it.second }
            .mapIndexed { i, (name, score) ->
                LeaderboardEntry(i + 1, if (name == "Tehoubi") "me" else name, name, score, 0, com.tehoubi.ddrtracker.core.scoring.DdrScoring.grade(score).name, "CLEAR", 0)
            }
        ChartContent(
            ChartUiState(
                isLoading = false, title = "MAX 300", label = "ESP 15", progress = progress,
                board = BoardState.Loaded(
                    Leaderboard("max-300", "MAX 300", "SINGLE", "EXPERT", 15, 4, entries, entries.first { it.playerId == "me" }, 0.67)
                ),
            ),
            {}, {},
        )
    }

    @Test
    fun review() = shoot("review") {
        val page = OcrPage(1280, 720, listOf(
            "Butterfly" to Box(420, 60, 640, 100), "EXPERT" to Box(420, 140, 540, 168), "12" to Box(552, 140, 584, 168),
            "999,830" to Box(700, 250, 1010, 320),
        ).map { OcrLine.of(it.first, it.second) } + listOf(
            "MARVELOUS" to "452", "PERFECT" to "12", "GREAT" to "0", "GOOD" to "0", "O.K." to "34", "MISS" to "0", "EX SCORE" to "1482",
        ).flatMapIndexed { i, (l, v) ->
            listOf(OcrLine.of(l, Box(420, 400 + i * 34, 420 + l.length * 18, 426 + i * 34)), OcrLine.of(v, Box(800, 400 + i * 34, 860, 426 + i * 34)))
        })
        val scan = ScanValidator.check(ResultScreenParser().parse(page))
        val form = PlayForm.fromScan(scan, PlaySource.CAMERA, Instant.parse("2026-05-17T03:00:00Z"))
        ReviewContent(ReviewUiState(ReviewMode.SCAN, form, form.check()), {}, {}, {}, {}, {})
    }

    @Test
    fun compare() = shoot("compare") {
        val charts = listOf(
            ComparedChart("pluto-relinquish", "Pluto Relinquish", "SINGLE", "CHALLENGE", 16, 871_220, 902_440),
            ComparedChart("max-300", "MAX 300", "SINGLE", "EXPERT", 15, 948_310, 940_120),
            ComparedChart("paranoia", "PARANOiA", "SINGLE", "EXPERT", 14, 967_550, 967_550),
            ComparedChart("dead-end", "DEAD END", "SINGLE", "EXPERT", 13, 981_020, 955_870),
            ComparedChart("butterfly", "Butterfly", "SINGLE", "EXPERT", 12, 995_430, 989_960),
        )
        CompareContent(
            CompareUiState(
                connected = true,
                comparison = Comparison(PlayerSummary("me", "Tehoubi"), PlayerSummary("k", "Kaito"), 3, 1, 1, charts),
            ),
            {}, {}, {}, {}, {}, {}, { _, _, _ -> },
        )
    }

    @Test
    fun settings() = shoot("settings") {
        SettingsContent(
            SettingsUiState(
                settings = Settings(
                    serverUrl = "http://192.168.1.20:8080",
                    account = LeaderboardAccount("http://192.168.1.20:8080", "me", "Tehoubi", "t"),
                ),
                uploads = UploadCounts(pending = 2, submitted = 184, rejected = 1),
                serverUrlDraft = "http://192.168.1.20:8080",
                nameDraft = "Tehoubi",
            ),
            onBack = {},
            actions = SettingsActions(),
        )
    }
}
