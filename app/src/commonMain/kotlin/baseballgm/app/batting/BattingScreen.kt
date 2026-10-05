package baseballgm.app.batting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import baseballgm.app.ui.ColorTokens
import baseballgm.app.ui.T
import baseballgm.batting.PitchType
import baseballgm.sim.BattedBallType
import baseballgm.sim.PaOutcome
import androidx.compose.ui.graphics.drawscope.translate
import baseballgm.batting.ContactQuality
import baseballgm.batting.Pitch
import baseballgm.batting.PitchCall
import baseballgm.app.audio.SilentPlayer
import baseballgm.app.audio.SoundPlayer
import androidx.compose.runtime.DisposableEffect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeMark

/**
 * 타격 프로토타입 화면 — "내 선수의 타석 하나를 직접 조작하는 게 재밌는가" 검증용.
 *
 * 위: 능력치 슬라이더·카운트·기록 / 가운데: 타구 분포도 / 아래: 투구 화면(탭 = 스윙).
 * 그림은 단순 도형이다. 판정은 엔진([BattingSession] → `AtBat`)이 한다.
 */
@Composable
fun BattingScreen(session: BattingSession, player: SoundPlayer = SilentPlayer) {
    DisposableEffect(session, player) {
        session.onSound = { sfx, volume -> player.play(sfx, volume) }
        onDispose { session.onSound = { _, _ -> } }
    }
    // 매 프레임 시각. 캔버스가 이 값을 읽어서 프레임마다 다시 그린다
    var frameNanos by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(session) {
        var windowStart = 0L
        var frames = 0
        while (true) {
            withFrameNanos { now ->
                frameNanos = now
                session.tick()
                if (windowStart == 0L) windowStart = now
                frames++
                if (now - windowStart >= 1_000_000_000L) {
                    fps = (frames * 1_000_000_000.0 / (now - windowStart)).roundToInt()
                    frames = 0
                    windowStart = now
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(T.color.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SkillPanel(session)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(session) {
                        // 손가락이 닿는 순간 스윙 (떼는 순간이 아니라) — 타이밍이 생명이다
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            player.unlock()
                            session.tap()
                        }
                    },
            ) {
                Field(session, frameNanos)
                CalloutView(session, frameNanos, Modifier.align(Alignment.Center))
            }
        }
        Text(
            "$fps FPS",
            style = MaterialTheme.typography.bodySmall,
            color = T.color.chalk,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(T.space.xs)
                .background(T.color.scrim, RoundedCornerShape(T.space.xs))
                .padding(horizontal = T.space.xs),
        )
    }
}

@Composable
private fun SkillPanel(session: BattingSession) {
    val s = session.skills
    val tally = session.tally
    Column(Modifier.fillMaxWidth().padding(horizontal = T.space.md, vertical = T.space.xs)) {
        SkillSlider("컨택", "판정 폭", s.contact) { session.updateSkills(s.copy(contact = it)) }
        SkillSlider("파워", "타구 거리", s.power) { session.updateSkills(s.copy(power = it)) }
        SkillSlider("선구안", "구종 보이는 시점", s.eye) { session.updateSkills(s.copy(eye = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // 볼넷·삼진으로 끝나면 4볼·3스트라이크가 되므로 표시 칸(3·2)에 맞춰 자른다
            val balls = session.atBat.balls.coerceAtMost(3)
            val strikes = session.atBat.strikes.coerceAtMost(2)
            Text(
                "B ${"●".repeat(balls)}${"○".repeat(3 - balls)}  S ${"●".repeat(strikes)}${"○".repeat(2 - strikes)}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                session.lastTimingMs?.let { "직전 스윙 ${timingText(it)}" } ?: "공이 오면 화면을 탭!",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "${tally.plateAppearances}타석 ${tally.atBats}타수 ${tally.hits}안타 (${tally.average}) · 홈런 ${tally.homeRuns} · 삼진 ${tally.strikeouts} · 볼넷 ${tally.walks} · 시드 #${session.seedLabel}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SkillSlider(label: String, effect: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(104.dp)) {
            Text("$label $value", style = MaterialTheme.typography.labelLarge)
            Text(effect, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f).height(36.dp),
            colors = SliderDefaults.colors(thumbColor = T.color.primary, activeTrackColor = T.color.primary),
        )
    }
}

@Composable
private fun CalloutView(session: BattingSession, frameNanos: Long, modifier: Modifier) {
    val callout = session.callout ?: return
    @Suppress("UNUSED_VARIABLE")
    val redrawEveryFrame = frameNanos // 이 값을 읽어 두면 매 프레임 다시 계산된다
    val age = callout.since.ms() - callout.delayMs
    if (age < 0) return
    val over = session.phase is Phase.Over
    if (!over && age > session.presentation.resultHoldMs) return
    // 처음 150ms 동안 커졌다 돌아오는 "팡" 효과
    // 홈런은 더 크게, 더 오래 튄다
    val popMs = if (callout.tone == CalloutTone.BIG) 320.0 else 150.0
    val popAmp = if (callout.tone == CalloutTone.BIG) 0.6f else 0.3f
    val pop = if (age < popMs) 1f + popAmp * sin((age / popMs * PI).toFloat()) else 1f
    val color = when (callout.tone) {
        CalloutTone.GOOD -> T.color.good
        CalloutTone.BAD -> T.color.bad
        CalloutTone.NEUTRAL -> T.color.ink
        CalloutTone.BIG -> T.color.accent
    }
    Column(
        modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .background(T.color.surface, RoundedCornerShape(T.shape.card))
            .padding(horizontal = T.space.lg, vertical = T.space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(callout.text, style = MaterialTheme.typography.displaySmall, color = color)
        callout.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (over) Text("탭해서 다음 타석", style = MaterialTheme.typography.bodySmall)
    }
}

/** 타구 분포도(위 30%) + 투구 화면(아래 70%) + 타격 연출(흔들림·섬광·파편·꽃가루) */
@Composable
private fun Field(session: BattingSession, frameNanos: Long) {
    val measurer = rememberTextMeasurer()
    val c = T.color
    val labelStyle = MaterialTheme.typography.labelLarge
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE")
        val redrawEveryFrame = frameNanos // 이 값을 읽어 두면 매 프레임 다시 그린다
        val pres = session.presentation
        val impact = session.impact
        val sinceImpact = impact?.mark?.ms()

        // 화면 흔들림: 맞는 순간 크게, 빠르게 잦아든다
        val shake = if (impact != null && sinceImpact!! < pres.shakeMs) {
            val amp = when {
                impact.homeRun -> pres.shakeHomeRunPx
                impact.quality == ContactQuality.SOLID -> pres.shakeSolidPx
                else -> pres.shakeWeakPx
            }.dp.toPx()
            val decay = (1 - sinceImpact / pres.shakeMs).let { it * it }
            val t = sinceImpact / 1000.0
            Offset((amp * decay * sin(2 * PI * 31 * t)).toFloat(), (amp * decay * cos(2 * PI * 23 * t)).toFloat())
        } else {
            Offset.Zero
        }

        translate(shake.x, shake.y) {
            val sprayArea = Rect(0f, 0f, size.width, size.height * 0.3f)
            val pitchArea = Rect(0f, sprayArea.bottom, size.width, size.height)
            drawSprayChart(session, sprayArea, c)
            drawPitchView(session, PitchGeometry(pitchArea), c)

            // 구종 이름 (선구안 시점이 지나야 보인다)
            val flight = session.phase as? Phase.Flight
            if (flight != null) {
                val progress = flight.release.ms() / flight.pitch.flightMs
                if (flight.pitch.isRevealedAt(progress) && progress <= 1.0) {
                    val color = if (flight.pitch.type == PitchType.BREAKING) c.accent else c.primary
                    val layout = measurer.measure(flight.pitch.label, labelStyle.merge(TextStyle(color = color, fontWeight = FontWeight.Bold)))
                    drawText(layout, topLeft = Offset(pitchArea.left + 16.dp.toPx(), pitchArea.top + 16.dp.toPx()))
                }
            }
        }

        // 정타 섬광: 화면 전체가 잠깐 하얗게
        if (impact != null && impact.quality == ContactQuality.SOLID && sinceImpact!! < pres.flashMs) {
            val strength = if (impact.homeRun) 0.55f else 0.35f
            drawRect(c.chalk.copy(alpha = strength * (1 - (sinceImpact / pres.flashMs).toFloat())))
        }
        // 홈런 꽃가루
        if (impact != null && impact.homeRun && sinceImpact!! < pres.confettiMs) {
            drawConfetti(sinceImpact / pres.confettiMs, listOf(c.primary, c.accent, c.good, c.warn, c.bad))
        }
    }
}

/** 투구 화면의 좌표 계산 (투수 손, 스트라이크존, 공의 화면 위치) */
private class PitchGeometry(val area: Rect) {
    val cx = area.center.x
    val release = Offset(cx, area.top + area.height * 0.14f)
    val zoneCenter = Offset(cx, area.top + area.height * 0.66f)

    // 가로가 넓은 화면(데스크톱 창)에서도 존이 너무 커지지 않게 짧은 쪽 기준으로 잡는다
    private val unit = min(area.width, area.height * 0.75f)
    val zoneHalf = Size(unit * 0.15f, unit * 0.19f)

    /** 진행률 [progress] 에서 공의 화면 위치와 반지름(px 비율: 0~1 → 작게~크게) */
    fun ball(pitch: Pitch, progress: Double): Pair<Offset, Float> {
        val (zx, zy) = pitch.zonePositionAt(progress)
        val target = Offset(zoneCenter.x + (zx * zoneHalf.width).toFloat(), zoneCenter.y + (zy * zoneHalf.height).toFloat())
        // 원근감: 가까워질수록 빨리 커지고 빨리 움직이는 것처럼
        val p = progress.coerceAtMost(1.15).toFloat()
        val s = 0.35f * p + 0.65f * p * p
        return Offset(release.x + (target.x - release.x) * s, release.y + (target.y - release.y) * s) to s
    }
}

private fun DrawScope.drawSprayChart(session: BattingSession, area: Rect, c: ColorTokens) {
    val fence = session.config.fenceM
    val home = Offset(area.center.x, area.bottom - 6.dp.toPx())
    val scale = min((area.height - 12.dp.toPx()) / (fence * 1.05f).toFloat(), area.width / 2 / (fence * 0.75f).toFloat())
    fun point(angleDeg: Double, meters: Double): Offset {
        val a = angleDeg * PI / 180
        return Offset(home.x + (sin(a) * meters * scale).toFloat(), home.y - (cos(a) * meters * scale).toFloat())
    }
    // 페어 지역(부채꼴)
    val fan = Path().apply {
        moveTo(home.x, home.y)
        for (deg in -45..45 step 5) point(deg.toDouble(), fence).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(fan, c.grass)
    // 내야 다이아몬드
    val base = 27.4
    val diamond = Path().apply {
        moveTo(home.x, home.y)
        point(45.0, base).let { lineTo(it.x, it.y) }
        point(0.0, base * 1.414).let { lineTo(it.x, it.y) }
        point(-45.0, base).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(diamond, c.dirt)
    drawPath(fan, c.chalk, style = Stroke(1.5.dp.toPx()))

    for (dot in session.spray) {
        val color = when {
            dot.outcome == PaOutcome.HOME_RUN -> c.accent
            dot.outcome.isHit -> c.good
            else -> c.ink
        }
        drawCircle(color, 4.dp.toPx(), point(dot.angleDeg, dot.distanceM))
    }

    // 지금 날아가는 타구 (히트스톱이 끝난 뒤 출발)
    val phase = session.phase as? Phase.Hit ?: return
    val pres = session.presentation
    val t = ((phase.since.ms() - pres.hitStopMs) / pres.hitFlightMs).coerceIn(0.0, 1.0)
    val target = point(phase.contact.angleDeg, phase.contact.distanceM)
    fun at(tt: Double) = Offset(home.x + (target.x - home.x) * tt.toFloat(), home.y + (target.y - home.y) * tt.toFloat())
    // 뜬공은 포물선처럼 위로 솟았다 내려온다 (화면상 높이)
    fun arc(tt: Double) = if (phase.contact.battedBall == BattedBallType.GROUND) 0f else (sin(tt * PI) * area.height * 0.25).toFloat()
    // 꼬리
    for (k in 1..5) {
        val tt = (t - k * 0.03).coerceAtLeast(0.0)
        drawCircle(c.chalk.copy(alpha = 0.5f - k * 0.08f), (5 - k * 0.6f).dp.toPx(), at(tt) - Offset(0f, arc(tt)))
    }
    drawCircle(c.shadow, 3.dp.toPx(), at(t))
    drawCircle(c.chalk, 5.dp.toPx(), at(t) - Offset(0f, arc(t)))
    drawCircle(c.ink, 5.dp.toPx(), at(t) - Offset(0f, arc(t)), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawPitchView(session: BattingSession, g: PitchGeometry, c: ColorTokens) {
    val area = g.area
    val pres = session.presentation
    val cx = g.cx
    val zoneCenter = g.zoneCenter
    val zoneHalf = g.zoneHalf
    drawRect(c.grass, area.topLeft, area.size)

    // 마운드와 홈 주변 흙
    drawOval(c.dirt, Offset(cx - area.width * 0.12f, g.release.y - 6.dp.toPx()), Size(area.width * 0.24f, 30.dp.toPx()))
    drawOval(c.dirt, Offset(cx - area.width * 0.42f, zoneCenter.y + zoneHalf.height * 0.6f), Size(area.width * 0.84f, area.height * 0.32f))

    // 투수 (와인드업 중엔 팔이 올라간다)
    val windup = when (val p = session.phase) {
        is Phase.Windup -> (p.since.ms() / pres.windupMs).coerceIn(0.0, 1.0)
        is Phase.Flight -> 1.0
        else -> 0.0
    }
    val body = Offset(cx, g.release.y + 4.dp.toPx())
    drawCircle(c.ink, 7.dp.toPx(), body - Offset(0f, 16.dp.toPx()))
    drawLine(c.ink, body - Offset(0f, 10.dp.toPx()), body + Offset(0f, 8.dp.toPx()), 6.dp.toPx())
    val armAngle = (-PI / 2 * windup).toFloat()
    val shoulder = body - Offset(0f, 6.dp.toPx())
    drawLine(c.ink, shoulder, shoulder + Offset(12.dp.toPx() * cos(armAngle), 12.dp.toPx() * sin(armAngle)), 3.dp.toPx())

    // 스트라이크존과 홈플레이트
    drawRect(c.chalk.copy(alpha = 0.18f), zoneCenter - Offset(zoneHalf.width, zoneHalf.height), zoneHalf * 2f)
    drawRect(c.chalk, zoneCenter - Offset(zoneHalf.width, zoneHalf.height), zoneHalf * 2f, style = Stroke(2.dp.toPx()))
    val plateTop = zoneCenter.y + zoneHalf.height + 18.dp.toPx()
    val pw = zoneHalf.width * 0.9f
    val plate = Path().apply {
        moveTo(cx - pw, plateTop)
        lineTo(cx + pw, plateTop)
        lineTo(cx + pw, plateTop + 8.dp.toPx())
        lineTo(cx, plateTop + 18.dp.toPx())
        lineTo(cx - pw, plateTop + 8.dp.toPx())
        close()
    }
    drawPath(plate, c.chalk)

    val flight = session.phase as? Phase.Flight
    val hit = session.phase as? Phase.Hit

    // 스윙 진행률 (0 = 배트를 세운 대기 자세, 1 = 팔로스루 끝). 탭하자마자 반응하도록 0.35 에서 시작
    val swingProgress: Double? = when {
        hit != null -> {
            val e = hit.since.ms()
            // 히트스톱 동안 배트는 맞은 자리에서 멈춘다
            if (e < pres.hitStopMs) CONTACT_SWING else (CONTACT_SWING + (e - pres.hitStopMs) / pres.swingMs).coerceAtMost(1.0)
        }
        flight?.swing != null -> (0.35 + flight.swing.mark.ms() / pres.swingMs).coerceAtMost(1.0)
        else -> null
    }

    // 타자 (오른손 타자, 화면 왼쪽) + 배트
    val batterHip = Offset(cx - zoneHalf.width - 34.dp.toPx(), zoneCenter.y + zoneHalf.height * 0.4f)
    drawCircle(c.ink, 12.dp.toPx(), batterHip - Offset(0f, 58.dp.toPx()))
    drawLine(c.ink, batterHip - Offset(0f, 46.dp.toPx()), batterHip, 14.dp.toPx())
    val hands = batterHip - Offset(-6.dp.toPx(), 36.dp.toPx())
    val baseBat = 64.dp.toPx()
    var batLength = baseBat
    var batAngle = batAngleDeg(swingProgress ?: 0.0)
    if (hit != null && swingProgress != null) {
        // 맞은 공: 배트가 공이 있는 자리를 향하게 한다. 멀면 배트를 조금 늘려 닿게 (최대 1.8배)
        val (ballAt, _) = g.ball(hit.pitch, hit.swingAtMs / hit.pitch.flightMs)
        val v = ballAt - hands
        val aim = atan2(v.x.toDouble(), -v.y.toDouble()) * 180 / PI
        val follow = ((swingProgress - CONTACT_SWING) / (1 - CONTACT_SWING)).coerceIn(0.0, 1.0)
        batAngle = aim + (batAngleDeg(1.0) - aim) * follow
        batLength = (v.getDistance() + 6.dp.toPx()).coerceIn(baseBat, baseBat * 1.8f).let { baseBat + (it - baseBat) * (1 - follow.toFloat()) }
    }
    // 배트 궤적 잔상: 휘두른 범위를 반투명 부채꼴로
    if (swingProgress != null && swingProgress < 1.0) {
        val from = batAngleDeg(0.0)
        drawArc(
            c.chalk.copy(alpha = 0.35f),
            startAngle = (from - 90).toFloat(),
            sweepAngle = (batAngle - from).toFloat(),
            useCenter = true,
            topLeft = hands - Offset(batLength, batLength),
            size = Size(batLength * 2, batLength * 2),
        )
    }
    rotate(degrees = batAngle.toFloat(), pivot = hands) {
        drawLine(c.bat, hands, hands - Offset(0f, batLength), 6.dp.toPx())
    }

    // 날아오는 공
    if (flight != null) {
        val pitch = flight.pitch
        val progress = flight.release.ms() / pitch.flightMs
        val swing = flight.swing
        if (swing != null && swing.outcome.call == PitchCall.FOUL) {
            // 파울: 맞은 자리에서 옆·뒤로 튕겨 나간다
            val (hitPos, s) = g.ball(pitch, swing.atMs / pitch.flightMs)
            val t = (swing.mark.ms() / FOUL_FLIGHT_MS).coerceIn(0.0, 1.0)
            val side = if ((swing.outcome.contact?.angleDeg ?: 0.0) < 0) -1f else 1f
            drawLaunchedBall(hitPos, hitPos + Offset(side * area.width * 0.7f, -area.height * 0.5f), t, ballRadius(s), c)
            drawBurst(hitPos, swing.mark.ms() / pres.burstMs, ContactQuality.FOUL, c)
        } else if (progress <= 1.15) {
            val (pos, s) = g.ball(pitch, progress)
            val radius = ballRadius(s)
            val revealed = pitch.isRevealedAt(progress)
            val fill = if (revealed && pitch.type == PitchType.BREAKING) c.accent else c.chalk
            drawCircle(c.shadow, radius, pos + Offset(radius * 0.3f, radius * 0.5f))
            drawCircle(fill, radius, pos)
            drawCircle(if (revealed) (if (pitch.type == PitchType.BREAKING) c.accent else c.primary) else c.ink, radius, pos, style = Stroke(1.5.dp.toPx()))
        }
        // 미트에 꽂힌 순간 작은 링
        if (flight.mittPlayed && progress < 1.0 + MITT_RING_MS / pitch.flightMs) {
            val (end, s) = g.ball(pitch, 1.0)
            val t = ((progress - 1.0) * pitch.flightMs / MITT_RING_MS).toFloat().coerceIn(0f, 1f)
            drawCircle(c.chalk.copy(alpha = 1 - t), ballRadius(s) * (1.2f + t * 1.5f), end, style = Stroke(2.dp.toPx()))
        }
    }

    // 맞은 공: 히트스톱 동안 맞은 자리에 멈춰 있다가 튕겨 나간다
    if (hit != null) {
        val (hitPos, s) = g.ball(hit.pitch, hit.swingAtMs / hit.pitch.flightMs)
        val e = hit.since.ms()
        drawBurst(hitPos, e / pres.burstMs, hit.contact.quality, c)
        val t = ((e - pres.hitStopMs) / (pres.hitFlightMs * 0.6)).coerceIn(0.0, 1.0)
        val a = hit.contact.angleDeg * PI / 180
        val target = if (hit.contact.battedBall == BattedBallType.GROUND) {
            // 땅볼: 투수 쪽으로 굴러간다
            Offset(cx + (sin(a) * area.width * 0.5).toFloat(), g.release.y)
        } else {
            // 뜬공·라이너: 화면 위쪽(외야)으로 솟구친다
            Offset(cx + (sin(a) * area.width * 0.9).toFloat(), area.top - area.height * 0.2f)
        }
        if (t < 1.0) drawLaunchedBall(hitPos, target, t, ballRadius(s), c)
    }
}

/** 맞은 공이 [from] 에서 [to] 로 날아가며 작아진다. 꼬리를 그린다 */
private fun DrawScope.drawLaunchedBall(from: Offset, to: Offset, t: Double, startRadius: Float, c: ColorTokens) {
    fun at(tt: Double): Offset {
        val e = 1 - (1 - tt) * (1 - tt) // 처음에 빠르게
        return Offset(from.x + (to.x - from.x) * e.toFloat(), from.y + (to.y - from.y) * e.toFloat())
    }
    fun radius(tt: Double) = startRadius * (1 - 0.75f * tt.toFloat())
    for (k in 1..6) {
        val tt = (t - k * 0.025).coerceAtLeast(0.0)
        drawCircle(c.chalk.copy(alpha = 0.55f - k * 0.08f), radius(tt) * (1 - k * 0.08f), at(tt))
    }
    drawCircle(c.chalk, radius(t), at(t))
    drawCircle(c.ink, radius(t), at(t), style = Stroke(1.dp.toPx()))
}

/** 맞은 자리에서 퍼지는 별 모양 파편. 정타일수록 크고 노랗다 */
private fun DrawScope.drawBurst(center: Offset, t: Double, quality: ContactQuality, c: ColorTokens) {
    if (t >= 1.0 || quality == ContactQuality.MISS) return
    val (spokes, reach, color) = when (quality) {
        ContactQuality.SOLID -> Triple(12, 46.dp.toPx(), c.warn)
        ContactQuality.WEAK -> Triple(8, 26.dp.toPx(), c.chalk)
        else -> Triple(6, 18.dp.toPx(), c.chalk)
    }
    val e = (1 - (1 - t) * (1 - t)).toFloat()
    val alpha = (1 - t).toFloat()
    for (i in 0 until spokes) {
        val a = 2 * PI * i / spokes + 0.3
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(color.copy(alpha = alpha), center + dir * (reach * 0.25f * e), center + dir * (reach * e), (3.dp.toPx() * alpha).coerceAtLeast(1f))
    }
    drawCircle(color.copy(alpha = alpha * 0.8f), reach * 0.6f * e, center, style = Stroke(2.dp.toPx()))
}

/** 홈런 꽃가루. 위치는 조각 번호로 정해지는 가짜 난수라 매번 같다 */
private fun DrawScope.drawConfetti(t: Double, colors: List<androidx.compose.ui.graphics.Color>) {
    val alpha = if (t > 0.75) ((1 - t) / 0.25).toFloat() else 1f
    for (i in 0 until 48) {
        fun h(k: Int): Double = ((sin(i * 12.9898 + k * 78.233) * 43758.5453) % 1.0).let { if (it < 0) it + 1 else it }
        val x = size.width * h(1) + sin(t * 8 + i) * 18.dp.toPx()
        val y = -20.dp.toPx() + (size.height * 1.1f) * (t * (0.7 + 0.5 * h(2))).toFloat()
        val w = (5 + 4 * h(3)).dp.toPx()
        rotate(degrees = (t * 720 * (h(4) - 0.5)).toFloat(), pivot = Offset(x.toFloat(), y)) {
            drawRect(colors[i % colors.size].copy(alpha = alpha), Offset(x.toFloat() - w / 2, y - w / 4), Size(w, w / 2))
        }
    }
}

/** 배트 각도: 대기 −60° → 수평을 지나 → 팔로스루 +110°. 처음에 빠르고 끝에서 느려진다 */
private fun batAngleDeg(progress: Double): Double {
    val e = 1 - (1 - progress) * (1 - progress)
    return -60 + 170 * e
}

private fun DrawScope.ballRadius(s: Float): Float = (2.5f + 9f * s).dp.toPx()

/** 공이 배트에 맞는 지점의 스윙 진행률 */
private const val CONTACT_SWING = 0.45

/** 파울 타구가 화면 밖으로 날아가는 시간·미트 링이 퍼지는 시간 (연출 전용) */
private const val FOUL_FLIGHT_MS = 350.0
private const val MITT_RING_MS = 140.0

private fun TimeMark.ms(): Double = elapsedNow().inWholeMicroseconds / 1000.0
