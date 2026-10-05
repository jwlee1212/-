package baseballgm.app.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import baseballgm.app.BriefingTarget
import baseballgm.app.GameHost
import baseballgm.app.GameSession
import baseballgm.app.ProgressAction
import baseballgm.app.navigation.MainTab
import baseballgm.app.navigation.Route
import baseballgm.app.navigation.TabNavigator
import baseballgm.app.ui.BackHandler
import baseballgm.app.ui.ExitConfirmDialog
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.TeamColors
import baseballgm.app.ui.TeamEmblem
import baseballgm.app.ui.Secretary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 메인 탭 셸. 새 게임·이어하기 모두 여기로 들어온 뒤로는 계속 이 구조다.
 *
 * ```
 * ┌ 머리줄: [←] 화면 제목            [바로가기] ┐
 * │ 지금 탭의 맨 위 화면 (탭별 백스택)          │
 * ├ 진행 버튼 (구단 색, 항상 고정)             ┤
 * └ 하단 탭: 홈 / 로스터 / 스카우트 / 시장 / 기록 ┘
 * ```
 *
 * - 뉴스·구단은 홈 카드에서 여는 전체 화면이다 (2026-09-27). 돌발 이벤트가 생기면 어느 탭에 있든 창이 뜬다.
 *
 * - 탭마다 백스택이 따로 있다([TabNavigator]). 스크롤 위치·탭 안의 선택은 SaveableStateHolder 가
 *   화면별로 보관해서, 다른 탭에 다녀와도 그대로 남는다.
 * - 뒤로가기: 하위 화면이면 한 칸 뒤로, 탭 루트면 앱 종료 확인 창.
 */
