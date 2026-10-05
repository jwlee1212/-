package baseballgm.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/*
 * 화면별 미리보기 — 360dp 폭, 라이트/다크 (2026-10-01, 절제 작업).
 *
 * IntelliJ·Android Studio 의 Compose 미리보기 창에서 보인다. VS Code 는 미리보기를 못 그리므로
 * 같은 화면을 PNG 로 구운 `docs/screenshots/restraint/` 를 보면 된다 (`./gradlew :app:renderScreens`).
 * 고치기 **전** 화면은 코드가 바뀌어 미리보기로 다시 그릴 수 없어서, 고치기 직전에 구운 PNG
 * (`before/`) 와 비교 이미지(`compare/`) 로 남겼다.
 */

private fun entry(id: String) = screenCatalog.first { it.id == id }

@Composable
private fun Show(id: String, dark: Boolean) = PreviewFrame(dark) { entry(id).content() }

@Preview(name = "스플래시 · 라이트", widthDp = 360, heightDp = 640)
@Composable
fun SplashLightPreview() = Show("splash", dark = false)

@Preview(name = "스플래시 · 다크", widthDp = 360, heightDp = 640)
@Composable
fun SplashDarkPreview() = Show("splash", dark = true)

@Preview(name = "타이틀 · 라이트", widthDp = 360, heightDp = 640)
@Composable
fun TitleLightPreview() = Show("title", dark = false)

@Preview(name = "타이틀 · 다크", widthDp = 360, heightDp = 640)
@Composable
fun TitleDarkPreview() = Show("title", dark = true)

@Preview(name = "단장 이름 입력 · 라이트", widthDp = 360, heightDp = 640)
@Composable
fun NameEntryLightPreview() = Show("name-entry", dark = false)

@Preview(name = "단장 이름 입력 · 다크", widthDp = 360, heightDp = 640)
@Composable
fun NameEntryDarkPreview() = Show("name-entry", dark = true)

@Preview(name = "구단 선택 · 라이트", widthDp = 360, heightDp = 1600)
@Composable
fun TeamSelectLightPreview() = Show("team-select", dark = false)

@Preview(name = "구단 선택 · 다크", widthDp = 360, heightDp = 1600)
@Composable
fun TeamSelectDarkPreview() = Show("team-select", dark = true)

@Preview(name = "리그 구성 · 라이트", widthDp = 360, heightDp = 640)
@Composable
fun LeagueBuildingLightPreview() = Show("league-building", dark = false)

@Preview(name = "리그 구성 · 다크", widthDp = 360, heightDp = 640)
@Composable
fun LeagueBuildingDarkPreview() = Show("league-building", dark = true)

