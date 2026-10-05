package baseballgm.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import baseballgm.app.screen.CareerScreen
import baseballgm.app.screen.ClubScreen
import baseballgm.app.screen.CompareScreen
import baseballgm.app.screen.DirectiveScreen
import baseballgm.app.screen.IncidentCard
import baseballgm.app.screen.LeagueBuildingScreen
import baseballgm.app.screen.MainShell
import baseballgm.app.screen.RecruitScreen
import baseballgm.app.screen.RecruitSection
import baseballgm.app.screen.MessagesScreen
import baseballgm.app.screen.MessageThreadScreen
import baseballgm.app.screen.SquadScreen
import baseballgm.app.screen.NameEntryScreen
import baseballgm.app.screen.NewsScreen
import baseballgm.app.screen.PlayerDetailScreen
import baseballgm.app.screen.ProspectDetailScreen
import baseballgm.app.screen.ScoutScreen
import baseballgm.app.screen.SplashScreen
import baseballgm.app.screen.TeamSelectScreen
import baseballgm.app.screen.TitleScreen
import baseballgm.app.screen.WatchScreen
import baseballgm.app.screen.WeeklyReportScreen
import baseballgm.app.screen.WeekRevealScreen
import baseballgm.app.ui.AppTheme
import baseballgm.app.ui.PlayerList
import baseballgm.app.screen.playerListSections

/**
 * 미리보기·스크린샷 대상 화면 목록. `@Preview` 함수와 PNG 렌더러([main] in RenderScreens.kt)가 **같은 목록**을 쓴다.
 *
 * @param id 파일 이름 (예: `roster` → roster-light.png)
 * @param heightDp 그릴 높이. 스크롤 화면은 전체가 들어가게 길게 잡는다
 */
class ScreenEntry(val id: String, val title: String, val heightDp: Int, val content: @Composable () -> Unit)

private val fixture get() = PreviewFixture

