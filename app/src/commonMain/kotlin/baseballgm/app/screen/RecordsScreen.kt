package baseballgm.app.screen

import baseballgm.util.fixed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.PlayerLine
import androidx.compose.ui.Alignment
import baseballgm.app.ui.StatTable
import baseballgm.app.ui.TableCell
import baseballgm.app.ui.TableColumn
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.SectionCard
import baseballgm.model.PlayerId

/** 리그 탭의 구역 (2026-10-03 화면 개편). 2026-10-04 부터 허브 카드 순서다 */
enum class LeagueSection(val label: String) {
    STANDINGS("순위"),
    BATTING("타자"),
    PITCHING("투수"),
    TEAMS("팀 기록"),
    NEWS("뉴스"),
    FANS("팬 반응"),
    HISTORY("역대"),
}

/**
 * 리그 탭 (docs/15 M10 표, 2026-10-03 화면 개편 — 예전 "기록" 탭 + 홈의 뉴스·팬 반응).
 * 순위·개인 기록·세이버·뉴스·팬 반응·역대를 가로로 미는 탭 하나에 모았다.
 */
/**
 * 리그 허브에서 들어가는 한 구역의 전체 화면 (2026-10-04, 유저 요청 "상단 탭 대신 컨테이너를 누르면 그 화면으로").
 * 예전엔 리그 탭 상단 가로 탭으로 골랐다. 이제 허브([LeagueHubScreen])의 카드를 누르면 이 화면이 쌓인다.
 *
 * @param teamScope 타자·투수 기록표를 "우리 팀"(팀 내 순위)으로 열지 (선수단 탭 바로가기)
 */
@Composable
fun LeagueSectionScreen(
    session: GameSession,
    section: LeagueSection,
    onPlayer: (PlayerId) -> Unit,
    onAwards: (season: Int) -> Unit = {},
    teamScope: Boolean = false,
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    when (section) {
        // 뉴스·팬 반응은 자기 목록(지연 목록)을 가진다
        LeagueSection.NEWS -> NewsFeed(session)
        LeagueSection.FANS -> FanFeed(session)
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = AppTheme.tokens.spacing.l, vertical = AppTheme.tokens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        ) {
            when (section) {
                LeagueSection.STANDINGS -> StandingsTable(session)
                LeagueSection.BATTING -> StatsBoard(session, pitchers = false, onPlayer = onPlayer, teamScope = teamScope)
                LeagueSection.PITCHING -> StatsBoard(session, pitchers = true, onPlayer = onPlayer, teamScope = teamScope)
                LeagueSection.TEAMS -> TeamStatsBoard(session)
                else -> HistoryTab(session, onPlayer, onAwards)
            }
            Spacer(Modifier.height(AppTheme.tokens.spacing.s))
        }
    }
}

/**
 * 순위표. 첫 열(순위·구단)은 고정하고 숫자 열은 가로로 넘긴다 (docs/16 §3).
 * 득실까지 한 표에 넣었다 — 폭이 좁은 폰에서도 칸이 접히지 않는다.
 */
/**
 * 순위 변화 표시: 올랐으면 위 화살표 + 칸 수(좋음), 내렸으면 아래 화살표(나쁨), 그대로면 아무것도 없다.
 * 색만이 아니라 아이콘 모양과 숫자로도 구분한다.
 */
