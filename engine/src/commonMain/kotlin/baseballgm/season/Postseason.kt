package baseballgm.season

import baseballgm.condition.FatigueModel
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.MedicalRole
import baseballgm.model.Pitcher
import baseballgm.model.PlayerId
import baseballgm.model.Position
import baseballgm.model.TeamId
import baseballgm.sim.GameRules
import baseballgm.sim.GameSimulator
import baseballgm.sim.RatingTables
import baseballgm.stats.BoxScore
import baseballgm.stats.StatsRecorder
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import baseballgm.tactics.WeeklyPolicy
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** 포스트시즌 단계 (docs/14). */
@Serializable
enum class PostseasonRound(val label: String) {
    WILDCARD("와일드카드"),
    SEMI_PLAYOFF("준플레이오프"),
    PLAYOFF("플레이오프"),
    KOREAN_SERIES("한국시리즈"),
}

/** 시리즈 한 판의 결과. */
@Serializable
data class SeriesResult(
    val round: PostseasonRound,
    /** 상위 시드 (홈 어드밴티지를 가진 쪽) */
    val higherSeed: TeamId,
    val lowerSeed: TeamId,
    val higherWins: Int,
    val lowerWins: Int,
    val ties: Int,
    val winner: TeamId,
) {
    val games: Int get() = higherWins + lowerWins + ties

    val loser: TeamId get() = if (winner == higherSeed) lowerSeed else higherSeed

    fun line(nameOf: (TeamId) -> String): String =
        "${round.label}: ${nameOf(higherSeed)} $higherWins - $lowerWins ${nameOf(lowerSeed)}" +
            (if (ties > 0) " (무 $ties)" else "") +
            " → ${nameOf(winner)} 진출"
}

/** 포스트시즌 전체 결과 (docs/14). */
@Serializable
data class PostseasonResult(
    val season: Int,
    /** 5강 진출 팀 (순위 순) */
    val participants: List<TeamId>,
    val series: List<SeriesResult>,
    val champion: TeamId,
    val runnerUp: TeamId,
) {
    /** 이 팀이 어디까지 갔는가. 진출하지 못했으면 null */
    fun reachedRound(teamId: TeamId): PostseasonRound? =
        series.filter { it.higherSeed == teamId || it.lowerSeed == teamId }.maxByOrNull { it.round.ordinal }?.round

    fun summary(nameOf: (TeamId) -> String): String = "${season} 한국시리즈 우승: ${nameOf(champion)}"
}

/**
 * 진행 중인 시리즈 하나 (2026-10-03 한 경기씩 진행).
 *
 * 와일드카드는 4위가 [higherWins] 1승을 안고 시작한다.
 */
@Serializable
data class SeriesProgress(
    val round: PostseasonRound,
    val higherSeed: TeamId,
    val lowerSeed: TeamId,
    /** 이만큼 이기면 시리즈가 끝난다 */
    val winsNeeded: Int,
    val higherWins: Int = 0,
    val lowerWins: Int = 0,
    val ties: Int = 0,
    /** 실제로 치른 경기 수 (안고 시작한 승리는 세지 않는다) */
    val gamesPlayed: Int = 0,
    /** 상위 시드가 전 경기 홈인가 (와일드카드) */
    val higherHostsAll: Boolean = false,
    /** 무승부면 상위 시드가 바로 진출하는가 (와일드카드: 4위는 1무로도 올라간다) */
    val higherAdvancesOnTie: Boolean = false,
) {
    fun involves(teamId: TeamId): Boolean = higherSeed == teamId || lowerSeed == teamId

    fun opponentOf(teamId: TeamId): TeamId = if (teamId == higherSeed) lowerSeed else higherSeed

    /** 이긴 팀. 아직 안 끝났으면 null */
    val winner: TeamId?
        get() = when {
            higherWins >= winsNeeded -> higherSeed
            lowerWins >= winsNeeded -> lowerSeed
            higherAdvancesOnTie && ties > 0 -> higherSeed
            // 무승부가 끝없이 이어지는 일을 막는 상한. 그때는 상위 시드가 올라간다
            gamesPlayed >= MAX_GAMES -> if (higherWins >= lowerWins) higherSeed else lowerSeed
            else -> null
        }

    val isOver: Boolean get() = winner != null

    /** 다음 경기에서 상위 시드가 홈인가. 1·2차전과 6·7차전은 상위 시드 홈 (단순화한 홈 배정) */
    val higherHomeNext: Boolean get() = higherHostsAll || HOME_PATTERN[gamesPlayed % HOME_PATTERN.size]

    fun winsOf(teamId: TeamId): Int = if (teamId == higherSeed) higherWins else lowerWins

    fun toResult(): SeriesResult =
        SeriesResult(round, higherSeed, lowerSeed, higherWins, lowerWins, ties, checkNotNull(winner) { "시리즈가 안 끝났다" })

    companion object {
        /** 무승부가 이어져도 시리즈가 끝나도록 둔 상한 */
        const val MAX_GAMES = 12
        private val HOME_PATTERN = listOf(true, true, false, false, false, true, true)
    }
}

