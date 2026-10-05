package baseballgm.app.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import baseballgm.app.AppInfo
import baseballgm.app.ui.AppTheme

/**
 * 스플래시.
 *
 * 세이브 읽기는 바깥([baseballgm.app.App])에서 백그라운드로 돌고, 이 화면은 그동안 로고만 보여 준다.
 * [loading] 이 끝나기 전에는 탭해도 넘어가지 않는다 — 분기할 근거(세이브 유무)가 아직 없기 때문이다.
 */
@Composable
fun SplashScreen(loading: Boolean, error: String? = null, onSkip: () -> Unit) {
    val tokens = AppTheme.tokens
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(durationMillis = 600)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(tokens.base.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !loading,
                onClick = onSkip,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppMark(tokens.base.brand, tokens.base.onBrand)
            Spacer(Modifier.height(tokens.spacing.xl))
            Text(AppInfo.TITLE, style = MaterialTheme.typography.displaySmall, color = tokens.base.text)
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(AppInfo.SUBTITLE, style = MaterialTheme.typography.titleMedium, color = tokens.base.textSecondary)
            Spacer(Modifier.height(tokens.spacing.m))
            Text(AppInfo.ENGLISH, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
        }
        Text(
            when {
                error != null -> "게임 자료를 읽지 못했어요: $error"
                loading -> "자료 챙기는 중이에요…"
                else -> "화면을 누르면 바로 시작해요"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) tokens.semantic.bad else tokens.base.textMuted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = AppTheme.tokens.spacing.xxl).alpha(fade.value),
        )
    }
}

/** 앱 로고: 브랜드 색 둥근 사각형 안에 야구공 실밥. 구단 로고와 겹치지 않게 앱 색만 쓴다 */
@Composable
internal fun AppMark(background: Color, ink: Color) {
    Box(
        Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(60.dp)) {
            val stroke = size.minDimension * 0.06f
            val radius = size.minDimension / 2
            drawCircle(ink, radius - stroke / 2, style = Stroke(stroke))

            // 실밥: 양옆에서 안쪽으로 휘는 두 호 + 바늘땀
            val inner = radius * 0.9f
            val arcSize = Size(inner * 1.3f, inner * 1.9f)
            val top = center.y - arcSize.height / 2
            drawArc(
                ink, startAngle = -60f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(center.x - inner * 1.55f, top), size = arcSize, style = Stroke(stroke * 0.8f),
            )
            drawArc(
                ink, startAngle = 120f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(center.x + inner * 1.55f - arcSize.width, top), size = arcSize, style = Stroke(stroke * 0.8f),
            )
            val stitch = inner * 0.12f
            for (i in -2..2) {
                val y = center.y + i * inner * 0.28f
                val dx = inner * 0.34f + kotlin.math.abs(i) * inner * 0.03f
                drawLine(ink, Offset(center.x - dx - stitch, y - stitch / 2), Offset(center.x - dx + stitch, y + stitch / 2), stroke * 0.6f)
                drawLine(ink, Offset(center.x + dx - stitch, y + stitch / 2), Offset(center.x + dx + stitch, y - stitch / 2), stroke * 0.6f)
            }
        }
    }
}
