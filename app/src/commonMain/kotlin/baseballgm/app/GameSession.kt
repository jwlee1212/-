package baseballgm.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import baseballgm.condition.FormModel
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.ManagerTendencies
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutedPlayer
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingView
import baseballgm.season.Offseason
import baseballgm.season.OffseasonReport
import baseballgm.season.RookieSupplier
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.season.WeekReport
import baseballgm.stats.BattingLine
import baseballgm.stats.PitchingLine
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import baseballgm.tactics.WeeklyPolicy
import kotlin.random.Random

/**
 * 화면과 엔진 사이의 유일한 창구.
 *
 * 화면은 엔진 객체를 직접 만지지 않고 전부 여기를 거친다. 특히 **타 팀 선수 정보는
 * [scout] 로만** 볼 수 있다 — 숨김 수치는 엔진 밖에서 아예 읽히지 않고(컴파일 차단),
 * 타 팀 능력치는 정확도에 따라 흐려진 범위로만 나온다 (불변 원칙 4).
 */
class GameSession(
    val balance: BalanceConfig,
    startingLeague: League,
    val userTeamId: TeamId,
    /** 신인을 만들어 주는 쪽. 엔진·화면은 선수를 만들 수 없어서 밖에서 받는다 (docs/09) */
    private val rookieSupplier: (League) -> RookieSupplier = { RookieSupplier { _, _, _, _ -> emptyList() } },
    seed: Long = 20260401L,
) {
    val strength = StrengthCalculator(balance)
    private val formModel = FormModel(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val calendar = SeasonCalendar.from(balance)
    private val random = Random(seed)

    private var loop = WeekLoop(balance, startingLeague)

    var state by mutableStateOf(SeasonState(startingLeague, calendar))
        private set

    val league: League get() = state.league

    /** 상태가 바뀔 때마다 올라간다. 화면은 이 값을 읽어 다시 그린다. */
    var revision by mutableStateOf(0)
        private set

    var lastReport by mutableStateOf<WeekReport?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    /** 직전 스토브리그 결과 (은퇴·신인·각성 등). 새 시즌 홈 화면에서 보여준다 */
    var lastOffseason by mutableStateOf<OffseasonReport?>(null)
        private set

    val userTeam get() = state.league.team(userTeamId)

    val week: Int get() = state.week

    val seasonOver: Boolean get() = state.isRegularSeasonOver

    fun calendarLabel(week: Int = state.week): String = state.calendar.label(week)

    // ---------- 진행 ----------

    /** 한 주 진행. 유저 팀 경기는 관전할 수 있게 이벤트까지 남긴다. */
    fun advanceWeek() {
        if (seasonOver) return
        busy = true
        lastReport = loop.playWeek(state, random, validate = false, highlightTeam = userTeamId)
        busy = false
        revision++
    }

    /** 목표 주차까지 자동 진행 (docs/07). 멈춤 조건은 화면에서 고른 목표로 단순화했다. */
    fun advanceUntil(targetWeek: Int) {
        busy = true
        while (!seasonOver && state.week <= targetWeek) {
            lastReport = loop.playWeek(state, random, validate = false, highlightTeam = userTeamId)
        }
        busy = false
        revision++
    }

    /**
     * 스토브리그를 치르고 다음 시즌으로 넘어간다 (docs/09).
     * 성장·노화 → 각성·급노쇠 → 은퇴 → 군 복무 → 계약 → 신인 유입 순서로 처리된다.
     */
    fun startNextSeason() {
        if (!seasonOver) return
        busy = true
        val (nextLeague, report) = Offseason(balance, strength).run(state, random, rookieSupplier(state.league))
        loop = WeekLoop(balance, nextLeague)
        state = SeasonState(nextLeague, calendar)
        lastOffseason = report
        lastReport = null
        busy = false
        revision++
    }

    // ---------- 조회 ----------

    fun teamsRanked() = state.standings.ranked()

    fun record(teamId: TeamId = userTeamId) = state.standings.record(teamId)

    fun rank(teamId: TeamId = userTeamId) = state.standings.rankOf(teamId)

    fun gamesBehind(teamId: TeamId = userTeamId) = state.standings.gamesBehind(teamId)

    fun roster(level: RosterLevel, teamId: TeamId = userTeamId): List<Player> =
        state.playersOf(teamId)
            .filter { it.rosterLevel == level }
            .sortedByDescending { strength.overallOf(it) }

    fun player(id: PlayerId): Player = state.player(id)

    fun overall(player: Player): Double = strength.overallOf(player)

    fun formLabel(player: Player): String = formModel.levelOf(player.condition.form)

    fun batting(id: PlayerId): BattingLine = state.stats.battingOf(id).total

    fun pitching(id: PlayerId): PitchingLine = state.stats.pitchingOf(id).total

    fun futuresBatting(id: PlayerId): BattingLine = state.stats.futuresBattingOf(id)

    fun futuresPitching(id: PlayerId): PitchingLine = state.stats.futuresPitchingOf(id)

    /**
     * 선수 정보를 스카우트 시선으로 본다.
     * 우리 팀이면 현재 능력치가 정확하고, 타 팀이면 1군·2군에 따라 범위가 넓어진다 (docs/02).
     */
    fun scout(player: Player): ScoutedPlayer = ScoutingView.of(player, accuracyFor(player), state.season)

    fun accuracyFor(player: Player): ScoutingAccuracy = when {
        player.teamId == userTeamId -> ScoutingAccuracy.OWN_TEAM
        player.rosterLevel == RosterLevel.FIRST_TEAM -> ScoutingAccuracy.MEDIUM
        else -> ScoutingAccuracy.LOW
    }

    fun isOwn(player: Player): Boolean = player.teamId == userTeamId

    /** 이번 주 유저 팀 일정. */
    fun weekSchedule(week: Int = state.week) =
        state.league.schedule.gamesInWeek(week).filter { it.involves(userTeamId) }

    fun sheet(teamId: TeamId = userTeamId): DirectiveSheet =
        managerAI.applyPolicy(
            managerAI.buildSheet(state.tendenciesOf(teamId), state.playersOf(teamId), state.season),
            state.policyOf(teamId),
        )

    fun manager(teamId: TeamId = userTeamId) = state.league.managerOf(teamId)

    fun tendencies(teamId: TeamId = userTeamId): ManagerTendencies = state.tendenciesOf(teamId)

    // ---------- 지시 ----------

    fun setPolicy(policy: WeeklyPolicy) {
        state.policies[userTeamId] = policy
        revision++
    }

    fun policy(): WeeklyPolicy = state.policyOf(userTeamId)

    /** 단장 방침. 감독 성향이 매주 이쪽으로 조금씩 끌려온다 (docs/06 반영률). */
    fun setDirection(direction: ManagerTendencies?) {
        if (direction == null) state.gmDirections.remove(userTeamId) else state.gmDirections[userTeamId] = direction
        revision++
    }

    fun direction(): ManagerTendencies? = state.gmDirections[userTeamId]

    // ---------- 기록실 ----------

    fun battingLeaders(limit: Int = 10): List<Pair<Player, BattingLine>> {
        val minimum = (record().games * PA_PER_GAME).toInt()
        return state.stats.battingLeaders(minimum) { it.battingAverage }
            .take(limit)
            .map { state.player(it.first) to it.second }
    }

    fun homeRunLeaders(limit: Int = 10): List<Pair<Player, BattingLine>> =
        state.stats.battingLeaders(0) { it.homeRuns.toDouble() }
            .take(limit)
            .map { state.player(it.first) to it.second }

    fun eraLeaders(limit: Int = 10): List<Pair<Player, PitchingLine>> {
        val minimum = record().games * OUTS_PER_GAME
        return state.stats.pitchingLeaders(minimum) { -it.era }
            .take(limit)
            .map { state.player(it.first) to it.second }
    }

    fun winLeaders(limit: Int = 10): List<Pair<Player, PitchingLine>> =
        state.stats.pitchingLeaders(0) { it.wins.toDouble() }
            .take(limit)
            .map { state.player(it.first) to it.second }

    fun isPitcher(player: Player): Boolean = player is Pitcher

    fun positionLabel(player: Player): String = when (player) {
        is Batter -> player.primaryPosition.label
        is Pitcher -> if (player.role.isReliever) "RP" else "SP"
    }

    private companion object {
        const val PA_PER_GAME = 3.1
        const val OUTS_PER_GAME = 3
    }
}
