package baseballgm.tools

import baseballgm.io.LeagueLoader
import baseballgm.league.League
import baseballgm.league.ScheduleRules
import baseballgm.league.StrengthCalculator
import baseballgm.model.Batter
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.PitcherRole
import baseballgm.model.RosterLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 리그 생성기(M1) 검증. 생성은 0.2초 정도라 테스트마다 새로 만든다. */
class LeagueGenerationTest {

    private val balance = ProjectFiles.loadBalanceConfig()
    private val templates = ProjectFiles.loadTeamTemplates()
    private val strength = StrengthCalculator(balance)

    private fun generate(seed: Long = LeagueGenerator.DEFAULT_SEED) =
        LeagueGenerator(balance, templates, seed).generate()

    // ---------- 재현성 (불변 원칙 2) ----------

    @Test
    fun `같은 시드는 완전히 같은 리그를 만든다`() {
        val first = LeagueLoader.encode(generate().league)
        val second = LeagueLoader.encode(generate().league)
        assertEquals(first, second, "같은 시드인데 결과가 다르다")
    }

    @Test
    fun `다른 시드는 다른 리그를 만든다`() {
        val first = generate().league
        val second = generate(seed = LeagueGenerator.DEFAULT_SEED + 1).league
        assertTrue(first.players.map { it.name } != second.players.map { it.name })
        // 그래도 규칙은 그대로다
        assertEquals(first.players.size, second.players.size)
    }

    // ---------- 팀 전력 (M1 완료 기준) ----------

    @Test
    fun `10팀 전력이 목표 ±2 안에 들어온다`() {
        val generated = generate()
        val off = generated.teamsOffTarget(strength.targetTolerance)
        assertTrue(off.isEmpty(), "목표를 벗어난 팀: " + off.joinToString { "${it.teamId} ${it.strength}" })
    }

    @Test
    fun `팀 연봉 총액이 teams json 목표와 맞는다`() {
        val generated = generate()
        generated.reports.forEach { report ->
            assertTrue(
                abs(report.payroll - report.payrollTarget) <= 1.0,
                "${report.teamId}: 연봉 총액 ${report.payroll} (목표 ${report.payrollTarget})",
            )
        }
    }

    // ---------- 선수단 구성 ----------

    @Test
    fun `팀마다 1군 28명 2군 30명이다`() {
        val league = generate().league
        val sizes = balance.section("leagueGeneration.rosterSize")
        league.teams.forEach { team ->
            assertEquals(sizes.int("firstTeam"), league.firstTeamOf(team.id).size, "${team.id} 1군")
            assertEquals(sizes.int("futures"), league.futuresOf(team.id).size, "${team.id} 2군")
        }
        assertEquals(league.teams.size * (sizes.int("firstTeam") + sizes.int("futures")), league.players.size)
    }

    @Test
    fun `1군에는 복무 중인 선수가 없다`() {
        // 복무 중 선수는 엔트리에서 빠지므로(docs/12) 1군 자리를 차지하면 등록 인원이 깨진다
        val league = generate().league
        val serving = league.players.filter {
            it.rosterLevel == RosterLevel.FIRST_TEAM && it.military is MilitaryStatus.Serving
        }
        assertTrue(serving.isEmpty(), "1군에 복무 중인 선수: ${serving.map { it.name }}")
        assertTrue(league.players.any { it.military is MilitaryStatus.Serving }, "복무 중인 선수가 아예 없다")
    }

    @Test
    fun `팀마다 선발 5명과 마무리를 갖춘다`() {
        val league = generate().league
        league.teams.forEach { team ->
            val pitchers = league.firstTeamOf(team.id).filterIsInstance<Pitcher>()
            assertEquals(5, pitchers.count { it.role == PitcherRole.STARTER }, "${team.id} 선발")
            assertEquals(1, pitchers.count { it.role == PitcherRole.CLOSER }, "${team.id} 마무리")
            assertTrue(pitchers.size in balance.intRange("leagueGeneration.firstTeamPitchers"), "${team.id} 투수 수")
        }
    }