val screenCatalog: List<ScreenEntry> = listOf(
    ScreenEntry("splash", "스플래시", 640) { SplashScreen(loading = true) {} },
    ScreenEntry("title", "타이틀", 640) { TitleScreen(resume = fixture.session, onContinue = {}, onNewGame = {}) },
    ScreenEntry("name-entry", "단장 이름 입력", 640) { NameEntryScreen {} },
    ScreenEntry("team-select", "구단 선택", 1600) { TeamSelectScreen(fixture.host.teams) {} },
    ScreenEntry("league-building", "리그 구성", 640) {
        LeagueBuildingScreen(fixture.host.teams.first(), "선수단을 불러오는 중", null, {}, {})
    },
    ScreenEntry("home", "홈 (셸 포함)", 1600) {
        MainShell(fixture.host, fixture.session, welcome = false, onDismissWelcome = {})
    },
    ScreenEntry("incident", "돌발 이벤트 카드", 900) {
        fixture.incidentSession?.let { session ->
            Column(Modifier.padding(AppTheme.tokens.spacing.m)) { IncidentCard(session, session.pendingIncident!!) }
        }
    },
    // 결과 공개 (재미 개선 1번): 끝 상태와 연출 도중(세 장 열림) 모습
    ScreenEntry("week-reveal", "결과 공개", 1800) {
        WeekRevealScreen(fixture.session, fixture.revealWeek, fixture.revealRankBefore, animate = false, drive = false, onWatch = {}, onReport = {}, onClose = {})
    },
    ScreenEntry("week-reveal-mid", "결과 공개 (연출 중)", 1800) {
        WeekRevealScreen(fixture.session, fixture.revealWeek, fixture.revealRankBefore, animate = true, initialShown = 3, drive = false, onWatch = {}, onReport = {}, onClose = {})
    },
    // 주중 돌발 이벤트로 멈춘 상태: 치른 경기까지만 열리고 그 아래 "답하기" 카드, 남은 경기는 "경기 전"
    ScreenEntry("week-reveal-incident", "결과 공개 (이벤트 대기)", 1800) {
        fixture.incidentSession?.let { session ->
            WeekRevealScreen(session, session.week, null, animate = false, drive = false, onWatch = {}, onReport = {}, onClose = {})
        }
    },
    ScreenEntry("incident-opportunity", "기회형 이벤트 카드", 900) {
        fixture.opportunitySession?.let { session ->
            Column(Modifier.padding(AppTheme.tokens.spacing.l)) { IncidentCard(session, session.pendingIncident!!) }
        }
    },
    ScreenEntry("weekly-report", "주간 브리핑", 2400) { WeeklyReportScreen(fixture.session, onWatch = {}, onOpenNews = {}) },
    ScreenEntry("watch", "문자 중계", 2000) { WatchScreen(fixture.session) },
    ScreenEntry("directive", "사전 지시", 2000) { DirectiveScreen(fixture.session) },
    ScreenEntry("news", "뉴스·팬 반응", 2400) { NewsScreen(fixture.session) },
    ScreenEntry("club", "구단 운영", 2400) { ClubScreen(fixture.session) },
    ScreenEntry("career", "단장 커리어", 2000) { CareerScreen(fixture.session) },
    ScreenEntry("roster", "선수단", 2600) { SquadScreen(fixture.session, onPlayer = {}) },
    ScreenEntry("records-teams", "리그 · 팀 기록", 1200) { baseballgm.app.screen.LeagueSectionScreen(fixture.session, baseballgm.app.screen.LeagueSection.TEAMS, onPlayer = {}) },
    ScreenEntry("messages", "메시지", 1800) {
        MessagesScreen(fixture.session, onThread = {}, onDecision = {}, onPlayer = {}, onCompare = {})
    },
    ScreenEntry("message-thread", "메시지 · 미스 백 대화", 1800) {
        MessageThreadScreen(fixture.session, baseballgm.app.Sender.SECRETARY)
    },
    // 선수 목록 컴포넌트만 (2026-10-02 레퍼런스 리팩토링). 우리 팀 = 정확한 숫자, 타 팀 = ScoutingView 범위
    ScreenEntry("player-list-own", "선수 목록 (우리 팀)", 1800) { PlayerListPreview(fixture.session.userTeamId) },
    ScreenEntry("player-list-other", "선수 목록 (타 팀)", 1800) { PlayerListPreview(fixture.otherTeam) },
    ScreenEntry("player-own", "선수 상세 (우리 팀)", 2000) { PlayerDetailScreen(fixture.session, fixture.ownBatter) },
    ScreenEntry("player-other", "선수 상세 (타 팀)", 2000) { PlayerDetailScreen(fixture.session, fixture.otherBatter) },
    ScreenEntry("compare", "선수 비교", 2000) { CompareScreen(fixture.session, fixture.compareIds) {} },
    ScreenEntry("scout", "스카우트", 2400) { ScoutScreen(fixture.session, {}) },
    ScreenEntry("draft-class", "우리 신인 (드래프트 직후)", 3600) { baseballgm.app.screen.DraftClassScreen(fixture.draftSession) {} },
    ScreenEntry("prospect", "스카우트 리포트", 2000) { ProspectDetailScreen(fixture.session, fixture.prospect) {} },
    ScreenEntry("market", "영입 (트레이드)", 2400) { RecruitScreen(fixture.session, RecruitSection.TRADE, onSection = {}) },
    // 교환대에 선수를 올려 둔 모습 (상대 반응 게이지)
    ScreenEntry("trade-counter", "영입 (교환대)", 2200) {
        val session = fixture.session
        val partner = session.league.teams.first { it.id != session.userTeamId }.id
        val give = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM).filterIsInstance<baseballgm.model.Pitcher>().filter { !it.isForeign }.drop(3).take(1).map { it.id }
        val get = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM, partner).filterIsInstance<baseballgm.model.Pitcher>().filter { !it.isForeign }.take(1).map { it.id }
        // 지명권도 하나씩: 우리가 다음 시즌 2라운드를 주고, 상대의 다음 시즌 3라운드를 요구
        val givePick = session.picksOf(session.userTeamId).filter { it.round == 2 }.take(1)
        val getPick = session.picksOf(partner).filter { it.round == 3 }.take(1)
        RecruitScreen(session, RecruitSection.TRADE, onSection = {}, tradeDraft = baseballgm.app.screen.TradeDraft(partner, give, get, givePick, getPick))
    },
    ScreenEntry("recruit-fa", "영입 (FA, 시즌 중)", 1600) { RecruitScreen(fixture.session, RecruitSection.FREE_AGENCY, onSection = {}) },
    ScreenEntry("recruit-fa-market", "영입 (FA 시장 진행 중)", 2600) { RecruitScreen(fixture.faSession, RecruitSection.FREE_AGENCY, onSection = {}) },
    // 비FA 다년계약 협상 테이블 (2026-10-05)
    ScreenEntry("extension-negotiation", "다년계약 협상", 2600) {
        val session = fixture.session
        baseballgm.app.screen.ExtensionNegotiationScreen(session, session.extensionCandidates().first().id, onPlayer = {})
    },
    // FA 협상 테이블 (2026-10-04, FM식 — 예전 조건 창을 대신한다). 한 번 모자라게 제안해 대화·역제안이 보이는 상태
    ScreenEntry("fa-negotiation", "FA 협상 테이블", 2600) {
        val session = fixture.faTalkSession
        baseballgm.app.screen.FaNegotiationScreen(session, fixture.faTalkPlayer, onPlayer = {})
    },
    // 연도별 능력치 그래프: 우리 팀에서 가장 많이 바뀐 젊은 선수 / 타 팀 선수(범위 띠)
    ScreenEntry("rating-history-own", "연도별 능력치 (우리 팀)", 900) {
        val session = fixture.veteranSession
        val player = session.state.playersOf(session.userTeamId).filter { !it.isForeign }
            .maxBy { p -> session.ratingHistory(p).let { h -> h.last().overall.center - h.first().overall.center } }
        RatingHistoryPreview(session, player)
    },
    ScreenEntry("rating-history-other", "연도별 능력치 (타 팀)", 900) {
        val session = fixture.veteranSession
        val player = session.state.allPlayers().filter { it.teamId != null && it.teamId != session.userTeamId && !it.isForeign }
            .maxBy { session.ratingHistory(it).size * 100 + session.scout(it).overall.center }
        RatingHistoryPreview(session, player)
    },
    ScreenEntry("roster-stove", "로스터 (스토브리그)", 1800) { baseballgm.app.screen.RosterScreen(fixture.faSession, onPlayer = {}) },
    ScreenEntry("records", "리그 허브", 2600) { baseballgm.app.screen.LeagueHubScreen(fixture.session) {} },
    ScreenEntry("records-standings", "리그 · 순위", 1200) { baseballgm.app.screen.LeagueSectionScreen(fixture.session, baseballgm.app.screen.LeagueSection.STANDINGS, onPlayer = {}) },
    ScreenEntry("records-batting", "리그 · 타자 기록표", 2400) { baseballgm.app.screen.LeagueSectionScreen(fixture.session, baseballgm.app.screen.LeagueSection.BATTING, onPlayer = {}) },
    ScreenEntry("records-pitching", "리그 · 투수 기록표", 2400) { baseballgm.app.screen.LeagueSectionScreen(fixture.session, baseballgm.app.screen.LeagueSection.PITCHING, onPlayer = {}) },
    ScreenEntry("player-record-own", "선수 상세 · 기록 (우리 팀)", 1800) { PlayerDetailScreen(fixture.session, fixture.ownBatter, initialTab = 2) },
    ScreenEntry("player-record-other", "선수 상세 · 기록 (타 팀)", 1800) { PlayerDetailScreen(fixture.session, fixture.otherBatter, initialTab = 2) },
    // 선수 성향과 만족도 (2026-10-04)
    ScreenEntry("player-morale", "선수 상세 · 요약 (만족도 카드)", 1800) { PlayerDetailScreen(fixture.session, fixture.ownBatter, initialTab = 0) },
    ScreenEntry("player-personality-other", "선수 상세 · 능력치 (타 팀 성향)", 1800) { PlayerDetailScreen(fixture.session, fixture.otherBatter, initialTab = 1) },
    ScreenEntry("incident-player", "선수가 보낸 메시지 카드 (이적 요청)", 1000) {
        fixture.playerMessageSession?.let { session ->
            Column(Modifier.padding(AppTheme.tokens.spacing.m)) { IncidentCard(session, session.pendingIncident!!) }
        }
    },
    ScreenEntry("messages-players", "메시지 · 선수단 대화", 1200) {
        (fixture.answeredPlayerSession ?: fixture.session).let { session ->
            baseballgm.app.screen.MessageThreadScreen(session, baseballgm.app.Sender.PLAYERS)
        }
    },
)

