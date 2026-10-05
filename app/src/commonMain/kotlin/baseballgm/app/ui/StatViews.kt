package baseballgm.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import baseballgm.app.PercentileBar
import baseballgm.app.StatTile

/*
 * 기록 한눈에 보기 (2026-10-04, 유저 요청 "스탯을 한 눈에 보기가 어렵다").
 * 한 줄 요약 → 핵심 숫자(타일) → 리그 백분위 막대 → 상세 표 (docs/16 §3).
 */

/**
 * 핵심 숫자 타일 한 줄: 값(굵게) + "팀 내 N위" (보조 회색). 넷이 한 줄에 같은 폭으로 놓인다.
 * 숫자가 주인공이라 색은 쓰지 않는다 (절제 규칙: 색은 점에).
 */
@Composable
fun StatTileRow(tiles: List<StatTile>, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        tiles.forEach { tile ->
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "${tile.label} ${tile.value}" + (tile.teamRank?.let { ", 팀 내 ${it}위" } ?: "")
                },
            ) {
                Text(tile.label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, maxLines = 1)
                Text(tile.value, style = MaterialTheme.typography.titleMedium, color = tokens.base.text, maxLines = 1)
                Text(
                    tile.teamRank?.let { "팀 내 ${it}위" } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 리그 백분위 막대 (Baseball Savant 식). 막대 길이 = 백분위, 색 = 능력치 등급 색과 같은 구간
 * (상위 15% 보라, 25% 파랑, 40% 초록, 55% 황토, 그 아래 회색) — "얼마나 좋은가"를 능력치와 같은 눈금으로 읽는다.
 *
 * 처음 나타날 때 한 번 0 에서 차오른다 (docs/16 §12 개정). 위에서부터 [index] 순서로 조금씩 늦게.
 * 값이 바뀌면 지금 길이에서 새 길이로 이어진다. 숫자(값·상위 N%)는 글자색 — 막대 색을 글자에 쓰지 않는다.
 */
@Composable
fun PercentileBarRow(bar: PercentileBar, index: Int, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val target = bar.percentile.coerceIn(0, 100) / 100f
    val fill = remember(bar.label) { Animatable(0f) }
    LaunchedEffect(bar.label, target) {
        // 처음(0 에서)만 차례로 늦게 출발한다. 값이 바뀔 때는 바로 이어진다
        val wait = if (fill.value == 0f) index * tokens.motion.barStagger else 0
        fill.animateTo(target, tween(durationMillis = tokens.motion.barFill, delayMillis = wait))
    }
    val barHeight = 8.dp
    Row(
        modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs).clearAndSetSemantics {
            contentDescription = "${bar.label} ${bar.value}, 리그 ${bar.rankText}"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(bar.label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.width(48.dp), maxLines = 1)
        Text(
            bar.value,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(tokens.spacing.s))
        Box(Modifier.weight(1f).height(barHeight).clip(CircleShape).background(tokens.base.cardInset)) {
            if (fill.value > 0f) {
                Box(Modifier.fillMaxWidth(fill.value).height(barHeight).clip(CircleShape).background(tokens.grade.of(bar.percentile.toDouble())))
            }
        }
        Spacer(Modifier.width(tokens.spacing.s))
        Text(
            bar.rankText,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.base.text,
            fontWeight = if (bar.percentile >= 50 && bar.topShare <= TOP_HIGHLIGHT) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
            maxLines = 1,
        )
    }
}

/** 상위 몇 % 까지 굵게 보이나 (리그 최상위 지표만 눈에 띄게, 표시 기준) */
private const val TOP_HIGHLIGHT = 10

/**
 * 상세 기록 격자: 이름(회색) 위 값(본문)을 [columns] 칸씩. 세로로 긴 "이름 … 값" 줄 목록보다 한 화면에 많이 들어온다.
 */
@Composable
fun StatGrid(items: List<Pair<String, String>>, columns: Int = 4, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, maxLines = 1)
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = tokens.base.text, maxLines = 1)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 기록표 필터 막대 (2026-10-04). 선수 목록 필터([PlayerFilterBar])와 같은 모양 — 기본은 접혀 있고, 머리줄에 적용 수·결과 수·초기화.
 * 최소 표본이 "직접"이면 슬라이더가 나온다.
 */
@Composable
fun StatFilterBar(
    positions: List<String>,
    position: Int,
    onPosition: (Int) -> Unit,
    samples: List<String>,
    sample: Int,
    onSample: (Int) -> Unit,
    custom: Int,
    customMax: Int,
    customStep: Int,
    customLabel: (Int) -> String,
    onCustom: (Int) -> Unit,
    showCustom: Boolean,
    activeCount: Int,
    resultCount: Int,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val tokens = AppTheme.tokens
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("필터", style = MaterialTheme.typography.labelLarge)
            androidx.compose.material3.Icon(
                if (expanded) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "필터 접기" else "필터 펴기",
            )
            if (activeCount > 0) {
                Spacer(Modifier.width(tokens.spacing.s))
                Pill("${activeCount}개 적용", AppColors.good)
            }
            Spacer(Modifier.weight(1f))
            Text("${resultCount}명", style = MaterialTheme.typography.labelMedium, color = tokens.base.textMuted)
            if (activeCount > 0) {
                androidx.compose.material3.TextButton(onClick = onReset) { Text("초기화") }
            }
        }
        if (expanded) {
            ChipLine("포지션", positions.indices.toList(), position, { positions[it] }, onPosition)
            ChipLine("최소 표본", samples.indices.toList(), sample, { samples[it] }, onSample)
            if (showCustom && customMax > 0) {
                val steps = (customMax / customStep).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Slider(
                        value = custom.coerceIn(0, customMax).toFloat(),
                        onValueChange = { raw -> onCustom(((raw / customStep).toInt() * customStep).coerceIn(0, customMax)) },
                        valueRange = 0f..customMax.toFloat(),
                        // 슬라이더 칸이 너무 잘면 손가락으로 못 맞춘다 — 최대 30칸
                        steps = (minOf(steps, MAX_SLIDER_STEPS) - 1).coerceAtLeast(0),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(tokens.spacing.s))
                    Text(customLabel(custom), style = MaterialTheme.typography.bodySmall, color = tokens.base.text, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

private const val MAX_SLIDER_STEPS = 30
