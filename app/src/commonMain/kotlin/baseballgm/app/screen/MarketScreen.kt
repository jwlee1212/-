package baseballgm.app.screen

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Check
import baseballgm.app.ui.PositionBadge
import baseballgm.app.ui.NumberBadge
import baseballgm.app.ui.SectionDivider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill
import baseballgm.app.ui.PlayerFilterBar
import baseballgm.app.ui.rememberPlayerFilter
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.ListCell
import baseballgm.app.ui.PlayerIdentity
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.PlayerList
import baseballgm.app.ui.PlayerStatus
import baseballgm.app.ui.RowAction
import baseballgm.app.ui.StatusBadge
import baseballgm.app.ui.playerListItems
import baseballgm.app.ui.rememberPlayerListScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import baseballgm.app.ui.StatRow
import baseballgm.market.ForeignCandidate
import baseballgm.market.FaNegotiation
import baseballgm.market.FaStanding
import baseballgm.market.FreeAgent
import baseballgm.market.TradeVerdict
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import kotlin.math.roundToInt

/** 교환대에 미리 올려 둘 거래 (미리보기용) */
data class TradeDraft(
    val partner: TeamId,
    val giving: List<PlayerId> = emptyList(),
    val receiving: List<PlayerId> = emptyList(),
    val givingPicks: List<baseballgm.market.DraftPickRight> = emptyList(),
    val receivingPicks: List<baseballgm.market.DraftPickRight> = emptyList(),
)

/** 영입 탭의 구역 (2026-10-03 화면 개편). 순서가 탭 순서다 */
enum class RecruitSection(val label: String) {
    TRADE("트레이드"),
    FREE_AGENCY("FA"),
    FOREIGN("외국인"),
    SCOUT("스카우트"),
    TEAMS("구단 현황"),
}

/**
 * 영입 탭 (docs/11·10·12, 2026-10-03 화면 개편 — 예전 "시장" + "스카우트" 탭).
 *
 * 선수를 데려오는 길을 한 탭에 모았다: 트레이드 / FA / 외국인 / 스카우트(드래프트) / 구단 현황.
 * - FA 는 스토브리그에만 열린다. 그 밖의 때엔 "이번 시즌 뒤 FA 가 되는 우리 선수"를 미리 보여 준다
 * - FA 시장이 열리면 FA 구역, 드래프트 주차가 되면 스카우트 구역이 저절로 열린다
 * 구역은 화면 밖(셸)이 들고 있어서, 비서 브리핑의 "결정할 것"에서 바로 그 구역으로 보낼 수 있다.
 */
@Composable
fun RecruitScreen(
    session: GameSession,
    section: RecruitSection,
    onSection: (RecruitSection) -> Unit,
    onPlayer: (PlayerId) -> Unit = {},
    onCompare: (List<PlayerId>) -> Unit = {},
    onProspect: (PlayerId) -> Unit = {},
    /** 우리 신인 화면 (드래프트 직후) */
    onDraftClass: () -> Unit = {},
    /** 스카우트 정기 리포트 화면 */
    onScoutDigest: () -> Unit = {},
    /** 미리보기용: 교환대에 미리 올려 둘 거래 */
    tradeDraft: TradeDraft? = null,
    /** FA 협상 테이블 (2026-10-04) */
    onNegotiate: (PlayerId) -> Unit = {},
    /** 비FA 다년계약 협상 테이블 (2026-10-05) */
    onExtension: (PlayerId) -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val draftLive = session.isDraftWeek && !session.draftDone
    androidx.compose.runtime.LaunchedEffect(session.inFreeAgency, draftLive) {
        if (session.inFreeAgency) onSection(RecruitSection.FREE_AGENCY)
        if (draftLive) onSection(RecruitSection.SCOUT)
    }
    Column(Modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = AppTheme.tokens.spacing.l) {
            RecruitSection.entries.forEach { entry ->
                Tab(selected = section == entry, onClick = { onSection(entry) }, text = { Text(entry.label) })
            }
        }
        when (section) {
            RecruitSection.TRADE -> TradeTab(session, onPlayer, onCompare, tradeDraft)
            RecruitSection.FREE_AGENCY -> if (session.inFreeAgency) FreeAgencyTab(session, onNegotiate) else FreeAgencyPreview(session, onPlayer, onExtension)
            RecruitSection.FOREIGN -> ForeignTab(session)
            RecruitSection.SCOUT -> ScoutScreen(session, onProspect, onDraftClass, onScoutDigest)
            RecruitSection.TEAMS -> TeamModeTab(session)
        }
    }
}

/**
 * FA 시장이 닫혀 있을 때: 언제 열리는지 + 이번 시즌이 끝나면 FA 가 되는 우리 선수 + **비FA 다년계약** 후보.
 * FA 대상도 시즌 중엔 다년계약으로 미리 묶을 수 있다 (시장에 나가면 다른 팀과 경쟁해야 한다).
 */