@Composable
fun MainShell(
    host: GameHost,
    session: GameSession,
    welcome: Boolean,
    onDismissWelcome: () -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val navigator = remember(session) { TabNavigator(onDiscard = holder::removeState) }
    var askExit by remember { mutableStateOf(false) }
    // 돌발 이벤트 창: "나중에"를 누른 이벤트는 다시 띄우지 않는다 (카드는 홈에 남는다)
    var laterIncidentId by remember { mutableStateOf<String?>(null) }
    // 이번 주를 시작할 때의 (주차, 순위). 결과 공개 화면의 순위 변화에 쓴다
    var weekStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // 영입 탭의 구역. 셸이 들고 있어서 "결정할 것"·드래프트 진행이 바로 그 구역으로 보낼 수 있다 (2026-10-03)
    var recruitSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(RecruitSection.TRADE) }
    // 다른 탭에서 리그 탭의 한 구역으로 바로 보낼 때 (선수단 → 팀 기록표, 2026-10-04): 리그 허브 위에 그 구역을 쌓는다
    val openLeague: (LeagueSection, Boolean) -> Unit = { section, teamScope ->
        navigator.openRoot(MainTab.LEAGUE)
        navigator.push(Route.LeagueDetail(section.name, teamScope))
    }
    val openRecruit: (RecruitSection) -> Unit = { section ->
        recruitSection = section
        navigator.openRoot(MainTab.RECRUIT)
    }
    // 주차별로 결과 공개를 몇 장까지 봤는지 (공개 화면을 나갔다 들어와도 이어 보게)
    val revealMemory = remember { mutableMapOf<Int, Int>() }
    val snackbar = remember { SnackbarHostState() }

    // 뒤로가기: 하위 화면이면 pop. 탭 루트에서는 앱을 끝낼 수 있는 플랫폼만 종료 확인을 띄운다
    BackHandler(enabled = navigator.canPop || host.exitApp != null) {
        if (!navigator.pop()) askExit = true
    }

    AutoSave(host, session, snackbar)

    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val teamColor = TeamColors.of(session.userTeamId)
    val tab = navigator.currentTab
    val stack = navigator.stackOf(tab)
    val route = stack.last()

    Scaffold(
        topBar = {
            RouteHeader(
                title = titleOf(route, session),
                emblem = if (route == Route.Root(MainTab.HOME)) {
                    { TeamEmblem(session.userTeamId, session.userTeam.nickname.take(1), size = 30.dp) }
                } else {
                    null
                },
                canPop = stack.size > 1,
                onBack = { navigator.pop() },
                shortcuts = shortcutsOf(route),
                onShortcut = navigator::push,
            )
        },
        bottomBar = {
            Column {
                // 진행 버튼은 홈에서만 (유저 요청 2026-09-27). 주간 브리핑은 홈에서 여는 "승인" 화면이라 같이 둔다
                if (tab == MainTab.HOME && (route is Route.Root || route == Route.WeeklyReport)) {
                    ProgressBar(session, teamColor, approving = route == Route.WeeklyReport) { action ->
                        if (action == ProgressAction.ResolveIncident) laterIncidentId = null
                        // 한 주 진행은 결과 공개 화면이 맡는다 (2026-10-02 재미 개선 1번): 화면을 먼저 열고, 그 화면이
                        // 다음 돌발 이벤트까지 진행 → 공개 → 이벤트 창 → 답하면 재개를 이어 간다.
                        // 주를 시작할 때의 순위를 적어 둔다 — 공개 화면이 "4위 → 3위"를 보여 준다
                        when (action) {
                            is ProgressAction.PlayWeek -> {
                                weekStart = session.week to session.rank()
                                navigator.push(Route.WeekReveal(session.week, session.rank()))
                            }
                            is ProgressAction.ContinueWeek ->
                                navigator.push(Route.WeekReveal(session.week, weekStart?.takeIf { it.first == session.week }?.second))
                            ProgressAction.GoToDraft -> openRecruit(RecruitSection.SCOUT)
                            else -> runProgress(action, session, navigator)
                        }
                    }
                }
                NavigationBar {
                    // 메시지 탭 배지: 답장이 필요한 수 (돌발 이벤트 + 급한 결정할 것)
                    val needsReply = baseballgm.app.Messages.needsReply(session)
                    MainTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { navigator.selectTab(entry) },
                            icon = {
                                if (entry == MainTab.MESSAGES && needsReply > 0) {
                                    androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge { Text("$needsReply") } }) {
                                        Icon(iconOf(entry), contentDescription = "${entry.label}, 답장 필요 ${needsReply}건")
                                    }
                                } else {
                                    Icon(iconOf(entry), contentDescription = entry.label)
                                }
                            },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = StackEntry(tab, stack.lastIndex, route),
                contentKey = { navigator.stateKey(it.tab, it.depth, it.route) },
                transitionSpec = {
                    when {
                        // 탭 전환: 짧게 교차 페이드
                        initialState.tab != targetState.tab ->
                            fadeIn(tween(TAB_FADE_MILLIS)) togetherWith fadeOut(tween(TAB_FADE_MILLIS))
                        // push: 오른쪽에서 들어온다
                        targetState.depth > initialState.depth ->
                            (slideInHorizontally(tween(SLIDE_MILLIS)) { it / 3 } + fadeIn(tween(SLIDE_MILLIS))) togetherWith
                                fadeOut(tween(SLIDE_MILLIS / 2))
                        // pop: 오른쪽으로 빠진다
                        else ->
                            fadeIn(tween(SLIDE_MILLIS)) togetherWith
                                (slideOutHorizontally(tween(SLIDE_MILLIS)) { it / 3 } + fadeOut(tween(SLIDE_MILLIS)))
                    }
                },
            ) { entry ->
                holder.SaveableStateProvider(navigator.stateKey(entry.tab, entry.depth, entry.route)) {
                    RouteContent(entry.route, session, navigator, welcome, onDismissWelcome, revealMemory, recruitSection, { recruitSection = it }, openRecruit, openLeague) { laterIncidentId = null }
                }
            }
        }
    }

    session.pendingIncident?.let { incident ->
        // 결과 공개 중에는 창을 띄우지 않는다 (다음 주 시작 이벤트가 연출을 끊지 않게). 닫으면 뜬다
        // 급한 일만 어느 탭에서든 창으로 (2026-10-03). 나머지·기회형은 메시지 탭에 쌓이고 배지로 알린다.
        // 자동 진행이 멈춘 결정도 창으로 — 답하면 자동 진행이 이어진다
        if ((incident.kind.urgent || session.autoTarget != null) && incident.id != laterIncidentId && route !is Route.WeekReveal && tab != MainTab.MESSAGES) {
            // 창에서 선수를 보러 가면 창은 접고(카드는 홈에 남는다) 그 화면을 연다
            IncidentDialog(
                session = session,
                incident = incident,
                onLater = { laterIncidentId = incident.id },
                onPlayer = {
                    laterIncidentId = incident.id
                    navigator.push(Route.PlayerDetail(it))
                },
                onCompare = {
                    laterIncidentId = incident.id
                    navigator.push(Route.Compare(it))
                },
            )
        }
    }

    val exit = host.exitApp
    if (askExit && exit != null) ExitConfirmDialog(onExit = exit, onDismiss = { askExit = false })
}

