package baseballgm.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import baseballgm.scouting.PotentialGrade
import baseballgm.scouting.ScoutedPlayer

/**
 * 선수 목록 필터 (스카우트·로스터·시장 공용).
 *
 * **스카우트 시선([ScoutedPlayer])으로만 거른다.** 진짜 능력치로 거르면 "필터에 걸리는가"로
 * 숨김 값을 역추적할 수 있어서다 (불변 원칙 4). 우리 선수는 시선 자체가 정확하니 차이가 없다.
 *
 * - 종합: 추정 범위의 **가운데 값**으로 판단한다 (화면에 보이는 범위와 가장 가깝게 느껴지는 기준)
 * - 잠재력: 추정 등급 범위의 **위쪽 끝**으로 판단한다. "B~A" 는 A 필터에 걸린다 — 가능성을 찾는 필터다
 * - 계약·연봉: 계약이 있는 목록(로스터·트레이드·FA)에서만 쓴다. FA 는 희망 조건으로 거른다
 * - 나이 (2026-10-03, 트레이드 선수 고르기): 공개 정보라 스카우트 시선의 나이 그대로
 */
data class PlayerFilter(
    val position: PositionFilter = PositionFilter.ALL,
    val potential: PotentialFilter = PotentialFilter.ALL,
    val overall: OverallFilter = OverallFilter.ALL,
    val contract: ContractFilter = ContractFilter.ALL,
    val salary: SalaryFilter = SalaryFilter.ALL,
    val age: AgeFilter = AgeFilter.ALL,
) {
    val activeCount: Int
        get() = listOf(position, potential, overall, contract, salary, age).count { it.ordinal != 0 }

    /**
     * @param salary 연봉(억원). 모르면 null — 연봉 필터가 켜져 있으면 걸러진다
     * @param years 남은 계약 연수. 모르면 null
     */
    fun matches(scouted: ScoutedPlayer, salary: Double? = null, years: Int? = null): Boolean =
        position.matches(scouted.positionLabel) &&
            potential.matches(scouted.potentialHigh) &&
            overall.matches(scouted.overall.center) &&
            contract.matches(years) &&
            this.salary.matches(salary) &&
            age.matches(scouted.age)

    companion object {
        val Saver: Saver<PlayerFilter, List<Int>> = Saver(
            save = { listOf(it.position.ordinal, it.potential.ordinal, it.overall.ordinal, it.contract.ordinal, it.salary.ordinal, it.age.ordinal) },
            restore = {
                PlayerFilter(
                    PositionFilter.entries[it[0]],
                    PotentialFilter.entries[it[1]],
                    OverallFilter.entries[it[2]],
                    ContractFilter.entries[it[3]],
                    SalaryFilter.entries[it[4]],
                    // 나이는 나중에 생겨서, 예전에 저장된 필터(다섯 칸)는 "전체"로
                    AgeFilter.entries[it.getOrElse(5) { 0 }],
                )
            },
        )
    }
}

enum class PositionFilter(val label: String, private val positions: Set<String>) {
    ALL("전체", emptySet()),
    SP("SP", setOf("SP")),
    RP("RP", setOf("RP")),
    C("C", setOf("C")),
    FIRST("1B", setOf("1B")),
    SECOND("2B", setOf("2B")),
    THIRD("3B", setOf("3B")),
    SS("SS", setOf("SS")),
    OF("외야", setOf("LF", "CF", "RF")),
    DH("DH", setOf("DH")),
    ;

    fun matches(label: String): Boolean = this == ALL || label in positions
}

enum class PotentialFilter(val label: String, private val minimum: PotentialGrade?) {
    ALL("전체", null),
    C("C 이상", PotentialGrade.C),
    B("B 이상", PotentialGrade.B),
    A("A 이상", PotentialGrade.A),
    S("S", PotentialGrade.S),
    ;

    fun matches(high: PotentialGrade): Boolean = minimum == null || high >= minimum
}

enum class OverallFilter(val label: String, private val minimum: Int) {
    ALL("전체", 0),
    OVER40("40+", 40),
    OVER50("50+", 50),
    OVER60("60+", 60),
    OVER70("70+", 70),
    ;

    fun matches(value: Double): Boolean = value >= minimum
}

