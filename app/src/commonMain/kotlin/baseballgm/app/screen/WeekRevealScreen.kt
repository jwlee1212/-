package baseballgm.app.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import baseballgm.app.GameOutcome
import baseballgm.app.GameSession
import baseballgm.app.RevealGame
import baseballgm.app.RevealResult
import baseballgm.app.WeekReveal
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.SecretaryAvatar
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.model.PlayerId
import kotlinx.coroutines.delay

/**
 * 한 주 결과 공개 (2026-10-02, 재미 개선 1번 "결과 공개 연출", 같은 날 주중 개입과 묶음).
 *
 * **이 화면이 한 주를 진행한다.** 진행 버튼을 누르면 이 화면이 먼저 열리고, 여기서 `advanceWeek` 를 부른다.
 * 엔진은 돌발 이벤트가 생기면 그 경기 뒤에서 멈추므로 흐름은 이렇다:
 * 1. 진행 → 이벤트 전까지의 경기가 생긴다 → 카드를 한 장씩 공개
 * 2. 공개가 멈춘 곳까지 따라오면 **돌발 이벤트 창** (공개는 거기서 멈춘다)
 * 3. 답하면 답한 내용이 그 경기 카드 뒤에 한 줄로 남고, 다시 진행 → 남은 경기 공개 재개
 * 4. 주가 끝나면 전적(0부터 올라감) · 순위 변화 · 이번 주 영웅
 *
 * 카드 연출: 흐리게 깔린 카드(요일·상대, 치른 경기면 선발 매치업) → 가로축으로 뒤집히며 열림 → 라인 스코어가 한 이닝씩
 * 채워지고 점수가 따라 오름 → 승·패 도장 → 경기 성격 칩 · 결승 장면 · 승패 투수. 접전은 천천히, 마지막 이닝 앞에서 멈칫.
 * 시간은 전부 [baseballgm.app.ui.Motion] 토큰, 언제든 "건너뛰기". 경기를 다시 돌리지 않는다 (불변 원칙 5).
 *
 * @param animate false 면 연출 없이 끝 상태 (미리보기·테스트)
 * @param initialShown 처음부터 이만큼 열린 채로 시작 (화면에 다시 들어왔을 때 이어 보기, 미리보기의 "연출 중" 모습)
 * @param drive true 면 이 화면이 주를 진행한다. 미리보기는 false
 */
