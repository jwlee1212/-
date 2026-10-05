package baseballgm.app.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SectionCard
import baseballgm.model.Attribute
import baseballgm.scouting.RatingRange
import baseballgm.scouting.SeasonRatings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 연도별 능력치 그래프 (2026-10-03, 유저 요청 "선수 상세 화면에서 연도별 능력치 변화를 그래프로").
 *
 * - 한 번에 **한 줄**만 그린다 (종합 또는 능력치 하나, 칩으로 고른다). 여러 줄을 색으로 겹치면 범례를 읽어야 해서
 * - 타 팀 선수처럼 범위로만 아는 값은 **옅은 띠(범위) + 가운데 선**으로 그린다. 우리 선수는 띠 없이 선만
 * - 점을 누르면 그 시즌 값이 위에 뜬다. 아래 표는 같은 값을 글자로 (그래프를 못 읽는 사람용)
 * 값은 전부 엔진(`ScoutingService.ratingHistory`)이 정확도만큼 흐려서 준 것이다 (불변 원칙 4).
 */
@Composable
fun RatingHistoryCard(history: List<SeasonRatings>, attributes: List<Attribute>) {
    val tokens = AppTheme.tokens
    // null = 종합
    var attribute by remember { mutableStateOf<Attribute?>(null) }
    var selected by remember(history.size) { mutableStateOf(history.lastIndex) }
    val label = attribute?.label ?: "종합"
    val points = history.map { season -> attribute?.let { season.ratings[it] } ?: season.overall }

    SectionCard("연도별 변화") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            (listOf<Attribute?>(null) + attributes).forEach { option ->
                FilterChip(
                    selected = attribute == option,
                    onClick = { attribute = option },
                    label = { Text(option?.label ?: "종합") },
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.s))

        // 고른 시즌 한 줄 (툴팁 대신 그래프 위 고정 자리 — 손가락에 가리지 않게)
        history.getOrNull(selected)?.let { season ->
            val value = points[selected]
            val previous = points.getOrNull(selected - 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${season.season}${if (season.current) " (올해)" else ""} · ${season.age}세 · $label ",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                )
                Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                previous?.let {
                    Text(" ${deltaText(it, value)}", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.xs))

        LineChart(points, selected, onSelect = { selected = it }, label = label)

        // 시즌 라벨: 점과 같은 칸 나눔(칸 가운데)이라 점 바로 아래에 온다
        Row(Modifier.fillMaxWidth().padding(start = Y_GUTTER)) {
            history.forEachIndexed { index, season ->
                val show = history.size <= MAX_X_LABELS || index % 2 == history.lastIndex % 2
                Text(
                    if (show) "'${season.season % 100}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selected) tokens.base.text else tokens.base.textMuted,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        if (history.size < 2) {
            Spacer(Modifier.height(tokens.spacing.s))
            Text(
                "시즌이 끝날 때마다 한 점씩 쌓여요. 지금은 올해 값만 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        } else if (points.any { !it.isExact }) {
            Spacer(Modifier.height(tokens.spacing.s))
            Text(
                "옅은 띠는 우리 스카우트가 보는 범위예요. 진짜 값은 그 안 어딘가예요.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textMuted,
            )
        }

        // 표: 최근 시즌부터
        if (history.size >= 2) {
            Spacer(Modifier.height(tokens.spacing.s))
            history.indices.reversed().take(MAX_TABLE_ROWS).forEach { index ->
                val season = history[index]
                Row(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
                    Text("${season.season}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(TABLE_SEASON))
                    Text("${season.age}세", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.weight(1f))
                    Text(points[index].toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        points.getOrNull(index - 1)?.let { deltaText(it, points[index]) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.base.textMuted,
                        modifier = Modifier.width(TABLE_DELTA),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
        }
    }
}

/** 전 시즌 대비 변화. 범위면 가운데끼리 비교한다 */
private fun deltaText(before: RatingRange, after: RatingRange): String {
    val delta = (after.center - before.center).roundToInt()
    return when {
        delta > 0 -> "+$delta"
        delta < 0 -> "−${abs(delta)}"
        else -> "±0"
    }
}

/**
 * 선 하나 + (범위면) 띠. 세로 축은 값이 있는 구간을 10 단위로 감싼다 (최소 폭 20).
 * 가로는 시즌마다 같은 칸, 점은 칸 가운데 — 아래 시즌 라벨과 줄이 맞는다.
 */
@Composable
private fun LineChart(points: List<RatingRange>, selected: Int, onSelect: (Int) -> Unit, label: String) {
    val tokens = AppTheme.tokens
    val measurer = rememberTextMeasurer()
    val line = tokens.base.brand
    val grid = tokens.base.textMuted.copy(alpha = GRID_ALPHA)
    val axisText: TextStyle = MaterialTheme.typography.labelSmall.copy(color = tokens.base.textMuted)
    val surface = tokens.base.card

    var bottom = floor((points.minOf { it.low } - PAD) / STEP) * STEP
    var top = ceil((points.maxOf { it.high } + PAD) / STEP) * STEP
    if (top - bottom < MIN_SPAN) top = bottom + MIN_SPAN
    bottom = bottom.coerceAtLeast(0.0)
    top = top.coerceAtMost(MAX_RATING)

    val description = "$label 연도별: " + points.joinToString(", ")
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(points.size) {
                detectTapGestures { tap ->
                    val left = Y_GUTTER.toPx()
                    val cell = (size.width - left) / points.size
                    val index = ((tap.x - left) / cell).toInt().coerceIn(0, points.lastIndex)
                    onSelect(index)
                }
            },
    ) {
        val left = Y_GUTTER.toPx()
        val cell = (size.width - left) / points.size
        fun x(index: Int) = left + cell * (index + 0.5f)
        fun y(value: Double) = (size.height * (1 - (value - bottom) / (top - bottom))).toFloat()

        // 가로 눈금선 + 왼쪽 숫자 (흐리게)
        var tick = bottom
        while (tick <= top + 0.1) {
            drawLine(grid, Offset(left, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(tick.roundToInt().toString(), axisText)
            drawText(text, topLeft = Offset(0f, (y(tick) - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)))
            tick += STEP
        }

        // 범위 띠 (타 팀 선수)
        if (points.any { !it.isExact } && points.size >= 2) {
            val band = Path().apply {
                moveTo(x(0), y(points[0].high.toDouble()))
                points.indices.drop(1).forEach { lineTo(x(it), y(points[it].high.toDouble())) }
                points.indices.reversed().forEach { lineTo(x(it), y(points[it].low.toDouble())) }
                close()
            }
            drawPath(band, line.copy(alpha = BAND_ALPHA))
        }

        // 선 (2dp)
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(x(0), y(points[0].center))
                points.indices.drop(1).forEach { lineTo(x(it), y(points[it].center)) }
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        }

        // 점: 8dp, 바탕색 테두리로 선과 떼어 보이게. 고른 점은 조금 크게
        points.forEachIndexed { index, value ->
            val center = Offset(x(index), y(value.center))
            val radius = (if (index == selected) 6.dp else 4.dp).toPx()
            drawCircle(surface, radius + 2.dp.toPx(), center)
            drawCircle(line, radius, center)
        }

        // 마지막 점에만 값 글자 (모든 점에 숫자를 달지 않는다)
        val last = points.last()
        val text = measurer.measure(last.toString(), axisText.copy(color = tokens.base.text, fontWeight = FontWeight.Bold))
        val lx = (x(points.lastIndex) - text.size.width / 2f).coerceIn(left, size.width - text.size.width)
        val ly = (y(last.high.toDouble()) - text.size.height - 4.dp.toPx()).coerceAtLeast(0f)
        drawText(text, topLeft = Offset(lx, ly))
    }
}

private val CHART_HEIGHT = 160.dp
private val Y_GUTTER = 28.dp
private val TABLE_SEASON = 56.dp
private val TABLE_DELTA = 48.dp
private const val STEP = 10.0
private const val PAD = 3
private const val MIN_SPAN = 20.0
private const val MAX_RATING = 100.0
private const val GRID_ALPHA = 0.25f
private const val BAND_ALPHA = 0.18f
private const val MAX_X_LABELS = 8
private const val MAX_TABLE_ROWS = 10
