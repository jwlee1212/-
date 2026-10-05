package baseballgm.season

import baseballgm.io.BalanceConfig
import baseballgm.model.Pitcher
import baseballgm.model.Player
import baseballgm.model.PlayerId
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId

/** 엔트리 이동 한 건. 알림함에 그대로 올라간다. */
data class RosterMove(
    val teamId: TeamId,
    val playerId: PlayerId,
    val promoted: Boolean,
    val reason: String,
    /** 부상으로 내린 것인가 (알림함 칩 색) */
    val injury: Boolean = false,
)

/**
 * 엔트리 이력 (docs/07).
 *
 * 말소한 선수는 **다음 주에 다시 못 올리고 그다음 주부터** 가능하다. 부상 복귀 선수는 2군에서
 * 재활 1주를 채워야 한다. 두 규칙 모두 "언제 내려갔는지"를 기억해야 해서 따로 들고 있는다.
 */
class RosterState {
    private val demotedAtWeek = mutableMapOf<PlayerId, Int>()
    private val rehabReadyWeek = mutableMapOf<PlayerId, Int>()
    private val pickedUntilWeek = mutableMapOf<PlayerId, Int>()

    fun markDemoted(playerId: PlayerId, week: Int) {
        demotedAtWeek[playerId] = week
        pickedUntilWeek.remove(playerId)
    }

    /**
     * 단장이 직접 1군에 올린 선수 (2026-10-03, 유저 요청). [untilWeek] 주차 시작까지 자동 베스트 맞추기가 내리지 않는다.
     * 유망주 시험처럼 일부러 올린 선수를 다음 주에 바로 내리지 않으려는 것이다
     */
    fun markPicked(playerId: PlayerId, untilWeek: Int) {
        pickedUntilWeek[playerId] = untilWeek
    }

    fun isPicked(playerId: PlayerId, week: Int): Boolean = (pickedUntilWeek[playerId] ?: Int.MIN_VALUE) >= week

    /** 세이브: 단장 지명 보호가 끝나는 주차 */
    internal fun exportPicks(): Map<PlayerId, Int> = pickedUntilWeek.toMap()

    internal fun importPicks(picks: Map<PlayerId, Int>) {
        pickedUntilWeek.clear(); pickedUntilWeek.putAll(picks)
    }

    /** 세이브: (말소 주차, 재활 복귀 주차) */
    internal fun export(): Pair<Map<PlayerId, Int>, Map<PlayerId, Int>> = demotedAtWeek.toMap() to rehabReadyWeek.toMap()

    internal fun import(demoted: Map<PlayerId, Int>, rehab: Map<PlayerId, Int>) {
        demotedAtWeek.clear(); demotedAtWeek.putAll(demoted)
        rehabReadyWeek.clear(); rehabReadyWeek.putAll(rehab)
    }

    fun markRehab(playerId: PlayerId, readyWeek: Int) {
        rehabReadyWeek[playerId] = readyWeek
    }

    fun clearRehab(playerId: PlayerId) {
        rehabReadyWeek.remove(playerId)
    }

    fun rehabReadyAt(playerId: PlayerId): Int? = rehabReadyWeek[playerId]

    /** 말소 후 재등록 제한 (임시값: 다음 주 불가, 그다음 주부터 가능). */
    fun canPromote(playerId: PlayerId, week: Int, blockWeeks: Int): Boolean {
        val demoted = demotedAtWeek[playerId] ?: return true
        if (week < demoted + 1 + blockWeeks) return false
        val rehab = rehabReadyWeek[playerId] ?: return true
        return week >= rehab
    }
}