@Composable
private fun FreeAgencyPreview(session: GameSession, onPlayer: (PlayerId) -> Unit, onExtension: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    val leaving = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM) + session.roster(baseballgm.model.RosterLevel.FUTURES)
    val expiring = leaving.filter { !it.isForeign && it.contract.seasonsToFreeAgency == 0 && it.contract.yearsRemaining <= 1 }
    val candidates = session.extensionCandidates()
    val extended = leaving.filter { it.contract.type == baseballgm.model.ContractType.MULTI_YEAR }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = tokens.spacing.m),
    ) {
        item {
            baseballgm.app.ui.SecretaryCard(
                "FA 시장은 정규시즌과 포스트시즌이 끝나고 스토브리그에 열려요. " +
                    (if (expiring.isEmpty()) "올해 FA 가 되는 우리 선수는 없어요." else "그때 우리 선수 ${expiring.size}명도 시장에 나가요.") +
                    " 그 전에 다년계약으로 미리 묶어 둘 수 있어요. FA가 멀수록 싸게 잡혀요.",
            )
        }
        if (expiring.isNotEmpty()) {
            item {
                SectionCard("이번 시즌 뒤 FA 대상") {
                    expiring.forEach { player ->
                        PlayerLine(
                            session.tagOf(player, caption = "${player.ageIn(session.league.season)}세 · 연봉 ${player.contract.salary.oneDecimal()}억"),
                            onClick = { onPlayer(player.id) },
                        ) {
                            if (session.extensionBlockedReason(player.id) == null) {
                                TextButton(onClick = { onExtension(player.id) }) { Text("다년계약") }
                            } else {
                                val overall = session.scout(player).overall
                                Text(overall.toString(), fontWeight = FontWeight.Bold, color = tokens.grade.of(overall.center))
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard("비FA 다년계약 협상") {
                Text(
                    "계약이 2년 이하로 남은 우리 국내 선수와 협상할 수 있어요. 연봉은 다음 시즌부터 바뀌고, 계약금은 지금 나가요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                if (candidates.isEmpty()) {
                    Text("지금 협상할 수 있는 선수가 없어요.", style = MaterialTheme.typography.bodySmall)
                }
                candidates.take(MAX_EXTENSION_ROWS).forEach { player ->
                    val terms = session.extensionTerms(player.id)
                    PlayerLine(
                        session.tagOf(
                            player,
                            caption = "${player.ageIn(session.league.season)}세 · " +
                                (if (terms.seasonsToFa == 0) "이번 시즌 뒤 FA" else "FA까지 ${terms.seasonsToFa}시즌") +
                                " · 요구 ${terms.demand.oneDecimal()}억×${terms.years}년",
                        ),
                        onClick = { onPlayer(player.id) },
                    ) {
                        TextButton(onClick = { onExtension(player.id) }) { Text("협상") }
                    }
                }
            }
        }
        if (extended.isNotEmpty()) {
            item {
                SectionCard("다년계약으로 묶은 선수") {
                    extended.forEach { player ->
                        val next = player.contract.nextSalary
                        PlayerLine(
                            session.tagOf(
                                player,
                                caption = "${player.contract.yearsRemaining}년 남음 · " +
                                    (if (next != null) "다음 시즌부터 ${next.oneDecimal()}억" else "연봉 ${player.contract.salary.oneDecimal()}억"),
                            ),
                            onClick = { onPlayer(player.id) },
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_EXTENSION_ROWS = 15

// ---------- FA ----------

/**
 * FA 시장 (docs/11, 2026-10-03 협상 현황 개편).
 *
 * 위에서부터 "지금 어디쯤인가 → 우리 협상은 어떤가 → 무슨 일이 있었나 → 전체 명단" 순서다.
 * - 라운드 진행 막대: 몇 라운드 남았는지, 마지막 라운드엔 최고 조건에 무조건 도장이라는 것
 * - 우리 협상 현황: 조건을 낸 선수마다 1순위/밀림/망설임 + 다음 진행 때 계약 확률 + "얼마면 1순위"
 * - 지난 라운드 소식: 우리 관련 소식(밀림·철수·영입 성공)을 맨 위에
 * - 계약 완료: 이번 스토브리그에 끝난 계약
 */
@Composable
private fun FreeAgencyTab(session: GameSession, onNegotiate: (PlayerId) -> Unit) {
    var filter by rememberPlayerFilter("fa")
    val allAgents = session.faAgents()
    var onlyOurs by rememberSaveable { mutableStateOf(false) }
    val ourCount = allAgents.count { session.isOurFormer(it) }
    // FA 는 아직 계약이 없어서 희망 연봉·기간으로 거른다
    val agents = allAgents.filter { filter.matches(session.scout(session.faPlayer(it)), it.askingSalary, it.askingYears) }
        .filter { !onlyOurs || session.isOurFormer(it) }
    val scroll = rememberPlayerListScroll()
    val tokens = AppTheme.tokens
    val gap = tokens.spacing.m
    val mine = session.faMyNegotiations()

    // FA 목록이 둥근 카드 하나로 이어지도록 spacedBy 대신 카드마다 아래 Spacer
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = tokens.spacing.s),
    ) {
        item {
            SectionCard("FA 시장") {
                RoundProgress(session.faRound(), session.faRounds())
                Spacer(Modifier.height(tokens.spacing.s))
                Text(
                    "조건을 내면 선수가 가장 마음에 드는 곳을 1순위로 둬요. 밀린 구단은 값을 올리거나 물러나요. " +
                        "1순위이고 선수가 만족하면 진행할 때 도장을 찍을 수 있어요. 마지막 라운드엔 1순위 조건에 무조건 찍어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                StatRow("남은 FA", "${allAgents.size}명")
                StatRow("그중 우리 팀 출신", "${ourCount}명")
                StatRow("운용 자금", "${session.currentFunds().oneDecimal()}억")
                Spacer(Modifier.height(tokens.spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
                    androidx.compose.material3.Button(onClick = { session.advanceFaRound() }, enabled = !session.busy) {
                        Text(if (session.faRound() >= session.faRounds()) "마지막 라운드 진행" else "다음 라운드 진행")
                    }
                    OutlinedButton(onClick = { session.skipFreeAgency() }, enabled = !session.busy) {
                        Text("한 번에 마무리")
                    }
                }
            }
            Spacer(Modifier.height(gap))
        }
        item {
            SectionCard("우리 협상 현황") {
                if (mine.isEmpty()) {
                    Text(
                        "아직 조건을 낸 선수가 없어요. 아래 명단에서 '협상'을 눌러 협상 테이블에 앉아 보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.base.textMuted,
                    )
                }
                mine.forEachIndexed { index, (agent, negotiation) ->
                    if (index > 0) SectionDivider()
                    NegotiationRow(session, agent, negotiation, onClick = { onNegotiate(agent.playerId) })
                }
            }
            Spacer(Modifier.height(gap))
        }
        // 연봉 계획: 낸 조건이 다 성사되면 앞으로 몇 년 캡 여유·운용 자금이 어떻게 되는가 (2026-10-04)
        item {
            SectionCard("연봉 계획") {
                val base = session.financialPlan()
                if (mine.isEmpty()) {
                    FinancePlanSummary(base, showTable = false)
                } else {
                    Text("낸 조건이 모두 성사되면", style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
                    PlanChangeTable(base, session.financialPlan(session.faOffersChange()))
                }
            }
            Spacer(Modifier.height(gap))
        }
        session.lastFaReport?.let { report ->
            if (report.messages.isNotEmpty() || report.userMessages.isNotEmpty()) {
                item {
                    SectionCard("${report.round}라운드 소식") {
                        report.userMessages.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        report.messages.take(8).forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                        }
                    }
                    Spacer(Modifier.height(gap))
                }
            }
        }
        val signings = session.faSignings()
        if (signings.isNotEmpty()) {
            item {
                SectionCard("계약 완료 ${signings.size}건") {
                    signings.take(10).forEach { signing ->
                        val ours = signing.offer.teamId == session.userTeamId
                        val name = session.faSignedName(signing.playerId)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "$name → ${session.teamName(signing.offer.teamId)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (ours) FontWeight.Bold else FontWeight.Normal,
                                color = if (ours) AppColors.good else tokens.base.text,
                            )
                            Text(
                                "${signing.offer.salary.oneDecimal()}억 × ${signing.offer.years}년",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.base.textMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(gap))
            }
        }
        item {
            PlayerFilterBar(filter, { filter = it }, resultCount = agents.size, showContract = true, showSalary = true)
            if (ourCount > 0) {
                androidx.compose.material3.FilterChip(
                    selected = onlyOurs,
                    onClick = { onlyOurs = !onlyOurs },
                    label = { Text("우리 팀 출신만 ($ourCount)") },
                )
            }
            Spacer(Modifier.height(gap))
        }
        faSections(session, agents).forEach { section ->
            playerListItems(section, scroll, onPlayer = onNegotiate, action = { row -> RowAction("협상") { onNegotiate(row.id) } })
            item { Spacer(Modifier.height(gap)) }
        }
    }
}

/** 라운드 진행 막대: 지난 라운드는 진하게, 지금 라운드는 테두리, 마지막 칸엔 "도장" 표시 */
@Composable
private fun RoundProgress(round: Int, rounds: Int) {
    val tokens = AppTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        (1..rounds).forEach { index ->
            val color = when {
                index < round -> tokens.base.brand
                index == round -> tokens.base.brand.copy(alpha = 0.55f)
                else -> tokens.base.textMuted.copy(alpha = 0.25f)
            }
            Box(Modifier.weight(1f).height(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        }
    }
    Spacer(Modifier.height(tokens.spacing.xs))
    val left = rounds - round
    Text(
        if (left <= 0) "${round}/${rounds} 라운드 · 마지막 라운드예요" else "${round}/${rounds} 라운드 · ${left}라운드 남음",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

/** 협상 상태 → 칩 글자·색 */
@Composable
internal fun standingPill(negotiation: FaNegotiation) {
    when (negotiation.standing) {
        FaStanding.LEADING -> Pill("1순위", AppColors.good)
        FaStanding.LEADING_WAITING -> Pill("1순위 · 고민 중", AppColors.warn)
        FaStanding.OUTBID -> Pill("밀림", AppColors.bad)
        FaStanding.NO_OFFER -> Pill("미제안", AppColors.muted)
        FaStanding.BELOW_FLOOR -> Pill("최저 연봉 미달", AppColors.bad)
    }
}

/** 상태를 한 문장으로 */
internal fun standingSentence(negotiation: FaNegotiation): String = when (negotiation.standing) {
    FaStanding.LEADING ->
        if (negotiation.lastRound) "우리가 1순위예요. 진행하면 우리와 계약해요."
        else "우리가 1순위고 선수도 만족해요. 다음 진행 때 ${percent(negotiation.ourSignChance)} 확률로 도장을 찍어요."

    FaStanding.LEADING_WAITING ->
        if (negotiation.lastRound) "우리가 1순위예요. 마지막 라운드라 진행하면 우리와 계약해요."
        else "우리가 1순위지만 선수가 아직 망설여요. 조금 더 쓰거나 마지막 라운드까지 버티면 돼요."

    FaStanding.OUTBID -> buildString {
        append("다른 구단 조건이 더 좋아요.")
        negotiation.salaryToLead?.let { append(" 연 ${it.oneDecimal()}억이면 1순위가 돼요.") }
            ?: append(" 연봉만으론 뒤집기 어려워요 (기간·계약금을 늘려 보세요).")
        if (negotiation.rivalSignChance > 0) append(" 이대로면 다음 진행 때 ${percent(negotiation.rivalSignChance)} 확률로 다른 팀에 가요.")
    }

    FaStanding.NO_OFFER -> "아직 조건을 내지 않았어요."

    FaStanding.BELOW_FLOOR ->
        "최저 연봉 ${negotiation.salaryFloor.oneDecimal()}억(${floorReason(negotiation.lastWar)})에 못 미쳐서 선수가 쳐다보지 않아요. " +
            "마지막 라운드에도 이 조건으론 계약이 안 돼요."
}

/** 최저 연봉의 근거 */
internal fun floorReason(lastWar: Double?): String =
    if (lastWar == null) "지난 시즌 1군 기록 없음" else "지난 시즌 WAR ${lastWar.oneDecimal()}"

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

@Composable
private fun NegotiationRow(session: GameSession, agent: FreeAgent, negotiation: FaNegotiation, onClick: () -> Unit) {
    val tokens = AppTheme.tokens
    val offer = negotiation.ourOffer ?: return
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = tokens.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            Text(session.faPlayer(agent).registeredName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (session.isOurFormer(agent)) baseballgm.app.ui.OurFormerMark(session.userTeamId)
            standingPill(negotiation)
            Spacer(Modifier.weight(1f))
            Text(
                "우리 ${offer.salary.oneDecimal()}억 × ${offer.years}년",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(standingSentence(negotiation), style = MaterialTheme.typography.bodySmall)
        Text(
            "경쟁 ${negotiation.rivals.size}곳 · 희망 ${agent.askingSalary.oneDecimal()}억 × ${agent.askingYears}년",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
    }
}

/**
 * FA 목록 묶음: 기본 능력치 열 + FA 등급 · 희망 연봉 · 희망 기간 · 경쟁 구단 수. 기록·계약 열은 없다(계약이 끝난 선수다).
 * 조건을 낸 선수는 상태 배지(1순위 / 고민 중 / 밀림), 방출 선수는 보조 글자에 "방출".
 */
private fun faSections(session: GameSession, agents: List<FreeAgent>): List<baseballgm.app.ui.PlayerListSection> {
    val byId = agents.associateBy { it.playerId }
    val extras = listOf(
        ExtraColumn(baseballgm.app.ui.TableColumn("등급", 44.dp)) { p -> ListCell.Value("${byId.getValue(p.id).grade}", bold = true) },
        ExtraColumn(baseballgm.app.ui.TableColumn("희망", 56.dp)) { p -> ListCell.Value("${byId.getValue(p.id).askingSalary.oneDecimal()}억") },
        ExtraColumn(baseballgm.app.ui.TableColumn("기간", 44.dp)) { p -> ListCell.Value("${byId.getValue(p.id).askingYears}년") },
        ExtraColumn(baseballgm.app.ui.TableColumn("경쟁", 44.dp)) { p -> ListCell.Value("${byId.getValue(p.id).interestedTeams}곳") },
    )
    return playerListSections(
        session,
        agents.map { session.faPlayer(it) },
        baseballgm.model.RosterLevel.FIRST_TEAM,
        stats = false,
        contract = false,
        extras = extras,
    ).map { section ->
        section.copy(
            rows = section.rows.map { row ->
                val agent = byId.getValue(row.id)
                val negotiation = session.faOffer(agent)?.let { session.faNegotiation(agent) }
                val badge = when (negotiation?.standing) {
                    FaStanding.LEADING -> StatusBadge(PlayerStatus.OFFERED, "1순위")
                    FaStanding.LEADING_WAITING -> StatusBadge(PlayerStatus.OFFER_WAITING, "1순위 · 고민 중")
                    FaStanding.OUTBID -> StatusBadge(PlayerStatus.OUTBID, "밀림")
                    FaStanding.BELOW_FLOOR -> StatusBadge(PlayerStatus.OUTBID, "최저 연봉 미달")
                    else -> null
                }
                val tag = row.tag.copy(
                    caption = (if (agent.released) "방출 · " else "") + row.tag.caption,
                    ourFormerTeam = session.userTeamId.takeIf { session.isOurFormer(agent) },
                    badges = (listOfNotNull(badge) + row.tag.badges).take(baseballgm.app.ui.MAX_STATUS_BADGES),
                )
                row.copy(tag = tag)
            },
        )
    }
}

// ---------- 트레이드 ----------

@Composable
private fun TradeTab(
    session: GameSession,
    onPlayer: (PlayerId) -> Unit,
    onCompare: (List<PlayerId>) -> Unit,
    draft: TradeDraft? = null,
) {
    // 선수 상세·비교 화면에 다녀와도 고른 상대·선수가 남아 있어야 한다 → 저장 가능한 문자열로 들고 있는다
    var partnerKey by rememberSaveable { mutableStateOf(draft?.partner?.value ?: session.league.teams.first { it.id != session.userTeamId }.id.value) }
    var givingKeys by rememberSaveable { mutableStateOf(draft?.giving.orEmpty().map { it.value }) }
    var receivingKeys by rememberSaveable { mutableStateOf(draft?.receiving.orEmpty().map { it.value }) }
    // 지명권도 저장 가능한 열쇠("시즌:라운드:원래 팀")로 들고 있는다 (2026-10-03 지명권 추가/요구)
    var givingPickKeys by rememberSaveable { mutableStateOf(draft?.givingPicks.orEmpty().map { pickKey(it) }) }
    var receivingPickKeys by rememberSaveable { mutableStateOf(draft?.receivingPicks.orEmpty().map { pickKey(it) }) }
    // 지명권 고르기 창: 어느 쪽 (true = 우리가 주는 쪽, false = 받는 쪽, null = 닫힘)
    var pickChooser by remember { mutableStateOf<Boolean?>(null) }
    val partner = TeamId(partnerKey)
    val giving = givingKeys.map { PlayerId(it) }.toSet()
    val receiving = receivingKeys.map { PlayerId(it) }.toSet()
    val givingPicks = session.picksOf(session.userTeamId).filter { pickKey(it) in givingPickKeys }
    val receivingPicks = session.picksOf(partner).filter { pickKey(it) in receivingPickKeys }
    // 현금 · 연봉 보조 (2026-10-05): 우리가 얹는 현금(억)과 보내는 선수별 연봉 보조("선수id=억")
    var cash by rememberSaveable { mutableStateOf(0.0) }
    var retainedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    val retained = retainedKeys.mapNotNull { key ->
        key.split('=').takeIf { it.size == 2 }?.let { (id, amount) -> PlayerId(id) to (amount.toDoubleOrNull() ?: 0.0) }
    }.toMap().filterKeys { it in giving }
    var verdict by remember { mutableStateOf<TradeVerdict?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    // 상대 역제안 (2026-10-05): 거절당했을 때만
    var counter by remember { mutableStateOf<baseballgm.market.TradeCounter?>(null) }
    val empty = giving.isEmpty() && receiving.isEmpty() && givingPicks.isEmpty() && receivingPicks.isEmpty() && cash <= 0.0

    val proposal = session.buildProposal(partner, giving.toList(), receiving.toList(), givingPicks, receivingPicks, cash, retained)
    // 상대 반응은 선수·지명권을 넣고 뺄 때마다 저절로 다시 본다 (미리 보기 판정 — 협상 피로도가 쌓이지 않는다, 2026-10-03 교환대)
    val preview = remember(partnerKey, givingKeys, receivingKeys, givingPickKeys, receivingPickKeys, cash, retainedKeys, session.revision) {
        if (empty) null else session.previewTrade(proposal)
    }
    // 교환대를 상대 역제안으로 바꾼다
    val loadCounter = { offer: baseballgm.market.TradeProposal ->
        givingKeys = offer.fromProposer.playerIds.map { it.value }
        receivingKeys = offer.fromPartner.playerIds.map { it.value }
        givingPickKeys = offer.fromProposer.picks.map { pickKey(it) }
        receivingPickKeys = offer.fromPartner.picks.map { pickKey(it) }
        cash = offer.fromProposer.cash
        retainedKeys = offer.fromProposer.retained.map { "${it.key.value}=${it.value}" }
        counter = null
        verdict = null
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        session.pendingTradeOffer()?.let { offer ->
            item {
                SectionCard("들어온 제안 — ${session.league.team(offer.proposer).name}") {
                    session.tradeOfferDeadlineWeek()?.let {
                        Text("${it}주차까지 답해야 해요. 지나면 제안이 철회돼요.", style = MaterialTheme.typography.labelSmall, color = AppColors.warn)
                    }
                    OfferSide(session, "우리가 받는 것", offer.fromProposer, onPlayer)
                    OfferSide(session, "우리가 주는 것", offer.fromPartner, onPlayer)
                    val ids = offer.fromProposer.playerIds + offer.fromPartner.playerIds
                    if (ids.size >= 2) {
                        OutlinedButton(onClick = { onCompare(ids.take(GameSession.MAX_COMPARE)) }, modifier = Modifier.fillMaxWidth().padding(top = AppTheme.tokens.spacing.s)) {
                            Text("제안 선수 한눈에 비교")
                        }
                    }
                    Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.s)) {
                        OutlinedButton(onClick = { result = session.acceptPendingTrade() }) { Text("수락") }
                        OutlinedButton(onClick = { session.rejectPendingTrade() }) { Text("거절") }
                    }
                }
            }
        }
        // 트레이드 소문 (2026-10-05): 최근 셋. 출처 꼬리표가 붙지만 헛소문도 섞여 있다
        val rumors = session.tradeRumors()
        if (rumors.isNotEmpty()) {
            item {
                SectionCard("트레이드 소문") {
                    rumors.take(RUMORS_SHOWN).forEach { rumor ->
                        Text("${rumor.week}주 · ${rumor.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = AppTheme.tokens.spacing.xs))
                    }
                    Text(
                        "진짜 소문은 상대가 실제로 노리는 선수예요. 다만 헛소문도 섞여 있고, 소식통이라고 다 맞는 건 아니에요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.tokens.base.textMuted,
                    )
                }
            }
        }
        // 상대 구단 고르기: 칩 아홉 개 대신 스크롤 탭 한 줄 (절제 규칙). 카드가 아니라 받을 선수 카드 위 조작부
        item {
            val others = session.league.teams.filter { it.id != session.userTeamId }
            SecondaryScrollableTabRow(
                selectedTabIndex = others.indexOfFirst { it.id == partner }.coerceAtLeast(0),
                edgePadding = AppTheme.tokens.spacing.xs,
            ) {
                others.forEach { team ->
                    Tab(
                        selected = partner == team.id,
                        onClick = {
                            partnerKey = team.id.value
                            receivingKeys = emptyList()
                            receivingPickKeys = emptyList()
                            verdict = null
                            counter = null
                        },
                        text = { Text(team.nickname) },
                    )
                }
            }
            Text(
                if (session.tradeOpen) "${session.league.team(partner).name} · ${session.teamMode(partner).label}" else "트레이드 마감이 지났어요.",
                style = MaterialTheme.typography.bodySmall,
                color = if (session.tradeOpen) AppColors.muted else AppColors.bad,
                modifier = Modifier.padding(top = AppTheme.tokens.spacing.s),
            )
            // 상대 단장 성격 (2026-10-05): 업계에 알려진 평판
            val (gmName, style) = session.tradeGm(partner)
            Text(
                "단장 $gmName · ${style.label} — ${style.description}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.tokens.base.textSecondary,
            )
        }
        item {
            ExchangeCounter(
                session = session,
                partner = partner,
                giving = giving.toList(),
                receiving = receiving.toList(),
                givingPicks = givingPicks,
                receivingPicks = receivingPicks,
                preview = preview,
                verdict = verdict,
                result = result,
                cash = cash,
                retained = retained,
                onCash = { cash = it; verdict = null },
                onRetain = { id, amount ->
                    retainedKeys = (retained - id + (id to amount)).filter { it.value > 0.0 }.map { "${it.key.value}=${it.value}" }
                    verdict = null
                },
                counter = counter,
                onAcceptCounter = { offer ->
                    val outcome = session.acceptTradeCounter(offer)
                    verdict = outcome
                    result = if (outcome.accepted) "역제안대로 거래 성사" else null
                    counter = null
                    if (outcome.accepted) loadCounter(baseballgm.market.TradeProposal(session.userTeamId, partner))
                },
                onLoadCounter = loadCounter,
                onRemove = { id ->
                    givingKeys = giving.minus(id).map { it.value }
                    receivingKeys = receiving.minus(id).map { it.value }
                    verdict = null
                },
                onRemovePick = { pick ->
                    givingPickKeys = givingPickKeys - pickKey(pick)
                    receivingPickKeys = receivingPickKeys - pickKey(pick)
                    verdict = null
                },
                onAddPick = { ours -> pickChooser = ours },
                onPropose = {
                    // 거절되면 상대 역제안을 먼저 받아 둔다 (제안 판정 전 상태로 만든다 — 피로도와 상관없이)
                    val proposed = proposal
                    val outcome = session.proposeTrade(proposed)
                    verdict = outcome
                    result = if (outcome.accepted) "거래 성사" else null
                    counter = if (!outcome.accepted && outcome.problems.isEmpty()) session.tradeCounter(proposed) else null
                    if (outcome.accepted) {
                        givingKeys = emptyList()
                        receivingKeys = emptyList()
                        givingPickKeys = emptyList()
                        receivingPickKeys = emptyList()
                        cash = 0.0
                        retainedKeys = emptyList()
                    }
                },
                onCompare = onCompare,
                onPlayer = onPlayer,
            )
        }
        // 이 거래를 하면 앞으로 몇 년 캡 여유·운용 자금이 어떻게 바뀌는가 (2026-10-04)
        if (giving.isNotEmpty() || receiving.isNotEmpty() || proposal.fromProposer.cash > 0.0) {
            item {
                val before = remember(session.revision) { session.financialPlan() }
                val after = remember(partnerKey, givingKeys, receivingKeys, session.revision) { session.financialPlan(session.tradeChange(proposal)) }
                SectionCard("이 거래를 하면") { PlanChangeTable(before, after) }
            }
        }
        // 선수 고르기: 왼쪽 우리 선수(줄 선수) | 오른쪽 상대 선수(받을 선수) — 교환대와 같은 방향 (2026-10-03)
        item {
            TradePicker(
                session = session,
                partner = partner,
                giving = giving,
                receiving = receiving,
                onToggleGive = { id ->
                    givingKeys = giving.toggle(id).map { it.value }
                    verdict = null
                },
                onToggleReceive = { id ->
                    receivingKeys = receiving.toggle(id).map { it.value }
                    verdict = null
                },
                onPlayer = onPlayer,
            )
        }
    }

    pickChooser?.let { ours ->
        val team = if (ours) session.userTeamId else partner
        val chosen = if (ours) givingPickKeys else receivingPickKeys
        PickChooser(
            session = session,
            title = if (ours) "줄 지명권 고르기" else "${session.league.team(partner).nickname}에 요구할 지명권",
            picks = session.picksOf(team),
            chosen = chosen.toSet(),
            onToggle = { pick ->
                val key = pickKey(pick)
                if (ours) {
                    givingPickKeys = if (key in givingPickKeys) givingPickKeys - key else givingPickKeys + key
                } else {
                    receivingPickKeys = if (key in receivingPickKeys) receivingPickKeys - key else receivingPickKeys + key
                }
                verdict = null
            },
            onDismiss = { pickChooser = null },
        )
    }
}

/**
 * 교환대 (2026-10-03 화면 개편 — 더쇼 프랜차이즈 트레이드 화면을 캐주얼하게).
 *
 * 왼쪽 "보내는 선수", 오른쪽 "받는 선수"를 나란히 올려 두고(누르면 빠진다), 아래에 상대 반응을 숫자 대신 칸 게이지 +
 * 비서 한마디로 보여 준다. 선수는 아래 두 목록에서 "넣기"로 올린다. 화면의 주 버튼은 "제안하기" 하나.
 */
@Composable
private fun ExchangeCounter(
    session: GameSession,
    partner: TeamId,
    giving: List<PlayerId>,
    receiving: List<PlayerId>,
    givingPicks: List<baseballgm.market.DraftPickRight>,
    receivingPicks: List<baseballgm.market.DraftPickRight>,
    preview: TradeVerdict?,
    verdict: TradeVerdict?,
    result: String?,
    cash: Double,
    retained: Map<PlayerId, Double>,
    onCash: (Double) -> Unit,
    onRetain: (PlayerId, Double) -> Unit,
    counter: baseballgm.market.TradeCounter?,
    onAcceptCounter: (baseballgm.market.TradeProposal) -> Unit,
    onLoadCounter: (baseballgm.market.TradeProposal) -> Unit,
    onRemove: (PlayerId) -> Unit,
    onRemovePick: (baseballgm.market.DraftPickRight) -> Unit,
    onAddPick: (ours: Boolean) -> Unit,
    onPropose: () -> Unit,
    onCompare: (List<PlayerId>) -> Unit,
    onPlayer: (PlayerId) -> Unit,
) {
    val tokens = AppTheme.tokens
    SectionCard("교환대 · ${session.league.team(partner).nickname}") {
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
            ExchangeColumn(session, "보내는 것", giving, givingPicks, Modifier.weight(1f), onRemove, onRemovePick) { onAddPick(true) }
            ExchangeColumn(session, "받는 것", receiving, receivingPicks, Modifier.weight(1f), onRemove, onRemovePick) { onAddPick(false) }
        }
        SectionDivider()
        // 현금 · 연봉 보조 (2026-10-05): 우리 쪽에 얹는 돈
        SweetenerRows(session, giving, cash, retained, onCash, onRetain)
        SectionDivider()
        val reaction = preview?.let { baseballgm.app.TradeReaction.of(it, session.balance) }
        if (reaction == null) {
            Text("아래에서 선수를 누르거나 지명권을 더해 보세요. 상대 반응이 바로 보여요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        } else {
            val color = when (reaction.level) {
                baseballgm.app.ReactionLevel.YES -> AppColors.good
                baseballgm.app.ReactionLevel.CLOSE, baseballgm.app.ReactionLevel.GAP -> AppColors.warn
                else -> AppColors.bad
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("상대 반응", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill(reaction.level.label, color)
            }
            Spacer(Modifier.height(tokens.spacing.s))
            // 칸 게이지: 칸 수로 말하고 색은 보조 (색만으로 구분하지 않는다)
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "상대 반응 ${reaction.cells}칸 중 ${reaction.filled}칸" },
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                repeat(reaction.cells) { index ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.weight(1f).height(tokens.sizes.meter).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (index < reaction.filled) color else tokens.base.cardInset),
                    )
                }
            }
            Spacer(Modifier.height(tokens.spacing.s))
            Row(verticalAlignment = Alignment.Top) {
                baseballgm.app.ui.SecretaryAvatar(size = tokens.sizes.numberBadge)
                Spacer(Modifier.width(tokens.spacing.s))
                Text(reaction.hint, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary, modifier = Modifier.weight(1f))
            }
        }
        // 제안 결과 (거절이면 이유, 성사면 축하)
        verdict?.takeIf { !it.accepted }?.let {
            Text("제안 거절 — ${it.reason}", style = MaterialTheme.typography.bodySmall, color = AppColors.bad, modifier = Modifier.padding(top = tokens.spacing.s))
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.good, modifier = Modifier.padding(top = tokens.spacing.s)) }
        counter?.let { CounterCard(session, it, onAcceptCounter, onLoadCounter, onPlayer) }
        Spacer(Modifier.height(tokens.spacing.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val chosen = giving + receiving
            val anything = chosen.isNotEmpty() || givingPicks.isNotEmpty() || receivingPicks.isNotEmpty()
            if (chosen.size >= 2) {
                TextButton(onClick = { onCompare(chosen.take(GameSession.MAX_COMPARE)) }) { Text("비교 보기") }
            }
            Spacer(Modifier.weight(1f))
            // 화면의 유일한 채운 버튼 (절제 규칙 "채운 버튼은 화면의 주 버튼만")
            androidx.compose.material3.Button(
                onClick = onPropose,
                enabled = session.tradeOpen && anything,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.control),
            ) { Text("제안하기") }
        }
        Text(
            "반응 게이지는 피로도가 쌓이지 않아요. 제안이 거절되면 상대의 협상 피로도가 쌓여요.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.textMuted,
            modifier = Modifier.padding(top = tokens.spacing.xs),
        )
    }
}

/**
 * 현금 · 연봉 보조 줄 (2026-10-05 유저 요청 "현금·지명권·연봉 보조를 묶음에 넣기").
 * 현금은 운용 자금에서 한 번에, 연봉 보조는 보내는 선수의 연봉 일부를 계약 끝까지 우리가 계속 낸다 (연봉 총액에 남는다)
 */
@Composable
private fun SweetenerRows(
    session: GameSession,
    giving: List<PlayerId>,
    cash: Double,
    retained: Map<PlayerId, Double>,
    onCash: (Double) -> Unit,
    onRetain: (PlayerId, Double) -> Unit,
) {
    val tokens = AppTheme.tokens
    val cashMax = minOf(session.tradeCashLimit, session.currentFunds())
    StepperRow(
        label = "현금 얹기",
        caption = "운용 자금 ${session.currentFunds().oneDecimal()}억에서 한 번에",
        value = if (cash > 0.0) "${cash.oneDecimal()}억" else "없음",
        canMinus = cash > 0.0,
        canPlus = cash + CASH_STEP <= cashMax + 1e-6,
        onMinus = { onCash((cash - CASH_STEP).coerceAtLeast(0.0).oneDecimalValue()) },
        onPlus = { onCash((cash + CASH_STEP).oneDecimalValue()) },
    )
    giving.mapNotNull { runCatching { session.player(it) }.getOrNull() }.forEach { player ->
        val amount = retained[player.id] ?: 0.0
        val max = (player.contract.salary * session.tradeRetainRate * 10).toInt() / 10.0
        StepperRow(
            label = "연봉 보조 · ${player.registeredName}",
            caption = "연봉 ${player.contract.salary.oneDecimal()}억 · ${player.contract.yearsRemaining}년 남음 · 최대 ${max.oneDecimal()}억/년",
            value = if (amount > 0.0) "${amount.oneDecimal()}억/년" else "없음",
            canMinus = amount > 0.0,
            canPlus = amount + RETAIN_STEP <= max + 1e-6,
            onMinus = { onRetain(player.id, (amount - RETAIN_STEP).coerceAtLeast(0.0).oneDecimalValue()) },
            onPlus = { onRetain(player.id, (amount + RETAIN_STEP).oneDecimalValue()) },
        )
    }
    if (giving.isNotEmpty()) {
        Text(
            "연봉 보조는 보낸 선수의 연봉 일부를 계약이 끝날 때까지 우리가 계속 내는 거예요. 상대 연봉 부담이 줄어 값을 더 쳐 줘요.",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.base.textMuted,
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    caption: String,
    value: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val tokens = AppTheme.tokens
    Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
        }
        TextButton(onClick = onMinus, enabled = canMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        TextButton(onClick = onPlus, enabled = canPlus) { Text("+") }
    }
}

/**
 * 상대 역제안 (2026-10-05 유저 요청 "그 선수는 안 되고 대신 이 선수면 된다").
 * 비서가 상대 말을 전하고, 역제안 묶음(받는 것 / 주는 것)을 보여 준다. "역제안 수락"은 바로 성사, "교환대에 올리기"는 고쳐서 다시 제안.
 */
@Composable
private fun CounterCard(
    session: GameSession,
    counter: baseballgm.market.TradeCounter,
    onAccept: (baseballgm.market.TradeProposal) -> Unit,
    onLoad: (baseballgm.market.TradeProposal) -> Unit,
    onPlayer: (PlayerId) -> Unit,
) {
    val tokens = AppTheme.tokens
    Spacer(Modifier.height(tokens.spacing.s))
    baseballgm.app.ui.SecretaryCard(counter.notes.joinToString(" "), title = "상대 역제안") {
        OfferSide(session, "우리가 받는 것", counter.proposal.fromPartner, onPlayer)
        OfferSide(session, "우리가 주는 것", counter.proposal.fromProposer, onPlayer)
        Spacer(Modifier.height(tokens.spacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            OutlinedButton(onClick = { onAccept(counter.proposal) }) { Text("역제안 수락") }
            TextButton(onClick = { onLoad(counter.proposal) }) { Text("교환대에 올리기") }
        }
    }
}

/** 교환대 한쪽: 올린 선수 정체 표시와 지명권 (누르면 빠진다) + "지명권 더하기" */
@Composable
private fun ExchangeColumn(
    session: GameSession,
    title: String,
    ids: List<PlayerId>,
    picks: List<baseballgm.market.DraftPickRight>,
    modifier: Modifier,
    onRemove: (PlayerId) -> Unit,
    onRemovePick: (baseballgm.market.DraftPickRight) -> Unit,
    onAddPick: () -> Unit,
) {
    val tokens = AppTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        if (ids.isEmpty() && picks.isEmpty()) Text("비어 있어요", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        ids.mapNotNull { runCatching { session.player(it) }.getOrNull() }.forEach { player ->
            val overall = session.scout(player).overall
            Row(
                Modifier.fillMaxWidth().clickable { onRemove(player.id) }.semantics { contentDescription = "${player.registeredName} 빼기" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIdentity(session.tagOf(player, caption = overall.toString()), Modifier.weight(1f))
                Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = null, tint = tokens.base.textMuted)
            }
        }
        picks.forEach { pick ->
            Row(
                Modifier.fillMaxWidth().clickable { onRemovePick(pick) }.semantics { contentDescription = "${pickLabel(session, pick)} 빼기" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PickBadge(pick)
                Spacer(Modifier.width(tokens.spacing.s))
                Text(pickLabel(session, pick), style = MaterialTheme.typography.bodySmall, color = tokens.base.text, modifier = Modifier.weight(1f))
                Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = null, tint = tokens.base.textMuted)
            }
        }
        TextButton(onClick = onAddPick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = tokens.spacing.xs)) {
            Text("+ 지명권")
        }
    }
}

/** 지명권 표시: 등번호 배지 자리에 라운드 글자 (원래 팀 구단 색 원) — 선수와 같은 줄 모양 */
@Composable
private fun PickBadge(pick: baseballgm.market.DraftPickRight) {
    val tokens = AppTheme.tokens
    androidx.compose.foundation.layout.Box(
        Modifier.size(tokens.sizes.numberBadge).clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.chip))
            .background(tokens.base.cardInset),
        contentAlignment = Alignment.Center,
    ) {
        Text("${pick.round}R", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tokens.base.textSecondary)
    }
}

/** "2027 1라운드", 다른 팀 지명권을 들고 있으면 "(원래 나이츠)" */
internal fun pickLabel(session: GameSession, pick: baseballgm.market.DraftPickRight): String =
    "${pick.season} ${pick.round}라운드" + if (pick.isTraded) " (원래 ${session.league.team(pick.originalTeam).nickname})" else ""

/** 지명권을 저장 가능한 글자로 (rememberSaveable 용) */
private fun pickKey(pick: baseballgm.market.DraftPickRight): String = "${pick.season}:${pick.round}:${pick.originalTeam.value}"

/**
 * 지명권 고르기 창. 그 팀이 들고 있는 지명권 전부 (시즌·라운드 순). 거래할 수 없는 지명권(다음 시즌 너머, 1라운드 연속 거래 등)도
 * 고를 수는 있다 — 교환대 상대 반응이 "규칙에 걸려요"와 이유를 보여 준다 (규칙 판정은 엔진 한 곳에서).
 */
@Composable
private fun PickChooser(
    session: GameSession,
    title: String,
    picks: List<baseballgm.market.DraftPickRight>,
    chosen: Set<String>,
    onToggle: (baseballgm.market.DraftPickRight) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = AppTheme.tokens
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (picks.isEmpty()) Text("가진 지명권이 없어요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                picks.forEach { pick ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(pick) }.padding(vertical = tokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = pickKey(pick) in chosen, onCheckedChange = { onToggle(pick) })
                        PickBadge(pick)
                        Spacer(Modifier.width(tokens.spacing.s))
                        Text(pickLabel(session, pick), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
    )
}

/**
 * 선수 고르기 (2026-10-03): 왼쪽 우리 선수(줄 선수) | 오른쪽 상대 선수(받을 선수). 교환대와 같은 방향이다.
 * 반 폭이라 표 대신 짧은 줄: 등번호 · 이름 / 포지션 · 종합(범위) · 상세(i). 줄을 누르면 교환대에 넣고 빼며, 넣은 줄은 한 단계 진한 바탕 + 체크.
 * 위의 필터(포지션·현재 종합·나이)는 양쪽에 같이 걸린다. 줄 세우기와 거르기는 스카우트 범위 중심 (불변 원칙 4).
 */
@Composable
private fun TradePicker(
    session: GameSession,
    partner: TeamId,
    giving: Set<PlayerId>,
    receiving: Set<PlayerId>,
    onToggleGive: (PlayerId) -> Unit,
    onToggleReceive: (PlayerId) -> Unit,
    onPlayer: (PlayerId) -> Unit,
) {
    val tokens = AppTheme.tokens
    // 양쪽에 같이 걸리는 필터: 포지션 · 현재 종합 · 나이 (2026-10-03 유저 요청). 스카우트 시선으로만 거른다 (불변 원칙 4)
    var filter by rememberPlayerFilter("trade-picker")
    fun playersFor(team: TeamId) = session.state.playersOf(team)
        .filter { it.military.isAvailable }
        .filter { filter.matches(session.scout(it)) }
        .sortedByDescending { session.scout(it).overall.center }
        .take(TRADE_LIST_SIZE)
    val ours = playersFor(session.userTeamId)
    val theirs = playersFor(partner)
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        PlayerFilterBar(filter, { filter = it }, resultCount = ours.size + theirs.size, showPotential = false, showAge = true)
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            PickerColumn(session, "줄 선수 · 우리 ${ours.size}", ours, giving, Modifier.weight(1f), onToggleGive, onPlayer)
            PickerColumn(session, "받을 선수 · ${session.league.team(partner).nickname} ${theirs.size}", theirs, receiving, Modifier.weight(1f), onToggleReceive, onPlayer)
        }
    }
}

@Composable
private fun PickerColumn(
    session: GameSession,
    title: String,
    players: List<Player>,
    chosen: Set<PlayerId>,
    modifier: Modifier,
    onToggle: (PlayerId) -> Unit,
    onPlayer: (PlayerId) -> Unit,
) {
    val tokens = AppTheme.tokens
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radii.card)).background(tokens.base.card)
            .padding(vertical = tokens.spacing.s),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = tokens.base.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
        )
        players.forEachIndexed { index, player ->
            val selected = player.id in chosen
            val overall = session.scout(player).overall
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) tokens.base.cardInset else if (index % 2 == 1) tokens.base.rowAlt else tokens.base.card)
                    .clickable { onToggle(player.id) }
                    .padding(start = tokens.spacing.s)
                    .semantics { contentDescription = "${player.registeredName} ${if (selected) "빼기" else "넣기"}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBadge(player.uniformNumber.takeIf { it > 0 }, player.teamId)
                Spacer(Modifier.width(tokens.spacing.s))
                Column(Modifier.weight(1f).padding(vertical = tokens.spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null, tint = tokens.base.brand, modifier = Modifier.size(tokens.sizes.statusIcon))
                            Spacer(Modifier.width(tokens.spacing.xs))
                        }
                        Text(player.registeredName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tokens.base.text, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PositionBadge(session.scout(player).positionLabel)
                        Spacer(Modifier.width(tokens.spacing.xs))
                        Text(overall.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = tokens.grade.of(overall.center), maxLines = 1)
                    }
                }
                androidx.compose.material3.IconButton(onClick = { onPlayer(player.id) }, modifier = Modifier.size(tokens.sizes.numberBadge + tokens.spacing.s)) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Info, contentDescription = "${player.registeredName} 상세", tint = tokens.base.textMuted)
                }
            }
        }
    }
}

/** 들어온 제안 한쪽: 선수마다 한 줄(누르면 상세), 지명권·현금은 글로 */
@Composable
private fun OfferSide(session: GameSession, title: String, pack: baseballgm.market.TradePackage, onPlayer: (PlayerId) -> Unit) {
    val tokens = AppTheme.tokens
    Text(title, style = MaterialTheme.typography.labelMedium, color = tokens.base.textSecondary, modifier = Modifier.padding(top = AppTheme.tokens.spacing.s))
    pack.playerIds.mapNotNull { runCatching { session.player(it) }.getOrNull() }.forEach { player ->
        val scouted = session.scout(player)
        PlayerLine(
            session.tagOf(player, caption = "${scouted.age}세 · ${player.contract.salary.oneDecimal()}억×${player.contract.yearsRemaining}년 · 잠재 ${scouted.potentialLabel}"),
            onClick = { onPlayer(player.id) },
        ) {
            Text(
                scouted.overall.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.grade.of(scouted.overall.center),
            )
        }
    }
    val extras = buildList {
        pack.picks.forEach { add(pickLabel(session, it)) }
        if (pack.cash > 0.0) add("현금 ${pack.cash.oneDecimal()}억")
        pack.retained.filter { it.value > 0.0 }.forEach { (id, amount) -> add("${session.nameOf(id)} 연봉 ${amount.oneDecimal()}억/년 보조") }
    }
    if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TeamModeTab(session: GameSession) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("우리 구단") {
                StatRow("구단 모드", session.teamMode().label)
                StatRow("연봉 총액", "${session.league.payrollOf(session.userTeamId).oneDecimal()}억")
                StatRow("소프트캡", "${session.salaryCap.oneDecimal()}억")
                StatRow("운용 자금", "${session.currentFunds().oneDecimal()}억")
                StatRow("보유 지명권", "${session.myPicksForTrade().size}장")
            }
        }
        item {
            SectionCard("연봉 계획") { FinancePlanSummary(session.financialPlan()) }
        }
        items(session.league.teams.filter { it.id != session.userTeamId }) { team ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(AppTheme.tokens.spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(team.name, fontWeight = FontWeight.Bold)
                        Text(
                            "연봉 ${session.league.payrollOf(team.id).oneDecimal()}억 · 지명권 ${session.picksOf(team.id).size}장",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill(session.teamMode(team.id).label)
                }
            }
        }
    }
}

