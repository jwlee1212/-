package baseballgm.events

import baseballgm.model.PlayerId
import baseballgm.model.TeamId
import baseballgm.tactics.WeeklyPolicy

/**
 * 돌발 이벤트 종류 (docs/07·16 주중 개입).
 *
 * @param urgent 자동 진행 중에도 반드시 멈추고 묻는 일. 나머지는 자동 진행 중이면 비서가 추천대로 처리한다
 */
@kotlinx.serialization.Serializable
enum class IncidentKind(
    val label: String,
    val urgent: Boolean,
    /** 기회형 (2026-10-03, 재미 개선 3번): 문제가 아니라 좋은 상황·시장 타이밍에서 온다. 넘기는 게 안전한 기본값 */
    val opportunity: Boolean = false,
    /**
     * 계약·선수 이동이 걸린 결정 (2026-10-03): 트레이드·영입·연장 계약.
     * 자동 진행(올스타까지·시즌 끝까지)은 이것에만 멈추고, 부상 대체·콜업 같은 나머지 엔트리 조정은 비서가 처리한다.
     */
    val contract: Boolean = false,
    /**
     * 선수가 단장에게 직접 보낸 메시지 (2026-10-04, docs/13 만족도). 카드의 발신자가 비서가 아니라 [Incident.playerId] 선수이고,
     * [Incident.message] 는 그 선수의 1인칭 말이다.
     */
    val fromPlayer: Boolean = false,
) {
    INJURY_REPLACEMENT("주전 부상", true),
    PLAY_THROUGH("부상 투혼", true),
    /** 주전이 부상·재활을 마치고 돌아올 준비가 됐다 (2026-10-05). 주 시작에 묻고, 자동 진행이면 비서가 바로 올린다 */
    INJURY_RETURN("부상 복귀", false),
    LOSING_STREAK("연패", true),
    MANAGER_HEAT("감독 거취", true),
    TRADE_OFFER("트레이드 제안", true, contract = true),
    CONTROVERSY("SNS 논란", false),
    MEDIA("기자 질문", false),
    FATIGUE("피로 누적", false),
    HOT_PROSPECT("2군 폭격", false),
    PLAYING_TIME("출장 요구", false, fromPlayer = true),
    RIVAL_WEEK("라이벌 주간", false),
    TRADE_INQUIRY("트레이드 문의", false, opportunity = true, contract = true),
    RELEASED_VETERAN("방출 매물", false, opportunity = true, contract = true),
    PROSPECT_TRIAL("유망주 시험", false, opportunity = true),
    EXTENSION("연장 계약", false, opportunity = true, contract = true),

    // ---------- 선수가 보내는 메시지 (2026-10-04, docs/13 선수 성향과 만족도) ----------
    TRADE_REQUEST("이적 요청", false, fromPlayer = true),
    SALARY_COMPLAINT("연봉 불만", false, fromPlayer = true),
    MENTOR("멘토링 자청", false, fromPlayer = true),
}

/**
 * 선택지 하나가 실제로 하는 일. 엔진이 [baseballgm.season.IncidentResolver] 로 적용한다.
 * 화면은 이걸 읽지 않고 [IncidentOption.detail] 문장만 보여 준다.
 */
sealed interface IncidentEffect {
    /** 1군 등록 (자리가 없으면 [swapOut] 을 내린다) */
    data class Promote(val playerId: PlayerId, val swapOut: PlayerId? = null) : IncidentEffect

    /** 1군 말소 (징계 등). 재등록 제한이 걸린다 */
    data class Demote(val playerId: PlayerId) : IncidentEffect

    /** 부상자 말소 + 재활 복귀 주차 기록 */
    data class InjuredList(val playerId: PlayerId) : IncidentEffect

    /**
     * 부상 복귀를 한 주 미룬다 (2026-10-05): 2군에서 한 주 더 경기 감각을 찾는다. 재발 위험 기간이 [relapseCut] 주 줄고,
     * 다음 주 시작에는 묻지 않고 자동으로 올라온다
     */
    data class DelayReturn(val playerId: PlayerId, val relapseCut: Int) : IncidentEffect

    /** 경미한 부상을 참고 뛴다: 부상이 지워지고 폼이 떨어지고 재발 위험이 길어진다 */
    data class PlayThrough(val playerId: PlayerId) : IncidentEffect