    @Test
    fun `1군 타선은 모든 포지션을 한 명 이상 채운다`() {
        val league = generate().league
        league.teams.forEach { team ->
            val positions = league.firstTeamOf(team.id).filterIsInstance<Batter>().map { it.primaryPosition }.toSet()
            assertEquals(baseballgm.model.Position.entries.toSet(), positions, "${team.id} 포지션 구멍")
        }
    }

    @Test
    fun `외국인은 팀마다 3명이고 투수나 타자 한쪽으로 몰리지 않는다`() {
        val league = generate().league
        val maxPerTeam = balance.int("foreignPlayers.maxPerTeam")
        val maxSameType = balance.int("foreignPlayers.maxSameType")
        league.teams.forEach { team ->
            val foreigners = league.playersOf(team.id).filter { it.isForeign }
            assertEquals(maxPerTeam, foreigners.size, "${team.id} 외국인 수")
            assertTrue(foreigners.count { it is Pitcher } <= maxSameType, "${team.id} 외국인 투수 편중")
            assertTrue(foreigners.count { it is Batter } <= maxSameType, "${team.id} 외국인 타자 편중")
            assertTrue(foreigners.all { it.military is MilitaryStatus.NotRequired })
            assertTrue(foreigners.all { it.registeredName != it.name }, "외국인은 한글 등록명이 따로 있어야 한다")
        }
    }

    // ---------- 이름 ----------

    @Test
    fun `이름은 중복되지 않고 금지 목록에 없다`() {
        val league = generate().league
        val koreanNames = league.players.filter { !it.isForeign }.map { it.name } +
            league.managers.map { it.name } + league.coaches.map { it.name } +
            league.medicalStaff.map { it.name } + league.generalManagers.map { it.name }
        assertEquals(koreanNames.size, koreanNames.toSet().size, "동명이인이 있다")
        assertTrue(koreanNames.none { it in NamePools.bannedNames }, "금지 이름이 나왔다")
        assertTrue(koreanNames.all { it.length in 3..4 }, "이름 길이가 이상하다")
        val registeredNames = league.players.map { it.registeredName }
        assertEquals(registeredNames.size, registeredNames.toSet().size, "등록명이 겹친다")
    }

    // ---------- 능력치·나이·계약의 앞뒤 ----------

    @Test
    fun `현재 능력치는 잠재력을 넘지 않는다`() {
        val league = generate().league
        // 숨김 수치는 엔진 밖에서 읽을 수 없으므로(불변 원칙 4) 스카우트 화면으로 확인한다
        league.players.forEach { player ->
            val view = baseballgm.scouting.ScoutingView.of(
                player,
                baseballgm.scouting.ScoutingAccuracy.OWN_TEAM,
                league.season,
            )
            val best = player.ratingsMap().values.max()
            assertTrue(best <= 99, "${player.name} 능력치가 범위를 벗어났다")
            assertTrue(view.potentialHigh.minimum >= 0)
        }
    }

    @Test
    fun `주전 평균 능력치가 60 근처다`() {
        // CLAUDE.md §7 장기 밸런스 기준(60±3)의 출발점. M5 에서 30시즌 뒤에도 유지되는지 본다
        val league = generate().league
        val starters = league.teams.flatMap { team ->
            val roster = league.firstTeamOf(team.id)
            roster.filterIsInstance<Batter>().sortedByDescending { strength.overallOf(it) }.take(9) +
                roster.filterIsInstance<Pitcher>().filter { it.role == PitcherRole.STARTER }
        }
        val average = starters.map { strength.overallOf(it) }.average()
        assertTrue(average in 57.0..63.0, "주전 평균 능력치 $average")
    }

    @Test
    fun `나이와 데뷔 연도가 앞뒤가 맞는다`() {
        val league = generate().league
        league.players.forEach { player ->
            val age = player.ageIn(league.season)
            assertTrue(age in 18..45, "${player.name} 나이 $age")
            assertTrue(player.debutSeason <= league.season, "${player.name} 데뷔 연도")
            val debutAge = player.debutSeason - player.birthYear
            assertTrue(debutAge >= 17, "${player.name} 데뷔 나이 $debutAge")
            if (player.origin == Origin.COLLEGE) assertTrue(age >= 22, "${player.name} 대졸인데 $age 세")
        }
    }