/**
 * 엔트리 관리 (docs/07).
 *
 * AI 구단과 유저 구단의 주 시작 자동 관리를 이 규칙으로 한다. 유저는 그 위에서 직접 등록·말소할 수 있다([RosterActions]).
 *
 * 순서가 중요하다.
 * ① 부상자·복무자를 내린다 → ② 부상 복귀 선수를 올린다 → ③ 최대를 넘은 포지션을 줄인다 →
 * ④ 최소에 못 미치는 포지션을 채운다 (자리가 없으면 남는 포지션의 가장 약한 선수와 바꾼다) →
 * ⑤ 남은 빈자리를 2군 최고 선수로 채운다 (최대를 넘지 않는 포지션에서) → ⑥ 28명을 넘으면 정리한다.
 *
 * 포지션 범위는 [PositionLimits] (`roster.positionLimits`). 28명만 보고 "2군에서 제일 잘하는 선수"로 채우면
 * 유격수 다섯, 포수 하나 같은 1군이 나와서 넣었다 (2026-10-01).
 *
 * **포지션 맞추기(②의 맞바꾸기·③·④)는 AI 구단에만 한다** ([guided]). 유저 구단은 같은 날 유저 요청으로 포지션 제한을 없앴다 —
 * 단장이 짠 구성을 주 시작마다 자동 관리가 뒤집으면 그것도 제한이다. 유저 구단에는 부상자 말소·부상 복귀·
 * 빈자리 채우기·28명 정리, 그리고 ⑦ 베스트 맞추기(2군의 확실히 나은 선수를 1군 최약체와 맞바꿈)만 한다.
 * 부상 복귀는 1군이 꽉 차 있어도 가장 약한 선수와 바꿔 올린다 (2026-10-03).
 */
class RosterManager(balance: BalanceConfig) {

    private val firstTeamSize = balance.int("roster.firstTeamRegistered")
    private val blockWeeks = balance.int("roster.reRegisterBlockWeeks")
    private val rehabWeeks = balance.int("injury.rehabWeeksInFutures")
    private val userSwapMargin = balance.double("roster.userAutoBestMargin")
    private val limits = PositionLimits(balance)