    /** 이번 주 남은 경기 휴식 */
    data class Rest(val playerId: PlayerId) : IncidentEffect

    data class PlayerForm(val playerId: PlayerId, val delta: Int) : IncidentEffect

    /** 1군 전원 폼 */
    data class TeamForm(val delta: Int) : IncidentEffect

    /** 1군 전원 피로 */
    data class TeamFatigue(val delta: Int) : IncidentEffect

    data class Fan(val delta: Int) : IncidentEffect

    data class OwnerTrust(val delta: Int) : IncidentEffect

    /** 이번 주만 방침을 바꾼다. 주가 끝나면 원래대로 */
    data class PolicyThisWeek(val policy: WeeklyPolicy) : IncidentEffect

    data object AcceptTrade : IncidentEffect

    data object RejectTrade : IncidentEffect

    // ---------- 기회형 (2026-10-03) ----------

    /** 이 거래를 바로 성사시킨다 (트레이드 문의). 그사이 선수가 떠났거나 마감이 지났으면 아무 일도 없다 */
    data class ExecuteTrade(val proposal: baseballgm.market.TradeProposal) : IncidentEffect

    /** 다른 팀 선수를 계약 그대로 데려온다 (방출 매물). 2군으로 합류 */
    data class AcquirePlayer(val playerId: PlayerId) : IncidentEffect

    /**
     * 능력치를 조금 올린다: 잠재력과 차이가 큰 능력치부터 (잠재력은 넘지 않는다).
     * 유망주 1군 시험은 `incidents.opportunity.prospectTrial` 값, 멘토링은 [amount]·[attributes] 를 직접 준다
     */
    data class RatingBoost(val playerId: PlayerId, val amount: Int? = null, val attributes: Int? = null) : IncidentEffect

    /** 연장 계약: 지금부터 [salary], 이번 시즌 + [years] 년 */
    data class ExtendContract(val playerId: PlayerId, val salary: Double, val years: Int) : IncidentEffect

    // ---------- 만족도 (2026-10-04, docs/13) ----------

    /** 선수에게 기억을 남긴다 (만족도 요인). [key] 는 balance.json `morale.memories` 의 키 */
    data class Memory(
        val playerId: PlayerId,
        val slot: baseballgm.management.MemorySlot,
        val key: String,
        val label: String,
    ) : IncidentEffect

    /** 선수에게 약속한다. [deadlineWeek] 주차가 끝날 때까지 지켜야 한다 */
    data class Promise(val playerId: PlayerId, val kind: baseballgm.management.PromiseKind, val deadlineWeek: Int) : IncidentEffect

    /** 이적 요청을 받아들여 트레이드 시장에 내놓는다 (트레이드 문의가 이 선수로 먼저 온다) */
    data class TransferList(val playerId: PlayerId) : IncidentEffect
}

/** 팬이 이 결정에 보일 반응. 주말 팬 SNS 에 섞인다 */
@kotlinx.serialization.Serializable
data class FanReaction(val mood: FanMood, val text: String)

/**
 * 선택지.
 * @param detail 고르면 무슨 일이 생기는지 — 숫자를 감추지 않고 비서가 미리 말해 준다
 * @param quote 언론에 나갈 단장 발언 (있으면 뉴스가 된다)
 */
data class IncidentOption(
    val id: String,
    val label: String,
    val detail: String,
    val recommended: Boolean = false,
    val effects: List<IncidentEffect> = emptyList(),
    val quote: String? = null,
    val reactions: List<FanReaction> = emptyList(),
    /** 고른 뒤 비서가 하는 말 */
    val followUp: String = "",
)

/**
 * 돌발 이벤트 한 건.
 * @param day 0=화 … 5=일. null 이면 주 시작(경기 전)
 */
