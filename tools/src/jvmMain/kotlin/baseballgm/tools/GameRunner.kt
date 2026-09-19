package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.model.TeamId
import baseballgm.sim.GameEvent
import baseballgm.sim.GameRules
import baseballgm.sim.GameSimulator
import baseballgm.sim.GameTeam
import baseballgm.sim.PitcherAvailability
import baseballgm.sim.RatingTables
import baseballgm.stats.BoxScore
import baseballgm.stats.BoxScoreValidator
import baseballgm.stats.StatsRecorder
import baseballgm.tactics.DirectiveSheet
import baseballgm.tactics.ManagerAI
import baseballgm.tactics.WeeklyPolicy
import kotlin.random.Random

/** 경기 하나의 결과: 이벤트 스트림과 거기서 뽑은 박스스코어. */
data class PlayedGame(val events: List<GameEvent>, val box: BoxScore)

/**
 * 리그 데이터로 경기를 돌리는 도구.
 *
 * 각 팀의 사전 지시 규칙표는 감독 성향으로 만든다 (docs/06). 주간 루프·로테이션 관리는 M4 에서
 * `season` 패키지가 맡고, 여기서는 시뮬레이터를 대량으로 돌리기 위한 최소한의 껍데기만 만든다.
 */
class GameRunner(
    private val balance: BalanceConfig,
    private val league: League,
    private val policy: WeeklyPolicy = WeeklyPolicy.NORMAL,
) {
    private val tables = RatingTables(balance)
    private val strength = StrengthCalculator(balance)
    private val managerAI = ManagerAI(balance, strength)
    private val simulator = GameSimulator(balance, tables, GameRules.regularSeason(balance), league.environment)
    private val sheets = mutableMapOf<TeamId, DirectiveSheet>()

    /** 팀의 규칙표. 감독 성향 → 규칙표 → 주간 방침 적용 순서로 만든다. */
    fun sheetFor(teamId: TeamId): DirectiveSheet = sheets.getOrPut(teamId) {
        val base = managerAI.buildSheet(league.managerOf(teamId), league.playersOf(teamId), league.season)
        managerAI.applyPolicy(base, policy)
    }

    fun play(homeId: TeamId, awayId: TeamId, rotationIndex: Int, seed: Long): PlayedGame =
        playWithRotations(homeId, awayId, rotationIndex, rotationIndex, seed)

    fun playWithRotations(
        homeId: TeamId,
        awayId: TeamId,
        homeRotation: Int,
        awayRotation: Int,
        seed: Long,
        homeAvailability: PitcherAvailability = PitcherAvailability.ALL_READY,
        awayAvailability: PitcherAvailability = PitcherAvailability.ALL_READY,
    ): PlayedGame {
        val home = gameTeam(homeId, homeRotation, homeAvailability)
        val away = gameTeam(awayId, awayRotation, awayAvailability)
        val events = simulator.simulate(home, away, league.team(homeId).parkFactor, Random(seed))
        return PlayedGame(events, StatsRecorder.record(events))
    }

    /** 규칙표를 직접 지정해 경기를 돌린다 (시나리오 테스트용). */
    fun playWithSheets(
        homeId: TeamId,
        awayId: TeamId,
        homeSheet: DirectiveSheet,
        awaySheet: DirectiveSheet,
        homeStarter: baseballgm.model.PlayerId = homeSheet.rotation.starters.first(),
        awayStarter: baseballgm.model.PlayerId = awaySheet.rotation.starters.first(),
        homeAvailability: PitcherAvailability = PitcherAvailability.ALL_READY,
        awayAvailability: PitcherAvailability = PitcherAvailability.ALL_READY,
        seed: Long,
    ): PlayedGame {
        val home = GameTeam(homeId, rosterOf(homeId), homeSheet, homeStarter, homeAvailability)
        val away = GameTeam(awayId, rosterOf(awayId), awaySheet, awayStarter, awayAvailability)
        val events = simulator.simulate(home, away, league.team(homeId).parkFactor, Random(seed))
        return PlayedGame(events, StatsRecorder.record(events))
    }

    /**
     * 일정표 순서대로 [limit] 경기를 돌린다. 일정표(720경기)보다 많이 요청하면 처음부터 다시 돈다.
     * 각 경기는 시드에서 파생된 고정 난수를 쓴다 (같은 시드 → 같은 결과).
     */
    fun playSchedule(limit: Int, seed: Long, onGame: (PlayedGame) -> Unit) {
        val all = league.schedule.games
        val games = List(limit) { all[it % all.size] }
        val rotationCounter = mutableMapOf<TeamId, Int>()
        games.forEachIndexed { index, game ->
            val homeRotation = rotationCounter.getOrElse(game.home) { 0 }
            val awayRotation = rotationCounter.getOrElse(game.away) { 0 }
            rotationCounter[game.home] = homeRotation + 1
            rotationCounter[game.away] = awayRotation + 1
            onGame(playWithRotations(game.home, game.away, homeRotation, awayRotation, seed + index))
        }
    }

    /** 검증까지 함께 돌린다. 실패하면 예외. */
    fun playAndValidate(homeId: TeamId, awayId: TeamId, rotationIndex: Int, seed: Long): PlayedGame {
        val played = play(homeId, awayId, rotationIndex, seed)
        BoxScoreValidator.validateOrThrow(played.box)
        return played
    }

    fun rosterOf(teamId: TeamId) = league.playersOf(teamId).associateBy { it.id }

    private fun gameTeam(teamId: TeamId, rotationIndex: Int, availability: PitcherAvailability): GameTeam {
        val sheet = sheetFor(teamId)
        val starters = sheet.rotation.starters
        val starter = starters[rotationIndex.mod(starters.size)]
        return GameTeam(teamId, rosterOf(teamId), sheet, starter, availability)
    }
}
