package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.PositionNeed
import baseballgm.market.TeamMode
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.util.chance
import baseballgm.util.eulReul
import baseballgm.util.iGa
import kotlin.random.Random

/** 소문 한 건 (화면·테스트용) */
data class TradeRumor(val text: String, val reliable: Boolean, val genuine: Boolean)

/**
 * 트레이드 소문과 정보전 (2026-10-05 유저 요청 "루머와 정보전: ○○ 구단이 우리 유격수를 알아보고 있대요").
 *
 * 매주 `trade.rumors.chance` 로 한 건이 프런트 알림으로 들어온다.
 * - **진짜 소문**은 AI 의 실제 판단에서 나온다: 그 구단 자리가 비어(포지션 필요도 ≥ `offers.needThreshold`) 우리 1군 선수를
 *   원하고 있다 / 리빌딩 구단이 1군 베테랑을 내놓으려 한다. 트레이드 제안이 실제로 그 선수로 올 수 있다
 * - `falseRate` 만큼은 **헛소문** — 아무 구단·아무 선수
 * - 출처 꼬리표("믿을 만한 소식통" / "카더라")는 진짜일수록 소식통일 확률이 높지만 확실하지 않다. 헛소문도 소식통으로 올 수 있다
 *
 * 난수는 주차 시드로 따로 만든다 — 소문이 경기 결과를 흔들지 않는다 (불변 원칙 2). 상태를 바꾸지 않고 알림만 남긴다.
 */
class TradeRumors(private val balance: BalanceConfig, strength: StrengthCalculator, private val trades: TradeService) {
    private val cfg = balance.section("trade.rumors")
    private val offers = balance.section("trade.offers")
    private val positionNeed = PositionNeed(balance, strength)

    fun weekly(state: SeasonState, userTeam: TeamId, week: Int): TradeRumor? {
        if (!trades.isOpen(state)) return null
        val random = Random(state.league.seed * RUMOR_SEED + state.season * SEASON_SALT + week * WEEK_SALT)
        if (!random.chance(cfg.double("chance"))) return null
        val league = state.currentLeague()
        val aiTeams = league.teams.map { it.id }.filter { it != userTeam }.shuffled(random)
        val lie = random.chance(cfg.double("falseRate"))
        val text = (if (lie) falseRumor(state, userTeam, aiTeams, random) else trueRumor(state, userTeam, aiTeams, random)) ?: return null
        val reliable = random.chance(cfg.double(if (lie) "reliableIfFalse" else "reliableIfTrue"))
        state.inbox.add(week, InboxCategory.TRADE, userTeam, "[소문 · ${if (reliable) "믿을 만한 소식통" else "카더라"}] $text")
        return TradeRumor(text, reliable, !lie)
    }

    private fun trueRumor(state: SeasonState, userTeam: TeamId, aiTeams: List<TeamId>, random: Random): String? {
        val league = state.currentLeague()
        val ours = state.firstTeamOf(userTeam).filter { !it.condition.isInjured }
        val interest = {
            aiTeams.firstNotNullOfOrNull { team ->
                val roster = state.playersOf(team)
                val mode = trades.ai.modeOf(league, state.standings, team)
                ours.filter { positionNeed.of(roster, it) >= offers.double("needThreshold") }
                    .filter { trades.ai.playerValue(it, team, league, mode) >= offers.double("minTargetValue") }
                    .maxByOrNull { trades.ai.playerValue(it, team, league, mode) }
                    ?.let { interestText(state, team, it) }
            }
        }
        val selling = {
            aiTeams.filter { trades.ai.modeOf(league, state.standings, it) == TeamMode.REBUILD }.firstNotNullOfOrNull { team ->
                state.firstTeamOf(team).filter { it.ageIn(state.season) >= offers.int("veteranAge") && !it.condition.isInjured }
                    .maxByOrNull { trades.ai.playerValue(it, team, league, TeamMode.REBUILD) }
                    ?.let { sellingText(state, team, it) }
            }
        }
        return if (random.nextBoolean()) interest() ?: selling() else selling() ?: interest()
    }

    private fun falseRumor(state: SeasonState, userTeam: TeamId, aiTeams: List<TeamId>, random: Random): String? {
        val team = aiTeams.firstOrNull() ?: return null
        return if (random.nextBoolean()) {
            val ours = state.firstTeamOf(userTeam)
            if (ours.isEmpty()) null else interestText(state, team, ours[random.nextInt(ours.size)])
        } else {
            val theirs = state.firstTeamOf(team)
            if (theirs.isEmpty()) null else sellingText(state, team, theirs[random.nextInt(theirs.size)])
        }
    }

    private fun interestText(state: SeasonState, team: TeamId, player: Player): String =
        "${state.league.team(team).name.iGa()} 우리 ${roleOf(player)} ${player.registeredName.eulReul()} 알아보고 있대요."

    private fun sellingText(state: SeasonState, team: TeamId, player: Player): String =
        "${state.league.team(team).name.iGa()} ${roleOf(player)} ${player.registeredName.eulReul()} 내놓으려 한대요."

    private fun roleOf(player: Player): String = when (player) {
        is Batter -> POSITION_NAMES[player.primaryPosition.label] ?: player.primaryPosition.label
        is Pitcher -> if (player.role == baseballgm.model.PitcherRole.STARTER) "선발" else "불펜"
    }

    private companion object {
        const val RUMOR_SEED = 92_821L
        const val SEASON_SALT = 31_337L
        const val WEEK_SALT = 977L
        val POSITION_NAMES = mapOf(
            "C" to "포수", "1B" to "1루수", "2B" to "2루수", "3B" to "3루수", "SS" to "유격수",
            "LF" to "좌익수", "CF" to "중견수", "RF" to "우익수", "DH" to "지명타자",
        )
    }
}