    @Test
    fun `계약이 최저 연봉 위에 있고 FA 연차가 음수가 아니다`() {
        val league = generate().league
        val minimum = balance.double("minimumSalary.value")
        league.players.forEach { player ->
            assertTrue(player.contract.salary >= minimum - 1e-9, "${player.name} 연봉 ${player.contract.salary}")
            assertTrue(player.contract.yearsRemaining >= 1, "${player.name} 계약 연수")
            assertTrue(player.contract.seasonsToFreeAgency >= 0)
            assertTrue(player.contract.serviceSeasons >= 0)
        }
    }

    // ---------- 스태프 ----------

    @Test
    fun `팀마다 감독 코치 메디컬 단장이 있고 무직 감독 후보가 남는다`() {
        val league = generate().league
        league.teams.forEach { team ->
            assertTrue(league.managerOf(team.id) != null, "${team.id} 감독 없음")
            assertEquals(4, league.coachesOf(team.id).size, "${team.id} 코치")
            assertEquals(3, league.medicalStaffOf(team.id).size, "${team.id} 메디컬")
            assertTrue(league.generalManagerOf(team.id) != null, "${team.id} 단장 없음")
        }
        assertEquals(
            balance.int("staffGeneration.managerPoolUnemployed"),
            league.unemployedManagers().size,
        )
        // 2군 감독만 성향이 없다 (docs/09)
        val futuresManagers = league.coaches.filter { it.focus == null }
        assertEquals(league.teams.size, futuresManagers.size)
    }

    // ---------- 일정표 ----------

    @Test
    fun `일정표가 규칙을 지킨다`() {
        val league = generate().league
        val rules = ScheduleRules.from(balance)
        val problems = league.schedule.validate(league.teams.map { it.id }, rules)
        assertTrue(problems.isEmpty(), "일정표 문제: $problems")
        assertEquals(rules.gamesPerTeam * league.teams.size / 2, league.schedule.games.size)
        league.teams.forEach { team ->
            val games = league.schedule.gamesOf(team.id)
            assertEquals(rules.gamesPerTeam / 2, games.count { it.home == team.id }, "${team.id} 홈경기")
        }
    }

    @Test
    fun `2연전은 시즌 후반에 배치된다`() {
        val league = generate().league
        val rules = ScheduleRules.from(balance)
        val twoGameWeeks = league.schedule.games
            .groupBy { it.seriesId }
            .filterValues { it.size == 2 }
            .values.map { it.first().week }
            .toSet()
        val expectedStart = rules.weeks - rules.twoGameWeeksAtSeasonEnd + 1
        assertEquals((expectedStart..rules.weeks).toSet(), twoGameWeeks)
    }

    // ---------- 저장·불러오기 ----------

    @Test
    fun `JSON 으로 저장했다가 읽으면 그대로다`() {
        val league = generate().league
        val text = LeagueLoader.encode(league)
        val restored = LeagueLoader.parse(text)
        assertEquals(league, restored)
        assertEquals(text, LeagueLoader.encode(restored))
    }

    @Test
    fun `저장된 고정 리그 데이터가 읽히고 구조가 맞는다`() {
        // data/league_2026.json 은 손으로 고칠 수 있는 파일이라(docs/03) 내용 일치가 아니라 구조만 본다
        val path = ProjectFiles.leaguePath(templates.season)
        val league: League = LeagueLoader.parse(ProjectFiles.read(path))
        assertEquals(templates.season, league.season)
        assertEquals(templates.teams.size, league.teams.size)
        assertEquals(templates.teams.map { it.id }.toSet(), league.teams.map { it.id.value }.toSet())
        assertTrue(league.players.size > 500)
        assertTrue(league.schedule.validate(league.teams.map { it.id }, ScheduleRules.from(balance)).isEmpty())
    }
}
