package baseballgm.text

import baseballgm.model.PlayerId
import baseballgm.sim.GameEnded
import baseballgm.sim.GameEvent
import baseballgm.sim.GameStarted
import baseballgm.sim.Half
import baseballgm.sim.HalfInningEnded
import baseballgm.sim.HalfInningStarted
import baseballgm.sim.PaOutcome
import baseballgm.sim.PitcherChanged
import baseballgm.sim.PlateAppearanceCompleted
import baseballgm.sim.PlayerSubstituted
import baseballgm.sim.StolenBaseAttempted
import baseballgm.sim.WildPitchThrown

/**
 * 문자 중계 (docs/07).
 *
 * **이벤트 스트림을 문장으로 바꾸기만 한다.** 중계를 위해 경기를 다시 돌리지 않는다 (불변 원칙 5).
 * 관전하든 건너뛰든 같은 스트림이므로, 나중에 하이라이트만 뽑아도 경기 내용과 어긋나지 않는다.
 *
 * 조사는 [Hangul] 로 이름 끝 받침을 보고 고른다 ("김민준이 / 이서우가").
 */
class CommentaryRenderer(
    /** 선수 id → 화면에 쓸 이름 */
    private val nameOf: (PlayerId) -> String,
) {
    /** 경기 전체를 문장 목록으로 바꾼다. */
    fun render(events: List<GameEvent>): List<String> = events.mapNotNull { line(it) }

    /**
     * 주간 결산용 하이라이트 (docs/07 주요 장면 3~5줄).
     * 홈런, 3점 이상 나온 타석, 결승타, 도루 실패 같은 "장면"만 남긴다.
     */
    fun highlights(events: List<GameEvent>, limit: Int = DEFAULT_HIGHLIGHTS): List<String> =
        events.filter { event ->
            when (event) {
                is PlateAppearanceCompleted ->
                    event.outcome == PaOutcome.HOME_RUN || event.rbi >= BIG_HIT_RBI
                is GameEnded -> true
                else -> false
            }
        }.mapNotNull { line(it) }.takeLast(limit)

    private fun line(event: GameEvent): String? = when (event) {
        is GameStarted -> "경기 시작 — 선발 ${nameOf(event.awayStarter)} vs ${nameOf(event.homeStarter)}"

        is HalfInningStarted -> "${event.inning}회 ${if (event.half == Half.TOP) "초" else "말"} 시작"

        is PlateAppearanceCompleted -> plateAppearance(event)

        is StolenBaseAttempted -> {
            val runner = nameOf(event.runnerId)
            if (event.success) {
                "${runner}${Hangul.subject(runner)} 2루 도루 성공"
            } else {
                "${runner}${Hangul.subject(runner)} 2루 도루 실패, 아웃"
            }
        }

        is WildPitchThrown -> {
            val pitcher = nameOf(event.pitcherId)
            val scored = if (event.runs.isEmpty()) "" else " 주자 득점!"
            "${pitcher}${Hangul.subject(pitcher)} 폭투.$scored".trim()
        }

        is PitcherChanged -> "투수 교체 — ${nameOf(event.leavingPitcherId)} 내려가고 ${nameOf(event.enteringPitcherId)}"

        is PlayerSubstituted ->
            "${event.kind.label} — ${nameOf(event.leavingPlayerId)} 대신 ${nameOf(event.enteringPlayerId)}"

        is HalfInningEnded ->
            if (event.runsScored > 0) "이닝 종료 (${event.runsScored}점, 잔루 ${event.leftOnBase})" else null

        is GameEnded -> buildString {
            append("경기 종료 ${event.awayTeam} ${event.awayScore} : ${event.homeScore} ${event.homeTeam}")
            if (event.walkOff) append(" — 끝내기!")
            if (event.tie) append(" — 무승부")
            if (event.innings > REGULATION_INNINGS) append(" (${event.innings}회)")
        }

        else -> null
    }

    private fun plateAppearance(event: PlateAppearanceCompleted): String {
        val batter = nameOf(event.batterId)
        val subject = "$batter${Hangul.subject(batter)}"
        val rbi = if (event.rbi > 0) " ${event.rbi}타점" else ""
        return when (event.outcome) {
            PaOutcome.HOME_RUN -> "$subject 홈런!$rbi"
            PaOutcome.TRIPLE -> "$subject 3루타$rbi"
            PaOutcome.DOUBLE -> "$subject 2루타$rbi"
            PaOutcome.SINGLE -> "$subject 안타$rbi"
            PaOutcome.WALK -> "$subject 볼넷$rbi"
            PaOutcome.INTENTIONAL_WALK -> "$subject 고의사구"
            PaOutcome.HIT_BY_PITCH -> "$subject 몸에 맞는 공$rbi"
            PaOutcome.CATCHER_INTERFERENCE -> "포수 타격방해로 $subject 출루"
            PaOutcome.STRIKEOUT -> "$subject 삼진"
            PaOutcome.STRIKEOUT_REACHED -> "$subject 삼진, 그러나 낫아웃으로 출루"
            PaOutcome.GROUND_OUT -> "$subject 땅볼 아웃$rbi"
            PaOutcome.FLY_OUT -> "$subject 뜬공 아웃"
            PaOutcome.LINE_OUT -> "$subject 직선타 아웃"
            PaOutcome.DOUBLE_PLAY -> "$subject 병살타"
            PaOutcome.SAC_FLY -> "$subject 희생플라이$rbi"
            PaOutcome.SAC_BUNT -> "$subject 희생번트"
            PaOutcome.REACHED_ON_ERROR -> "$subject 상대 실책으로 출루"
            PaOutcome.FIELDERS_CHOICE -> "$subject 야수선택으로 출루"
        }
    }

    private companion object {
        const val DEFAULT_HIGHLIGHTS = 5
        const val BIG_HIT_RBI = 2
        const val REGULATION_INNINGS = 9
    }
}
