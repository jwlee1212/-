package baseballgm.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import baseballgm.app.Briefing
import baseballgm.app.BriefingTarget
import baseballgm.app.GameSession
import baseballgm.app.MessageItem
import baseballgm.app.Messages
import baseballgm.app.Sender
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.NavRow
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.model.PlayerId

/**
 * 메시지 탭 (2026-10-03 화면 개편 — FM 받은편지함을 메신저처럼, docs/16 §3·4-1).
 *
 * 위에서부터
 * 1. 답장 필요: 기다리는 돌발 이벤트. 선택지 카드를 바로 여기서 고른다 (예전엔 홈 맨 위 카드)
 * 2. 결정할 것: FA·드래프트·1군 빈자리 같은 일 — 누르면 그 화면으로 (예전 홈 브리핑 카드의 목록)
 * 3. 대화: 발신자별 한 줄 (미스 백·스카우트팀·의료진·코칭스태프·프런트). 누르면 그 사람과의 대화
 */
@Composable
fun MessagesScreen(
    session: GameSession,
    onThread: (Sender) -> Unit,
    onDecision: (BriefingTarget) -> Unit,
    onPlayer: (PlayerId) -> Unit,
    onCompare: (List<PlayerId>) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val incident = session.pendingIncident
    val decisions = Briefing.decisions(session).filter { it.target != BriefingTarget.INCIDENT }
    val threads = Messages.threads(session)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        contentPadding = PaddingValues(vertical = tokens.spacing.m),
    ) {
        if (incident != null) {
            item(key = "incident-${incident.id}") {
                IncidentCard(session, incident, onPlayer = onPlayer, onCompare = onCompare)
            }
        } else {
            session.lastIncidentRecord?.let { record ->
                item(key = "follow-up") { IncidentFollowUp(record) { session.dismissIncidentRecord() } }
            }
        }
        if (decisions.isNotEmpty()) {
            item(key = "decisions") {
                SectionCard("결정할 것 ${decisions.size}건") {
                    decisions.forEach { item ->
                        NavRow(
                            title = item.text,
                            caption = null,
                            onClick = { onDecision(item.target) },
                            leading = if (item.urgent) ({ Pill("급함", AppColors.bad) }) else null,
                        )
                    }
                }
            }
        }
        item(key = "threads") {
            SectionCard(null) {
                threads.forEachIndexed { index, thread ->
                    if (index > 0) SectionDivider()
                    ThreadRow(thread.sender, thread.latest, thread.count, thread.fresh) { onThread(thread.sender) }
                }
            }
        }
    }
}

/** 대화 목록 한 줄: 발신자 아바타 · 이름(새 소식이면 점) · 최근 말 한 줄 · 주차 */
@Composable
private fun ThreadRow(sender: Sender, latest: MessageItem?, count: Int, fresh: Boolean, onClick: () -> Unit) {
    val tokens = AppTheme.tokens
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderAvatar(sender)
        Spacer(Modifier.width(tokens.spacing.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sender.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.text)
                if (fresh) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    // 새 소식 표시: 점 하나 + 접근성 라벨 (색만으로 알리지 않게 화면 낭독기엔 글자로)
                    Box(
                        Modifier.size(tokens.spacing.s).clip(CircleShape).background(tokens.base.brand)
                            .semantics { contentDescription = "새 소식" },
                    )
                }
                Spacer(Modifier.weight(1f))
                latest?.let { Text("${it.week}주", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted) }
            }
            Text(
                latest?.lines?.firstOrNull() ?: "아직 소식이 없어요.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.base.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 발신자 아바타: 비서는 기존 실루엣, 나머지는 중립 원 + 글자 한 자 (docs/16 §9 — 얼굴 없음, 구단 색 아님).
 */
@Composable
internal fun SenderAvatar(sender: Sender) {
    val tokens = AppTheme.tokens
    if (sender == Sender.SECRETARY) {
        baseballgm.app.ui.SecretaryAvatar()
        return
    }
    Box(
        Modifier.size(tokens.sizes.numberBadge + tokens.spacing.s).clip(CircleShape).background(tokens.base.cardInset),
        contentAlignment = Alignment.Center,
    ) {
        Text(sender.initial, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tokens.base.textSecondary)
    }
}

/**
 * 한 사람과의 대화. 최근 것이 위, 주차가 바뀌면 "N주차" 구분. 말풍선은 카드가 아니라 옅은 면(카드 중첩 금지).
 * 비서 대화에는 결정마다 성적표 판정 칩이 붙는다 (예전 뉴스 화면의 결정 일지).
 */
@Composable
fun MessageThreadScreen(session: GameSession, sender: Sender, onScoutDigest: () -> Unit = {}) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    val tokens = AppTheme.tokens
    val items = Messages.thread(session, sender)
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        contentPadding = PaddingValues(vertical = tokens.spacing.m),
    ) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(sender)
                Spacer(Modifier.width(tokens.spacing.m))
                Text(sender.label, style = MaterialTheme.typography.titleMedium, color = tokens.base.text)
            }
            // 스카우트팀 대화: 정기 리포트 전문을 여는 입구 (2026-10-04)
            if (sender == Sender.SCOUT && session.scoutDigests().isNotEmpty()) {
                Spacer(Modifier.height(tokens.spacing.s))
                androidx.compose.material3.OutlinedButton(onClick = onScoutDigest, modifier = Modifier.fillMaxWidth()) {
                    Text("최근 스카우팅 리포트 보기 (${session.scoutDigests().last().week}주차)")
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { Text("아직 소식이 없어요.", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted) }
        }
        var lastWeek: Int? = null
        items.forEachIndexed { index, message ->
            if (message.week != lastWeek) {
                val week = message.week
                item(key = "week-$index") {
                    Text("${week}주차", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted, modifier = Modifier.padding(top = tokens.spacing.s))
                }
                lastWeek = week
            }
            item(key = "msg-$index") { Bubble(message) }
        }
    }
}

@Composable
private fun Bubble(message: MessageItem) {
    val tokens = AppTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = tokens.radii.chip, topEnd = tokens.radii.card, bottomEnd = tokens.radii.card, bottomStart = tokens.radii.card))
            .background(tokens.base.card)
            .padding(tokens.spacing.m),
    ) {
        message.verdict?.let { verdict ->
            Pill(verdict.label, verdictColor(verdict))
            Spacer(Modifier.height(tokens.spacing.xs))
        }
        message.lines.forEachIndexed { index, line ->
            Text(
                line,
                style = if (index == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (index == 0 && message.verdict != null) FontWeight.Bold else FontWeight.Normal,
                color = if (index == 0) tokens.base.text else tokens.base.textSecondary,
            )
        }
    }
}