@Composable
fun WeekRevealScreen(
    session: GameSession,
    week: Int,
    rankBefore: Int?,
    animate: Boolean = true,
    initialShown: Int = 0,
    drive: Boolean = true,
    onShown: (Int) -> Unit = {},
    onWatch: (index: Int) -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit,
    onPlayer: (PlayerId) -> Unit = {},
    onCompare: (List<PlayerId>) -> Unit = {},
) {
    val tokens = AppTheme.tokens
    val motion = tokens.motion
    val revision = session.revision
    val reveal = remember(revision, week) { WeekReveal.of(session, week, rankBefore) }
    val games = reveal.games
    val played = reveal.played
    var skipped by remember { mutableStateOf(!animate) }
    var shown by remember { mutableIntStateOf(if (animate) initialShown.coerceAtMost(played) else played) }
    val caughtUp = shown >= played
    val incident = session.pendingIncident
    var laterIncident by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    // ① 주 진행: 기다리는 돌발 이벤트가 없고 주가 안 끝났으면 다음 멈춤까지 진행한다 (이벤트에 답하면 여기서 재개)
    LaunchedEffect(revision, drive) {
        if (!drive || incident != null || reveal.complete || session.busy || session.seasonOver || session.week != week) return@LaunchedEffect
        session.advanceWeek()
    }

    // ② 공개: 치른 경기까지 한 장씩. 경기가 더 생기면(이벤트에 답한 뒤) 이어서 연다
    LaunchedEffect(played, skipped) {
        if (skipped) {
            shown = played
            onShown(shown)
            return@LaunchedEffect
        }
        while (shown < played) {
            delay(motion.cardGap.toLong())
            shown += 1
            onShown(shown)
            list.animateScrollToItem(itemIndexOfGame(reveal, shown - 1))
            delay(cardMillis(games[shown - 1].result!!, motion).toLong())
        }
    }
    LaunchedEffect(caughtUp, reveal.complete) {
        if (caughtUp && reveal.complete && animate) list.animateScrollToItem(itemCount(reveal) - 1)
    }

    val finished = caughtUp && reveal.complete
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        state = list,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        contentPadding = PaddingValues(vertical = tokens.spacing.m),
    ) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecretaryAvatar(size = tokens.sizes.numberBadge)
                Spacer(Modifier.width(tokens.spacing.s))
                Text(
                    when {
                        finished -> "${week}주차 결과 정리했어요."
                        caughtUp && incident != null -> "잠깐만요, 일이 하나 생겼어요."
                        else -> "${week}주차 결과 나왔어요. 하나씩 볼까요?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.base.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (!skipped && !finished) TextButton(onClick = { skipped = true }) { Text("건너뛰기") }
            }
        }
        // 주 시작 이벤트(첫 경기 전)
        reveal.incidents.filter { it.afterGame == -1 }.forEachIndexed { i, marker ->
            item(key = "incident-start-$i") { IncidentMarker(marker.text, Modifier.animateItem()) }
        }
        games.forEachIndexed { index, game ->
            item(key = "game-${game.index}") {
                GameRevealCard(
                    session = session,
                    game = game,
                    revealed = index < shown,
                    // 지나간 카드는 끝 상태로 (목록이 화면 밖 카드를 지웠다 다시 만들어도 연출이 되풀이되지 않게)
                    instant = skipped || index < shown - 1 || !animate,
                    onClick = { onWatch(game.index) },
                )
            }
            // 이 경기 뒤에 답한 이벤트는 카드가 열린 뒤에 보인다
            if (index < shown) {
                reveal.incidents.filter { it.afterGame == index }.forEachIndexed { i, marker ->
                    item(key = "incident-$index-$i") { IncidentMarker(marker.text, Modifier.animateItem()) }
                }
            }
            // 지금 기다리는 이벤트: 공개가 여기까지 왔고 다음 경기 전이면 이 자리에서 답한다
            if (incident != null && caughtUp && index == shown - 1) {
                item(key = "pending-${incident.id}") {
                    PendingIncident(incident.headline, Modifier.animateItem()) { laterIncident = null }
                }
            }
        }
        if (incident != null && caughtUp && shown == 0) {
            item(key = "pending-${incident.id}") { PendingIncident(incident.headline, Modifier.animateItem()) { laterIncident = null } }
        }
        if (games.isEmpty()) {
            item(key = "empty") { Text("이번 주는 경기가 없었어요.", style = MaterialTheme.typography.bodyMedium, color = tokens.base.textMuted) }
        }
        item(key = "summary") {
            AnimatedVisibility(
                visible = finished,
                enter = if (skipped || !animate) fadeIn(tween(0)) else fadeIn(tween(motion.settle)) + slideInVertically(tween(motion.settle)) { it / 4 },
            ) {
                WeekSummaryCard(session, reveal, instant = skipped || !animate, onReport = onReport, onClose = onClose)
            }
        }
    }

    // ③ 공개가 멈춘 곳까지 따라오면 이벤트 창. "나중에"를 누르면 창만 접고 목록 안 카드로 남는다
    if (drive && incident != null && caughtUp && laterIncident != incident.id) {
        IncidentDialog(
            session = session,
            incident = incident,
            onLater = { laterIncident = incident.id },
            onPlayer = {
                laterIncident = incident.id
                onPlayer(it)
            },
            onCompare = {
                laterIncident = incident.id
                onCompare(it)
            },
        )
    }
}

/** LazyColumn 안에서 [gameIndex] 번째 경기 카드의 위치 (머리·이벤트 줄 포함) */
private fun itemIndexOfGame(reveal: WeekReveal, gameIndex: Int): Int =
    1 + reveal.incidents.count { it.afterGame < gameIndex } + gameIndex

private fun itemCount(reveal: WeekReveal): Int = 1 + reveal.incidents.size + reveal.games.size + 1

/** 카드 한 장 연출 시간: 뒤집기 + 이닝 채우기 (+ 접전이면 멈칫) + 도장 */
private fun cardMillis(result: RevealResult, motion: baseballgm.app.ui.Motion): Int {
    val innings = maxOf(result.ourInnings.size, result.theirInnings.size)
    val step = if (result.tense) motion.tenseInningStep else motion.inningStep
    return motion.flip + innings * step + (if (result.tense) motion.tenseHold else 0) + motion.settle
}

/** 답한 돌발 이벤트 한 줄: 비서 아바타 + "헤드라인 → 고른 답" (카드가 아니라 글줄 — 경기 카드 사이의 쉼표) */
@Composable
private fun IncidentMarker(text: String, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    // 목록에 새로 끼어들 때 자리를 밀며 들어온다 (animateItem, 재미 개선 5번)
    Row(modifier.padding(horizontal = tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
        SecretaryAvatar(size = tokens.sizes.statusBadge)
        Spacer(Modifier.width(tokens.spacing.s))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary)
    }
}

/** 지금 답을 기다리는 이벤트. 창을 접었으면 여기서 다시 연다 */
@Composable
private fun PendingIncident(headline: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    SecretaryCard(message = "$headline — 답해 주시면 남은 경기를 이어서 볼게요.", modifier = modifier) {
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("답하기") }
    }
}

