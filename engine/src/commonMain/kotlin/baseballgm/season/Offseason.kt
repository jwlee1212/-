package baseballgm.season

import baseballgm.development.AgingCurves
import baseballgm.development.AwakeningModel
import baseballgm.development.GrowthModel
import baseballgm.development.RetirementModel
import baseballgm.development.hasContract
import baseballgm.io.BalanceConfig
import baseballgm.league.League
import baseballgm.league.StrengthCalculator
import baseballgm.events.Enlistment
import baseballgm.events.EnlistmentWarning
import baseballgm.events.LeagueEnvironmentEvents
import baseballgm.events.MilitaryService
import baseballgm.league.Standings
import baseballgm.management.CapPenalty
import baseballgm.management.ClubSeasonReview
import baseballgm.management.SalaryCap
import baseballgm.management.SeasonReview
import baseballgm.management.StaffMarket
import baseballgm.management.StaffMarketState
import baseballgm.management.TeamSpend
import baseballgm.market.DraftPool
import baseballgm.market.DraftProspect
import baseballgm.market.DraftRights
import baseballgm.market.FaSigning
import baseballgm.market.ForeignMarket
import baseballgm.market.ForeignPool
import baseballgm.market.ForeignSupplier
import baseballgm.market.FreeAgencyMarket
import baseballgm.market.FreeAgencyState
import baseballgm.market.MarketView
import baseballgm.market.PositionNeed
import baseballgm.market.ProspectSupplier
import baseballgm.market.TeamModeResolver
import baseballgm.market.Valuation
import baseballgm.model.Batter
import baseballgm.model.Coach
import baseballgm.model.Condition
import baseballgm.model.Contract
import baseballgm.model.ContractType
import baseballgm.model.MilitaryStatus
import baseballgm.model.Origin
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.ServiceKind
import baseballgm.model.StaffId
import baseballgm.model.Team
import baseballgm.model.TeamId
import baseballgm.scouting.PotentialGrade
import baseballgm.scouting.ScoutingBudget
import baseballgm.stats.LeagueConstants
import baseballgm.stats.Sabermetrics
import baseballgm.stats.War
import baseballgm.util.chance
import baseballgm.util.nextInRange
import kotlin.math.max
import kotlin.random.Random

/**
 * 신인 공급기.
 *
 * 엔진은 선수를 만들 수 없다 — 이름 생성기와 아키타입이 도구(`tools`)에 있기 때문이다.
 * M6 부터 신인은 드래프트로 들어오고, 이 공급기는 **선수단이 최소 인원 아래로 떨어질 때만**
 * 쓰는 안전망이다 (드래프트 풀이 비어 있는 테스트 상황 포함).
 */
fun interface RookieSupplier {
    fun create(count: Int, season: Int, teamId: TeamId, random: Random): List<Player>
}

/** 스토브리그에서 일어난 일 (docs/09, 10). */
data class OffseasonReport(
    val season: Int,
    val retired: List<PlayerId>,
    val awakened: List<PlayerId>,
    val collapsed: List<PlayerId>,
    val rookies: List<PlayerId>,
    val newCoachCandidates: List<StaffId>,
    val enlisted: List<PlayerId>,
    val discharged: List<PlayerId>,
    /** 드래프트로 입단한 선수 */
    val drafted: List<PlayerId> = emptyList(),
    /** 미지명 선수 중 육성선수로 계약한 선수 (docs/10) */
    val developmentSignings: List<PlayerId> = emptyList(),
    /** 선수단 정리로 방출된 선수 */
    val released: List<PlayerId> = emptyList(),
    /** 다음 시즌 스카우트 투자 단계 (시즌 상태로 넘겨 준다) */
    val scoutingLevels: Map<TeamId, Int> = emptyMap(),
    /** FA 자격을 얻어 시장에 나온 선수 (docs/11) */
    val freeAgents: List<PlayerId> = emptyList(),
    /** 성사된 FA 계약. 자동 진행일 때만 채워진다 */
    val faSignings: List<FaSigning> = emptyList(),
    /** 소프트캡 제재 (docs/11) */
    val capPenalties: List<CapPenalty> = emptyList(),
    /** 외국인 계약 (docs/12) */
    val foreignSignings: List<ForeignSigning> = emptyList(),
    /** 외국인 이탈 (재계약 결렬·해외 유출) */
    val foreignDepartures: List<ForeignDeparture> = emptyList(),
    /** 이번 스토브리그 입대 (상무 합격 여부 포함) */
    val enlistments: List<Enlistment> = emptyList(),
    /** 입대 기한 임박 알림 */
    val enlistmentWarnings: List<EnlistmentWarning> = emptyList(),
    /** 다음 시즌 리그 환경 발표 (docs/04) */
    val environmentAnnouncement: String? = null,
    /** 경영 결산 — 재정·팬심·구단주·커리어 (docs/13) */
    val review: SeasonReview? = null,
    /** AI 구단이 데려간 스태프 (docs/13) */
    val staffHires: List<baseballgm.management.StaffHire> = emptyList(),
    /** 단장 스토브리그 계획대로 처리한 결과 (외국인 재계약·연봉·방출) — 비서가 읽어 준다 */
    val planNotes: List<String> = emptyList(),
) {
    fun summary(): String =
        "은퇴 ${retired.size} · 드래프트 ${drafted.size} · 육성 ${developmentSignings.size} · 방출 ${released.size} · " +
            "FA ${freeAgents.size}(계약 ${faSignings.size}) · 외국인 ${foreignSignings.size}(이탈 ${foreignDepartures.size}) · " +
            "각성 ${awakened.size} · 급노쇠 ${collapsed.size} · 입대 ${enlisted.size} · 제대 ${discharged.size}"
}

/**
 * 시즌 후 처리 (docs/09, 10).
 *
 * 순서가 중요하다.
 * ① 성장·노화 → ② 각성·급노쇠 → ③ 은퇴 → ④ 군 복무 갱신 → ⑤ 계약 갱신 →
 * ⑥ 드래프트 입단 → ⑦ 육성선수 계약 → ⑧ 선수단 정리(방출) → ⑨ 인원 보충 →
 * ⑩ 컨디션 초기화 → 다음 시즌 리그 완성(지명 순번·지명권·드래프트 풀)
 *
 * 성장을 먼저 하는 이유는, 그해 성적으로 얻은 성장까지 반영한 뒤에 은퇴를 판정해야
 * "마지막에 반등한 베테랑이 그대로 은퇴하는" 이상한 결과가 안 나오기 때문이다.
 *
 * **방출이 M6 에서 새로 생겼다.** 드래프트로 매년 11명씩 들어오는데 은퇴만으로는 그만큼 빠지지
 * 않아서, 목표 인원을 넘는 만큼 정리하지 않으면 선수단이 해마다 불어난다.
 */
