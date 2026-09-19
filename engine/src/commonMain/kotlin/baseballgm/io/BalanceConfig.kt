package baseballgm.io

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * `config/balance.json` 을 담는 객체.
 *
 * 불변 원칙 3(밸런스 수치를 코드에 하드코딩하지 않는다)을 지키기 위한 단 하나의 입구다.
 * 엔진 코드는 숫자를 직접 쓰지 않고 항상 이 객체에서 읽는다.
 * 파일을 읽는 일(파일 시스템 접근)은 플랫폼 코드가 맡고, 여기서는 문자열만 받는다.
 */
class BalanceConfig private constructor(root: JsonObject) : JsonSection(root) {

    /**
     * 아직 현실 규정 확인이 끝나지 않은 섹션 이름들(`status: "verify"`, CLAUDE.md §8).
     * 콘솔/도구가 실행할 때 이 목록을 보여 주어, 임시값이 확정값처럼 쓰이는 일을 막는다.
     */
    fun sectionsNeedingVerification(): List<String> =
        keys.filter { sectionOrNull(it)?.status == "verify" }.sorted()

    /** 캘리브레이션으로 조정할 초안 수치 섹션들(`status: "draft"`). */
    fun draftSections(): List<String> =
        keys.filter { sectionOrNull(it)?.status == "draft" }.sorted()

    companion object {
        private val json = Json {
            allowComments = true
            allowTrailingComma = true
        }

        /** `config/balance.json` 내용을 파싱한다. 최상위가 객체가 아니면 [ConfigException]. */
        fun parse(text: String): BalanceConfig {
            val root = try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw ConfigException("balance.json 을 파싱하지 못했다: ${e.message}")
            }
            return BalanceConfig(
                root as? JsonObject ?: throw ConfigException("balance.json 의 최상위는 객체여야 한다"),
            )
        }
    }
}