/** 애니메이션 대상. 탭·깊이를 알아야 전환 방향(push/pop/탭)을 정할 수 있다 */
private data class StackEntry(val tab: MainTab, val depth: Int, val route: Route)

@Composable
private fun RouteContent(
    route: Route,
    session: GameSession,
    navigator: TabNavigator,
    welcome: Boolean,
    onDismissWelcome: () -> Unit,
    revealMemory: MutableMap<Int, Int>,
    recruitSection: RecruitSection,
    /** 영입 탭 안에서 구역만 바꾼다 (탭을 다시 열지 않는다 — 다시 열면 화면 상태가 지워진다) */
    onRecruitSection: (RecruitSection) -> Unit,
    openRecruit: (RecruitSection) -> Unit,
    openLeague: (LeagueSection, Boolean) -> Unit,
    onShowIncident: () -> Unit,
) {
    val openPlayer: (baseballgm.model.PlayerId) -> Unit = { navigator.push(Route.PlayerDetail(it)) }
    val openCompare: (List<baseballgm.model.PlayerId>) -> Unit = { navigator.push(Route.Compare(it)) }
    when (route) {
        is Route.Root -> when (route.tab) {
            MainTab.HOME -> HomeScreen(
                session = session,
                welcome = welcome,
                onDismissWelcome = onDismissWelcome,
                actions = HomeActions(
                    openReport = { navigator.push(Route.WeeklyReport) },
                    openClub = { navigator.push(Route.Club) },
                    openCareer = { navigator.push(Route.Career) },
                    openDecision = { openDecision(it, navigator, openRecruit) },
                    openMessages = { navigator.openRoot(MainTab.MESSAGES) },
                    openSquad = { navigator.openRoot(MainTab.SQUAD) },
                    openRecruit = { navigator.openRoot(MainTab.RECRUIT) },
                    openLeague = { navigator.openRoot(MainTab.LEAGUE) },
                    openPlayer = openPlayer,
                    openCompare = openCompare,
                    openPostseason = { navigator.push(Route.Postseason) },
                    openPostseasonGame = { navigator.push(Route.PostseasonWatch(it)) },
                    openAwards = { navigator.push(Route.Awards(it)) },
                ),
            )
            MainTab.SQUAD -> SquadScreen(session, openPlayer, onLeague = openLeague)
            MainTab.RECRUIT -> RecruitScreen(
                session,
                section = recruitSection,
                onSection = onRecruitSection,
                onPlayer = openPlayer,
                onCompare = openCompare,
                onProspect = { navigator.push(Route.Prospect(it)) },
                onDraftClass = { navigator.push(Route.DraftClass) },
                onScoutDigest = { navigator.push(Route.ScoutDigest()) },
                onNegotiate = { navigator.push(Route.FaNegotiation(it)) },
                onExtension = { navigator.push(Route.ExtensionNegotiation(it)) },
            )
            MainTab.MESSAGES -> MessagesScreen(
                session,
                onThread = { navigator.push(Route.MessageThread(it.name)) },
                onDecision = { openDecision(it, navigator, openRecruit) },
                onPlayer = openPlayer,
                onCompare = openCompare,
            )
            MainTab.LEAGUE -> LeagueHubScreen(session) { section -> navigator.push(Route.LeagueDetail(section.name)) }
        }
        is Route.MessageThread -> MessageThreadScreen(
            session,
            baseballgm.app.Sender.valueOf(route.sender),
            onScoutDigest = { navigator.push(Route.ScoutDigest()) },
        )
        is Route.ScoutDigest -> ScoutDigestScreen(session, route.index) { navigator.push(Route.Prospect(it)) }
        is Route.News -> NewsScreen(session, route.tab)
        Route.Club -> ClubScreen(session)
        is Route.Awards -> AwardsScreen(session, route.season, openPlayer)
        is Route.LeagueDetail -> LeagueSectionScreen(
            session,
            LeagueSection.valueOf(route.section),
            openPlayer,
            onAwards = { navigator.push(Route.Awards(it)) },
            teamScope = route.teamScope,
        )
        Route.Postseason -> PostseasonScreen(session) { navigator.push(Route.PostseasonWatch(it)) }
        is Route.PostseasonWatch -> {
            val played = session.postseasonGames
            WatchScreen(
                session,
                override = played.map { baseballgm.season.WatchedGame(it.box, it.events) },
                labels = played.map { "${it.round.label} ${it.gameNumber}차전" },
                initial = route.index,
            )
        }
        Route.OffseasonPrep -> OffseasonPrepScreen(session, openPlayer) { navigator.openRoot(MainTab.HOME) }
        is Route.PlayerDetail -> PlayerDetailScreen(session, route.playerId, openCompare, onExtension = { navigator.push(Route.ExtensionNegotiation(it)) })
        is Route.FaNegotiation -> FaNegotiationScreen(session, route.playerId, openPlayer)
        is Route.ExtensionNegotiation -> ExtensionNegotiationScreen(session, route.playerId, openPlayer)
        is Route.Compare -> CompareScreen(session, route.playerIds, openPlayer)
        is Route.Prospect -> ProspectDetailScreen(session, route.playerId) { navigator.pop() }
        Route.DraftClass -> DraftClassScreen(session) { navigator.push(Route.Prospect(it)) }
        Route.WeeklyReport -> WeeklyReportScreen(session, onWatch = { navigator.push(Route.Watch()) }, onOpenNews = { navigator.push(Route.News(it)) })
        Route.Directive -> DirectiveScreen(session)
        is Route.Watch -> WatchScreen(session, initial = route.initial)
        is Route.WeekReveal -> WeekRevealScreen(
            session = session,
            week = route.week,
            rankBefore = route.rankBefore,
            // 공개 도중 나갔다 다시 들어오면(이벤트에서 선수 상세를 보고 오는 등) 본 데까지는 다시 연출하지 않는다
            initialShown = revealMemory[route.week] ?: 0,
            onShown = { revealMemory[route.week] = it },
            onWatch = { navigator.push(Route.Watch(it)) },
            onReport = { navigator.pop(); navigator.push(Route.WeeklyReport) },
            onClose = { navigator.pop() },
            onPlayer = { navigator.push(Route.PlayerDetail(it)) },
            onCompare = { navigator.push(Route.Compare(it)) },
        )
        Route.Career -> CareerScreen(session)
    }
}

