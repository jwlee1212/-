package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.model.Personality
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.season.InboxCategory
import baseballgm.season.RosterSlot
import baseballgm.season.SeasonState
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * 기억의 종류 (docs/13 "선수 성향과 만족도"). **같은 종류는 쌓이지 않고 덮어쓴다** —
 * 콜업·말소를 되풀이하거나 같은 칭찬을 여러 번 해서 만족도를 끌어올리는 꼼수를 막는다.
 */
@Serializable
enum class MemorySlot {
    /** 1군 등록·말소 (단장 직접 + 콜업 이벤트) */
    ROSTER,

    /** SNS 논란 대응 */
    CONTROVERSY,

    /** 부상 대응 */
    INJURY,

    /** 계약 (연장·다년계약·연봉 불만 답) */
    CONTRACT,

    /** 트레이드 (지켜 줌·이적 요청 답) */
    TRADE,

    /** 약속 (했다·지켰다·어겼다) */
    PROMISE,

    /** 멘토링 */
    MENTOR,
}

/**
 * 단장의 결정이 남긴 기억 하나. [remaining] 주가 지나면 사라지고, 그동안 선형으로 옅어진다.
 * @param key balance.json `morale.memories` 의 키 (값·기간을 거기서 읽었다)
 * @param label 화면의 요인 목록 한 줄 ("SNS 논란 때 감싸 줬다")
 */
@Serializable
data class MoraleMemory(
    val slot: MemorySlot,
    val key: String,
    val label: String,
    val value: Int,
    val weeks: Int,
    val remaining: Int,
) {
    /** 지금 만족도 목표값에 더하는 몫 */
    val current: Int get() = if (weeks <= 0) 0 else (value.toDouble() * remaining / weeks).roundToInt()
}

/** 약속 종류 (FM 의 약속) */
@Serializable
enum class PromiseKind(val label: String) {
    /** 기한 안에 1군 등록 */
    PLAYING_TIME("1군 기회"),

    /** 정규시즌이 끝나기 전에 다년계약(연장) */
    CONTRACT("다년계약"),
}

/** 선수에게 한 약속. [deadlineWeek] 주차가 끝날 때까지 못 지키면 어긴 것 */
@Serializable
data class PlayerPromise(val kind: PromiseKind, val season: Int, val madeWeek: Int, val deadlineWeek: Int)

/**
 * 선수 한 명의 만족도 상태 (유저 구단 선수만). 시즌을 넘어 이어지므로 [ManagementState.morale] 에 저장된다.
 *
 * @param value 만족도 0~100
 * @param lowWeeks 불만 문턱(`tradeRequest.below`) 아래로 이어진 주 수 — 이적 요청 판정
 * @param transferListed 이적 요청을 받아 트레이드 시장에 내놓았다 (시즌이 바뀌면 풀린다)
 */
@Serializable
data class PlayerMorale(
    val value: Int,
    val memories: List<MoraleMemory> = emptyList(),
    val promise: PlayerPromise? = null,
    val lowWeeks: Int = 0,
    val transferListed: Boolean = false,
)

/** 만족도 요인 한 줄 (화면의 "출장 기회 −8") */
data class MoraleFactor(val label: String, val value: Int)

/** 만족도 단계. [index] 0 = 매우 만족 … 4 = 매우 불만 */
data class MoraleLevel(val index: Int, val label: String) {
    val happy: Boolean get() = index <= 1
    val unhappy: Boolean get() = index >= 3
}

/** 성향을 화면에 보낼 글자 (정확도만큼 흐린 값의 단계). 숫자는 담지 않는다 */
data class PersonalityReading(
    val archetype: String,
    val loyalty: String,
    val ambition: String,
    val professionalism: String,
)

/** 선수 한 명의 만족도 보기 (화면용). 숨김 성향 숫자는 담지 않는다 */
data class MoraleView(
    val value: Int,
    val level: MoraleLevel,
    val factors: List<MoraleFactor>,
    val promise: PlayerPromise?,
    val transferListed: Boolean,
    val bond: Bond?,
)

/**
 * 선수 만족도 (docs/13 "선수 성향과 만족도", 2026-10-04 FM 모티브).
 *
 * 만족도는 **목표값 쪽으로 매주 끌려가는 값**이다. 목표값 = 50 + 요인 합 —
 * 출장 기회 · 팀 성적 · 연봉 · 함께한 시간(유대) · 기억(단장의 결정·약속). 성향(충성심·야망·프로의식)이 요인의 배율이다.
 * 끌려가게 만든 이유: 한 주 2군에 내려갔다고 바로 바닥을 치면 숫자가 요동쳐서 읽을 수가 없다. 쌓여야 움직인다.
 *
 * **유저 구단 선수만 굴린다.** AI 구단은 원래대로 돌아가고(장기 밸런스 테스트에 영향 없음), 성향만 FA 에서 쓴다.
 * 난수를 쓰지 않는다 — 같은 결정이면 항상 같은 만족도다 (불변 원칙 2).
 */
