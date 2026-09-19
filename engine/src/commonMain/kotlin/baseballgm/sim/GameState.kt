package baseballgm.sim

import baseballgm.model.PlayerId

/**
 * 베이스 위의 주자.
 *
 * @param responsiblePitcherId 이 주자를 내보낸 투수. 투수가 바뀌어도 따라다닌다 (docs/05 책임 주자)
 * @param reachedByError 실책·야수선택으로 나갔는가. 이 주자의 득점은 비자책
 */
data class Runner(
    val playerId: PlayerId,
    val responsiblePitcherId: PlayerId,
    val reachedByError: Boolean = false,
)

/**
 * 베이스 상태. 1루=비트0, 2루=비트1, 3루=비트2 로 24가지 상태를 만든다 (docs/05).
 * 주자 객체를 함께 들고 있어야 책임 투수와 자책점 판정을 할 수 있다.
 */
class BaseState {
    private val runners = arrayOfNulls<Runner>(BASE_COUNT)

    /** 1=1루, 2=2루, 3=3루 */
    operator fun get(base: Int): Runner? = runners[base - 1]

    operator fun set(base: Int, runner: Runner?) {
        runners[base - 1] = runner
    }

    val occupiedCode: Int
        get() = (if (runners[0] != null) 1 else 0) or
            (if (runners[1] != null) 2 else 0) or
            (if (runners[2] != null) 4 else 0)

    val count: Int get() = runners.count { it != null }

    fun isOccupied(base: Int): Boolean = runners[base - 1] != null

    fun isEmpty(base: Int): Boolean = runners[base - 1] == null

    fun occupiedBases(): List<Int> = (1..BASE_COUNT).filter { isOccupied(it) }

    fun remove(base: Int): Runner? {
        val runner = runners[base - 1]
        runners[base - 1] = null
        return runner
    }

    fun clear() {
        for (index in runners.indices) runners[index] = null
    }

    fun snapshot(): List<Runner?> = runners.toList()

    companion object {
        const val BASE_COUNT: Int = 3
    }
}

/**
 * 경기 상태. 시뮬레이터가 고쳐 나가고, 이벤트와 문자 중계는 읽기만 한다.
 *
 * @param virtualOuts 실책이 없었다면 잡혔을 아웃 수. 자책점 판정에 쓴다 (docs/05)
 */
class GameState(val rules: GameRules) {
    var inning: Int = 1
        internal set
    var half: Half = Half.TOP
        internal set
    var outs: Int = 0
        internal set
    var homeScore: Int = 0
        internal set
    var awayScore: Int = 0
        internal set
    var virtualOuts: Int = 0
        internal set

    val bases: BaseState = BaseState()

    val battingIsHome: Boolean get() = half == Half.BOTTOM

    val battingScore: Int get() = if (battingIsHome) homeScore else awayScore

    val fieldingScore: Int get() = if (battingIsHome) awayScore else homeScore

    /** 공격 팀 기준 점수 차 (음수면 지고 있다). */
    val scoreMargin: Int get() = battingScore - fieldingScore

    internal fun addRuns(count: Int) {
        if (battingIsHome) homeScore += count else awayScore += count
    }

    /** 9회말 이후, 홈팀이 앞서면 그 순간 경기가 끝난다 (끝내기). */
    fun isWalkOffSituation(): Boolean =
        half == Half.BOTTOM && inning >= rules.regulationInnings && homeScore > awayScore
}
