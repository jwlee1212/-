package baseballgm.app

import baseballgm.season.InboxCategory

/**
 * 메시지 탭의 발신자 (2026-10-03, 화면 개편 — FM 받은편지함을 메신저처럼).
 * 주간 알림(엔진 [baseballgm.season.Inbox])을 종류에 따라 사람에게 나눠 준다. 비서는 결정(돌발 이벤트)과 브리핑을 맡는다.
 *
 * @param initial 아바타 글자 (단색 원 + 글자, docs/16 §9 — 얼굴 없음)
 */
enum class Sender(val label: String, val initial: String) {
    SECRETARY(baseballgm.app.ui.Secretary.NAME, "백"),
    SCOUT("스카우트팀", "스"),
    MEDICAL("의료진", "의"),
    COACH("코칭스태프", "코"),
    FRONT("프런트", "프"),

    /** 선수들이 단장에게 직접 한 말 (docs/13 만족도, 2026-10-04). 말풍선 첫머리에 선수 이름이 붙는다 */
    PLAYERS("선수단", "선"),
    ;

    companion object {
        fun of(category: InboxCategory): Sender = when (category) {
            InboxCategory.INJURY, InboxCategory.RETURN -> MEDICAL
            InboxCategory.SCOUTING, InboxCategory.DRAFT -> SCOUT
            InboxCategory.ROSTER, InboxCategory.FUTURES, InboxCategory.STREAK, InboxCategory.RECORD -> COACH
            InboxCategory.DECISION -> SECRETARY
            InboxCategory.PLAYER -> PLAYERS
            InboxCategory.WARNING, InboxCategory.SCHEDULE, InboxCategory.TRADE, InboxCategory.MARKET,
            InboxCategory.NATIONAL_TEAM, InboxCategory.MILITARY -> FRONT
        }
    }
}

/**
 * 대화 안의 말풍선 하나.
 * @param lines 첫 줄이 본문, 나머지는 회색 곁줄 (결정 성적표 등)
 * @param verdict 결정 성적표 판정 (비서의 결정 기록에만)
 */
data class MessageItem(
    val sender: Sender,
    val week: Int,
    val lines: List<String>,
    val verdict: Verdict? = null,
)

/** 발신자별 대화 목록의 한 줄 */
data class MessageThreadSummary(val sender: Sender, val latest: MessageItem?, val count: Int, val fresh: Boolean)

/**
 * 메시지 모으기. 화면 없이 테스트할 수 있게 여기 둔다.
 *
 * - 비서: 지금 브리핑 한 줄 + 이번 시즌 내린 결정(돌발 이벤트)마다 "헤드라인 → 고른 답", 비서의 후속 한마디, 결정 성적표.
 *   예전 뉴스 화면의 "결정 일지"가 이 대화로 들어왔다
 * - 나머지: 엔진 알림함의 이번 시즌 알림 중 우리 팀(또는 리그 전체) 것을 종류별로 나눈다. "결정" 알림은 비서 대화와 겹쳐서 뺀다
 * 최근 것부터. 지난주·이번 주 것은 "새 소식"
 */
object Messages {

    fun thread(session: GameSession, sender: Sender): List<MessageItem> = when (sender) {
        Sender.SECRETARY -> secretary(session)
        Sender.PLAYERS -> players(session)
        else -> session.state.inbox.all()
            .filter { (it.teamId == null || it.teamId == session.userTeamId) && it.category != InboxCategory.DECISION }
            .filter { Sender.of(it.category) == sender }
            .sortedByDescending { it.week }
            .map { MessageItem(sender, it.week, listOf(it.text)) }
    }

    fun threads(session: GameSession): List<MessageThreadSummary> {
        val freshFrom = freshWeek(session)
        return Sender.entries.map { sender ->
            val items = thread(session, sender)
            MessageThreadSummary(sender, items.firstOrNull(), items.size, items.any { it.week >= freshFrom })
        }
    }

    /** 답장이 필요한 수 (메시지 탭 배지): 기다리는 돌발 이벤트 + 급한 결정할 것 */
    fun needsReply(session: GameSession): Int =
        session.pendingIncidentCount + Briefing.decisions(session).count { it.urgent && it.target != BriefingTarget.INCIDENT }

    private fun secretary(session: GameSession): List<MessageItem> {
        val now = MessageItem(Sender.SECRETARY, session.week, listOf(Briefing.headline(session)))
        val decisions = session.incidentLog()
            .filter { it.season == session.league.season }
            .map { record ->
                val review = session.decisionReviewer.review(session, record)
                MessageItem(
                    Sender.SECRETARY,
                    record.week,
                    listOfNotNull(
                        "${record.headline} → ${record.choice}" + if (record.delegated) " (비서 처리)" else "",
                        record.summary.takeIf { it.isNotBlank() },
                        review?.main,
                    ) + review?.extras.orEmpty(),
                    review?.verdict,
                )
            }
        return listOf(now) + decisions
    }

    /**
     * 선수단: 선수가 보낸 말(약속 지킴·어김, 만족도 단계 변화) + 선수가 직접 보낸 메시지 이벤트에 단장이 어떻게 답했는지.
     * 메시지 이벤트 답은 비서 대화(결정 기록)에도 남는다 — 같은 일을 선수 쪽에서 본 줄이다.
     */
    private fun players(session: GameSession): List<MessageItem> {
        val said = session.state.inbox.all()
            .filter { it.category == InboxCategory.PLAYER && it.teamId == session.userTeamId }
            .map { MessageItem(Sender.PLAYERS, it.week, listOf(it.text)) }
        val answered = session.incidentLog()
            .filter { it.season == session.league.season && it.kind.fromPlayer }
            .map { record ->
                val name = record.playerId?.let { session.nameOf(it) } ?: "선수"
                MessageItem(
                    Sender.PLAYERS,
                    record.week,
                    listOf("$name: ${record.headline.removePrefix("$name ").removePrefix("$name, ")}", "단장 답 → ${record.choice}" + if (record.delegated) " (비서 처리)" else ""),
                )
            }
        return (said + answered).sortedByDescending { it.week }
    }

    private fun freshWeek(session: GameSession): Int = (session.lastReport?.week ?: session.week)
}