@Composable
private fun RankDelta(delta: Int) {
    if (delta == 0) return
    val tokens = AppTheme.tokens
    val color = if (delta > 0) tokens.semantic.good else tokens.semantic.bad
    baseballgm.app.ui.EnterOnce(delta) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = tokens.spacing.xs)) {
            androidx.compose.material3.Icon(
                if (delta > 0) androidx.compose.material.icons.Icons.Filled.ArrowUpward else androidx.compose.material.icons.Icons.Filled.ArrowDownward,
                contentDescription = if (delta > 0) "${delta}계단 상승" else "${-delta}계단 하락",
                tint = color,
                modifier = Modifier.size(tokens.sizes.statusIcon),
            )
            Text("${kotlin.math.abs(delta)}", style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun StandingsTable(session: GameSession) {
    val tokens = AppTheme.tokens
    val ranked = session.teamsRanked()
    // 탭 이름(순위표)을 되풀이하는 카드 제목은 뺐다
    SectionCard(null) {
        StatTable(
            firstHeader = "구단",
            firstWidth = 156.dp,
            columns = listOf(
                TableColumn("경기", 40.dp),
                TableColumn("승", 36.dp),
                TableColumn("패", 36.dp),
                TableColumn("무", 30.dp),
                TableColumn("승률", 52.dp),
                TableColumn("차", 40.dp),
                TableColumn("연속", 48.dp),
                TableColumn("득", 40.dp),
                TableColumn("실", 40.dp),
                TableColumn("득실", 48.dp),
            ),
            rowCount = ranked.size,
            firstCell = { row ->
                val record = ranked[row]
                val isUser = record.teamId == session.userTeamId
                Text(
                    "${row + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row < POSTSEASON_SPOTS) tokens.semantic.good else tokens.base.textMuted,
                    modifier = Modifier.width(22.dp),
                )
                Text(
                    session.league.team(record.teamId).name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) tokens.base.brand else tokens.base.text,
                    fontWeight = if (isUser) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 지난주 대비 순위 변화 (재미 개선 5번): 아이콘 모양 + 숫자, 처음 보일 때 살짝 들어온다
                session.ranksAtWeekStart[record.teamId]?.let { before -> RankDelta(before - (row + 1)) }
            },
            cell = { row, column ->
                val record = ranked[row]
                when (column) {
                    0 -> TableCell("${record.games}")
                    1 -> TableCell("${record.wins}")
                    2 -> TableCell("${record.losses}")
                    3 -> TableCell("${record.ties}")
                    4 -> TableCell(record.winPct.fixed(3), bold = true)
                    5 -> TableCell((session.gamesBehind(record.teamId)).fixed(1))
                    6 -> TableCell(
                        record.streakText(),
                        if (record.streak >= 0) tokens.semantic.good else tokens.semantic.bad,
                    )
                    7 -> TableCell("${record.runsScored}")
                    8 -> TableCell("${record.runsAllowed}")
                    else -> TableCell(
                        "${if (record.runDifferential >= 0) "+" else ""}${record.runDifferential}",
                        if (record.runDifferential >= 0) tokens.semantic.good else tokens.semantic.bad,
                    )
                }
            },
        )
    }
}

/**
 * 기록표 (2026-10-04, 유저 요청 "팀 내 스탯 순위", "스탯을 한 눈에 보기 어렵다", "유격수 OPS, 일정 타석 이상 필터링").
 *
 * 한 표에서
 * - 범위: 리그 전체 / 우리 팀(1군 기록이 있는 전원 = 팀 내 순위)
 * - 보기: 기본 / 세이버 (docs/16 §3 기본·세이버 분리)
 * - 필터(접이식): 포지션(타자 주 포지션·투수 보직), 최소 타석·이닝(규정 이상 / 규정 절반 / 직접 / 전체)
 * - 머리글을 누르면 그 열로 정렬, 한 번 더 누르면 방향이 바뀐다. 첫 열(순위·이름)은 고정, 숫자 열은 가로로 넘긴다
 */
@Composable
private fun StatsBoard(session: GameSession, pitchers: Boolean, onPlayer: (PlayerId) -> Unit, teamScope: Boolean = false) {
    val tokens = AppTheme.tokens
    val kind = if (pitchers) "p" else "b"
    // 화면 상태 저장은 모든 기기(웹·iOS)에서 되는 정수로 둔다
    var scopeIndex by rememberSaveable(kind + "scope") {
        mutableStateOf((if (teamScope) baseballgm.app.StatScope.TEAM else baseballgm.app.StatScope.LEAGUE).ordinal)
    }
    var viewIndex by rememberSaveable(kind + "view") { mutableStateOf(baseballgm.app.StatView.BASIC.ordinal) }
    val scope = baseballgm.app.StatScope.entries[scopeIndex]
    val view = baseballgm.app.StatView.entries[viewIndex]
    fun columnsOf(v: baseballgm.app.StatView) =
        if (pitchers) baseballgm.app.StatBoard.pitchingColumns(v).map { it.header to it.lowerIsBetter }
        else baseballgm.app.StatBoard.battingColumns(v).map { it.header to it.lowerIsBetter }
    fun defaultSort(v: baseballgm.app.StatView) =
        if (pitchers) baseballgm.app.StatBoard.defaultPitchingSort(v) else baseballgm.app.StatBoard.defaultBattingSort(v)
    var sortColumn by rememberSaveable(kind + "sort") { mutableStateOf(defaultSort(view)) }
    val columnInfo = columnsOf(view)
    val column = sortColumn.coerceIn(columnInfo.indices)
    var descending by rememberSaveable(kind + "desc") { mutableStateOf(!columnInfo[column].second) }

    // 필터: 포지션은 두 범위가 같이 쓰고, 최소 표본은 범위마다 따로 (리그는 규정 이상, 우리 팀은 전원이 기본)
    val positions = baseballgm.app.StatPosition.forPitchers(pitchers)
    var positionIndex by rememberSaveable(kind + "pos") { mutableStateOf(0) }
    var sampleLeague by rememberSaveable(kind + "sampleL") { mutableStateOf(baseballgm.app.SampleMode.QUALIFIED.ordinal) }
    var sampleTeam by rememberSaveable(kind + "sampleT") { mutableStateOf(baseballgm.app.SampleMode.ALL.ordinal) }
    var custom by rememberSaveable(kind + "custom") { mutableStateOf(-1) }
    val defaults = baseballgm.app.StatFilter.default(pitchers, scope)
    val filter = baseballgm.app.StatFilter(
        position = positions[positionIndex.coerceIn(positions.indices)],
        sample = baseballgm.app.SampleMode.entries[if (scope == baseballgm.app.StatScope.LEAGUE) sampleLeague else sampleTeam],
        custom = if (custom >= 0) custom else if (pitchers) session.qualifyingOuts() else session.qualifyingPa(),
    )

    // 조작부는 표 위 한 줄에 모은다 (범위 · 보기)
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            baseballgm.app.StatScope.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = scope == entry,
                    onClick = { scopeIndex = entry.ordinal },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, baseballgm.app.StatScope.entries.size),
                ) { Text(entry.label, maxLines = 1) }
            }
        }
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            baseballgm.app.StatView.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = view == entry,
                    onClick = {
                        if (view != entry) {
                            viewIndex = entry.ordinal
                            sortColumn = defaultSort(entry)
                            descending = !columnsOf(entry)[sortColumn].second
                        }
                    },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, baseballgm.app.StatView.entries.size),
                ) { Text(entry.label, maxLines = 1) }
            }
        }
    }

    val rows: List<Pair<baseballgm.model.Player, (Int) -> String>> = androidx.compose.runtime.remember(session.revision, scope, view, column, descending, filter) {
        if (pitchers) {
            val columns = baseballgm.app.StatBoard.pitchingColumns(view)
            baseballgm.app.StatBoard.pitchingTable(session, scope, view, column, descending, filter).map { row -> row.player to { index: Int -> columns[index].format(row) } }
        } else {
            val columns = baseballgm.app.StatBoard.battingColumns(view)
            baseballgm.app.StatBoard.battingTable(session, scope, view, column, descending, filter).map { row -> row.player to { index: Int -> columns[index].format(row) } }
        }
    }
    // 직접 고르는 최소 표본의 끝: 리그에서 가장 많이 나온 선수 (투수는 아웃)
    val maxSample = androidx.compose.runtime.remember(session.revision, pitchers) {
        if (pitchers) baseballgm.app.StatBoard.pitchers(session).maxOfOrNull { it.line.outs } ?: 0
        else baseballgm.app.StatBoard.batters(session).maxOfOrNull { it.line.plateAppearances } ?: 0
    }
    baseballgm.app.ui.StatFilterBar(
        positions = positions.map { it.label },
        position = positionIndex,
        onPosition = { positionIndex = it },
        samples = baseballgm.app.SampleMode.entries.map { it.label },
        sample = filter.sample.ordinal,
        onSample = { if (scope == baseballgm.app.StatScope.LEAGUE) sampleLeague = it else sampleTeam = it },
        custom = filter.custom,
        customMax = maxSample,
        customStep = if (pitchers) OUTS_PER_INNING else 1,
        customLabel = { value -> if (pitchers) "최소 ${value / OUTS_PER_INNING}이닝" else "최소 ${value}타석" },
        onCustom = { custom = it },
        showCustom = filter.sample == baseballgm.app.SampleMode.CUSTOM,
        activeCount = filter.activeCount(defaults.sample),
        resultCount = rows.size,
        onReset = {
            positionIndex = 0
            if (scope == baseballgm.app.StatScope.LEAGUE) sampleLeague = defaults.sample.ordinal else sampleTeam = defaults.sample.ordinal
        },
    )

    val onSort = { index: Int ->
        if (index == column) {
            descending = !descending
        } else {
            sortColumn = index
            descending = !columnInfo[index].second
        }
    }

    if (view == baseballgm.app.StatView.SABER) {
        val constants = session.leagueConstants()
        Text(
            "리그 기준 · wOBA ${constants.leagueWoba.fixed(3)} · 평균자책 ${constants.leagueEra.fixed(2)} · 승당 득점 ${constants.runsPerWin.fixed(1)}",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.muted,
        )
    }

    SectionCard(null) {
        StatBoardTable(
            session,
            rows.map { it.first },
            columnInfo.mapIndexed { index, (header, _) ->
                val width = if (pitchers) baseballgm.app.StatBoard.pitchingColumns(view)[index].width else baseballgm.app.StatBoard.battingColumns(view)[index].width
                TableColumn(header, width.dp)
            },
            column,
            descending,
            onSort,
            onPlayer,
        ) { row, index -> TableCell(rows[row].second(index)) }
    }
    val minimum = if (pitchers) baseballgm.app.StatBoard.minimumOuts(session, filter) else baseballgm.app.StatBoard.minimumPa(session, filter)
    val sampleText = when {
        minimum <= 0 -> "1군 기록이 있는 ${if (scope == baseballgm.app.StatScope.TEAM) "우리 선수 " else ""}전원"
        pitchers -> "${minimum / OUTS_PER_INNING}이닝 이상"
        else -> "${minimum}타석 이상"
    }
    val note = buildString {
        append(sampleText)
        if (filter.sample == baseballgm.app.SampleMode.QUALIFIED) append(" (규정)")
        if (scope == baseballgm.app.StatScope.LEAGUE && minimum < (if (pitchers) baseballgm.app.StatBoard.pitcherSample(session) else baseballgm.app.StatBoard.batterSample(session))) {
            append(" · 상위 ${baseballgm.app.StatBoard.UNQUALIFIED_LIMIT}명까지")
        }
        if (minimum < (if (pitchers) baseballgm.app.StatBoard.pitcherSample(session) else baseballgm.app.StatBoard.batterSample(session))) {
            append(" · 비율 기록은 표본이 적은 선수를 아래로 보내요")
        }
    }
    Text(note, style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
}