/** 트레이드 한쪽 목록에 보여줄 최대 인원. 필터를 건 뒤 종합 순으로 자른다 */
private const val TRADE_LIST_SIZE = 40

private fun Set<PlayerId>.toggle(id: PlayerId): Set<PlayerId> = if (id in this) this - id else this + id

private fun Double.oneDecimal(): String = ((this * 10).roundToInt() / 10.0).toString()

private fun Double.oneDecimalValue(): Double = (this * 10).roundToInt() / 10.0

private fun Float.oneDecimal(): String = this.toDouble().oneDecimal()

// ---------- 외국인 (docs/12) ----------

/**
 * 외국인 선수 화면.
 *
 * 세 가지를 한 화면에서 본다.
 * ① 지금 보유한 3명의 **적응 상태** — 능력치가 좋아도 성적이 안 나오는 이유가 여기 있다
 * ② 시장 후보와 **KBO 환산 기록** (환산 오차가 섞여 있다)
 * ③ 시즌 중 교체 — 횟수와 마감일이 있고, 내보내는 선수의 잔여 연봉을 물어야 한다
 */
@Composable
private fun ForeignTab(session: GameSession) {
    var outgoing by remember { mutableStateOf<PlayerId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by rememberPlayerFilter("foreign")
    val mine = session.foreigners()
    val allCandidates = session.foreignCandidates()
    val candidates = allCandidates.filter { filter.matches(session.foreignReport(it).scouted, it.askingSalary) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = AppTheme.tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.tokens.spacing.m),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = AppTheme.tokens.spacing.s),
    ) {
        item {
            SectionCard("보유 외국인 ${mine.size}/${session.foreignMaxPerTeam}") {
                Text(
                    if (session.foreignReplacementOpen) {
                        if (session.hasOpenForeignSlot()) {
                            "빈 자리 ${session.foreignMaxPerTeam - mine.size}개 — 바로 영입할 수 있어요 · 시즌 중 영입 가능 ${session.foreignReplacementsLeft()}회"
                        } else {
                            "교체 가능 ${session.foreignReplacementsLeft()}회 · 내보낼 선수를 고르면 잔여 연봉을 지급해요"
                        }
                    } else {
                        "외국인 교체 마감이 지났어요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.foreignReplacementOpen) MaterialTheme.colorScheme.onSurfaceVariant else AppColors.bad,
                )
                Spacer(Modifier.height(AppTheme.tokens.spacing.s))
                mine.forEach { player -> ForeignHeldRow(session, player, outgoing) { outgoing = it } }
                if (mine.isEmpty()) Text("보유한 외국인 선수가 없어요.", style = MaterialTheme.typography.bodySmall)
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.warn) } }
        item {
            SectionCard("외국인 시장 ${allCandidates.size}명") {
                Text(
                    "환산 기록은 출신 리그 성적을 KBO 기준으로 옮긴 값이라 오차가 있어요. " +
                        "집중 관찰을 붙이면 능력치 범위가 좁아져요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            PlayerFilterBar(filter, { filter = it }, resultCount = candidates.size, showSalary = true)
        }
        if (candidates.isNotEmpty()) {
            item {
                val byId = candidates.associateBy { it.id }
                PlayerList(
                    listOf(foreignSection(session, candidates)),
                    onPlayer = {},
                    action = { row ->
                        val candidate = byId.getValue(row.id)
                        if (!session.foreignReplacementOpen || session.foreignReplacementsLeft() <= 0) {
                            null
                        } else {
                            RowAction(if (outgoing == null && session.hasOpenForeignSlot()) "영입" else "교체") {
                                val target = outgoing?.let { id -> session.foreigners().firstOrNull { it.id == id } }
                                message = if (target == null && !session.hasOpenForeignSlot()) {
                                    "외국인 자리가 꽉 찼어요. 내보낼 선수를 먼저 고르세요"
                                } else {
                                    session.replaceForeign(target, candidate).firstOrNull()
                                        ?: if (target == null) {
                                            "${candidate.player.registeredName} 영입 완료 — 빈 자리를 채웠어요"
                                        } else {
                                            "${target.registeredName} 대신 ${candidate.player.registeredName} 영입 완료"
                                        }
                                }
                                outgoing = null
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 보유 외국인 한 줄. 누르면 "내보낼 선수"로 고른다 (교체 영입의 짝) */
@Composable
private fun ForeignHeldRow(
    session: GameSession,
    player: Player,
    outgoing: PlayerId?,
    onSelect: (PlayerId?) -> Unit,
) {
    val scouted = session.scout(player)
    val chosen = outgoing == player.id
    PlayerLine(
        session.tagOf(
            player,
            caption = "${player.contract.salary.oneDecimal()}억 · ${session.adaptationLabel(player) ?: ""} · 잔여 ${session.foreignBuyout(player).oneDecimal()}억",
        ),
        onClick = null,
        modifier = Modifier.clickable { onSelect(if (chosen) null else player.id) },
    ) {
        Text(
            scouted.overall.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = AppTheme.tokens.grade.of(scouted.overall.center),
        )
        Checkbox(checked = chosen, onCheckedChange = { onSelect(if (it) player.id else null) })
    }
}

/**
 * 외국인 시장 목록. 능력치는 외국인 스카우트 리포트(관찰하면 좁아지는 범위)만 쓴다.
 * 열: 종합 · 출신 · 요구 연봉 · 환산 기록 · 관찰 버튼. 줄 끝 "영입" = 고른 보유 선수와 맞바꾸기.
 */
private fun foreignSection(session: GameSession, candidates: List<ForeignCandidate>): baseballgm.app.ui.PlayerListSection =
    baseballgm.app.ui.PlayerListSection(
        "후보",
        listOf(
            baseballgm.app.ui.TableColumn("종합", 60.dp),
            baseballgm.app.ui.TableColumn("출신", 64.dp),
            baseballgm.app.ui.TableColumn("요구", 56.dp),
            baseballgm.app.ui.TableColumn("환산 기록", 180.dp),
            baseballgm.app.ui.TableColumn("관찰", 64.dp),
        ),
        candidates.map { candidate ->
            val report = session.foreignReport(candidate)
            val scouted = report.scouted
            val focused = session.isFocused(candidate)
            baseballgm.app.ui.PlayerListRow(
                baseballgm.app.ui.PlayerTag(
                    id = candidate.id,
                    name = candidate.player.registeredName,
                    number = null,
                    teamId = null,
                    position = scouted.positionLabel,
                    caption = "${scouted.age}세",
                    badges = if (focused) listOf(StatusBadge(PlayerStatus.WATCHING, "집중 관찰 중")) else emptyList(),
                ),
                listOf(
                    ListCell.Rating(scouted.overall, emphasized = true),
                    ListCell.Value(candidate.originLabel),
                    ListCell.Value("${candidate.askingSalary.oneDecimal()}억"),
                    ListCell.Value(session.convertedLine(candidate).text()),
                    ListCell.Action(if (focused) "해제" else "관찰+", active = focused) { session.toggleFocus(candidate) },
                ),
            )
        },
    )

/** 교환대 현금 조절 단위(억) */
private const val CASH_STEP = 1.0

/** 연봉 보조 조절 단위(억/년) */
private const val RETAIN_STEP = 0.5

/** 트레이드 탭에 보여 줄 최근 소문 수 */
private const val RUMORS_SHOWN = 3