/**
 * 포스트시즌이 어디까지 왔는가 (2026-10-03). 세이브에 들어간다.
 *
 * 유저가 시리즈 사이·경기 사이에 결정을 내리므로 포스트시즌을 한 번에 돌리지 않고 이 상태를 들고 한 경기씩 간다.
 */
@Serializable
data class PostseasonProgress(
    val season: Int,
    /** 5강 (순위 순) */
    val participants: List<TeamId>,
    /** 끝난 시리즈 */
    val completed: List<SeriesResult> = emptyList(),
    /** 지금 시리즈. 포스트시즌이 끝나면 null */
    val current: SeriesProgress? = null,
    /** 포스트시즌 며칠째인가 (연투·피로 계산용). 경기마다, 시리즈 사이 쉬는 날마다 하루씩 간다 */
    val day: Int = 0,
    /** 치른 경기 수 (경기마다 다른 시드를 뽑는 데 쓴다) */
    val gamesPlayed: Int = 0,
    /**
     * 팀별 방침. 없으면 총력전 — 짧은 시리즈라 감독은 아낄 이유가 없다 (docs/06).
     * 단장은 자기 팀 방침만 바꾼다
     */
    val policies: Map<TeamId, WeeklyPolicy> = emptyMap(),
    /** 다음 경기에 쉬게 할 선수 (단장 지시). 그 선수 팀이 경기를 치르면 비운다 */
    val resting: Set<PlayerId> = emptySet(),
) {
    val isFinished: Boolean get() = current == null

    fun policyOf(teamId: TeamId): WeeklyPolicy = policies[teamId] ?: WeeklyPolicy.ALL_OUT

    /** 아직 탈락하지 않았는가 (진출 못 한 팀은 false) */
    fun isAlive(teamId: TeamId): Boolean =
        teamId in participants && completed.none { it.loser == teamId } && !isFinished

    /**
     * 엔트리를 바꿀 수 있는가. 살아 있는 팀은 **자기 시리즈를 시작하기 전까지** 바꿀 수 있다 —
     * 현실 포스트시즌 엔트리도 시리즈마다 새로 낸다. 시리즈 도중에는 못 바꾼다
     */
    fun entryOpenFor(teamId: TeamId): Boolean {
        if (!isAlive(teamId)) return false
        val series = current ?: return false
        return !series.involves(teamId) || series.gamesPlayed == 0
    }

    /** 이 팀이 다음에 치를 시리즈 단계. 탈락했으면 null */
    fun nextRoundOf(teamId: TeamId): PostseasonRound? {
        if (!isAlive(teamId)) return null
        val series = current ?: return null
        if (series.involves(teamId)) return series.round
        // 아직 차례가 안 왔다: 시드대로 기다리는 단계 (1위 한국시리즈, 2위 플레이오프, 3위 준플레이오프)
        return when (participants.indexOf(teamId)) {
            0 -> PostseasonRound.KOREAN_SERIES
            1 -> PostseasonRound.PLAYOFF
            2 -> PostseasonRound.SEMI_PLAYOFF
            else -> PostseasonRound.WILDCARD
        }
    }
}

