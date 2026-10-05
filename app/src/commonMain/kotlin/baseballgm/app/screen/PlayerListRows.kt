package baseballgm.app.screen

import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.ListCell
import baseballgm.app.ui.MAX_STATUS_BADGES
import baseballgm.app.ui.PlayerListRow
import baseballgm.app.ui.PlayerListSection
import baseballgm.app.ui.PlayerStatus
import baseballgm.app.ui.PlayerTag
import baseballgm.app.ui.StatusBadge
import baseballgm.app.ui.StatusTone
import baseballgm.app.ui.TableColumn
import baseballgm.market.DraftProspect
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.scouting.ScoutReport
import baseballgm.scouting.ScoutedPlayer
import baseballgm.util.fixed

/*
 * 선수 → 화면 표시 데이터 (2026-10-02, 레퍼런스 리팩토링 · 전 화면 통일).
 *
 * 능력치는 **전부 [GameSession.scout] (= 엔진 ScoutingView) 나 스카우트 리포트를 거친다.** 우리 선수면 범위 폭이 0 이라
 * 정확한 숫자가 나오고, 타 팀이면 정확도만큼 넓은 범위가 나온다. `session.overall()` 처럼 진짜 값을 주는
 * 함수는 여기서 쓰지 않는다 (불변 원칙 4) — 정렬도 스카우트 범위의 중심으로 한다.
 *
 * 폼·피로·휴식은 우리 선수만 보인다. 부상·군 복무는 공개 정보라 타 팀도 보인다. 등번호도 공개 정보다.
 */

private val RATING = 60.dp
private val STAT = 48.dp
private val WIDE = 56.dp

// ---------- 정체 ----------

/** 프로 선수 정체. [caption] 을 안 주면 나이 */
internal fun GameSession.tagOf(player: Player, caption: String? = null): PlayerTag = tagOf(player, scout(player), caption)

private fun GameSession.tagOf(player: Player, scouted: ScoutedPlayer, caption: String? = null) = PlayerTag(
    id = player.id,
    name = scouted.name,
    number = player.uniformNumber.takeIf { it > 0 },
    teamId = player.teamId,
    position = scouted.positionLabel,
    caption = caption ?: "${scouted.age}세",
    badges = badgesOf(this, player),
    rookie = isRookie(player),
    coreProspect = isCoreProspect(player),
)

/** 드래프트 후보 정체. 아마추어라 등번호·소속이 없다 */
internal fun GameSession.tagOf(prospect: DraftProspect, report: ScoutReport): PlayerTag = PlayerTag(
    id = prospect.id,
    name = report.scouted.name,
    number = null,
    teamId = null,
    position = report.scouted.positionLabel,
    caption = "${report.scouted.age}세 · ${prospect.schoolTypeLabel}",
    badges = buildList {
        if (isFocused(prospect)) {
            val done = isFullyScouted(prospect)
            val how = if (isAutoFocused(prospect)) "자동 관찰" else "관찰"
            add(StatusBadge(PlayerStatus.WATCHING, if (done) "$how 완료" else "$how ${focusWeeks(prospect)}주"))
        }
        if (report.knownProspect) add(StatusBadge(PlayerStatus.KNOWN_PROSPECT, "주목 유망주"))
    },
)

/**
 * 상태 배지. 부상 → 군 복무 → 휴식 → 폼 순으로 급한 것부터, 최대 2개.
 * 폼은 우리 선수만 (남의 컨디션은 모른다). 보통 폼은 배지를 달지 않는다 — 아무 표시 없음이 "평소대로"다.
 */
