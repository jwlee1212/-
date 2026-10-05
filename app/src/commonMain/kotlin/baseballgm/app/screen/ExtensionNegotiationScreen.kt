package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.app.ui.StatRow
import baseballgm.market.ContractOffer
import baseballgm.market.FaTerm
import baseballgm.model.PlayerId
import baseballgm.season.ExtensionResult
import kotlin.math.roundToInt

/**
 * 비FA 다년계약 협상 테이블 (docs/11, 2026-10-05 유저 요청 "비FA 계약에도 똑같이 적용해줘").
 *
 * FA 협상 테이블과 같은 모양: 협상 상황(지금 계약 · FA 까지 · 시장가 · 만족도 · 인내심) → 조건 표(항목 · 선수 요구 · 중요도 · 우리 제안) →
 * 옵션 조항 → 제안하기(확인) → 선수 측 답 → 연봉 계획 → 대화. 경쟁 구단이 없어서 "고민 중"은 없다 — 요구를 채우면 바로 도장.
 * 새 연봉·옵션은 다음 시즌부터, 계약금은 지금 운용 자금에서.
 */
@Composable
fun ExtensionNegotiationScreen(session: GameSession, playerId: PlayerId, onPlayer: (PlayerId) -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val player = session.player(playerId)
    val preferred = remember(playerId) { session.extensionTerms(playerId) }
    var offer by remember(playerId) {
        mutableStateOf(ContractOffer(session.userTeamId, playerId, preferred.demand, preferred.years))
    }
    var reply by remember(playerId) { mutableStateOf<ExtensionResult?>(null) }
    val terms = session.extensionTerms(playerId, offer.years)
    val blocked = session.extensionBlockedReason(playerId)
    val done = session.extendedThisSeason(playerId)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIdentity(
                session.tagOf(player, caption = "${player.ageIn(session.league.season)}세 · 다년계약 협상"),
                modifier = Modifier.weight(1f),
                large = true,
            )
            TextButton(onClick = { onPlayer(playerId) }) { Text("선수 보기") }
        }

        if (done) {
            SecretaryCard(reply?.message ?: "올 시즌 다년계약을 이미 맺었어요.", title = "협상 끝")
            TalkLines(session.extensionTalkLog(playerId), "주차", "선수 측")
            return@Column
        }

        // ① 협상 상황
        SectionCard("협상 상황") {
            StatRow("지금 연봉", "${terms.currentSalary.text()}억 · ${player.contract.yearsRemaining}년 남음")
            StatRow("FA까지", if (terms.seasonsToFa == 0) "이번 시즌 뒤 FA" else "${terms.seasonsToFa}시즌")
            StatRow("FA 시장에 나가면", "연 ${terms.marketSalary.text()}억 안팎")
            val discount = ((1 - terms.demand / terms.marketSalary) * 100).roundToInt()
            Text(
                if (terms.seasonsToFa == 0) {
                    "FA가 코앞이지만 지금 묶으면 시장가보다 ${discount}% 싸요. 시장에 나가면 다른 팀과 값 경쟁을 해야 해요."
                } else {
                    "FA가 ${terms.seasonsToFa}시즌 남아서, 지금 묶으면 시장가보다 ${discount}% 싸게 잡을 수 있어요. FA가 멀수록 더 싸요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
            session.morale(player)?.let { morale ->
                StatRow("만족도", "${morale.value} · ${morale.level.label}", if (morale.level.unhappy) AppColors.bad else null)
                Text(
                    "만족도와 충성심이 높을수록 덜 불러요. 만족도가 바닥이면 협상 자체를 안 해요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textMuted,
                )
            }
            Spacer(Modifier.height(tokens.spacing.s))
            PatienceRow(
                terms.talksLeft,
                session.extensionMaxTalks,
                if (terms.talksLeft <= 0) "올 시즌엔 더 얘기하지 않겠대요." else "모자란 제안마다 한 칸, 터무니없는 제안은 두 칸 줄어요. 다 떨어지면 올 시즌 협상은 끝이에요.",
            )
            blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.bad) }
        }

        // ② 조건 표
        SectionCard("조건 (다음 시즌부터)") {
            Row(Modifier.fillMaxWidth()) {
                Text("항목", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1f))
                Text("선수 요구", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1.1f))
                Text("우리 제안", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.weight(1.6f), textAlign = TextAlign.End)
            }
            val limits = OfferLimits(SALARY_STEP, terms.demand * SALARY_HEADROOM, terms.minYears, terms.maxYears, session.faMaxOptionRate)
            FaTerm.entries.forEach { term ->
                SectionDivider()
                TermRow(
                    term = term,
                    wanted = when (term) {
                        FaTerm.SALARY -> terms.demand
                        FaTerm.YEARS -> terms.preferredYears.toDouble()
                        FaTerm.BONUS -> null
                    },
                    stars = terms.importance[term] ?: 1,
                    value = valueOf(offer, term),
                    onMinus = { offer = step(offer, term, -1, limits) },
                    onPlus = { offer = step(offer, term, +1, limits) },
                )
            }
            if (terms.years < terms.preferredYears) {
                Text(
                    "선수는 ${terms.preferredYears}년을 원해요. 짧게 하면 연봉을 더 달래요 (지금 기간이면 연 ${terms.demand.text()}억).",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.base.textMuted,
                )
            }
            Spacer(Modifier.height(tokens.spacing.s))
            // 요구액 대비: 연봉 + 계약금/년 + 옵션(선수가 보는 값)
            val effective = offer.salary + offer.signingBonus / offer.years + session.extensionOptionValue(player, offer.options)
            val ratio = (effective / terms.demand * 100).roundToInt()
            Text("요구액 대비 $ratio%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            MeterBar(ratio.coerceAtMost(100), if (ratio >= 100) AppColors.good else AppColors.warn, Modifier.fillMaxWidth())
            Text(
                "보장 총액 ${offer.totalValue.text()}억 · 계약금은 지금 운용 자금(${session.currentFunds().text()}억)에서",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.base.textMuted,
            )
        }

        OptionCard(
            kinds = session.optionKindsFor(player),
            label = session::faOptionLabel,
            outlook = { session.extensionOptionOutlook(player, it) },
            offer = offer,
            maxRate = session.faMaxOptionRate,
        ) { offer = it }

        // ③ 제안
        ProposeBar(
            summary = "${offer.years}년 연 ${offer.salary.text()}억" + (if (offer.signingBonus > 0) " + 계약금 ${offer.signingBonus.text()}억" else "") +
                (if (offer.options.isNotEmpty()) " + 옵션 ${offer.options.size}개" else ""),
            firstTime = session.extensionTalkLog(playerId).isEmpty(),
            enabled = blocked == null,
            onPropose = { reply = session.proposeExtension(playerId, offer.salary, offer.years, offer.signingBonus, offer.options) },
            onFillDemand = { offer = offer.copy(salary = terms.demand, years = terms.years) },
        )
        reply?.let { result -> AgentReply(result.outcome, result.message, result.counter?.let { salary -> { offer = offer.copy(salary = salary) } }) }

        // ④ 연봉 계획
        SectionCard("연봉 계획") {
            val before = remember(playerId, session.revision) { session.financialPlan() }
            val after = remember(playerId, session.revision, offer) {
                session.financialPlan(session.extensionChange(playerId, offer.salary, offer.years, offer.signingBonus))
            }
            Text("이 조건이면 (옵션은 빼고)", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
            PlanChangeTable(before, after)
        }

        TalkLines(session.extensionTalkLog(playerId), "주차", "선수 측")
    }
}

/** 연봉 조작 상한: 요구액의 1.5배 (예전 슬라이더와 같다, 표시 조작 기준) */
private const val SALARY_HEADROOM = 1.5