class MoraleService(private val balance: BalanceConfig, private val strength: StrengthCalculator) {

    private val cfg = balance.section("morale")
    private val levels = cfg.intList("levels")
    private val levelLabels = cfg.stringList("levelLabels")
    private val weeklyRate = cfg.double("weeklyRate")
    private val instantShare = cfg.double("instantShare")
    private val playingTime = cfg.section("playingTime")
    private val teamResult = cfg.section("teamResult")
    private val performance = cfg.section("performance")
    private val salary = cfg.section("salary")
    private val bondCfg = cfg.section("bond")
    private val formShift = cfg.double("form.shiftPerPoint")
    private val contract = cfg.section("contract")
    private val memories = cfg.section("memories")
    private val coreBatters = balance.int("incidents.coreBatters")
    private val corePitchers = balance.int("incidents.corePitchers")
    private val valuation = baseballgm.market.Valuation(balance, baseballgm.development.AgingCurves(balance))
    private val bonds = Bonds(balance)

    // ---------- 읽기 ----------

    fun personalityOf(player: Player): Personality = Personality.of(player)

    fun levelOf(value: Int): MoraleLevel {
        val index = levels.indexOfFirst { value >= it }.let { if (it < 0) levels.size else it }
        return MoraleLevel(index, levelLabels[index])
    }

    /** 지금 만족도. 아직 계산한 적 없는 선수(새로 온 선수)는 목표값 */
    fun valueOf(state: SeasonState, player: Player): Int =
        state.morale[player.id]?.value ?: target(state, player.teamId ?: return NEUTRAL, player, emptyList())

    fun view(state: SeasonState, team: TeamId, player: Player): MoraleView? {
        if (player.teamId != team) return null
        val morale = state.morale[player.id]
        val factors = factors(state, team, player, morale?.memories.orEmpty())
        val value = morale?.value ?: clamp(NEUTRAL + factors.sumOf { it.value })
        return MoraleView(value, levelOf(value), factors, morale?.promise, morale?.transferListed == true, bondOf(state, team, player))
    }

    /**
     * 목표값을 이루는 요인 (0 인 요인은 뺀다). 화면의 요인 목록이 곧 이것이다.
     * 기억은 같은 종류가 하나뿐이라 그대로 한 줄씩.
     */
    fun factors(state: SeasonState, team: TeamId, player: Player, memories: List<MoraleMemory>): List<MoraleFactor> {
        val personality = personalityOf(player)
        val ambition = personality.scale(personality.ambition)
        val loyalty = personality.scale(personality.loyalty)
        return buildList {
            playingTimeFactor(state, team, player, ambition)?.let { add(it) }
            teamResultFactor(state, team, ambition)?.let { add(it) }
            performanceFactor(state, player)?.let { add(it) }
            salaryFactor(player)?.let { add(it) }
            bondOf(state, team, player)?.let { bond ->
                val value = (minOf(bondCfg.double("max"), bond.seasons * bondCfg.double("perSeason")) * loyalty).roundToInt()
                if (value != 0) add(MoraleFactor(bond.label(), value))
            }
            memories.filter { it.current != 0 }.forEach { add(MoraleFactor(it.label, it.current)) }
        }.filter { it.value != 0 }
    }

    // ---------- 매주 ----------

