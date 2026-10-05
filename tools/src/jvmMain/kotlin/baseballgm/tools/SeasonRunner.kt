package baseballgm.tools

import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.model.TeamId
import baseballgm.season.Postseason
import baseballgm.season.PostseasonResult
import baseballgm.season.SeasonCalendar
import baseballgm.season.SeasonState
import baseballgm.season.WeekLoop
import baseballgm.season.WeekReport
import kotlin.random.Random

/** 한 시즌을 끝까지 돌린 결과. */
data class SeasonResult(
    val state: SeasonState,
    val reports: List<WeekReport>,
    /** 포스트시즌 결과 (docs/14). 치르지 않았으면 null */
    val postseason: PostseasonResult? = null,
) {
    val validationProblems: List<String> get() = reports.flatMap { it.validationProblems }
}

/**
 * 정규시즌 한 시즌을 돌리는 도구.
 *
 * 콘솔·테스트·캘리브레이터가 모두 이것을 쓴다. 게임 진행(유저가 주마다 결정하는 흐름)은
 * M10 화면에서 [WeekLoop] 을 직접 쓰게 된다.
 */
class SeasonRunner(private val balance: BalanceConfig, private val league: League) {

    fun newSeason(scoutingLevels: Map<TeamId, Int> = emptyMap()): SeasonState =
        SeasonState.of(league, SeasonCalendar.from(balance), balance, scoutingLevels)

    fun playSeason(
        seed: Long,
        validate: Boolean = true,
        state: SeasonState = newSeason(),
        onWeek: ((WeekReport) -> Unit)? = null,
        /** 정규시즌이 끝나면 포스트시즌까지 치른다 (docs/14) */
        playPostseason: Boolean = true,
    ): SeasonResult {
        val loop = WeekLoop(balance, league)
        val random = Random(seed)
        val reports = mutableListOf<WeekReport>()
        while (!state.isRegularSeasonOver) {
            val report = loop.playWeek(state, random, validate)
            reports += report
            onWeek?.invoke(report)
        }
        val postseason = if (playPostseason) Postseason(balance, league).run(state, random).result else null
        return SeasonResult(state, reports, postseason)
    }
}