/**
 * 포스트시즌 (docs/14 확정 규정).
 *
 * 5강이 와일드카드 → 준플레이오프 → 플레이오프 → 한국시리즈로 올라간다. 규정은 확정된 것을 그대로 쓴다.
 *
 * - 와일드카드: **4위가 1승을 안고 시작**한다. 전 경기 4위 홈이고, 4위는 1승 또는 1무로 진출한다
 *   (5위는 2연승이 필요하다)
 * - 준PO·PO 5전 3선승, 한국시리즈 7전 4선승
 * - 연장 15회까지, 동점이면 **무승부로 끝내고 시리즈에 경기를 더한다**
 *
 * 2026-10-03: 한 경기씩 진행한다. [start] 로 대진을 짜고 [playNextGame] 으로 한 경기씩 치른다.
 * 단장은 경기 사이에 자기 팀 방침과 휴식 지시를 [PostseasonProgress] 에 적고, 시리즈 시작 전에는 엔트리도 바꾼다
 * ([RosterActions] 가 [PostseasonProgress.entryOpenFor] 를 본다). [run] 은 처음부터 끝까지 한 번에 치르는 지름길이다.
 *
 * 시뮬레이터는 정규시즌과 같은 것을 쓰고 [GameRules] 만 다르다 (불변 원칙 5). 경기마다 하루가 지나고
 * 시리즈 사이에는 쉬는 날이 있어서, 불펜 연투 제한과 피로 회복이 정규시즌과 똑같이 돈다.
 */
