package baseballgm.management

import baseballgm.io.BalanceConfig
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.stats.BoxScore
import kotlinx.serialization.Serializable

/** 단장이 내린 선수 결정의 종류 (docs/13 과거 선택의 메아리). */
@Serializable
enum class DecisionKind(val label: String) {
    DRAFT("드래프트 지명"),
    TRADE_IN("트레이드 영입"),
    TRADE_OUT("트레이드로 보냄"),
    FREE_AGENT("FA 영입"),
    FOREIGN("외국인 영입"),
    EXTENSION("다년계약"),
}

/**
 * 단장의 결정 한 건. 커리어 기록과 함께 세이브에 남는다.
 *
 * 선수 이름을 같이 적어 두는 이유: 은퇴하거나 리그를 떠난 선수도 커리어 연대기에서 이름이 보여야 한다.
 */
@Serializable
data class GmDecision(
    val season: Int,
    val week: Int,
    val kind: DecisionKind,
    val playerId: PlayerId,
    val playerName: String,
    /** 결정할 때 단장이 맡고 있던 구단 */
    val teamId: TeamId,
) {
    fun line(): String = "$season ${kind.label} · $playerName"
}

/**
 * 과거 선택의 메아리 (docs/13).
 *
 * 몇 년 전 내린 결정이 이번 주 경기에서 되돌아오는 순간을 찾아 비서가 한 줄로 알려 준다.
 * - 보낸 선수가 **우리를 상대로** 홈런을 치거나 승리 투수가 되면 → 아픈 메아리
 * - 데려온 선수(트레이드·FA·외국인)가 한 주에 홈런을 여러 개 치면 → 보람 있는 메아리
 * - 지명한 신인이 처음 1군 경기에 나오면 → 첫걸음
 *
 * 경기 결과는 바꾸지 않는다. 이미 일어난 일에서 의미를 골라낼 뿐이다.
 */
class Echoes(balance: BalanceConfig) {
    private val section = balance.section("narrative").section("echo")
    private val maxPerWeek = section.int("maxPerWeek")
    private val multiHomeRun = section.int("multiHomeRunWeek")

    /**
     * @param decisions 유저 단장의 결정 전부 (지난 시즌 것 + 올 시즌 것)
     * @param firstAppearances 이번 주에 **올 시즌 처음** 1군 경기에 나온 선수
     */
    fun find(
        season: Int,
        userTeam: TeamId,
        decisions: List<GmDecision>,
        games: List<BoxScore>,
        firstAppearances: Set<PlayerId>,
        playerOf: (PlayerId) -> Player?,
    ): List<String> {
        if (decisions.isEmpty()) return emptyList()
        val latest = decisions.groupBy { it.playerId }.mapValues { (_, list) -> list.maxBy { it.season * 100 + it.week } }
        val found = mutableListOf<String>()

        // 1) 보낸 선수가 우리 상대로 활약
        games.filter { it.home.teamId == userTeam || it.away.teamId == userTeam }.forEach { box ->
            val opponent = if (box.home.teamId == userTeam) box.away else box.home
            opponent.batting.forEach { (id, line) ->
                val decision = latest[id]?.takeIf { it.kind == DecisionKind.TRADE_OUT } ?: return@forEach
                val homeRuns = line.total.homeRuns
                if (homeRuns > 0) {
                    found += "${ago(season, decision.season)} 트레이드로 보낸 ${decision.playerName} 선수가 " +
                        "우리 상대로 홈런을 쳤어요. 이런 날도 있죠."
                }
            }
            box.winningPitcher?.let { id ->
                val decision = latest[id]?.takeIf { it.kind == DecisionKind.TRADE_OUT } ?: return@let
                if (opponent.pitching.containsKey(id)) {
                    found += "${ago(season, decision.season)} 보낸 ${decision.playerName} 선수가 우리 상대로 승리 투수가 됐어요."
                }
            }
        }

        // 2) 데려온 선수가 한 주에 홈런 여럿
        val weeklyHomeRuns = mutableMapOf<PlayerId, Int>()
        games.forEach { box ->
            val mine = when (userTeam) {
                box.home.teamId -> box.home
                box.away.teamId -> box.away
                else -> return@forEach
            }
            mine.batting.forEach { (id, line) -> weeklyHomeRuns[id] = (weeklyHomeRuns[id] ?: 0) + line.total.homeRuns }
        }
        weeklyHomeRuns.filterValues { it >= multiHomeRun }.forEach { (id, count) ->
            val decision = latest[id] ?: return@forEach
            if (decision.kind !in ACQUIRED) return@forEach
            found += "${ago(season, decision.season)} ${decision.kind.label}으로 데려온 ${decision.playerName} 선수가 " +
                "이번 주 홈런 ${count}개예요. 그때 결정이 빛을 보네요."
        }

        // 3) 지명한 신인의 첫 1군 경기
        firstAppearances.forEach { id ->
            val decision = latest[id]?.takeIf { it.kind == DecisionKind.DRAFT && it.teamId == userTeam } ?: return@forEach
            val player = playerOf(id) ?: return@forEach
            if (player.teamId != userTeam) return@forEach
            found += "${decision.season} 드래프트에서 뽑은 ${decision.playerName} 선수가 올해 처음 1군 무대를 밟았어요."
        }

        return found.distinct().take(maxPerWeek)
    }

    private fun ago(now: Int, then: Int): String = when (val years = now - then) {
        0 -> "올해"
        1 -> "작년에"
        else -> "${years}년 전"
    }

    private companion object {
        val ACQUIRED = setOf(DecisionKind.TRADE_IN, DecisionKind.FREE_AGENT, DecisionKind.FOREIGN)
    }
}
