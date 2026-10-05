package baseballgm.app.screen

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import baseballgm.util.fixed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AccuracyGauge
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.Pill
import baseballgm.app.ui.RatingBar
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.StatRow
import baseballgm.model.Attribute
import baseballgm.model.Batter
import baseballgm.model.Hand
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PlayerId

/**
 * 선수 상세 (docs/15 M10 표).
 *
 * **능력치는 `ScoutingView` 를 거쳐서만 들어온다.** 우리 팀 선수는 정확한 값이, 타 팀 선수는
 * 범위가 나온다. 잠재력은 우리 팀 선수라도 등급으로만 보인다 (docs/02).
 *
 * 2026-10-01 절제 작업: 카드 일곱 장이 한 줄로 이어졌던 것을 머리(이름·종합) + 세그먼트 탭 셋으로 나눴다.
 * - 능력치: 정보 정확도(타 팀) · 능력치 막대 · 스카우트 리포트
 * - 기록: 시즌 기록 · 세이버 지표 (docs/04 "기본 탭과 세이버 탭 분리")
 * - 정보: 기본 · 계약
 */
@Composable
fun PlayerDetailScreen(
    session: GameSession,
    playerId: PlayerId,
    onCompare: (List<PlayerId>) -> Unit = {},
    initialTab: Int = 0,
    /** 비FA 다년계약 협상 테이블로 (2026-10-05) */
    onExtension: (PlayerId) -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val player = session.player(playerId)
    val scouted = session.scout(player)
    val own = session.isOwn(player)
    val tokens = AppTheme.tokens
    var tab by rememberSaveable(playerId.value) { mutableStateOf(initialTab) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        // 머리: 큰 정체(구단 색 원 안 등번호 · 이름 · 상태 아이콘 / 포지션 · 나이·소속) · 종합 (2026-10-02 전 화면 통일)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIdentity(
                session.tagOf(
                    player,
                    caption = listOfNotNull(
                        "${scouted.age}세",
                        player.teamId?.let { session.league.team(it).name } ?: "무소속",
                        // 함께한 시간(유대)은 2026-10-04 부터 만족도 카드의 요인 한 줄이다 (docs/13 유저 결정)
                    ).joinToString(" · "),
                ),
                modifier = Modifier.weight(1f),
                large = true,
            )
            // 종합이 이 화면의 주인공 (큰 숫자, 등급 색). 잠재력은 등급으로 그 아래 회색 (2026-10-03 프로필 개편)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    scouted.overall.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.forRating(scouted.overall.center),
                )
                Text("잠재 ${scouted.potentialLabel}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
            }
        }

        // 비교함 (2026-10-01): 담아 두고 다른 선수와 나란히 본다
        Row(verticalAlignment = Alignment.CenterVertically) {
            val inside = session.inCompare(playerId)
            OutlinedButton(onClick = { session.toggleCompare(playerId) }) {
                Text(if (inside) "비교함에서 빼기" else "비교함에 담기")
            }
            Spacer(Modifier.width(tokens.spacing.s))
            if (session.compareList.size >= 2) {
                TextButton(onClick = { onCompare(session.compareList.toList()) }) {
                    Text("비교 보기 (${session.compareList.size}명)")
                }
            }
        }

        // 탭 넷 (2026-10-03 프로필 개편 — FM 프로필을 캐주얼하게): 요약 / 능력치 / 기록 / 계약
        SecondaryTabRow(selectedTabIndex = tab) {
            listOf("요약", "능력치", "기록", "계약").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }

        when (tab) {
            0 -> SummaryTab(session, player)
            1 -> {
                // 타 팀 선수: 정보 정확도 게이지 + 비서 코멘트 (docs/16 §5)
                if (!own) AccuracyGauge(scouted.precision)
                // "정확/추정 범위" 표시는 위 정보 정확도 카드가 말하므로 카드 머리에서 되풀이하지 않는다
                SectionCard("능력치") {
                    val order = if (player is Pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes
                    order.forEach { attribute ->
                        val range = scouted.ratings.getValue(attribute)
                        RatingBar(attribute.label, range.low, range.high)
                    }
                    if (player is Pitcher) StatRow("최고 구속", "${player.topSpeedKmh}km/h")
                }

                RatingHistoryCard(
                    session.ratingHistory(player),
                    if (player is Pitcher) Attribute.pitcherAttributes else Attribute.batterAttributes,
                )

                SectionCard("스카우트 리포트") {
                    StatRow("잠재력", scouted.potentialLabel)
                    StatRow(
                        "성장 타입",
                        when (scouted.growthTypeGuess) {
                            baseballgm.model.GrowthType.EARLY -> "조기 완성형"
                            baseballgm.model.GrowthType.NORMAL -> "일반형"
                            baseballgm.model.GrowthType.LATE -> "대기만성형"
                        } + if (scouted.precision.growthTypeReliable) "" else " (추정)",
                    )
                    StatRow("내구성", scouted.durabilityComment)
                    // 성향 (docs/13): 우리 선수도 숫자 없이 글자만, 타 팀은 정확도만큼 흐리다
                    StatRow("성향", scouted.personality.archetype + if (scouted.traitsExact) "" else " (추정)")
                    StatRow(
                        "충성심 · 야망 · 프로의식",
                        with(scouted.personality) { "$loyalty · $ambition · $professionalism" },
                    )
                    StatRow("병역", session.militaryLabel(player))
                    session.adaptationLabel(player)?.let { StatRow("KBO 적응", it) }
                    if (session.isOnInternationalDuty(player)) {
                        StatRow("대표팀", "국제대회 차출 중", AppColors.warn)
                    }
                }

                        }

            2 -> {
                RecordTab(session, player)
                CareerCard(session, player)
                        }

            else -> {
                // 계약이 이 탭의 주인공. 폼·피로·부상은 요약 탭으로 옮겼다 (2026-10-03)
                SectionCard("계약") {
                    StatRow("연봉", "${player.contract.salary.fixed(2)}억")
                    player.contract.nextSalary?.let { StatRow("다음 시즌부터", "${it.fixed(2)}억") }
                    StatRow("잔여 계약", "${player.contract.yearsRemaining}년")
                    StatRow("계약 종류", contractTypeLabel(player.contract.type))
                    StatRow(
                        "FA까지",
                        if (player.contract.seasonsToFreeAgency == 0) "이번 시즌 후 취득" else "${player.contract.seasonsToFreeAgency}시즌",
                    )
                    StatRow("누적 시즌", "${player.contract.serviceSeasons}시즌")
                    if (own && !player.isForeign) {
                        val blocked = session.extensionBlockedReason(player.id)
                        Spacer(Modifier.height(tokens.spacing.s))
                        OutlinedButton(onClick = { onExtension(player.id) }, enabled = blocked == null) { Text("다년계약 협상") }
                        blocked?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                        }
                    }
                }

                SectionCard("선수 정보") {
                    StatRow("투타", "${handLabel(player.throwsWith)}투 ${handLabel(player.bats)}타")
                    StatRow(
                        "출신",
                        when (player.origin) {
                            Origin.HIGH_SCHOOL -> "고졸 (${player.debutSeason} 데뷔)"
                            Origin.COLLEGE -> "대졸 (${player.debutSeason} 데뷔)"
                            Origin.FOREIGN -> "외국인 (${player.name})"
                        },
                    )
                    StatRow("군 복무", militaryLabel(player.military))
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.s))
    }
}

