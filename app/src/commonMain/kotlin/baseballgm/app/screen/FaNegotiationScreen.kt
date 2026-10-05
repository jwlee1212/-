package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatRow
import baseballgm.market.ContractOffer
import baseballgm.market.FaDemand
import baseballgm.market.FaTalkOutcome
import baseballgm.market.FaTerm
import baseballgm.market.FreeAgent
import baseballgm.model.PlayerId
import kotlin.math.roundToInt

/**
 * FA 협상 테이블 (docs/11·16, 2026-10-04 유저 요청 "세부 조건을 조율할 수 있는 별개의 창 — FM식 협상").
 *
 * 위에서부터 한 줄 요약(지금 어디쯤인가 · 에이전트 인내심) → 조건 표(항목 · 선수 요구 · 중요도 · 우리 제안) →
 * 선수 마음 · 비용 → 제안하기 → 대화. 에이전트가 그 자리에서 답하고, 합의하면 바로 도장이다 (경쟁 구단보다 좋을 때).
 * 조건 표의 값은 화면이 들고 있다가 "제안하기"를 눌러야 시장에 들어간다.
 */
@Composable
fun FaNegotiationScreen(session: GameSession, playerId: PlayerId, onPlayer: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val agent = session.faAgent(playerId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        if (agent == null) {
            // 계약을 마쳤거나 시장이 닫혔다
            val signing = session.faOurSigning(playerId)
            SecretaryCard(
                if (signing != null) {
                    "${session.faSignedName(playerId)} 선수와 계약했어요. 연 ${signing.offer.salary.text()}억 × ${signing.offer.years}년" +
                        (if (signing.offer.signingBonus > 0) ", 계약금 ${signing.offer.signingBonus.text()}억" else "") +
                        (if (signing.offer.options.isNotEmpty()) ", 옵션 ${signing.offer.options.size}개(최대 연 ${signing.offer.optionPerYear.text()}억)" else "") + "."
                } else {
                    "이 선수는 이제 시장에 없어요."
                },
                title = "협상 끝",
            )
            TalkLines(session.faTalkLog(playerId), "R", "에이전트")
            return@Column
        }
        Negotiation(session, agent, onPlayer)
    }
}