class Offseason(
    private val balance: BalanceConfig,
    private val strength: StrengthCalculator,
) {
    private val curves = AgingCurves(balance)
    private val potentialScale = baseballgm.scouting.PotentialScale.from(balance)
    private val growthModel = GrowthModel(balance, curves)
    private val awakeningModel = AwakeningModel(balance, curves)
    private val retirementModel = RetirementModel(balance)
    private val scoutingBudget = ScoutingBudget(balance)

    private val contexts = GrowthContextResolver(balance)
    private val rosterLimits = balance.section("rosterLimits")
    private val targetRosterSize = rosterLimits.int("targetRosterSize")
    private val minRosterSize = rosterLimits.int("minRosterSize")
    private val protectAgeUnder = rosterLimits.int("protectAgeUnder")
    private val protectGrade = PotentialGrade.fromName(rosterLimits.string("protectPotentialGrade"))

    private val draftSection = balance.section("draft")
    private val draftRounds = draftSection.int("rounds")
    private val draftPoolSize = draftSection.int("poolSize")
    private val tradeablePickYears = draftSection.int("tradeablePickYears")
    private val developmentMaxPerTeam = draftSection.int("developmentPlayer.maxPerTeam")
    private val developmentSignChance = draftSection.double("developmentPlayer.signChance")

    private val offseasonWeeks = balance.int("season.offseasonWeeks")
    private val contractYears = balance.section("salary.contractYears")
    private val minimumSalary = balance.double("minimumSalary.value")
    private val rookieSeasons = balance.int("salary.rookieSeasons")
    private val reNegotiation = balance.section("salary.reNegotiation")
    private val releasedToMarketOverall = balance.double("rosterLimits.releasedToMarketOverall")
    private val baseAnnualIncome = balance.double("initialTeamState.baseAnnualIncome")

    private val sabermetrics = Sabermetrics(balance)
    private val war = War(balance, sabermetrics)
    private val optionRules = baseballgm.market.OptionRules(balance)
    private val positionNeed = PositionNeed(balance, strength)
    private val valuation = Valuation(balance, curves)
    private val marketView = MarketView(balance, strength)
    private val modeResolver = TeamModeResolver(balance, strength)
    private val salaryCap = SalaryCap(balance)
    private val militaryService = MilitaryService(balance)
    private val environmentEvents = LeagueEnvironmentEvents(balance)
    private val clubReview = ClubSeasonReview(balance, strength)
    private val staffMarket = StaffMarket(balance)
    private val foreignMarket = ForeignMarket(balance, valuation, marketView, positionNeed)
    private val foreignPoolSize = balance.int("foreignPlayers.poolSizePerSeason")
    private val decisions = balance.section("offseasonDecisions")
    private val lowballRatio = decisions.double("lowballRatio")
    private val lowballForm = decisions.int("lowballForm")
    private val acceptForm = decisions.int("acceptForm")
    private val formMin = balance.int("form.min")
    private val formMax = balance.int("form.max")

    /** 유저 구단이 스토브리그에 둘 수 있는 최대 인원 (목표 인원 + 여유) */
    val userRosterCap: Int = rosterLimits.int("targetRosterSize") + decisions.int("rosterSlack")

    /** FA 시장. 자동 진행이면 여기서 다 돌리고, 화면이 직접 몰 때는 밖에서 쓴다 (docs/11). */
    val freeAgency: FreeAgencyMarket =
        FreeAgencyMarket(balance, valuation, marketView, positionNeed, modeResolver)

    /**
     * @param autoFreeAgency true 면 FA 시장까지 자동으로 끝낸다. 화면이 유저 입찰을 받아야 할 때는
     *   false 로 두고 [openFreeAgency] → [FreeAgencyMarket.runRound] → [closeFreeAgency] 를 직접 부른다
     */
    fun run(
        state: SeasonState,
        random: Random,
        rookieSupplier: RookieSupplier,
        prospectSupplier: ProspectSupplier = ProspectSupplier { _, _, _ -> emptyList() },
        foreignSupplier: ForeignSupplier = ForeignSupplier { _, _, _ -> emptyList() },
        autoFreeAgency: Boolean = true,
        /** 유저 구단. [plan] 이 있으면 이 구단은 계획대로 처리한다 */
        userTeam: TeamId? = null,
        plan: OffseasonPlan? = null,
    ): Pair<League, OffseasonReport> {
        // 시즌 중 트레이드로 소속·지명권·자금이 바뀌었을 수 있다. 시즌 상태 쪽이 진짜다
        val league = state.currentLeague()
        val season = league.season
        val nextSeason = season + 1
        val constants = LeagueConstants.from(state.stats, balance)

        // 끝난 시즌을 역사에 적는다 — 성장·은퇴 전에 (그 시즌을 뛴 모습 그대로) (2026-10-01)
        val awards = SeasonAwards(balance).compute(state, league.history)
        val history = HistoryRecorder(balance).record(state, league.history, awards)

        val survivors = mutableListOf<Player>()
        val retired = mutableListOf<PlayerId>()
        val awakened = mutableListOf<PlayerId>()
        val collapsed = mutableListOf<PlayerId>()
        val enlisted = mutableListOf<PlayerId>()
        val discharged = mutableListOf<PlayerId>()
        val newCoaches = mutableListOf<Coach>()
        val faOrigins = mutableMapOf<PlayerId, TeamId>()
        val declaredFreeAgents = mutableListOf<PlayerId>()
        val foreignCarryOver = mutableListOf<Player>()
        val enlistments = mutableListOf<Enlistment>()
        val enlistmentWarnings = mutableListOf<EnlistmentWarning>()
        var sangmuTaken = 0
        var nextCoachNumber = league.coaches.size + 1
        // 계획을 따르는 구단 (계획이 없으면 null — 전부 자동)
        val planTeam = if (plan != null) userTeam else null
        val planNotes = mutableListOf<String>()
        val formDelta = mutableMapOf<PlayerId, Int>()

        state.allPlayers().forEach { player ->
            // ① 성장·노화
            var updated = growthModel.afterSeason(player, season, contexts.contextOf(state, player), random).player

            // ② 각성·급노쇠
            val awakening = awakeningModel.check(updated, season, random)
            updated = awakening.player
            if (awakening.awakened) awakened += updated.id
            if (awakening.collapsed) collapsed += updated.id

            // ③ 은퇴 (다음 시즌 나이 기준으로 판정한다)
            val overall = strength.overallOf(updated)
            if (retirementModel.retires(updated, nextSeason, overall, updated.hasContract(), random)) {
                retired += updated.id
                retirementModel.toCoach(updated, overall, StaffId("C" + (nextCoachNumber).toString().padStart(3, '0')), random)
                    ?.let {
                        newCoaches += it
                        nextCoachNumber++
                    }
                return@forEach
            }

            // 외국인은 1년 계약이라 국내 선수와 다른 길을 간다 (docs/12 외국인 시장)
            if (updated.isForeign) {
                foreignCarryOver += updated
                return@forEach
            }

            // ④ 군 복무 (상무 지원·합격 판정 포함, docs/12)
            val (military, enlistment) = updateMilitary(updated, nextSeason, overall, sangmuTaken, random)
            if (military != updated.military) {
                if (military is MilitaryStatus.Serving) enlisted += updated.id
                if (military is MilitaryStatus.Completed) discharged += updated.id
            }
            enlistment?.let {
                enlistments += it
                if (it.kind == ServiceKind.SANGMU) sangmuTaken++
            }
            militaryService.warningFor(updated, nextSeason)?.let { enlistmentWarnings += it }

            // ⑤ 계약: 자격을 채우고 계약이 끝났으면 FA 로 나간다
            updated = updated.withMilitary(military)
            var contract = updateContract(updated, state, constants, league, random)
            // 연봉 재계약 (단장 결정): 계약이 끝나 다시 맺은 비FA 선수만. 요구를 들어주면 기분 좋게, 깎으면 서운하게 시작한다
            val renewed = updated.contract.yearsRemaining - 1 <= 0 && contract.yearsRemaining > 0
            val decision = plan?.salaries?.get(updated.id)
            if (planTeam != null && updated.teamId == planTeam && renewed && decision != null) {
                // 미리보기에서 보여 준 금액 그대로 — 성장으로 능력치가 바뀌어도 합의한 금액은 바뀌지 않는다
                contract = contract.copy(salary = decision.salary)
                val lowball = decision.lowball
                formDelta[updated.id] = if (lowball) lowballForm else acceptForm
                planNotes += "${updated.registeredName} ${contract.salary}억 ${contract.yearsRemaining}년 재계약" +
                    if (lowball) " (깎아서 — 서운해해요)" else " (요구 수용)"
            }
            updated = updated.withContract(contract)
            if (becomesFreeAgent(updated, contract)) {
                faOrigins[updated.id] = updated.teamId ?: return@forEach
                declaredFreeAgents += updated.id
                updated = updated.withTeam(null, RosterLevel.FUTURES)
            }
            survivors += updated
        }

        // ⑥ 드래프트 입단 (docs/10: 다음 시즌 개막에 2군으로 입단)
        val bonusSpent = mutableMapOf<TeamId, Double>()
        val drafted = mutableListOf<Player>()
        state.draftResult?.selections?.forEach { selection ->
            val prospect = league.draftPool.byId(selection.playerId) ?: return@forEach
            drafted += signProspect(prospect, selection.teamId, nextSeason)
            bonusSpent[selection.teamId] = (bonusSpent[selection.teamId] ?: 0.0) + selection.signingBonus
        }

        // ⑦ 육성선수: 미지명 선수 중 일부를 싸게 데려간다
        val development = signDevelopmentPlayers(state, league, nextSeason, random)

        // ⑦-b 외국인 시장 (docs/12): 재계약 · 해외 유출 · 빈 자리 채우기
        val foreign = runForeignMarket(league, state, foreignCarryOver, constants, nextSeason, random, foreignSupplier, planTeam, plan, planNotes)

        // ⑧ 선수단 정리(방출). 올해 들어온 신인은 건드리지 않는다 — 한 시즌은 보고 판단한다
        val newcomers = (drafted + development + foreign.signed).map { it.id }.toSet()
        val intake = survivors + drafted + development + foreign.kept + foreign.signed
        // 단장이 고른 방출 명단을 먼저 내보내고, 남은 인원으로 정리한다 (유저 구단은 목표 + 여유까지 둘 수 있다)
        val (plannedOut, rest) = intake.partition {
            planTeam != null && it.teamId == planTeam && it.id in plan!!.releases && it.id !in newcomers
        }
        val (kept, autoReleased) = trimRosters(league, rest, nextSeason, newcomers, planTeam)
        val released = plannedOut + autoReleased
        if (planTeam != null) {
            if (plannedOut.isNotEmpty()) planNotes += "방출 ${plannedOut.size}명 (단장 명단)"
            val forced = autoReleased.filter { it.teamId == planTeam }
            if (forced.isNotEmpty()) {
                planNotes += "인원이 ${userRosterCap}명을 넘어 ${forced.joinToString { it.registeredName }} 추가 방출"
            }
        }

        // ⑨ 인원 보충 (안전망)
        // 드래프트가 열린 시즌에는 최소 인원까지만 채운다 — 신인 공급은 드래프트의 몫이다.
        // 드래프트가 없는 리그(테스트·구버전 데이터)에서는 예전처럼 목표 인원을 유지한다.
        val fillTo = if (state.draftResult != null) minRosterSize else targetRosterSize
        val rookies = mutableListOf<Player>()
        league.teams.forEach { team ->
            val current = kept.count { it.teamId == team.id }
            val need = fillTo - current
            if (need > 0) rookies += rookieSupplier.create(need, nextSeason, team.id, random)
        }

        // ⑩ 방출 선수 중 쓸 만한 선수는 시장에 올린다 (docs/11 "방출: 다른 팀이 영입 가능")
        val releasedIds = released.map { it.id }.toSet()
        val toMarket = released.filter { strength.overallOf(it) >= releasedToMarketOverall }
        toMarket.forEach { player -> player.teamId?.let { faOrigins[player.id] = it } }
        val marketReleased = toMarket.map { it.withTeam(null, RosterLevel.FUTURES) }

        // ⑪ 컨디션 초기화
        val nextPlayers = (kept + rookies + marketReleased).map { player ->
            val rested = restedCondition(player)
            val delta = formDelta[player.id] ?: 0
            player.withCondition(if (delta == 0) rested else rested.copy(form = (rested.form + delta).coerceIn(formMin, formMax)))
        }

        // ⑫ 소프트캡 제재 — **끝난 시즌의 연봉 총액**으로 판정한다 (docs/11)
        val (capPenalties, overruns) = salaryCap.evaluate(league, league.capOverruns)

        // ⑫-2 FA 옵션 지급 (2026-10-04 옵션 조항): 끝난 시즌 1군 기록이 기준을 넘은 조항만
        val optionsPaid = mutableMapOf<TeamId, Double>()
        league.teams.forEach { team ->
            val madePostseason = state.postseason?.reachedRound(team.id) != null
            state.playersOf(team.id).filter { it.contract.options.isNotEmpty() }.forEach { player ->
                val stats = baseballgm.market.OptionStats.of(state.stats.battingOf(player.id).total, state.stats.pitchingOf(player.id).total)
                val paid = player.contract.options.filter { optionRules.achieved(it.kind, stats, madePostseason) }.sumOf { it.amount }
                if (paid > 0.0) optionsPaid[team.id] = (optionsPaid[team.id] ?: 0.0) + paid
            }
        }

        // ⑬ 경영 결산: 재정 → 팬심 → 구단주 평가 → 커리어·업적 (docs/13)
        val scoutingLevels = league.teams.associate { it.id to state.scoutingOf(it.id).level }
        val spend = league.teams.associate { team ->
            team.id to TeamSpend(
                signingBonus = bonusSpent[team.id] ?: 0.0,
                scouting = scoutingBudget.annualCost(scoutingLevels[team.id] ?: 1),
                penalties = capPenalties.firstOrNull { it.teamId == team.id }?.fine ?: 0.0,
                options = optionsPaid[team.id] ?: 0.0,
            )
        }
        val review = clubReview.run(state, state.postseason, spend, random, state.difficulty)
        val nextTeams = nextTeams(league, state, review, capPenalties)
        val nextPool = DraftPool(
            season = nextSeason,
            prospects = prospectSupplier.create(nextSeason, draftPoolSize, random),
        )

        // ⑫-b 리그 환경 이벤트 (docs/04). 발표와 실제 효과에 작은 오차가 있다
        val environmentChange = environmentEvents.next(random)

        val nextLeague = league.copy(
            season = nextSeason,
            teams = nextTeams,
            players = nextPlayers,
            environment = environmentChange.environment,
            // 리그를 떠난 선수(은퇴·방출)는 통산 한 줄로 줄인다
            history = history.withDeparted(nextPlayers.map { it.id }.toSet()),
            foreignPool = foreign.pool,
            coaches = league.coaches + newCoaches,
            schedule = league.schedule.copy(season = nextSeason),
            draftPool = nextPool,
            management = review.management.copy(
                seasonGoals = clubReview.nextSeasonGoals(league),
                // 시즌 중에 쌓인 유저 결정을 커리어 기록으로 옮긴다
                decisions = state.userDecisions(league.management.userTeam),
                // 만족도는 이어진다. 이적 희망 표시·불만 주 수·못 지킨 약속은 시즌과 함께 끝난다 (약속은 마지막 주에 이미 판정됐다)
                morale = state.morale.mapValues { (_, morale) -> morale.copy(transferListed = false, lowWeeks = 0, promise = null) },
            ),
            // 연봉 보조는 한 시즌씩 줄고, 기간이 끝났거나 선수가 리그를 떠나면 사라진다 (2026-10-05)
            retainedSalaries = league.retainedSalaries
                .map { it.copy(years = it.years - 1) }
                .filter { retained -> retained.years > 0 && nextPlayers.any { it.id == retained.playerId } },
            draftRights = league.draftRights.rolledForward(
                finishedSeason = season,
                teams = league.teams.map { it.id },
                rounds = draftRounds,
                years = tradeablePickYears,
            ),
        )
        // ⑭ 스태프 시장 (docs/13): AI 구단이 먼저 데려간다. 남은 사람은 유저가 고를 수 있다
        val (staffedLeague, staffHires) = runStaffMarket(expireStaffContracts(nextLeague), random)

        // 방출된 선수는 다음 시즌 리그에 없으므로 보고서에서도 빼 준다 (시장에 오른 선수는 남는다)
        val goneIds = releasedIds - marketReleased.map { it.id }.toSet()
        val report = OffseasonReport(
            season = season,
            retired = retired,
            awakened = awakened - goneIds,
            collapsed = collapsed - goneIds,
            rookies = rookies.map { it.id },
            newCoachCandidates = newCoaches.map { it.id },
            enlisted = enlisted - goneIds,
            discharged = discharged - goneIds,
            drafted = drafted.map { it.id },
            developmentSignings = development.map { it.id },
            released = releasedIds.toList(),
            scoutingLevels = scoutingLevels,
            freeAgents = declaredFreeAgents + marketReleased.map { it.id },
            capPenalties = capPenalties,
            foreignSignings = foreign.signings,
            foreignDepartures = foreign.departures,
            enlistments = enlistments,
            enlistmentWarnings = enlistmentWarnings,
            environmentAnnouncement = environmentChange.announcement,
            review = review,
            staffHires = staffHires,
            planNotes = planNotes,
        )

        // ⑮ FA 시장 (docs/11). 화면이 유저 입찰을 받아야 할 때는 밖에서 라운드를 돌린다
        val withMarket = staffedLeague.copy(
            freeAgentPool = report.freeAgents,
            faOrigins = faOrigins,
            capOverruns = overruns,
        )
        if (!autoFreeAgency) return withMarket to report

        val marketState = freeAgency.open(withMarket, state.standings, random, marketReleased.map { it.id }.toSet())
        repeat(freeAgency.rounds) { freeAgency.runRound(marketState, withMarket, state.standings, random) }
        val settled = freeAgency.close(marketState, withMarket, random)
        val (finalLeague, extraReleased) = settleRosters(settled, newcomers + marketState.signings.map { it.playerId })
        return finalLeague to report
            .copy(faSignings = marketState.signings.toList(), released = report.released + extraReleased)
            .onlyPlayersIn(finalLeague)
    }

    /**
     * 리그에 남지 않은 선수를 보고서에서 걸러낸다.
     *
     * 미계약 방출 선수는 FA 시장이 닫힐 때 리그를 떠난다. 그 선수가 "입대"나 "각성" 목록에
     * 남아 있으면 화면이 없는 선수를 찾다가 터진다.
     */
    private fun OffseasonReport.onlyPlayersIn(league: League): OffseasonReport {
        val present = league.players.map { it.id }.toSet()
        return copy(
            awakened = awakened.filter { it in present },
            collapsed = collapsed.filter { it in present },
            enlisted = enlisted.filter { it in present },
            discharged = discharged.filter { it in present },
            drafted = drafted.filter { it in present },
            developmentSignings = developmentSignings.filter { it in present },
        )
    }

    /**
     * FA 시장이 닫힌 뒤 선수단 인원을 맞춘다.
     *
     * 시장 전에 한 번 정리했지만, **미계약 FA 가 원소속팀으로 돌아오고 영입한 FA 가 들어오면서**
     * 인원이 다시 넘친다. 현실에서도 영입한 뒤에 자리를 비우므로 한 번 더 정리한다.
     * 이번에 들어온 신인·FA 는 건드리지 않는다.
     */
    fun settleRosters(league: League, protectedIds: Set<PlayerId>, userTeam: TeamId? = null): Pair<League, List<PlayerId>> {
        val (kept, released) = trimRosters(league, league.players, league.season, protectedIds, userTeam)
        // FA 시장이 닫히며 떠난 선수(미계약 FA·추가 방출)도 역사에서 통산 한 줄로 줄인다
        return league.copy(players = kept, history = league.history.withDeparted(kept.map { it.id }.toSet())) to released.map { it.id }
    }

    // ---------- 드래프트·육성선수 ----------

    /** 지명된 선수를 2군 신인 계약으로 입단시킨다. 계약금은 지명 시점에 이미 정해져 있다. */
    private fun signProspect(prospect: DraftProspect, teamId: TeamId, season: Int): Player =
        prospect.player
            .withTeam(teamId, RosterLevel.FUTURES)
            .withContract(rookieContract(prospect.player.origin))
            .withDebut(season)

    /**
     * 육성선수 계약 (docs/10).
     *
     * 미지명 선수 중 **숨은 잠재력**이 있는 선수를 싸게 데려간다. AI 는 진짜 잠재력을 보고 고르지
     * 않는다 — 무작위로 집어서 "가끔 대박이 섞이는" 모양만 만든다.
     *
     * 목표 인원을 따지지 않고 데려온다. 어차피 바로 뒤의 선수단 정리에서 인원이 맞춰지므로,
     * **육성선수를 둘 데려오면 베테랑 둘이 더 방출되는** 형태가 된다.
     */
    private fun signDevelopmentPlayers(
        state: SeasonState,
        league: League,
        nextSeason: Int,
        random: Random,
    ): List<Player> {
        val undrafted = state.draftResult?.undrafted.orEmpty()
            .mapNotNull { league.draftPool.byId(it) }
            .toMutableList()
        if (undrafted.isEmpty()) return emptyList()

        val signed = mutableListOf<Player>()
        league.teams.forEach { team ->
            var quota = developmentMaxPerTeam
            while (quota > 0 && undrafted.isNotEmpty()) {
                if (!random.chance(developmentSignChance)) break
                val index = random.nextInt(undrafted.size)
                val prospect = undrafted.removeAt(index)
                signed += signProspect(prospect, team.id, nextSeason)
                quota--
            }
        }
        return signed
    }

    private fun rookieContract(origin: Origin): Contract = Contract(
        salary = minimumSalary,
        yearsRemaining = rookieSeasons,
        signingBonusRemaining = 0.0,
        seasonsToFreeAgency = faQualifyingSeasons(origin),
        serviceSeasons = 0,
        type = ContractType.ROOKIE,
    )

    private fun faQualifyingSeasons(origin: Origin): Int = when (origin) {
        Origin.COLLEGE -> balance.int("freeAgency.qualifyingSeasons.college")
        else -> balance.int("freeAgency.qualifyingSeasons.highSchool")
    }

    // ---------- 외국인 시장 (docs/12) ----------

    private class ForeignOutcome(
        val kept: List<Player>,
        val signed: List<Player>,
        val signings: List<ForeignSigning>,
        val departures: List<ForeignDeparture>,
        val pool: ForeignPool,
    )

    /**
     * 외국인 시장 (docs/12).
     *
     * 외국인은 **1년 계약**이라 스토브리그마다 세 갈래로 갈린다.
     *
     * 1. **재계약** — 구단이 값을 치를 만하다고 보면 인상해서 잡는다. 재계약에는 연봉 상한이 없다
     * 2. **해외 유출** — 크게 성공한 선수는 일본 구단 제안을 받는다. 그 금액을 맞추지 못하면 떠난다
     * 3. **결렬** — 기대에 못 미친 선수는 그대로 리그를 떠난다
     *
     * 그리고 빈 자리를 새 풀에서 채운다. **한쪽으로 3명은 안 되므로**(docs/12) 마지막 한 자리는
     * 자동으로 부족한 쪽이 된다.
     */
    private fun runForeignMarket(
        league: League,
        state: SeasonState,
        carryOver: List<Player>,
        constants: LeagueConstants,
        nextSeason: Int,
        random: Random,
        supplier: ForeignSupplier,
        planTeam: TeamId? = null,
        plan: OffseasonPlan? = null,
        planNotes: MutableList<String> = mutableListOf(),
    ): ForeignOutcome {
        val pool = ForeignPool(
            season = nextSeason,
            candidates = supplier.create(nextSeason, foreignPoolSize, random),
        ).candidates.toMutableList()

        val kept = mutableListOf<Player>()
        val signed = mutableListOf<Player>()
        val signings = mutableListOf<ForeignSigning>()
        val departures = mutableListOf<ForeignDeparture>()

        // ① 재계약 · 해외 유출
        carryOver.forEach { player ->
            val teamId = player.teamId ?: return@forEach
            // 단장이 정한 선수는 난수 없이 그대로 (미리보기 때 보여 준 요구액으로 재계약)
            if (teamId == planTeam && plan != null && player.id in plan.foreign) {
                val salary = plan.foreign[player.id]
                if (salary != null) {
                    kept += player.withContract(player.contract.copy(salary = salary, yearsRemaining = 1, signingBonusRemaining = 0.0))
                    signings += ForeignSigning(player.id, teamId, salary, "재계약 (단장 결정)", reSigned = true)
                    planNotes += "${player.registeredName} ${salary}억 재계약"
                } else {
                    departures += ForeignDeparture(player.id, teamId, "단장 결정 — 재계약 안 함")
                    planNotes += "${player.registeredName} 결별"
                }
                return@forEach
            }
            val mode = modeResolver.modeOf(league, state.standings, teamId)
            val park = league.team(teamId).parkFactor
            val produced = war.of(player, state.stats, constants, park).war

            val outflowOffer = produced >= foreignMarket.outflowWarThreshold &&
                random.chance(foreignMarket.outflowChance)
            val asking = if (outflowOffer) {
                foreignMarket.outflowOffer(player, random)
            } else {
                foreignMarket.reSignSalary(player, random)
            }
            val maximum = foreignMarket.maximumFor(player, teamId, league, mode, capped = false)

            if (maximum >= asking) {
                kept += player.withContract(
                    player.contract.copy(salary = asking, yearsRemaining = 1, signingBonusRemaining = 0.0),
                )
                signings += ForeignSigning(player.id, teamId, asking, "재계약", reSigned = true)
            } else {
                departures += ForeignDeparture(
                    playerId = player.id,
                    teamId = teamId,
                    reason = if (outflowOffer) "일본 구단 이적 (${asking}억 제안)" else "재계약 결렬",
                )
            }
        }

        // ② 빈 자리 채우기
        league.teams.forEach { team ->
            val mode = modeResolver.modeOf(league, state.standings, team.id)
            var funds = state.funds[team.id] ?: team.operatingFunds
            while (true) {
                val current = (kept + signed).filter { it.teamId == team.id }
                if (current.size >= foreignMarket.rules.maxPerTeam) break
                if (!random.chance(foreignMarket.signChance)) break

                val candidate = pool
                    .filter { foreignMarket.rules.problemsForSigning(current, it.player, it.askingSalary, true).isEmpty() }
                    .filter { it.askingSalary <= funds }
                    .maxByOrNull { foreignMarket.maximumFor(it.player, team.id, league, mode, capped = true) - it.askingSalary }
                    ?: break
                val maximum = foreignMarket.maximumFor(candidate.player, team.id, league, mode, capped = true)
                if (maximum < candidate.askingSalary) break

                pool.remove(candidate)
                funds -= candidate.askingSalary
                signed += candidate.player
                    .withTeam(team.id, RosterLevel.FUTURES)
                    .withContract(foreignContract(candidate.askingSalary))
                    .withDebut(nextSeason)
                signings += ForeignSigning(
                    playerId = candidate.id,
                    teamId = team.id,
                    salary = candidate.askingSalary,
                    originLabel = candidate.originLabel,
                    reSigned = false,
                )
            }
        }

        return ForeignOutcome(kept, signed, signings, departures, ForeignPool(nextSeason, pool.toList()))
    }

    private fun foreignContract(salary: Double): Contract = Contract(
        salary = salary,
        yearsRemaining = 1,
        signingBonusRemaining = 0.0,
        seasonsToFreeAgency = 0,
        serviceSeasons = 0,
        type = ContractType.FOREIGN,
    )

    // ---------- 선수단 정리 ----------

    /**
     * 목표 인원을 넘는 만큼 방출한다.
     *
     * 기준은 지금 실력만이 아니라 **남은 가능성**이다. 어린 선수는 잠재력을 크게 쳐 주고,
     * 어리고 잠재력 등급이 높은 선수와 복무 중인 선수는 보호해서 마지막에만 건드린다.
     * (방출된 선수는 지금은 리그에서 사라진다. FA·웨이버 시장은 M7 이다)
     */
    private fun trimRosters(
        league: League,
        players: List<Player>,
        nextSeason: Int,
        newcomers: Set<PlayerId>,
        /** 이 구단은 목표 인원 대신 [userRosterCap] 까지 둔다 (단장이 방출 명단을 직접 정한 구단) */
        userTeam: TeamId? = null,
    ): Pair<List<Player>, List<Player>> {
        val kept = mutableListOf<Player>()
        val released = mutableListOf<Player>()
        val byTeam = players.groupBy { it.teamId }

        byTeam.forEach { (teamId, roster) ->
            val limit = if (teamId != null && teamId == userTeam) userRosterCap else targetRosterSize
            if (teamId == null || roster.size <= limit) {
                kept += roster
                return@forEach
            }
            val surplus = roster.size - limit
            val order = roster.sortedWith(
                compareBy<Player> { if (it.id in newcomers || isProtected(it, nextSeason)) 1 else 0 }
                    .thenBy { keepValue(it, nextSeason) },
            )
            released += order.take(surplus)
            kept += order.drop(surplus)
        }
        return kept to released
    }

    /** 지금 실력 + 남은 가능성. 어릴수록 잠재력을 크게 친다. */
    private fun keepValue(player: Player, season: Int): Double {
        val overall = strength.overallOf(player)
        val potential = player.hidden.potential.values.average()
        val age = player.ageIn(season)
        val futureWeight = when {
            age <= YOUNG_AGE -> 1.0
            age <= PRIME_AGE -> 0.6
            age <= VETERAN_AGE -> 0.25
            else -> 0.0
        }
        return overall + max(0.0, potential - overall) * futureWeight
    }

    private fun isProtected(player: Player, season: Int): Boolean {
        if (!player.military.isAvailable) return true
        val grade = potentialScale.gradeOf(player.hidden.potential.values.average())
        return player.ageIn(season) < protectAgeUnder && grade.ordinal >= protectGrade.ordinal
    }

    // ---------- 구단 ----------

    /**
     * 다음 시즌 구단 상태.
     *
     * 지명 순번은 **그해 최종 순위의 역순**이다 (꼴찌가 1순위). 운용 자금에서는 계약금과
     * 스카우트 비용이 나가고 모기업 지원금이 들어온다 (제대로 된 재정은 M9).
     */
    /**
     * 다음 시즌 구단 상태 (docs/11, 13).
     *
     * 지명 순번은 **그해 최종 순위의 역순**이고(꼴찌가 1순위), 소프트캡 2년 연속 초과 팀은 뒤로 밀린다.
     * 운용 자금·팬심·구단주 신뢰도·모기업 상태는 경영 결산([ClubSeasonReview])이 계산한 값을 그대로 쓴다.
     */
    private fun nextTeams(
        league: League,
        state: SeasonState,
        review: SeasonReview,
        capPenalties: List<CapPenalty>,
    ): List<Team> {
        val order = state.standings.ranked().reversed().map { it.teamId }.toMutableList()
        capPenalties.filter { it.draftPickDrop }.forEach { penalty ->
            val index = order.indexOf(penalty.teamId)
            if (index >= 0) {
                order.removeAt(index)
                order.add(minOf(order.size, index + salaryCap.draftPickDropPlaces()), penalty.teamId)
            }
        }
        val pickByTeam = order.withIndex().associate { (index, teamId) -> teamId to index + 1 }

        return league.teams.map { team ->
            val teamReview = review.of(team.id)
            team.copy(
                draftPick = pickByTeam[team.id] ?: team.draftPick,
                operatingFunds = teamReview?.finance?.fundsAfter ?: team.operatingFunds,
                fanSupport = teamReview?.fanAfter ?: team.fanSupport,
                ownerTrust = teamReview?.nextSeasonTrust ?: team.ownerTrust,
                parentCompany = teamReview?.parent?.company ?: team.parentCompany,
            )
        }
    }

    // ---------- 스태프 시장 (docs/13) ----------

    /**
     * 스태프 계약을 한 해 넘긴다 (docs/13).
     *
     * 계약이 끝난 스태프는 **무직이 되어 시장에 나온다.** 스태프가 늙지도 은퇴하지도 않으면
     * 시장이 영원히 비어 있게 되므로, 계약 만료가 시장을 돌리는 엔진이다. 원 소속팀도 다시
     * 데려올 수 있지만 AI 와 경쟁해야 한다.
     */
    private fun expireStaffContracts(league: League): League {
        fun next(contract: baseballgm.model.StaffContract) = contract.copy(yearsRemaining = contract.yearsRemaining - 1)
        return league.copy(
            coaches = league.coaches.map { coach ->
                val contract = next(coach.contract)
                if (contract.yearsRemaining <= 0) coach.copy(teamId = null, contract = contract) else coach.copy(contract = contract)
            },
            medicalStaff = league.medicalStaff.map { staff ->
                val contract = next(staff.contract)
                if (contract.yearsRemaining <= 0) staff.copy(teamId = null, contract = contract) else staff.copy(contract = contract)
            },
            managers = league.managers.map { manager ->
                val contract = next(manager.contract)
                if (contract.yearsRemaining <= 0) manager.copy(teamId = null, contract = contract) else manager.copy(contract = contract)
            },
        )
    }

    /**
     * AI 구단의 스태프 고용.
     *
     * 은퇴 선수가 코치로 전향해 쌓인 무직 스태프(docs/09)가 여기서 팀을 찾는다. **좋은 스태프는
     * AI 가 먼저 데려간다** — 유저가 고민하는 동안 3등급 타격코치가 사라지는 경험을 만드는 부분이다.
     * 유저의 고용은 화면에서 남은 시장을 보고 따로 한다.
     */
    private fun runStaffMarket(league: League, random: Random): Pair<League, List<baseballgm.management.StaffHire>> {
        val market = staffMarket.open(league, random)
        val funds = league.teams.associate { it.id to it.operatingFunds }
        val hires = staffMarket.runAiHiring(market, league, funds, league.management.userTeam, random)
        if (hires.isEmpty()) return league to hires

        // 스태프 연봉은 **매년 재정 결산의 지출 항목**으로 나간다 (docs/13). 계약할 때 한 번에
        // 빼면 같은 돈을 두 번 세는 셈이라, 여기서는 자금을 건드리지 않는다
        val byStaff = hires.associateBy { it.staffId }
        return league.copy(
            coaches = league.coaches.map { coach ->
                byStaff[coach.id]?.let { hire ->
                    coach.copy(teamId = hire.teamId, contract = staffMarket.contractFor(hire.salary, random))
                } ?: coach
            },
            medicalStaff = league.medicalStaff.map { staff ->
                byStaff[staff.id]?.let { hire ->
                    staff.copy(teamId = hire.teamId, contract = staffMarket.contractFor(hire.salary, random))
                } ?: staff
            },
            managers = league.managers.map { manager ->
                byStaff[manager.id]?.let { hire ->
                    manager.copy(teamId = hire.teamId, contract = staffMarket.contractFor(hire.salary, random))
                } ?: manager
            },
        ) to hires
    }

    // ---------- 군 복무 (docs/12) ----------

    /**
     * 입대·제대 판정.
     *
     * 복무 중인 선수의 제대는 주간 루프가 처리하므로(시즌 중 제대) 여기서는 시즌을 넘기며
     * 이미 기간이 끝난 경우만 정리한다. 입대는 [MilitaryService] 가 상무 지원·합격까지 판단한다.
     *
     * @param sangmuTaken 올 스토브리그에 이미 상무에 합격한 인원 (정원 관리)
     */
    private fun updateMilitary(
        player: Player,
        nextSeason: Int,
        overall: Double,
        sangmuTaken: Int,
        random: Random,
    ): Pair<MilitaryStatus, Enlistment?> {
        return when (val military = player.military) {
            is MilitaryStatus.Serving ->
                if (military.returnSeason < nextSeason) MilitaryStatus.Completed to null else military to null

            is MilitaryStatus.Unfulfilled ->
                if (militaryService.shouldEnlist(player, nextSeason, overall, random)) {
                    val enlistment = militaryService.enlist(player, nextSeason, overall, sangmuTaken, random)
                    enlistment.status to enlistment
                } else {
                    military to null
                }

            else -> military to null
        }
    }

    // ---------- 계약 ----------

    /**
     * 계약을 한 해 넘긴다.
     *
     * 계약이 끝난 선수는 두 갈래다.
     * - **FA 자격을 채웠으면** 시장으로 나간다 ([becomesFreeAgent])
     * - 아직 못 채웠으면 **연봉 재계약**을 한다. 금액은 전 시즌 WAR 의 시장가로 천천히 끌어간다
     *   (docs/11 "비FA 연봉 재계약: 전 시즌 성적(WAR 중심)으로 자동 산정")
     */
    private fun updateContract(
        player: Player,
        state: SeasonState,
        constants: LeagueConstants,
        league: League,
        random: Random,
    ): Contract {
        val playedInFirstTeam = state.stats.battingOf(player.id).total.plateAppearances > 0 ||
            state.stats.pitchingOf(player.id).total.outs > 0
        val contract = player.contract
        val years = contract.yearsRemaining - 1
        val serviceSeasons = contract.serviceSeasons +
            if (playedInFirstTeam && player.military.isAvailable) 1 else 0
        val seasonsToFa = max(0, contract.seasonsToFreeAgency - if (playedInFirstTeam) 1 else 0)

        if (years > 0) {
            return contract.copy(
                // 시즌 중 맺은 다년계약은 다음 시즌부터 새 연봉 (옵션 없는 새 계약)
                salary = contract.nextSalary ?: contract.salary,
                options = contract.nextOptions ?: if (contract.nextSalary != null) emptyList() else contract.options,
                nextOptions = null,
                nextSalary = null,
                yearsRemaining = years,
                signingBonusRemaining = max(0.0, contract.signingBonusRemaining * BONUS_PAYOUT),
                serviceSeasons = serviceSeasons,
                seasonsToFreeAgency = seasonsToFa,
            )
        }

        // 계약 만료: FA 는 시장에서 조건이 정해지므로 연봉을 건드리지 않는다
        val renewed = if (qualifiesForFreeAgency(player, serviceSeasons, seasonsToFa)) {
            contract.copy(yearsRemaining = 0, signingBonusRemaining = 0.0, nextSalary = null, options = emptyList(), nextOptions = null)
        } else {
            val park = player.teamId?.let { league.team(it).parkFactor } ?: 1.0
            contract.copy(
                salary = reNegotiatedSalary(player, state, constants, park),
                yearsRemaining = random.nextInRange(contractYears.intRange("veteran")),
                signingBonusRemaining = 0.0,
                type = if (contract.type == ContractType.ROOKIE || contract.type == ContractType.MULTI_YEAR) ContractType.STANDARD else contract.type,
                nextSalary = null,
                options = emptyList(),
                nextOptions = null,
            )
        }
        return renewed.copy(serviceSeasons = serviceSeasons, seasonsToFreeAgency = seasonsToFa)
    }

    /** 전 시즌 WAR 의 시장가로 연봉을 다시 정한다. 한 번에 가지 않고 절반만 끌어간다. */
    private fun reNegotiatedSalary(
        player: Player,
        state: SeasonState,
        constants: LeagueConstants,
        parkFactor: Double,
    ): Double {
        val produced = war.of(player, state.stats, constants, parkFactor).war
        val market = max(minimumSalary, produced * valuation.salaryPerWar)
        val smoothing = reNegotiation.double("smoothing")
        val blended = player.contract.salary * (1 - smoothing) + market * smoothing
        return blended
            .coerceIn(
                player.contract.salary * reNegotiation.double("maxCut"),
                player.contract.salary * reNegotiation.double("maxRaise"),
            )
            .coerceAtLeast(minimumSalary)
            .let { (it * 100).toInt() / 100.0 }
    }

    private fun qualifiesForFreeAgency(player: Player, serviceSeasons: Int, seasonsToFa: Int): Boolean =
        player.origin != Origin.FOREIGN && seasonsToFa <= 0 && serviceSeasons >= faQualifyingSeasons(player.origin)

    private fun becomesFreeAgent(player: Player, contract: Contract): Boolean =
        contract.yearsRemaining <= 0 && player.teamId != null

    /** 비시즌 동안 피로가 풀리고 폼이 평균으로 돌아온다. 긴 부상은 다음 시즌으로 이어진다. */
    private fun restedCondition(player: Player): Condition {
        val injury = player.condition.injury
        val remaining = (injury?.weeksRemaining ?: 0) - offseasonWeeks
        return Condition(
            fatigue = 0,
            form = NEUTRAL_FORM,
            injury = if (injury != null && remaining > 0) injury.copy(weeksRemaining = remaining) else null,
            relapseRiskWeeks = 0,
        )
    }

    // ---------- 스토브리그 준비: 비서 브리핑 (2026-10-01) ----------

    /** 깎아서 제시하는 연봉. 최저 연봉 아래로는 못 내려간다 */
    fun lowballSalary(demand: Double): Double =
        (demand * lowballRatio).coerceAtLeast(minimumSalary).let { (it * 100).toInt() / 100.0 }

    /**
     * 유저 구단의 겨울 결정거리를 미리 계산한다 (시즌·포스트시즌이 끝난 뒤, 스토브리그 전).
     *
     * @param randomFor 외국인 선수마다 고정된 난수 — 요구액·해외 유출을 미리 정해 두려고 쓴다.
     *   같은 선수면 몇 번을 열어 봐도 같은 숫자고, 재계약하면 이 액수 그대로 계약한다.
     *
     * 방출 추천은 **보이는 정보만** 쓴다 — 지금 능력(우리 선수라 정확) + 잠재력 등급(범위) + 나이 (불변 원칙 4).
     */
    fun preview(state: SeasonState, userTeam: TeamId, randomFor: (PlayerId) -> Random): OffseasonPreview {
        val league = state.currentLeague()
        val constants = LeagueConstants.from(state.stats, balance)
        val park = league.team(userTeam).parkFactor
        val mode = modeResolver.modeOf(league, state.standings, userTeam)
        val roster = state.playersOf(userTeam)

        val foreign = roster.filter { it.isForeign }.map { player ->
            val random = randomFor(player.id)
            val produced = war.of(player, state.stats, constants, park).war
            val outflow = produced >= foreignMarket.outflowWarThreshold && random.chance(foreignMarket.outflowChance)
            val asking = if (outflow) foreignMarket.outflowOffer(player, random) else foreignMarket.reSignSalary(player, random)
            val maximum = foreignMarket.maximumFor(player, userTeam, league, mode, capped = false)
            ForeignRenewal(player.id, produced, asking, player.contract.salary, outflow, recommendResign = maximum >= asking)
        }

        val domestic = roster.filter { !it.isForeign }
        val expiring = domestic.filter { it.contract.yearsRemaining - 1 <= 0 }
        val faLeaving = expiring.filter { player ->
            val played = state.stats.battingOf(player.id).total.plateAppearances > 0 || state.stats.pitchingOf(player.id).total.outs > 0
            val service = player.contract.serviceSeasons + if (played && player.military.isAvailable) 1 else 0
            val toFa = max(0, player.contract.seasonsToFreeAgency - if (played) 1 else 0)
            qualifiesForFreeAgency(player, service, toFa)
        }.map { it.id }.toSet()
        val salaries = expiring.filter { it.id !in faLeaving }.map { player ->
            val demand = reNegotiatedSalary(player, state, constants, park)
            SalaryCase(player.id, war.of(player, state.stats, constants, park).war, player.contract.salary, demand, lowballSalary(demand))
        }

        val drafted = state.draftResult?.selections.orEmpty().count { it.teamId == userTeam }
        val foreignLeaving = foreign.count { !it.recommendResign }
        val projected = roster.size - faLeaving.size - foreignLeaving + drafted
        val surplus = (projected - targetRosterSize).coerceAtLeast(0)
        val candidates = domestic.filter { it.id !in faLeaving && it.military.isAvailable }
            .map { player -> player to visibleKeepScore(player, state.season + 1, userTeam, state) }
            .sortedBy { it.second }
        val recommended = candidates.take(surplus).map { it.first.id }.toSet()

        return OffseasonPreview(
            foreign = foreign,
            salaries = salaries,
            freeAgents = faLeaving.toList(),
            releaseCandidates = candidates.map { (player, score) -> ReleaseCandidate(player.id, score, player.id in recommended) },
            projectedRoster = projected,
            targetRoster = targetRosterSize,
            maxRoster = userRosterCap,
        )
    }

    /** 비서 추천대로 채운 계획. 아무것도 안 건드리고 시작해도 말이 되는 겨울이 된다 */
    fun defaultPlan(preview: OffseasonPreview): OffseasonPlan = OffseasonPlan(
        foreign = preview.foreign.associate { it.playerId to (if (it.recommendResign) it.asking else null) },
        salaries = preview.salaries.associate { it.playerId to SalaryDecision(it.demand, lowball = false) },
        releases = preview.releaseCandidates.filter { it.recommended }.map { it.playerId }.toSet(),
    )

    /** 지금 능력 + (잠재력 등급 가운데 − 지금)× 나이 가중치. 숨김 수치 대신 단장이 보는 등급을 쓴다 */
    private fun visibleKeepScore(player: Player, season: Int, userTeam: TeamId, state: SeasonState): Double {
        val overall = strength.overallOf(player)
        val scouted = baseballgm.scouting.ScoutingView.of(player, baseballgm.scouting.ScoutingAccuracy.OWN_TEAM, state.season, potentialScale)
        val potential = (potentialScale.midpoint(scouted.potentialLow) + potentialScale.midpoint(scouted.potentialHigh)) / 2.0
        val age = player.ageIn(season)
        val futureWeight = when {
            age <= YOUNG_AGE -> 1.0
            age <= PRIME_AGE -> 0.6
            age <= VETERAN_AGE -> 0.25
            else -> 0.0
        }
        return overall + max(0.0, potential - overall) * futureWeight
    }

    private companion object {
        /** 등급 하한에서 가운데까지 (등급 폭 10의 절반) */
        const val NEUTRAL_FORM = 50
        const val BONUS_PAYOUT = 0.0
        const val YOUNG_AGE = 22
        const val PRIME_AGE = 25
        const val VETERAN_AGE = 29
    }
}

