#!/usr/bin/env python3
"""시나리오 문서(docs/scenario/*.md) → config/content.json 의 이야기 이벤트·변수.

문서가 원본이다. 대사·지문 글자는 문서에서 그대로 옮기고(손으로 고치지 않는다), 이 스크립트에는
"문서에 없는 것"만 적는다: 장면을 어느 주·어느 시점에 띄울지(EVENT_META), 게임플레이 구간을
엔진의 무엇으로 이을지(HOOKS — 체력 테스트 종목, 연습 drill, 경기 대본 gamePlan), 장소(PLACES), 화자 id(SPEAKERS).

문서를 고치면 다시 실행한다:  python3 scripts/build_scenario.py
  → content.json 의 events 중 id 가 ep 로 시작하는 것과 vars 를 새로 쓴다 (다른 이벤트는 그대로).

문서 문법 (docs/scenario 0장): SCENE · NARR · 화자: 대사 · CHOICE id: (- "말" -> 효과 ; goto A) · A: …
IF / ELIF / ELSE / END · UI 종류: · AUTO: · ITEM_SHOW · SYSTEM · GAMEPLAY (들여쓰기 2칸)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC = ROOT / "docs/scenario/scenario_y1_ep01-08.md"
CONTENT = ROOT / "config/content.json"

# ---------- 문서 화자 id → 게임 인물 id (또는 인물이 아닌 화자의 한글 이름) ----------
SPEAKERS = {
    "player": "player", "harin": "neighbor", "soyul": "manager", "junsu": "buddy", "coach_oh": "coach",
    "senior_a": "2학년 선배", "taeo": "rival", "cheongun_coach": "청운고 감독", "cheongun_player": "청운고 선수",
    "umpire": "심판", "first_base_coach": "1루 코치", "minjae": "minjae", "doyun": "senior", "seongho": "seongho",
    "jaehyuk": "jaehyuk", "mom": "mom", "na_scout": "scout",
}
# 처음 말할 때 만나는(인물 탭에 열리는) 인물
HIDDEN = {"manager", "neighbor", "rival", "scout", "minjae", "jaehyuk", "seongho"}

UI_KINDS = {"phone_message": "phone", "laptop_folder": "laptop", "tv": "tv", "whiteboard": "whiteboard",
            "whiteboard_stats": "whiteboard", "scoreboard_lineup": "scoreboard", "phone_news_notification": "news"}

# ---------- 변수 (문서 2장 + 회차별 결과 변수 + 선택 지점 id) ----------
GRADE3 = ["high", "mid", "low"]
VARS = {
    "harin_affinity": {"type": "int"}, "soyul_affinity": {"type": "int"}, "minjae_trust": {"type": "int"},
    "stats.at_bat_video_count": {"type": "int"}, "stats.fielding_video_count": {"type": "int"},
    "test_speed_grade": {"type": "enum", "values": GRADE3}, "test_squat_grade": {"type": "enum", "values": GRADE3},
    "test_reaction_grade": {"type": "enum", "values": GRADE3}, "test_overall_grade": {"type": "enum", "values": GRADE3},
    "warmup_effect": {"type": "enum", "values": ["calm", "eager"]},
    "taeo_interest": {"type": "bool"}, "taeo_first_talk": {"type": "bool"}, "taeo_ball_fielded": {"type": "bool"},
    "first_ab_result": {"type": "enum", "values": ["hit", "out", "strikeout"]},
    "seongho_tee": {"type": "bool"}, "doyun_talk": {"type": "bool"}, "team_goal": {"type": "string"},
    "ep06_game_result": {"type": "enum", "values": ["win", "lose"]}, "dongin_first_win": {"type": "bool"},
    "form_answer": {"type": "enum", "values": ["immediate", "question"]}, "form_adaptation": {"type": "int"},
    "training_direction": {"type": "enum", "values": ["contact", "power", "defense_run"]},
    "soyul_restaurant_invite": {"type": "bool"},
    "scout_interest": {"type": "enum", "values": ["checked", "watching", "none"]},
    "scout_visited_teams": {"type": "list"}, "school_year": {"type": "int", "default": 1},
    "phone_contacts": {"type": "bool"}, "phone_records_team": {"type": "bool"}, "phone_profile_taeo": {"type": "bool"},
    "phone_records_personal": {"type": "bool"}, "phone_news": {"type": "bool"}, "phone_records_season": {"type": "bool"},
    "scout_tab_unlocked": {"type": "bool"},
    # 회차 안에서만 쓰는 결과 변수
    "ep04_def_result": {"type": "enum", "values": ["success", "error"]},
    "ep05_ab_result": {"type": "enum", "values": ["hit", "out", "strikeout"]},
    "ep05_def_result": {"type": "enum", "values": ["success", "error"]},
    "ep06_dp_practice_success": {"type": "int"},
    "ep06_dp_result": {"type": "enum", "values": ["success", "fail"]},
    "ep06_season_avg_grade": {"type": "enum", "values": GRADE3},
    "ep08_ab1_result": {"type": "enum", "values": ["hit", "walk", "out", "strikeout"]},
    "ep08_def_result": {"type": "enum", "values": ["double_play", "out", "error"]},
    "ep08_ab2_result": {"type": "enum", "values": ["rbi_hit", "extra_base", "out", "strikeout"]},
    "ep08_ab3_result": {"type": "enum", "values": ["hit", "out", "strikeout"]},
    "ep08_performance": {"type": "int"},
}
NOTE_VARS = ("시나리오 변수 (docs/scenario 2장의 ID 그대로 — scripts/build_scenario.py 가 쓴다). type: int|bool|enum|string|list, "
             "values(enum), default. 이벤트 효과 set{변수: 값}·add{변수: n}, 대본 branch·선택지 if·이벤트 when 의 조건 {var, eq|ne|gte|lte|gt|lt|in}. "
             "선택 지점 id 도 변수다 (고른 선택지의 A·B·C). 대사 속 {변수} 는 값으로 바뀐다")

# ---------- 장소 (SCENE → places id) ----------
PLACES = {
    "ep01_01": "road", "ep01_02": "clubroom", "ep01_03": "clubroom", "ep01_04": "field", "ep01_05": "field",
    "ep01_06": "field", "ep01_07": "bedroom",
    "ep02_01": "field", "ep02_02": "clubroom", "ep02_03": "dugout", "ep02_04": "bus", "ep02_05": "playground",
    "ep03_01": "cheongun_field", "ep03_02": "cheongun_field", "ep03_03": "dugout", "ep03_04": "cheongun_field",
    "ep03_05": "sink", "ep03_06": "bus", "ep03_07": "bedroom",
    "ep04_01": "clubroom", "ep04_02": "store", "ep04_03": "dugout", "ep04_04": "stadium", "ep04_05": "stadium",
    "ep04_06": "dugout", "ep04_07": "bedroom",
    "ep05_01": "dugout", "ep05_02": "cage", "ep05_03": "bus", "ep05_04": "stadium", "ep05_05": "stadium",
    "ep05_06": "dugout", "ep05_07": "hallway", "ep05_08": "bus", "ep05_09": "convenience",
    "ep06_01": "field", "ep06_02": "field", "ep06_03": "restaurant", "ep06_04": "stadium", "ep06_05": "dugout",
    "ep06_06": "field", "ep06_07": "clubroom", "ep06_08": "bedroom",
    "ep07_01": "clubroom", "ep07_02": "cage", "ep07_03": "restaurant", "ep07_04": "clubroom", "ep07_05": "field",
    "ep07_06": "cage", "ep07_07": "clubroom", "ep07_08": "bedroom",
    "ep08_01": "seoho_field", "ep08_02": "dugout", "ep08_03": "seoho_field", "ep08_04": "stands",
    "ep08_05": "restaurant", "ep08_06": "clubroom", "ep08_07": "bedroom",
}
NEW_PLACES = {"road": "학교 가는 길", "clubroom": "야구부실", "bedroom": "내 방", "playground": "아파트 놀이터",
              "cheongun_field": "청운고 야구장", "sink": "수돗가", "store": "매점", "cage": "배팅 케이지",
              "hallway": "야구장 복도", "convenience": "아파트 앞 편의점", "restaurant": "은하식당", "seoho_field": "서호대 야구장"}

# ---------- 경기 대본 (gamePlan) — 문서의 경기 상황을 엔진 장면으로 ----------
HITS = {"single": "hit", "double": "hit", "triple": "hit", "home_run": "hit"}
OUTS3 = dict(HITS, strikeout="strikeout", walk="hit", **{"_": "out"})  # 볼넷은 문서에 없는 결과라 출루(hit)로 본다
FIELD2 = {"bonus": "success", "normal": "success", "_": "error"}
POWER_XB = {"if": {"var": "training_direction", "eq": "power"}, "then": "extra_base", "else": "rbi_hit"}
PLANS = {
    # EP02 동인고전: 관전 (결과 고정, 1-10 8회 콜드 — 2회 3점, 4회 3점, … 8회 말 1점)
    "ep02_02": {"matchOverride": {"score": [1, 10], "innings": 8, "home": False,
                                  "line": [[0, 0, 0, 0, 1, 0, 0, 0], [0, 3, 0, 3, 0, 1, 2, 1]]}},
    # EP03 청운고 연습경기 (7이닝): 7회 말 2사 주자 없음 대수비 — 강태오 타구
    "ep03_02": {"gamePlan": {"opponent": "cheongun", "practice": True, "innings": 7, "role": "sub",
                             "line": [[0, 0, 0, 0, 1, 0, 0], [2, 0, 1, 3, 0, 2, 0]],
                             "scenes": [{"inning": 7, "half": "bottom", "outs": 2, "bases": [], "slot": 9, "kind": "field",
                                         "event": "ep03_04", "var": "taeo_ball_fielded",
                                         "map": {"bonus": True, "normal": True, "_": False}}], "after": "fixed"}},
    # EP04 한빛고전: 7회 초 2사 1루 대타 → 7회 말 1사 3유간 땅볼 (1-6)
    "ep04_02": {"gamePlan": {"role": "sub", "line": [[0, 0, 0, 0, 0, 1, 0, 0, 0], [0, 2, 0, 0, 4, 0, 0, 0, 0]],
                             "scenes": [{"inning": 7, "half": "top", "outs": 2, "bases": [1], "slot": 9, "kind": "pa",
                                         "event": "ep04_03", "var": "first_ab_result", "map": OUTS3, "pitcher": "balanced"},
                                        {"inning": 7, "half": "bottom", "outs": 1, "bases": [], "kind": "field",
                                         "event": "ep04_04b", "var": "ep04_def_result", "map": FIELD2}], "after": "fixed"}},
    # EP05 백호기 1회전 해성고전: 7회 초 1사 2루 대타 → 7회 말 수비 → 그대로 끝 (1-5 / 2-5)
    "ep05_03": {"gamePlan": {"opponent": "haeseong", "role": "sub", "home": False,
                             "line": [[0, 0, 1, 0, 0, 0, 0, 0, 0], [2, 0, 0, 0, 2, 1, 0, 0, 0]],
                             "scenes": [{"inning": 7, "half": "top", "outs": 1, "bases": [2], "slot": 9, "kind": "pa",
                                         "event": "ep05_04", "var": "ep05_ab_result", "map": OUTS3, "pitcher": "balanced"},
                                        {"inning": 7, "half": "bottom", "outs": 0, "bases": [], "kind": "field",
                                         "event": "ep05_04b", "var": "ep05_def_result", "map": FIELD2}], "after": "fixed"}},
    # EP06 후반기 개막 동인고전: 9번 유격수 선발. 3회 초·6회 초 타석, 4회 말 수비, 7회 말 2-2 무사 1루 병살 → 그 뒤는 실제 경기
    "ep06_04": {"gamePlan": {"role": "start", "home": False,
                             "line": [[0, 0, 0, 1, 0, 1, 0, 0, 0], [0, 1, 0, 0, 1, 0, 0, 0, 0]],
                             "scenes": [{"inning": 3, "half": "top", "outs": 0, "bases": [], "kind": "pa"},
                                        {"inning": 4, "half": "bottom", "outs": 0, "bases": [], "kind": "field"},
                                        {"inning": 6, "half": "top", "outs": 0, "bases": [], "kind": "pa"},
                                        {"inning": 7, "half": "bottom", "outs": 0, "bases": [1], "score": [2, 2], "kind": "field",
                                         "event": "ep06_04b", "var": "ep06_dp_result", "doublePlay": ["BONUS", "NORMAL"],
                                         "map": {"bonus": "success", "normal": "success", "_": "fail"}}], "after": "play"}},
    # EP08 서호대 연습경기 (8이닝): 타석 셋 + 수비 하나, 류건우 (직구 위주 → 슬라이더 → 전력 직구)
    "ep08_02": {"gamePlan": {"opponent": "seoho", "practice": True, "innings": 8, "role": "start", "home": False,
                             "line": [[0, 0, 0, 0, 0, 0, 0, 0], [2, 0, 0, 0, 1, 0, 1, 0]],
                             "scenes": [{"inning": 3, "half": "top", "outs": 0, "bases": [], "kind": "pa", "event": "ep08_03",
                                         "var": "ep08_ab1_result", "pitcher": "power",
                                         "map": dict(HITS, walk="walk", strikeout="strikeout", **{"_": "out"})},
                                        {"inning": 4, "half": "bottom", "outs": 1, "bases": [1], "kind": "field", "event": "ep08_03b",
                                         "var": "ep08_def_result", "doublePlay": ["BONUS"],
                                         "map": {"bonus": "double_play", "normal": "out", "_": "error"}},
                                        {"inning": 6, "half": "top", "outs": 2, "bases": [2], "kind": "pa", "event": "ep08_03c",
                                         "var": "ep08_ab2_result", "pitcher": "finesse",
                                         "map": {"single": "rbi_hit", "double": POWER_XB, "triple": POWER_XB, "home_run": POWER_XB,
                                                 "strikeout": "strikeout", "_": "out"}},
                                        {"inning": 8, "half": "top", "outs": 0, "bases": [], "kind": "pa", "event": "ep08_03d",
                                         "var": "ep08_ab3_result", "pitcher": "power",
                                         "map": dict(HITS, strikeout="strikeout", **{"_": "out"})}], "after": "fixed"}},
}


def gb(boosts):  # gameBoost 효과 노드
    return {"do": {"gameBoost": boosts}}


def by_var(var, arms):
    """변수 값별 갈래: arms {값: [노드…]}"""
    return {"branch": [{"if": {"var": var, "eq": v}, "then": nodes} for v, nodes in arms.items()]}


# GAMEPLAY 구간 → 엔진. split: 여기서 이벤트를 끊는다(minigame 이면 앞 이벤트가 끝날 때 연다). inline: 그 자리에 노드를 넣는다
HOOKS = {
    "fitness_test id=speed": {"split": True, "minigame": "fitness_run"},
    "fitness_test id=squat": {"split": True, "minigame": "fitness_lift"},
    "fitness_test id=reaction": {"split": True, "minigame": "fitness_react"},
    "fielding_drill id=fungo": {"split": True, "minigame": "drill:fungo"},
    "observation id=taeo_first_step": {"inline": []},   # 관찰 미니게임은 뺐다 (2026-11-02 유저 결정) — 대사만 남는다
    "fielding id=ep03_def": {"split": True},          # 경기 대본 장면 (ep03_02 의 gamePlan)
    "batting id=ep04_ab": {"split": True},
    "fielding id=ep04_def": {"split": True},
    "batting_practice id=ep05_cage": {"split": True, "minigame": "drill:cage"},
    "batting id=ep05_ab": {"split": True},
    "fielding id=ep05_def": {"split": True},
    "fielding_drill id=dp_practice": {"split": True, "minigame": "drill:dp_practice"},
    "full_game id=ep06_game": {"split": True},
    "fielding id=ep06_dp": {"split": True},
    "season_progress id=ep06_second_half": {"inline": []},
    "batting_practice id=ep07_new_form_w1": {"split": True, "minigame": "drill:new_form"},
    "training_minigame id=ep07_focus": {"split": True, "nodes": [by_var("training_direction", {
        "contact": [{"do": {"minigame": "drill:focus_contact"}}], "power": [{"do": {"minigame": "drill:focus_power"}}],
        "defense_run": [{"do": {"minigame": "drill:focus_defense"}}]})]},
    "batting_practice id=ep07_final": {"split": True, "minigame": "drill:final_form"},
    "batting id=ab1": {"split": True},
    "fielding id=def1": {"split": True},
    "batting id=ab2": {"split": True},
    "batting id=ab3": {"split": True},
}

# 해금 → 폰 연락처에 추가 (탭을 잠그지 않는다 — 2026-11-02 유저 결정)
CONTACTS = {"phone_contacts": ["neighbor", "manager", "buddy"], "phone_profile_taeo": ["rival"]}
# 폰 문자로 주고받는 선택 지점 → 대화방(인물 id). 선택지는 내 말풍선, 뒤따르는 대사는 문자로 보인다
PHONE_CHOICES = {"ep01_harin_text": "neighbor", "ep03_harin_text": "neighbor", "ep04_text_first": "neighbor",
                 "ep06_junsu_text": "buddy", "ep07_harin_text": "neighbor"}


def phone_marked(nodes):
    """폰 선택지 뒤 대사를 문자(ui phone)로"""
    for n in nodes:
        if "speaker" in n:
            n["ui"] = "phone"
        for arm in n.get("branch", []):
            phone_marked(arm["then"])
        for c in n.get("choices", []):
            phone_marked(c.get("then", []))
    return nodes


# SYSTEM 줄에서 엔진이 직접 하는 것 (글자 대신 효과)
EP08_PERF = [  # 문서 3장 공식
    by_var("ep08_ab1_result", {"hit": [{"do": {"add": {"ep08_performance": 2}}}], "walk": [{"do": {"add": {"ep08_performance": 1}}}]}),
    by_var("ep08_def_result", {"double_play": [{"do": {"add": {"ep08_performance": 2}}}], "out": [{"do": {"add": {"ep08_performance": 1}}}],
                               "error": [{"do": {"add": {"ep08_performance": -1}}}]}),
    by_var("ep08_ab2_result", {"extra_base": [{"do": {"add": {"ep08_performance": 3}}}], "rbi_hit": [{"do": {"add": {"ep08_performance": 2}}}]}),
    by_var("ep08_ab3_result", {"hit": [{"do": {"add": {"ep08_performance": 3}}}], "out": [{"do": {"add": {"ep08_performance": 1}}}]}),
]
# 능력치 대폭 상승 (EP07 #6, 임시 수치): 모두 +2, 고른 방향 +3 더
STAT_JUMP = [{"do": {"contact": 2, "power": 2, "eye": 2, "speed": 1, "fielding": 2}},
             by_var("training_direction", {"contact": [{"do": {"contact": 3, "eye": 2}}], "power": [{"do": {"power": 4}}],
                                           "defense_run": [{"do": {"fielding": 3, "speed": 2}}]})]
SYSTEM_FX = {
    "SYSTEM: 초기 능력치가 결정되었습니다.": [{"ui": "system", "text": "초기 능력치가 결정되었습니다."}],
    "SYSTEM: test_overall_grade = 3종목 등급의 평균 (high=3, mid=2, low=1 → 평균 2.5 이상 high, 1.5 이상 mid, 미만 low)": [],
    "SYSTEM: 주요 경기는 직접 조작 / 나머지 경기는 자동 진행 (주인공 능력치 기반 결과 산출)": [
        {"ui": "system", "text": "주요 경기는 직접 조작 / 나머지 경기는 자동 진행"}],
    "SYSTEM: 타석마다 stats.at_bat_video_count += 1, 수비 조작마다 stats.fielding_video_count += 1": [],
    "SYSTEM: form_adaptation 증가 (최종 SCENE ep07_06에서 100 도달하도록 조정)": [{"do": {"add": {"form_adaptation": 30}}}],
    "SYSTEM: 능력치 대폭 상승 (training_direction에 따라 추가 보너스)": [{"ui": "system", "text": "능력치 대폭 상승"}] + STAT_JUMP,
    "SYSTEM: ep08_performance 계산 (3장 공식)": EP08_PERF,
    "SYSTEM: 이번 경기 활약도가 스카우트 관심도 첫 평가에 반영됩니다": [{"ui": "system", "text": "이번 경기 활약도가 스카우트 관심도 첫 평가에 반영됩니다"}],
    "SYSTEM: 첫 평가 결과 표시 — checked: \"체크 대상\" / watching: \"관찰 중\" / none: \"관심 없음\"": [by_var("scout_interest", {
        "checked": [{"ui": "system", "text": "스카우트 첫 평가 — 체크 대상"}], "watching": [{"ui": "system", "text": "스카우트 첫 평가 — 관찰 중"}],
        "none": [{"ui": "system", "text": "스카우트 첫 평가 — 관심 없음"}]})],
    "SYSTEM: scout_interest = checked": [{"do": {"set": {"scout_interest": "checked"}, "scoutInterest": 8}}],
    "SYSTEM: scout_interest = watching": [{"do": {"set": {"scout_interest": "watching"}, "scoutInterest": 4}}],
}
# UI 화면을 엔진 값으로 그려야 하는 곳
UI_FX = {
    "whiteboard_stats": [{"ui": "whiteboard", "text": "후반기 개인 기록 — {phaseLine}"}],
    "phone_scout_tab": [
        {"ui": "phone", "text": "{player} — 서림고 2학년 (예정) / 유격수"},
        by_var("scout_interest", {"checked": [{"ui": "phone", "text": "관심도: 체크 대상"}], "watching": [{"ui": "phone", "text": "관심도: 관찰 중"}],
                                  "none": [{"ui": "phone", "text": "관심도: 관심 없음"}]}),
        {"branch": [{"if": {"var": "scout_interest", "eq": "checked"}, "then": [{"ui": "phone", "text": "방문 구단: 태성 썬더스"}]},
                    {"then": [{"ui": "phone", "text": "방문 구단: 없음"}]}]},
        {"ui": "phone", "text": "예상 지명: —"}],
    "stat_screen": [],    # 체력 테스트 종합 카드가 대신 보여 준다
    "stat_compare": [],   # 아직 화면 없음 (dev-log 보고)
}

# ---------- 이벤트 메타: 언제 뜨나 (문서에 없는 엔진 정보) ----------
W1 = {"grade": [1, 1], "week": [1, 1]}
G1 = {"grade": [1, 1]}
LEAGUE_END = {"grade": [1, 1], "lastPhaseEnd": "leagueEnd"}
def camp(w):
    return {"grade": [1, 1], "winter": True, "campWeek": [w, w]}
META = {
    # EP01·EP02 앞부분: 첫 주 주 시작 (입부 → 체력 테스트 → 다음 날 새벽 펑고 → 토요일 공지)
    **{k: dict(W1, timing="start") for k in ["ep01_01", "ep01_02", "ep01_05", "ep01_05b", "ep01_05c", "ep01_05d", "ep01_07",
                                             "ep02_01", "ep02_01b", "ep02_02"]},
    # 첫 주 경기(동인고, 관전) 뒤
    **{k: dict(G1, timing="after") for k in ["ep02_03", "ep02_04", "ep02_05"]},
    # 둘째 주: 청운고 연습경기
    **{k: dict(G1, timing="start") for k in ["ep03_01", "ep03_02", "ep03_03"]},
    "ep03_04": {"timing": "game", "moment": "scene"},
    **{k: dict(G1, timing="after") for k in ["ep03_04b", "ep03_05", "ep03_06", "ep03_07"]},
    # 셋째 주: 한빛고전 (대타 → 수비)
    **{k: dict(G1, timing="start") for k in ["ep04_01", "ep04_02"]},
    "ep04_03": {"timing": "game", "moment": "scene"},
    "ep04_04b": {"timing": "game", "moment": "scene"},
    **{k: dict(G1, timing="after") for k in ["ep04_05b", "ep04_06", "ep04_07"]},
    # 주말리그 전반기 마지막 경기 뒤 → 백호기 주
    "ep05_01": dict(LEAGUE_END, timing="after"),
    **{k: dict(G1, timing="start") for k in ["ep05_02", "ep05_02b", "ep05_03"]},
    "ep05_04": {"timing": "game", "moment": "scene"},
    "ep05_04b": {"timing": "game", "moment": "scene"},
    **{k: dict(G1, timing="after") for k in ["ep05_05b", "ep05_06", "ep05_07", "ep05_08", "ep05_09"]},
    # 후반기 개막 주
    **{k: dict(G1, timing="start") for k in ["ep06_01", "ep06_02", "ep06_02b", "ep06_03", "ep06_04"]},
    "ep06_04b": {"timing": "game", "moment": "scene"},
    **{k: dict(G1, timing="after") for k in ["ep06_04c", "ep06_05"]},
    "ep06_06": dict(G1, timing="start"),
    "ep06_07": dict(LEAGUE_END, timing="after"),
    "ep06_08": dict(G1, timing="after"),
    # 겨울 캠프 (4주): 1주 면담·새 폼, 2주 연말·1월 초, 3주 훈련 방향·완성·예고, 4주 서호대 연습경기
    **{k: dict(camp(1), timing="start") for k in ["ep07_01", "ep07_02", "ep07_02b"]},
    **{k: dict(camp(2), timing="start") for k in ["ep07_03", "ep07_04"]},
    **{k: dict(camp(3), timing="start") for k in ["ep07_05", "ep07_05b", "ep07_06", "ep07_06b", "ep07_07", "ep07_08"]},
    **{k: dict(camp(4), timing="start") for k in ["ep08_01", "ep08_02"]},
    **{k: {"timing": "game", "moment": "scene"} for k in ["ep08_03", "ep08_03b", "ep08_03c", "ep08_03d"]},
    **{k: dict(camp(4), timing="after") for k in ["ep08_03e", "ep08_04", "ep08_05", "ep08_06", "ep08_07"]},
}
# 장면을 이어 붙인다 (False: 앞 이벤트에 이어서 한 화면으로) — 경기 장면 대화가 SCENE 경계를 넘을 때
JOIN = {"ep01_03", "ep01_04", "ep01_06", "ep04_04", "ep04_05", "ep05_05"}
# 이벤트 앞·뒤에 덧붙이는 노드 (엔진 효과 — 문서의 SYSTEM·modifiers 를 옮긴 것)
PREPEND = {
    "ep01_01": [{"do": {"lineupSlot": 0}}],   # 입부한 1학년은 벤치
    # EP03 대수비: 반응속도 등급이 높을수록 포구 판정 구간 확대 (이번 경기만)
    "ep03_04": [by_var("test_reaction_grade", {"high": [gb({"fielding": 8})], "low": [gb({"fielding": -4})]})],
    # EP04 대타: 몸풀기 — calm 은 공이 잘 보이고(선구안), eager 는 타구가 세지만 헛스윙이 는다(파워 +, 컨택 −)
    "ep04_03": [by_var("warmup_effect", {"calm": [gb({"eye": 8})], "eager": [gb({"power": 8, "contact": -5})]})],
    # EP06 병살: 연습 4개 이상이면 판정 확대, 정민재 신뢰가 있으면 송구 보너스
    "ep06_04b": [{"branch": [{"if": {"var": "ep06_dp_practice_success", "gte": 4}, "then": [gb({"fielding": 6})]}]},
                 {"branch": [{"if": {"var": "minjae_trust", "gte": 1}, "then": [gb({"fielding": 3})]}]}],
    # EP06 경기 결과: 실제 승패로 (무승부는 진 것으로)
    "ep06_04c": [{"branch": [{"if": {"lastResult": "win"}, "then": [{"do": {"set": {"ep06_game_result": "win"}}}]},
                             {"then": [{"do": {"set": {"ep06_game_result": "lose"}}}]}]}],
    # EP06 후반기 타율 등급 (임계값 balance.json career.story.constants.ep06SeasonAvg)
    "ep06_07": [{"do": {"gradeVar": {"var": "ep06_season_avg_grade", "of": "lastPhaseAvg", "thresholds": "ep06SeasonAvg"}}}],
    # EP08 훈련 방향 보정 (경기 전체)
    "ep08_03": [by_var("training_direction", {"contact": [gb({"eye": 8})], "power": [gb({"power": 8})],
                                              "defense_run": [gb({"fielding": 8, "speed": 6})]})],
}
APPEND = {
    # 1학년이 끝나면 2학년부터 원래 커리어 (진행 플래그)
    "ep08_07": [{"do": {"flag": "y1_done"}}],
    "ep06_01": [{"do": {"lineupSlot": 9}}],   # 유격수 주전 (9번)
}
# 선택지에 붙는 엔진 효과 (선택 지점 id, 선택지 번호 → 효과)
CHOICE_FX = {
    ("ep07_training", 0): {"campFocus": "contact"},
    ("ep07_training", 1): {"campFocus": "power"},
    ("ep07_training", 2): {"campFocus": "fielding"},
}
# 조건 임계값을 상수로 (문서 "상수로 분리할 것")
CONSTS = {("ep08_performance", "gte", 6): "ep08ScoutChecked", ("ep08_performance", "gte", 3): "ep08ScoutWatching"}


# ---------- 문서 읽기 ----------

def scenes_of(md: str):
    """[(scene id, 제목, [코드 블록 줄 목록…])]"""
    out = []
    cur = None
    in_code = False
    block = None
    for raw in md.splitlines():
        m = re.match(r"^### SCENE (ep\d\d_\d\d) \| (.*)$", raw)
        if m and not in_code:
            cur = (m.group(1), m.group(2).split("|")[-1].strip(), [])
            out.append(cur)
            continue
        if raw.startswith("# ") and not in_code:
            cur = None
            continue
        if raw.startswith("```"):
            if in_code:
                in_code = False
                if cur is not None:
                    cur[2].append(block)
            else:
                in_code = True
                block = []
            continue
        if in_code:
            block.append(raw)
    return out


def value(v: str):
    v = v.strip()
    if v == "true":
        return True
    if v == "false":
        return False
    if re.fullmatch(r"-?\d+", v):
        return int(v)
    if v.startswith("[") and v.endswith("]"):
        return [value(x) for x in v[1:-1].split(",") if x.strip()]
    return v.strip('"')


def cond(expr: str):
    e = expr.strip().rstrip(":").strip()
    if e.startswith("(") and e.endswith(")"):
        e = e[1:-1].strip()
    m = re.fullmatch(r"(\S+)\s+in\s+\[(.*)\]", e)
    if m:
        return {"var": m.group(1), "in": [value(x) for x in m.group(2).split(",")]}
    m = re.fullmatch(r"(\S+)\s*(==|!=|>=|<=|>|<)\s*(.+)", e)
    assert m, "조건을 못 읽었다: " + expr
    op = {"==": "eq", "!=": "ne", ">=": "gte", "<=": "lte", ">": "gt", "<": "lt"}[m.group(2)]
    val = value(m.group(3))
    key = (m.group(1), op, val)
    if key in CONSTS:
        val = {"const": CONSTS[key]}
    return {"var": m.group(1), op: val}


def effects(spec: str):
    """'harin_affinity += 1 ; taeo_interest = true ; goto A' → (효과, goto)"""
    fx = {}
    goto = None
    for part in spec.split(";"):
        p = part.strip()
        if p == "" or p.startswith("(변화"):
            continue
        m = re.fullmatch(r"goto (\w+)", p)
        if m:
            goto = m.group(1)
            continue
        m = re.fullmatch(r"(\S+) \+= (-?\d+)", p)
        if m:
            fx.setdefault("add", {})[m.group(1)] = int(m.group(2))
            continue
        m = re.fullmatch(r"(\S+) = (.+)", p)
        assert m, "효과를 못 읽었다: " + spec
        fx.setdefault("set", {})[m.group(1)] = value(m.group(2))
    return fx, goto


def ind(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


class Parser:
    """들여쓰기 줄 → 대본 노드. GAMEPLAY 는 {"hook": 이름} 노드로 남긴다 (나중에 자른다)"""

    def __init__(self, lines):
        self.lines = [l for l in lines if l.strip() != ""]
        self.i = 0

    def peek(self):
        return self.lines[self.i] if self.i < len(self.lines) else None

    def block(self, level: int):
        nodes = []
        while self.i < len(self.lines):
            line = self.lines[self.i]
            if ind(line) < level:
                break
            t = line.strip()
            if ind(line) == level and (t.startswith("ELIF ") or t == "ELSE:" or t == "END"):
                break
            if ind(line) > level:
                raise SystemExit("들여쓰기가 이상하다: %r" % line)
            nodes.extend(self.statement(level))
        return nodes

    def body(self, level: int):
        """다음 줄들 중 level 보다 깊은 것"""
        start = self.i
        while self.i < len(self.lines) and ind(self.lines[self.i]) > level:
            self.i += 1
        return [l for l in self.lines[start:self.i]]

    def statement(self, level: int):
        t = self.lines[self.i].strip()
        self.i += 1
        if t.startswith("IF "):
            arms = [{"if": cond(t[3:]), "then": self.block(level + 2)}]
            while self.peek() is not None and ind(self.peek()) == level:
                n = self.peek().strip()
                if n.startswith("ELIF "):
                    self.i += 1
                    arms.append({"if": cond(n[5:]), "then": self.block(level + 2)})
                elif n == "ELSE:":
                    self.i += 1
                    arms.append({"then": self.block(level + 2)})
                elif n == "END":
                    self.i += 1
                    break
                else:
                    break
            return [{"branch": arms}]
        if t.startswith("CHOICE "):
            return self.choice(t[7:].rstrip(":").strip(), level)
        if t.startswith("GAMEPLAY "):
            self.body(level)
            return [{"hook": t[9:].strip()}]
        if t.startswith("UI "):
            return self.ui(t, level)
        if t == "AUTO:":
            return [{"text": l.strip()[2:].strip()} for l in self.body(level)]
        if t.startswith("ITEM_SHOW"):
            return [{"ui": "item", "text": "\n".join(l.strip() for l in self.body(level))}]
        if t.startswith("SYSTEM:"):
            sub = self.body(level)
            return self.system(t, sub)
        if t.startswith("NARR:"):
            text = t[5:].strip()
            if text == "(몽타주)":
                out = []
                for l in self.body(level):
                    item = l.strip()[2:].strip()
                    m = re.match(r"^\[조건: (.+?)\] (.*)$", item)
                    if m:
                        out.append({"branch": [{"if": cond(m.group(1)), "then": [{"text": m.group(2)}]}]})
                    else:
                        out.append({"text": item})
                return out
            m = re.match(r"^\((3학년 전원)\) (.*)$", text)
            if m:
                return [{"speaker": m.group(1), "text": m.group(2)}]
            return [{"text": text}]
        m = re.match(r"^(\w+): (.*)$", t)
        if m and m.group(1) in SPEAKERS:
            return [{"speaker": SPEAKERS[m.group(1)], "text": m.group(2)}]
        raise SystemExit("모르는 줄: %r" % t)

    def ui(self, t: str, level: int):
        m = re.match(r"^UI (\w+)(.*?):\s*(.*)$", t)
        kind = m.group(1)
        rest = m.group(3)
        body = self.body(level)
        if kind in UI_FX:
            return [dict(n) for n in UI_FX[kind]]
        ui = UI_KINDS[kind]
        if kind == "phone_message":
            out = []
            for l in body:
                mm = re.match(r"^\s*(\w+): (.*)$", l)
                out.append({"speaker": SPEAKERS[mm.group(1)], "text": mm.group(2), "ui": ui})
            return out
        lines = [l.strip() for l in body] or [rest]
        return [{"ui": ui, "text": "\n".join(lines)}]

    def system(self, t: str, sub):
        if t in SYSTEM_FX:
            return [json.loads(json.dumps(n)) for n in SYSTEM_FX[t]]
        text = t[7:].strip()
        if sub:
            text = text + "\n" + "\n".join(l.strip() for l in sub)
        if re.match(r"^(획득|플래그) —", text):
            return []
        m = re.match(r"^해금 — (\w+)(?: \((.*)\))?$", text)
        if m:
            fx = {"set": {m.group(1): True}}
            if m.group(1) in CONTACTS:
                fx["addContact"] = CONTACTS[m.group(1)]
            return [{"do": fx}, {"ui": "system", "text": "해금 — " + (m.group(2) or m.group(1))}]
        m = re.match(r"^([\w.]+) \+= (\d+)$", text)
        if m:
            return [{"do": {"add": {m.group(1): int(m.group(2))}}}]
        m = re.match(r'^([a-z_.]+) = ("[^"]*"|\[[^\]]*\]|\S+)\s*(?:—\s*(.*)|\((.*)\))?\s*$', text)
        if m:
            nodes = [{"do": {"set": {m.group(1): value(m.group(2))}}}]
            note = (m.group(3) or m.group(4) or "").strip()
            if note:
                nodes.append({"ui": "system", "text": note})
            return nodes
        return [{"ui": "system", "text": text}]

    def choice(self, cid: str, level: int):
        opts = []
        for l in self.body(level):
            s = l.strip()
            assert s.startswith("- "), l
            s = s[2:]
            guard = None
            m = re.match(r"^\[조건: (.+?)\] (.*)$", s)
            if m:
                guard = cond(m.group(1))
                s = m.group(2)
            label, _, spec = s.partition(" -> ")
            fx, goto = effects(spec)
            opts.append({"label": label.strip().strip('"'), "fx": fx, "goto": goto, "if": guard})
        labels = {}
        cont = []
        while self.peek() is not None and ind(self.peek()) == level and re.fullmatch(r"[A-Z][A-Z_]*:", self.peek().strip()):
            name = self.peek().strip()[:-1]
            self.i += 1
            labels[name] = self.block(level + 2)
        choices = []
        for k, o in enumerate(opts):
            c = {"label": o["label"]}
            letter = o["goto"] if o["goto"] and len(o["goto"]) == 1 else "ABCDEFGH"[k]
            c["key"] = letter
            if o["if"]:
                c["if"] = o["if"]
            fx = dict(o["fx"])
            fx.update(CHOICE_FX.get((cid, k), {}))
            if fx:
                c["effects"] = fx
            then = labels.get(o["goto"], []) if o["goto"] and len(o["goto"]) == 1 else []
            if then:
                c["then"] = then
                first = then[0]
                if first.get("speaker") == "player" and first["text"].startswith(o["label"].rstrip(".")):
                    c["echo"] = False
            choices.append(c)
        # 모든 선택지가 같은 이름표(NEXT)로 가면 그 이름표는 선택 뒤에 이어지는 줄이다
        for name, nodes in labels.items():
            if len(name) > 1:
                cont.extend(nodes)
        node = {"id": cid, "choices": choices}
        if cid in PHONE_CHOICES:
            node["ui"] = "phone"
            node["room"] = PHONE_CHOICES[cid]
            for c in choices:
                phone_marked(c.get("then", []))
            phone_marked(cont)
        return [node] + cont


# ---------- 이벤트로 ----------

def label_body(lines, name):
    """대본 줄에서 이름표 name: 의 몸통 (들여쓰기 2칸 걷어 낸 것)"""
    out = []
    grab = False
    for l in lines:
        if l.strip() == name + ":" and ind(l) == 0:
            grab = True
            continue
        if grab:
            if l.strip() and ind(l) == 0:
                break
            out.append(l[2:] if l.startswith("  ") else l)
    return out


def ep04_07_lines(lines):
    """EP04 #7 은 goto RESULT / END_SCENE 이 엇갈려서 같은 뜻의 IF/CHOICE 로 다시 짠다 (글자는 문서 그대로)"""
    result = label_body(lines, "RESULT")
    end = label_body(lines, "END_SCENE")
    phone = [l for l in lines[lines.index("IF ep04_harin_store == B:") + 1:] if True][:2]
    sub = ["IF ep04_harin_store == B:"] + phone + ["  " + l for l in result]
    sub += ["ELSE:", "  CHOICE ep04_text_first:",
            '    - "(하린에게 먼저 보낸다) 오늘 경기 나갔어" -> harin_affinity += 1 ; goto A',
            '    - "(안 보낸다)" -> goto B', "  A:"] + ["    " + l for l in result] + ["END"] + end
    return sub


