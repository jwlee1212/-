package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import baseballgm.app.AppInfo
import baseballgm.app.GameSession
import baseballgm.app.GmName
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.Pill
import baseballgm.app.ui.Secretary
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.TeamEmblem

/**
 * 타이틀 화면 (CLAUDE.md §4-1 게임성 보완, docs/16 앱 진입 흐름).
 *
 * 세이브가 있으면 "이어하기" 카드가 먼저 보이고, 새 게임은 그 아래에 둔다. 세이브가 있는데 새 게임을
 * 고르면 지금 기록이 사라지므로 확인 창을 거친다 (되돌릴 수 없는 행동).
 */
@Composable
fun TitleScreen(resume: GameSession?, onContinue: () -> Unit, onNewGame: () -> Unit) {
    val tokens = AppTheme.tokens
    var confirmNew by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppMark(tokens.base.brand, tokens.base.onBrand)
        Spacer(Modifier.height(tokens.spacing.l))
        Text(AppInfo.TITLE, style = MaterialTheme.typography.displaySmall, color = tokens.base.text)
        Text(AppInfo.SUBTITLE, style = MaterialTheme.typography.titleMedium, color = tokens.base.textSecondary)
        Spacer(Modifier.height(AppTheme.tokens.spacing.xxl))

        if (resume != null) {
            // 카드 제목 "이어하기"는 아래 버튼과 같은 말이라 뺐다
            SectionCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamEmblem(resume.userTeamId, resume.userTeam.nickname.take(1), size = 44.dp)
                    Spacer(Modifier.width(tokens.spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text("${resume.gmName} 단장", style = MaterialTheme.typography.titleMedium)
                        // 칭호는 상태가 아니라 칩 대신 글자
                        Text(
                            "${resume.gmTitle().name} · ${resume.userTeam.name} · ${resume.league.season} " +
                                when {
                                    resume.seasonOver -> "정규시즌 종료"
                                    resume.week <= 1 -> "시즌 개막"
                                    else -> "${resume.week}주차부터"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.base.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(tokens.spacing.m))
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(tokens.radii.control),
                    // 구단 색 면은 게임 안 진행 버튼 하나뿐 — 타이틀의 주 버튼은 기본(브랜드) 색 하나
                ) { Text("이어하기", style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.height(tokens.spacing.m))
            OutlinedButton(
                onClick = { confirmNew = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(tokens.radii.control),
            ) { Text("새 게임") }
        } else {
            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(tokens.radii.control),
            ) { Text("새 게임 시작", style = MaterialTheme.typography.titleMedium) }
        }

        Spacer(Modifier.height(tokens.spacing.xl))
        Text(AppInfo.ENGLISH, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted)
    }

    if (confirmNew && resume != null) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("새로 시작할까요?") },
            text = {
                Text("지금 기록(${resume.userTeam.name}, ${resume.league.season} 시즌)은 새 게임을 시작하는 순간 지워져요. 되돌릴 수 없어요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    onNewGame()
                }) { Text("새로 시작", color = tokens.semantic.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("그만둘게요") } },
        )
    }
}

/**
 * 단장 이름 입력. 비서와의 첫 대화라 비서 카드 안에서 묻는다.
 */
@Composable
fun NameEntryScreen(onConfirm: (String) -> Unit) {
    val tokens = AppTheme.tokens
    var input by rememberSaveable { mutableStateOf("") }
    val name = GmName.normalize(input)
    val submit = { name?.let(onConfirm) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(tokens.spacing.l)) {
            SecretaryCard(
                "처음 뵙겠어요. 앞으로 매주 브리핑해 드릴 ${Secretary.NAME}예요. 단장님 성함을 어떻게 불러 드릴까요?",
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= GmName.MAX_LENGTH + 2) input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("예: 한결") },
                    supportingText = {
                        Text(
                            when {
                                input.isBlank() -> "최대 ${GmName.MAX_LENGTH}자"
                                name == null -> "${GmName.MAX_LENGTH}자까지만 쓸 수 있어요"
                                else -> "\"$name 단장님\"으로 불러 드릴게요"
                            },
                        )
                    },
                    isError = input.isNotBlank() && name == null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
            Spacer(Modifier.height(tokens.spacing.l))
            Button(
                onClick = { submit() },
                enabled = name != null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(tokens.radii.control),
            ) { Text("이 이름으로 할게요", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
