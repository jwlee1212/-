package baseballgm.app
import baseballgm.io.LeagueLoader
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.Pitcher
import baseballgm.model.RosterLevel
import baseballgm.scouting.ScoutingAccuracy
import baseballgm.scouting.ScoutingView
import baseballgm.tools.ProjectFiles
import kotlin.test.Test
class TmpPotentialStatsTest {
    @Test fun stats() {
        val balance = ProjectFiles.loadBalanceConfig()
        val league = LeagueLoader.parse(ProjectFiles.read(ProjectFiles.leaguePath(ProjectFiles.loadTeamTemplates().season)))
        val strength = StrengthCalculator(balance)
        val exact = ScoutingAccuracy.OWN_TEAM.precision
        fun pot(p: baseballgm.model.Player) = ScoutingView.potentialRange(p, exact).low.toDouble()
        fun pct(xs: List<Double>) = xs.sorted().let { s -> listOf(10, 25, 50, 75, 90, 97).joinToString(" ") { q -> "p$q=${"%.0f".format(s[(s.size - 1) * q / 100])}" } }
        val pool = league.draftPool.prospects.map { pot(it.player) }
        println("STAT pool potential n=${pool.size} ${pct(pool)} mean=${"%.1f".format(pool.average())}")
        println("STAT pool SP share=${league.draftPool.prospects.count { (it.player as? Pitcher)?.role?.isReliever == false }}/${pool.size}")
        val pros = league.players.filter { it.teamId != null }
        println("STAT pro potential ${pct(pros.map { pot(it) })}")
        println("STAT pro overall ${pct(pros.map { strength.overallOf(it) })}")
        val first = pros.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }
        println("STAT 1군 overall ${pct(first.map { strength.overallOf(it) })}")
        // 팀별 역할
        val aces = mutableListOf<Double>(); val starters = mutableListOf<Double>(); val semi = mutableListOf<Double>(); val bench = mutableListOf<Double>()
        val spAce = mutableListOf<Double>(); val sp5 = mutableListOf<Double>()
        league.teams.forEach { t ->
            val ps = pros.filter { it.teamId == t.id }
            val bats = ps.filterIsInstance<Batter>().map { strength.overallOf(it) }.sortedDescending()
            val sps = ps.filterIsInstance<Pitcher>().filter { !it.role.isReliever }.map { strength.overallOf(it) }.sortedDescending()
            val all = ps.map { strength.overallOf(it) }.sortedDescending()
            aces += all.take(2); spAce += sps.take(1); sp5 += sps.take(5)
            starters += bats.take(9) + sps.take(5)
            semi += bats.drop(9).take(5) + sps.drop(5).take(3)
            bench += all.drop(28)
        }
        fun m(x: List<Double>) = "%.1f".format(x.average())
        println("STAT 팀 상위2 ${m(aces)} / SP1 ${m(spAce)} / 주전(야수9+SP5) ${m(starters)} ${pct(starters)} / 준주전 ${m(semi)} / 29위 이하 ${m(bench)}")
        // 전성기 선수: 능력 vs 잠재력
        val prime = pros.filter { it.ageIn(league.season) in 28..31 }
        println("STAT 28~31세 n=${prime.size} overall-potential mean=${"%.1f".format(prime.map { strength.overallOf(it) - pot(it) }.average())}")
        val primeStar = starters.size
        // 주전들의 잠재력
        val starterPlayers = league.teams.flatMap { t ->
            val ps = pros.filter { it.teamId == t.id }
            ps.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(9) + ps.filterIsInstance<Pitcher>().filter { !it.role.isReliever }.sortedByDescending { strength.overallOf(it) }.take(5)
        }
        println("STAT 주전 잠재력 ${pct(starterPlayers.map { pot(it) })}")
    }
}
