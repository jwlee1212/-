package baseballgm.tactics

import baseballgm.model.PlayerId
import baseballgm.model.Position
import kotlinx.serialization.Serializable

/**
 * 불펜 역할 (docs/06 ④).
 *
 * 역할은 "이 상황에 누구를 낸다"는 약속이다. 시뮬레이터는 상황을 보고 역할을 고른 뒤,
 * 그 역할에 적힌 순서대로 던질 수 있는 투수를 찾는다.
 */
@Serializable
enum class BullpenRole(val label: String) {
    CLOSER("마무리"),
    SETUP("셋업"),
    HIGH_LEVERAGE("필승조"),
    LEFTY_SPECIALIST("좌완 스페셜리스트"),
    MOP_UP("추격조"),
    LONG_RELIEF("롱릴리프"),
}

/** 타순 한 자리: 누가 어느 수비 위치로 나가는가. */
@Serializable
data class LineupSlot(val playerId: PlayerId, val position: Position)

/**
 * 라인업 한 벌 (docs/06 ①).
 * 좌완 선발용과 우완 선발용 두 벌이 필수라서, 규칙표는 이 객체를 두 개 들고 있다.
 */
@Serializable
data class LineupPlan(val slots: List<LineupSlot>) {
    init {
        require(slots.size == SIZE) { "라인업은 ${SIZE}자리여야 한다 (현재 ${slots.size})" }
    }

    val playerIds: List<PlayerId> get() = slots.map { it.playerId }

    fun positionOf(playerId: PlayerId): Position? = slots.firstOrNull { it.playerId == playerId }?.position

    companion object {
        const val SIZE: Int = 9
    }
}

/** 휴식 규칙 (docs/06 ①). 피로도가 기준을 넘으면 라인업에서 뺀다. */
@Serializable
data class RestRule(
    val fatigueThreshold: Int,
    /** 포수는 며칠에 한 번 쉬게 할지 */
    val catcherRestEveryDays: Int,
)

/** 선발 로테이션 (docs/06 ②). */
@Serializable
data class RotationPlan(
    val starters: List<PlayerId>,
    val minimumRestDays: Int,
    /** 로테이션이 비었을 때 대신 올릴 투수 */
    val substituteStarter: PlayerId?,
)

/**
 * 선발 교체 조건 (docs/06 ③). **먼저 충족되는 것**이 적용된다.
 */
@Serializable
data class StarterHookRule(
    val pitchLimit: Int,
    val runsAllowedLimit: Int,
    /** 세 번째로 같은 타순을 상대할 때 내릴지 */
    val pullOnThirdTimeThroughOrder: Boolean,
    val fatigueLimit: Int,
)

/** 불펜 운용 (docs/06 ④). */
@Serializable
data class BullpenPlan(
    /** 역할별 후보. 앞에 적힌 투수부터 쓴다 */
    val roles: Map<BullpenRole, List<PlayerId>>,
    val maxConsecutiveDays: Int,
    val maxPitchesLast7Days: Int,
) {
    fun candidates(role: BullpenRole): List<PlayerId> = roles[role].orEmpty()
}

/** 선수 교체 조건 (docs/06 ⑤). */
@Serializable
data class SubstitutionRules(
    /** 대타는 몇 회부터 */
    val pinchHitFromInning: Int,
    /** 몇 점 차까지 대타를 낼지 (그보다 크게 지면 굳이 안 쓴다) */
    val pinchHitMaxDeficit: Int,
    /** 대타 후보가 원래 타자보다 이 점수 이상 좋아야 바꾼다 */
    val pinchHitRatingGap: Int,
    /** 득점권에서만 대타를 낼지 */
    val pinchHitOnlyInScoringPosition: Boolean,
    /** 좌우 상성이 맞을 때만 대타를 낼지 */
    val pinchHitPlatoonOnly: Boolean,
    val pinchRunFromInning: Int,
    val pinchRunMaxMargin: Int,
    val pinchRunSpeedGap: Int,
    val defensiveSubFromInning: Int,
    val defensiveSubMinLead: Int,
    val defensiveSubDefenseGap: Int,
)

/** 작전 성향 슬라이더 1~5 (docs/06 ⑥). */
@Serializable
data class TacticSliders(
    val bunt: Int,
    val steal: Int,
    val intentionalWalk: Int,
) {
    init {
        require(bunt in RANGE && steal in RANGE && intentionalWalk in RANGE) { "슬라이더는 1~5" }
    }

    companion object {
        val RANGE: IntRange = 1..5
    }
}

/** 주간 방침 (docs/06). 규칙표를 그대로 쓰되 제한을 풀거나 조인다. */
@Serializable
enum class WeeklyPolicy(val configKey: String, val label: String) {
    NORMAL("normal", "정상 운영"),
    ALL_OUT("allOut", "총력전"),
    PROTECT("protect", "선수 보호"),
}

/**
 * 사전 지시 규칙표 (docs/06).
 *
 * **규칙표는 하나이고, 누가 채우는지만 다르다.** 단장 전용 모드면 감독 AI 가 성향으로 채우고,
 * 감독 겸직 모드면 유저가 채운다. AI 구단 9개도 같은 구조를 쓴다. 시뮬레이터는 이 객체만 보고
 * 경기를 운영하기 때문에, 누가 채웠든 경기 로직은 완전히 같다.
 */
@Serializable
data class DirectiveSheet(
    val lineupVsLeft: LineupPlan,
    val lineupVsRight: LineupPlan,
    val rest: RestRule,
    val rotation: RotationPlan,
    val starterHook: StarterHookRule,
    val bullpen: BullpenPlan,
    val substitution: SubstitutionRules,
    val tactics: TacticSliders,
) {
    /** 상대 선발의 손에 맞는 라인업을 고른다 (docs/06 ①). */
    fun lineupFor(opposingStarterThrows: baseballgm.model.Hand): LineupPlan =
        if (opposingStarterThrows == baseballgm.model.Hand.LEFT) lineupVsLeft else lineupVsRight
}
