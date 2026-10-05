package baseballgm.app

import baseballgm.io.BalanceConfig
import baseballgm.io.SaveGame
import baseballgm.io.SaveGameCodec
import baseballgm.league.League
import baseballgm.market.ForeignSupplier
import baseballgm.market.ProspectSupplier
import baseballgm.model.TeamId
import baseballgm.season.RookieSupplier

/**
 * 팀 선택 화면이 보여 주는 구단 한 개 (data/teams.json 에서 온다).
 *
 * 리그를 만들기 **전에** 고르는 화면이라 실제 선수단 전력이 아니라 생성 목표치를 보여 준다.
 * 생성기는 목표 ±허용 오차 안으로 선수단을 맞추므로(docs/03) 선택 판단에는 충분하다.
 */
data class TeamChoice(
    val id: TeamId,
    val name: String,
    /** 애칭 (나이츠, 시걸스…). 엠블럼 글자로 쓴다 */
    val nickname: String,
    val keyword: String,
    /** strong / mid / weak */
    val tier: String,
    val recommendedForTutorial: Boolean,
    /** teams.json 의 difficulty 가 "hardest" 인 구단 */
    val hardest: Boolean,
    val lineup: Int,
    val rotation: Int,
    val bullpen: Int,
    val overall: Int,
    val payroll: Int,
    val operatingFunds: Int,
    val draftPick: Int,
    val ownerGoal: String,
    /** 구단 색 `#RRGGBB` — 라이트 / 다크 변형 (docs/16 §6-3) */
    val color: String,
    val colorDark: String,
)

/**
 * 세이브 저장소. 어디에(파일·앱 저장 공간) 둘지는 플랫폼이 정한다.
 *
 * 둘 다 **블로킹 호출**이다. 화면은 항상 백그라운드 스레드에서 부른다.
 */
interface SaveStore {
    /** 세이브가 없으면 null */
    fun read(): String?

    fun write(text: String)
}

/**
 * 플랫폼(데스크톱·Android·iOS)이 앱에 넘겨주는 것 전부.
 *
 * 파일 읽기, 신인 생성처럼 엔진·화면이 직접 못 하는 일은 여기 함수로 받는다 (불변 원칙 1).
 */
class GameHost(
    val balance: BalanceConfig,
    val teams: List<TeamChoice>,
    val saveStore: SaveStore,
    /** 고정 리그(docs/03)를 읽는다. 웹·iOS 는 앱 리소스에서, 데스크톱은 프로젝트 파일에서 */
    val loadStartingLeague: suspend () -> League,
    val rookieSupplier: (League) -> RookieSupplier,
    val prospectSupplier: (League) -> ProspectSupplier,
    val foreignSupplier: (League) -> ForeignSupplier,
    /**
     * 앱 종료. 탭 루트에서 뒤로가기 → 확인 창 → 여기.
     * null 이면 앱이 스스로 끝날 수 없는 플랫폼(웹 탭, iOS)이라 종료 확인 창을 띄우지 않는다.
     */
    val exitApp: (() -> Unit)?,
) {
    fun newSession(league: League, userTeam: TeamId, gmName: String? = null): GameSession = GameSession(
        balance = balance,
        startingLeague = league,
        userTeamId = userTeam,
        gmName = gmName,
        rookieSupplier = rookieSupplier,
        prospectSupplier = prospectSupplier,
        foreignSupplier = foreignSupplier,
    )

    /** 세이브에서 이어 한다. 시즌 중 세이브면 그 주 시작으로, 개막 세이브(형식 1)면 개막으로 */
    fun resume(save: SaveGame): GameSession = GameSession(
        balance = balance,
        startingLeague = save.league,
        userTeamId = save.userTeam,
        rookieSupplier = rookieSupplier,
        prospectSupplier = prospectSupplier,
        foreignSupplier = foreignSupplier,
        seed = save.seed ?: DEFAULT_SEED,
        savedSeason = save.season,
        savedReport = save.lastReport,
    )

    /**
     * 저장한다 (docs/14). 저장할 수 없는 순간(주중 멈춤·드래프트 도중·스토브리그·무직)이면 아무것도 하지 않는다 —
     * 직전 세이브가 남는다. 저장했으면 true.
     */
    fun save(session: GameSession): Boolean {
        val data = session.saveData() ?: return false
        saveStore.write(SaveGameCodec.encode(data))
        return true
    }

    private companion object {
        /** 형식 1 세이브(시드 없음)를 이어 할 때 쓰는 시드 — GameSession 기본값과 같다 */
        const val DEFAULT_SEED = 20260401L
    }
}

/**
 * 앱 시작 시 세이브 유무 판단.
 *
 * Loading → (HasSave | NoSave). 스플래시는 Loading 동안 떠 있고,
 * 결과에 따라 HasSave → 메인 탭 셸, NoSave → 새 게임 흐름으로 간다.
 */
sealed interface LaunchState {
    data object Loading : LaunchState

    data class HasSave(val save: SaveGame) : LaunchState

    data object NoSave : LaunchState

    companion object {
        /** 세이브를 못 읽으면(깨짐·옛 버전) 새 게임으로 간다. 새 게임을 시작하면 그 파일은 덮어쓴다 */
        fun from(saveText: String?): LaunchState =
            saveText?.let(SaveGameCodec::parseOrNull)?.let(::HasSave) ?: NoSave
    }
}

/**
 * 앱이 보여 주는 큰 단계.
 *
 * ```
 * Splash → Title ─┬─ 이어하기 → Main
 *                 └─ 새 게임 → NameEntry → SelectTeam → Building → Main(첫 인사)
 * ```
 */
sealed interface AppScreen {
    data class Splash(val launch: LaunchState) : AppScreen

    /** 타이틀. [resume] 이 있으면 이어하기 카드를 보여 준다 (CLAUDE.md §4-1 게임성 보완) */
    data class Title(val resume: GameSession?) : AppScreen

    /** 새 게임 ① 단장 이름. 비서가 이후 이 이름으로 부른다 */
    data object NameEntry : AppScreen

    /** 새 게임 ② 팀 선택 (확인 창은 이 화면 안의 모달) */
    data class SelectTeam(val gmName: String) : AppScreen

    /** 새 게임 ③ 리그 구성 중. 실패하면 [error] 에 이유가 들어간다 */
    data class Building(val team: TeamChoice, val gmName: String, val error: String? = null) : AppScreen

    /** 메인 탭 셸. [welcome] 이면 홈에 부임 인사 메모를 한 번 띄운다 */
    data class Main(val session: GameSession, val welcome: Boolean) : AppScreen
}

/** 단장 이름 규칙. 화면 밖에서 테스트할 수 있게 따로 둔다 */
object GmName {
    const val MAX_LENGTH: Int = 8

    /** 앞뒤 공백을 떼고 가운데 공백은 하나로. 비었거나 너무 길면 null */
    fun normalize(input: String): String? {
        val cleaned = input.trim().replace(Regex("\\s+"), " ")
        return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
    }
}
