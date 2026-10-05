package baseballgm.app.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.PlayerLine
import baseballgm.app.ui.Pill
import baseballgm.app.ui.Secretary
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import baseballgm.events.Incident
import baseballgm.events.IncidentKind
import baseballgm.events.IncidentOption
import baseballgm.model.PlayerId
import baseballgm.events.IncidentRecord

/**
 * 돌발 이벤트 카드 (docs/16 §4-1 주중 개입).
 *
 * 비서가 "무슨 일이 있었는지 → 선택지마다 무슨 일이 생기는지"를 말해 주고, 단장은 하나를 고른다.
 * 선택지 설명에 효과 숫자를 그대로 적는다 — 숨김 수치가 아니라 단장이 내리는 결정의 값이기 때문이다.
 * 비서 추천은 표시만 하고 강요하지 않는다. "맡기기"를 누르면 추천대로 처리된다.
 */
@Composable
fun IncidentCard(
    session: GameSession,
    incident: Incident,
    modifier: Modifier = Modifier,
    onPlayer: (PlayerId) -> Unit = {},
    onCompare: (List<PlayerId>) -> Unit = {},
) {
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        IncidentHeader(incident, session.pendingIncidentCount)
        TradeOfferPlayers(session, incident, onPlayer, onCompare)
        SectionDivider()
        IncidentOptions(incident) { session.resolveIncident(it.id) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            senderPlayer(session, incident)?.let { player ->
                TextButton(onClick = { onPlayer(player.id) }) { Text("선수 보기") }
            }
            TextButton(onClick = { session.delegateIncident() }) { Text("${Secretary.NAME}에게 맡기기") }
        }
    }
    val sender = senderPlayer(session, incident)
    if (sender != null) {
        PlayerMessageCard(sender, "${incident.kind.label} · ${incident.whenLabel}", incident.message, modifier, body)
    } else {
        SecretaryCard(
            message = incident.message,
            title = "${incident.kind.label} · ${incident.whenLabel}",
            modifier = modifier,
            content = body,
        )
    }
}

/** 선수가 직접 보낸 메시지면 그 선수 (docs/13 만족도, 2026-10-04). 비서가 전하는 일이면 null */
private fun senderPlayer(session: GameSession, incident: Incident): baseballgm.model.Player? =
    incident.playerId?.takeIf { incident.kind.fromPlayer }?.let { id -> runCatching { session.player(id) }.getOrNull() }

/**
 * 선수가 보낸 메시지 카드. 비서 카드와 같은 모양에 아바타만 그 선수의 등번호 배지(구단 색 원, 얼굴 없음 — docs/16 §9)다.
 * 말은 선수의 1인칭이다.
 */
@Composable
private fun PlayerMessageCard(
    player: baseballgm.model.Player,
    title: String,
    message: String,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.radii.card),
        colors = CardDefaults.cardColors(containerColor = tokens.base.card, contentColor = tokens.base.text),
    ) {
        Column(Modifier.padding(tokens.spacing.l)) {
            Row(verticalAlignment = Alignment.Top) {
                baseballgm.app.ui.NumberBadge(player.uniformNumber.takeIf { it > 0 }, player.teamId)
                Spacer(Modifier.width(tokens.spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${player.registeredName} · $title",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(top = tokens.spacing.xs))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.base.text)
                }
            }
            Spacer(Modifier.padding(top = tokens.spacing.m))
            content()
        }
    }
}

/**
 * 헤드라인 한 줄. 종류는 카드 머리(회색)에 쓰고, 칩은 **급한 일**일 때만 단다 (절제 규칙: 칩은 상태에만).
 * 남은 이벤트 수는 칩이 아니라 회색 글자.
 */
@Composable
private fun IncidentHeader(incident: Incident, count: Int) {
    val tokens = AppTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (incident.kind.urgent) {
            Pill("급함", AppColors.bad)
            Spacer(Modifier.width(tokens.spacing.s))
        } else if (incident.kind.opportunity) {
            // 기회형 (재미 개선 3번): 문제가 아니라 제안이다 — 좋음 색 "기회" 칩
            Pill("기회", AppColors.good)
            Spacer(Modifier.width(tokens.spacing.s))
        }
        Text(incident.headline, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (count > 1) Text("외 ${count - 1}건", style = MaterialTheme.typography.bodySmall, color = tokens.base.textMuted)
    }
}

/**
 * 선택지 목록 — 한 줄 = 하나의 선택 (라벨 + 무슨 일이 생기는지).
 * 예전엔 선택지마다 테두리 카드였는데 비서 카드 안의 카드라 중첩이었다. 지금은 얇은 선으로 나눈 줄이다.
 * 비서 추천은 칩 하나로만 표시한다.
 */