def build():
    md = DOC.read_text(encoding="utf-8")
    scenes = scenes_of(md)
    stream = []  # [(scene id, block index, nodes)]
    for sid, title, blocks in scenes:
        for bi, block in enumerate(blocks):
            if sid == "ep04_07":
                block = ep04_07_lines(block)
            nodes = Parser(block).block(0)
            stream.append((sid, bi, title, nodes))
    events = []
    cur = None
    hooks_seen = {}

    def start_event(eid, sid, title):
        nonlocal cur
        cur = {"id": eid, "scene": sid, "title": title, "script": []}
        events.append(cur)

    for sid, bi, title, nodes in stream:
        # SCENE 이 바뀌면 새 이벤트 (JOIN 이면 앞 이벤트에 이어서), 같은 SCENE 의 다음 코드 블록은 이어서
        if cur is None or (bi == 0 and sid not in JOIN):
            start_event(sid, sid, title)
        for node in nodes:
            if "hook" in node:
                h = HOOKS[node["hook"]]
                if "inline" in h:
                    cur["script"].extend(json.loads(json.dumps(h["inline"])))
                    continue
                if "minigame" in h:
                    cur["script"].append({"do": {"minigame": h["minigame"]}})
                cur["script"].extend(json.loads(json.dumps(h.get("nodes", []))))
                n = hooks_seen.get(sid, 0)
                hooks_seen[sid] = n + 1
                start_event(sid + "bcdefgh"[n], sid, title)
                continue
            _hooks_inside(node)
            cur["script"].append(node)
    # 빈 이벤트 (끊은 뒤 남은 줄이 없음) 는 뺀다
    events = [e for e in events if any("text" in n or "choices" in n or "branch" in n for n in e["script"])]
    return events