/**
 * 자동 저장 (docs/14, 2026-10-01 — 시즌 중 저장).
 *
 * 상태가 바뀔 때마다(한 주 진행, 엔트리 조작, 트레이드, 돌발 이벤트 답 …) 저장한다. 연달아 누를 때 매번 쓰지 않도록
 * **마지막 변경 뒤 [SAVE_DEBOUNCE_MILLIS] 동안 조용하면** 쓴다 — 새 변경이 오면 앞의 저장은 취소된다.
 * 저장할 수 없는 순간(주중 멈춤·드래프트 도중·스토브리그)이면 건너뛰고 직전 세이브가 남는다 ([GameSession.saveData]).
 *
 * 세이브 묶기는 화면 스레드에서(상태를 읽는 동안 바뀌지 않게), 문자열로 바꾸고 쓰는 일은 백그라운드에서 한다.
 */
@Composable
private fun AutoSave(host: GameHost, session: GameSession, snackbar: SnackbarHostState) {
    val revision = session.revision
    var first by remember(session) { mutableStateOf(true) }
    LaunchedEffect(session, revision) {
        // 처음 그릴 때는 이미 저장돼 있다 (새 게임 = 구성 직후 저장, 이어하기 = 세이브에서 읽음)
        if (first) {
            first = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(SAVE_DEBOUNCE_MILLIS)
        val data = session.saveData() ?: return@LaunchedEffect
        val result = withContext(Dispatchers.Default) {
            runCatching { host.saveStore.write(baseballgm.io.SaveGameCodec.encode(data)) }
        }
        result.exceptionOrNull()?.let { snackbar.showSnackbar("저장하지 못했어요: ${it.message}") }
    }
}

private const val SAVE_DEBOUNCE_MILLIS = 1_000L

// ---------- 진행 버튼 ----------

@Composable
private fun ProgressBar(
    session: GameSession,
    teamColor: Color,
    approving: Boolean,
    onPress: (ProgressAction) -> Unit,
) {
    // 주차·시즌 단계는 스냅샷 상태가 아니라서 revision 을 여기서 직접 읽어야 버튼 문구가 따라 바뀐다
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val action = ProgressAction.of(session)
    val tokens = AppTheme.tokens
    // 2026-10-02 레퍼런스 리팩토링: 가로로 꽉 차고 두꺼운 버튼. 화면에서 구단 색으로 칠한 유일한 큰 면이다 (docs/16 §3).
    // 바탕은 화면 바탕과 같은 중립색 — 예전 tonalElevation 은 Material 이 브랜드 색을 섞어 칠했다
    // 자동 진행(올스타까지·시즌 끝까지)은 옆 "⋯" 메뉴 — 더쇼의 "어디까지 시뮬" (2026-10-03, 예전엔 홈 일정 카드 아래 글자 버튼)
    val canAuto = action is ProgressAction.PlayWeek && !session.busy
    var menu by remember { mutableStateOf(false) }
    Surface(color = tokens.base.background) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onPress(action) },
                enabled = !session.busy,
                modifier = Modifier
                    .weight(1f)
                    .height(tokens.sizes.progressButton),
                shape = RoundedCornerShape(tokens.radii.control),
                colors = ButtonDefaults.buttonColors(containerColor = teamColor, contentColor = tokens.base.onTeam),
                contentPadding = PaddingValues(horizontal = tokens.spacing.l),
            ) {
                // 주간 브리핑을 보고 있을 때 진행 = "승인" (docs/16 §2)
                val label = if (approving && action is ProgressAction.PlayWeek) "승인하고 ${action.label}" else action.label
                // 홈의 주인공 둘 중 하나라 큰 글자 단계(20sp Bold)를 쓴다 (docs/16 절제 규칙)
                Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canAuto) {
                Spacer(Modifier.width(tokens.spacing.s))
                Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(tokens.sizes.progressButton),
                        shape = RoundedCornerShape(tokens.radii.control),
                        contentPadding = PaddingValues(),
                    ) { Icon(Icons.Filled.MoreHoriz, contentDescription = "어디까지 자동 진행") }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val calendar = session.state.calendar
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("올스타 브레이크까지") },
                            enabled = session.week <= calendar.allStarBreakAfterWeek,
                            onClick = { menu = false; session.advanceUntil(calendar.allStarBreakAfterWeek) },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("정규시즌 끝까지") },
                            onClick = { menu = false; session.advanceUntil(calendar.regularSeasonWeeks) },
                        )
                        Text(
                            "드래프트·트레이드·계약 결정에만 멈춰요.\n부상·콜업 같은 엔트리 정리는 ${Secretary.NAME}이 처리해요",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.base.textMuted,
                            modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.s),
                        )
                    }
                }
            }
        }
    }
}

