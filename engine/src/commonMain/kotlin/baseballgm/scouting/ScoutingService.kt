package baseballgm.scouting

import baseballgm.development.withRatings

import baseballgm.io.BalanceConfig
import baseballgm.model.Player
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId

/**
 * "누가 누구를 얼마나 정확히 보는가"를 한 군데서 정한다.
 *
 * 화면·AI 어느 쪽이든 선수를 볼 때는 여기서 정확도를 받아 [ScoutingView] 에 넘긴다.
 * 정확도 판단이 여러 곳에 흩어지면 "어떤 화면에서는 타 팀 선수가 정확히 보이는" 사고가 나기 때문이다
 * (불변 원칙 4).
 */
/** 공개 기록 정도 ([ScoutingService.publicRecordOf]) */
enum class PublicRecord {
    /** 아마추어 · 기록 없음 */
    NONE,

    /** 프로 선수 (기록이 적다) */
    PROFESSIONAL,

    /** 준주전급 — 1군에서 충분히 뛰었다 */
    ESTABLISHED,
}

class ScoutingService(balance: BalanceConfig) {

    private companion object {
        /** "아무리 더 봐도"를 대신하는 주차. 한 시즌(약 26주)보다 훨씬 길면 된다 */
        const val SATURATION_WEEKS = 1_000
    }

    val budget: ScoutingBudget = ScoutingBudget(balance)

    /** 잠재력 등급 기준선 (balance.json scouting.potentialGrades) */
    val potentialScale: PotentialScale = PotentialScale.from(balance)

    /** 집중 관찰 슬롯 수. 투자 단계가 정한다. */
    fun focusSlots(department: ScoutingDepartment): Int = budget.focusSlots(department.level)

    /**
     * 정확도.
     *
     * 기본 등급(우리 팀 / 타 팀 1군 / 타 팀 2군 / 아마추어)에서 출발해, 집중 관찰 주차만큼 좁힌다.
     * 이름난 고교 유망주는 관찰 주차에 기본 주차가 더해진다 ([ScoutingBudget.headStartWeeks]).
     */
    fun precisionFor(
        department: ScoutingDepartment?,
        player: Player,
        viewerTeam: TeamId?,
        /** 소속은 아직 없지만 이미 우리 선수인가 (우리가 지명한 신인, docs/10 "지명 이후") */
        ownedByViewer: Boolean = false,
        /** 지금 관찰 주차에 더해 볼 주차. "더 봐도 달라지나"를 확인할 때만 쓴다 */
        extraWeeks: Int = 0,
        /**
         * 이 선수의 공개 기록 (2026-10-04, [publicRecordOf]). 유저 화면만 넘긴다 — null 이면 예전 그대로다.
         * AI 구단의 평가(MarketView)는 넘기지 않아 AI 판단·장기 밸런스가 바뀌지 않는다
         */
        record: PublicRecord? = null,
    ): ScoutingPrecision {
        val base = when {
            ownedByViewer -> ScoutingAccuracy.OWN_TEAM.precision
            player.teamId != null && player.teamId == viewerTeam -> ScoutingAccuracy.OWN_TEAM.precision
            record == PublicRecord.ESTABLISHED -> ScoutingAccuracy.ESTABLISHED.precision
            // FA 시장의 프로 선수는 소속이 없어도 아마추어가 아니다 — 2군 정도로 본다
            player.teamId == null && record == PublicRecord.PROFESSIONAL -> ScoutingAccuracy.LOW.precision
            player.teamId == null -> budget.amateurPrecision(department?.level ?: budget.defaultLevel)
            player.rosterLevel == RosterLevel.FIRST_TEAM -> ScoutingAccuracy.MEDIUM.precision
            else -> ScoutingAccuracy.LOW.precision
        }
        val weeks = (department?.weeksOn(player.id) ?: 0) + budget.headStartWeeks(player) + extraWeeks
        return budget.refine(base, weeks)
    }

    /**
     * 집중 관찰을 더 해도 정확도가 오르지 않는가 (범위 바닥·등급 확정·성장 타입 확정에 모두 닿았다).
     * 자동 집중 관찰이 이 선수를 슬롯에서 빼는 기준이다.
     */
    fun isFullyScouted(department: ScoutingDepartment?, player: Player, viewerTeam: TeamId?): Boolean =
        precisionFor(department, player, viewerTeam) == precisionFor(department, player, viewerTeam, extraWeeks = SATURATION_WEEKS)

    private val coreSection = balance.section("scouting").section("coreProspect")
    private val coreMaxAge = coreSection.int("maxAge")
    private val corePerTeam = coreSection.int("perTeam")
    private val coreMinGrade = PotentialGrade.fromName(coreSection.string("minGrade"))

    /**
     * 팀내 핵심 유망주 (유저 요청 2026-10-04). 한 구단 선수단에서 어린 선수를 잠재력 순으로 위에서 몇 명.
     *
     * 잠재력은 **보는 구단의 스카우트 시선**으로 판단한다 — 우리 팀은 정확히, 남의 팀은 흐린 범위의 가운데로.
     * 진짜 잠재력으로 고르면 남의 팀 표시를 보고 숨김 값을 역추적할 수 있다 (불변 원칙 4).
     *
     * @param squad 그 구단 선수단
     */
    fun coreProspects(
        department: ScoutingDepartment?,
        squad: List<Player>,
        viewerTeam: TeamId?,
        season: Int,
        record: ((Player) -> PublicRecord)? = null,
    ): Set<baseballgm.model.PlayerId> =
        squad.asSequence()
            .filter { it.ageIn(season) <= coreMaxAge && !it.isForeign }
            .map { player ->
                val precision = precisionFor(department, player, viewerTeam, record = record?.invoke(player))
                val potential = ScoutingView.potentialRange(player, precision).center
                Triple(player, potential, ScoutingView.of(player, precision, season, potentialScale).overall.center)
            }
            .filter { (_, potential, _) -> potentialScale.gradeOf(potential) >= coreMinGrade }
            .sortedWith(compareByDescending<Triple<Player, Double, Double>> { it.second }.thenByDescending { it.third })
            .take(corePerTeam)
            .map { it.first.id }
            .toSet()