/** 군 복무 상태만 바꾼 사본. */
fun Player.withMilitary(military: MilitaryStatus): Player = when (this) {
    is Batter -> copy(military = military)
    is Pitcher -> copy(military = military)
}

/** 계약만 바꾼 사본. */
fun Player.withContract(contract: Contract): Player = when (this) {
    is Batter -> copy(contract = contract)
    is Pitcher -> copy(contract = contract)
}

/** 소속과 1·2군을 바꾼 사본. 드래프트 입단·트레이드가 쓴다. */
fun Player.withTeam(teamId: TeamId?, rosterLevel: RosterLevel): Player = when (this) {
    is Batter -> copy(teamId = teamId, rosterLevel = rosterLevel)
    is Pitcher -> copy(teamId = teamId, rosterLevel = rosterLevel)
}

/** 데뷔 시즌만 바꾼 사본. 드래프트 지명 선수는 입단하는 해가 데뷔 시즌이다. */
fun Player.withDebut(season: Int): Player = when (this) {
    is Batter -> copy(debutSeason = season)
    is Pitcher -> copy(debutSeason = season)
}

/** 복무 중이거나 부상 중이면 1군에 둘 수 없다. 새 시즌을 시작할 때 정리한다. */
fun Player.startingRosterLevel(): RosterLevel =
    if (!military.isAvailable || condition.isInjured) RosterLevel.FUTURES else rosterLevel

/** 외국인 계약 한 건 (docs/12). 화면과 보고서가 함께 쓴다. */
data class ForeignSigning(
    val playerId: baseballgm.model.PlayerId,
    val teamId: TeamId,
    val salary: Double,
    val originLabel: String,
    /** 재계약인가 (신규 계약은 연봉 상한이 걸린다) */
    val reSigned: Boolean,
)

/** 외국인 이탈 한 건. */
data class ForeignDeparture(
    val playerId: baseballgm.model.PlayerId,
    val teamId: TeamId,
    val reason: String,
)
