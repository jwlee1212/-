package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId

/**
 * 단장이 직접 하는 엔트리 등록·말소 (docs/07 엔트리 규칙).
 *
 * 지키는 규칙은 1군 28명, 말소하면 다음 주까지 재등록 불가, 부상·군 복무 중인 선수는 못 올린다 — 이것뿐이다.
 * **포지션별 인원 범위는 유저에게 걸지 않는다** (2026-10-01 유저 요청: 유동적인 운영을 막는다). 포수 없이 가든, 투수를 16명 두든
 * 단장 마음이다. 경기는 감독 AI 의 비상 라인업(2군에서 메우기, 다른 포지션 기용)으로 어떻게든 치른다. 돌발 이벤트("누구를 올릴까요?")와 로스터 화면이 함께 쓴다.
 *
 * 주중에 써도 된다. 바뀐 엔트리는 **다음 날 경기부터** 반영된다 — 라인업을 매일 다시 짜기 때문이다.
 *
 * 포스트시즌(2026-10-03): 살아 있는 팀은 **자기 시리즈를 시작하기 전까지** 엔트리를 바꿀 수 있다
 * ([PostseasonProgress.entryOpenFor]). 포스트시즌 엔트리는 시리즈마다 새로 내는 것이라 재등록 제한은 보지 않는다 (임시 결정).
 */
class RosterActions(balance: BalanceConfig) {

    private val firstTeamSize = balance.int("roster.firstTeamRegistered")
    private val blockWeeks = balance.int("roster.reRegisterBlockWeeks")
    /** 포지션별 인원 세기(화면 표시용)에만 쓴다. 범위 판정은 하지 않는다 */
    private val limits = PositionLimits(balance)
    private val rehabWeeks = balance.int("injury.rehabWeeksInFutures")
    private val protectWeeks = balance.int("roster.userPickProtectWeeks")
    private val postseasonSpots = balance.int("postseason.spots")

    /** 1군 등록이 안 되는 이유. 되면 null */
    fun promoteProblem(state: SeasonState, teamId: TeamId, playerId: PlayerId, swappingOut: PlayerId? = null): String? {
        val player = ownPlayer(state, teamId, playerId) ?: return "우리 선수가 아니에요"
        seasonClosedProblem(state, teamId)?.let { return it }
        if (player.rosterLevel == RosterLevel.FIRST_TEAM) return "이미 1군이에요"
        eligibilityProblem(state, playerId)?.let { return it }
        val firstTeam = state.firstTeamOf(teamId)
        if (swappingOut == null && firstTeam.size >= firstTeamSize) {
            return "1군이 ${firstTeamSize}명으로 꽉 찼어요. 내릴 선수를 같이 골라 주세요"
        }
        return null
    }

    /** 인원·포지션과 상관없이 이 선수를 1군에 올릴 수 있는 몸·자격인가 (부상·복무·재등록 제한) */
    fun eligibilityProblem(state: SeasonState, playerId: PlayerId): String? {
        val player = state.player(playerId)
        player.condition.injury?.let { return "${it.part} 부상 중이에요 (${it.weeksRemaining}주)" }
        if (!player.military.isAvailable) return "군 복무 중이에요"
        if (!inPostseasonWindow(state) && !state.roster.canPromote(playerId, state.week, blockWeeks)) {
            return "말소된 지 얼마 안 돼서 ${reRegisterWeek(state, playerId)}주차부터 올릴 수 있어요"
        }
        return null
    }

    /** 1군 말소가 안 되는 이유. 되면 null */
    fun demoteProblem(state: SeasonState, teamId: TeamId, playerId: PlayerId, replacement: PlayerId? = null): String? {
        val player = ownPlayer(state, teamId, playerId) ?: return "우리 선수가 아니에요"
        seasonClosedProblem(state, teamId)?.let { return it }
        if (player.rosterLevel != RosterLevel.FIRST_TEAM) return "1군 선수가 아니에요"
        return null
    }

    /** 정규시즌이 끝난 뒤 엔트리를 못 바꾸는 이유. 포스트시즌 엔트리 제출 기간이면 null */
    private fun seasonClosedProblem(state: SeasonState, teamId: TeamId): String? {
        if (!state.isRegularSeasonOver) return null
        val progress = state.postseasonProgress
        return when {
            progress != null && progress.entryOpenFor(teamId) -> null
            progress != null && progress.isAlive(teamId) -> "시리즈 중에는 엔트리를 못 바꿔요. 이 시리즈가 끝나면 다시 열려요"
            progress == null && state.standings.ranked().take(postseasonSpots).any { it.teamId == teamId } ->
                "포스트시즌을 시작하면 엔트리를 정할 수 있어요"
            else -> "정규시즌이 끝났어요"
        }
    }

    private fun inPostseasonWindow(state: SeasonState): Boolean =
        state.isRegularSeasonOver && state.postseasonProgress?.isFinished == false

    /** 말소하면 언제 다시 올릴 수 있나 (지금 말소한다고 칠 때) */
    fun reRegisterWeekIfDemotedNow(state: SeasonState): Int = state.week + 1 + blockWeeks

