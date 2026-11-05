#!/usr/bin/env python3
"""프로 편 콘텐츠 → config/content.json (2026-11-05).

build_scenario.py 와 같은 방식: 이 파일이 원본이다. content.json 의 다음 부분을 새로 쓴다 (나머지는 그대로).
  - proTeams 의 id·연고·전력 구분·성격·감독·주장 (이름·약칭은 그대로)
  - pro (뉴스·SNS 문장 틀, 매체, 기념 순간 = 상황 컷신 목록, 부문 이름)
  - people.chae (프로 편 새 인물 윤채아), places 의 프로 장소
  - messages 중 m_y2_jian_confess · m_y3_* · m_pro_* (사이드 스토리 문자)
  - events 중 y2_side_jian_answer · pro_side_* (사이드 스토리 카드)
고친 뒤:  python3 scripts/pro_content.py  (그다음 build_scenario.py 를 돌려도 이 내용은 남는다 — 그 스크립트는 epNN_ 이벤트만 다시 쓴다)
모든 이름은 가상이다 (CLAUDE.md §1 — 실제 구단·선수·매체 이름 금지).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "config/content.json"

# ---------- 구단 (docs/worldview.md 5-2) ----------
TEAMS = {
    "KKG 스타즈": dict(id="kkg", city="서울", tier="강", style="자금력 최고, FA 큰손, 인기 구단", coach="백진우", captain="오세혁"),
    "동진 불스": dict(id="dongjin", city="대구", tier="강", style="투수력 중심의 전통 강호", coach="마상철", captain="허민수"),
    "한성 블루호크스": dict(id="hansung", city="서울", tier="중", style="젊은 선수를 적극 기용하는 육성형", coach="표재윤", captain="도형석"),
    "세광 웨이브스": dict(id="segwang", city="인천", tier="중", style="장타력 중심 공격 야구", coach="남궁태", captain="엄기찬"),
    "남도 팔콘스": dict(id="namdo", city="광주", tier="중", style="열성 팬덤, 분위기에 따라 출렁인다", coach="채명훈", captain="봉준서"),
    "가야 워리어스": dict(id="gaya", city="창원", tier="중", style="데이터 야구, 프런트 주도", coach="시우진", captain="탁민철"),
    "한별 코메츠": dict(id="hanbyeol", city="대전", tier="중", style="수비·주루 중심의 짜임새 야구", coach="계상훈", captain="연태규"),
    "해동 샤크스": dict(id="haedong", city="부산", tier="약", style="인기 최상위, 성적은 수년째 하위권", coach="천동욱", captain="반석호"),
    "태성 썬더스": dict(id="taesung", city="수원", tier="약", style="창단 짧은 신생 구단, 리빌딩 중 — 주인공 고향", coach="길영배", captain="우정한"),
    "대륙 웨일즈": dict(id="daeryuk", city="울산", tier="약", style="2년 연속 최하위, 전면 리빌딩", coach="모재석", captain="편상우"),
}

PLACES = {"fan_event": "구단 팬 사인회장", "cafe": "청담 카페", "pro_parking": "구장 주차장", "store_night": "숙소 앞 편의점",
          "han_river": "한강 둔치"}

# ---------- 뉴스·SNS ----------
MEDIA = {"news": ["누리스포츠", "KBA 투데이", "스포츠한울", "베이스볼 투모로우", "수원 한울신문"], "sns": "피치톡"}
STAT_NAMES = {"avg": "타율", "hr": "홈런", "rbi": "타점", "era": "평균자책점", "so": "탈삼진", "sb": "도루", "hits": "안타", "wins": "다승"}
NEWS = {
    "_note": "프로 뉴스 문장 틀 (ProFeed). 종류마다 하나를 시드로 고른다. 치환: {player} {team} {teamShort} {city} {season} {rival} {rivalTeam} {year} {rank} {line} {week} {n} {stat} {name} {leaderTeam} {text} {first} {round} {winner} {loser} {wins} {award} {salary} {title}",
    "my_hr": ["'{n}호 아치' {team} {player}, 시즌 {n}호 홈런", "{player}의 방망이가 터졌다… 시즌 {n}호", "고졸 신인 {player}, 담장 넘긴 {n}번째 타구"],
    "my_hot": ["{team} {player}, 이번 주 {line} 맹타", "'무서운 신인' {player}, 일주일 사이 {line}", "{player} 날았다… {team} 타선의 새 엔진"],
    "futures_hot": ["퓨처스 폭격 중인 {player}, 1군 콜업 임박?", "{team} 2군 {player}, 이번 주 {line}"],
    "my_cold": ["{player}, 프로의 벽… 이번 주 {line}", "'신인 성장통' {player} 침묵"],
    "callup": ["{team}, 신인 {player} 1군 등록", "{player} 1군 콜업… 감독 \"수비는 이미 1군\""],
    "demote": ["{player} 퓨처스행, 재정비 들어간다", "{team} 엔트리 변동… {player} 말소"],
    "milestone": ["[기록] {player}, {title}", "{team} {player} {title} — 팬들 환호"],
    "streak_win": ["{team} {n}연승 질주", "멈추지 않는 {team}, {n}연승"],
    "streak_loss": ["{team} {n}연패 늪", "{team}, 끝없는 {n}연패"],
    "post": ["{winner}, {loser} 꺾고 {round} 통과 ({wins})", "{round} — {winner}가 웃었다 ({wins})"],
    "champion": ["{year}년 한국시리즈 우승 {team}", "{team}, 정상에 서다"],
    "award": ["{name}({team}) {award} 수상", "[시상] {award} — {name}({team})"],
    "leader": ["[순위] {stat} 1위 {name}({leaderTeam}) {text}", "{stat} 부문 선두는 {name} — {text}"],
    "rival": ["대륙 {rival}, 시즌 {season}", "'전체 1순위' {rival} 근황: {season}"],
    "standings": ["{week}주차 순위 — 1위 {first}, {team} {rank}위", "선두 {first}… {team}은(는) {rank}위"],
    "my_award": ["{player}, {award} 영예", "[시상] {team} {player} {award}"],
    "national": ["[대표팀] {team} {player}, {title}", "태극마크 단 {player} — {title}"],
    "enlist": ["{team} {player}, {title}… \"돌아와서 더 잘하겠다\"", "[병역] {player} {title}"],
    "discharge": ["{player} {title}, {team} 복귀 준비", "돌아온 {player} — {title}"],
    "fa_stay": ["{player}, {team} 잔류 FA 계약", "'원클럽맨' {player}, {team}에 남는다"],
    "fa_move": ["[FA] {player}, {team} 이적!", "{player} 새 유니폼 — {team}"],
    "retire": ["{player}, 프로 {year}년 차 시즌을 끝으로 현역 은퇴", "'{team}의 유격수' {player}, 그라운드를 떠난다"],
    "rival_retire": ["'전체 1순위' {rival} 현역 은퇴", "라이벌의 퇴장 — {rival}, 유니폼을 벗는다"],
    "salary": ["{player}, 내년 연봉 {salary}에 사인", "{team} {player} 연봉 계약 완료 — {salary}"],
}
SNS = {
    "_note": "피치톡(가상 SNS) 글 틀. handles = 팬 아이디, posts = 종류별 글, comments = 종류별 댓글 (없으면 _), postHandles = 이야기 인물이 올리는 글의 아이디",
    "handles": ["@수원토박이", "@직관요정", "@9회말2사", "@불펜포수", "@유격수덕후", "@야구는구라다", "@외야3층", "@삼진왕아님", "@치킨과야구"],
    "postHandles": {"chae_post": "@chae_a.yoon"},
    "posts": {
        "my_hr": ["{player} 홈런 ㅋㅋㅋㅋ 이게 고졸 신인이라고?", "방금 {player} 타구 소리 들은 사람", "{player} 시즌 홈런 갱신 중 🔥"],
        "my_good": ["요즘 {player} 타석은 화장실도 못 감", "{player} 이번 주 {line} ㄷㄷ", "{team} 유격수 이제 걱정 끝"],
        "my_bad": ["{player} 언제 터지냐…", "신인한테 너무 바라지 말자 {player} 힘내", "{player} 이번 주 {line}… 2군 가서 감 잡자"],
        "callup": ["{player} 1군 왔다!!! 퓨처스 직관 간 보람", "드디어 {player} 1군 ㅠㅠ"],
        "demote": ["{player} 2군 가네… 금방 올라와라", "{player} 말소라니 아쉽다"],
        "milestone": ["{player} {title} 축하!!! 공 누가 주웠냐", "{title} 순간 직관한 사람 손"],
        "team_win": ["이번 주 {team} 미쳤다", "{team} {rank}위… 가을 냄새 난다", "오늘도 이겼다 {teamShort} 최고"],
        "team_loss": ["{team} 이번 주 뭐함", "{teamShort} 불펜 좀 어떻게 해 봐", "{rank}위… 내년을 보자"],
        "team_even": ["반타작 한 주 {team}", "{teamShort} 이번 주 무난무난"],
        "my_award": ["{player} {award} 받을 줄 알았다", "{award} {player} 🏆 수원의 자랑"],
        "national": ["{player} 대표팀 ㅠㅠ 자랑스럽다", "{title} 실화냐", "태극마크 {player} 🇰🇷"],
        "enlist": ["{player} 군대 간다… 기다릴게", "건강하게만 다녀와 {player}"],
        "discharge": ["{player} 돌아왔다!!!", "전역 축하 {player} 이제 1군 가자"],
        "fa_stay": ["{player} 남는다!! {team} 영원히", "원클럽맨 {player} 사랑해"],
        "fa_move": ["{player} 결국 {team}로… 그동안 고마웠다", "새 팀에서도 응원한다 {player}"],
        "retire": ["{player} 은퇴라니… 고마웠다 ㅠㅠ", "은퇴식 직관 간다. 마지막까지 {player}", "고교 때부터 봤는데 벌써 은퇴네"],
        "rival_retire": ["{rival} 은퇴… {player}는 아직 뛴다", "라이벌 시대가 끝났다"],
        "chae_post": ["오늘 저녁 ♥ 누군지 맞혀 보세요 #야구 #{teamShort}", "경기 끝나고 같이 ✌ 얼굴은 비밀"],
    },
    "comments": {
        "_": ["ㅋㅋㅋㅋ", "인정", "직관 가야겠다", "{teamShort} 가즈아"],
        "my_hr": ["타구 속도 실화냐", "{rival}보다 낫다 이제", "신인왕 각"],
        "my_bad": ["신인이잖아", "2군 보내자", "기다려 줘라"],
        "chae_post": ["{player} 아님?", "인플루언서랑 사귀면 성적 떨어지던데", "잘 어울린다", "프로 선수랑? 대박"],
        "my_award": ["축하해요!!", "{team}의 미래", "연봉 대박 나겠다"],
    },
}

# ---------- 기념 순간 = 상황 컷신 (docs/scenario/pro-cutscene-list.md 2장) ----------
def ms(id_, title, line, when, fame=2):
    return {"id": id_, "title": title, "line": line, "cutscene": "pro_" + id_, "when": when, "fame": fame}
MILESTONES = [
    ms("debut", "1군 데뷔", "조명 아래 처음 걸어 나가는 타석. 관중 함성이 헬멧 속에서 울린다.", {"kind": "event", "event": "debut"}, 3),
    ms("callup", "1군 콜업", "퓨처스 라커에서 짐을 싸는 밤. 휴대폰에 '내일 1군 합류' 한 줄.", {"kind": "event", "event": "callup"}),
    ms("first_hit", "프로 첫 안타", "1루 베이스를 밟고 돌아본 덕아웃. 선배가 공을 챙겨 흔든다.", {"kind": "career", "stat": "hits", "gte": 1}, 3),
    ms("first_rbi", "프로 첫 타점", "홈을 밟은 주자가 헬멧을 두드리고 지나간다.", {"kind": "career", "stat": "rbi", "gte": 1}),
    ms("first_hr", "프로 첫 홈런", "타구가 조명탑 사이로 사라진다. 베이스를 도는 동안 아무 소리도 안 들린다.", {"kind": "career", "stat": "home_runs", "gte": 1}, 5),
    ms("first_sb", "프로 첫 도루", "흙먼지 속 슬라이딩, 2루심의 두 팔이 옆으로 벌어진다.", {"kind": "career", "stat": "steals", "gte": 1}),
    ms("four_hit", "첫 4안타 경기", "네 번째 안타 뒤 1루 코치가 웃으며 고개를 젓는다.", {"kind": "game", "stat": "hits", "gte": 4}),
    ms("multi_hr", "첫 멀티 홈런", "두 번째 홈런. 덕아웃 난간에 선배들이 매달린다.", {"kind": "game", "stat": "home_runs", "gte": 2}, 4),
    ms("hr10", "시즌 10홈런", "전광판에 '10'. 은하식당 단골들이 단체 문자를 보낸다.", {"kind": "season", "stat": "home_runs", "gte": 10}, 4),
    ms("hr20", "시즌 20홈런", "20번째 타구가 넘어가고, 상대 투수가 모자를 벗는다.", {"kind": "season", "stat": "home_runs", "gte": 20}, 6),
    ms("hr30", "시즌 30홈런", "외야 관중석이 일어선다. 30.", {"kind": "season", "stat": "home_runs", "gte": 30}, 8),
    ms("hits150", "시즌 150안타", "150번째 안타 공이 덕아웃으로 굴러온다.", {"kind": "season", "stat": "hits", "gte": 150}, 6),
    ms("hits100", "통산 100안타", "구단 직원이 기념구를 받아 든다. 통산 100.", {"kind": "career", "stat": "hits", "gte": 100}, 4),
    ms("hr100", "통산 100홈런", "100번째 아치. 엄마가 관중석에서 행주 대신 응원봉을 든다.", {"kind": "career", "stat": "home_runs", "gte": 100}, 10),
    ms("post_debut", "첫 가을야구", "포스트시즌 엠블럼이 박힌 더그아웃. 공기가 다르다.", {"kind": "event", "event": "post_debut"}, 4),
    ms("ks_champion", "한국시리즈 우승", "마지막 아웃, 마운드로 달려가는 실루엣들. 하늘에 꽃가루.", {"kind": "event", "event": "ks_champion"}, 15),
    ms("roy", "신인왕", "시상식 무대 위 조명. 객석 어딘가에 강태오가 있다.", {"kind": "award", "award": "신인왕"}, 10),
    ms("gg", "골든글러브 유격수", "황금 글러브를 받아 든 손. 고1 때 끈을 다시 묶던 그 손.", {"kind": "award", "award": "골든글러브 유격수"}, 10),
    ms("mvp", "정규시즌 MVP", "MVP 트로피 너머로 은하식당 가족 테이블이 보인다.", {"kind": "award", "award": "MVP"}, 15),
    ms("ks_mvp", "한국시리즈 MVP", "샴페인 속에서 누군가 내 이름을 외친다.", {"kind": "award", "award": "한국시리즈 MVP"}, 12),
    ms("hr_king", "홈런왕", "시즌 마지막 날, 홈런 순위표 맨 위의 내 이름.", {"kind": "award", "award": "홈런왕"}, 8),
    ms("national", "첫 국가대표", "대표팀 라커에 걸린 유니폼, 등 뒤 이름 위로 태극마크가 보인다.", {"kind": "event", "event": "national"}, 6),
    ms("asian_gold", "아시안게임 금메달", "시상대 위 실루엣들, 목에 걸린 금빛. 병역 특례 소식이 덕아웃에 퍼진다.", {"kind": "event", "event": "medal_asian"}, 12),
    ms("olympic_medal", "올림픽 메달", "올림픽 경기장 조명 아래 메달을 깨무는 실루엣.", {"kind": "event", "event": "medal_olympic"}, 12),
    ms("exempt", "병역 특례", "엄마의 문자 한 줄: \"뉴스 봤다\".", {"kind": "event", "event": "exempt"}, 3),
    ms("enlist", "입대", "훈련소 정문 앞, 짧게 깎은 뒷머리. 은하식당 앞치마를 두른 엄마가 손을 흔든다.", {"kind": "event", "event": "enlist"}, 2),
    ms("discharge", "전역", "부대 정문을 나서는 실루엣, 어깨에 걸친 배트 가방.", {"kind": "event", "event": "discharge"}, 3),
    ms("fa_stay", "FA 잔류", "구단 사무실, 같은 유니폼을 다시 입는다. 메인 스토리 엔딩.", {"kind": "event", "event": "fa_stay"}, 10),
    ms("fa_move", "FA 이적", "새 구장 조명 아래 낯선 색의 유니폼. 메인 스토리 엔딩.", {"kind": "event", "event": "fa_move"}, 10),
    ms("retire", "은퇴식", "홈 플레이트 위 마지막 인사. 관중석에 은하식당 앞치마가 보인다.", {"kind": "event", "event": "retire"}, 0),
    ms("batting_title", "타격왕", "마지막 타석이 끝나고 기록원이 고개를 끄덕인다.", {"kind": "award", "award": "타격왕"}, 8),
]

# ---------- 메인 스토리 엔딩 (첫 FA, worldview 6장) — 시즌 결산 화면이 계약 뒤 한 번 보여 준다 ----------
ENDING = {
    "stay": {"title": "{proTeam}의 {player}", "lines": [
        "8년 전 계약서에 사인하던 그 사무실. 이번엔 펜을 쥔 손이 떨리지 않는다.",
        "은하식당 벽의 유니폼은 바뀌지 않는다. 엄마가 액자 유리만 새로 닦았다."]},
    "move": {"title": "새 유니폼, {proTeam}", "lines": [
        "낯선 구장, 낯선 덕아웃. 라커 이름표만 그대로다.",
        "은하식당 벽에 유니폼이 한 장 더 걸렸다. 엄마는 둘 다 내 거라고 했다."]},
}

# ---------- 은퇴식 (worldview 7장 — 은퇴식 → 은하식당이 커리어 모드 마지막 장면) — RetirementScreen 이 보여 준다 ----------
RETIRE_TEXT = {
    "ceremony": ["홈 플레이트에 등번호가 새겨진 베이스가 놓인다.", "고1 때 처음 잡은 글러브를 들고 마지막으로 유격수 자리에 선다."],
    "restaurant": ["은하식당. 벽에 걸린 유니폼들 사이로 고교 시절 사진이 한 장 더 붙었다.",
                   "엄마가 앞치마를 건넨다. 오늘부터는 이 가게 단골이 아니라 주인이다."],
    # 은퇴식에서 한마디씩 (만난 사람만, 지금 연인이 먼저)
    "voices": {
        "mom": "고생했다. 이제 밥은 엄마가 아니라 네가 차려.",
        "rival": "…수고했다. 그 3유간, 끝까지 못 뚫었다.",
        "buddy": "기록지 마지막 장 정리 끝. 평생 계약은 계속이다.",
        "neighbor": "처음부터 끝까지 다 그렸어. 마지막 장은 웃는 얼굴로 그릴게.",
        "manager": "폴더 이름 바꿨어. '끝까지'.",
        "reporter": "질문 하나만요. …행복했어요?",
        "chae": "처음엔 돈 보고 왔는데, 끝까지 남은 건 나야.",
        "scout": "고등학교 1루 관중석에서 적은 첫 줄, 아직 수첩에 있다.",
        "coach": "그 운동장에 네 사진이 걸렸다. 후배들이 매일 본다.",
    },
}

# ---------- 새 인물: 윤채아 ----------
CHAE = {"role": "인플루언서", "fixedName": "윤채아", "callFormat": "채아",
        "desc": "구독자 40만 패션·뷰티 인플루언서. 계약금 기사를 보고 먼저 다가왔다고 숨기지 않는다. 계산은 늘 내 몫이다.",
        "relLabel": "호감", "hiddenUntilMet": True, "romance": True, "place": "cafe", "contactOnMeet": True}

def card(id_, speaker, title, place, script, **cond):
    e = {"id": id_, "story": True, "free": True, "card": True}
    e.update(cond)
    e.update({"speaker": speaker, "title": title, "place": place, "script": script})
    return e

def say(sp, t):
    return {"speaker": sp, "text": t}

def narr(t):
    return {"text": t}

def choice(label, hint, effects=None, then=None, **extra):
    c = {"label": label, "hint": hint}
    c.update(extra)
    if effects:
        c["effects"] = effects
    if then:
        c["then"] = then
    return c

CHAE_END = ["chae_end"]
EVENTS = [
    # 2학년 서지안 고백 (하린·소율 갈래와 같은 틀)
    card("y2_side_jian_answer", "reporter", "중계석", "stands", [
        narr("텅 빈 야구장 중계석. 지안이 카메라 렌즈 뚜껑을 닫는다."),
        say("reporter", "오늘은 안 찍어요. 질문 하나만 할게요. …대답은요?"),
        {"choices": [
            choice("나도 너 좋아해.", "서지안과 사귄다 (프로 편까지 이어진다)", {"partner": "reporter", "rel": {"reporter": 10}},
                   [say("reporter", "녹화 안 해서 다행이다. 나 지금 얼굴 엄청 빨개.")]),
            choice("지금은 야구만 볼래.", "서지안 호감 ▼ · 컨디션 ▲", {"rel": {"reporter": -8}, "condition": 5},
                   [say("reporter", "그 대답도 좋아요. 기사 쓰기엔.")]),
        ]},
    ], scheduledOnly=True, timing="start", grade=[2, 3], partner="none"),
    # 프로: 윤채아 — 돈을 보고 다가온 사람
    card("pro_side_chae_meet", "chae", "팬 사인회", "fan_event", [
        {"do": {"meet": ["chae"]}},
        narr("구단 팬 사인회가 끝날 무렵. 줄 맨 끝에 사인지 없이 선 사람이 있다."),
        say("chae", "사인 말고 번호요. 계약금 기사 봤어요. …돌려 말하는 거 싫어해서."),
        {"choices": [
            choice("(번호를 준다)", "윤채아와 연락한다 · 호감 ▲", {"rel": {"chae": 8}, "flag": "chae_contact", "addContact": "chae"},
                   [say("chae", "밥은 오빠가 사요. 대신 사진은 제가 예쁘게 찍어 줄게.")]),
            choice("사인만 해 드릴게요.", "평판 ▲ · 인기 ▲ · 윤채아 이야기는 여기까지", {"reputation": 2, "fame": 1, "rel": {"chae": -5}, "flag": "chae_end"},
                   [say("chae", "…재밌네. 다들 번호 주던데.")]),
        ]},
    ], proYear=[1, 4], timing="after", requires=["pro_debut"], minMoney=30000000),
    card("pro_side_chae_partner", "chae", "주차장", "pro_parking", [
        narr("경기 뒤 선수 주차장. 채아가 내 차 옆에 서 있다."),
        say("chae", "{partnerName} 씨랑 사귄다며? 기사 봤어. …그래도 밥 한 번은 괜찮잖아."),
        {"choices": [
            choice("만나는 사람 있어. 미안.", "지금 연인 호감 ▲ · 윤채아 이야기 끝", {"rel": {"partner": 8, "chae": -20}, "flag": "chae_end"},
                   [say("chae", "…부럽다, 그 사람.")]),
            choice("밥만 먹자.", "윤채아 호감 ▲ · 지금 연인 호감 ▼", {"rel": {"chae": 8, "partner": -10}, "flag": "chae_dinner"},
                   [say("chae", "밥만. 약속.")]),
        ]},
    ], proYear=[1, 4], timing="start", requires=["chae_contact"], forbids=["chae_dinner", "chae_end"], partner="any"),
    card("pro_side_chae_truth", "chae", "새벽 편의점", "store_night", [
        narr("새벽 한 시, 숙소 앞 편의점. 화장을 지운 채아가 컵라면 앞에 앉아 있다."),
        say("chae", "처음엔 돈 보고 연락한 거 맞아. 엄마 가게에… 빚이 좀 있어."),
        say("chae", "근데 요즘은 오빠 경기만 보게 돼. 그게 무서워."),
        {"choices": [
            choice("돈 말고, 나를 봐 줄 수 있어?", "윤채아와 진심으로 시작한다 (사귄다)", {"partner": "chae", "rel": {"chae": 10}},
                   [say("chae", "…이번엔 내가 계산할게. 편의점이지만.")]),
            choice("빚은 내가 갚아 줄게.", "돈 ▼▼ (5천만 원) · 호감 ▲ · 그 뒤는…", {"rel": {"chae": 15}, "flag": "chae_paid"},
                   [say("chae", "…고마워. 진짜로. 근데 오빠, 그게 제일 무서운 말이야.")], cost=50000000),
            choice("여기까지 하자.", "컨디션 ▲ · 윤채아 이야기 끝", {"condition": 8, "rel": {"chae": -20}, "flag": "chae_end"},
                   [say("chae", "그래. 오빠는 그럴 줄 알았어. …경기 잘해.")]),
        ]},
    ], proYear=[1, 5], timing="start", requires=["chae_dinner"], forbids=CHAE_END, minRel={"chae": 30}, partner="none"),
    # 병역 결정 (시즌 결산, 만 22세부터 해마다 — 미루기는 27세 전까지). 입대는 다음 시즌 시작에
    card("pro_military_card", "mom", "입대 통지", "restaurant", [
        {"branch": [{"if": {"var": "military_last", "eq": True}, "then": [narr("병무청 우편. 더는 미룰 수 없다.")]},
                    {"then": [narr("은하식당 계산대 옆에 병무청 우편이 놓여 있다.")]}]},
        say("mom", "상무 간 선수들은 거기서도 야구 한다더라. …엄마는 네가 정해."),
        {"choices": [
            choice("상무에 지원한다", "국군체육부대 — 상무 팀으로 뛰며 복무 (1군 40경기 또는 감독 신뢰 45 필요)", {"military": "sangmu"},
                   [say("mom", "합격하면 엄마가 떡 돌린다.")]),
            choice("현역으로 간다", "1년 반 야구 공백 · 능력치가 조금 녹슨다", {"military": "active"},
                   [say("mom", "…밥은 잘 먹고 다녀.")]),
            choice("1년 미룬다", "아시안게임·올림픽 메달이면 면제 · 내년 결산에 다시 묻는다", {"set": {"military_delayed": True}},
                   [say("mom", "그래. 대신 내년엔 진짜 정해."), ], **{"if": {"var": "military_last", "eq": False}}),
        ]},
    ], proYear=[1, 30], timing="seasonEnd", repeat=True, cooldownWeeks=10,
        when={"all": [{"var": "military_ask", "eq": True}, {"var": "military_status", "eq": "none"}, {"not": {"flag": "retired"}}]}),
    # 은퇴 (시즌 결산, 만 34세부터 해마다 — 42세·방출이면 은퇴뿐)
    card("pro_retire_card", "mom", "은퇴", "restaurant", [
        {"branch": [{"if": {"var": "retire_released", "eq": True}, "then": [narr("구단 사무실. 재계약 서류 대신 악수가 나왔다.")]},
                    {"if": {"var": "retire_forced", "eq": True}, "then": [narr("몸이 먼저 안다. 이번 겨울이 마지막이다.")]},
                    {"then": [narr("시즌이 끝난 밤, 은하식당. 엄마가 앞치마를 벗는다.")]}]},
        say("mom", "그만해도 돼. 더 해도 되고. 엄마는 어느 쪽이든 좋아."),
        {"choices": [
            choice("은퇴를 선언한다", "커리어를 마친다 — 은퇴식과 마지막 장면", {"retire": True},
                   [say("mom", "…그래. 마지막 경기는 엄마가 맨 앞에서 볼게.")]),
            choice("한 시즌 더 뛴다", "다음 시즌도 — 내년 결산에 다시 묻는다", {"set": {"retire_delayed": True}},
                   [say("mom", "그럼 내년에도 도시락 싼다."), ], **{"if": {"var": "retire_forced", "eq": False}}),
        ]},
    ], proYear=[1, 40], timing="seasonEnd", repeat=True, cooldownWeeks=10,
        when={"all": [{"var": "retire_ask", "eq": True}, {"not": {"flag": "retired"}}]}),
    card("pro_side_chae_return", "chae", "한강 둔치", "han_river", [
        narr("1년 만의 한강. 채아가 편의점 봉투 두 개를 들고 온다."),
        say("chae", "빚 다 정리했어. 이번엔 진짜 내 돈으로 샀어. 삼각김밥이지만."),
        {"choices": [
            choice("이번엔 내가 기다린 거야.", "윤채아와 사귄다", {"partner": "chae", "rel": {"chae": 10}},
                   [say("chae", "알아. 그래서 왔어.")]),
            choice("친구로 지내자.", "윤채아 호감 ▼ · 평판 ▲", {"rel": {"chae": -10}, "reputation": 2},
                   [say("chae", "…응. 그것도 좋아. 진짜로.")]),
        ]},
    ], proYear=[2, 9], scheduledOnly=True, timing="start", partner="none"),
]

def msg(id_, speaker, lines, replies=None, **cond):
    m = {"id": id_, "speaker": speaker}
    m.update(cond)
    m["lines"] = lines
    if replies:
        m["replies"] = replies
    return m

def rep(label, effects=None, then=None):
    r = {"label": label}
    if effects:
        r["effects"] = effects
    if then:
        r["then"] = then
    return r

MESSAGES = [
    # ---- 2학년: 서지안 고백 (하린·소율과 같은 틀) ----
    msg("m_y2_jian_confess", "reporter", ["오늘 영상 편집하다가 알았어요", "네 타석만 세 번씩 돌려 보고 있더라고요, 내가"],
        [rep("무슨 뜻이야?", {"schedule": {"event": "y2_side_jian_answer", "weeks": 1}}, ["토요일에 중계석으로 와요. 거기서 말할게요"]),
         rep("편집 힘들지?", {"rel": {"reporter": -2}}, ["…네. 힘들어요"])],
        grade=[2, 3], requires=["jian_hello"], minRel={"reporter": 45}, after=["ep12_02"], partner="none"),
    # ---- 3학년 ----
    msg("m_y3_harin_exam", "neighbor", ["실기 날짜 나왔어. 네 백호기랑 같은 주야", "…둘 다 잘하자"],
        [rep("둘 다 이긴다", {"rel": {"neighbor": 4}, "condition": 3}, ["응. 약속"]), rep("시험 끝나면 보러 와", {"rel": {"neighbor": 2}}, ["결승까지 가 있어"])],
        grade=[3, 3], partner="neighbor", after=["ep16_01"]),
    msg("m_y3_harin_pass", "neighbor", ["합격했어", "…네 이름 불리는 날도 옆에서 보고 싶어"],
        [rep("은하식당 TV 앞으로 와", {"rel": {"neighbor": 5}}, ["엄마 몰래 갈게 ㅋㅋ"]), rep("축하해. 진짜로", {"rel": {"neighbor": 3}, "condition": 3}, ["너도. 다음은 너야"])],
        grade=[3, 3], partner="neighbor", after=["ep19_01"]),
    msg("m_y3_harin_friend", "neighbor", ["결승 간다며. 미술학원에서 다 같이 중계 틀어 놨어", "그림 그리는 척하면서 보는 중"],
        grade=[3, 3], partner="none", after=["ep18_01"]),
    msg("m_y3_soyul_college", "manager", ["대학 첫 수업 째고 너 개막전 보러 간다", "삼각대 챙겼어"],
        [rep("수업은 들어", {"rel": {"manager": 2}}, ["싫은데 ㅋ"]), rep("1루 쪽에 있어", {"rel": {"manager": 4}, "condition": 3}, ["1루 맨 앞줄. 늘 거기"])],
        grade=[3, 3], partner="manager", after=["ep15_01"]),
    msg("m_y3_soyul_rumor", "manager", ["찌라시 봤어. 걱정돼서", "네 영상 3년 치 다 있어. 필요하면 말해"],
        [rep("고마워요 선배", {"rel": {"manager": 3}, "condition": 4}, ["선배 소리 이제 안 해도 돼"]), rep("괜찮아요", {"condition": 2}, ["안 괜찮으면 전화해"])],
        grade=[3, 3], after=["ep17_01"]),
    msg("m_y3_jian_final", "reporter", ["결승 중계석 자리 받았어요. 이번엔 진짜 중계석", "첫 타석 멘트 미리 써 놨어요"],
        [rep("뭐라고 썼는데", {"rel": {"reporter": 3}}, ["비밀. 치면 알려 줄게요"]), rep("부담 주지 마", {"condition": 2}, ["ㅋㅋ 알겠어요"])],
        grade=[3, 3], after=["ep18_01"]),
    msg("m_y3_junsu_path", "buddy", ["나 서호대 간다. 선수 말고 기록원으로", "너 프로 가면 네 기록은 내가 다 정리해 줄게"],
        [rep("그럼 평생 계약이다", {"rel": {"buddy": 5}}, ["연봉은 치킨으로 받는다"]), rep("아깝다 너", {"rel": {"buddy": 3}}, ["난 이게 좋아"])],
        grade=[3, 3], after=["ep19_01"]),
    msg("m_y3_mom_wall", "mom", ["오늘 단골들이 네 기사 오려서 계산대 옆에 붙였어", "밥은 먹고 다니지?"],
        grade=[3, 3], after=["ep17_01"]),
    # ---- 프로: 가족·친구 ----
    msg("m_pro_mom_wall", "mom", ["가게 벽에 네 유니폼 걸었다", "손님들이 사진 찍고 가"],
        [rep("사인 볼 더 보낼게", {"rel": {"mom": 4}, "fame": 1}, ["열 개만"]), rep("부끄러워 엄마", {"rel": {"mom": 2}}, ["엄마는 하나도 안 부끄러워"])],
        proYear=[1, 1], requires=["pro_debut"]),
    msg("m_pro_mom_rent", "mom", ["월세 다 냈다", "…고맙다는 말은 안 한다. 엄마니까"], proYear=[1, 1], requires=["restaurant_saved"]),
    msg("m_pro_junsu_debut", "buddy", ["데뷔전 기록지 정리했다 ㅋㅋ", "다음엔 안타 칸 좀 채워라"],
        [rep("다음 주에 채운다", {"rel": {"buddy": 3}, "condition": 2}, ["캡처해 둔다"]), rep("기록원 다 됐네", {"rel": {"buddy": 3}}, ["서호대 에이스 기록원이다"])],
        proYear=[1, 2], requires=["pro_debut"]),
    msg("m_pro_taeo", "rival", ["…1군 왔네"],
        [rep("너도.", {"rel": {"rival": 5}}, ["신인왕은 하나다"]), rep("신인왕은 내 거야", {"rel": {"rival": 8}, "condition": -2}, ["말은 결승 때도 했지"])],
        proYear=[1, 2], requires=["pro_debut"]),
    msg("m_pro_taeo_roy", "rival", ["축하한다", "내년엔 골든글러브다. 유격수는 한 명이다"], proYear=[1, 3], requires=["ms_roy"]),
    msg("m_pro_scout", "scout", ["나성만이다. 고등학교 때 1루 관중석", "1군 첫 안타 봤다. 그 공 버리지 마라"],
        [rep("수첩에 적어 두세요", {"rel": {"scout": 5}, "reputation": 1}, ["벌써 적었다"]), rep("감사합니다", {"rel": {"scout": 3}}, ["감사는 은퇴할 때 해라"])],
        proYear=[1, 3], requires=["ms_first_hit"]),
    # ---- 프로: 병역 · 대표팀 · FA ----
    msg("m_pro_mom_enlist", "mom", ["입대 날짜 나왔다며", "그날은 가게 문 닫고 엄마가 데려다줄게"],
        [rep("고마워 엄마", {"rel": {"mom": 4}}, ["고맙긴. 머리는 엄마가 깎아 줄까"]), rep("혼자 갈게", {"rel": {"mom": -1}, "condition": 2}, ["…그래. 그럼 문 앞까지만"])],
        proYear=[1, 30], requires=["enlisted"]),
    msg("m_pro_harin_enlist", "neighbor", ["면회 갈게", "그림 그려서. 네 부대 그려 줄게"],
        [rep("기다릴게", {"rel": {"neighbor": 5}, "condition": 3}, ["나도"]), rep("멀어. 오지 마", {"rel": {"neighbor": -3}}, ["갈 거야"])],
        proYear=[1, 30], partner="neighbor", requires=["enlisted"]),
    msg("m_pro_mom_exempt", "mom", ["뉴스 봤다", "엄마 오늘 가게에서 울었어. 손님들이 다 박수 쳤다"], proYear=[1, 30], requires=["military_exempt"]),
    msg("m_pro_taeo_national", "rival", ["대표팀 같은 방이다", "코 골지 마라"],
        [rep("너나 골지 마", {"rel": {"rival": 5}}, ["…안 곤다"]), rep("유격수는 누가 봐?", {"rel": {"rival": 8}, "condition": -2}, ["감독이 정하겠지. 나다"])],
        proYear=[1, 30], requires=["national_asian"]),
    msg("m_pro_taeo_retire", "rival", ["나 먼저 간다", "유격수는 이제 네 거다. …골든글러브 하나는 더 받아라"],
        [rep("수고했다, 태오", {"rel": {"rival": 5}, "condition": 3}, ["…은퇴식엔 와라"]), rep("아직 안 끝났어", {"rel": {"rival": 3}, "fame": 1}, ["그래. 그 말 할 줄 알았다"])],
        proYear=[1, 40], requires=["taeo_retired"]),
    msg("m_pro_junsu_discharge", "buddy", ["전역 축하한다 ㅋㅋ", "네 퓨처스 기록 정리해 놨다. 1군까지 몇 경기 남았는지도"],
        [rep("몇 경기 남았는데", {"rel": {"buddy": 3}, "condition": 3}, ["감독님 마음이지 뭐. 잘 쳐"]), rep("고맙다 기록원", {"rel": {"buddy": 3}}, ["평생 계약이라며"])],
        proYear=[1, 30], requires=["discharged"]),
    msg("m_pro_mom_fa", "mom", ["계약 기사 봤다", "어디서 뛰든 은하식당 벽엔 네 유니폼 걸려 있을 거야"],
        [rep("엄마 가게 넓히자", {"rel": {"mom": 6}, "money": -30000000}, ["…이번 한 번만 받는다"]), rep("고마워", {"rel": {"mom": 3}}, ["밥 먹으러 와"])],
        proYear=[1, 30], requires=["fa_signed"], minMoney=30000000),
    # ---- 프로: 연인 (고교에서 이어진 사람) ----
    msg("m_pro_harin_debut", "neighbor", ["데뷔 축하해", "TV로 봤어. 유니폼이 좀 커 보이더라 ㅋㅋ"],
        [rep("금방 맞게 된다", {"rel": {"neighbor": 3}, "condition": 3}, ["그럼 그 유니폼 그려 줄게"]), rep("직관 와", {"rel": {"neighbor": 4}}, ["주말에 갈게. 3루 쪽"])],
        proYear=[1, 2], partner="neighbor", requires=["pro_debut"]),
    msg("m_pro_harin_hr", "neighbor", ["첫 홈런 그림으로 그렸어", "학교 과제로 냈더니 교수님이 야구 좋아하신대"],
        [rep("액자 해 줘", {"rel": {"neighbor": 4}}, ["은하식당 벽에 걸자"]), rep("A+ 받아", {"rel": {"neighbor": 2}, "condition": 2}, ["너도 홈런 더 쳐"])],
        proYear=[1, 5], partner="neighbor", requires=["ms_first_hr"]),
    msg("m_pro_harin_far", "neighbor", ["원정 길지? 나 과제 마감이라 이번 주 못 봐", "대신 경기 끝나면 전화해"],
        [rep("꼭 전화할게", {"rel": {"neighbor": 4}, "condition": 3}, ["기다릴게"]), rep("피곤해서 못 할 수도", {"rel": {"neighbor": -4}}, ["…그래"])],
        proYear=[1, 9], week=[10, 20], partner="neighbor", ignore={"rel": {"neighbor": -5}}),
    msg("m_pro_soyul_folder", "manager", ["프로 타석 폴더 새로 만들었어", "이름은 '101호부터'"],
        [rep("또 찍어 줘", {"rel": {"manager": 4}, "condition": 2}, ["안 시켜도 찍어"]), rep("이번엔 내가 볼게", {"rel": {"manager": 2}, "form": 0.5}, ["비밀번호는 그대로야"])],
        proYear=[1, 2], partner="manager", requires=["pro_debut"]),
    msg("m_pro_soyul_data", "manager", ["너 몸쪽 높은 직구에 배트 늦어. 세 경기 연속", "분석 동아리 애들이랑 확인했어"],
        [rep("고칠게", {"rel": {"manager": 3}, "form": 0.5}, ["다음 주에 다시 볼게"]), rep("알아, 일부러야", {"rel": {"manager": -2}}, ["…거짓말"])],
        proYear=[1, 9], week=[6, 18], partner="manager"),
    msg("m_pro_jian_intern", "reporter", ["누리스포츠 인턴 붙었어요", "첫 취재가 너면 공정성 문제래요 ㅋㅋ"],
        [rep("그럼 두 번째로 해", {"rel": {"reporter": 4}}, ["질문 미리 안 보낸다?"]), rep("축하해", {"rel": {"reporter": 3}, "fame": 1}, ["너도. 데뷔 축하"])],
        proYear=[1, 2], partner="reporter", requires=["pro_debut"]),
    msg("m_pro_jian_hr", "reporter", ["첫 홈런 인터뷰, 질문 하나만", "…무서웠어?"],
        [rep("응, 많이", {"rel": {"reporter": 5}, "fame": 1}, ["그게 제일 좋은 대답이에요"]), rep("아니, 재밌었어", {"fame": 2}, ["제목 그걸로 할게요"])],
        proYear=[1, 5], partner="reporter", requires=["ms_first_hr"]),
    # ---- 프로: 혼자일 때 옛 친구들 ----
    msg("m_pro_harin_friend", "neighbor", ["데뷔 축하해. 엄마가 은하식당에서 다 같이 봤대"], proYear=[1, 2], partner="none", requires=["pro_debut"]),
    msg("m_pro_soyul_friend", "manager", ["프로 데뷔 영상 내가 찍었다 ㅋㅋ 3루 쪽 맨 앞줄", "조회수 30만"], proYear=[1, 2], partner="none", requires=["pro_debut"]),
    msg("m_pro_jian_friend", "reporter", ["인턴 기자 됐어요. 첫 기사 주인공 해 줄래요?"],
        [rep("좋아", {"rel": {"reporter": 3}, "fame": 2}, ["질문은 미리 안 보내요"]), rep("다음에", {"rel": {"reporter": -1}}, ["기다릴게요"])],
        proYear=[1, 3], partner="none", requires=["pro_debut"]),
    # ---- 프로: 윤채아 (돈을 보고 다가온 사람 → 진심일까) ----
    msg("m_pro_chae_dinner", "chae", ["오빠 이번 주 홈경기 끝나고 시간 돼요?", "청담에 새로 생긴 오마카세. 예약은 제가 할게요", "계산은 오빠가 ✌"],
        [rep("좋아", {"money": -800000, "rel": {"chae": 8}, "flag": "chae_dinner"}, ["사진 잘 나왔다. 올려도 돼요?"]),
         rep("시즌 중이라 어려워", {"rel": {"chae": -4}, "condition": 2}, ["프로는 다 바쁘구나~"])],
        proYear=[1, 4], requires=["chae_contact"], forbids=["chae_dinner", "chae_end"], partner="none", minMoney=800000),
    msg("m_pro_chae_post", "chae", ["오빠랑 찍은 사진 스토리에 올렸어요", "얼굴은 가렸는데 팬들은 다 알아보네 ㅋㅋ"],
        [rep("내려 줘", {"rel": {"chae": -4}, "reputation": 2, "flag": "chae_boundary"}, ["…알겠어요. 생각보다 깐깐하네"]),
         rep("괜찮아", {"rel": {"chae": 5}, "fame": 3, "reputation": -2, "proSns": "chae_post"}, ["역시 ㅎㅎ 좋아요 벌써 2만"])],
        proYear=[1, 4], requires=["chae_dinner"], forbids=CHAE_END),
    msg("m_pro_chae_bag", "chae", ["오빠 이거 봐요", "다음 달이 제 생일인데… 이 가방 너무 예쁘지 않아요?", "480만원 ㅎㅎ"],
        [rep("사 줄게", {"money": -4800000, "rel": {"chae": 10}, "flag": "chae_bag"}, ["진짜? 오빠 최고 ♥"]),
         rep("생일엔 밥 살게", {"rel": {"chae": -5}, "flag": "chae_boundary"}, ["…밥. 알겠어요"])],
        proYear=[1, 4], requires=["chae_dinner"], forbids=CHAE_END, minRel={"chae": 20}, minMoney=4800000, ignore={"rel": {"chae": -6}}),
    msg("m_pro_chae_car", "chae", ["오빠 차 바꿀 생각 없어요?", "프로 선수가 그 차 타면 좀 그렇잖아요. 같이 보러 가요"],
        [rep("보러 가자", {"money": -30000000, "rel": {"chae": 8}, "reputation": -3, "flag": "chae_car"}, ["계약은 오빠 이름으로. 저는 조수석 ✌"]),
         rep("차는 됐어", {"rel": {"chae": -6}, "flag": "chae_boundary"}, ["재미없어…"])],
        proYear=[1, 5], requires=["chae_bag"], forbids=CHAE_END, minMoney=30000000),
    msg("m_pro_chae_gone", "chae", ["오빠, 그 돈으로 정리 다 했어", "…미안. 당분간 연락하지 말자. 오빠한테 빚진 사람으로 옆에 있기 싫어"],
        [rep("기다릴게", {"rel": {"chae": 5}, "schedule": {"event": "pro_side_chae_return", "weeks": 20}}, ["…바보"]),
         rep("알겠어", {"condition": -5, "flag": "chae_end"}, ["고마웠어. 그건 진심이었어"])],
        proYear=[1, 5], requires=["chae_paid"], forbids=["chae_end"]),
    msg("m_pro_chae_cheer", "chae", ["오늘 직관 왔어. 3루 쪽", "홈런 치면 라이브 켤 거야 ㅋㅋ"],
        [rep("보고 있어", {"condition": 5, "rel": {"chae": 3}}, ["♥"]), rep("라이브는 하지 마", {"rel": {"chae": -2}, "reputation": 1}, ["치사해"])],
        proYear=[1, 9], partner="chae"),
    msg("m_pro_chae_live", "chae", ["오빠 팬들이 내 라이브 와서 오빠 얘기만 해 ㅋㅋ", "이제 내가 오빠 덕 보네"],
        [rep("같이 한 번 켜자", {"fame": 3, "rel": {"chae": 4}, "reputation": -1}, ["진짜? 공지 올린다!"]), rep("네 방송이잖아", {"rel": {"chae": 2}, "reputation": 1}, ["…그런 말 처음 들어 봐"])],
        proYear=[1, 9], partner="chae", requires=["chae_dinner"]),
]


def main():
    c = json.loads(CONTENT.read_text(encoding="utf-8"))
    for t in c["proTeams"]:
        t.update(TEAMS[t["name"]])
    c["pro"] = {"_note": "프로 편 (scripts/pro_content.py 가 쓴다). media 매체, statNames 부문 이름, news·sns 문장 틀 (ProFeed), milestones = 상황 컷신 (Milestones — 그림이 없으면 컷신 자리 카드)",
                "ending": ENDING, "retireText": RETIRE_TEXT, "media": MEDIA, "statNames": STAT_NAMES, "news": NEWS, "sns": SNS, "milestones": MILESTONES}
    c["people"]["chae"] = CHAE
    for k, v in PLACES.items():
        c["places"][k] = v
    ids = {m["id"] for m in MESSAGES}
    c["messages"] = [m for m in c["messages"] if m["id"] not in ids] + MESSAGES
    eids = {e["id"] for e in EVENTS}
    c["events"] = [e for e in c["events"] if e["id"] not in eids] + EVENTS
    CONTENT.write_text(json.dumps(c, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("프로 편: 구단 %d · 기념 순간 %d · 문자 %d · 카드 %d" % (len(TEAMS), len(MILESTONES), len(MESSAGES), len(EVENTS)))


if __name__ == "__main__":
    main()