/**
 * 요약 탭: 비서의 쉬운 말 평가(강점·약점 칩) → 핵심 기록 셋 → (우리 선수) 컨디션 한 줄.
 * 타 팀 선수는 맨 위에 정보 정확도 게이지 — 범위로 본다는 걸 먼저 알린다 (docs/16 §5).
 */
@Composable
private fun SummaryTab(session: GameSession, player: baseballgm.model.Player) {
    val tokens = AppTheme.tokens
    val summary = baseballgm.app.PlayerSummary.of(session, player)
    val own = session.isOwn(player)
    // 타 팀 선수는 정보 정확도를 같은 비서 카드 안 한 줄로 (카드 두 장이 겹쳐 보이지 않게)
    SecretaryCard(summary.sentence) {
        if (!own) {
            baseballgm.app.ui.AccuracyGaugeRow(session.scout(player).precision)
            Spacer(Modifier.height(tokens.spacing.s))
        }
        if (summary.strength != null || summary.weakness != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
                summary.strength?.let { Pill("강점 ${it.label}", AppColors.good) }
                summary.weakness?.let { Pill("약점 ${it.label}", AppColors.warn) }
            }
        }
    }
    SectionCard(if (summary.fromFutures) "${session.league.season} 2군 추정 성적" else "${session.league.season} 시즌") {
        Row(Modifier.fillMaxWidth()) {
            summary.keyNumbers.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text)
                }
            }
        }
    }
    if (own) {
        SectionCard("컨디션") {
            StatRow("폼", session.formLabel(player))
            StatRow(
                "피로도",
                "${player.condition.fatigue}",
                if (player.condition.fatigue >= CONDITION_FATIGUE_ALERT) AppColors.bad else null,
            )
            player.condition.injury?.let { StatRow("부상", "${it.part} (${it.weeksRemaining}주)", AppColors.bad) }
        }
        MoraleCard(session, player)
    }
}