data class Incident(
    val id: String,
    val season: Int,
    val week: Int,
    val day: Int?,
    val teamId: TeamId,
    val kind: IncidentKind,
    val headline: String,
    val message: String,
    val options: List<IncidentOption>,
    val playerId: PlayerId? = null,
) {
    val recommended: IncidentOption get() = options.firstOrNull { it.recommended } ?: options.last()

    fun option(id: String): IncidentOption? = options.firstOrNull { it.id == id }

    /** "수요일 경기 뒤" / "주 시작" */
    val whenLabel: String get() = day?.let { "${DAY_NAMES.getOrElse(it) { "?" }}요일 경기 뒤" } ?: "주 시작"

    companion object {
        val DAY_NAMES = listOf("화", "수", "목", "금", "토", "일")
    }
}

/**
 * 결정한 순간의 기준값 (2026-10-02, 재미 개선 2번 "결정 성적표").
 * 지금 값에서 이 값을 빼면 "결정한 뒤로 무엇이 바뀌었나"가 나온다. 효과를 적용하기 **전**에 찍는다.
 */
@kotlinx.serialization.Serializable
data class DecisionBaseline(
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val runsScored: Int,
    val runsAllowed: Int,
    val fanSupport: Int,
    val ownerTrust: Int,
    /** 결정이 건드린 선수들. 역할([PlayerBaseline.role])로 주인공·받은 선수·보낸 선수를 나눈다 */
    val players: List<PlayerBaseline> = emptyList(),
    /** 고른 선택이 건드린 것 (2026-10-03). 성적표는 여기 있는 것만 보여 준다 — 팀 전체 성적을 일괄로 보여 주지 않는다 */
    val focus: List<DecisionFocus> = emptyList(),
    /** 결정 순간 연승(+)·연패(−) */
    val streak: Int = 0,
    /** 결정 순간 1군 평균 폼·피로 (팀 폼·팀 피로를 건드리는 결정용) */
    val teamForm: Int = 0,
    val teamFatigue: Int = 0,
    /** 라이벌과 결정 순간 상대 전적 (이번 주 방침 결정용) */
    val rival: TeamId? = null,
    val rivalWins: Int = 0,
    val rivalLosses: Int = 0,
)

/** 고른 선택이 건드린 것 */
@kotlinx.serialization.Serializable
enum class DecisionFocus {
    /** 선수 한 명(콜업·휴식·부상 투혼·폼·영입·유망주 시험) */
    PLAYER,
    TRADE_ACCEPTED,
    TRADE_DECLINED,
    CONTRACT,
    TEAM_FORM,
    TEAM_FATIGUE,
    FAN,
    OWNER_TRUST,
    POLICY,
    /** 아무것도 하지 않는 선택(더 지켜본다·그냥 쓴다·넘긴다) — 이벤트 대상 선수를 본다 */
    NOTHING,
}

/** 성적표에서 선수의 역할 */
@kotlinx.serialization.Serializable
enum class BaselineRole { SUBJECT, ACQUIRED, DEPARTED, KEPT, MISSED }

/** 결정 순간의 선수 기록 (1·2군 시즌 누적) */
@kotlinx.serialization.Serializable
data class PlayerBaseline(
    val playerId: PlayerId,
    val batting: baseballgm.stats.BattingLine,
    val pitching: baseballgm.stats.PitchingLine,
    val fatigue: Int,
    val injured: Boolean,
    val role: BaselineRole = BaselineRole.SUBJECT,
    val futuresBatting: baseballgm.stats.BattingLine = baseballgm.stats.BattingLine.EMPTY,
    val futuresPitching: baseballgm.stats.PitchingLine = baseballgm.stats.PitchingLine.EMPTY,
    /** 능력치 평균 (우리 선수만 — 유망주 시험의 "종합 52 → 54"). 타 팀 선수는 -1 (진짜 값을 남기지 않는다) */
    val overall: Int = -1,
    val salary: Double = 0.0,
    val yearsRemaining: Int = 0,
)

/** 답한 돌발 이벤트 */
@kotlinx.serialization.Serializable
data class IncidentRecord(
    val season: Int,
    val week: Int,
    val day: Int?,
    val kind: IncidentKind,
    val headline: String,
    val choice: String,
    val summary: String,
    /** 비서에게 맡겼는가 (자동 진행 포함) */
    val delegated: Boolean,
    val reactions: List<FanReaction> = emptyList(),
    val playerId: PlayerId? = null,
    /** 결정 성적표 기준값. 이 기능 전(2026-10-02)의 기록·세이브에는 없다 */
    val baseline: DecisionBaseline? = null,
)