@Composable
fun IncidentOptions(incident: Incident, onChoose: (IncidentOption) -> Unit) {
    val tokens = AppTheme.tokens
    Column {
        incident.options.forEachIndexed { index, option ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(color = tokens.base.line)
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoose(option) }.padding(vertical = tokens.spacing.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.base.brand,
                        modifier = Modifier.weight(1f),
                    )
                    if (option.recommended) Pill("비서 추천", tokens.base.brand)
                }
                Text(
                    option.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.base.textSecondary,
                    modifier = Modifier.padding(top = tokens.spacing.xs),
                )
            }
        }
    }
}

/**
 * 돌발 이벤트가 생긴 순간 뜨는 창. 진행 버튼을 누른 그 자리에서 경기가 멈췄다는 걸 확실히 느끼게 한다.
 * "나중에"를 누르면 창만 닫히고 카드는 홈 맨 위에 남는다 — 답하기 전에는 다음 경기로 못 간다.
 */
@Composable
fun IncidentDialog(
    session: GameSession,
    incident: Incident,
    onLater: () -> Unit,
    onPlayer: (PlayerId) -> Unit = {},
    onCompare: (List<PlayerId>) -> Unit = {},
) {
    val tokens = AppTheme.tokens
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Column {
                Text(
                    "${senderPlayer(session, incident)?.registeredName ?: Secretary.NAME} · ${incident.kind.label} · ${incident.whenLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.base.textSecondary,
                )
                Spacer(Modifier.padding(top = AppTheme.tokens.spacing.xs))
                IncidentHeader(incident, session.pendingIncidentCount)
            }
        },
        text = {
            Column(Modifier.heightIn(max = DIALOG_MAX_HEIGHT).verticalScroll(rememberScrollState())) {
                Text(incident.message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(top = tokens.spacing.s))
                TradeOfferPlayers(session, incident, onPlayer, onCompare)
                Spacer(Modifier.padding(top = tokens.spacing.s))
                IncidentOptions(incident) { session.resolveIncident(it.id) }
            }
        },
        confirmButton = { TextButton(onClick = { session.delegateIncident() }) { Text("맡길게요") } },
        dismissButton = { TextButton(onClick = onLater) { Text("나중에") } },
    )
}

/**
 * 트레이드 제안 카드에만: 오가는 선수 이름(누르면 상세)과 "비교하기".
 * 능력치를 보고 판단할 수 있어야 수락·거절이 결정이 된다 (2026-10-01 유저 요청).
 */
@Composable
private fun TradeOfferPlayers(
    session: GameSession,
    incident: Incident,
    onPlayer: (PlayerId) -> Unit,
    onCompare: (List<PlayerId>) -> Unit,
) {
    if (incident.kind != IncidentKind.TRADE_OFFER) return
    val offer = session.pendingTradeOffer() ?: return
    val tokens = AppTheme.tokens
    @Composable
    fun side(title: String, ids: List<PlayerId>) {
        if (ids.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelSmall, color = tokens.base.textMuted, modifier = Modifier.padding(top = AppTheme.tokens.spacing.xs))
        ids.mapNotNull { runCatching { session.player(it) }.getOrNull() }.forEach { player ->
            val scouted = session.scout(player)
            // 다른 화면과 같은 정체 표시 (2026-10-02 전 화면 통일). 종합은 스카우트 범위
            PlayerLine(session.tagOf(player), onClick = { onPlayer(player.id) }) {
                Text(
                    scouted.overall.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.grade.of(scouted.overall.center),
                )
            }
        }
    }
    side("받는 선수", offer.fromProposer.playerIds)
    side("주는 선수", offer.fromPartner.playerIds)
    val ids = offer.fromProposer.playerIds + offer.fromPartner.playerIds
    if (ids.size >= 2) {
        TextButton(onClick = { onCompare(ids.take(GameSession.MAX_COMPARE)) }) { Text("오가는 선수 능력치 비교하기") }
    }
    Spacer(Modifier.padding(top = AppTheme.tokens.spacing.xs))
}

/** 답한 뒤 비서의 후속 한마디 */
@Composable
fun IncidentFollowUp(record: IncidentRecord, onDismiss: () -> Unit) {
    SecretaryCard(
        message = record.summary.ifBlank { "처리했어요." },
        title = record.headline,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(if (record.delegated) "비서 처리" else "단장 결정", if (record.delegated) AppColors.muted else AppTheme.tokens.base.brand)
            Spacer(Modifier.width(AppTheme.tokens.spacing.s))
            Text(record.choice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    }
}

private val DIALOG_MAX_HEIGHT = 460.dp