/** 비서의 "결정할 것"이 데려가는 곳 (2026-10-03 탭 개편 반영) */
private fun openDecision(target: BriefingTarget, navigator: TabNavigator, openRecruit: (RecruitSection) -> Unit) {
    when (target) {
        BriefingTarget.INCIDENT -> navigator.openRoot(MainTab.MESSAGES)
        BriefingTarget.CAREER -> navigator.open(MainTab.HOME, Route.Career)
        BriefingTarget.MARKET -> openRecruit(RecruitSection.TRADE)
        BriefingTarget.SCOUT -> openRecruit(RecruitSection.SCOUT)
        BriefingTarget.ROSTER -> navigator.openRoot(MainTab.SQUAD)
        BriefingTarget.RECORDS -> navigator.openRoot(MainTab.LEAGUE)
        BriefingTarget.OFFSEASON -> navigator.open(MainTab.HOME, Route.OffseasonPrep)
    }
}

private fun runProgress(action: ProgressAction, session: GameSession, navigator: TabNavigator) {
    when (action) {
        is ProgressAction.PlayWeek -> session.advanceWeek()
        is ProgressAction.ContinueWeek -> session.advanceWeek()
        // 돌발 이벤트는 메시지 탭에서 답한다 (2026-10-03)
        ProgressAction.ResolveIncident -> navigator.openRoot(MainTab.MESSAGES)
        ProgressAction.GoToDraft -> navigator.openRoot(MainTab.RECRUIT)
        is ProgressAction.PostseasonStep -> session.advancePostseason()
        ProgressAction.PostseasonFinish -> session.runPostseason()
        is ProgressAction.PrepareOffseason -> navigator.open(MainTab.HOME, Route.OffseasonPrep)
        is ProgressAction.FreeAgencyRound -> session.advanceFaRound()
        ProgressAction.ChooseJob -> navigator.open(MainTab.HOME, Route.Career)
    }
}