    /**
     * 한 주가 끝날 때 우리 선수 전원의 만족도를 움직인다 (WeekLoop.endWeek).
     * ① 떠난 선수 정리 ② 기억 옅어짐 ③ 약속 확인 ④ 목표값 쪽으로 [weeklyRate] 만큼 ⑤ 불만 주 수 ⑥ 단계가 바뀌면 선수 메시지
     */
    fun weekly(state: SeasonState, team: TeamId, week: Int) {
        val roster = state.playersOf(team)
        val ids = roster.map { it.id }.toSet()
        state.morale.keys.retainAll(ids)
        val lowBelow = cfg.int("tradeRequest.below")
        roster.forEach { player ->
            val before = state.morale[player.id]
            var memoriesNow = before?.memories.orEmpty()
                .map { it.copy(remaining = it.remaining - 1) }
                .filter { it.remaining > 0 }
            var promise = before?.promise
            if (promise != null) {
                when (checkPromise(state, player, promise, week)) {
                    PromiseOutcome.KEPT -> {
                        memoriesNow = withMemory(memoriesNow, memory(MemorySlot.PROMISE, "promiseKept", "약속(${promise.kind.label})을 지켰다"))
                        say(state, team, week, player, KEPT_LINES[promise.kind] ?: "약속 지켜 주셔서 고맙습니다.")
                        promise = null
                    }
                    PromiseOutcome.BROKEN -> {
                        memoriesNow = withMemory(memoriesNow, memory(MemorySlot.PROMISE, "promiseBroken", "약속(${promise.kind.label})을 어겼다"))
                        say(state, team, week, player, BROKEN_LINES[promise.kind] ?: "약속하셨잖아요. 실망입니다.")
                        promise = null
                    }
                    PromiseOutcome.PENDING -> Unit
                }
            }
            val target = clamp(NEUTRAL + factors(state, team, player, memoriesNow).sumOf { it.value })
            val value = before?.value?.let { clamp((it + (target - it) * weeklyRate).roundToInt()) } ?: target
            state.morale[player.id] = PlayerMorale(
                value = value,
                memories = memoriesNow,
                promise = promise,
                lowWeeks = if (value < lowBelow) (before?.lowWeeks ?: 0) + 1 else 0,
                transferListed = before?.transferListed == true,
            )
            if (before != null) announceLevelChange(state, team, week, player, levelOf(before.value), levelOf(value))
        }
    }

    /** 처음 보는 우리 선수들의 만족도를 목표값으로 채운다 (새 게임·트레이드 직후 화면이 비지 않게) */
    fun ensure(state: SeasonState, team: TeamId) {
        state.playersOf(team).forEach { player ->
            if (player.id !in state.morale) state.morale[player.id] = PlayerMorale(target(state, team, player, emptyList()))
        }
    }

    // ---------- 기억·약속 ----------

    /**
     * 기억을 남긴다. 같은 [slot] 의 이전 기억은 지운다. 값의 [instantShare] 만큼은 바로 만족도에 반영한다.
     * 우리 선수가 아니면 아무 일도 없다.
     */
    fun remember(state: SeasonState, team: TeamId, playerId: PlayerId, slot: MemorySlot, key: String, label: String) {
        val player = runCatching { state.player(playerId) }.getOrNull()?.takeIf { it.teamId == team } ?: return
        val memory = memory(slot, key, label)
        val before = state.morale[playerId] ?: PlayerMorale(target(state, team, player, emptyList()))
        state.morale[playerId] = before.copy(
            value = clamp(before.value + (memory.value * instantShare).roundToInt()),
            memories = withMemory(before.memories, memory),
        )
    }

    fun promise(state: SeasonState, team: TeamId, playerId: PlayerId, kind: PromiseKind, deadlineWeek: Int) {
        val player = runCatching { state.player(playerId) }.getOrNull()?.takeIf { it.teamId == team } ?: return
        val before = state.morale[playerId] ?: PlayerMorale(target(state, team, player, emptyList()))
        state.morale[playerId] = before.copy(promise = PlayerPromise(kind, state.season, state.week, deadlineWeek))
    }

    fun listForTrade(state: SeasonState, team: TeamId, playerId: PlayerId) {
        val player = runCatching { state.player(playerId) }.getOrNull()?.takeIf { it.teamId == team } ?: return
        val before = state.morale[playerId] ?: PlayerMorale(target(state, team, player, emptyList()))
        state.morale[playerId] = before.copy(transferListed = true)
    }

    /**
     * 단장이 직접 1군에 올리거나 내렸다 (로스터 화면). 올리면 누구든 +, 내리면 주전급만 − (벤치 선수를 내리는 건 일상이다).
     */
    fun onUserRosterMove(state: SeasonState, team: TeamId, playerId: PlayerId, promoted: Boolean, wasCore: Boolean) {
        if (promoted) {
            remember(state, team, playerId, MemorySlot.ROSTER, "userPromoted", "단장이 직접 1군에 올렸다")
        } else if (wasCore) {
            remember(state, team, playerId, MemorySlot.ROSTER, "userDemoted", "주전인데 2군으로 내려갔다")
        }
    }

