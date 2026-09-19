package baseballgm.io

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** 설정 파일을 읽다가 키가 없거나 형식이 다를 때 던진다. 메시지에 전체 경로를 남겨 디버깅을 쉽게 한다. */
class ConfigException(message: String) : IllegalArgumentException(message)

/**
 * JSON 객체 한 덩어리를 "경로 문자열"로 읽는 얇은 뷰.
 *
 * 밸런스 수치는 마일스톤마다 늘어나고 모양도 자주 바뀐다. 매번 데이터 클래스를 고치는 대신
 * `double("ratingTables.batterContactToK.50")` 처럼 경로로 읽게 해서, 설정이 늘어나도
 * 로더 코드를 고칠 필요가 없게 했다. 대신 값이 없거나 타입이 다르면 즉시 예외를 던져서
 * "조용히 기본값으로 돌아가는" 상황(밸런스가 말없이 달라지는 상황)을 막는다.
 *
 * 키 이름이 `_`로 시작하는 항목(`_note` 등)은 주석으로 보고 [keys]에서 제외한다.
 */
open class JsonSection(
    private val node: JsonObject,
    /** 예외 메시지에 붙일 상위 경로. 최상위면 빈 문자열. */
    private val basePath: String = "",
) {
    /** 주석(`_`로 시작)을 뺀 이 섹션의 키 목록. */
    val keys: Set<String>
        get() = node.keys.filterNot { it.startsWith("_") }.toSet()

    /** 이 섹션의 `status` 값(`draft` / `verify`). 없으면 null. */
    val status: String?
        get() = (node["status"] as? JsonPrimitive)?.contentOrNullIfNotString()

    fun contains(path: String): Boolean = findOrNull(path) != null

    /** 하위 객체를 같은 방식으로 읽는다. */
    fun section(path: String): JsonSection = JsonSection(objectAt(path), fullPath(path))

    /** 하위 객체가 없거나 객체가 아니면 null. */
    fun sectionOrNull(path: String): JsonSection? =
        (findOrNull(path) as? JsonObject)?.let { JsonSection(it, fullPath(path)) }

    /** 객체 배열(예: `softCap.penalties`)을 섹션 목록으로 읽는다. */
    fun sections(path: String): List<JsonSection> {
        val array = arrayAt(path)
        return array.mapIndexed { index, element ->
            JsonSection(
                element as? JsonObject ?: fail("${fullPath(path)}[$index]", "객체", element),
                "${fullPath(path)}[$index]",
            )
        }
    }

    fun int(path: String): Int =
        primitiveAt(path).intOrNull ?: fail(fullPath(path), "정수", findOrNull(path))

    fun double(path: String): Double =
        primitiveAt(path).doubleOrNull ?: fail(fullPath(path), "실수", findOrNull(path))

    fun boolean(path: String): Boolean =
        primitiveAt(path).booleanOrNull ?: fail(fullPath(path), "불린", findOrNull(path))

    fun string(path: String): String =
        primitiveAt(path).contentOrNullIfNotString() ?: fail(fullPath(path), "문자열", findOrNull(path))

    fun intOrNull(path: String): Int? = if (contains(path)) int(path) else null

    fun doubleOrNull(path: String): Double? = if (contains(path)) double(path) else null

    fun booleanOr(path: String, default: Boolean): Boolean = if (contains(path)) boolean(path) else default

    fun stringOrNull(path: String): String? = if (contains(path)) string(path) else null

    /** `[1, 2]` → `1..2`. 기간·횟수 범위에 쓴다. */
    fun intRange(path: String): IntRange {
        val values = intList(path)
        if (values.size != 2 || values[0] > values[1]) {
            fail(fullPath(path), "[최소, 최대] 정수 2개", findOrNull(path))
        }
        return values[0]..values[1]
    }

    /** `[0.26, 0.275]` → `0.26..0.275`. 목표 구간·확률 범위에 쓴다. */
    fun doubleRange(path: String): ClosedFloatingPointRange<Double> {
        val values = doubleList(path)
        if (values.size != 2 || values[0] > values[1]) {
            fail(fullPath(path), "[최소, 최대] 실수 2개", findOrNull(path))
        }
        return values[0]..values[1]
    }

    fun intList(path: String): List<Int> =
        arrayAt(path).mapIndexed { index, element ->
            (element as? JsonPrimitive)?.intOrNull ?: fail("${fullPath(path)}[$index]", "정수", element)
        }

    fun doubleList(path: String): List<Double> =
        arrayAt(path).mapIndexed { index, element ->
            (element as? JsonPrimitive)?.doubleOrNull ?: fail("${fullPath(path)}[$index]", "실수", element)
        }

    fun stringList(path: String): List<String> =
        arrayAt(path).mapIndexed { index, element ->
            (element as? JsonPrimitive)?.contentOrNullIfNotString()
                ?: fail("${fullPath(path)}[$index]", "문자열", element)
        }

    /**
     * `{"20": 0.30, "50": 0.19}` 처럼 숫자 키를 가진 객체를 읽는다.
     * 능력치 → 비율 앵커표(M2 `RatingTables`)와 단계별 수치표에 쓴다. 키 오름차순으로 정렬해 돌려준다.
     */
    fun numericMap(path: String): Map<Int, Double> {
        val obj = objectAt(path)
        val entries = obj.entries
            .filterNot { it.key.startsWith("_") || it.key == "status" }
            .map { (key, value) ->
                val rating = key.toIntOrNull() ?: fail("${fullPath(path)}.$key", "숫자 키", value)
                val number = (value as? JsonPrimitive)?.doubleOrNull
                    ?: fail("${fullPath(path)}.$key", "실수", value)
                rating to number
            }
        if (entries.isEmpty()) fail(fullPath(path), "숫자 키를 가진 비어있지 않은 객체", obj)
        return entries.sortedBy { it.first }.toMap()
    }

    /** `{"easy": {...}, "normal": {...}}` 처럼 이름이 미리 정해지지 않은 객체 묶음. */
    fun sectionMap(path: String): Map<String, JsonSection> {
        val obj = objectAt(path)
        return obj.entries
            .filterNot { it.key.startsWith("_") || it.key == "status" }
            .associate { (key, value) ->
                key to JsonSection(
                    value as? JsonObject ?: fail("${fullPath(path)}.$key", "객체", value),
                    "${fullPath(path)}.$key",
                )
            }
    }

    private fun objectAt(path: String): JsonObject =
        required(path) as? JsonObject ?: fail(fullPath(path), "객체", findOrNull(path))

    private fun arrayAt(path: String): JsonArray =
        required(path) as? JsonArray ?: fail(fullPath(path), "배열", findOrNull(path))

    private fun primitiveAt(path: String): JsonPrimitive =
        required(path) as? JsonPrimitive ?: fail(fullPath(path), "단일 값", findOrNull(path))

    private fun required(path: String): JsonElement =
        findOrNull(path) ?: throw ConfigException("설정 값이 없다: ${fullPath(path)}")

    private fun findOrNull(path: String): JsonElement? {
        var current: JsonElement = node
        for (segment in path.split('.')) {
            val obj = current as? JsonObject ?: return null
            current = obj[segment] ?: return null
        }
        return current
    }

    private fun fullPath(path: String): String = if (basePath.isEmpty()) path else "$basePath.$path"

    private fun fail(path: String, expected: String, actual: JsonElement?): Nothing =
        throw ConfigException("설정 값의 형식이 다르다: $path 에 $expected 가 와야 하는데 ${actual ?: "없음"} 이다")

    private fun JsonPrimitive.contentOrNullIfNotString(): String? = if (isString) content else null
}