def _hooks_inside(node):
    """IF 안의 GAMEPLAY (EP03 관찰) 도 inline 으로 바꾼다"""
    for arm in node.get("branch", []):
        new = []
        for n in arm["then"]:
            if "hook" in n:
                new.extend(json.loads(json.dumps(HOOKS[n["hook"]]["inline"])))
            else:
                _hooks_inside(n)
                new.append(n)
        arm["then"] = new


def speakers_in(nodes, out):
    for n in nodes:
        if "speaker" in n:
            out.append(n["speaker"])
        for arm in n.get("branch", []):
            speakers_in(arm["then"], out)
        for c in n.get("choices", []):
            speakers_in(c.get("then", []), out)
    return out


def finish(events):
    order = []
    prev = None
    for e in events:
        eid = e["id"]
        meta = META.get(eid)
        assert meta is not None, "META 에 없는 이벤트: " + eid
        script = json.loads(json.dumps(PREPEND.get(eid, []))) + e["script"] + json.loads(json.dumps(APPEND.get(eid, [])))
        for k, v in PLANS.get(eid, {}).items():
            script.append({"do": {k: v}})
        sp = speakers_in(script, [])
        hidden = sorted({s for s in sp if s in HIDDEN})
        if hidden:
            script.insert(0, {"do": {"meet": hidden}})
        people = [s for s in sp if s not in ("player",) and not s[0] >= "가"]
        out = {"id": eid, "story": True, "free": True}
        out.update(meta)
        if meta.get("timing") != "game":
            if prev is not None:
                out["after"] = [prev]
        out["episode"] = int(eid[2:4])
        out["speaker"] = people[0] if people else "coach"
        out["title"] = e["title"]
        out["place"] = PLACES[e["scene"]]
        out["script"] = script
        order.append(out)
        prev = eid
    return order


def main():
    events = finish(build())
    content = json.loads(CONTENT.read_text(encoding="utf-8"))
    kept = [e for e in content["events"] if not e["id"].startswith("ep0")]
    content["events"] = events + kept
    content["vars"] = {"_note": NOTE_VARS, **VARS}
    for vid, d in list(VARS.items()):
        pass
    # 선택 지점 id 도 변수 (enum: 고른 선택지의 key)
    for e in events:
        for node in _all_nodes(e["script"]):
            if "choices" in node and "id" in node:
                content["vars"][node["id"]] = {"type": "enum", "values": sorted({c["key"] for c in node["choices"]})}
    for pid, name in NEW_PLACES.items():
        content["places"].setdefault(pid, name)
    CONTENT.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("이벤트 %d개 (%s … %s)" % (len(events), events[0]["id"], events[-1]["id"]))


def _all_nodes(nodes):
    for n in nodes:
        yield n
        for arm in n.get("branch", []):
            yield from _all_nodes(arm["then"])
        for c in n.get("choices", []):
            yield from _all_nodes(c.get("then", []))


if __name__ == "__main__":
    main()