enum class ContractFilter(val label: String, private val range: IntRange?) {
    ALL("전체", null),
    EXPIRING("1년 이하", 0..1),
    MID("2~3년", 2..3),
    LONG("4년 이상", 4..Int.MAX_VALUE),
    ;

    fun matches(years: Int?): Boolean = range == null || (years != null && years in range)
}

enum class SalaryFilter(val label: String, private val low: Double, private val high: Double) {
    ALL("전체", 0.0, Double.MAX_VALUE),
    UNDER1("1억 미만", 0.0, 1.0),
    TO5("1~5억", 1.0, 5.0),
    TO10("5~10억", 5.0, 10.0),
    OVER10("10억 이상", 10.0, Double.MAX_VALUE),
    ;

    fun matches(salary: Double?): Boolean = this == ALL || (salary != null && salary >= low && salary < high)
}

enum class AgeFilter(val label: String, private val range: IntRange?) {
    ALL("전체", null),
    YOUNG("25세 이하", 0..25),
    PRIME("26~30세", 26..30),
    VETERAN("31세 이상", 31..Int.MAX_VALUE),
    ;

    fun matches(age: Int): Boolean = range == null || age in range
}

/** 화면별로 필터를 따로 기억한다. 다른 탭에 다녀와도 남는다 */
@Composable
fun rememberPlayerFilter(key: String? = null): MutableState<PlayerFilter> =
    rememberSaveable(key, stateSaver = PlayerFilter.Saver) { mutableStateOf(PlayerFilter()) }

/**
 * 접었다 펴는 필터 막대. 폰 화면에서 목록을 가리지 않도록 기본은 접혀 있다.
 *
 * @param showContract 계약 기간 줄을 보일지 (드래프트 풀에는 계약이 없다)
 * @param showSalary 연봉 줄을 보일지
 * @param showAge 나이 줄을 보일지 (트레이드 선수 고르기, 2026-10-03)
 * @param resultCount 걸러진 결과 수. 머리줄에 보여준다
 */
@Composable
fun PlayerFilterBar(
    filter: PlayerFilter,
    onChange: (PlayerFilter) -> Unit,
    resultCount: Int,
    modifier: Modifier = Modifier,
    showPotential: Boolean = true,
    showContract: Boolean = false,
    showSalary: Boolean = false,
    showAge: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tokens = AppTheme.tokens
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("필터", style = MaterialTheme.typography.labelLarge)
            // 글자 화살표(▲▼) 대신 앱 아이콘 세트 하나만 쓴다 (절제 규칙 "아이콘은 한 세트")
            androidx.compose.material3.Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "필터 접기" else "필터 펴기",
            )
            if (filter.activeCount > 0) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(tokens.spacing.s))
                Pill("${filter.activeCount}개 적용", AppColors.good)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(
                "${resultCount}명",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filter.activeCount > 0) {
                TextButton(onClick = { onChange(PlayerFilter()) }) { Text("초기화") }
            }
        }
        if (expanded) {
            ChipLine("포지션", PositionFilter.entries, filter.position, { it.label }) { onChange(filter.copy(position = it)) }
            if (showPotential) {
                ChipLine("잠재력", PotentialFilter.entries, filter.potential, { it.label }) { onChange(filter.copy(potential = it)) }
            }
            ChipLine("현재 종합", OverallFilter.entries, filter.overall, { it.label }) { onChange(filter.copy(overall = it)) }
            if (showAge) {
                ChipLine("나이", AgeFilter.entries, filter.age, { it.label }) { onChange(filter.copy(age = it)) }
            }
            if (showContract) {
                ChipLine("계약", ContractFilter.entries, filter.contract, { it.label }) { onChange(filter.copy(contract = it)) }
            }
            if (showSalary) {
                ChipLine("연봉", SalaryFilter.entries, filter.salary, { it.label }) { onChange(filter.copy(salary = it)) }
            }
        }
    }
}

@Composable
internal fun <T> ChipLine(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val tokens = AppTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(tokens.spacing.xl * FILTER_LABEL_UNITS),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}

/** 줄 머리(포지션·잠재력 …) 너비. spacing.xl 의 배수 */
private const val FILTER_LABEL_UNITS = 3