/**
 * 경기 카드. 닫혀 있을 땐 흐린 요일·상대(+ 치른 경기면 선발 매치업), 열리면 뒤집힌 뒤 라인 스코어 → 도장 → 성격·결승 장면·승패 투수.
 * 아직 안 치른 경기(주중에 멈췄을 때)는 "경기 전". 카드 안에 카드를 넣지 않는다 — 라인 스코어는 글자 표.
 */
@Composable
private fun GameRevealCard(
    session: GameSession,
    game: RevealGame,
    revealed: Boolean,
    instant: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AppTheme.tokens
    val motion = tokens.motion
    val result = game.result
    val open = revealed && result != null
    val totalInnings = result?.let { maxOf(it.ourInnings.size, it.theirInnings.size) } ?: 0
    // 뒤집기(가로축 회전) · 채워진 이닝 수 · 도장 크기
    val flip = remember { Animatable(if (instant && open) 0f else FLIP_START) }
    var innings by remember { mutableIntStateOf(if (instant && open) totalInnings else 0) }
    val stamp = remember { Animatable(if (instant && open) 1f else 0f) }
    val done = open && innings >= totalInnings && stamp.value > 0f

    LaunchedEffect(open, instant) {
        if (!open || result == null) return@LaunchedEffect
        if (instant) {
            flip.snapTo(0f)
            innings = totalInnings
            stamp.snapTo(1f)
            return@LaunchedEffect
        }
        flip.animateTo(0f, tween(motion.flip))
        val step = if (result.tense) motion.tenseInningStep else motion.inningStep
        while (innings < totalInnings) {
            // 접전: 마지막 이닝 앞에서 멈칫
            if (result.tense && innings == totalInnings - 1) delay(motion.tenseHold.toLong())
            delay(step.toLong())
            innings += 1
        }
        stamp.snapTo(STAMP_START)
        stamp.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    val opponent = session.league.team(game.opponent)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = if (open) flip.value else 0f
                cameraDistance = CAMERA_DISTANCE * density
                alpha = if (open) 1f else CLOSED_ALPHA
            }
            .clip(RoundedCornerShape(tokens.radii.card))
            .background(tokens.base.card)
            .then(if (done) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(tokens.spacing.l)
            .semantics {
                contentDescription = if (done && result != null) {
                    "${game.dayLabel ?: ""} ${opponent.name} ${result.ourScore} 대 ${result.theirScore} ${result.outcome.label}"
                } else {
                    "${game.dayLabel ?: ""} ${opponent.name} 경기, 아직 결과 전"
                }
            },
    ) {
        // 머리: 요일 · 홈/원정 상대 · 결과 도장
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(game.dayLabel ?: "", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(tokens.spacing.xl))
            Text(
                "${if (game.home) "홈" else "원정"} ${opponent.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                // 카드가 Surface 가 아니라서 기본 글자색이 테마를 안 따른다 → 토큰으로 직접
                color = tokens.base.text,
                modifier = Modifier.weight(1f),
            )
            if (open && stamp.value > 0f) {
                Pill(
                    result!!.outcome.label,
                    outcomeColor(result.outcome),
                    Modifier.graphicsLayer {
                        scaleX = stamp.value
                        scaleY = stamp.value
                        alpha = (2f - stamp.value).coerceIn(0f, 1f)
                    },
                )
            } else if (!open) {
                Text("경기 전", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
            }
        }
        // 선발 매치업: 치른 경기는 닫혀 있을 때부터 보인다 — 뒤집기 전에 "오늘은 누가 던지나"
        if (game.ourStarter != null && game.theirStarter != null) {
            Text(
                "선발 ${game.ourStarter} vs ${game.theirStarter}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textSecondary,
                modifier = Modifier.padding(start = tokens.spacing.xl),
            )
        }
        if (open) {
            Spacer(Modifier.height(tokens.spacing.s))
            LineScore(session, game, result!!, innings)
        }
        AnimatedVisibility(visible = done, enter = if (instant) fadeIn(tween(0)) else fadeIn(tween(motion.settle))) {
            Column {
                if (result!!.tags.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                        result.tags.forEach { Pill(it, tagColor(it)) }
                    }
                }
                result.keyPlay?.let {
                    Spacer(Modifier.height(tokens.spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary)
                }
                result.decisions?.let {
                    Spacer(Modifier.height(tokens.spacing.xs))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                }
            }
        }
    }
}

/**
 * 라인 스코어: 우리 줄(굵게) / 상대 줄. [shown] 이닝까지만 채우고 나머지는 빈칸, 오른쪽 점수는 채운 이닝의 합이라
 * 이닝이 열릴 때마다 점수가 따라 오른다. 이닝 숫자는 고정폭 숫자로 세로 정렬된다.
 */
