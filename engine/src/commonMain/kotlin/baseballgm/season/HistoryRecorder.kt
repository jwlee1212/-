package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.AwardEntry
import baseballgm.league.BatSeason
import baseballgm.league.CareerSeason
import baseballgm.league.LeagueHistory
import baseballgm.league.PitchSeason
import baseballgm.league.SeasonRecord
import baseballgm.league.TeamFinish
import baseballgm.stats.LeagueConstants
import baseballgm.stats.Sabermetrics
import baseballgm.stats.War

/**
 * 끝난 시즌을 리그 역사에 적는다 (2026-10-01, 진단 3번).
 *
 * 스토브리그 맨 앞(성장·은퇴 전)에 부른다 — 시즌을 뛴 그 모습 그대로(그 시즌 소속·이름) 남겨야 하기 때문이다.
 * 정규시즌 1군 기록이 있는 선수만 한 줄씩 쌓는다 (2군 추정 성적은 통산에 넣지 않는다 — 현실의 KBO 통산 기록과 같다).
 */
class HistoryRecorder(private val balance: BalanceConfig) {

    private val war = War(balance, Sabermetrics(balance))

    fun record(state: SeasonState, history: LeagueHistory, awards: List<AwardEntry>): LeagueHistory {
        val constants = LeagueConstants.from(state.stats, balance)
        val careers = history.careers.toMutableMap()
        val ids = (state.stats.allBatting().keys + state.stats.allPitching().keys).sortedBy { it.value }
        ids.forEach { id ->
            val player = runCatching { state.player(id) }.getOrNull() ?: return@forEach
            val team = player.teamId ?: return@forEach
            val batting = state.stats.battingOf(id).total
            val pitching = state.stats.pitchingOf(id).total
            if (batting.plateAppearances == 0 && pitching.outs == 0) return@forEach
            val line = CareerSeason(
                season = state.season,
                team = team,
                name = player.registeredName,
                bat = BatSeason.of(batting).takeIf { batting.plateAppearances > 0 },
                pitch = PitchSeason.of(pitching).takeIf { pitching.outs > 0 },
                war = (war.of(player, state.stats, constants, state.league.team(team).parkFactor).war * WAR_ROUND).toInt() / WAR_ROUND,
            )
            careers[id] = careers[id].orEmpty().filter { it.season != state.season } + line
        }
        // 능력치는 1군 기록과 상관없이 소속 선수 전원 (2군 유망주가 어떻게 컸는지도 보여야 한다)
        val ratings = history.ratings.toMutableMap()
        state.allPlayers().filter { it.teamId != null }.forEach { player ->
            ratings[player.id] = ratings[player.id].orEmpty().filter { it.season != state.season } +
                baseballgm.league.RatingSnapshot.of(state.season, player)
        }
        val record = SeasonRecord(
            season = state.season,
            champion = state.postseason?.champion,
            runnerUp = state.postseason?.runnerUp,
            standings = state.standings.ranked().map { TeamFinish(it.teamId, it.wins, it.losses, it.ties) },
            awards = awards,
        )
        return history.copy(
            careers = careers,
            ratings = ratings,
            seasons = history.seasons.filter { it.season != state.season } + record,
        )
    }

    private companion object {
        const val WAR_ROUND = 10.0
    }
}
