package baseballgm.app

import baseballgm.management.GoalKind
import baseballgm.util.fixed

/** 목표 상태. 색은 화면이 의미 색 토큰에서 고른다 (좋음/주의/나쁨) */
enum class GoalStatus(val label: String) { ON_TRACK("순항"), CLOSE("아슬아슬"), OFF_TRACK("위험") }

/**
 * 홈 "구단주 목표" 카드의 진행 상황 (2026-10-02, 레퍼런스 리팩토링).
 *
 * @param detail "지금 4위 · 목표 5위 이내" 처럼 지금 값과 기준을 같이 쓴 한 줄
 * @param seasonFraction 정규시즌이 얼마나 지났는지 (0~1). 카드의 진행률 막대
 *
 * **임시 결정**: 엔진의 목표 판정(OwnerTrust)은 시즌이 끝나야 돈다. 시즌 중 "지금 이대로면?"은
 * 공개 순위·승률·예상 재정만으로 화면에서 어림한다 — 진짜 판정을 대신하지 않는다.
 * 한국시리즈 목표는 정규시즌 2위 이내(플레이오프 직행권)를, 플레이오프는 3위 이내를 "순항"으로 본다.
 */
data class GoalProgress(val status: GoalStatus, val detail: String, val seasonFraction: Float) {
    companion object {
        fun of(session: GameSession): GoalProgress {
            val weeks = session.state.calendar.regularSeasonWeeks
            val fraction = if (session.seasonOver) 1f else ((session.week - 1).toFloat() / weeks).coerceIn(0f, 1f)
            val rank = session.rank()
            val (status, detail) = when (val kind = session.seasonGoal().kind) {
                GoalKind.WINNING_RECORD -> {
                    val pct = session.record().winPct
                    val status = when {
                        pct >= WIN_TARGET -> GoalStatus.ON_TRACK
                        pct >= WIN_TARGET - CLOSE_WIN_PCT -> GoalStatus.CLOSE
                        else -> GoalStatus.OFF_TRACK
                    }
                    status to "승률 ${pct.fixed(3)} · 목표 ${WIN_TARGET.fixed(3)} 이상"
                }

                GoalKind.AVOID_DEFICIT -> {
                    val deficit = session.projectedFinance().deficit
                    val allowed = session.allowedDeficit()
                    val status = when {
                        deficit <= allowed * CLOSE_DEFICIT_RATIO -> GoalStatus.ON_TRACK
                        deficit <= allowed -> GoalStatus.CLOSE
                        else -> GoalStatus.OFF_TRACK
                    }
                    val now = if (deficit > 0) "예상 적자 ${deficit.fixed(1)}억" else "예상 흑자 ${(-deficit).fixed(1)}억"
                    status to "$now · 허용 ${allowed.fixed(1)}억"
                }

                else -> {
                    val target = targetRank(kind, session.league.teams.size)
                    val status = when {
                        rank <= target -> GoalStatus.ON_TRACK
                        rank == target + 1 -> GoalStatus.CLOSE
                        else -> GoalStatus.OFF_TRACK
                    }
                    status to "지금 ${rank}위 · 목표 ${target}위 이내"
                }
            }
            return GoalProgress(status, detail, fraction)
        }

        private fun targetRank(kind: GoalKind, teams: Int): Int = when (kind) {
            GoalKind.WIN_KOREAN_SERIES, GoalKind.REACH_KOREAN_SERIES -> 2
            GoalKind.REACH_PLAYOFF -> 3
            GoalKind.AVOID_LAST_PLACE -> teams - 1
            else -> POSTSEASON_SPOTS
        }

        private const val WIN_TARGET = 0.5
        private const val CLOSE_WIN_PCT = 0.02
        private const val CLOSE_DEFICIT_RATIO = 0.8
        private const val POSTSEASON_SPOTS = 5
    }
}