private fun badgesOf(session: GameSession, player: Player): List<StatusBadge> = buildList {
    player.condition.injury?.let { add(StatusBadge(PlayerStatus.INJURED, "부상 ${it.part} ${it.weeksRemaining}주")) }
    if (!player.military.isAvailable) add(StatusBadge(PlayerStatus.MILITARY, "군 복무"))
    if (session.isOwn(player)) {
        if (session.isResting(player)) add(StatusBadge(PlayerStatus.RESTING, "이번 주 휴식"))
        // 만족도 (docs/13): 불만 이하이거나 이적을 원하면 상태 아이콘
        session.morale(player)?.let { morale ->
            when {
                morale.transferListed -> add(StatusBadge(PlayerStatus.UNHAPPY, "이적 희망"))
                morale.level.unhappy -> add(StatusBadge(PlayerStatus.UNHAPPY, "만족도 ${morale.level.label}"))
            }
        }
        val form = player.condition.form
        when {
            form >= FORM_UP -> add(StatusBadge(PlayerStatus.FORM_UP, "폼 ${session.formLabel(player)}"))
            form < FORM_DOWN -> add(StatusBadge(PlayerStatus.FORM_DOWN, "폼 ${session.formLabel(player)}"))
        }
    }
}.take(MAX_STATUS_BADGES)

// ---------- 프로 선수 표 ----------

/** 기본 열 뒤에 붙이는 화면별 열 (FA 희망 조건, 트레이드 "비교+" 등) */
internal class ExtraColumn(val column: TableColumn, val cell: (Player) -> ListCell)

/**
 * 타자·투수 두 묶음. 빈 묶음은 뺀다.
 * 열: 종합 → 능력치 → (우리 팀만) 피로 → [stats] 기록 셋 → [contract] 연봉·계약 → [extras]
 */