    /** 주전 = 1군(부상자 포함) 야수 능력 상위 N + 투수 상위 N (돌발 이벤트와 같은 기준) */
    fun coreOf(state: SeasonState, team: TeamId): Set<PlayerId> {
        val firstTeam = state.firstTeamOf(team)
        val batters = firstTeam.filter { it !is baseballgm.model.Pitcher }.sortedByDescending { strength.overallOf(it) }.take(coreBatters)
        val pitchers = firstTeam.filterIsInstance<baseballgm.model.Pitcher>().sortedByDescending { strength.overallOf(it) }.take(corePitchers)
        return (batters + pitchers).map { it.id }.toSet()
    }

    // ---------- 효과 ----------

    /**
     * 폼이 매일 돌아가는 중심 (FormModel.next). 우리 선수가 아니면 null (= 50).
     * 만족도가 50 아래일 때는 프로의식이 높을수록 덜 빠진다.
     */
    fun formCenter(state: SeasonState, player: Player): Double? {
        val morale = state.morale[player.id] ?: return null
        val shift = (morale.value - NEUTRAL) * formShift
        if (shift >= 0) return NEUTRAL + shift
        val personality = personalityOf(player)
        return NEUTRAL + shift * (1.5 - personality.professionalism / 100.0).coerceIn(0.5, 1.5)
    }

    /**
     * 연장·비FA 다년계약 요구액 배율. 만족도·충성심이 높으면 1 보다 작다(싸게), 낮으면 크다.
     * 만족도를 굴리지 않는 선수(타 팀)는 충성심만.
     */
    fun contractMultiplier(state: SeasonState, player: Player): Double {
        val personality = personalityOf(player)
        val morale = state.morale[player.id]?.value ?: NEUTRAL
        return 1.0 - (morale - NEUTRAL) / 50.0 * contract.double("moraleSwing") -
            (personality.loyalty - NEUTRAL) / 50.0 * contract.double("loyaltySwing")
    }

    /** 만족도가 너무 낮아 협상 테이블에 앉지 않는다 */
    fun refusesTalks(state: SeasonState, player: Player): Boolean =
        (state.morale[player.id]?.value ?: NEUTRAL) < contract.int("refuseBelow")

    // ---------- 요인 ----------

    private fun target(state: SeasonState, team: TeamId, player: Player, memories: List<MoraleMemory>): Int =
        clamp(NEUTRAL + factors(state, team, player, memories).sumOf { it.value })

    private fun playingTimeFactor(state: SeasonState, team: TeamId, player: Player, ambition: Double): MoraleFactor? {
        // 다쳤거나 군 복무 중이면 뛸 수 없는 게 당연하다
        if (player.condition.isInjured || !player.military.isAvailable) return null
        if (player.rosterLevel == RosterLevel.FIRST_TEAM) {
            return if (player.id in coreOf(state, team)) {
                MoraleFactor("주전으로 뛰고 있다", playingTime.int("core"))
            } else {
                MoraleFactor("1군에 있다", playingTime.int("firstTeam"))
            }
        }
        if (player.ageIn(state.season) <= playingTime.int("youngAge")) return null
        val slot = RosterSlot.of(player)
        val weakest = state.firstTeamOf(team).filter { RosterSlot.of(it) == slot }.minOfOrNull { strength.overallOf(it) }
        if (weakest != null && strength.overallOf(player) >= weakest) {
            return MoraleFactor("1군 실력인데 2군에 있다", (playingTime.int("blocked") * ambition).roundToInt())
        }
        if (player.contract.salary >= playingTime.double("paidBenchSalary")) {
            return MoraleFactor("연봉에 맞지 않는 2군 생활", (playingTime.int("paidBench") * ambition).roundToInt())
        }
        return MoraleFactor("2군에 있다", (playingTime.int("futures") * ambition).roundToInt())
    }

    /** 개인 성적 (1군 공개 기록만). 리그 평균보다 잘하면 +, 못하면 − */
    private fun performanceFactor(state: SeasonState, player: Player): MoraleFactor? {
        val ratio = when (player) {
            is baseballgm.model.Pitcher -> {
                val line = state.stats.pitchingOf(player.id).total
                if (line.outs < performance.int("minOuts")) return null
                val league = state.stats.allPitching().values.fold(baseballgm.stats.PitchingLine.EMPTY) { sum, it -> sum + it.total }
                if (line.era <= 0.0 || league.outs == 0) return null
                league.era / line.era - 1.0
            }
            is baseballgm.model.Batter -> {
                val line = state.stats.battingOf(player.id).total
                if (line.plateAppearances < performance.int("minPa")) return null
                val league = state.stats.allBatting().values.fold(baseballgm.stats.BattingLine.EMPTY) { sum, it -> sum + it.total }
                if (league.ops <= 0.0) return null
                line.ops / league.ops - 1.0
            }
        }
        val max = performance.double("max")
        val value = (ratio * performance.double("perRatio")).coerceIn(-max, max).roundToInt()
        return MoraleFactor(if (value >= 0) "올 시즌 잘 풀린다" else "올 시즌 안 풀린다", value)
    }

