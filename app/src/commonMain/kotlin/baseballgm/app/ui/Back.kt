package baseballgm.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * 시스템 뒤로가기를 받는다.
 *
 * Android 뒤로 버튼, iOS 가장자리 스와이프, 데스크톱 Esc 키가 모두 같은 이벤트로 들어온다
 * (Compose Multiplatform 의 navigationevent). 가장 안쪽(나중에 그려진) 핸들러가 먼저 받는다.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBack)
}

/** 탭 루트·새 게임 첫 화면에서 뒤로가기를 누르면 뜨는 종료 확인 창. */
@Composable
fun ExitConfirmDialog(onExit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘은 여기까지 할까요?") },
        text = { Text("저장은 가장 최근 시즌 개막 시점까지 돼 있어요.") },
        confirmButton = { TextButton(onClick = onExit) { Text("종료할게요") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("조금 더 할게요") } },
    )
}