@Composable
private fun Negotiation(session: GameSession, agent: FreeAgent, onPlayer: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    val player = session.faPlayer(agent)
    val demand = session.faDemand(agent)
    val existing = session.faOffer(agent)
    // 처음엔 낸 조건, 없으면 에이전트 요구 조건으로 채운다
    var offer by remember(agent.playerId) {
        mutableStateOf(existing ?: demand?.asOffer(session.userTeamId, agent.playerId) ?: ContractOffer(session.userTeamId, agent.playerId, agent.askingSalary, agent.askingYears))
    }
    var reply by remember(agent.playerId) { mutableStateOf<baseballgm.market.FaTalkResult?>(null) }
    val patience = session.faPatience(agent.playerId)
    val status = session.faNegotiation(agent)
    val preview = session.faPreview(agent, offer)

    // 머리: 선수 · 등급 · 희망
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerIdentity(session.tagOf(player), modifier = Modifier.weight(1f), large = true)
        TextButton(onClick = { onPlayer(player.id) }) { Text("선수 보기") }
    }

    // ① 한 줄 요약: 상태 · 라운드 · 인내심 · 경쟁
    SectionCard("협상 상황") {
        if (status != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
                standingPill(status)
                Text(
                    "${agent.grade}등급 · ${session.faRound()}/${session.faRounds()} 라운드",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textMuted,
                )
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(standingSentence(status), style = MaterialTheme.typography.bodySmall)
        }
        if (session.isOurFormer(agent)) {
            Text(
                if (agent.released) "지난 시즌 우리가 방출한 선수예요." else "지난 시즌 우리 팀 선수예요. 정과 만족도에 따라 우리 쪽을 더 좋게 봐요.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
        Spacer(Modifier.height(tokens.spacing.s))
        PatienceRow(
            patience,
            session.faMaxPatience,
            if (patience <= 0) "이번 라운드엔 더 얘기하지 않겠대요. 낸 조건은 남아 있어요." else "모자란 제안마다 한 칸, 터무니없는 제안은 두 칸 줄어요. 라운드가 바뀌면 다시 차요.",
        )
        Spacer(Modifier.height(tokens.spacing.s))
        Text(
            if (status?.rivals.isNullOrEmpty()) "경쟁 구단 없음" else "경쟁 구단 ${status!!.rivals.size}곳: " + status.rivals.joinToString { session.teamName(it) },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(session.faRumor(agent), style = MaterialTheme.typography.bodySmall, color = AppColors.warn)
        StatRow("최저 연봉", "${agent.salaryFloor.text()}억 · ${floorReason(agent.lastWar)}")
        StatRow("계약금으로 쓸 수 있는 돈", "${session.faBonusBudget(agent).text()}억")
    }

    // ② 조건 표: 항목 · 선수 요구(중요도) · 우리 제안
    SectionCard("조건") {
        if (demand?.moneyOnlyShort == true) {
            Text(
                "다른 구단 조건이 워낙 좋아서, 요구 조건을 다 맞춰도 바로 사인하진 않을 수 있대요.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.warn,
            )
            Spacer(Modifier.height(tokens.spacing.s))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("항목", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1f))
            Text("선수 요구", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1.1f))
            Text("우리 제안", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1.6f), textAlign = TextAlign.End)
        }
        val optionRate = session.faMaxOptionRate
        FaTerm.entries.forEach { term ->
            SectionDivider()
            val limits = OfferLimits(agent.salaryFloor.coerceAtLeast(SALARY_STEP), agent.askingSalary * SALARY_CEILING, 1, MAX_YEARS, optionRate)
            TermRow(
                term = term,
                wanted = demand?.value(term),
                stars = demand?.importance?.get(term) ?: 1,
                value = valueOf(offer, term),
                onMinus = { offer = step(offer, term, -1, limits) },
                onPlus = { offer = step(offer, term, +1, limits) },
            )
        }
        Spacer(Modifier.height(tokens.spacing.s))
        Text(
            "보장 총액 ${offer.totalValue.text()}억" + if (offer.options.isNotEmpty()) " · 옵션까지 최대 ${offer.maxValue.text()}억" else "",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        preview?.let {
            Spacer(Modifier.height(tokens.spacing.s))
            val heart = (it.satisfaction * 100).roundToInt().coerceAtMost(100)
            Text("이 조건이면 선수 마음 $heart%", style = MaterialTheme.typography.labelSmall)
            MeterBar(heart, if (heart >= 100) AppColors.good else AppColors.warn, Modifier.fillMaxWidth())
        }
    }

    // ②-2 옵션 조항: 조항마다 금액. 선수는 지난 시즌 기록으로 본 가능성만큼만 쳐 준다
    OptionCard(
        kinds = session.faOptionKinds(agent),
        label = session::faOptionLabel,
        outlook = { session.faOptionOutlook(agent, it) },
        offer = offer,
        maxRate = session.faMaxOptionRate,
    ) { offer = it }

    // ③ 제안 — 받아들이면 바로 계약이라 한 번 더 묻는다 (docs/16 되돌릴 수 없는 행동의 확인)
    ProposeBar(
        summary = "${offer.years}년 연 ${offer.salary.text()}억" + (if (offer.signingBonus > 0) " + 계약금 ${offer.signingBonus.text()}억" else "") +
            (if (offer.options.isNotEmpty()) " + 옵션 ${offer.options.size}개" else ""),
        firstTime = existing == null,
        enabled = patience > 0 && !session.busy,
        onPropose = { reply = session.proposeFa(agent, offer) },
        onFillDemand = demand?.let { wanted -> { offer = wanted.asOffer(session.userTeamId, agent.playerId).copy(options = offer.options) } },
    )
    reply?.let { result -> AgentReply(result.outcome, result.message, result.counter?.let { counter -> { offer = counter } }) }
    if (existing != null) {
        TextButton(onClick = { session.withdrawFaOffer(agent) }) { Text("낸 조건 철회") }
    }

    // ④ 비용: 이 조건이 성사되면 앞으로의 캡 여유·운용 자금
    SectionCard("연봉 계획") {
        val before = remember(agent.playerId, session.revision) { session.financialPlan(session.faOffersChange(target = agent.playerId)) }
        val after = remember(agent.playerId, session.revision, offer) { session.financialPlan(session.faOffersChange(agent.playerId, offer)) }
        Text("이 조건이면 (옵션은 빼고)", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
        PlanChangeTable(before, after)
    }

    TalkLines(session.faTalkLog(agent.playerId), "R", "에이전트")
}

/**
 * 옵션 조항 카드 (2026-10-04 다양화). 조항 한 줄 = 이름 · 지난 시즌 기록 · 선수가 보는 가능성 / − 금액 +.
 * 금액 0 은 "안 건다". 조항 합은 연봉의 상한 비율까지 — 넘는 + 는 막는다.
 */
@Composable
internal fun OptionCard(
    kinds: List<baseballgm.market.OptionKind>,
    label: (baseballgm.market.OptionKind) -> String,
    /** (지난 시즌 기록 글자, 선수가 보는 가능성) */
    outlook: (baseballgm.market.OptionKind) -> Pair<String, Double>,
    offer: ContractOffer,
    maxRate: Double,
    onChange: (ContractOffer) -> Unit,
) {
    val tokens = AppTheme.tokens
    val cap = kotlin.math.floor(offer.salary * maxRate * 10) / 10.0
    SectionCard("옵션 조항") {
        Text(
            "기준을 달성한 시즌에만 주는 돈이에요(연봉 총액에는 안 들어가요). 선수는 지난 시즌 기록으로 달성 가능성을 따져서, 어려운 조항일수록 덜 쳐 줘요. " +
                "합계는 연봉의 ${(maxRate * 100).roundToInt()}%까지.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            "걸어 둔 옵션 연 ${offer.optionPerYear.text()}억 / 상한 ${cap.text()}억",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (offer.optionPerYear > cap + 1e-6) AppColors.bad else tokens.base.text,
        )
        kinds.forEach { kind ->
            SectionDivider()
            val amount = offer.options.firstOrNull { it.kind == kind }?.amount ?: 0.0
            val (last, chance) = outlook(kind)
            Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label(kind), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$last · 선수 생각: ${chanceWords(chance)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.base.textMuted,
                    )
                }
                TextButton(onClick = { onChange(withOption(offer, kind, amount - OPTION_STEP)) }, enabled = amount > 0.0) { Text("−") }
                Text(
                    if (amount > 0.0) "${amount.text()}억" else "없음",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (amount > 0.0) FontWeight.Bold else FontWeight.Normal,
                    color = if (amount > 0.0) tokens.base.text else tokens.base.textMuted,
                    modifier = Modifier.widthIn(min = tokens.spacing.xl * 2),
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = { onChange(withOption(offer, kind, amount + OPTION_STEP)) },
                    enabled = offer.optionPerYear + OPTION_STEP <= cap + 1e-6,
                ) { Text("+") }
            }
        }
    }
}

