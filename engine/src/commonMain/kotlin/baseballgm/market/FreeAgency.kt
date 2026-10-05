package baseballgm.market

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.Standings
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.Origin
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.season.withContract
import baseballgm.season.withTeam
import baseballgm.util.chance
import baseballgm.util.interpolateAnchors
import baseballgm.util.nextInRange
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** FA 등급 (docs/11). 연봉 순위로 나눈다. 등급이 높을수록 영입 팀의 보상 부담이 크다. */
enum class FaGrade { A, B, C }

/** 시장에 나온 FA 한 명. */
data class FreeAgent(
    val playerId: PlayerId,
    val previousTeam: TeamId,
    val grade: FaGrade,
    /** 희망 연봉(억원). 관심 구단이 많을수록 오른다 */
    val askingSalary: Double,
    val askingYears: Int,
    val interestedTeams: Int = 0,
    /** 방출 선수인가. 계약이 끝나 풀린 FA 와 달리, 아무도 데려가지 않으면 리그를 떠난다 */
    val released: Boolean = false,
    /**
     * 최저 연봉(억원, 2026-10-04). 직전 시즌 WAR 의 시장가 × `salaryFloorRateOfWar`. 이보다 낮은 조건(계약금은 연으로 나눠 더함)은
     * **마지막 라운드에도 받지 않는다** — 경쟁 없이 버티면 헐값에 잡히던 문제를 막는다
     */
    val salaryFloor: Double = 0.0,
    /** 직전 시즌 1군 WAR. 1군 기록이 없으면 null */
    val lastWar: Double? = null,
) {
    fun label(name: String): String = "$name ${grade}등급 · 희망 ${format(askingSalary)}억 × ${askingYears}년"

    private fun format(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
}

/** 구단이 FA 에게 내민 조건. */
data class ContractOffer(
    val teamId: TeamId,
    val playerId: PlayerId,
    val salary: Double,
    val years: Int,
    val signingBonus: Double = 0.0,
    /**
     * 옵션 조항 (2026-10-04 다양화). 조항마다 기준을 달성한 시즌에 그 금액을 준다. 선수는 달성 가능성만큼만 쳐 준다
     * ([OptionRules.valueToPlayer]). AI 구단은 쓰지 않는다
     */
    val options: List<OptionClause> = emptyList(),
) {
    /** 보장 총액 (옵션 제외) */
    val totalValue: Double get() = salary * years + signingBonus

    /** 한 해 옵션을 다 달성하면 받는 돈 */
    val optionPerYear: Double get() = options.sumOf { it.amount }

    /** 옵션까지 다 받았을 때 총액 */
    val maxValue: Double get() = totalValue + optionPerYear * years
}

/** FA 영입에 따른 보상 (docs/11). C등급은 보상선수가 없다. */
data class FaCompensation(
    val toTeam: TeamId,
    val fromTeam: TeamId,
    val cash: Double,
    val playerId: PlayerId?,
)

/** 계약 성사 한 건. */
data class FaSigning(
    val playerId: PlayerId,
    val offer: ContractOffer,
    val previousTeam: TeamId,
    val compensation: FaCompensation?,
)

/** 한 주(라운드)의 결과. 알림함에 그대로 올릴 수 있게 문장도 들고 있다. */
data class FaRoundReport(
    val round: Int,
    val signings: List<FaSigning>,
    val messages: List<String>,
    val remaining: Int,
    /** 유저 구단과 관련된 소식 ("우리 제안이 밀렸어요" 등). 화면이 맨 위에 따로 보여 준다 */
    val userMessages: List<String> = emptyList(),
)

/** 입찰 흐름 한 줄의 종류. */
enum class FaBidKind(val label: String) {
    /** 처음 조건을 냈다 */
    JOINED("참전"),

    /** 조건을 올렸다 */
    RAISED("조건 상향"),

    /** 조건을 바꿨다 (내리거나 기간만 바꿈) */
    REVISED("조건 수정"),

    /** 더 못 올리고 물러났다 */
    WITHDREW("철수"),
}

/**
 * 선수별 입찰 흐름 한 줄. **금액은 담지 않는다** — 타 구단 제시액은 에이전트 소문으로만 들린다 (docs/11).
 */
data class FaBidEvent(val round: Int, val teamId: TeamId, val kind: FaBidKind)

/** 우리 제안이 지금 어디에 서 있는가. */
enum class FaStanding {
    /** 아직 조건을 내지 않았다 */
    NO_OFFER,

    /** 우리가 1순위이고 선수도 만족한다 — 다음 진행 때 도장을 찍을 수 있다 */
    LEADING,

    /** 우리가 1순위지만 선수가 아직 망설인다 — 마지막 라운드까진 더 기다린다 */
    LEADING_WAITING,

    /** 다른 구단 조건이 더 낫다 */
    OUTBID,

    /** 우리 조건이 최저 연봉에 못 미친다 — 선수가 쳐다보지 않는다 (2026-10-04) */
    BELOW_FLOOR,
}

/**
 * 한 FA 를 두고 벌어지는 협상 현황 (유저 구단 시점).
 *
 * 화면이 "지금 이기고 있나, 다음 주에 계약할까, 얼마를 더 써야 하나"를 한눈에 보여 주는 데 필요한 값만 담는다.
 * 타 구단의 정확한 금액은 담지 않는다 — [rivals] 는 이름만, 금액은 소문([FreeAgencyMarket.rumor])으로만.
 */
data class FaNegotiation(
    val playerId: PlayerId,
    val standing: FaStanding,
    val ourOffer: ContractOffer?,
    /** 선수가 우리 조건을 얼마나 마음에 들어 하는가. 1.0 이면 도장 찍을 만큼 만족 (0 이상) */
    val satisfaction: Double,
    /** 지금 조건을 낸 다른 구단 */
    val rivals: List<TeamId>,
    /** 다음 진행 때 우리와 계약할 확률 */
    val ourSignChance: Double,
    /** 다음 진행 때 다른 구단과 계약해 버릴 확률 */
    val rivalSignChance: Double,
    /** 지금 기간·계약금 그대로 1순위가 되려면 필요한 연봉(억). 이미 1순위거나 연봉만으로 안 되면 null */
    val salaryToLead: Double?,
    /** 지금 기간·계약금 그대로 선수를 만족시키려면 필요한 연봉(억). 연봉만으로 안 되면 null */
    val salaryToSatisfy: Double?,
    /** 이번이 마지막 라운드인가 (진행하면 최고 조건에 무조건 도장) */
    val lastRound: Boolean,
    /** 최저 연봉. 연봉 + 계약금(연 환산)이 이보다 낮으면 선수가 받지 않는다 */
    val salaryFloor: Double,
    /** 최저 연봉의 근거인 직전 시즌 1군 WAR (기록이 없으면 null) */
    val lastWar: Double?,
    val history: List<FaBidEvent>,
)

/**
 * FA 시장 진행 상태.
 *
 * 스토브리그를 여러 라운드로 나눠 진행한다 (docs/11 "스토브리그를 주차로 나눠 매주 제안 → 응답").
 * **제안은 라운드가 지나도 남는다** — 그래서 라운드마다 "누가 1순위인가"가 이어지고,
 * 밀린 구단이 조건을 올리거나 물러나는 경쟁 입찰이 생긴다.
 */
class FreeAgencyState(val season: Int, agents: List<FreeAgent>) {
    private val pool = agents.associateBy { it.playerId }.toMutableMap()
    private val offers = mutableMapOf<PlayerId, MutableMap<TeamId, ContractOffer>>()
    private val withdrawn = mutableMapOf<PlayerId, MutableSet<TeamId>>()
    private val events = mutableMapOf<PlayerId, MutableList<FaBidEvent>>()

    var round: Int = 1
        internal set

    val signings: MutableList<FaSigning> = mutableListOf()

    /** 협상 테이블 대화 (2026-10-04). 유저 구단만 쓴다. 선수별로 오래된 것부터 */
    internal val talkLog: MutableMap<PlayerId, MutableList<FaTalkLine>> = mutableMapOf()

    /** 협상 테이블에서 이번 라운드에 깎인 에이전트 인내심: 선수 → (라운드, 깎인 양). 라운드가 바뀌면 다시 찬다 */
    internal val patienceSpent: MutableMap<PlayerId, Pair<Int, Int>> = mutableMapOf()

    fun talksOf(playerId: PlayerId): List<FaTalkLine> = talkLog[playerId].orEmpty()

    val remaining: List<FreeAgent> get() = pool.values.sortedByDescending { it.askingSalary }

    fun agent(playerId: PlayerId): FreeAgent? = pool[playerId]

    fun offersFor(playerId: PlayerId): List<ContractOffer> = offers[playerId]?.values?.toList().orEmpty()

    fun offerOf(teamId: TeamId, playerId: PlayerId): ContractOffer? = offers[playerId]?.get(teamId)

    /** 이 구단이 지금 낸 모든 제안 (예산 계산용) */
    fun offersBy(teamId: TeamId): List<ContractOffer> = offers.values.mapNotNull { it[teamId] }

    /** 선수별 입찰 흐름. 오래된 것부터 */
    fun historyOf(playerId: PlayerId): List<FaBidEvent> = events[playerId].orEmpty()

    /** 이 선수에게서 물러난 구단. 물러난 AI 구단은 다시 붙지 않는다 */
    fun withdrawnFrom(playerId: PlayerId): Set<TeamId> = withdrawn[playerId].orEmpty()

    /**
     * 한 구단은 한 선수에게 하나의 조건만 유지한다 (고쳐서 다시 낼 수는 있다).
     * 연봉이나 기간이 0 이하면 철회로 본다.
     */
    fun putOffer(offer: ContractOffer) {
        if (offer.playerId !in pool) return
        if (offer.salary <= 0.0 || offer.years <= 0) {
            withdraw(offer.teamId, offer.playerId)
            return
        }
        val previous = offerOf(offer.teamId, offer.playerId)
        offers.getOrPut(offer.playerId) { mutableMapOf() }[offer.teamId] = offer
        withdrawn[offer.playerId]?.remove(offer.teamId)
        val kind = when {
            previous == null -> FaBidKind.JOINED
            offer.salary > previous.salary -> FaBidKind.RAISED
            else -> FaBidKind.REVISED
        }
        record(offer.playerId, offer.teamId, kind)
    }

    /** 제안을 거둔다. 낸 적이 없으면 아무 일도 없다 */
    fun withdraw(teamId: TeamId, playerId: PlayerId) {
        if (offers[playerId]?.remove(teamId) == null) return
        withdrawn.getOrPut(playerId) { mutableSetOf() } += teamId
        record(playerId, teamId, FaBidKind.WITHDREW)
    }

    /**
     * 흐름을 적는다. 같은 라운드에 같은 구단이 여러 번 고치면 마지막 것만 남긴다
     * (유저가 슬라이더를 여러 번 움직여도 타임라인이 지저분해지지 않게). 단 처음 참전은 "참전"으로 남긴다.
     */
    private fun record(playerId: PlayerId, teamId: TeamId, kind: FaBidKind) {
        val list = events.getOrPut(playerId) { mutableListOf() }
        val last = list.lastOrNull()
        if (last != null && last.round == round && last.teamId == teamId) {
            list.removeAt(list.lastIndex)
            val merged = when {
                kind == FaBidKind.WITHDREW && last.kind == FaBidKind.JOINED -> null // 같은 라운드에 냈다가 거둠 → 없던 일
                last.kind == FaBidKind.JOINED && kind != FaBidKind.WITHDREW -> FaBidKind.JOINED
                else -> kind
            }
            if (merged != null) list += FaBidEvent(round, teamId, merged)
            return
        }
        list += FaBidEvent(round, teamId, kind)
    }

    internal fun update(agent: FreeAgent) {
        pool[agent.playerId] = agent
    }

    internal fun remove(playerId: PlayerId) {
        pool.remove(playerId)
        offers.remove(playerId)
    }

    val isClosed: Boolean get() = pool.isEmpty()
}

/**
 * FA 시장 (docs/11).
 *
 * **경쟁 입찰이 핵심이다.** 희망 조건은 시장가에서 출발해 관심 구단 수에 비례해 오르고,
 * 각 구단은 자기 가치 평가로 정한 상한을 넘으면 물러난다. 그래서 좋은 선수에게 여러 팀이 붙으면
 * 값이 치솟고, 아무도 안 붙으면 값이 내려간다.
 *
 * AI 가 호구가 되지 않게 하는 장치는 트레이드와 같다 — **상한은 자기 평가 가치**이고,
 * 그 평가에는 스카우트 오차가 섞인다.
 */
class FreeAgencyMarket(
    private val balance: BalanceConfig,
    private val valuation: Valuation,
    private val marketView: MarketView,
    private val positionNeed: PositionNeed,
    private val modeResolver: TeamModeResolver,
) {
    private val section = balance.section("faMarket")
    private val rules = balance.section("freeAgency")
    val rounds: Int = section.int("biddingRounds")
    private val askingPremium = section.double("askingPremium")
    private val competitionRaise = section.double("competitionRaisePerTeam")
    private val askingDrop = section.double("askingDropPerRound")
    private val maxYearsByAge = section.numericMap("maxYearsByAge")
    private val oldFromAge = section.int("oldFromAge")
    private val signThreshold = section.double("signThreshold")
    private val signChance = section.doubleList("signChancePerRound")
    private val leftoverRate = section.double("leftoverSalaryRate")
    private val budgetShare = section.double("aiBudgetShareOfFunds")
    private val rebuildMaxAge = section.int("rebuildMaxAge")
    private val overCapPenalty = section.double("overCapPenalty")
    private val salaryCap = balance.double("softCap.cap")
    private val faMoraleSwing = balance.double("morale.fa.moraleSwing")
    private val optionRules = OptionRules(balance)
    private val rumorRange = section.doubleRange("rumorExaggeration")
    private val bidIncrement = section.double("bidIncrement")
    private val valueMargin = section.double("aiValueMargin")
    private val payrollCeiling = section.double("aiPayrollCeilingOfCap")
    private val floorRate = section.double("salaryFloorRateOfWar")
    private val minimumSalary = balance.double("minimumSalary.value")
    private val maxContractYears = rules.int("maxContractYears")
    private val aTopRank = rules.int("gradeBySalaryRank.aTopRank")
    private val bTopRank = rules.int("gradeBySalaryRank.bTopRank")

    // ---------- 개장 ----------

    /**
     * 이번 스토브리그의 FA 명단.
     *
     * 계약이 끝나 풀린 선수와 **방출 선수**가 함께 올라온다 (docs/11 "방출: 다른 팀이 영입 가능").
     * 둘의 차이는 시장이 닫힐 때 드러난다 — FA 는 헐값에라도 원소속팀에 남고, 방출 선수는 떠난다.
     */
    fun open(
        league: League,
        standings: Standings,
        random: Random,
        released: Set<PlayerId> = emptySet(),
        /** 유저 구단. AI 가 대신 입찰하지 않는다 */
        userTeam: TeamId? = null,
    ): FreeAgencyState {
        val salaryRank = league.players
            .filter { it.military.isAvailable }
            .sortedByDescending { it.contract.salary }
            .mapIndexed { index, player -> player.id to index + 1 }
            .toMap()

        val agents = league.freeAgentPool.mapNotNull { playerId ->
            val player = league.players.firstOrNull { it.id == playerId } ?: return@mapNotNull null
            val previous = player.contract.let { league.teams.firstOrNull { team -> team.id == playerLastTeam(league, playerId) } }
                ?: return@mapNotNull null
            val estimate = marketView.estimate(player, previous.id)
            val war = valuation.expectedWar(player, estimate.overall)
            val lastWar = league.history.careerOf(playerId).lastOrNull { it.season == league.season - 1 }?.war
            val floor = round1(max(minimumSalary, (lastWar ?: 0.0) * valuation.salaryPerWar * floorRate))
            FreeAgent(
                playerId = playerId,
                previousTeam = previous.id,
                grade = if (playerId in released) FaGrade.C else gradeOf(salaryRank[playerId] ?: Int.MAX_VALUE),
                // 희망 연봉은 최저 연봉보다 낮게 부르지 않는다
                askingSalary = max(floor, max(minimumSalary, war * valuation.salaryPerWar * askingPremium)),
                askingYears = if (playerId in released) 1 else maxYearsFor(player, league.season),
                released = playerId in released,
                salaryFloor = floor,
                lastWar = lastWar,
            )
        }
        // 개장과 함께 AI 구단이 첫 조건을 낸다. 그래서 유저는 1라운드부터 경쟁 상황을 보고 조건을 낸다
        return FreeAgencyState(league.season, agents).also { state ->
            aiBidding(state, league, standings, random, userTeam)
            refreshInterest(state)
        }
    }

    private fun playerLastTeam(league: League, playerId: PlayerId): TeamId? =
        league.faOrigins[playerId]

    fun gradeOf(salaryRank: Int): FaGrade = when {
        salaryRank <= aTopRank -> FaGrade.A
        salaryRank <= bTopRank -> FaGrade.B
        else -> FaGrade.C
    }

    internal fun maxYearsFor(player: Player, season: Int): Int {
        val age = player.ageIn(season)
        return interpolateAnchors(maxYearsByAge, age.toDouble()).roundToInt().coerceIn(1, maxContractYears)
    }

    // ---------- 라운드 ----------

    /**
     * 한 라운드를 돌린다.
     *
     * ① **선수가 결정한다** — 지금 들어와 있는 조건(지난 라운드 입찰 + 유저가 이번에 낸 조건) 중 가장 마음에 드는
     *    것이 만족 기준을 넘으면 라운드별 확률로 도장을 찍는다. 마지막 라운드에서는 최고 조건에 무조건 찍는다.
     * ② **AI 구단이 응수한다** — 1순위에서 밀린 구단은 조건을 올리거나(상한 안에서) 물러난다.
     *    처음 보는 선수에게는 새로 참전한다.
     * ③ 희망 조건을 고친다 — 아무도 안 붙으면 내리고, 여럿이 붙으면 (최고 제시액까지) 올린다.
     *
     * 결정이 응수보다 먼저라서, 유저가 화면에서 본 "1순위/밀림"이 곧 다음 진행 때 선수가 보는 판이다.
     */
    fun runRound(
        state: FreeAgencyState,
        league: League,
        standings: Standings,
        random: Random,
        /** 소식을 따로 모아 줄 유저 구단 */
        userTeam: TeamId? = null,
    ): FaRoundReport {
        val messages = mutableListOf<String>()
        val userMessages = mutableListOf<String>()
        val signings = mutableListOf<FaSigning>()
        val lastRound = state.round >= rounds

        // ① 선수 결정
        state.remaining.forEach { agent ->
            val player = league.player(agent.playerId)
            val ranked = ranked(state, agent, league, standings)
            val (best, bestScore) = ranked.firstOrNull() ?: return@forEach
            val satisfied = bestScore >= signThreshold
            val willing = lastRound || (satisfied && random.chance(signChanceOf(state.round)))
            if (!willing) return@forEach

            val signing = FaSigning(
                playerId = agent.playerId,
                offer = best,
                previousTeam = agent.previousTeam,
                compensation = if (agent.released) null else compensationFor(league, agent, best.teamId, player),
            )
            val ourOffer = userTeam?.let { state.offerOf(it, agent.playerId) }
            signings += signing
            state.signings += signing
            state.remove(agent.playerId)
            messages += "${player.registeredName} → ${league.team(best.teamId).name} " +
                "(${format(best.salary)}억 × ${best.years}년, ${agent.grade}등급)"
            when {
                best.teamId == userTeam -> userMessages += "${player.registeredName} 영입 성공! ${format(best.salary)}억 × ${best.years}년에 도장 찍었어요."
                ourOffer != null -> userMessages += "${player.registeredName} 놓쳤어요. ${league.team(best.teamId).name} 조건을 골랐대요."
            }
        }

        if (!lastRound) {
            // 응수 전 1순위를 기억해 둔다 — 유저가 밀렸는지 알려 주려고
            val leadersBefore = state.remaining.associate { agent ->
                agent.playerId to ranked(state, agent, league, standings).firstOrNull()?.first?.teamId
            }
            // ② AI 응수
            aiBidding(state, league, standings, random, userTeam)

            // ③ 희망 조건
            state.remaining.forEach { agent ->
                val offers = state.offersFor(agent.playerId)
                val player = league.player(agent.playerId)
                if (offers.isEmpty()) {
                    // 아무도 안 붙으면 눈높이를 낮춘다. 단 최저 연봉 아래로는 안 내려간다
                    state.update(agent.copy(askingSalary = max(agent.salaryFloor, max(minimumSalary, agent.askingSalary * (1.0 - askingDrop)))))
                } else if (offers.size >= 2) {
                    // 경쟁이 붙으면 눈높이가 오른다. 단 실제 최고 제시액보다 높게 부르지는 않는다
                    val raised = agent.askingSalary * (1.0 + competitionRaise * (offers.size - 1))
                    val ceiling = max(agent.askingSalary, offers.maxOf { it.salary })
                    state.update(agent.copy(askingSalary = min(raised, ceiling)))
                    if (offers.size >= RUMOR_INTEREST) messages += "${player.registeredName}: ${offers.size}개 구단 경쟁"
                }

                if (userTeam == null) return@forEach
                val ours = state.offerOf(userTeam, agent.playerId) ?: return@forEach
                val now = state.agent(agent.playerId) ?: return@forEach
                val leaderNow = ranked(state, now, league, standings).firstOrNull()?.first?.teamId
                val history = state.historyOf(agent.playerId).filter { it.round == state.round && it.teamId != userTeam }
                if (leadersBefore[agent.playerId] == userTeam && leaderNow != userTeam && leaderNow != null) {
                    userMessages += "${player.registeredName}: ${league.team(leaderNow).name}이(가) 더 좋은 조건을 내서 우리가 밀렸어요."
                } else if (ours.teamId == leaderNow) {
                    val dropped = history.filter { it.kind == FaBidKind.WITHDREW }.map { league.team(it.teamId).name }
                    if (dropped.isNotEmpty()) userMessages += "${player.registeredName}: ${dropped.joinToString()} 철수. 우리가 1순위예요."
                }
            }
        }

        refreshInterest(state)
        state.round += 1
        return FaRoundReport(state.round - 1, signings, messages, state.remaining.size, userMessages)
    }

    /** 연봉 + 계약금(연 환산)이 최저 연봉 이상인가 */
    fun meetsFloor(offer: ContractOffer, agent: FreeAgent): Boolean =
        offer.salary + offer.signingBonus / max(1, offer.years) + SALARY_EPSILON >= agent.salaryFloor

    /** 이 라운드에 만족한 선수가 도장을 찍을 확률 */
    internal val signThresholdValue: Double get() = signThreshold

    /** 돈 점수 상한 (희망 연봉의 몇 배까지 돈으로 쳐 주나) */
    internal val moneyCap: Double get() = MONEY_CAP

    /**
     * 협상 테이블에서 합의한 조건으로 **지금 바로** 도장을 찍는다 (2026-10-04 혼합형).
     * 라운드 진행 때의 계약 성사와 같다 — 보상도 같은 식으로 정하고, 시장이 닫힐 때 계약이 적용된다.
     */
    fun signNow(state: FreeAgencyState, offer: ContractOffer, league: League): FaSigning? {
        val agent = state.agent(offer.playerId) ?: return null
        val player = league.player(offer.playerId)
        val signing = FaSigning(
            playerId = offer.playerId,
            offer = offer,
            previousTeam = agent.previousTeam,
            compensation = if (agent.released) null else compensationFor(league, agent, offer.teamId, player),
        )
        state.signings += signing
        state.remove(offer.playerId)
        return signing
    }

    private fun signChanceOf(round: Int): Double = signChance[min(round - 1, signChance.lastIndex).coerceAtLeast(0)]

    /** 들어온 조건을 선수가 좋아하는 순서로 */
    internal fun ranked(
        state: FreeAgencyState,
        agent: FreeAgent,
        league: League,
        standings: Standings,
        /** 이 구단 조건을 바꿔 넣고 본다 (역제안처럼 "이 조건이면?"을 물을 때) */
        override: ContractOffer? = null,
    ): List<Pair<ContractOffer, Double>> {
        val player = league.player(agent.playerId)
        val offers = state.offersFor(agent.playerId).filter { it.teamId != override?.teamId } + listOfNotNull(override)
        return offers
            // 최저 연봉에 못 미치는 조건은 선수가 쳐다보지도 않는다 (마지막 라운드에도)
            .filter { meetsFloor(it, agent) }
            .map { it to score(player, it, league, standings, agent) }
            // 점수가 같으면 구단 id 순 — 맵 순서에 결과가 흔들리지 않게
            .sortedWith(compareByDescending<Pair<ContractOffer, Double>> { it.second }.thenBy { it.first.teamId.value })
    }

    private fun refreshInterest(state: FreeAgencyState) {
        state.remaining.forEach { agent -> state.update(agent.copy(interestedTeams = state.offersFor(agent.playerId).size)) }
    }

    /**
     * AI 구단의 입찰 (경쟁 입찰의 핵심).
     *
     * - **1순위면 가만히 있는다.** 굳이 자기 조건을 올리지 않는다.
     * - **밀렸으면 1순위 조건보다 [bidIncrement] 만큼 더 부른다.** 단 상한(자기 가치 평가)을 넘으면 **물러난다(철수)**.
     *   물러난 구단은 그 선수에게 다시 붙지 않는다.
     * - **아직 아무 조건도 없는 선수**에게는 포지션이 필요하고 상한이 희망 연봉 이상일 때 참전한다.
     *
     * 구단 순서는 라운드마다 섞는다. 앞 구단의 조건을 뒤 구단이 보고 응수하므로, 순서가 고정이면 늘 같은 팀이 유리하다.
     * 예산은 이미 낸 제안까지 합쳐서 본다 — 여러 선수에게 동시에 돈을 걸 수 없다.
     * 연봉은 **연봉 총액이 샐러리캡의 [payrollCeiling] 배를 넘지 않는 만큼**, 계약금은 운용 자금 몫([budgetShare])만큼 쓴다.
     * 현금이 없는 구단도 연봉 여유가 있으면 계약금 없이 입찰한다.
     */
    private fun aiBidding(state: FreeAgencyState, league: League, standings: Standings, random: Random, userTeam: TeamId?) {
        league.teams.shuffled(random).forEach { team ->
            if (team.id == userTeam) return@forEach
            val mode = modeResolver.modeOf(league, standings, team.id)
            val committed = state.offersBy(team.id)
            // 연봉은 연봉 총액 여유(샐러리캡의 일정 비율까지)에서, 계약금은 운용 자금에서 나간다 (docs/01 예산 표시)
            var payroll = league.payrollOf(team.id) + committed.sumOf { it.salary }
            var cash = team.operatingFunds * budgetShare - committed.sumOf { it.signingBonus }
            val roster = league.playersOf(team.id)
            state.remaining.forEach forEachAgent@{ agent ->
                if (team.id in state.withdrawnFrom(agent.playerId)) return@forEachAgent
                val player = league.player(agent.playerId)
                val current = state.offerOf(team.id, agent.playerId)
                val leader = ranked(state, agent, league, standings).firstOrNull()?.first
                if (current != null && leader?.teamId == team.id) return@forEachAgent

                val need = positionNeed.of(roster, player)
                if (current == null) {
                    if (need < MIN_NEED_TO_BID) return@forEachAgent
                    // 리빌딩 팀은 나이 든 FA 에 돈을 쓰지 않는다
                    if (mode == TeamMode.REBUILD && player.ageIn(league.season) > rebuildMaxAge) return@forEachAgent
                }

                val target = if (leader == null) {
                    agent.askingSalary * random.nextInRange(OPENING_RANGE)
                } else {
                    max(agent.askingSalary, leader.salary * (1.0 + bidIncrement * random.nextInRange(INCREMENT_JITTER)))
                }
                val alreadyPaid = current?.salary ?: 0.0
                val alreadyBonus = current?.signingBonus ?: 0.0
                val maximum = maximumOffer(player, team.id, league, mode, max(need, MIN_NEED_TO_BID), payroll - alreadyPaid, agent)
                val payrollAfter = payroll - alreadyPaid + target
                if (maximum < target || payrollAfter > salaryCap * payrollCeiling) {
                    // 더는 못 따라간다
                    if (current != null) {
                        state.withdraw(team.id, agent.playerId)
                        payroll -= alreadyPaid
                        cash += alreadyBonus
                    }
                    return@forEachAgent
                }

                val salary = round1(target)
                val bonus = round1(min(salary * BONUS_SHARE, max(0.0, cash + alreadyBonus)))
                payroll += salary - alreadyPaid
                cash -= bonus - alreadyBonus
                state.putOffer(
                    ContractOffer(
                        teamId = team.id,
                        playerId = agent.playerId,
                        salary = salary,
                        years = min(agent.askingYears, maxYearsFor(player, league.season)),
                        signingBonus = bonus,
                    ),
                )
            }
        }
    }

    /**
     * 이 구단이 낼 수 있는 최대 연봉.
     *
     * 가치 평가에서 나온 "연봉을 얼마까지 주면 본전인가"다. 기대 WAR × 시장가에 포지션 필요도를
     * 곱하고, 수익을 남기려는 만큼 깎는다. 이 값이 있어서 **AI 가 무한정 따라붙지 않는다.**
     */
    fun maximumOffer(
        player: Player,
        teamId: TeamId,
        league: League,
        mode: TeamMode,
        need: Double,
        payroll: Double = league.payrollOf(teamId),
        agent: FreeAgent? = null,
    ): Double {
        val estimate = marketView.estimate(player, teamId)
        val projected = valuation.project(player, estimate, league.season, valuation.projectionYears(player))
        val averageWar = if (projected.isEmpty()) 0.0 else projected.average()
        var maximum = averageWar * valuation.salaryPerWar * need * valueMargin *
            section.double("modeSpending.${mode.configKey}")

        // 보상금·보상선수를 미리 계산한다 (docs/11). A등급 영입은 연봉 이상의 값을 치른다
        if (agent != null && agent.grade != FaGrade.C && !agent.released && agent.previousTeam != teamId) {
            val rate = rules.double("compensation.${agent.grade.name}.salaryRateWithPlayer")
            val years = max(1, agent.askingYears)
            maximum -= player.contract.salary * rate / years
        }
        // 소프트캡을 넘기게 되는 영입은 제재금만큼 값을 깎아서 본다
        if (payroll + maximum > salaryCap) maximum *= overCapPenalty
        return maximum
    }

    /** 선수가 제안을 얼마나 마음에 들어 하는가 (0~1 남짓). */
    fun score(
        player: Player,
        offer: ContractOffer,
        league: League,
        standings: Standings,
        agent: FreeAgent,
    ): Double {
        val optionValue = optionRules.valueToPlayer(offer.options, player, league, standings, offer.teamId)
        return money(offer, agent, optionValue) * weightsFor(player, league).double("money") + nonMoneyScore(player, offer, league, standings, agent)
    }

    /**
     * 돈 점수: (연봉 + 계약금을 연으로 나눈 값 + 옵션 × 옵션 가치율) ÷ 희망 연봉. 너무 커지지 않게 [MONEY_CAP] 에서 자른다.
     * 옵션은 못 받을 수도 있는 돈이라 선수가 덜 쳐 준다 (2026-10-04 FA 협상 테이블)
     */
    private fun money(offer: ContractOffer, agent: FreeAgent, optionValue: Double): Double =
        moneyRaw(offer.salary, offer, agent, optionValue).coerceAtMost(MONEY_CAP)

    private fun moneyRaw(salary: Double, offer: ContractOffer, agent: FreeAgent, optionValue: Double): Double =
        (salary + offer.signingBonus / max(1, offer.years) + optionValue) / max(0.1, agent.askingSalary)

    /** 돈 말고 나머지(기간·우승 가능성·애정·출전 기회) 점수 */
    private fun nonMoneyScore(
        player: Player,
        offer: ContractOffer,
        league: League,
        standings: Standings,
        agent: FreeAgent,
    ): Double {
        val weights = weightsFor(player, league)
        val years = (offer.years.toDouble() / max(1, agent.askingYears)).coerceAtMost(YEARS_CAP)
        val winning = winningScore(league, standings, offer.teamId)
        val loyalty = if (offer.teamId == agent.previousTeam) LOYALTY_BASE + (1.0 - LOYALTY_BASE) * attachment(player, league, agent) else LOYALTY_BASE
        val playingTime = positionNeed.of(league.playersOf(offer.teamId), player) / MAX_NEED
        return years * weights.double("years") +
            winning * weights.double("winning") +
            loyalty * weights.double("loyalty") +
            playingTime * weights.double("playingTime")
    }

    /**
     * 원소속 구단 애착 배율 (docs/13 "선수 성향과 만족도"). 충성심 배율(0.5~1.5) × 만족도 배율.
     * 불만이 크면 0 에 가까워져 다른 팀과 차이가 없어지고, 크게 만족하면 두 배까지. 충성심 50 · 만족도 50 이면 1.
     *
     * **유저 구단 FA 에만 건다** (임시 결정 2026-10-04): AI 구단 FA 까지 충성심을 걸었더니 30시즌 장기 밸런스(시드 5150)의
     * 90+ 선수 수가 8.8 → 10.8 개로 움직였다. 만족도처럼 성향 효과도 유저 구단에 묶어 AI 생태계를 예전과 똑같이 둔다.
     */
    private fun attachment(player: Player, league: League, agent: FreeAgent): Double {
        if (league.management.userTeam != agent.previousTeam) return 1.0
        val personality = baseballgm.model.Personality.of(player)
        val morale = league.management.morale[player.id]
            ?.let { 1.0 + (it.value - baseballgm.management.MoraleService.NEUTRAL) / 50.0 * faMoraleSwing }
            ?: 1.0
        return personality.scale(personality.loyalty) * morale.coerceAtLeast(0.0)
    }

    internal fun weightsFor(player: Player, league: League): baseballgm.io.JsonSection {
        val key = if (player.ageIn(league.season) >= oldFromAge) "old" else "young"
        return section.section("weights.$key")
    }

    /**
     * 점수 [target] 에 닿으려면 (기간·계약금은 그대로 두고) 연봉이 얼마여야 하는가.
     * 점수는 연봉에 대해 일차식이라 거꾸로 풀 수 있다. 돈 점수 상한에 걸려 연봉만으론 안 되면 null.
     */
    fun salaryFor(target: Double, player: Player, offer: ContractOffer, league: League, standings: Standings, agent: FreeAgent): Double? {
        val weight = weightsFor(player, league).double("money")
        val neededMoney = (target - nonMoneyScore(player, offer, league, standings, agent)) / weight
        if (neededMoney > MONEY_CAP) return null
        val optionValue = optionRules.valueToPlayer(offer.options, player, league, standings, offer.teamId)
        val salary = neededMoney * max(0.1, agent.askingSalary) - offer.signingBonus / max(1, offer.years) - optionValue
        // 0.1억 단위로 올려서 반올림 오차로 모자라지 않게. 최저 연봉 아래는 의미가 없다
        val floorSalary = agent.salaryFloor - offer.signingBonus / max(1, offer.years)
        return ceil1(max(max(minimumSalary, floorSalary), salary + SALARY_EPSILON))
    }

    /**
     * 유저 구단 시점의 협상 현황. 화면의 "1순위/밀림" 표시와 계약 확률, "얼마면 1순위" 안내가 여기서 나온다.
     */
    fun negotiation(
        state: FreeAgencyState,
        playerId: PlayerId,
        teamId: TeamId,
        league: League,
        standings: Standings,
        /** 아직 내지 않은 조건으로 미리 본다. null 이면 지금 낸 조건 */
        hypothetical: ContractOffer? = null,
    ): FaNegotiation? {
        val agent = state.agent(playerId) ?: return null
        val player = league.player(playerId)
        val ranked = ranked(state, agent, league, standings, hypothetical?.copy(teamId = teamId))
        val ours = hypothetical?.copy(teamId = teamId) ?: state.offerOf(teamId, playerId)
        val ourScore = ranked.firstOrNull { it.first.teamId == teamId }?.second
        val bestRival = ranked.firstOrNull { it.first.teamId != teamId }
        val lastRound = state.round >= rounds
        val leading = ours != null && ranked.firstOrNull()?.first?.teamId == teamId
        val standing = when {
            ours == null -> FaStanding.NO_OFFER
            !meetsFloor(ours, agent) -> FaStanding.BELOW_FLOOR
            !leading -> FaStanding.OUTBID
            ourScore != null && ourScore >= signThreshold -> FaStanding.LEADING
            else -> FaStanding.LEADING_WAITING
        }
        val chance = if (lastRound) 1.0 else signChanceOf(state.round)
        val ourChance = when (standing) {
            FaStanding.LEADING -> chance
            FaStanding.LEADING_WAITING -> if (lastRound) 1.0 else 0.0
            else -> 0.0
        }
        val rivalChance = when {
            bestRival == null || leading -> 0.0
            lastRound -> 1.0
            bestRival.second >= signThreshold -> chance
            else -> 0.0
        }
        // 기준 조건: 우리가 낸 것, 없으면 희망 기간·계약금 없이
        val basis = ours ?: ContractOffer(teamId, playerId, agent.askingSalary, agent.askingYears)
        return FaNegotiation(
            playerId = playerId,
            standing = standing,
            ourOffer = ours,
            satisfaction = (ourScore ?: 0.0) / signThreshold,
            rivals = ranked.map { it.first.teamId }.filter { it != teamId },
            ourSignChance = ourChance,
            rivalSignChance = rivalChance,
            salaryToLead = if (leading || bestRival == null) {
                null
            } else {
                salaryFor(bestRival.second, player, basis, league, standings, agent)
            },
            salaryToSatisfy = salaryFor(signThreshold, player, basis, league, standings, agent),
            lastRound = lastRound,
            salaryFloor = agent.salaryFloor,
            lastWar = agent.lastWar,
            history = state.historyOf(playerId),
        )
    }

    private fun winningScore(league: League, standings: Standings, teamId: TeamId): Double {
        val record = standings.record(teamId)
        if (record.games == 0) return NEUTRAL_SCORE
        return (record.winPct / TOP_WIN_PCT).coerceIn(0.0, 1.0)
    }

    /**
     * 에이전트가 전하는 소문. [viewer] 구단을 뺀 최고 제시액을 전하는데 **과장이 섞인다** (docs/11).
     */
    fun rumor(state: FreeAgencyState, playerId: PlayerId, viewer: TeamId, random: Random): String {
        state.agent(playerId) ?: return "이미 계약했어요"
        val rivals = state.offersFor(playerId).filter { it.teamId != viewer }
        if (rivals.isEmpty()) return "다른 구단은 아직 조용하대요"
        val best = rivals.maxOf { it.salary } * random.nextInRange(rumorRange)
        return "다른 구단 ${rivals.size}곳이 조건을 냈고, 제일 센 곳은 ${format(best)}억쯤 부른다네요 (에이전트 말이라 부풀렸을 수 있어요)"
    }

    /**
     * 역제안 (docs/11 "주차당 1회").
     * 선수가 지금 조건에서 무엇이 아쉬운지 알려 주고, 받아들일 만한 조건을 하나 제시한다.
     * 다른 구단에 밀려 있으면 "1순위가 되는 연봉"을, 1순위인데 망설이면 "만족하는 연봉"을 부른다.
     */
    fun counterProposal(
        state: FreeAgencyState,
        offer: ContractOffer,
        player: Player,
        league: League,
        standings: Standings,
    ): Pair<ContractOffer, String> {
        val agent = state.agent(offer.playerId) ?: return offer to "이미 계약했어요"
        val view = negotiation(state, offer.playerId, offer.teamId, league, standings, hypothetical = offer)
        val maxYears = maxYearsFor(player, state.season)
        val yearsShort = offer.years < min(agent.askingYears, maxYears)
        return when {
            view?.standing == FaStanding.OUTBID && view.salaryToLead != null ->
                offer.copy(salary = view.salaryToLead) to "다른 구단 조건이 더 좋대요. 연 ${format(view.salaryToLead)}억이면 우리 쪽으로 기운대요"

            view?.standing == FaStanding.OUTBID && yearsShort ->
                offer.copy(years = min(agent.askingYears, maxYears)) to "돈만으론 어렵고, 기간을 ${min(agent.askingYears, maxYears)}년으로 늘려 달래요"

            view?.standing == FaStanding.OUTBID ->
                offer to "다른 구단 조건이 워낙 좋아서 연봉만으로 뒤집긴 어렵대요"

            view?.standing == FaStanding.LEADING_WAITING && yearsShort ->
                offer.copy(years = min(agent.askingYears, maxYears)) to "금액은 괜찮은데 기간을 ${min(agent.askingYears, maxYears)}년으로 늘려 달래요"

            view?.standing == FaStanding.LEADING_WAITING && view.salaryToSatisfy != null ->
                offer.copy(salary = view.salaryToSatisfy) to "우리가 1순위긴 한데, 연 ${format(view.salaryToSatisfy)}억이면 바로 도장 찍겠대요"

            else -> offer to "지금 조건이면 충분하대요. 진행하면 곧 답이 올 거예요"
        }
    }

    // ---------- 보상 ----------

    /**
     * 보상선수·보상금 (docs/11).
     *
     * 영입 팀이 보호선수 명단을 내고, 원소속팀이 **명단 밖에서 한 명**을 데려간다.
     * 원소속팀은 "보상선수 + 적은 보상금"과 "보상금만"을 비교해서 이득인 쪽을 고른다.
     */
    fun compensationFor(league: League, agent: FreeAgent, toTeam: TeamId, player: Player): FaCompensation? {
        if (agent.grade == FaGrade.C || toTeam == agent.previousTeam) {
            return if (agent.grade == FaGrade.C && toTeam != agent.previousTeam) {
                FaCompensation(
                    toTeam = agent.previousTeam,
                    fromTeam = toTeam,
                    cash = player.contract.salary * rules.double("compensation.C.salaryRateOnly"),
                    playerId = null,
                )
            } else {
                null
            }
        }
        val gradeKey = agent.grade.name
        val protectedCount = rules.int("compensation.$gradeKey.protectedPlayers")
        val withPlayerRate = rules.double("compensation.$gradeKey.salaryRateWithPlayer")
        val onlyRate = rules.double("compensation.$gradeKey.salaryRateOnly")

        val mode = TeamMode.NEUTRAL
        val roster = league.playersOf(toTeam)
            .filter { it.military.isAvailable }
            .sortedByDescending { valueOf(it, agent.previousTeam, league, mode) }
        val unprotected = roster.drop(protectedCount)
        val best = unprotected.maxByOrNull { valueOf(it, agent.previousTeam, league, mode) }

        val cashOnly = player.contract.salary * onlyRate
        val cashWithPlayer = player.contract.salary * withPlayerRate
        val playerWorth = best?.let { valueOf(it, agent.previousTeam, league, mode) } ?: Double.NEGATIVE_INFINITY

        return if (best != null && playerWorth > cashOnly - cashWithPlayer) {
            FaCompensation(agent.previousTeam, toTeam, cashWithPlayer, best.id)
        } else {
            FaCompensation(agent.previousTeam, toTeam, cashOnly, null)
        }
    }

    private fun valueOf(player: Player, viewer: TeamId, league: League, mode: TeamMode): Double {
        val estimate = marketView.estimate(player, viewer)
        return valuation.valueOf(player, estimate, league.season, mode).value
    }

    // ---------- 폐장 ----------

    /**
     * 시장을 닫고 결과를 리그에 반영한다.
     *
     * 미계약 FA 는 **헐값 1년 계약**을 받는다 (docs/11). 현실의 "미계약 은퇴"는 은퇴 판정이
     * 이미 스토브리그 앞에서 끝났으므로 여기서는 만들지 않는다.
     */
    fun close(state: FreeAgencyState, league: League, random: Random): League {
        var players = league.players
        var teams = league.teams

        state.signings.forEach { signing ->
            players = players.map { player ->
                if (player.id != signing.playerId) {
                    player
                } else {
                    player
                        .withTeam(signing.offer.teamId, RosterLevel.FUTURES)
                        .withContract(contractOf(player, signing.offer, league.season))
                }
            }
            // 보상선수와 보상금
            signing.compensation?.let { compensation ->
                teams = teams.map { team ->
                    when (team.id) {
                        compensation.fromTeam ->
                            team.copy(operatingFunds = max(0.0, team.operatingFunds - compensation.cash))

                        compensation.toTeam -> team.copy(operatingFunds = team.operatingFunds + compensation.cash)
                        else -> team
                    }
                }
                compensation.playerId?.let { compensated ->
                    players = players.map { player ->
                        if (player.id == compensated) player.withTeam(compensation.toTeam, RosterLevel.FUTURES) else player
                    }
                }
            }
            // 계약금은 영입 팀의 운용 자금에서 나간다
            teams = teams.map { team ->
                if (team.id == signing.offer.teamId) {
                    team.copy(operatingFunds = max(0.0, team.operatingFunds - signing.offer.signingBonus))
                } else {
                    team
                }
            }
        }

        // 미계약 방출 선수는 리그를 떠난다
        val departing = state.remaining.filter { it.released }.map { it.playerId }.toSet()
        players = players.filterNot { it.id in departing }

        // 미계약 FA: 원소속팀과 헐값 1년 계약
        state.remaining.filterNot { it.released }.forEach { agent ->
            players = players.map { player ->
                if (player.id != agent.playerId) {
                    player
                } else {
                    // 헐값이라도 직전 시즌 WAR 로 정한 최저 연봉은 넘는다 (2026-10-04)
                    val salary = max(agent.salaryFloor, max(minimumSalary, player.contract.salary * leftoverRate))
                    player
                        .withTeam(agent.previousTeam, RosterLevel.FUTURES)
                        .withContract(
                            player.contract.copy(
                                salary = round1(salary),
                                yearsRemaining = 1,
                                signingBonusRemaining = 0.0,
                                type = ContractType.STANDARD,
                                options = emptyList(),
                            ),
                        )
                }
            }
        }

        // FA·보상선수로 팀을 옮긴 선수는 새 팀에서 번호가 겹치면 바꿔 단다 (원래 있던 선수가 번호를 지킨다)
        val movers = state.signings.flatMap { listOfNotNull(it.playerId, it.compensation?.playerId) }.toSet()
        players = baseballgm.model.UniformNumbers.assign(players, movers)
        return league.copy(players = players, teams = teams, freeAgentPool = emptyList(), faOrigins = emptyMap())
    }

    private fun contractOf(player: Player, offer: ContractOffer, season: Int): Contract {
        val qualifying = when (player.origin) {
            Origin.COLLEGE -> rules.int("qualifyingSeasons.college")
            else -> rules.int("qualifyingSeasons.highSchool")
        }
        return player.contract.copy(
            salary = offer.salary,
            yearsRemaining = offer.years,
            signingBonusRemaining = 0.0,
            seasonsToFreeAgency = rules.int("qualifyingSeasons.reQualify"),
            serviceSeasons = max(player.contract.serviceSeasons, qualifying),
            type = ContractType.FREE_AGENT,
            nextSalary = null,
            options = offer.options,
        )
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun ceil1(value: Double): Double = kotlin.math.ceil(value * 10) / 10.0

    private fun format(value: Double): String = round1(value).toString()

    private companion object {
        const val MIN_NEED_TO_BID = 1.0
        /** 처음 참전할 때 희망 연봉에 얹는 폭 */
        val OPENING_RANGE = 1.0..1.06
        /** 응수 폭의 흔들림 (bidIncrement × 이 값) */
        val INCREMENT_JITTER = 0.7..1.3
        const val SALARY_EPSILON = 0.01
        const val BONUS_SHARE = 0.3
        const val MONEY_CAP = 1.4
        const val YEARS_CAP = 1.2
        const val LOYALTY_BASE = 0.35
        const val MAX_NEED = 1.5
        const val NEUTRAL_SCORE = 0.5
        const val TOP_WIN_PCT = 0.6
        const val RUMOR_INTEREST = 3
    }
}
