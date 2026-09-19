package baseballgm.io

import baseballgm.league.League
import kotlinx.serialization.json.Json

/**
 * `data/league_2026.json` 읽기·쓰기.
 *
 * 파일 접근은 하지 않는다. 문자열만 다루고, 파일을 읽어오는 일은 플랫폼 코드가 한다 (불변 원칙 1).
 * 저장된 데이터는 손으로 고칠 수 있어야 해서(docs/03) 들여쓰기해서 저장한다.
 */
object LeagueLoader {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminator = "type"
        encodeDefaults = true
        allowComments = true
        allowTrailingComma = true
    }

    fun parse(text: String): League = try {
        json.decodeFromString(League.serializer(), text)
    } catch (e: Exception) {
        throw ConfigException("league json 을 파싱하지 못했다: ${e.message}")
    }

    fun encode(league: League): String = json.encodeToString(League.serializer(), league)
}
