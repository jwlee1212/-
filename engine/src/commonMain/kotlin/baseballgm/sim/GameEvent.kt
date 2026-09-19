package baseballgm.sim

import baseballgm.model.Hand
import baseballgm.model.PlayerId
import baseballgm.model.TeamId

/** 초/말. */
enum class Half { TOP, BOTTOM }

/** 타구 종류 (docs/04 ③). */
enum class BattedBallType { GROUND, FLY, LINE }

/**
 * 타석 결과 (docs/04 마지막 목록).
 *
 * @param isAtBat 타수에 포함되는가 (볼넷·사구·희생타·타격방해는 제외)
 * @param batterReaches 타자가 살아 나갔는가 (주자 보존 등식에 쓰인다)
 * @param bases 타자가 간 베이스 수
 */
enum class PaOutcome(
    val isAtBat: Boolean,
    val isHit: Boolean,
    val batterReaches: Boolean,
    val bases: Int = 0,
) {
    SINGLE(true, true, true, 1),
    DOUBLE(true, true, true, 2),
    TRIPLE(true, true, true, 3),
    HOME_RUN(true, true, true, 4),
    WALK(false, false, true, 1),
    INTENTIONAL_WALK(false, false, true, 1),
    HIT_BY_PITCH(false, false, true, 1),
    CATCHER_INTERFERENCE(false, false, true, 1),
    STRIKEOUT(true, false, false),

    /** 낫아웃 출루. 투수에게는 삼진, 타자에게는 출루. */
    STRIKEOUT_REACHED(true, false, true, 1),
    GROUND_OUT(true, false, false),
    FLY_OUT(true, false, false),
    LINE_OUT(true, false, false),
    DOUBLE_PLAY(true, false, false),
    SAC_FLY(false, false, false),
    SAC_BUNT(false, false, false),
    REACHED_ON_ERROR(true, false, true, 1),
    FIELDERS_CHOICE(true, false, true, 1),
    ;

    val isStrikeout: Boolean get() = this == STRIKEOUT || this == STRIKEOUT_REACHED
    val isWalk: Boolean get() = this == WALK || this == INTENTIONAL_WALK
}

/**
 * 득점 하나.
 *
 * @param responsiblePitcherId 이 주자를 내보낸 투수(책임 투수). 다음 투수 때 들어와도 앞 투수의 실점이다
 * @param earned 자책점인가 (실책으로 나간 주자, 가상 아웃 3개 이후의 득점은 비자책)
 */
data class ScoredRun(
    val runnerId: PlayerId,
    val responsiblePitcherId: PlayerId,
    val earned: Boolean,
)

/**
 * 경기 이벤트 스트림 (docs/05).
 *
 * 시뮬레이터는 항상 같은 스트림을 만들고, 기록(StatsRecorder)·검증(BoxScoreValidator)·
 * 문자 중계가 그것을 **읽기만** 한다. 관전용 시뮬레이션을 따로 두지 않는다 (불변 원칙 5).
 */
sealed interface GameEvent

data class GameStarted(
    val homeTeam: TeamId,
    val awayTeam: TeamId,
    val homeStarter: PlayerId,
    val awayStarter: PlayerId,
) : GameEvent

data class HalfInningStarted(
    val inning: Int,
    val half: Half,
    val battingTeam: TeamId,
) : GameEvent

data class PlateAppearanceCompleted(
    val inning: Int,
    val half: Half,
    val battingTeam: TeamId,
    val fieldingTeam: TeamId,
    val batterId: PlayerId,
    val batterHand: Hand,
    val pitcherId: PlayerId,
    val pitcherHand: Hand,
    val outcome: PaOutcome,
    val battedBall: BattedBallType?,
    val pitches: Int,
    val rbi: Int,
    /** 이 타석에서 잡은 아웃 수 (타자 아웃 + 주자 아웃) */
    val outsRecorded: Int,
    /** 그중 베이스에서 아웃된 주자 수 (병살의 선행 주자, 야수선택 등) */
    val runnersOutOnBase: Int,
    val runs: List<ScoredRun>,
    val errorBy: PlayerId?,
    val outsBefore: Int,
    val basesBefore: Int,
) : GameEvent

data class StolenBaseAttempted(
    val inning: Int,
    val half: Half,
    val battingTeam: TeamId,
    val runnerId: PlayerId,
    val pitcherId: PlayerId,
    val catcherId: PlayerId,
    val targetBase: Int,
    val success: Boolean,
) : GameEvent

data class WildPitchThrown(
    val inning: Int,
    val half: Half,
    val fieldingTeam: TeamId,
    val pitcherId: PlayerId,
    val runs: List<ScoredRun>,
) : GameEvent

data class PitcherChanged(
    val inning: Int,
    val half: Half,
    val teamId: TeamId,
    val leavingPitcherId: PlayerId,
    val enteringPitcherId: PlayerId,
) : GameEvent

data class HalfInningEnded(
    val inning: Int,
    val half: Half,
    val battingTeam: TeamId,
    val runsScored: Int,
    val leftOnBase: Int,
    val outsRecorded: Int,
) : GameEvent

data class GameEnded(
    val homeTeam: TeamId,
    val awayTeam: TeamId,
    val homeScore: Int,
    val awayScore: Int,
    val innings: Int,
    val walkOff: Boolean,
    val tie: Boolean,
) : GameEvent

/** 승·패·세이브·홀드 판정 결과 (docs/05 투수 기록 판정). */
data class PitcherDecisionsDecided(
    val winningPitcher: PlayerId?,
    val losingPitcher: PlayerId?,
    val savePitcher: PlayerId?,
    val holdPitchers: List<PlayerId>,
) : GameEvent

/** 선수 교체 종류 (docs/06 ⑤). */
enum class SubstitutionKind(val label: String) {
    PINCH_HITTER("대타"),
    PINCH_RUNNER("대주자"),
    DEFENSIVE("대수비"),
}

data class PlayerSubstituted(
    val inning: Int,
    val half: Half,
    val teamId: TeamId,
    val leavingPlayerId: PlayerId,
    val enteringPlayerId: PlayerId,
    val kind: SubstitutionKind,
) : GameEvent

/**
 * 규칙표대로 던질 투수가 없어서 제한을 무시하고 올린 경우 (docs/06 예외 처리).
 * 경기는 절대 멈추지 않지만, 무리한 등판이었다는 사실은 남긴다 (M4 에서 부상 위험에 반영).
 */
data class PitcherForcedIn(
    val inning: Int,
    val half: Half,
    val teamId: TeamId,
    val pitcherId: PlayerId,
) : GameEvent