    /** 지금 재등록 제한에 걸려 있으면 풀리는 주차 */
    fun reRegisterWeek(state: SeasonState, playerId: PlayerId): Int {
        var week = state.week
        while (!state.roster.canPromote(playerId, week, blockWeeks) && week < state.week + REGISTER_SEARCH_WEEKS) week++
        return week
    }

    /** @param picked 단장이 직접 고른 콜업인가. 그러면 몇 주 동안 자동 베스트 맞추기가 내리지 않는다 (비서 처리는 false) */
    fun promote(state: SeasonState, teamId: TeamId, playerId: PlayerId, picked: Boolean = true): RosterMove? {
        if (promoteProblem(state, teamId, playerId) != null) return null
        return applyPromote(state, teamId, playerId, "단장 지시 콜업", picked)
    }

    fun demote(state: SeasonState, teamId: TeamId, playerId: PlayerId): RosterMove? {
        if (demoteProblem(state, teamId, playerId) != null) return null
        return applyDemote(state, teamId, playerId, "단장 지시 말소")
    }

    /** 한 명 내리고 한 명 올린다. 둘 다 규칙을 통과해야 한다 */
    fun swap(state: SeasonState, teamId: TeamId, up: PlayerId, down: PlayerId, picked: Boolean = true): List<RosterMove> {
        if (promoteProblem(state, teamId, up, swappingOut = down) != null) return emptyList()
        if (demoteProblem(state, teamId, down, replacement = up) != null) return emptyList()
        return listOfNotNull(
            applyDemote(state, teamId, down, "${state.player(up).registeredName} 콜업으로 말소"),
            applyPromote(state, teamId, up, "단장 지시 콜업", picked),
        )
    }

    /**
     * 부상자를 바로 말소한다 (돌발 이벤트 — 주 시작까지 기다리지 않는다). 재활 복귀 주차를 같이 적는다.
     * 인원 하한은 보지 않는다 — 다친 선수는 어차피 못 뛴다.
     */
    fun placeOnInjuredList(state: SeasonState, teamId: TeamId, playerId: PlayerId): RosterMove? {
        val player = ownPlayer(state, teamId, playerId) ?: return null
        val injury = player.condition.injury ?: return null
        if (player.rosterLevel != RosterLevel.FIRST_TEAM) return null
        state.roster.markRehab(playerId, state.week + injury.weeksRemaining + rehabWeeks)
        return applyDemote(state, teamId, playerId, "${injury.part} 부상 (${injury.weeksRemaining}주)")
    }

    /**
     * 이 선수를 대신할 2군 후보 — 그 선수를 내리고 올려도 포지션 범위가 괜찮은 선수.
     * 같은 포지션이 맨 앞, 그다음 같은 투수/야수 쪽.
     */
    fun replacementsFor(state: SeasonState, teamId: TeamId, player: Player): List<Player> {
        val slot = RosterSlot.of(player)
        return state.futuresOf(teamId)
            .filter { (it is Pitcher) == (player is Pitcher) && promoteProblem(state, teamId, it.id, swappingOut = player.id) == null }
            .sortedBy { if (RosterSlot.of(it) == slot) 0 else 1 }
    }

    /** 지금 1군의 포지션별 인원과 범위 */
    fun slotCounts(state: SeasonState, teamId: TeamId): List<SlotCount> = limits.counts(state.firstTeamOf(teamId))

    private fun applyPromote(state: SeasonState, teamId: TeamId, playerId: PlayerId, reason: String, picked: Boolean): RosterMove {
        state.update(state.player(playerId).withRosterLevel(RosterLevel.FIRST_TEAM))
        state.roster.clearRehab(playerId)
        // 올린 주 + 보호 주수: 그 주차 시작까지 자동 정리에서 빠진다
        if (picked) state.roster.markPicked(playerId, state.week + protectWeeks)
        val move = RosterMove(teamId, playerId, promoted = true, reason = reason)
        log(state, move)
        return move
    }

    private fun applyDemote(state: SeasonState, teamId: TeamId, playerId: PlayerId, reason: String): RosterMove {
        state.update(state.player(playerId).withRosterLevel(RosterLevel.FUTURES))
        state.roster.markDemoted(playerId, state.week)
        state.restingThisWeek.remove(playerId)
        val move = RosterMove(teamId, playerId, promoted = false, reason = reason)
        log(state, move)
        return move
    }

    private fun log(state: SeasonState, move: RosterMove) {
        state.inbox.add(
            week = state.week,
            category = InboxCategory.ROSTER,
            teamId = move.teamId,
            text = "${state.player(move.playerId).registeredName} ${if (move.promoted) "1군 등록" else "1군 말소"} — ${move.reason}",
        )
    }

    private fun ownPlayer(state: SeasonState, teamId: TeamId, playerId: PlayerId): Player? =
        runCatching { state.player(playerId) }.getOrNull()?.takeIf { it.teamId == teamId }

    private companion object {
        const val REGISTER_SEARCH_WEEKS = 60
    }
}
