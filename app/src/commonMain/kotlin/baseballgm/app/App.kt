package baseballgm.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import baseballgm.app.screen.LeagueBuildingScreen
import baseballgm.app.screen.MainShell
import baseballgm.app.screen.SplashScreen
import baseballgm.app.screen.NameEntryScreen
import baseballgm.app.screen.TeamSelectScreen
import baseballgm.app.screen.TitleScreen
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.BackHandler
import baseballgm.app.ui.ExitConfirmDialog
import baseballgm.app.ui.TeamColorSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * 앱 뿌리. 큰 흐름만 가진다:
 *
 * ```
 * 스플래시(데이터·세이브 읽기) → 타이틀 ─┬─ 이어하기 → 메인 탭 셸
 *                                       └─ 새 게임 → 단장 이름 → 팀 선택 → (확인 창) → 리그 구성 → 메인 탭 셸(첫 인사)
 * ```
 *
 * 화면 코드는 엔진 상태를 직접 고치지 않는다 — 전부 [GameSession] 을 거친다 (불변 원칙 1).
 */
@Composable
fun App(loadHost: suspend () -> GameHost, darkTheme: Boolean = isSystemInDarkTheme()) {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash(LaunchState.Loading)) }
    var loadedHost by remember { mutableStateOf<GameHost?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val skipSplash = remember { CompletableDeferred<Unit>() }
    var askExit by remember { mutableStateOf(false) }
    // 세이브에서 되살린 세션. 새 게임 흐름에서 타이틀로 돌아와도 이어하기 카드가 남게 따로 들고 있는다
    var titleResume by remember { mutableStateOf<GameSession?>(null) }

    // ---------- 스플래시: 데이터·세이브 읽기 + 최소 노출 ----------
    LaunchedEffect(Unit) {
        val shownAt = TimeSource.Monotonic.markNow()
        // 밸런스·구단 데이터. 웹·iOS 는 앱 리소스에서 읽는다 (HostFactory)
        val host = runCatching { loadHost() }.getOrElse {
            loadError = it.message ?: it::class.simpleName
            return@LaunchedEffect
        }
        loadedHost = host
        val launch = withContext(Dispatchers.Default) {
            LaunchState.from(runCatching { host.saveStore.read() }.getOrNull())
        }
        // 세이브가 있으면 시즌 상태까지 만든 뒤에 스플래시를 내린다 (홈이 바로 그려지게)
        val restored = (launch as? LaunchState.HasSave)?.let { hasSave ->
            withContext(Dispatchers.Default) {
                runCatching { host.resume(hasSave.save) }.getOrNull()
            }
        }
        screen = AppScreen.Splash(if (launch is LaunchState.HasSave && restored == null) LaunchState.NoSave else launch)

        val remaining = SPLASH_MIN_MILLIS.milliseconds - shownAt.elapsedNow()
        if (remaining.isPositive()) withTimeoutOrNull(remaining) { skipSplash.await() }

        titleResume = restored
        screen = AppScreen.Title(resume = restored)
    }

    val host = loadedHost
    val teamColors = remember(host) { host?.teams.orEmpty().map { TeamColorSpec(it.id, it.color, it.colorDark) } }
    AppTheme(teamColors, darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = screen,
                // 같은 단계 안에서 값만 바뀔 때(스플래시 로딩 끝, 부임 메모 닫기)는 전환하지 않는다
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
            ) { current ->
                // 스플래시가 끝나기 전에는 host 가 없을 수 있다. 그 뒤로는 항상 있다
                if (current is AppScreen.Splash || host == null) {
                    SplashScreen(
                        loading = current is AppScreen.Splash && current.launch == LaunchState.Loading,
                        error = loadError,
                        onSkip = { skipSplash.complete(Unit) },
                    )
                    return@AnimatedContent
                }
                when (current) {
                    is AppScreen.Splash -> Unit

                    is AppScreen.Title -> {
                        BackHandler(enabled = host.exitApp != null) { askExit = true }
                        TitleScreen(
                            resume = current.resume,
                            onContinue = { current.resume?.let { screen = AppScreen.Main(it, welcome = false) } },
                            onNewGame = { screen = AppScreen.NameEntry },
                        )
                    }

                    AppScreen.NameEntry -> {
                        BackHandler { screen = AppScreen.Title(resume = titleResume) }
                        NameEntryScreen { name -> screen = AppScreen.SelectTeam(name) }
                    }

                    is AppScreen.SelectTeam -> {
                        BackHandler { screen = AppScreen.NameEntry }
                        TeamSelectScreen(host.teams) { team -> screen = AppScreen.Building(team, current.gmName) }
                    }

                    is AppScreen.Building -> BuildingStep(
                        host = host,
                        step = current,
                        onDone = { session -> screen = AppScreen.Main(session, welcome = true) },
                        onFailed = { message -> screen = current.copy(error = message) },
                        onClearError = { screen = current.copy(error = null) },
                        onBack = { screen = AppScreen.SelectTeam(current.gmName) },
                    )

                    is AppScreen.Main -> MainShell(
                        host = host,
                        session = current.session,
                        welcome = current.welcome,
                        onDismissWelcome = { screen = current.copy(welcome = false) },
                    )
                }
            }

            val exit = host?.exitApp
            if (askExit && exit != null) ExitConfirmDialog(onExit = exit, onDismiss = { askExit = false })
        }
    }
}

/**
 * 리그 구성 단계. 고정 리그를 읽고(docs/03) 시즌 상태를 만든 뒤 첫 세이브를 남긴다.
 * 너무 빨리 끝나 화면이 번쩍이지 않게 최소 노출 시간을 둔다.
 */
@Composable
private fun BuildingStep(
    host: GameHost,
    step: AppScreen.Building,
    onDone: (GameSession) -> Unit,
    onFailed: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf("") }

    // 구성 중에는 뒤로가기를 막는다. 실패 화면에서는 구단 다시 고르기로 돌아간다
    BackHandler { if (step.error != null) onBack() }

    LaunchedEffect(attempt) {
        val startedAt = TimeSource.Monotonic.markNow()
        val result = runCatching {
            stage = "리그 자료 챙기고 있어요."
            val league = withContext(Dispatchers.Default) { host.loadStartingLeague() }
            stage = "${league.season}시즌 리그를 꾸리는 중이에요. 일정이랑 엔트리까지 맞추고 있어요."
            val session = withContext(Dispatchers.Default) { host.newSession(league, step.team.id, step.gmName) }
            stage = "거의 다 됐어요. 단장님 자리 정리만 남았어요."
            withContext(Dispatchers.Default) { host.save(session) }
            session
        }
        val remaining = BUILDING_MIN_MILLIS.milliseconds - startedAt.elapsedNow()
        if (remaining.isPositive()) delay(remaining)
        result.fold(onSuccess = onDone, onFailure = { onFailed(it.message ?: it::class.simpleName ?: "알 수 없는 오류") })
    }

    LeagueBuildingScreen(
        team = step.team,
        stage = stage,
        error = step.error,
        onRetry = {
            onClearError()
            attempt++
        },
        onBack = onBack,
    )
}

/** 스플래시 최소 노출. 세이브를 빨리 읽어도 로고는 이만큼 보여 준다 (탭하면 건너뜀) */
private const val SPLASH_MIN_MILLIS = 1_800L

/** 리그 구성 화면 최소 노출. 진행 문구를 읽을 틈을 준다 */
private const val BUILDING_MIN_MILLIS = 1_200L

private const val FADE_MILLIS = 350