@Composable
private fun LineScore(session: GameSession, game: RevealGame, result: RevealResult, shown: Int) {
    val tokens = AppTheme.tokens
    val innings = maxOf(result.ourInnings.size, result.theirInnings.size, REGULATION)
    @Composable
    fun line(label: String, runs: List<Int>, ours: Boolean) {
        val weight = if (ours) FontWeight.Bold else FontWeight.Normal
        val color = if (ours) tokens.base.text else tokens.base.textSecondary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = weight, color = color, maxLines = 1, modifier = Modifier.width(INNING_LABEL))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                repeat(innings) { i ->
                    val value = if (i < shown) runs.getOrNull(i)?.toString() ?: "x" else ""
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (value != "0" && value != "x" && value.isNotEmpty()) color else tokens.base.textMuted,
                        fontWeight = weight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(INNING_CELL),
                    )
                }
            }
            Text(
                "${runs.take(shown).sum()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = weight,
                color = color,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SCORE_CELL),
            )
        }
    }
    line(session.userTeam.nickname, result.ourInnings, ours = true)
    line(session.league.team(game.opponent).nickname, result.theirInnings, ours = false)
}

/**
 * 주간 정리: 전적(0부터 올라감 — 이 화면의 주인공) → 순위 변화(화살표 아이콘 + 글자) → 이번 주 영웅 → 다음 행동.
 */
@Composable
private fun WeekSummaryCard(
    session: GameSession,
    reveal: WeekReveal,
    instant: Boolean,
    onReport: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = AppTheme.tokens
    var started by remember { mutableStateOf(instant) }
    LaunchedEffect(Unit) { started = true }
    val countSpec = tween<Int>(if (instant) 0 else tokens.motion.countUp)
    val wins by animateIntAsState(if (started) reveal.wins else 0, countSpec)
    val losses by animateIntAsState(if (started) reveal.losses else 0, countSpec)

    SectionCard(null) {
        Text(
            "${wins}승 ${losses}패" + if (reveal.ties > 0) " ${reveal.ties}무" else "",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "이번 주 ${reveal.wins}승 ${reveal.losses}패" },
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        RankChange(reveal)
        // 결정 성적표 한 줄 (재미 개선 2번): 지난 결정이 통했는지
        session.decisionReviewer.headline(session)?.let {
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary)
        }
        reveal.hero?.let { hero ->
            SectionDivider()
            Text("이번 주 영웅", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
            PlayerLine(session.tagOf(session.player(hero.playerId), caption = hero.line), onClick = null)
        }
        Spacer(Modifier.height(tokens.spacing.m))
        OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text("주간 브리핑 보기") }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("홈으로") }
    }
}

/** 순위 변화: 오르면 위 화살표(좋음), 내리면 아래 화살표(나쁨), 그대로면 가로줄. 색만이 아니라 아이콘 모양·글자로도 */
@Composable
private fun RankChange(reveal: WeekReveal) {
    val tokens = AppTheme.tokens
    val before = reveal.rankBefore
    val after = reveal.rankAfter
    val (icon, color, text) = when {
        before == null -> Triple(Icons.Filled.Remove, tokens.base.textMuted, "지금 ${after}위")
        after < before -> Triple(Icons.Filled.ArrowUpward, AppColors.good, "${before}위 → ${after}위, ${before - after}계단 올라섰어요")
        after > before -> Triple(Icons.Filled.ArrowDownward, AppColors.bad, "${before}위 → ${after}위, ${after - before}계단 내려갔어요")
        else -> Triple(Icons.Filled.Remove, tokens.base.textMuted, "${after}위 그대로예요")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.width(tokens.sizes.statusBadge).height(tokens.sizes.statusBadge))
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tokens.base.textSecondary)
    }
}

@Composable
private fun outcomeColor(outcome: GameOutcome) = when (outcome) {
    GameOutcome.WIN -> AppColors.good
    GameOutcome.LOSS -> AppColors.bad
    GameOutcome.TIE -> AppColors.muted
}

/** 경기 성격 칩 색: 좋은 일(승 쪽)은 좋음, 나쁜 일(패 쪽)은 나쁨, 나머지(연장·1점 차)는 주의 */
@Composable
private fun tagColor(tag: String) = when {
    tag.endsWith("승") || tag == "대승" -> AppColors.good
    tag.endsWith("패") -> AppColors.bad
    else -> AppColors.warn
}

private const val FLIP_START = -90f
private const val STAMP_START = 1.8f
private const val CLOSED_ALPHA = 0.6f
private const val CAMERA_DISTANCE = 12f
private const val REGULATION = 9
private val INNING_LABEL = 52.dp
private val INNING_CELL = 18.dp
private val SCORE_CELL = 28.dp