/**
 * 팀 기록 (2026-10-04, 유저 요청 "팀 ERA, 팀 타율"): 열 팀의 시즌 합계를 한 표에. 타격 / 투구, 머리글 정렬.
 * 박스스코어를 쌓을 때 그 경기 팀 쪽에 더한 합계라 트레이드한 선수 기록이 새 팀에 붙지 않는다.
 */
@Composable
private fun TeamStatsBoard(session: GameSession) {
    val tokens = AppTheme.tokens
    var viewIndex by rememberSaveable("teamView") { mutableStateOf(baseballgm.app.TeamView.BATTING.ordinal) }
    val view = baseballgm.app.TeamView.entries[viewIndex]
    val columns = baseballgm.app.StatBoard.teamColumns(view)
    var sortColumn by rememberSaveable("teamSort") { mutableStateOf(baseballgm.app.StatBoard.defaultTeamSort(view)) }
    val column = sortColumn.coerceIn(columns.indices)
    var descending by rememberSaveable("teamDesc") { mutableStateOf(!columns[column].lowerIsBetter) }

    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        baseballgm.app.TeamView.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = view == entry,
                onClick = {
                    if (view != entry) {
                        viewIndex = entry.ordinal
                        sortColumn = baseballgm.app.StatBoard.defaultTeamSort(entry)
                        descending = !baseballgm.app.StatBoard.teamColumns(entry)[sortColumn].lowerIsBetter
                    }
                },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, baseballgm.app.TeamView.entries.size),
            ) { Text(entry.label, maxLines = 1) }
        }
    }
    val rows = androidx.compose.runtime.remember(session.revision, view, column, descending) {
        baseballgm.app.StatBoard.teamTable(session, view, column, descending)
    }
    SectionCard(null) {
        StatTable(
            firstHeader = "구단",
            firstWidth = 132.dp,
            columns = columns.map { TableColumn(it.header, it.width.dp) },
            rowCount = rows.size,
            firstCell = { row ->
                val team = rows[row]
                val own = team.teamId == session.userTeamId
                Text("${row + 1}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(22.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier.size(tokens.sizes.statusIcon / 2).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(baseballgm.app.ui.TeamColors.of(team.teamId)),
                )
                Spacer(Modifier.width(tokens.spacing.xs))
                Text(
                    team.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.text,
                    fontWeight = if (own) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            },
            cell = { row, index -> TableCell(columns[index].format(rows[row])) },
            sortedColumn = column,
            sortDescending = descending,
            onSort = { index ->
                if (index == column) {
                    descending = !descending
                } else {
                    sortColumn = index
                    descending = !columns[index].lowerIsBetter
                }
            },
        )
    }
    // 순위는 표 방향과 상관없이 "좋은 쪽이 1위" (평균자책은 낮을수록 1위)
    val sorted = columns[column]
    val mine = rows.firstOrNull { it.teamId == session.userTeamId }
    if (mine != null) {
        val rank = rows.count { other ->
            if (sorted.lowerIsBetter) sorted.value(other) < sorted.value(mine) else sorted.value(other) > sorted.value(mine)
        } + 1
        Text(
            "우리 팀 ${sorted.header} 리그 ${rank}위" + if (rows.first().estimated) " · 옛 세이브라 지금 소속 선수 기록으로 더한 추정이에요" else "",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.muted,
        )
    }
}