/** 선수가 보는 달성 가능성을 말로 (표시 기준) */
private fun chanceWords(chance: Double): String = when {
    chance >= LIKELY -> "충분히 가능"
    chance >= MAYBE -> "반반"
    else -> "어렵다"
}

private fun withOption(offer: ContractOffer, kind: baseballgm.market.OptionKind, amount: Double): ContractOffer {
    val rest = offer.options.filterNot { it.kind == kind }
    val value = round1(amount)
    return offer.copy(options = if (value > 0.0) rest + baseballgm.market.OptionClause(kind, value) else rest)
}

/** 항목 한 줄: 이름 · 중요도 점 / 요구값 / − 값 + (요구에 못 미치면 주의 색) */
@Composable
internal fun TermRow(term: FaTerm, wanted: Double?, stars: Int, value: Double, onMinus: () -> Unit, onPlus: () -> Unit) {
    val tokens = AppTheme.tokens
    val met = wanted == null || value + 1e-6 >= wanted
    Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(term.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            // 중요도: 점 세 칸 중 채운 칸 (글자 아이콘을 쓰지 않는다 — 디자인 규칙)
            Row(
                Modifier.padding(top = tokens.spacing.xs).semantics { contentDescription = "선수에게 중요한 정도 $stars / $MAX_STARS" },
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                repeat(MAX_STARS) { index ->
                    Box(
                        Modifier.size(tokens.spacing.s).clip(CircleShape)
                            .background(if (index < stars) tokens.base.textSecondary else tokens.base.cardInset),
                    )
                }
            }
        }
        Text(
            if (wanted == null) "—" else format(term, wanted),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textSecondary,
            modifier = Modifier.weight(1.1f),
        )
        Row(Modifier.weight(1.6f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus) { Text("−") }
            Text(
                format(term, value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (met) tokens.base.text else AppColors.warn,
                modifier = Modifier.widthIn(min = tokens.spacing.xl * 2),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onPlus) { Text("+") }
        }
    }
}

