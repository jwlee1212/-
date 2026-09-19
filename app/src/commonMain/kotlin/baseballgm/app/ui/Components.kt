package baseballgm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 화면 한 덩어리. 제목 + 내용. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** 작은 라벨 칩. 상태 표시(부상·폼·역할)에 쓴다. */
@Composable
fun Pill(text: String, color: Color = AppColors.muted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** 이름 = 값 한 줄. */
@Composable
fun StatRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 능력치 막대.
 *
 * 타 팀 선수는 정확한 값을 모르므로 **범위**를 띄워 보여준다 (docs/02). 막대는 범위의 시작부터
 * 끝까지 칠해지고, 우리 팀 선수는 점 하나처럼 보인다.
 */
@Composable
fun RatingBar(label: String, low: Int, high: Int, modifier: Modifier = Modifier) {
    val exact = low == high
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val start = (low.coerceIn(1, 100)) / 100f
            val end = (high.coerceIn(1, 100)) / 100f
            Box(
                Modifier
                    .fillMaxWidth(end)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.forRating(((low + high) / 2).toDouble()).copy(alpha = if (exact) 0.95f else 0.45f)),
            )
            if (!exact) {
                Box(
                    Modifier
                        .fillMaxWidth(start)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (exact) "$low" else "$low~$high",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.forRating(((low + high) / 2).toDouble()),
            modifier = Modifier.width(52.dp),
        )
    }
}

/** 0~100 짜리 게이지 (피로도 등). */
@Composable
fun MeterBar(value: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.2f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth((value.coerceIn(0, 100)) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}
