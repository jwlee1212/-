package baseballgm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/*
 * 절제된 움직임 (2026-10-03, 재미 개선 5번, docs/16 §12).
 *
 * 숫자가 **바뀌는 순간에만** 이전 값에서 새 값으로 부드럽게 넘어간다. 처음 그릴 때는 움직이지 않는다 —
 * 화면을 열 때마다 숫자가 0부터 올라가면 그건 장식이다.
 *
 * "이전 값"은 rememberSaveable 에 둔다. 한 주를 진행하면 홈은 결과 공개 화면에 가려졌다가 다시 그려지는데,
 * 탭 화면 상태 보관(SaveableStateProvider)이 이 값을 지켜 주므로 돌아왔을 때 "지난주 값 → 이번 주 값"으로 움직인다.
 */

/**
 * 정수 숫자 이어 붙이기: [value] 가 바뀌면 마지막으로 보여 준 값에서 [value] 까지 [Motion.countUp] 동안 올라가거나 내려간다.
 * @param key 화면 안에서 이 숫자를 구분하는 이름 (같은 화면에 여러 개면 서로 달라야 한다)
 */
@Composable
fun rememberCountUp(value: Int, key: String): Int {
    val motion = AppTheme.tokens.motion
    var last by rememberSaveable(key = key) { mutableIntStateOf(value) }
    val animated = remember(key) { Animatable(last.toFloat()) }
    LaunchedEffect(value) {
        if (animated.value.roundToInt() != value) animated.animateTo(value.toFloat(), tween(motion.countUp))
        last = value
    }
    return animated.value.roundToInt()
}

/** 소수 숫자 이어 붙이기 (승률 등). [rememberCountUp] 과 같은 규칙 */
@Composable
fun rememberCountUp(value: Float, key: String): Float {
    val motion = AppTheme.tokens.motion
    var last by rememberSaveable(key = key) { mutableFloatStateOf(value) }
    val animated = remember(key) { Animatable(last) }
    LaunchedEffect(value) {
        if (animated.value != value) animated.animateTo(value, tween(motion.countUp))
        last = value
    }
    return animated.value
}

/**
 * 처음 나타날 때 한 번만 살짝 올라오며 들어온다 (비서 후속 한마디 등). 다시 그려질 때는 움직이지 않는다.
 * [key] 가 바뀌면 새 내용으로 보고 다시 한 번 들어온다.
 */
@Composable
fun EnterOnce(key: Any?, content: @Composable () -> Unit) {
    val motion = AppTheme.tokens.motion
    val state = remember(key) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(motion.settle)) + slideInVertically(tween(motion.settle)) { it / ENTER_OFFSET_DIVISOR },
    ) { content() }
}

/** 들어올 때 높이의 몇 분의 1 아래에서 출발하나 */
private const val ENTER_OFFSET_DIVISOR = 4