/** 에이전트 인내심: 칸 게이지 (트레이드 교환대 게이지와 같은 모양) */
@Composable
internal fun PatienceRow(left: Int, max: Int, note: String) {
    val tokens = AppTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("에이전트 인내심", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            modifier = Modifier.semantics { contentDescription = "에이전트 인내심 $left / $max" },
        ) {
            repeat(max) { index ->
                Box(
                    Modifier.size(width = tokens.spacing.l, height = tokens.spacing.s).clip(CircleShape)
                        .background(if (index < left) (if (left <= 1) AppColors.warn else tokens.base.brand) else tokens.base.cardInset),
                )
            }
        }
    }
    Text(note, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
}

/**
 * 제안 줄: "제안하기" → 확인 한 줄("… 으로 제안할까요? 받아들이면 바로 계약돼요") → 제안한다 / 다시 생각.
 * 받아들이면 되돌릴 수 없어서 한 번 더 묻는다 (docs/16)
 */
@Composable
internal fun ProposeBar(
    summary: String,
    firstTime: Boolean,
    enabled: Boolean,
    onPropose: () -> Unit,
    onFillDemand: (() -> Unit)?,
) {
    val tokens = AppTheme.tokens
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        Text("$summary 으로 제안할까요? 받아들이면 바로 계약돼요.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            Button(onClick = { confirming = false; onPropose() }, enabled = enabled) { Text("제안한다") }
            OutlinedButton(onClick = { confirming = false }) { Text("다시 생각") }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { confirming = true }, enabled = enabled) { Text(if (firstTime) "제안하기" else "다시 제안하기") }
            onFillDemand?.let { OutlinedButton(onClick = it) { Text("요구대로 채우기") } }
        }
    }
}

/** 에이전트의 답 한 마디 + 역제안이 있으면 "이 조건으로 맞추기" */
@Composable
internal fun AgentReply(outcome: FaTalkOutcome, message: String, onApply: (() -> Unit)?) {
    SecretaryCard(message, title = "에이전트 답 · ${outcome.label}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(outcome.label, outcomeColor(outcome))
            Spacer(Modifier.weight(1f))
            onApply?.let { TextButton(onClick = it) { Text("이 조건으로 맞추기") } }
        }
    }
}

/**
 * 협상 대화: 우리 말은 오른쪽, 상대 말은 왼쪽 (말풍선은 옅은 면 — 카드 중첩 금지).
 * @param unit 시점 단위 ("R" = FA 라운드, "주차" = 시즌 주차)
 */