class Postseason(
    private val balance: BalanceConfig,
    private val league: League,
) {
    private val tables = RatingTables(balance)
    private val strength = StrengthCalculator(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val simulator = GameSimulator(balance, tables, GameRules.postseason(balance), league.environment)
    private val fatigueModel = FatigueModel(balance, tables)

    private val series = balance.section("gameRules.series")
    private val wildcard = balance.section("gameRules.wildcard")
    private val spots = balance.int("postseason.spots")
    private val restDaysBetweenSeries = balance.int("postseason.restDaysBetweenSeries")

    /**
     * 치른 경기. [events] 는 문자 중계용 이벤트 스트림이다 (불변 원칙 5 — 중계는 이 스트림을 문장으로 바꿀 뿐).
     * 세이브에는 넣지 않는다 (크기). 불러온 뒤에는 결과(박스스코어)만 남는다
     */
    data class PlayedGame(
        val round: PostseasonRound,
        val box: BoxScore,
        val events: List<baseballgm.sim.GameEvent> = emptyList(),
        /** 시리즈 몇 차전인가 (1부터) */
        val gameNumber: Int = 0,
    )

    data class Outcome(val result: PostseasonResult, val games: List<PlayedGame>)

    /**
     * 대진을 짜고 와일드카드를 연다. 이미 시작했으면 그대로 둔다.
     *
     * 정규시즌 마지막 주의 휴식 지시·이번 주만 바꾼 방침은 여기서 끝낸다 — 포스트시즌 휴식은 [PostseasonProgress.resting] 이 맡는다.
     */
    fun start(state: SeasonState): PostseasonProgress {
        state.postseasonProgress?.let { return it }
        val ranked = state.standings.ranked().map { it.teamId }
        require(ranked.size >= spots) { "포스트시즌에 나갈 팀이 모자란다" }
        val participants = ranked.take(spots)
        state.restingThisWeek.clear()
        val headStart = wildcard.int("higherSeedStartsWithWins")
        val progress = PostseasonProgress(
            season = state.season,
            participants = participants,
            current = SeriesProgress(
                round = PostseasonRound.WILDCARD,
                higherSeed = participants[3],
                lowerSeed = participants[4],
                winsNeeded = headStart + 1,
                higherWins = headStart,
                higherHostsAll = wildcard.boolean("higherSeedHostsAll"),
                higherAdvancesOnTie = wildcard.boolean("higherSeedAdvancesOnTie"),
            ),
        )
        state.postseasonProgress = progress
        return progress
    }

    /**
     * 지금 시리즈의 다음 경기를 치른다. 시리즈가 끝나면 다음 시리즈를 열고, 한국시리즈가 끝나면
     * [SeasonState.postseason] 에 결과를 적는다. 이미 끝났으면 null.
     *
     * 포스트시즌 기록은 정규시즌 기록에 합치지 않는다 — 현실처럼 따로 집계하고, 정규시즌 타이틀(타율·평균자책)이
     * 포스트시즌 성적으로 흔들리지 않게 하려는 것이다.
     */
    fun playNextGame(state: SeasonState, random: Random): PlayedGame? {
        val progress = state.postseasonProgress ?: start(state)
        val current = progress.current ?: return null
        val higherHome = current.higherHomeNext
        val homeId = if (higherHome) current.higherSeed else current.lowerSeed
        val awayId = if (higherHome) current.lowerSeed else current.higherSeed
        val absoluteDay = absoluteDayOf(state, progress.day)
        val home = gameTeamOf(state, sheetFor(state, progress, homeId), homeId, absoluteDay, resting = progress.resting)
        val away = gameTeamOf(state, sheetFor(state, progress, awayId), awayId, absoluteDay, resting = progress.resting)
        val events = simulator.simulate(home, away, league.team(homeId).parkFactor, random)
        val played = StatsRecorder.record(events)
        val playedIds = applyFatigue(state, played, absoluteDay)
        recoverOneDay(state, progress.participants, playedIds)

        val higherWon = when {
            played.tie -> null
            played.homeScore > played.awayScore -> homeId == current.higherSeed
            else -> awayId == current.higherSeed
        }
        val updated = current.copy(
            higherWins = current.higherWins + if (higherWon == true) 1 else 0,
            lowerWins = current.lowerWins + if (higherWon == false) 1 else 0,
            ties = current.ties + if (higherWon == null) 1 else 0,
            gamesPlayed = current.gamesPlayed + 1,
        )
        val game = PlayedGame(current.round, played, events, updated.gamesPlayed)
        val restingLeft = progress.resting.filterNot { state.player(it).teamId == homeId || state.player(it).teamId == awayId }.toSet()
        val next = progress.copy(day = progress.day + 1, gamesPlayed = progress.gamesPlayed + 1, resting = restingLeft)
        state.postseasonProgress = if (updated.isOver) closeSeries(state, next, updated) else next.copy(current = updated)
        return game
    }

    /** 지금 시리즈를 끝까지 치른다. [randomFor] 는 포스트시즌 통산 몇 번째 경기인지를 받아 난수를 준다 */
    fun playSeries(state: SeasonState, randomFor: (gameIndex: Int) -> Random): List<PlayedGame> {
        val round = state.postseasonProgress?.current?.round ?: return emptyList()
        val games = mutableListOf<PlayedGame>()
        while (state.postseasonProgress?.current?.round == round) {
            val index = state.postseasonProgress!!.gamesPlayed
            games += playNextGame(state, randomFor(index)) ?: break
        }
        return games
    }

    /**
     * 포스트시즌을 처음부터 끝까지 한 번에 치른다 (도구·콘솔·자동 진행). 모든 팀이 감독 기본값(총력전)으로 뛴다.
     */
    fun run(state: SeasonState, random: Random): Outcome {
        start(state)
        val games = mutableListOf<PlayedGame>()
        while (true) games += playNextGame(state, random) ?: break
        return Outcome(result = checkNotNull(state.postseason), games = games)
    }

    /**
     * 감독이 이 팀의 다음 경기에 낼 선발 (화면의 "감독 구상"). 순번을 넘기지 않는다
     */
    fun plannedStarter(state: SeasonState, teamId: TeamId): PlayerId? {
        val progress = state.postseasonProgress ?: return null
        val starters = sheetFor(state, progress, teamId).rotation.starters
        if (starters.isEmpty()) return null
        return starters[state.peekRotationIndex(teamId).mod(starters.size)]
    }

    private fun closeSeries(state: SeasonState, progress: PostseasonProgress, finished: SeriesProgress): PostseasonProgress {
        val completed = progress.completed + finished.toResult()
        val winner = checkNotNull(finished.winner)
        val participants = progress.participants
        val nextSeries = when (finished.round) {
            PostseasonRound.WILDCARD -> bestOf(PostseasonRound.SEMI_PLAYOFF, participants[2], winner, series.int("semiPlayoff"))
            PostseasonRound.SEMI_PLAYOFF -> bestOf(PostseasonRound.PLAYOFF, participants[1], winner, series.int("playoff"))
            PostseasonRound.PLAYOFF -> bestOf(PostseasonRound.KOREAN_SERIES, participants[0], winner, series.int("koreanSeries"))
            PostseasonRound.KOREAN_SERIES -> null
        }
        if (nextSeries == null) {
            state.postseason = PostseasonResult(
                season = state.season,
                participants = participants,
                series = completed,
                champion = winner,
                runnerUp = finished.toResult().loser,
            )
        } else {
            // 시리즈 사이 쉬는 날 (경기 없이 하루씩 회복)
            repeat(restDaysBetweenSeries) { recoverOneDay(state, participants, emptySet()) }
        }
        return progress.copy(
            completed = completed,
            current = nextSeries,
            day = progress.day + if (nextSeries == null) 0 else restDaysBetweenSeries,
        )
    }

    private fun bestOf(round: PostseasonRound, higher: TeamId, lower: TeamId, games: Int): SeriesProgress =
        SeriesProgress(round = round, higherSeed = higher, lowerSeed = lower, winsNeeded = games / 2 + 1)

    /** 정규시즌이 끝난 다음 날부터 센다 (연투·피로 계산이 정규시즌과 이어진다) */
    private fun absoluteDayOf(state: SeasonState, day: Int): Int =
        state.absoluteDay(state.calendar.regularSeasonWeeks + 1, 0) + day

    /**
     * 방침을 얹은 규칙표. 단장 휴식 지시를 받은 선수는 라인업에서 아예 뺀다 (정규시즌 [WeekLoop] 과 같은 방식).
     */
    private fun sheetFor(state: SeasonState, progress: PostseasonProgress, teamId: TeamId): DirectiveSheet {
        val roster = state.playersOf(teamId).filterNot { it.id in progress.resting }
        val base = managerAI.buildSheet(state.tendenciesOf(teamId), roster, state.season)
        return managerAI.applyPolicy(base, progress.policyOf(teamId))
    }

    /** 경기에 나선 선수에게 피로를 얹는다. 나선 선수 id 를 돌려준다 */
    private fun applyFatigue(state: SeasonState, box: BoxScore, absoluteDay: Int): Set<PlayerId> {
        val played = mutableSetOf<PlayerId>()
        listOf(box.home, box.away).forEach { team ->
            team.pitching.forEach { (pitcherId, line) ->
                val pitches = line.total.pitches
                if (pitches <= 0 && line.total.outs <= 0) return@forEach
                val pitcher = state.player(pitcherId)
                val consecutive = state.usage.consecutiveDays(pitcherId, absoluteDay)
                state.usage.record(pitcherId, absoluteDay, pitches)
                played += pitcherId
                state.update(
                    pitcher.withCondition(
                        pitcher.condition.copy(
                            fatigue = fatigueModel.afterPitching(pitcher.condition.fatigue, pitches, consecutive),
                        ),
                    ),
                )
            }
            team.batting.forEach { (batterId, line) ->
                if (line.total.plateAppearances <= 0) return@forEach
                val batter = state.player(batterId)
                played += batterId
                val position = (batter as? Batter)?.primaryPosition ?: Position.DESIGNATED_HITTER
                state.update(
                    batter.withCondition(
                        batter.condition.copy(fatigue = fatigueModel.afterPlaying(batter.condition.fatigue, position)),
                    ),
                )
            }
        }
        return played
    }

    /**
     * 하루가 지났다: 포스트시즌 팀 선수의 피로 회복 (정규시즌의 하루 회복과 같은 식).
     * 폼은 움직이지 않는다 (임시 결정 — 짧은 기간이라 정규시즌 마지막 폼을 그대로 들고 간다).
     */
    private fun recoverOneDay(state: SeasonState, teams: List<TeamId>, playedToday: Set<PlayerId>) {
        teams.forEach { teamId ->
            val conditioning = league.medicalStaffOf(teamId).firstOrNull { it.role == MedicalRole.CONDITIONING_COACH }?.grade ?: 0
            state.playersOf(teamId).forEach { player ->
                val fatigue = fatigueModel.recover(player, state.season, player.id in playedToday, conditioning)
                state.update(player.withCondition(player.condition.copy(fatigue = fatigue)))
            }
        }
    }
}