/** 이닝 ↔ 아웃 */
private const val OUTS_PER_INNING = 3

@Composable
private fun StatBoardTable(
    session: GameSession,
    players: List<baseballgm.model.Player>,
    columns: List<TableColumn>,
    sortColumn: Int,
    descending: Boolean,
    onSort: (Int) -> Unit,
    onPlayer: (PlayerId) -> Unit,
    cell: (row: Int, column: Int) -> TableCell,
) {
    val tokens = AppTheme.tokens
    if (players.isEmpty()) {
        Text("아직 기록이 없어요.", style = MaterialTheme.typography.bodySmall, color = AppColors.muted)
        return
    }
    StatTable(
        firstHeader = "선수",
        firstWidth = 132.dp,
        columns = columns,
        rowCount = players.size,
        firstCell = { row ->
            val player = players[row]
            val own = player.teamId == session.userTeamId
            Text("${row + 1}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(28.dp))
            // 구단은 색 점으로 (글자에 구단 색을 쓰지 않는다), 우리 선수는 이름을 굵게
            androidx.compose.foundation.layout.Box(
                Modifier.size(tokens.sizes.statusIcon / 2).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(baseballgm.app.ui.TeamColors.of(player.teamId!!)),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                player.registeredName,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.text,
                fontWeight = if (own) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        },
        cell = cell,
        sortedColumn = sortColumn,
        sortDescending = descending,
        onSort = onSort,
        onRowClick = { row -> onPlayer(players[row].id) },
    )
}

private const val POSTSEASON_SPOTS = 5

/**
 * 역대 (2026-10-01, 진단 3번): 시즌별 결과(우승·정규 1위·MVP·우리 순위) → 통산 순위.
 * 끝난 시즌만 담긴다 — 올 시즌은 스토브리그 때 역사에 적힌다.
 */
@Composable
private fun HistoryTab(session: GameSession, onPlayer: (PlayerId) -> Unit, onAwards: (Int) -> Unit) {
    val tokens = AppTheme.tokens
    val history = session.history()
    if (history.seasons.isEmpty()) {
        baseballgm.app.ui.SecretaryCard("첫 시즌이 끝나면 여기에 역대 우승팀과 수상자, 통산 기록이 쌓여요.")
        return
    }
    SectionCard("역대 시즌") {
        history.seasons.asReversed().forEach { record ->
            val mvp = record.awards.firstOrNull { it.kind == baseballgm.league.AwardKind.MVP }
            val ourRank = record.standings.indexOfFirst { it.teamId == session.userTeamId } + 1
            baseballgm.app.ui.NavRow(
                "${record.season} " + (record.champion?.let { "우승 ${session.league.team(it).name}" } ?: "포스트시즌 없음"),
                listOfNotNull(
                    record.standings.firstOrNull()?.let { "정규 1위 ${session.league.team(it.teamId).nickname}" },
                    mvp?.let { "MVP ${it.name}" },
                    if (ourRank > 0) "우리 ${ourRank}위" else null,
                ).joinToString(" · "),
                { onAwards(record.season) },
            )
        }
    }
    SectionCard("통산 순위") {
        LeaderBlock(session, "홈런", session.allTimeLeaders(ALL_TIME_TOP) { it.bat?.hr ?: 0 }, onPlayer) { "${it.bat?.hr ?: 0}" }
        LeaderBlock(session, "안타", session.allTimeLeaders(ALL_TIME_TOP) { it.bat?.h ?: 0 }, onPlayer) { "${it.bat?.h ?: 0}" }
        LeaderBlock(session, "승리", session.allTimeLeaders(ALL_TIME_TOP) { it.pitch?.w ?: 0 }, onPlayer) { "${it.pitch?.w ?: 0}" }
        LeaderBlock(session, "세이브", session.allTimeLeaders(ALL_TIME_TOP) { it.pitch?.sv ?: 0 }, onPlayer) { "${it.pitch?.sv ?: 0}" }
        Text(
            "끝난 시즌까지의 1군 정규시즌 기록이에요. 은퇴한 선수도 들어 있어요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
            modifier = Modifier.padding(top = tokens.spacing.s),
        )
    }
}

@Composable
private fun LeaderBlock(
    session: GameSession,
    title: String,
    leaders: List<Pair<PlayerId, baseballgm.league.RetiredCareer>>,
    onPlayer: (PlayerId) -> Unit,
    value: (baseballgm.league.RetiredCareer) -> String,
) {
    if (leaders.isEmpty()) return
    val tokens = AppTheme.tokens
    Text(title, style = MaterialTheme.typography.labelMedium, color = tokens.base.textSecondary, modifier = Modifier.padding(top = tokens.spacing.s))
    leaders.forEachIndexed { index, (id, career) ->
        val active = session.inLeague(id)
        val ours = active && session.player(id).teamId == session.userTeamId
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth()
                .then(if (active) Modifier.clickable { onPlayer(id) } else Modifier)
                .padding(vertical = tokens.spacing.xs),
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(20.dp))
            Text(
                career.name + if (active) "" else " (은퇴·떠남)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
                color = if (ours) tokens.base.brand else tokens.base.text,
                modifier = Modifier.weight(1f),
            )
            Text("${career.firstSeason}~${career.lastSeason}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(80.dp))
            Text(value(career), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        }
    }
}

private const val ALL_TIME_TOP = 5

