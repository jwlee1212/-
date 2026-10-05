package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import baseballgm.app.TeamChoice
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.TeamEmblem

/**
 * 새 게임 ② 리그 구성 중.
 *
 * 고정 리그를 읽고 시즌 상태(일정·엔트리·스카우트 부서)를 만드는 동안 떠 있는다.
 * 실패하면 이유와 함께 다시 시도 / 구단 다시 고르기를 보여 준다.
 */
@Composable
fun LeagueBuildingScreen(
    team: TeamChoice,
    stage: String,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = AppTheme.tokens
    val color = tokens.teamColor(team.id)
    Box(Modifier.fillMaxSize().background(tokens.base.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TeamEmblem(team.id, team.nickname.take(1), size = 64.dp)
            Spacer(Modifier.height(tokens.spacing.m))
            Text(team.name, style = MaterialTheme.typography.headlineSmall, color = tokens.base.text)
            Text("신임 단장 부임", style = MaterialTheme.typography.bodyMedium, color = tokens.base.textMuted)
            Spacer(Modifier.height(tokens.spacing.xl))

            if (error == null) {
                SecretaryCard(stage.ifEmpty { "단장님 자리 정리하고 있어요." }) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        color = color,
                        trackColor = color.copy(alpha = 0.18f),
                    )
                }
            } else {
                SecretaryCard("리그 자료를 여는 데 문제가 생겼어요. 한 번 더 해 볼까요?") {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.base.textMuted,
                        textAlign = TextAlign.Start,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.l))
                OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                TextButton(onClick = onBack) { Text("구단 다시 고르기") }
            }
        }
    }
}