    fun view(department: ScoutingDepartment?, player: Player, viewerTeam: TeamId?, season: Int, record: PublicRecord? = null): ScoutedPlayer =
        ScoutingView.of(player, precisionFor(department, player, viewerTeam, record = record), season, potentialScale)

    private val establishedMinPa = balance.int("scouting.established.minPa")
    private val establishedMinOuts = balance.int("scouting.established.minOuts")

    /**
     * 공개 기록 (2026-10-04 유저 요청 "준주전급 선수들까지는 능력치를 다 보여주자. FA 에서도 능력치가 안 보이니 불확실성이 너무 크다").
     *
     * **올 시즌 또는 지난 시즌 1군에서 준주전급으로 뛴 선수**(타자 `minPa` 타석 / 투수 `minOuts` 아웃 이상)는 ESTABLISHED —
     * 기록이 충분히 쌓여 리그 전체가 아는 선수라 현재 능력치를 정확히 보여 준다. 판정은 공개 기록(1군 출장)만 쓴다 —
     * 진짜 능력치로 고르면 "정확히 보이는 선수 = 잘하는 선수"가 되어 숨김 값이 샌다.
     * 그 밖에 1군 기록이나 프로 경력이 있으면 PROFESSIONAL (FA 시장에서 아마추어처럼 흐리게 보이지 않게).
     *
     * @param stats 올 시즌(스토브리그면 막 끝난 시즌) 기록
     * @param history [season] - 1 시즌 기록을 찾는 리그 역사
     */
    fun publicRecordOf(player: Player, stats: baseballgm.stats.SeasonStats?, history: baseballgm.league.LeagueHistory, season: Int): PublicRecord {
        val now = stats?.let {
            it.battingOf(player.id).total.plateAppearances >= establishedMinPa || it.pitchingOf(player.id).total.outs >= establishedMinOuts
        } == true
        val career = history.careerOf(player.id)
        val last = career.firstOrNull { it.season == season - 1 }?.let {
            (it.bat?.pa ?: 0) >= establishedMinPa || (it.pitch?.outs ?: 0) >= establishedMinOuts
        } == true
        return when {
            now || last -> PublicRecord.ESTABLISHED
            career.isNotEmpty() || player.contract.serviceSeasons > 0 || player.teamId != null -> PublicRecord.PROFESSIONAL
            else -> PublicRecord.NONE
        }
    }

    /**
     * 연도별 능력치 (선수 상세 그래프). 지난 시즌들은 리그 역사의 기록, 마지막 점은 지금 능력치다.
     *
     * 과거 값도 **지금의 정확도**로 흐려서 내보낸다. 오차는 선수마다 고정이라 범위가 시즌마다 같은 쪽으로
     * 치우친다 — 타 팀 선수라도 "올랐다/떨어졌다" 흐름은 읽히지만 진짜 값은 여전히 모른다 (불변 원칙 4).
     */
    fun ratingHistory(
        department: ScoutingDepartment?,
        player: Player,
        viewerTeam: TeamId?,
        history: baseballgm.league.LeagueHistory,
        season: Int,
        record: PublicRecord? = null,
    ): List<SeasonRatings> {
        val precision = precisionFor(department, player, viewerTeam, record = record)
        val past = history.ratings[player.id].orEmpty()
            .filter { it.season < season }
            .map { snapshot -> snapshot.season to player.withRatings(snapshot.toMap(player is baseballgm.model.Pitcher)) }
        return (past + (season to player)).map { (year, shown) ->
            val view = ScoutingView.of(shown, precision, year, potentialScale)
            SeasonRatings(year, view.age, view.overall, view.ratings, current = year == season)
        }
    }

    /** 리포트. 드래프트 풀 화면이 쓰는 형태다 (docs/10 리포트 구성). */
    fun report(
        department: ScoutingDepartment?,
        player: Player,
        viewerTeam: TeamId?,
        season: Int,
        school: String? = null,
        physique: Physique? = null,
        injuryHistory: List<String> = emptyList(),
        ownedByViewer: Boolean = false,
    ): ScoutReport = ScoutReportWriter.write(
        player = player,
        precision = precisionFor(department, player, viewerTeam, ownedByViewer),
        knownProspect = !ownedByViewer && budget.isKnownProspect(player),
        season = season,
        focusWeeks = department?.weeksOn(player.id) ?: 0,
        school = school,
        physique = physique,
        injuryHistory = injuryHistory,
        scale = potentialScale,
    )
}

/** 한 시즌의 능력치 (정확도만큼 흐린 값). [current] 면 지금 능력치 */
data class SeasonRatings(
    val season: Int,
    val age: Int,
    val overall: RatingRange,
    val ratings: Map<baseballgm.model.Attribute, RatingRange>,
    val current: Boolean,
)
