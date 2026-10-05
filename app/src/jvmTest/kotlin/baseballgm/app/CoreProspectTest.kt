package baseballgm.app

import baseballgm.app.screen.tagOf
import baseballgm.io.LeagueLoader
import baseballgm.scouting.PotentialGrade
import baseballgm.scouting.PotentialScale
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 팀내 핵심 유망주 표시 (유저 요청 2026-10-04, balance `scouting.coreProspect`) */
class CoreProspectTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
    private val maxAge = balance.int("scouting.coreProspect.maxAge")
    private val perTeam = balance.int("scouting.coreProspect.perTeam")

    @Test
    fun `구단마다 어린 선수 중 잠재력 상위 몇 명에게만 붙고 화면 표시에 실린다`() {
        val session = GameSession(balance, league, league.teams.first().id)
        val scale = PotentialScale.from(balance)
        val perTeamCounts = league.teams.map { team ->
            val squad = session.state.playersOf(team.id)
            val core = squad.filter { session.isCoreProspect(it) }
            assertTrue(core.size <= perTeam, "${team.name} ${core.size}명")
            core.forEach { player ->
                assertTrue(player.ageIn(league.season) <= maxAge, "${player.registeredName} 나이")
                // 규칙과 같은 기준: 우리 스카우트 시선으로 본 잠재력 범위의 가운데가 B 이상
                val center = baseballgm.scouting.ScoutingView.potentialRange(player, session.accuracyFor(player)).center
                assertTrue(scale.gradeOf(center) >= PotentialGrade.B, "${player.registeredName} 잠재력이 낮다 ($center)")
                assertTrue(session.tagOf(player).coreProspect, "화면 표시에 안 실렸다")
            }
            core.size
        }
        println("CORE 구단별 핵심 유망주 수 $perTeamCounts")
        assertTrue(perTeamCounts.sum() >= league.teams.size, "핵심 유망주가 거의 없다")

        // 우리 팀: 정확히 보이니, 표시된 선수보다 잠재력이 높은 어린 선수가 표시 밖에 있으면 안 된다
        val ours = session.state.playersOf(session.userTeamId).filter { it.ageIn(league.season) <= maxAge && !it.isForeign }
        val potentialOf = { p: baseballgm.model.Player -> baseballgm.scouting.ScoutingView.potentialRange(p, session.accuracyFor(p)).center }
        val marked = ours.filter { session.isCoreProspect(it) }
        val unmarked = ours.filterNot { session.isCoreProspect(it) }
        if (marked.size == perTeam) {
            assertTrue(unmarked.all { u -> marked.all { m -> potentialOf(u) <= potentialOf(m) } }, "더 높은 유망주가 빠졌다")
        }
        // 같은 상태면 같은 결과
        assertEquals(marked.map { it.id }, ours.filter { session.isCoreProspect(it) }.map { it.id })
    }
}