/**
 * 만족도 카드 (docs/13 "선수 성향과 만족도", 2026-10-04 유저 결정 "선수 상세에 막대로").
 * 한 줄 요약(단계 · 값) → 막대 → 이적 희망·약속 → 요인 목록(큰 것부터) → 성향 한 줄.
 * 함께한 시간(유대)도 요인 한 줄로 들어 있다.
 */
@Composable
private fun MoraleCard(session: GameSession, player: baseballgm.model.Player) {
    val view = session.morale(player) ?: return
    val tokens = AppTheme.tokens
    val color = when {
        view.level.happy -> AppColors.good
        view.level.index == MORALE_NEUTRAL_LEVEL -> tokens.base.brand
        view.level.index == MORALE_NEUTRAL_LEVEL + 1 -> AppColors.warn
        else -> AppColors.bad
    }
    val personality = session.scout(player).personality
    SectionCard("만족도") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(view.level.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text, modifier = Modifier.weight(1f))
            Text("${view.value}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text)
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        baseballgm.app.ui.MeterBar(view.value, color, Modifier.fillMaxWidth())
        Spacer(Modifier.height(tokens.spacing.s))
        if (view.transferListed) {
            Text("이적을 원해요 — 트레이드 시장에 내놓았어요", style = MaterialTheme.typography.bodySmall, color = AppColors.bad)
        }
        view.promise?.let { promise ->
            val left = (promise.deadlineWeek - session.week + 1).coerceAtLeast(0)
            Text(
                "약속: ${promise.deadlineWeek}주차까지 ${promise.kind.label} (${left}주 남음)",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textSecondary,
            )
        }
        view.factors.sortedByDescending { kotlin.math.abs(it.value) }.forEach { factor ->
            StatRow(factor.label, if (factor.value > 0) "+${factor.value}" else "${factor.value}", if (factor.value > 0) AppColors.good else AppColors.bad)
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            "성향 ${personality.archetype} — 충성심 ${personality.loyalty} · 야망 ${personality.ambition} · 프로의식 ${personality.professionalism}",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textMuted,
        )
    }
}

/** 만족도 "보통" 단계 번호 (balance.json morale.levelLabels 순서: 매우 만족, 만족, 보통, 불만, 매우 불만) */
private const val MORALE_NEUTRAL_LEVEL = 2

/** 컨디션 카드에서 피로도를 빨갛게 보이는 기준 (요약 문장의 피로 경고와 같다, 표시 기준) */
private const val CONDITION_FATIGUE_ALERT = 70

/** 상세 기록 격자에 넣을 타격 기록 (2026-10-04: 긴 줄 목록 → 4칸 격자) */
private fun battingItems(line: baseballgm.stats.BattingLine): List<Pair<String, String>> = listOf(
    "타율" to line.battingAverage.fixed(3),
    "출루율" to line.onBasePercentage.fixed(3),
    "장타율" to line.sluggingPercentage.fixed(3),
    "OPS" to line.ops.fixed(3),
    "타석" to "${line.plateAppearances}",
    "타수" to "${line.atBats}",
    "안타" to "${line.hits}",
    "홈런" to "${line.homeRuns}",
    "2루타" to "${line.doubles}",
    "3루타" to "${line.triples}",
    "타점" to "${line.rbi}",
    "득점" to "${line.runs}",
    "볼넷" to "${line.walks}",
    "삼진" to "${line.strikeouts}",
    "도루" to "${line.stolenBases}",
)

private fun pitchingItems(line: baseballgm.stats.PitchingLine): List<Pair<String, String>> = listOf(
    "평균자책" to line.era.fixed(2),
    "WHIP" to line.whip.fixed(2),
    "이닝" to line.inningsText(),
    "경기" to "${line.games}",
    "승" to "${line.wins}",
    "패" to "${line.losses}",
    "세이브" to "${line.saves}",
    "홀드" to "${line.holds}",
    "탈삼진" to "${line.strikeouts}",
    "볼넷" to "${line.walks}",
    "피안타" to "${line.hits}",
    "피홈런" to "${line.homeRuns}",
    "상대 타자" to "${line.battersFaced}",
    "투구수" to "${line.pitches}",
)

private fun handLabel(hand: Hand) = when (hand) {
    Hand.LEFT -> "좌"
    Hand.RIGHT -> "우"
    Hand.SWITCH -> "양"
}

