package baseballgm.app.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import baseballgm.app.DecisionReview
import baseballgm.app.Verdict
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill

/*
 * 결정 성적표 표시 (2026-10-02, 재미 개선 2번). 주간 브리핑·결정 일지가 같이 쓴다.
 */

/**
 * 성적표 한 줄: 제목(종류 · 고른 답) + 판정 칩 / 근거 숫자 / 곁들이는 숫자.
 * 판정은 칩 글자로도 말한다 — 색만으로 구분하지 않는다.
 */
@Composable
internal fun DecisionReviewRow(review: DecisionReview, showTitle: Boolean = true) {
    val tokens = AppTheme.tokens
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showTitle) {
                Text(
                    review.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.base.text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(tokens.spacing.s))
            }
            Pill(review.verdict.label, verdictColor(review.verdict))
        }
        Text(review.main, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary)
        review.extras.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
        }
    }
}

@Composable
internal fun verdictColor(verdict: Verdict) = when (verdict) {
    Verdict.WORKED -> AppColors.good
    Verdict.MISSED -> AppColors.bad
    Verdict.MIXED -> AppColors.warn
    Verdict.TOO_EARLY -> AppColors.muted
}