internal fun playerListSections(
    session: GameSession,
    players: List<Player>,
    level: RosterLevel,
    selected: Set<PlayerId> = emptySet(),
    stats: Boolean = true,
    contract: Boolean = true,
    extras: List<ExtraColumn> = emptyList(),
    /** false 면 간단히: 종합 + 핵심 기록 둘만 (2026-10-03 선수단 "간단히/자세히") */
    detailed: Boolean = true,
): List<PlayerListSection> {
    val scouted = players.associateWith { session.scout(it) }
    val ordered = players.sortedByDescending { scouted.getValue(it).overall.center }
    fun section(title: String, list: List<Player>, pitcher: Boolean): PlayerListSection {
        if (!detailed) return casualSection(session, title, list, pitcher, level, selected, scouted)
        val own = list.all { session.isOwn(it) }
        val attributes = if (pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes
        val columns = buildList {
            add(TableColumn("종합", RATING))
            attributes.forEach { add(TableColumn(shortLabel(it), RATING)) }
            if (own) add(TableColumn("피로", STAT))
            if (stats) {
                if (pitcher) {
                    add(TableColumn("ERA", STAT)); add(TableColumn("이닝", STAT)); add(TableColumn("승패", WIDE))
                } else {
                    add(TableColumn("타율", STAT)); add(TableColumn("홈런", STAT)); add(TableColumn("OPS", STAT))
                }
            }
            if (contract) {
                add(TableColumn("연봉", WIDE)); add(TableColumn("계약", STAT))
            }
            extras.forEach { add(it.column) }
        }
        val rows = list.map { player ->
            val view = scouted.getValue(player)
            val cells = buildList {
                add(ListCell.Rating(view.overall, emphasized = true))
                attributes.forEach { add(view.ratings[it]?.let { range -> ListCell.Rating(range) } ?: ListCell.Value(DASH)) }
                if (own) add(fatigueCell(player))
                if (stats) addAll(statCells(session, player, level))
                if (contract) {
                    add(ListCell.Value("${player.contract.salary.fixed(1)}억"))
                    add(ListCell.Value("${player.contract.yearsRemaining}년"))
                }
                extras.forEach { add(it.cell(player)) }
            }
            PlayerListRow(session.tagOf(player, view), cells, selected = player.id in selected)
        }
        return PlayerListSection(title, columns, rows)
    }
    val batters = ordered.filterIsInstance<Batter>()
    val pitchers = ordered.filterIsInstance<Pitcher>()
    return listOfNotNull(
        batters.takeIf { it.isNotEmpty() }?.let { section("타자", it, pitcher = false) },
        pitchers.takeIf { it.isNotEmpty() }?.let { section("투수", it, pitcher = true) },
    )
}

/** 간단히: 종합 + 핵심 기록 둘 (타자 타율·홈런 / 투수 평균자책·승패) */
private fun casualSection(
    session: GameSession,
    title: String,
    list: List<Player>,
    pitcher: Boolean,
    level: RosterLevel,
    selected: Set<PlayerId>,
    scouted: Map<Player, ScoutedPlayer>,
): PlayerListSection {
    val columns = listOf(TableColumn("종합", RATING)) +
        if (pitcher) listOf(TableColumn("ERA", STAT), TableColumn("승패", WIDE)) else listOf(TableColumn("타율", STAT), TableColumn("홈런", STAT))
    val rows = list.map { player ->
        val view = scouted.getValue(player)
        val stats = statCells(session, player, level)
        val cells = listOf(ListCell.Rating(view.overall, emphasized = true)) +
            if (pitcher) listOf(stats[0], stats[2]) else listOf(stats[0], stats[1])
        PlayerListRow(session.tagOf(player, view), cells, selected = player.id in selected)
    }
    return PlayerListSection(title, columns, rows)
}

/** 1군은 시즌 기록, 2군은 추정 성적 (docs/07) */
private fun statCells(session: GameSession, player: Player, level: RosterLevel): List<ListCell> = when (player) {
    is Batter -> {
        val line = if (level == RosterLevel.FIRST_TEAM) session.batting(player.id) else session.futuresBatting(player.id)
        val record = line.plateAppearances > 0
        listOf(
            ListCell.Value(if (record) line.battingAverage.fixed(3) else DASH),
            ListCell.Value(if (record) "${line.homeRuns}" else DASH),
            ListCell.Value(if (record) line.ops.fixed(3) else DASH),
        )
    }

    is Pitcher -> {
        val line = if (level == RosterLevel.FIRST_TEAM) session.pitching(player.id) else session.futuresPitching(player.id)
        val record = line.outs > 0
        listOf(
            ListCell.Value(if (record) line.era.fixed(2) else DASH),
            ListCell.Value(if (record) line.inningsText() else DASH),
            ListCell.Value(if (record) "${line.wins}-${line.losses}" else DASH),
        )
    }
}

/** 열 머리는 두 글자로 ("선구안" → "선구", "땅볼 유도" → "땅볼") */
private fun shortLabel(attribute: Attribute): String = attribute.label.replace(" ", "").take(2)

private fun fatigueCell(player: Player): ListCell {
    val fatigue = player.condition.fatigue
    return ListCell.Value(
        "$fatigue",
        tone = when {
            fatigue >= FATIGUE_BAD -> StatusTone.BAD
            fatigue >= FATIGUE_WARN -> StatusTone.WARN
            else -> null
        },
    )
}

// ---------- 드래프트 후보 표 ----------

/**
 * 드래프트 후보 한 묶음. 아마추어는 기록이 없어서 열이 짧다: 종합(범위) · 잠재(등급) · 정확도 · 학교.
 * 능력치는 스카우트 리포트(투자 단계·관찰 주수만큼의 범위)만 쓴다.
 */
internal fun prospectSection(
    session: GameSession,
    title: String,
    prospects: List<Pair<DraftProspect, ScoutReport>>,
): PlayerListSection = PlayerListSection(
    title,
    listOf(
        TableColumn("종합", RATING),
        TableColumn("잠재", STAT),
        TableColumn("정확도", WIDE),
        TableColumn("학교", WIDE + STAT),
    ),
    prospects.map { (prospect, report) ->
        PlayerListRow(
            session.tagOf(prospect, report),
            listOf(
                ListCell.Rating(report.scouted.overall, emphasized = true),
                ListCell.Value(report.scouted.potentialLabel, bold = true),
                ListCell.Value(report.accuracyLabel),
                ListCell.Value(prospect.school),
            ),
        )
    },
)

private const val DASH = "—"

// 예전 로스터 줄(RosterRow)의 표시 기준을 그대로 옮겼다 — 화면 표시 구간이지 게임 밸런스 수치가 아니다
private const val FORM_UP = 60
private const val FORM_DOWN = 45
private const val FATIGUE_BAD = 70
private const val FATIGUE_WARN = 45
