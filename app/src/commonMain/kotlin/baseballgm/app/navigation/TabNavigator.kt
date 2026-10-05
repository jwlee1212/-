package baseballgm.app.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import baseballgm.model.PlayerId

/**
 * 하단 탭 5개. 순서는 고정이다 (단장실 / 선수단 / 영입 / 메시지 / 리그).
 *
 * 2026-10-03 유저 요청으로 더쇼 프랜차이즈·FM 식으로 다시 묶었다 (docs/16 §3):
 * 스카우트는 영입 안으로, 뉴스·팬 반응은 리그로, 돌발 이벤트·주간 알림·결정 일지는 메시지로.
 * 그 전(2026-09-27)엔 홈 / 로스터 / 스카우트 / 시장 / 기록이었다.
 */
enum class MainTab(val label: String) {
    HOME("단장실"),
    SQUAD("선수단"),
    RECRUIT("영입"),
    MESSAGES("메시지"),
    LEAGUE("리그"),
}

/**
 * 탭 안에서 갈 수 있는 화면.
 *
 * 탭 루트([Root]) 위에 전체 화면이 쌓인다(push). 확인 창·빠른 액션은 여기 넣지 않는다 —
 * 그건 모달이라 백스택에 남지 않는다.
 */
sealed interface Route {
    /** 저장 상태(스크롤 위치 등)를 구분하는 이름. 같은 스택 안에서 겹치지 않으면 된다 */
    val key: String

    data class Root(val tab: MainTab) : Route {
        override val key: String get() = "root"
    }

    data class PlayerDetail(val playerId: PlayerId) : Route {
        override val key: String get() = "player:${playerId.value}"
    }

    /** 비FA 다년계약 협상 테이블 (2026-10-05) */
    data class ExtensionNegotiation(val playerId: PlayerId) : Route {
        override val key: String get() = "extension-talk:${playerId.value}"
    }

    /** FA 협상 테이블 (2026-10-04, FM식) */
    data class FaNegotiation(val playerId: PlayerId) : Route {
        override val key: String get() = "fa-talk:${playerId.value}"
    }

    /** 드래프트 후보 스카우트 리포트. 목록 화면 위에 쌓아야 필터·스크롤이 남는다 */
    data class Prospect(val playerId: PlayerId) : Route {
        override val key: String get() = "prospect:${playerId.value}"
    }

    /** 스카우트팀 정기 리포트. [index] 는 받은 순서 (−1 이면 가장 최근) */
    data class ScoutDigest(val index: Int = -1) : Route {
        override val key: String get() = "scout-digest:$index"
    }

    /** 우리 신인 — 드래프트 직후 뽑은 신인들의 능력치 */
    data object DraftClass : Route {
        override val key: String = "draft-class"
    }

    /** 메시지 탭의 대화 하나 (발신자별) */
    data class MessageThread(val sender: String) : Route {
        override val key: String get() = "thread:$sender"
    }

    /** 주간 결산 보고서 */
    data object WeeklyReport : Route {
        override val key: String = "weekly-report"
    }

    /** 사전 지시 */
    data object Directive : Route {
        override val key: String = "directive"
    }

    /** 문자 중계 */
    /** 문자 중계. [initial] 번째 경기를 먼저 연다 (결과 공개 화면에서 경기 카드를 눌렀을 때) */
    data class Watch(val initial: Int = 0) : Route {
        override val key: String = "watch-$initial"
    }

    /**
     * 한 주 결과 공개 (2026-10-02, 재미 개선 1번). 주간 진행이 끝나면 홈 탭 위에 쌓인다.
     * @param rankBefore 주를 시작할 때 순위 (모르면 null)
     */
    data class WeekReveal(val week: Int, val rankBefore: Int?) : Route {
        override val key: String = "week-reveal-$week"
    }

    /** 단장 커리어 */
    data object Career : Route {
        override val key: String = "career"
    }

    /** 뉴스·팬 반응·결정 일지. [tab] 0=리그 기사, 1=팬 반응, 2=결정 일지 */
    data class News(val tab: Int = 0) : Route {
        override val key: String get() = "news:$tab"
    }

    /** 선수 비교 (최대 4명). 어느 탭에서든 쌓을 수 있다 */
    data class Compare(val playerIds: List<PlayerId>) : Route {
        override val key: String get() = "compare:" + playerIds.joinToString(",") { it.value }
    }