@Composable
internal fun TalkLines(lines: List<baseballgm.market.FaTalkLine>, unit: String, counterpart: String) {
    if (lines.isEmpty()) return
    val tokens = AppTheme.tokens
    SectionCard("대화") {
        lines.asReversed().forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), horizontalArrangement = if (line.fromAgent) Arrangement.Start else Arrangement.End) {
                Column(
                    Modifier.fillMaxWidth(BUBBLE_WIDTH)
                        .clip(RoundedCornerShape(tokens.radii.chip))
                        .background(if (line.fromAgent) tokens.base.cardInset else tokens.base.brand.copy(alpha = BRAND_TINT))
                        .padding(tokens.spacing.s),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (line.fromAgent) counterpart else "우리") + " · ${line.round}$unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.base.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        line.outcome?.let { Pill(it.label, outcomeColor(it)) }
                    }
                    Text(line.text, style = MaterialTheme.typography.bodySmall, color = tokens.base.text)
                }
            }
        }
    }
}

@Composable
private fun outcomeColor(outcome: FaTalkOutcome) = when (outcome) {
    FaTalkOutcome.SIGNED -> AppColors.good
    FaTalkOutcome.CONSIDERING, FaTalkOutcome.COUNTERED -> AppColors.warn
    FaTalkOutcome.REJECTED, FaTalkOutcome.BROKEN_OFF -> AppColors.bad
}

internal fun valueOf(offer: ContractOffer, term: FaTerm): Double = when (term) {
    FaTerm.SALARY -> offer.salary
    FaTerm.YEARS -> offer.years.toDouble()
    FaTerm.BONUS -> offer.signingBonus
}

/** 조절 범위: 연봉 바닥·천장, 기간 최소·최대, 옵션 상한 비율 */
internal data class OfferLimits(val salaryMin: Double, val salaryMax: Double, val minYears: Int, val maxYears: Int, val optionRate: Double)

/**
 * 한 칸 움직이기. 연봉 0.5억 · 기간 1년 · 계약금 1억 (표시 조작 단위).
 * 연봉을 내려 옵션 합이 상한(연봉 × 옵션 상한 비율)을 넘게 되면 큰 조항부터 깎는다.
 */
internal fun step(offer: ContractOffer, term: FaTerm, direction: Int, limits: OfferLimits): ContractOffer {
    val optionRate = limits.optionRate
    val moved = when (term) {
        FaTerm.SALARY -> offer.copy(salary = round1((offer.salary + SALARY_STEP * direction).coerceIn(limits.salaryMin, limits.salaryMax)))
        FaTerm.YEARS -> offer.copy(years = (offer.years + direction).coerceIn(limits.minYears, limits.maxYears))
        FaTerm.BONUS -> offer.copy(signingBonus = round1((offer.signingBonus + BONUS_STEP * direction).coerceAtLeast(0.0)))
    }
    var over = moved.optionPerYear - kotlin.math.floor(moved.salary * optionRate * 10) / 10.0
    if (over <= 1e-6) return moved
    val trimmed = moved.options.sortedByDescending { it.amount }.mapNotNull { clause ->
        val cut = minOf(clause.amount, over.coerceAtLeast(0.0))
        over -= cut
        clause.copy(amount = round1(clause.amount - cut)).takeIf { it.amount > 0.0 }
    }
    return moved.copy(options = trimmed)
}

private fun format(term: FaTerm, value: Double): String = when (term) {
    FaTerm.YEARS -> "${value.roundToInt()}년"
    else -> "${value.text()}억"
}

private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

internal fun Double.text(): String = round1(this).toString()

private const val MAX_STARS = 3
/** 가능성 말 기준 (표시용) */
private const val LIKELY = 0.65
private const val MAYBE = 0.35
internal const val SALARY_STEP = 0.5
private const val BONUS_STEP = 1.0
private const val OPTION_STEP = 0.5
/** 연봉 조작 상한: 희망 연봉의 두 배 (예전 슬라이더와 같다) */
private const val SALARY_CEILING = 2.0
/** 기간 조작 상한 (예전 슬라이더와 같다 — FA 최장 계약은 엔진이 다시 본다) */
private const val MAX_YEARS = 6
private const val BUBBLE_WIDTH = 0.85f
private const val BRAND_TINT = 0.12f
