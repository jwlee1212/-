package baseballgm.io

import baseballgm.league.League
import baseballgm.model.TeamId
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 세이브 파일 한 개 (docs/14).
 *
 * - [league]: **지금 리그**. 시즌 중 세이브면 현재 선수(컨디션·소속·성장)·자금·팬심·지명권까지 담긴
 *   [baseballgm.season.SeasonState.currentLeague], 개막 세이브면 개막 리그다.
 * - [season]: 시즌 진행 스냅샷(순위·기록·엔트리 이력·스카우트·뉴스·결정 일지 …). 개막 세이브면 null.
 * - [seed]: 게임 시드. 주차별 난수는 여기서 파생해서([baseballgm.util.Seeds]) 세이브에 따로 담을 필요가 없다.
 *
 * 형식 1(개막 스냅샷만)도 그대로 읽는다 — 시드가 없으면 기본 시드로 이어 간다.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SaveGame(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val formatVersion: Int = CURRENT_VERSION,
    val userTeam: TeamId,
    val league: League,
    val seed: Long? = null,
    val season: SeasonSnapshot? = null,
    val lastReport: ReportSnapshot? = null,
) {
    companion object {
        /** 형식을 바꾸면 올린다. [READABLE_VERSIONS] 밖의 세이브는 읽지 않는다 */
        const val CURRENT_VERSION: Int = 2
        val READABLE_VERSIONS: Set<Int> = setOf(1, 2)
    }
}

/**
 * 세이브 문자열 읽기·쓰기.
 *
 * 파일 접근은 하지 않는다 — 문자열만 다루고, 어디에 저장할지는 플랫폼 코드가 정한다 (불변 원칙 1).
 * 리그 전체가 들어가 크기가 커서 들여쓰기는 하지 않는다.
 */
object SaveGameCodec {

    // 기본값(0, 빈 목록)은 쓰지 않는다 — 시즌 기록의 대부분이 0 이라 크기가 절반 아래로 준다 (폰 웹 저장 한도 5MB).
    // 읽을 때는 빠진 칸이 기본값으로 채워진다
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = false
        ignoreUnknownKeys = false
        allowStructuredMapKeys = true
    }

    fun encode(save: SaveGame): String = json.encodeToString(SaveGame.serializer(), save)

    /** 읽을 수 없거나 버전이 다르면 null. 깨진 세이브 때문에 앱이 켜지지 않는 일은 없어야 한다 */
    fun parseOrNull(text: String): SaveGame? {
        val save = try {
            json.decodeFromString(SaveGame.serializer(), text)
        } catch (e: Exception) {
            return null
        }
        return save.takeIf { it.formatVersion in SaveGame.READABLE_VERSIONS }
    }
}