    private fun teamResultFactor(state: SeasonState, team: TeamId, ambition: Double): MoraleFactor? {
        val record = state.standings.record(team)
        val decided = record.wins + record.losses
        if (decided < teamResult.int("minGames")) return null
        val pct = record.wins.toDouble() / decided
        val value = ((pct - HALF) * teamResult.double("perPoint") * ambition).roundToInt()
        return MoraleFactor(if (value >= 0) "팀 성적이 좋다" else "팀 성적이 나쁘다", value)
    }

    private fun salaryFactor(player: Player): MoraleFactor? {
        if (player.contract.serviceSeasons < salary.int("minService")) return null
        val war = valuation.expectedWar(player, strength.overallOf(player))
        val fair = war * valuation.salaryPerWar
        if (fair <= 0.0) return null
        val ratio = player.contract.salary / fair
        return when {
            war >= salary.double("minWar") && ratio < salary.double("underpaidRatio") ->
                MoraleFactor("실력에 비해 연봉이 적다", salary.int("underpaid"))
            ratio >= salary.double("wellPaidRatio") -> MoraleFactor("연봉 대우에 만족한다", salary.int("wellPaid"))
            else -> null
        }
    }

    private fun bondOf(state: SeasonState, team: TeamId, player: Player): Bond? =
        bonds.of(player, team, state.season, state.league.management.career, state.userDecisions(team))

    private enum class PromiseOutcome { KEPT, BROKEN, PENDING }

    private fun checkPromise(state: SeasonState, player: Player, promise: PlayerPromise, week: Int): PromiseOutcome {
        if (promise.season != state.season) return PromiseOutcome.BROKEN
        val kept = when (promise.kind) {
            PromiseKind.PLAYING_TIME -> player.rosterLevel == RosterLevel.FIRST_TEAM
            PromiseKind.CONTRACT -> player.id in state.extendedThisSeason
        }
        return when {
            kept -> PromiseOutcome.KEPT
            week >= promise.deadlineWeek -> PromiseOutcome.BROKEN
            else -> PromiseOutcome.PENDING
        }
    }

    private fun memory(slot: MemorySlot, key: String, label: String): MoraleMemory {
        val weeks = memories.int("$key.weeks")
        return MoraleMemory(slot, key, label, memories.int("$key.value"), weeks, weeks)
    }

    private fun withMemory(list: List<MoraleMemory>, memory: MoraleMemory): List<MoraleMemory> =
        list.filterNot { it.slot == memory.slot } + memory

    /** 단계가 바뀐 순간 선수가 한마디 — 불만으로 떨어졌을 때와 매우 만족에 올랐을 때만 (매주 말하면 잔소리다) */
    private fun announceLevelChange(state: SeasonState, team: TeamId, week: Int, player: Player, before: MoraleLevel, after: MoraleLevel) {
        if (before.index == after.index) return
        val line = when {
            after.unhappy && !before.unhappy -> "요즘 솔직히 많이 답답합니다. 단장님이 한번 신경 써 주셨으면 해요."
            after.index == 0 && before.index > 0 -> "요즘 야구할 맛 납니다. 이 팀에 있어서 다행이에요."
            else -> return
        }
        say(state, team, week, player, line)
    }

    private fun say(state: SeasonState, team: TeamId, week: Int, player: Player, line: String) {
        state.inbox.add(week, InboxCategory.PLAYER, team, "${player.registeredName}: $line")
    }

    private fun clamp(value: Int): Int = value.coerceIn(0, MAX)

    companion object {
        const val NEUTRAL = 50
        const val MAX = 100
        private const val HALF = 0.5

        private val KEPT_LINES = mapOf(
            PromiseKind.PLAYING_TIME to "약속 지켜 주셔서 고맙습니다. 1군에서 꼭 보답할게요.",
            PromiseKind.CONTRACT to "말씀하신 대로 계약해 주셨네요. 믿고 따라가겠습니다.",
        )
        private val BROKEN_LINES = mapOf(
            PromiseKind.PLAYING_TIME to "기회 주신다고 하셨잖아요. 솔직히 많이 실망했습니다.",
            PromiseKind.CONTRACT to "계약 얘기는 결국 없던 일이 됐네요. 섭섭합니다.",
        )
    }
}
