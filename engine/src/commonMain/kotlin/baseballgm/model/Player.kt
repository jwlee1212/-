package baseballgm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 선수. 투타 겸업은 없으므로 [Batter] 와 [Pitcher] 둘 중 하나다 (docs/02).
 *
 * 나이는 저장하지 않고 출생 연도로 둔다. 시즌이 넘어갈 때 전원의 나이를 일일이 올리는 대신
 * `ageIn(season)` 으로 계산하면 나이가 틀어질 일이 없다.
 */
@Serializable
sealed class Player {
    abstract val id: PlayerId
    abstract val name: String
    abstract val birthYear: Int
    abstract val throwsWith: Hand
    abstract val bats: Hand
    abstract val origin: Origin
    abstract val debutSeason: Int
    abstract val teamId: TeamId?
    abstract val rosterLevel: RosterLevel
    abstract val contract: Contract
    abstract val military: MilitaryStatus
    abstract val condition: Condition

    /** 숨김 수치. 프로퍼티가 전부 `internal` 이라 엔진 밖에서는 내용을 읽을 수 없다 (불변 원칙 4). */
    abstract val hidden: HiddenTraits

    /** 등록명. 외국인 선수는 한글 등록명이 따로 있다. */
    abstract val registeredName: String

    /**
     * 등번호 (2026-10-02). 0 이면 아직 없다 — 리그 파일·예전 세이브에는 없어서, 시즌 상태가 만들어질 때
     * [UniformNumbers] 가 채운다. 공개 정보라 숨김 수치가 아니다. 시뮬레이션에는 쓰지 않는다.
     */
    abstract val uniformNumber: Int

    abstract fun withUniformNumber(number: Int): Player

    /**
     * 이름난 아마추어 유망주인가 (2026-10-04). 대회·언론 노출이 많아 어느 구단이든 자료를 꽤 갖고 있다는 **공개 평판**이라
     * 숨김 수치가 아니다. 드래프트 풀이 만들어질 때 엔진(`ScoutingBudget.markKnownProspects`)이 정한다.
     */
    abstract val knownProspect: Boolean

    abstract fun withKnownProspect(known: Boolean): Player

    fun ageIn(season: Int): Int = season - birthYear

    val isForeign: Boolean get() = origin == Origin.FOREIGN

    /** 이 선수의 능력치를 능력치 종류로 읽는다. 성장·노화·스카우트가 공통 코드로 돌게 한다. */
    abstract fun rating(attribute: Attribute): Int

    abstract fun ratingsMap(): Map<Attribute, Int>
}

/** 타자. */
@Serializable
@SerialName("batter")
data class Batter(
    override val id: PlayerId,
    override val name: String,
    override val birthYear: Int,
    override val throwsWith: Hand,
    override val bats: Hand,
    override val origin: Origin,
    override val debutSeason: Int,
    override val teamId: TeamId?,
    override val rosterLevel: RosterLevel,
    override val contract: Contract,
    override val military: MilitaryStatus,
    override val condition: Condition = Condition.HEALTHY,
    override val hidden: HiddenTraits,
    override val registeredName: String = name,
    override val uniformNumber: Int = 0,
    override val knownProspect: Boolean = false,
    val primaryPosition: Position,
    /** 포지션별 수비 적성 1~100. 주포지션이 가장 높다. */
    val defenseFitness: Map<Position, Int>,
    val ratings: BatterRatings,
) : Player() {
    override fun withUniformNumber(number: Int): Player = copy(uniformNumber = number)

    override fun withKnownProspect(known: Boolean): Player = copy(knownProspect = known)
    override fun rating(attribute: Attribute): Int = ratings[attribute]
    override fun ratingsMap(): Map<Attribute, Int> = ratings.toMap()
}

/** 투수. */
@Serializable
@SerialName("pitcher")
data class Pitcher(
    override val id: PlayerId,
    override val name: String,
    override val birthYear: Int,
    override val throwsWith: Hand,
    override val bats: Hand,
    override val origin: Origin,
    override val debutSeason: Int,
    override val teamId: TeamId?,
    override val rosterLevel: RosterLevel,
    override val contract: Contract,
    override val military: MilitaryStatus,
    override val condition: Condition = Condition.HEALTHY,
    override val hidden: HiddenTraits,
    override val registeredName: String = name,
    override val uniformNumber: Int = 0,
    override val knownProspect: Boolean = false,
    val role: PitcherRole,
    val ratings: PitcherRatings,
    /** 최고 구속(km/h). 표시값이며 시뮬레이션에 직접 쓰지 않는다 (docs/02). */
    val topSpeedKmh: Int,
) : Player() {
    override fun withUniformNumber(number: Int): Player = copy(uniformNumber = number)

    override fun withKnownProspect(known: Boolean): Player = copy(knownProspect = known)
    override fun rating(attribute: Attribute): Int = ratings[attribute]
    override fun ratingsMap(): Map<Attribute, Int> = ratings.toMap()
}