    fun manage(
        teamId: TeamId,
        roster: List<Player>,
        week: Int,
        state: RosterState,
        rank: (Player) -> Double,
        onChange: (Player) -> Unit,
        /** 포지션 범위에 맞춰 구성을 고치는가. AI 구단 true, 유저 구단 false */
        guided: Boolean = true,
        /** 이번에는 올리지 않고 2군에 그대로 두는 선수 — 부상 복귀 돌발 이벤트로 단장에게 물을 주전 (2026-10-05) */
        hold: Set<PlayerId> = emptySet(),
    ): List<RosterMove> {
        val moves = mutableListOf<RosterMove>()
        // 작업용 사본. 승격·말소할 때마다 이 목록의 선수를 바꿔 끼워야 인원 계산이 맞는다
        val current = roster.toMutableList()
        fun firstTeam() = current.filter { it.rosterLevel == RosterLevel.FIRST_TEAM }

        fun replace(player: Player, level: RosterLevel) {
            val updated = player.withRosterLevel(level)
            val index = current.indexOfFirst { it.id == player.id }
            if (index >= 0) current[index] = updated else current += updated
            onChange(updated)
        }

        fun demote(player: Player, reason: String, injury: Boolean = false) {
            replace(player, RosterLevel.FUTURES)
            state.markDemoted(player.id, week)
            moves += RosterMove(teamId, player.id, promoted = false, reason = reason, injury = injury)
        }

        fun promote(player: Player, reason: String) {
            replace(player, RosterLevel.FIRST_TEAM)
            state.clearRehab(player.id)
            moves += RosterMove(teamId, player.id, promoted = true, reason = reason)
        }

        // ① 부상자는 엔트리 자리를 차지하지 않는다 → 2군으로 내리고 재활 복귀 주차를 기록한다
        firstTeam().filter { it.condition.isInjured }.forEach { player ->
            val injury = player.condition.injury!!
            state.markRehab(player.id, week + injury.weeksRemaining + rehabWeeks)
            demote(player, "${injury.part} 부상 (${injury.weeksRemaining}주)", injury = true)
        }
        // 복무 중인 선수도 엔트리에서 빠진다 (docs/12)
        firstTeam().filter { !it.military.isAvailable }.forEach { player -> demote(player, "군 복무") }

        val candidates = {
            current.filter {
                it.rosterLevel == RosterLevel.FUTURES && !it.condition.isInjured && it.military.isAvailable &&
                    state.canPromote(it.id, week, blockWeeks) && it.id !in hold
            }
        }

        // ② 부상에서 돌아온 선수를 먼저 올린다. 그 포지션이 꽉 찼으면 같은 포지션의 더 약한 선수와 바꾼다
        candidates().filter { state.rehabReadyAt(it.id) != null && week >= state.rehabReadyAt(it.id)!! }
            .sortedByDescending(rank)
            .forEach { player ->
                val team = firstTeam()
                if (team.size < firstTeamSize && (!guided || limits.hasRoom(team, player))) {
                    promote(player, "부상 복귀")
                    return@forEach
                }
                val slot = RosterSlot.of(player)
                if (!guided) {
                    // 유저 구단 (2026-10-03, 유저 요청): 부상 전 1군이던 선수는 회복하면 무조건 돌아온다.
                    // 같은 포지션 → 같은 투수/야수 → 전체 순으로 가장 약한 선수와 바꾼다. 이번 주에 올린 선수는 내리지 않는다
                    val returned = moves.filter { it.promoted }.map { it.playerId }.toSet()
                    val pool = team.filter { it.id !in returned && !state.isPicked(it.id, week) }
                    val weakest = pool.filter { RosterSlot.of(it) == slot }.minByOrNull(rank)
                        ?: pool.filter { (it is Pitcher) == (player is Pitcher) }.minByOrNull(rank)
                        ?: pool.minByOrNull(rank)
                        ?: return@forEach
                    demote(weakest, "${player.registeredName} 부상 복귀로 자리 양보")
                    promote(player, "부상 복귀")
                    return@forEach
                }
                val weakest = team.filter { RosterSlot.of(it) == slot }.minByOrNull(rank) ?: return@forEach
                if (rank(player) > rank(weakest)) {
                    demote(weakest, "${player.registeredName} 부상 복귀로 자리 양보")
                    promote(player, "부상 복귀")
                }
            }

        // ③ 최대를 넘은 포지션(또는 투수·야수 전체)을 줄인다. 빈자리는 ⑤ 에서 다른 포지션으로 채운다
        var guard = if (guided) 0 else GUARD
        while (guard++ < GUARD) {
            val team = firstTeam()
            val slot = limits.crowdedSlots(team).firstOrNull()
            val victim = if (slot != null) {
                team.filter { RosterSlot.of(it) == slot }.minByOrNull(rank)
            } else {
                listOf(true, false).firstOrNull { pitcher -> team.count { (it is Pitcher) == pitcher } > limits.groupRange(pitcher).last }
                    ?.let { pitcher -> team.filter { (it is Pitcher) == pitcher && limits.canSpare(team, it) }.minByOrNull(rank) }
            } ?: break
            demote(victim, "포지션 정리 — ${RosterSlot.of(victim).label} 과다")
        }

        // ④ 최소에 못 미치는 포지션을 채운다. 자리가 없으면 남는 포지션에서 가장 약한 선수를 내린다
        // 바로 올릴 수 없으면(28명이 찼거나 투수·야수 전체가 최대) 다른 포지션의 가장 약한 선수와 바꾼다.
        // 어떻게 해도 못 채우는 포지션은 건너뛰고 다음 포지션으로 간다
        guard = if (guided) 0 else GUARD
        val unfixable = mutableSetOf<RosterSlot>()
        while (guard++ < GUARD) {
            val team = firstTeam()
            val pool = candidates()
            val slot = limits.shortSlots(team)
                .firstOrNull { short -> short !in unfixable && pool.any { RosterSlot.of(it) == short } } ?: break
            val pick = pool.filter { RosterSlot.of(it) == slot }.maxBy(rank)
            val direct = team.size < firstTeamSize && limits.problem(team, team + pick) == null
            if (!direct) {
                val victim = team.filter { RosterSlot.of(it) != slot && limits.problem(team, team - it + pick) == null }
                    .minByOrNull(rank)
                if (victim == null) {
                    unfixable += slot
                    continue
                }
                demote(victim, "${slot.label} 보강으로 말소")
            }
            promote(pick, "${slot.label} 보강 콜업")
        }

        // ⑤ 남은 빈자리: 투수·야수 전체 최소부터 맞추고, 최대를 넘지 않는 포지션의 2군 최고 선수로 채운다
        while (firstTeam().size < firstTeamSize) {
            val team = firstTeam()
            val shortGroup = limits.shortGroup(team)
            // 유저 구단은 포지션 최대를 보지 않는다. 투수·야수 중 모자란 쪽을 먼저 채우는 건 둘 다 같다
            val open = candidates().filter { !guided || limits.hasRoom(team, it) }
            val pool = open.filter { shortGroup == null || (it is Pitcher) == shortGroup }.ifEmpty { open }
            val pick = pool.maxByOrNull(rank) ?: break
            promote(
                pick,
                when (shortGroup) {
                    true -> "투수 보강 콜업"
                    false -> "야수 보강 콜업"
                    null -> "콜업"
                },
            )
        }

        // ⑥ 인원이 넘치면 최소를 깨지 않는 선에서 가장 아래 선수를 내린다
        while (firstTeam().size > firstTeamSize) {
            val team = firstTeam()
            val pick = team.filter { !guided || limits.canSpare(team, it) }.minByOrNull(rank) ?: team.minByOrNull(rank) ?: break
            demote(pick, "엔트리 정리")
        }

        // ⑦ 유저 구단 베스트 맞추기 (2026-10-03, 유저 요청 "수동 조작 안 하더라도 로스터를 항상 베스트로").
        // 트레이드로 온 선수·부상에서 돌아온 선수처럼 2군에 남은 고종합 선수를, 같은 투수/야수 1군 최약체와 맞바꾼다.
        // 투수·야수 인원은 그대로고, 포지션 최소(포수 2명 등)를 깨는 맞바꿈은 하지 않는다. 이번 주에 올린 선수와
        // 단장이 직접 올린 지 얼마 안 된 선수([RosterState.isPicked], `roster.userPickProtectWeeks`)는 내리지 않는다
        if (!guided) {
            var swaps = 0
            while (swaps++ < GUARD) {
                val team = firstTeam()
                val promoted = moves.filter { it.promoted }.map { it.playerId }.toSet()
                val swap = candidates().sortedByDescending(rank).firstNotNullOfOrNull { player ->
                    val slot = RosterSlot.of(player)
                    team.filter { victim ->
                        victim.id !in promoted && !state.isPicked(victim.id, week) && (victim is Pitcher) == (player is Pitcher) &&
                            (RosterSlot.of(victim) == slot || limits.canSpareSlot(team, victim))
                    }.minByOrNull(rank)
                        ?.takeIf { rank(player) > rank(it) + userSwapMargin }
                        ?.let { player to it }
                } ?: break
                val (player, victim) = swap
                demote(victim, "${player.registeredName} 등록으로 말소 (베스트 엔트리)")
                promote(player, "베스트 엔트리 콜업")
            }
        }

        return moves
    }

    private companion object {
        /** 포지션 맞추기 반복 한도 (무한 루프 방지) */
        const val GUARD = 60
    }
}

/** 로스터 레벨만 바꾼 사본. `Player` 가 sealed 라 여기서 한 번만 분기한다. */
fun Player.withRosterLevel(level: RosterLevel): Player = when (this) {
    is baseballgm.model.Batter -> copy(rosterLevel = level)
    is Pitcher -> copy(rosterLevel = level)
}

/** 컨디션만 바꾼 사본. */
fun Player.withCondition(condition: baseballgm.model.Condition): Player = when (this) {
    is baseballgm.model.Batter -> copy(condition = condition)
    is Pitcher -> copy(condition = condition)
}