@Composable
private fun RatingHistoryPreview(session: baseballgm.app.GameSession, player: baseballgm.model.Player) {
    Column(Modifier.padding(AppTheme.tokens.spacing.l)) {
        baseballgm.app.screen.RatingHistoryCard(
            session.ratingHistory(player),
            if (player is baseballgm.model.Pitcher) baseballgm.model.Attribute.pitcherAttributes else baseballgm.model.Attribute.batterAttributes,
        )
    }
}

/** 선수 목록 컴포넌트 하나를 그 팀 1군으로 그린다 */
@Composable
private fun PlayerListPreview(teamId: baseballgm.model.TeamId) {
    val session = fixture.session
    val players = session.roster(baseballgm.model.RosterLevel.FIRST_TEAM, teamId)
    Column(Modifier.padding(AppTheme.tokens.spacing.l)) {
        PlayerList(playerListSections(session, players, baseballgm.model.RosterLevel.FIRST_TEAM), onPlayer = {})
    }
}

/** 한 화면을 테마·바탕과 함께 그린다. 셸 밖 화면은 Scaffold 바탕이 없어서 바탕을 직접 깐다 */
@Composable
fun PreviewFrame(dark: Boolean, content: @Composable () -> Unit) {
    AppTheme(PreviewFixture.teamColors, dark) {
        Box(Modifier.fillMaxSize().background(AppTheme.tokens.base.background)) { content() }
    }
}