@Preview(name = "홈 (셸 포함) · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun HomeLightPreview() = Show("home", dark = false)

@Preview(name = "홈 (셸 포함) · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun HomeDarkPreview() = Show("home", dark = true)

@Preview(name = "돌발 이벤트 카드 · 라이트", widthDp = 360, heightDp = 900)
@Composable
fun IncidentLightPreview() = Show("incident", dark = false)

@Preview(name = "돌발 이벤트 카드 · 다크", widthDp = 360, heightDp = 900)
@Composable
fun IncidentDarkPreview() = Show("incident", dark = true)

@Preview(name = "결과 공개 · 라이트", widthDp = 360, heightDp = 1800)
@Composable
fun WeekRevealLightPreview() = Show("week-reveal", dark = false)

@Preview(name = "결과 공개 · 다크", widthDp = 360, heightDp = 1800)
@Composable
fun WeekRevealDarkPreview() = Show("week-reveal", dark = true)

@Preview(name = "결과 공개 (연출 중) · 라이트", widthDp = 360, heightDp = 1800)
@Composable
fun WeekRevealMidLightPreview() = Show("week-reveal-mid", dark = false)

@Preview(name = "주간 브리핑 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun WeeklyReportLightPreview() = Show("weekly-report", dark = false)

@Preview(name = "주간 브리핑 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun WeeklyReportDarkPreview() = Show("weekly-report", dark = true)

@Preview(name = "문자 중계 · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun WatchLightPreview() = Show("watch", dark = false)

@Preview(name = "문자 중계 · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun WatchDarkPreview() = Show("watch", dark = true)

@Preview(name = "사전 지시 · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun DirectiveLightPreview() = Show("directive", dark = false)

@Preview(name = "사전 지시 · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun DirectiveDarkPreview() = Show("directive", dark = true)

@Preview(name = "뉴스·팬 반응 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun NewsLightPreview() = Show("news", dark = false)

@Preview(name = "뉴스·팬 반응 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun NewsDarkPreview() = Show("news", dark = true)

@Preview(name = "구단 운영 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun ClubLightPreview() = Show("club", dark = false)

@Preview(name = "구단 운영 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun ClubDarkPreview() = Show("club", dark = true)

@Preview(name = "단장 커리어 · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun CareerLightPreview() = Show("career", dark = false)

@Preview(name = "단장 커리어 · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun CareerDarkPreview() = Show("career", dark = true)

@Preview(name = "로스터 · 라이트", widthDp = 360, heightDp = 2600)
@Composable
fun RosterLightPreview() = Show("roster", dark = false)

@Preview(name = "로스터 · 다크", widthDp = 360, heightDp = 2600)
@Composable
fun RosterDarkPreview() = Show("roster", dark = true)

@Preview(name = "선수 목록 (우리 팀) · 라이트", widthDp = 360, heightDp = 1800)
@Composable
fun PlayerListOwnLightPreview() = Show("player-list-own", dark = false)

@Preview(name = "선수 목록 (우리 팀) · 다크", widthDp = 360, heightDp = 1800)
@Composable
fun PlayerListOwnDarkPreview() = Show("player-list-own", dark = true)

@Preview(name = "선수 목록 (타 팀) · 라이트", widthDp = 360, heightDp = 1800)
@Composable
fun PlayerListOtherLightPreview() = Show("player-list-other", dark = false)

@Preview(name = "선수 목록 (타 팀) · 다크", widthDp = 360, heightDp = 1800)
@Composable
fun PlayerListOtherDarkPreview() = Show("player-list-other", dark = true)

@Preview(name = "선수 상세 (우리 팀) · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun PlayerOwnLightPreview() = Show("player-own", dark = false)

@Preview(name = "선수 상세 (우리 팀) · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun PlayerOwnDarkPreview() = Show("player-own", dark = true)

@Preview(name = "선수 상세 (타 팀) · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun PlayerOtherLightPreview() = Show("player-other", dark = false)

@Preview(name = "선수 상세 (타 팀) · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun PlayerOtherDarkPreview() = Show("player-other", dark = true)

@Preview(name = "메시지 · 라이트", widthDp = 360, heightDp = 1800)
@Composable
fun MessagesLightPreview() = Show("messages", dark = false)

@Preview(name = "메시지 · 다크", widthDp = 360, heightDp = 1800)
@Composable
fun MessagesDarkPreview() = Show("messages", dark = true)

@Preview(name = "선수 비교 · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun CompareLightPreview() = Show("compare", dark = false)

@Preview(name = "선수 비교 · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun CompareDarkPreview() = Show("compare", dark = true)

@Preview(name = "스카우트 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun ScoutLightPreview() = Show("scout", dark = false)

@Preview(name = "스카우트 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun ScoutDarkPreview() = Show("scout", dark = true)

@Preview(name = "스카우트 리포트 · 라이트", widthDp = 360, heightDp = 2000)
@Composable
fun ProspectLightPreview() = Show("prospect", dark = false)

@Preview(name = "스카우트 리포트 · 다크", widthDp = 360, heightDp = 2000)
@Composable
fun ProspectDarkPreview() = Show("prospect", dark = true)

@Preview(name = "시장 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun MarketLightPreview() = Show("market", dark = false)

@Preview(name = "시장 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun MarketDarkPreview() = Show("market", dark = true)

@Preview(name = "기록 · 라이트", widthDp = 360, heightDp = 2400)
@Composable
fun RecordsLightPreview() = Show("records", dark = false)

@Preview(name = "기록 · 다크", widthDp = 360, heightDp = 2400)
@Composable
fun RecordsDarkPreview() = Show("records", dark = true)
