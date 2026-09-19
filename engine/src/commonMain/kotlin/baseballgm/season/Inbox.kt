package baseballgm.season

import baseballgm.model.TeamId

/** 알림 종류 (docs/07 주 시작 알림함). */
enum class InboxCategory(val label: String) {
    INJURY("부상"),
    RETURN("복귀"),
    ROSTER("엔트리"),
    FUTURES("2군"),
    STREAK("연승·연패"),
    RECORD("기록"),
    WARNING("경고"),
    SCHEDULE("일정"),
}

data class InboxMessage(
    val week: Int,
    val category: InboxCategory,
    val teamId: TeamId?,
    val text: String,
)

/**
 * 알림함 (docs/07).
 *
 * 주 시작 결정 단계에서 보여 줄 소식을 모은다. **알림이 없으면 바로 "진행" 할 수 있어야 하므로**
 * 정말 결정거리가 되는 것만 넣는다.
 */
class Inbox {
    private val messages = mutableListOf<InboxMessage>()

    fun add(week: Int, category: InboxCategory, teamId: TeamId?, text: String) {
        messages += InboxMessage(week, category, teamId, text)
    }

    fun ofWeek(week: Int, teamId: TeamId? = null): List<InboxMessage> =
        messages.filter { it.week == week && (teamId == null || it.teamId == teamId) }

    fun all(): List<InboxMessage> = messages.toList()

    fun clear() = messages.clear()
}
