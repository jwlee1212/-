package baseballgm.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import baseballgm.app.GameSession
import baseballgm.app.ui.AppColors
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.FanAvatar
import baseballgm.app.ui.MeterBar
import baseballgm.app.ui.Pill
import baseballgm.app.ui.SecretaryCard
import baseballgm.app.ui.SectionCard
import baseballgm.app.ui.SectionDivider
import baseballgm.events.FanMood
import baseballgm.events.NewsItem
import baseballgm.events.NewsKind

/**
 * 뉴스·팬 반응 (docs/16 뉴스·팬 SNS). 홈의 "뉴스·팬 반응" 카드에서 연다.
 *
 * - 리그 기사: 매체·헤드라인·첫 문장. 우리 팀 기사와 단장 인터뷰는 강조
 * - 팬 반응: 팬심 게이지 + 우리 팬 SNS (공감 수)
 * (결정 일지는 2026-10-03 메시지 탭 미스 백 대화로 옮겼다. 리그 탭에도 뉴스·팬 반응 구역이 있다)
 */
@Composable
fun NewsScreen(session: GameSession, initialTab: Int = 0) {
    @Suppress("UNUSED_VARIABLE")
    val revision = session.revision
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("리그 기사") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("팬 반응") })
        }
        // 결정 일지는 2026-10-03 메시지 탭(미스 백 대화)으로 옮겼다
        when (tab) {
            0 -> NewsFeed(session)
            else -> FanFeed(session)
        }
    }
}

@Composable
internal fun NewsFeed(session: GameSession) {
    val spacing = AppTheme.tokens.spacing
    var oursOnly by rememberSaveable { mutableStateOf(false) }
    val byWeek = session.news()
        .filter { !oursOnly || session.userTeamId in it.teams }
        .groupBy { it.week }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                FilterChip(selected = !oursOnly, onClick = { oursOnly = false }, label = { Text("리그 전체") })
                FilterChip(selected = oursOnly, onClick = { oursOnly = true }, label = { Text("${session.userTeam.name}만") })
            }
        }
        if (byWeek.isEmpty()) {
            item { SecretaryCard("아직 기사가 없어요. 한 주가 지나면 리그 소식이 여기 쌓여요.") }
        }
        byWeek.forEach { (week, items) ->
            item(key = "week-$week-$oursOnly") {
                SectionCard("${week}주차") {
                    items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = spacing.s), color = AppTheme.tokens.base.line)
                        NewsLine(session, item)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsLine(session: GameSession, item: NewsItem) {
    val tokens = AppTheme.tokens
    val ours = session.userTeamId in item.teams
    val frontOffice = item.kind == NewsKind.FRONT_OFFICE
    Column(Modifier.fillMaxWidth()) {
        // 기사 종류는 분류일 뿐 상태가 아니라 칩 대신 회색 글자 (절제 규칙)
        Text(
            "${item.kind.label} · ${item.outlet.ifBlank { "리그 소식" }}",
            style = MaterialTheme.typography.labelSmall,
            color = if (ours || frontOffice) tokens.base.brand else tokens.base.textMuted,
        )
        Text(
            item.headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (ours || frontOffice) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = AppTheme.tokens.spacing.xs),
        )
        if (item.lead.isNotBlank()) {
            Text(item.lead, style = MaterialTheme.typography.bodySmall, color = tokens.base.textSecondary, modifier = Modifier.padding(top = AppTheme.tokens.spacing.xs))
        }
    }
}

@Composable
internal fun FanFeed(session: GameSession) {
    val tokens = AppTheme.tokens
    val spacing = tokens.spacing
    val posts = session.fanPosts()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        item {
            val fan = session.fanSupport()
            SecretaryCard(fanComment(fan, session.fanLabel())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("팬심", style = MaterialTheme.typography.labelMedium, color = tokens.base.textSecondary)
                    Spacer(Modifier.width(spacing.s))
                    MeterBar(fan, moodColor(fan), Modifier.weight(1f))
                    Spacer(Modifier.width(spacing.s))
                    Text("$fan", style = MaterialTheme.typography.titleLarge, color = moodColor(fan))
                }
            }
        }
        if (posts.isEmpty()) {
            item { Text("아직 올라온 글이 없어요.", style = MaterialTheme.typography.bodySmall, color = AppColors.muted) }
        }
        items(posts, key = { "${it.season}-${it.week}-${it.author}-${it.text}" }) { post ->
            // 글쓴이는 카드 제목이 아니라 글 아래 회색 줄에 한 번만
            SectionCard(null) {
                Row(verticalAlignment = Alignment.Top) {
                    FanAvatar(post.author)
                    Spacer(Modifier.width(spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(post.text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(spacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${post.author} · ${post.week}주차 · 공감 ${post.likes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.muted,
                                modifier = Modifier.weight(1f),
                            )
                            Pill(post.mood.label, moodColorOf(post.mood))
                        }
                    }
                }
            }
        }
    }
}

private fun fanComment(fan: Int, label: String): String = when {
    fan >= 70 -> "팬들 분위기 좋아요($label). 이럴 때 관중도 스폰서도 따라와요."
    fan >= 45 -> "팬들은 지켜보는 중이에요($label). 한두 주 성적에 따라 금방 움직여요."
    else -> "팬들 마음이 많이 식었어요($label). 연승 한 번이면 분위기가 바뀌니 너무 걱정 마세요."
}

@Composable
private fun moodColor(fan: Int) = when {
    fan >= 65 -> AppColors.good
    fan >= 40 -> AppColors.warn
    else -> AppColors.bad
}

@Composable
private fun moodColorOf(mood: FanMood) = when (mood) {
    FanMood.POSITIVE -> AppColors.good
    FanMood.NEUTRAL -> AppColors.muted
    FanMood.NEGATIVE -> AppColors.bad
}