// ---------- 머리줄 ----------

@Composable
private fun RouteHeader(
    title: String,
    emblem: (@Composable () -> Unit)?,
    canPop: Boolean,
    onBack: () -> Unit,
    shortcuts: List<Pair<String, Route>>,
    onShortcut: (Route) -> Unit,
) {
    Surface {
        Column {
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = AppTheme.tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canPop) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                } else {
                    Box(Modifier.width(AppTheme.tokens.spacing.m))
                }
                if (emblem != null) {
                    emblem()
                    Box(Modifier.width(AppTheme.tokens.spacing.s))
                }
                // 머리줄 제목은 본문 굵게 — 가장 큰 글자는 각 화면의 주인공 몫이다 (절제 규칙)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                shortcuts.forEach { (label, target) ->
                    TextButton(onClick = { onShortcut(target) }) { Text(label) }
                }
            }
            HorizontalDivider()
        }
    }
}

private fun titleOf(route: Route, session: GameSession): String = when (route) {
    is Route.Root -> if (route.tab == MainTab.HOME) session.userTeam.name else route.tab.label
    is Route.MessageThread -> baseballgm.app.Sender.valueOf(route.sender).label
    is Route.PlayerDetail -> "선수 상세"
    is Route.FaNegotiation -> "FA 협상 테이블"
    is Route.ExtensionNegotiation -> "다년계약 협상"
    is Route.Prospect -> "스카우트 리포트"
    is Route.ScoutDigest -> "스카우팅 리포트"
    Route.DraftClass -> "우리 신인"
    Route.WeeklyReport -> "주간 브리핑"
    Route.Directive -> "사전 지시"
    is Route.Watch -> "문자 중계"
    is Route.WeekReveal -> "${route.week}주차 결과"
    Route.Career -> "단장 커리어"
    is Route.News -> "뉴스·팬 반응"
    is Route.Compare -> "선수 비교"
    Route.Club -> "구단 운영"
    is Route.Awards -> "${route.season} 시상식"
    is Route.LeagueDetail -> LeagueSection.valueOf(route.section).label
    Route.Postseason -> "포스트시즌"
    is Route.PostseasonWatch -> "포스트시즌 중계"
    Route.OffseasonPrep -> "스토브리그 준비"
}

/** 탭 루트 머리줄의 바로가기. 하단 탭에 들어가지 못한 전체 화면들의 입구다 */
private fun shortcutsOf(route: Route): List<Pair<String, Route>> = when (route) {
    Route.Club -> listOf("커리어" to Route.Career)
    else -> emptyList()
}

private fun iconOf(tab: MainTab): ImageVector = when (tab) {
    MainTab.HOME -> Icons.Filled.Home
    MainTab.SQUAD -> Icons.Filled.Groups
    MainTab.RECRUIT -> Icons.Filled.PersonAdd
    MainTab.MESSAGES -> Icons.Filled.Forum
    MainTab.LEAGUE -> Icons.Filled.Leaderboard
}

private const val TAB_FADE_MILLIS = 150
private const val SLIDE_MILLIS = 260
