package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.league.StrengthCalculator
import baseballgm.market.Draft
import baseballgm.market.DraftAI
import baseballgm.market.DraftAdvisor
import baseballgm.market.DraftPolicy
import baseballgm.market.RoundAdvice
import baseballgm.market.DraftProspect
import baseballgm.market.DraftSelection
import baseballgm.market.DraftState
import baseballgm.market.PositionNeed
import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.scouting.ScoutReport
import baseballgm.scouting.ScoutingService
import kotlin.random.Random

/**
 * 드래프트 주차 진행 (docs/07, 10).
 *
 * 유저 차례에서 멈출 수 있어야 하므로 "끝까지 돌리기"가 아니라 **"다음 유저 차례까지 돌리기"** 가
 * 기본 동작이다. 콘솔·대량 시뮬레이션은 유저를 지정하지 않고 통째로 돌린다.
 */
class DraftRunner(
    balance: BalanceConfig,
    strength: StrengthCalculator,
) {
    private val draft = Draft(balance)
    val scouting: ScoutingService = ScoutingService(balance)
    private val positionNeed = PositionNeed(balance, strength)
    private val ai = DraftAI(balance, positionNeed, scouting.budget)
    val advisor: DraftAdvisor = DraftAdvisor(balance, positionNeed, scouting.budget)

    val rounds: Int get() = draft.rounds

    /** 아직 시작하지 않았으면 순번표를 만든다. */
    fun begin(state: SeasonState): DraftState {
        state.draft?.let { return it }
        val league = state.league
        val prepared = draft.prepare(
            season = league.season,
            pickOrder = league.draftOrder(),
            // 시즌 중 트레이드로 옮겨 간 지명권까지 반영된 쪽 (state.league 는 개막 때 그대로다)
            rights = state.draftRights,
            pool = league.draftPool,
        )
        state.draft = prepared
        return prepared
    }

    fun isTurnOf(state: SeasonState, teamId: TeamId): Boolean =
        state.draft?.current()?.ownerTeam == teamId

    /**
     * [stopForTeam] 차례가 오거나 드래프트가 끝날 때까지 AI 가 지명한다.
     *
     * @return 드래프트가 끝났으면 true
     */
    fun advance(state: SeasonState, stopForTeam: TeamId?, random: Random): Boolean {
        while (step(state, stopForTeam, random) != null) Unit
        return state.draftResult != null
    }

    /**
     * AI 지명 **한 건**만 한다. 화면이 다른 구단의 지명을 한 장씩 보여줄 때 쓴다 (드래프트 생중계).
     *
     * [advance] 도 이 함수를 되풀이할 뿐이라, 한 건씩 넘기든 한 번에 넘기든 랜덤을 쓰는 순서가 같다 —
     * 같은 시드면 결과도 같다 (불변 원칙 2).
     *
     * @return 이번에 나온 지명. [stopForTeam] 차례이거나 드래프트가 끝났으면 null
     */
    fun step(state: SeasonState, stopForTeam: TeamId?, random: Random): DraftSelection? {
        val current = begin(state)
        val slot = current.current()
        if (current.isComplete || slot == null) {
            finish(state)
            return null
        }
        if (slot.ownerTeam == stopForTeam) return null
        val available = current.availableProspects()
        val chosen = ai.choose(
            prospects = available,
            teamId = slot.ownerTeam,
            roster = state.playersOf(slot.ownerTeam),
            season = state.season,
            random = random,
            pending = draftedSoFar(state, slot.ownerTeam),
            precisionOf = { precisionOf(state, slot.ownerTeam, it) },
        )
        val selection = draft.select(current, chosen.id)
        commit(state, selection)
        finish(state)
        return selection
    }

    /**
     * 스카우트팀에 맡긴 지명 한 건 (유저 요청 2026-10-04 "지명 자동 진행").
     *
     * 다른 구단 차례면 [step] 과 같고, [delegatingTeam] 차례면 그 구단의 추천 기준([ScoutingDepartment.draftPolicy])으로
     * 본 **우리 평가 1위**를 뽑는다. 우리 쪽 선택에는 난수를 쓰지 않는다 — 유저가 고른 기준이 그대로 드러나게.
     *
     * @return 이번에 나온 지명. 드래프트가 끝났으면 null
     */
    fun stepDelegated(state: SeasonState, delegatingTeam: TeamId, random: Random): DraftSelection? {
        val current = begin(state)
        val slot = current.current() ?: return step(state, null, random)
        if (current.isComplete || slot.ownerTeam != delegatingTeam) return step(state, null, random)
        val pick = scoutTeamPick(state, delegatingTeam) ?: return step(state, null, random)
        return select(state, pick.id)
    }

    /**
     * 스카우트 정기 리포트를 쓴다 (2026-10-04). 추천([advise])·평가 오르내림·관찰 완료를 **값으로 고정**해 담는다.
     *
     * @param previous 지난 리포트. 오르내림과 "이번에 새로 관찰을 마친 선수"를 가리는 데 쓴다
     */
    fun writeDigest(state: SeasonState, teamId: TeamId, final: Boolean, previous: baseballgm.scouting.ScoutDigest?): baseballgm.scouting.ScoutDigest {
        val department = state.scoutingOf(teamId)
        val pool = state.league.draftPool
        val available = state.draft?.availableProspects() ?: pool.prospects
        val reports = available.associateWith { report(state, teamId, it) }
        fun potentialOf(r: ScoutReport) = r.scouted.potentialLabel

        val advice = advise(state, teamId)
        val rounds = advice.map { round ->
            baseballgm.scouting.DigestRound(
                round = round.slot.round,
                overallPick = round.slot.overallPick,
                picks = round.picks.map { pick ->
                    val r = reports.getValue(pick.prospect)
                    baseballgm.scouting.DigestPick(
                        playerId = pick.prospect.id,
                        name = r.scouted.name,
                        position = r.scouted.positionLabel,
                        overall = r.scouted.overall.toString(),
                        potential = potentialOf(r),
                        availability = pick.availability,
                        reasons = pick.reasons,
                    )
                },
            )
        }

        // 평가 = 현재 능력·잠재력 추정의 평균. 관찰로 범위가 좁아지며 중심이 움직이면 오르내림이 생긴다
        val estimates = reports.mapKeys { it.key.id }.mapValues { (_, r) -> (r.scouted.overall.center + r.potentialRange.center) / 2.0 }
        val moves = previous?.estimates.orEmpty().let { before ->
            reports.mapNotNull { (prospect, r) ->
                val old = before[prospect.id] ?: return@mapNotNull null
                val delta = estimates.getValue(prospect.id) - old
                if (kotlin.math.abs(delta) < digestMoverMin) null
                else baseballgm.scouting.DigestMove(prospect.id, r.scouted.name, r.scouted.positionLabel, delta, potentialOf(r))
            }
        }

        val completedIds = available.filter { department.weeksOn(it.id) > 0 && scouting.isFullyScouted(department, it.player, teamId) }
            .map { it.id }
        val newlyCompleted = completedIds.filter { it !in previous?.completedIds.orEmpty() }
            .mapNotNull { id -> reports.entries.firstOrNull { it.key.id == id } }
            .map { (p, r) -> baseballgm.scouting.DigestName(p.id, r.scouted.name, r.scouted.positionLabel, potentialOf(r)) }

        return baseballgm.scouting.ScoutDigest(
            season = state.season,
            week = state.week,
            final = final,
            policy = department.draftPolicy,
            headline = digestHeadline(rounds, final),
            rounds = rounds,
            risers = moves.filter { it.delta > 0 }.sortedByDescending { it.delta }.take(digestMovers),
            fallers = moves.filter { it.delta < 0 }.sortedBy { it.delta }.take(digestMovers),
            completed = newlyCompleted,
            estimates = estimates,
            completedIds = completedIds,
        )
    }

    /** 스카우트팀장 한 줄. 1라운드 1순위 후보와 확률을 담백하게 */
    private fun digestHeadline(rounds: List<baseballgm.scouting.DigestRound>, final: Boolean): String {
        val head = if (final) "드래프트 직전 최종 보고입니다." else "정기 보고입니다."
        val first = rounds.firstOrNull() ?: return "$head 올해 남은 우리 지명권이 없습니다."
        val top = first.picks.firstOrNull() ?: return "$head ${first.round}라운드엔 마땅히 노릴 선수가 안 보입니다."
        val percent = (top.availability * PERCENT).toInt()
        return "$head ${first.round}라운드는 ${top.name}(${top.position}) 선수를 노려볼 만합니다 — 우리 차례까지 남을 확률 $percent%."
    }

    private val digestMoverMin = balance.double("draftAdvisor.reportMoverMin")
    private val digestMovers = balance.int("draftAdvisor.reportMovers")
    /** 리포트 주기 (주) */
    val digestEveryWeeks: Int = balance.int("draftAdvisor.reportEveryWeeks")

    /** 스카우트팀이 고를 선수: 추천 기준으로 본 우리 평가 1위 (같은 자리 겹침 감점 포함) */
    fun scoutTeamPick(state: SeasonState, teamId: TeamId): DraftProspect? {
        val available = state.draft?.availableProspects() ?: return null
        return advisor.rank(
            available,
            state.scoutingOf(teamId).draftPolicy,
            state.playersOf(teamId),
            draftedSoFar(state, teamId),
            state.season,
        ) { precisionOf(state, teamId, it) }.firstOrNull()
    }

    /** 유저 지명. */
    fun select(state: SeasonState, prospectId: PlayerId): DraftSelection {
        val current = begin(state)
        val selection = draft.select(current, prospectId)
        commit(state, selection)
        finish(state)
        return selection
    }

    /** 남은 순번이 없으면 결과를 확정한다. */
    fun finish(state: SeasonState) {
        val current = state.draft ?: return
        if (!current.isComplete) return
        if (state.draftResult != null) return
        state.draftResult = current.result()
        // 드래프트가 끝나면 올해 풀에 붙은 관찰 슬롯을 모든 구단에서 비운다. 더 볼 이유가 없다
        releaseFocus(state, current.pool.prospects.map { it.id })
    }

    /**
     * 집중 관찰 슬롯에서 뺀다. 관찰 주차 기록은 남으므로(ScoutingDepartment) 리포트 정확도는 그대로다 —
     * 슬롯만 비워서 외국인 후보 등 다른 데 쓸 수 있게 한다. 지명 결과에는 영향이 없다 (정확도는 주차로만 정해진다).
     */
    private fun releaseFocus(state: SeasonState, ids: Collection<PlayerId>) {
        state.league.teams.forEach { team ->
            val department = state.scoutingOf(team.id)
            ids.forEach(department::removeFocus)
        }
    }

    /**
     * 남은 우리 순번마다 지명 추천 ([DraftAdvisor]). 드래프트 전이면 순번표를 미리 만들어 본다 (상태는 안 바꾼다).
     * 드래프트가 끝났으면 빈 목록.
     */
    fun advise(state: SeasonState, teamId: TeamId): List<RoundAdvice> {
        if (state.draftResult != null) return emptyList()
        val league = state.league
        val current = state.draft
        val order = current?.order?.drop(current.index)
            ?: draft.prepare(league.season, league.draftOrder(), state.draftRights, league.draftPool).order
        val available = current?.availableProspects() ?: league.draftPool.prospects
        val department = state.scoutingOf(teamId)
        val policy = department.draftPolicy
        return advisor.advise(
            order = order,
            available = available,
            userTeam = teamId,
            roster = state.playersOf(teamId),
            pending = draftedSoFar(state, teamId),
            season = state.season,
            policy = policy,
            ourPrecisionOf = { precisionOf(state, teamId, it) },
            seed = adviceSeed(league.season, current?.index ?: 0, policy, teamId),
        )
    }

    /**
     * 자동 집중 관찰 (유저 요청 2026-10-03). 매주 초 유저 구단에 돌린다.
     *
     * ① 더 봐도 정확도가 오르지 않는 드래프트 후보를 슬롯에서 뺀다 (관찰 주차 기록은 남는다)
     * ② 빈 슬롯을 추천 기준 순으로 채운다 — 이미 다 본 선수·지명된 선수는 건너뛴다
     *
     * 외국인 후보에 붙인 슬롯은 건드리지 않는다. 드래프트가 끝났으면 아무것도 하지 않는다.
     */
    fun autoFocus(state: SeasonState, teamId: TeamId): AutoFocusResult {
        val department = state.scoutingOf(teamId)
        if (!department.autoFocus || state.draftResult != null) return AutoFocusResult()
        val pool = state.league.draftPool
        if (pool.prospects.isEmpty()) return AutoFocusResult()

        val finished = department.activeFocus()
            .mapNotNull { pool.byId(it) }
            .filter { scouting.isFullyScouted(department, it.player, teamId) }
        finished.forEach { department.removeFocus(it.id) }

        val slots = scouting.focusSlots(department)
        val available = state.draft?.availableProspects() ?: pool.prospects
        val candidates = available.filter {
            !department.isFocused(it.id) && !scouting.isFullyScouted(department, it.player, teamId)
        }
        val started = mutableListOf<DraftProspect>()
        if (department.activeFocus().size < slots) {
            advisor.rank(candidates, department.draftPolicy, state.playersOf(teamId), draftedSoFar(state, teamId), state.season) {
                precisionOf(state, teamId, it)
            }.forEach { prospect ->
                if (department.activeFocus().size < slots && department.addAutoFocus(prospect.id, slots)) started += prospect
            }
        }
        return AutoFocusResult(started = started, finished = finished)
    }

    /** 이 팀의 눈으로 본 지명 후보 (docs/10 리포트). */
    fun board(state: SeasonState, teamId: TeamId, limit: Int = BOARD_SIZE): List<Pair<DraftProspect, Double>> {
        val pool = state.draft?.availableProspects() ?: state.league.draftPool.prospects
        return ai.rank(pool, teamId, state.playersOf(teamId), state.season, draftedSoFar(state, teamId)) {
            precisionOf(state, teamId, it)
        }.take(limit)
    }

    /**
     * 포지션 필요도는 **영입 이후 상태로 계산한다** (docs/11). 이번 드래프트에서 이미 뽑은 선수도
     * 선수단에 넣고 본다 — 그러지 않으면 같은 포지션만 내리 뽑는다.
     */
    private fun draftedSoFar(state: SeasonState, teamId: TeamId) =
        (state.draft?.selectionsOf(teamId).orEmpty())
            .mapNotNull { state.league.draftPool.byId(it.playerId)?.player }

    /**
     * 리포트. **이 팀이 지명한 신인은 우리 선수로 본다** — 현재 능력치는 정확히, 잠재력은 등급으로
     * (docs/10 "지명 이후"). 입단(다음 시즌 개막) 전이라 소속은 비어 있어도 계약금을 치른 우리 선수다.
     */
    fun report(state: SeasonState, teamId: TeamId, prospect: DraftProspect): ScoutReport =
        report(state, teamId, prospect, owned = isDraftedBy(state, teamId, prospect.id))

    /** 지명 전 스카우트가 보던 리포트 (우리 신인 화면의 "지명 전 예상"과 비교용) */
    fun scoutingReport(state: SeasonState, teamId: TeamId, prospect: DraftProspect): ScoutReport =
        report(state, teamId, prospect, owned = false)

    private fun report(state: SeasonState, teamId: TeamId, prospect: DraftProspect, owned: Boolean): ScoutReport =
        scouting.report(
            ownedByViewer = owned,
            department = state.scoutingOf(teamId),
            player = prospect.player,
            viewerTeam = teamId,
            season = state.season,
            school = prospect.school,
            physique = prospect.physique(),
            injuryHistory = prospect.injuryHistory,
        )

    /** 올해 드래프트에서 [teamId] 가 지명한 선수인가 (진행 중이든 끝났든) */
    fun isDraftedBy(state: SeasonState, teamId: TeamId, playerId: baseballgm.model.PlayerId): Boolean =
        (state.draft?.selections ?: state.draftResult?.selections.orEmpty())
            .any { it.teamId == teamId && it.playerId == playerId }

    private fun precisionOf(state: SeasonState, teamId: TeamId, prospect: DraftProspect) =
        scouting.precisionFor(state.scoutingOf(teamId), prospect.player, teamId)

    private fun commit(state: SeasonState, selection: DraftSelection) {
        // 지명된 선수는 곧바로 관찰 슬롯에서 빠진다
        releaseFocus(state, listOf(selection.playerId))
        state.inbox.add(
            week = state.week,
            category = InboxCategory.DRAFT,
            teamId = selection.teamId,
            text = selection.line(),
        )
    }

    /** 모의 드래프트 씨앗. 시즌 난수와 따로 둔다 — 추천을 열어 봐도 실제 결과가 바뀌지 않는다 */
    private fun adviceSeed(season: Int, index: Int, policy: DraftPolicy, teamId: TeamId): Long =
        season * SEED_SEASON + index * SEED_INDEX + policy.ordinal * SEED_POLICY + teamId.value.hashCode()

    private companion object {
        const val BOARD_SIZE = 30
        const val SEED_SEASON = 1_000_003L
        const val SEED_INDEX = 7_919L
        const val SEED_POLICY = 31L
        const val PERCENT = 100
    }
}

/** 자동 집중 관찰 결과. 알림함·비서 브리핑에 쓴다 */
data class AutoFocusResult(
    val started: List<DraftProspect> = emptyList(),
    val finished: List<DraftProspect> = emptyList(),
) {
    val isEmpty: Boolean get() = started.isEmpty() && finished.isEmpty()
}