private fun militaryLabel(status: MilitaryStatus) = when (status) {
    is MilitaryStatus.Completed -> "군필"
    is MilitaryStatus.Unfulfilled -> "미필 (${status.deadlineAge}세까지)"
    is MilitaryStatus.Serving -> "복무 중 (${status.returnSeason} 복귀)"
    is MilitaryStatus.Exempt -> "면제"
    is MilitaryStatus.NotRequired -> "해당 없음"
}

/**
 * 기록 탭 (2026-10-04 개편, 유저 요청 "스탯을 한 눈에 보기가 어렵다", "팀 내 스탯 순위").
 *
 * 한 줄 요약 → 핵심 숫자 → 상세 표 (docs/16 §3):
 * 1. 핵심 숫자 넷 + **팀 내 순위**
 * 2. **리그 백분위 막대** — 규정 절반 이상 선수들 사이에서 어디쯤인가 (Baseball Savant 식). 처음 볼 때 한 번 차오른다
 * 3. 상세 기록 격자 (기본 기록 · 세이버 지표 · WAR 구성)
 * 1군 기록이 없으면 2군 추정 성적 격자만.
 */
@Composable
private fun RecordTab(session: GameSession, player: baseballgm.model.Player) {
    val tokens = AppTheme.tokens
    val season = session.league.season
    val sheet = androidx.compose.runtime.remember(session.revision, player.id) { baseballgm.app.PlayerStatSheet.of(session, player) }
    if (sheet == null) {
        SectionCard("$season 시즌") {
            val futures = when (player) {
                is Batter -> session.futuresBatting(player.id).takeIf { it.plateAppearances > 0 }?.let { battingItems(it) }
                is Pitcher -> session.futuresPitching(player.id).takeIf { it.outs > 0 }?.let { pitchingItems(it) }
            }
            if (futures == null) {
                Text("1군·2군 출장 기록이 없어요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
            } else {
                Text("1군 기록이 없어요. 2군 추정 성적이에요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                Spacer(Modifier.height(tokens.spacing.s))
                baseballgm.app.ui.StatGrid(futures)
            }
        }
        return
    }

    SectionCard("$season 시즌") { baseballgm.app.ui.StatTileRow(sheet.tiles) }

    SectionCard(
        "리그 백분위",
        trailing = { Text("규정 절반 이상과 비교", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted) },
    ) {
        val bars = sheet.percentiles
        if (bars == null) {
            Text(sheet.note.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        } else {
            bars.forEachIndexed { index, bar -> baseballgm.app.ui.PercentileBarRow(bar, index) }
        }
    }

    SectionCard("상세 기록") {
        val war = session.war(player)
        when (player) {
            is Batter -> {
                baseballgm.app.ui.StatGrid(battingItems(session.batting(player.id)))
                baseballgm.app.ui.SectionDivider()
                val metrics = session.batterMetrics(player)
                baseballgm.app.ui.StatGrid(
                    listOf(
                        "wRC+" to metrics.wrcPlus.fixed(0),
                        "wOBA" to metrics.woba.fixed(3),
                        "ISO" to metrics.iso.fixed(3),
                        "BABIP" to metrics.babip.fixed(3),
                        "볼넷%" to "${(metrics.walkRate * 100).fixed(1)}%",
                        "삼진%" to "${(metrics.strikeoutRate * 100).fixed(1)}%",
                        "땅볼%" to "${(metrics.groundBallRate * 100).fixed(0)}%",
                        "뜬공%" to "${(metrics.flyBallRate * 100).fixed(0)}%",
                    ),
                )
            }
            is Pitcher -> {
                baseballgm.app.ui.StatGrid(pitchingItems(session.pitching(player.id)))
                baseballgm.app.ui.SectionDivider()
                val metrics = session.pitcherMetrics(player)
                baseballgm.app.ui.StatGrid(
                    listOf(
                        "ERA+" to metrics.eraPlus.fixed(0),
                        "FIP" to metrics.fip.fixed(2),
                        "K/9" to metrics.strikeoutsPer9.fixed(1),
                        "BB/9" to metrics.walksPer9.fixed(1),
                        "HR/9" to metrics.homeRunsPer9.fixed(1),
                        "K-BB%" to "${(metrics.strikeoutMinusWalkRate * 100).fixed(1)}%",
                        "BABIP" to metrics.babip.fixed(3),
                        "LOB%" to "${(metrics.leftOnBaseRate * 100).fixed(0)}%",
                    ),
                )
            }
        }
        baseballgm.app.ui.SectionDivider()
        val detail = when (player) {
            is Batter -> "타격 ${war.batting.fixed(1)} · 수비 ${war.fielding.fixed(1)} · 포지션 ${war.positional.fixed(1)} · 대체 ${war.replacement.fixed(1)}"
            is Pitcher -> "투구 ${war.pitching.fixed(1)} · 대체 ${war.replacement.fixed(1)}"
        }
        StatRow("WAR", war.war.fixed(1))
        Text("$detail (단위: 득점)", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
    }
}

/**
 * 통산 기록 (2026-10-01, 진단 3번): 시즌별 1군 기록 + 통산 줄 + 수상 내역.
 * 지난 시즌은 리그 역사에서, 올 시즌은 지금까지 기록으로 채운다. 2군 추정 성적은 넣지 않는다 (현실 통산 기록과 같게).
 */
@Composable
private fun CareerCard(session: GameSession, player: baseballgm.model.Player) {
    val lines = session.careerLines(player)
    val total = session.careerTotal(player)
    val awards = session.awardsOf(player)
    if (lines.isEmpty() && total == null && awards.isEmpty()) return
    val tokens = AppTheme.tokens
    SectionCard("통산 기록") {
        val earlier = session.history().retired[player.id]
        if (earlier != null) {
            Text(
                "${earlier.firstSeason}~${earlier.lastSeason} 이전 소속 시절 기록이 통산에 함께 들어 있어요.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.base.textMuted,
            )
        }
        val rows = lines.map { "${it.season}" to it } + listOfNotNull(total?.let { "통산" to null })
        val pitcher = player is Pitcher
        val columns = if (pitcher) {
            listOf("팀", "경기", "이닝", "승", "패", "세", "ERA", "삼진", "WAR").map { baseballgm.app.ui.TableColumn(it) }
        } else {
            listOf("팀", "타석", "타율", "홈런", "타점", "도루", "OPS", "WAR").map { baseballgm.app.ui.TableColumn(it) }
        }
        baseballgm.app.ui.StatTable(
            firstHeader = "시즌",
            firstWidth = 48.dp,
            columns = columns,
            rowCount = rows.size,
            firstCell = { row ->
                Text(rows[row].first, style = MaterialTheme.typography.bodySmall, fontWeight = if (rows[row].second == null) FontWeight.Bold else FontWeight.Normal)
            },
            cell = { row, column ->
                val line = rows[row].second
                val bold = line == null
                val team = line?.team?.let { session.league.team(it).nickname.take(TEAM_CHARS) } ?: ""
                val war = line?.war ?: total?.war ?: 0.0
                val text = if (pitcher) {
                    val p = line?.pitch ?: if (line == null) total?.pitch else null
                    when (column) {
                        0 -> team
                        1 -> p?.g?.toString() ?: "—"
                        2 -> p?.innings?.fixed(1) ?: "—"
                        3 -> p?.w?.toString() ?: "—"
                        4 -> p?.l?.toString() ?: "—"
                        5 -> p?.sv?.toString() ?: "—"
                        6 -> p?.era?.fixed(2) ?: "—"
                        7 -> p?.so?.toString() ?: "—"
                        else -> war.fixed(1)
                    }
                } else {
                    val b = line?.bat ?: if (line == null) total?.bat else null
                    when (column) {
                        0 -> team
                        1 -> b?.pa?.toString() ?: "—"
                        2 -> b?.avg?.fixed(3) ?: "—"
                        3 -> b?.hr?.toString() ?: "—"
                        4 -> b?.rbi?.toString() ?: "—"
                        5 -> b?.sb?.toString() ?: "—"
                        6 -> b?.ops?.fixed(3) ?: "—"
                        else -> war.fixed(1)
                    }
                }
                baseballgm.app.ui.TableCell(text, bold = bold)
            },
        )
        if (awards.isNotEmpty()) {
            Text(
                "수상",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.base.textSecondary,
                modifier = Modifier.padding(top = tokens.spacing.s),
            )
            awards.forEach { (season, entry) ->
                Text(
                    "$season ${entry.kind.label}" + (entry.position?.let { " ($it)" } ?: "") + " · ${entry.value}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val TEAM_CHARS = 3

private fun contractTypeLabel(type: baseballgm.model.ContractType): String = when (type) {
    baseballgm.model.ContractType.ROOKIE -> "신인 계약"
    baseballgm.model.ContractType.STANDARD -> "일반 계약"
    baseballgm.model.ContractType.FREE_AGENT -> "FA 계약"
    baseballgm.model.ContractType.FOREIGN -> "외국인 계약"
    baseballgm.model.ContractType.MULTI_YEAR -> "비FA 다년계약"
}
