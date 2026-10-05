package baseballgm.season

import baseballgm.model.Pitcher
import baseballgm.model.RosterLevel
import baseballgm.model.TeamId
import baseballgm.sim.GameTeam
import baseballgm.tactics.DirectiveSheet

/**
 * 경기 한 판에 내보낼 팀을 꾸린다.
 *
 * 정규시즌([WeekLoop])과 포스트시즌([Postseason])이 **같은 방식으로** 팀을 꾸려야 한다.
 * 규칙표·엔트리·투수 연투 상태를 한쪽만 다르게 보면 "포스트시즌용 시뮬레이터"를 따로 만든 셈이
 * 되어 불변 원칙 5를 어기게 된다.
 */
internal fun gameTeamOf(
    state: SeasonState,
    sheet: DirectiveSheet,
    teamId: TeamId,
    absoluteDay: Int,
    startingPitcherIndex: Int = state.nextRotationIndex(teamId),
    /** 단장 휴식 지시. 정규시즌은 이번 주 지시, 포스트시즌은 다음 경기 지시를 넘긴다 */
    resting: Set<baseballgm.model.PlayerId> = state.restingThisWeek,
): GameTeam {
    val roster = state.playersOf(teamId)
    val starters = sheet.rotation.starters
    val starter = starters[startingPitcherIndex.mod(starters.size)]
    val pitcherIds = roster.filterIsInstance<Pitcher>().map { it.id }
    val unavailable = roster
        .filter {
            it.rosterLevel != RosterLevel.FIRST_TEAM || it.condition.isInjured ||
                !it.military.isAvailable || state.isOnInternationalDuty(it.id) ||
                it.id in resting
        }
        .map { it.id }
        .toSet()
    // 국제대회 차출·휴식 지시 선수는 대타·대수비로도 못 나온다 → 경기 명단에서 아예 뺀다
    val away = roster.filter { state.isOnInternationalDuty(it.id) || it.id in resting }.map { it.id }.toSet()
    return GameTeam(
        teamId = teamId,
        roster = roster.filter { it.id !in away || it.id == starter }.associateBy { it.id },
        directives = sheet,
        startingPitcher = starter,
        availability = state.usage.availabilityFor(pitcherIds, absoluteDay, unavailable),
    )
}