    /** 스토브리그 준비 — 외국인 재계약·연봉 재계약·방출 명단·스태프 (2026-10-01) */
    data object OffseasonPrep : Route {
        override val key: String = "offseason-prep"
    }

    /** 시즌 시상식 (2026-10-01) — 올 시즌이면 계산, 지난 시즌이면 역사에서 */
    data class Awards(val season: Int) : Route {
        override val key: String get() = "awards:$season"
    }

    /** 포스트시즌 대진·경기 결과 */
    data object Postseason : Route {
        override val key: String = "postseason"
    }

    /** 포스트시즌 경기 문자 중계. [index] 는 포스트시즌 경기 순서 */
    data class PostseasonWatch(val index: Int) : Route {
        override val key: String get() = "postseason-watch:$index"
    }

    /**
     * 리그 허브에서 들어가는 한 구역 (2026-10-04). [section] 은 `LeagueSection` 이름.
     * @param teamScope 타자·투수 기록표를 "우리 팀"으로 열지 (선수단 탭 바로가기)
     */
    data class LeagueDetail(val section: String, val teamScope: Boolean = false) : Route {
        override val key: String get() = "league:$section:$teamScope"
    }

    /** 구단 운영 (재정·구단주·스태프) */
    data object Club : Route {
        override val key: String = "club"
    }
}

/**
 * 탭별로 독립된 백스택.
 *
 * - 탭을 바꿔도 각 탭의 스택은 그대로 남는다 (로스터에서 선수 상세를 보다가 홈에 다녀와도 선수 상세).
 * - 뒤로가기는 **지금 탭의 스택만** 줄인다. 탭 루트에서 뒤로가기는 앱 종료 확인으로 넘긴다.
 * - 이미 고른 탭을 다시 누르면 그 탭 루트로 돌아간다 (모바일 앱의 흔한 관례).
 *
 * 화면 상태 보존(스크롤 등)은 [stateKey] 로 SaveableStateHolder 에 맡긴다.
 * 스택에서 빠진 화면의 저장 상태는 [onDiscard] 로 알려서 지우게 한다.
 */
class TabNavigator(
    private val onDiscard: (stateKey: String) -> Unit = {},
) {
    var currentTab: MainTab by mutableStateOf(MainTab.HOME)
        private set

    private val stacks: Map<MainTab, SnapshotStateList<Route>> =
        MainTab.entries.associateWith { mutableStateListOf<Route>(Route.Root(it)) }

    fun stackOf(tab: MainTab): List<Route> = stacks.getValue(tab)

    val currentRoute: Route get() = stackOf(currentTab).last()

    /** 지금 탭에서 뒤로 갈 화면이 있는가. 없으면 뒤로가기 = 앱 종료 확인 */
    val canPop: Boolean get() = stackOf(currentTab).size > 1

    /** 탭 순서 + 스택 깊이 + 화면 이름. 같은 화면이 다른 탭·깊이에 있어도 상태가 섞이지 않는다 */
    fun stateKey(tab: MainTab, depth: Int, route: Route): String = "${tab.name}/$depth/${route.key}"

    fun selectTab(tab: MainTab) {
        if (tab == currentTab) popToRoot(tab) else currentTab = tab
    }

    /** 다른 탭의 루트로 보낸다 (예: 진행 버튼 → 스카우트 탭의 드래프트). 그 탭에 쌓인 화면은 걷어 낸다 */
    fun openRoot(tab: MainTab) {
        currentTab = tab
        popToRoot(tab)
    }

    /** 지금 탭 위에 화면을 쌓는다. 맨 위와 같은 화면이면 다시 쌓지 않는다 (연타 방지) */
    fun push(route: Route) {
        val stack = stacks.getValue(currentTab)
        if (stack.last() != route) stack.add(route)
    }

    /** 다른 탭으로 옮겨 가서 화면을 쌓는다 (예: 진행 버튼 → 구단 탭의 커리어) */
    fun open(tab: MainTab, route: Route) {
        currentTab = tab
        push(route)
    }

    /** 한 칸 뒤로. 탭 루트라서 못 가면 false */
    fun pop(): Boolean {
        val stack = stacks.getValue(currentTab)
        if (stack.size <= 1) return false
        val depth = stack.lastIndex
        val removed = stack.removeAt(depth)
        onDiscard(stateKey(currentTab, depth, removed))
        return true
    }

    private fun popToRoot(tab: MainTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) {
            val depth = stack.lastIndex
            onDiscard(stateKey(tab, depth, stack.removeAt(depth)))
        }
    }
}
