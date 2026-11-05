#!/usr/bin/env python3
"""콘텐츠 전수 점검 (Godot 없이 돈다): config/content.json 의 모든 이야기 이벤트·문자의 모든 갈래·선택지를 따라가며
"고르면 에러가 나거나 의도와 다르게 움직일 수 있는 곳"을 찾는다.

  python3 scripts/check_content.py          → 문제 목록 (없으면 "문제 없음")

보는 것:
  1. 키·효과·조건 (EventBook.validate · Phone.validate 와 같은 규칙 — GDScript 상수를 그대로 읽는다)
  2. 모든 선택 경로 (갈래 IF/ELSE 는 모든 쪽, 선택지는 모두): 카드 선택지의 힌트·효과, 대사 속 {치환}, 효과 값의 형식
  3. 이야기 순서 (after): 없는 이벤트·순환·조건 때문에 영영 안 열리는 이벤트, requires 플래그를 세우는 곳
  4. 돈: 돈이 빠지는 선택에 돈 조건(cost·minMoney)이 없으면 — 지갑이 0 으로 깎이기만 하고 막히지 않는다
  5. 프로/고교 구분: proYear 가 있는 이벤트·문자는 프로에서만 — 고교 조건(grade·camp 등)을 같이 쓰면 안 열린다
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
content = json.loads((ROOT / "config/content.json").read_text(encoding="utf-8"))
balance = json.loads((ROOT / "config/balance.json").read_text(encoding="utf-8"))
eb_src = (ROOT / "godot/scripts/events/event_book.gd").read_text(encoding="utf-8")
ph_src = (ROOT / "godot/scripts/phone/phone.gd").read_text(encoding="utf-8")


def gd_const(src, name):
    m = re.search(r"const %s := \[(.*?)\]" % name, src, re.S)
    return re.findall(r'"([^"]+)"', m.group(1))


CONDITION_KEYS = set(gd_const(eb_src, "CONDITION_KEYS"))
EFFECT_KEYS = set(gd_const(eb_src, "EFFECT_KEYS"))
UI_KINDS = set(gd_const(eb_src, "UI_KINDS"))
ENGINE_FLAGS = set(gd_const(eb_src, "ENGINE_FLAGS"))
MSG_KEYS = set(gd_const(ph_src, "KEYS"))
MSG_EFFECTS = set(gd_const(ph_src, "EFFECTS"))
STATS = ["contact", "power", "eye", "speed", "fielding"]

events = content["events"]
messages = content["messages"]
people = {k for k in content["people"] if not k.startswith("_")}
places = {k for k in content["places"] if not k.startswith("_")}
vars_ = {k: v for k, v in content["vars"].items() if not k.startswith("_")}
cutscenes = {k for k in content.get("cutscenes", {}) if not k.startswith("_")}
milestones = content.get("pro", {}).get("milestones", [])
sns_types = set(content.get("pro", {}).get("sns", {}).get("posts", {}).keys())
event_ids = {e["id"] for e in events}

problems = []


def bad(where, what):
    problems.append("%s: %s" % (where, what))


# ---------- 플래그를 세우는 곳 ----------
def walk(nodes, fn):
    """대본 노드를 모두 (갈래·선택지·결과 안까지)"""
    for n in nodes:
        fn(n)
        for arm in n.get("branch", []):
            walk(arm.get("then", []), fn)
        for c in n.get("choices", []):
            fn({"_choice": c})
            walk(c.get("then", []), fn)
            for o in c.get("outcomes", []):
                fn({"_outcome": o})
                walk(o.get("then", []), fn)


def script_of(e):
    if "script" in e:
        return e["script"]
    return [{"choices": e.get("choices", [])}]


def effects_in(e):
    out = []

    def f(n):
        if "do" in n:
            out.append(("do", n["do"], None))
        if "_choice" in n:
            out.append(("choice", n["_choice"].get("effects", {}), n["_choice"]))
        if "_outcome" in n:
            out.append(("outcome", n["_outcome"].get("effects", {}), None))
    walk(script_of(e), f)
    return out


flag_setters = {}   # flag -> [이벤트·문자 id]
for f in ENGINE_FLAGS | set(content.get("pendingFlags", [])):
    flag_setters.setdefault(f, []).append("(엔진)")
for m in milestones:
    flag_setters.setdefault("ms_" + m["id"], []).append("(기념 순간)")
for e in events:
    for kind, fx, _ in effects_in(e):
        for fl in (fx.get("flag", []) if isinstance(fx.get("flag", []), list) else [fx["flag"]]):
            flag_setters.setdefault(fl, []).append(e["id"])
for m in messages:
    for r in m.get("replies", []) + [{"effects": m.get("ignore", {})}]:
        fx = r.get("effects", {})
        for fl in (fx.get("flag", []) if isinstance(fx.get("flag", []), list) else [fx["flag"]]):
            flag_setters.setdefault(fl, []).append(m["id"])

# 변수를 세우는 곳 (set·add 효과, 선택 지점 id, 엔진 변수)
ENGINE_VARS = {"stats.at_bat_video_count", "stats.fielding_video_count", "draft_tier", "draft_team", "pro_age", "pro_trust",
               "military_status", "military_ask", "military_last", "fa_choice", "retire_ask", "retire_forced", "retire_released"}
# 미니게임이 세우는 변수: 체력 테스트 등급(test_*_grade — balance.json fitness) · 연습 성공 수(content.json drills.countVar)
ENGINE_VARS |= {v for v in vars_ if v.startswith("test_") and v.endswith("_grade")}
ENGINE_VARS |= {d["countVar"] for k, d in content.get("drills", {}).items() if not k.startswith("_") and "countVar" in d}
var_setters = {v: ["(엔진)"] for v in ENGINE_VARS}
for e in events:
    def fv(n, eid=e["id"]):
        if "choices" in n and "id" in n:
            var_setters.setdefault(n["id"], []).append(eid)
    walk(script_of(e), fv)
    for kind, fx, _ in effects_in(e):
        for k in ("set", "add"):
            for v in fx.get(k, {}):
                var_setters.setdefault(v, []).append(e["id"])
        if "gradeVar" in fx:
            var_setters.setdefault(fx["gradeVar"]["var"], []).append(e["id"])
        gp = fx.get("gamePlan")
        if gp:
            for sc in gp.get("scenes", []):
                if "var" in sc:
                    var_setters.setdefault(sc["var"], []).append(e["id"] + "(경기 장면)")


# ---------- 조건 ----------
def cond_check(where, c):
    if not isinstance(c, dict):
        bad(where, "조건 형식 %r" % (c,))
        return
    for k in ("all", "any"):
        if k in c:
            for x in c[k]:
                cond_check(where, x)
            return
    if "not" in c:
        cond_check(where, c["not"])
        return
    if "flag" in c:
        if c["flag"] not in flag_setters:
            bad(where, "조건 플래그 %s 를 세우는 곳이 없다" % c["flag"])
        return
    if "fired" in c or "lastResult" in c:
        return
    if "var" not in c:
        bad(where, "조건에 var 가 없다 %r" % (c,))
        return
    v = c["var"]
    if v not in vars_:
        bad(where, "선언 안 된 변수 %s" % v)
        return
    if v not in var_setters and "default" not in vars_[v]:
        bad(where, "변수 %s 를 세우는 곳이 없다 (늘 기본값)" % v)
    d = vars_[v]
    for op in ("eq", "ne", "in"):
        if op in c and d.get("type") == "enum":
            vals = c[op] if op == "in" else [c[op]]
            for x in vals:
                if x not in d.get("values", []):
                    bad(where, "%s 의 값 %r 은 enum 에 없다 %s" % (v, x, d.get("values")))
        if op in c and d.get("type") == "bool" and op != "in" and not isinstance(c[op], bool):
            bad(where, "%s 는 bool 인데 %r 와 비교" % (v, c[op]))


TOKENS = {"player", "school", "rivalSchool", "scoutTeam", "rivalLast", "pick", "rank", "partner", "partnerName", "phaseLine",
          "proTeam", "proTeamShort", "proCity", "proCoach", "proCaptain", "draftPick", "bonus", "salary", "proLine"}
for p in people:
    TOKENS |= {p, p + "Name"}
TOKENS |= set(vars_)


def text_check(where, t):
    for tok in re.findall(r"\{(\w+)\}", t or ""):
        if tok not in TOKENS:
            bad(where, "모르는 글자 치환 {%s} in %r" % (tok, t))


def value_check(where, vid, val):
    d = vars_.get(vid)
    if d is None:
        bad(where, "선언 안 된 변수에 set/add: %s" % vid)
        return
    t = d.get("type")
    if t == "enum" and val not in d.get("values", []):
        bad(where, "%s = %r 은 enum 에 없다" % (vid, val))
    if t == "bool" and not isinstance(val, bool):
        bad(where, "%s (bool) = %r" % (vid, val))
    if t == "int" and not isinstance(val, int):
        bad(where, "%s (int) = %r" % (vid, val))


def fx_check(where, fx, allowed):
    for k, v in fx.items():
        if k not in allowed:
            bad(where, "쓸 수 없는 효과 %s" % k)
        if k in STATS and not isinstance(v, int):
            bad(where, "능력치 효과 %s 는 정수여야 한다 (%r)" % (k, v))
        if k == "rel":
            for p in v:
                if p not in people and p != "partner":
                    bad(where, "rel 인물 %s 없음" % p)
        if k in ("meet", "addContact"):
            for p in (v if isinstance(v, list) else [v]):
                if p not in people:
                    bad(where, "%s 인물 %s 없음" % (k, p))
        if k == "set":
            for vid, val in v.items():
                value_check(where, vid, val)
        if k == "add":
            for vid in v:
                if vars_.get(vid, {}).get("type") != "int":
                    bad(where, "add 는 int 변수만 (%s)" % vid)
        if k == "train":
            for st in v:
                if st not in STATS:
                    bad(where, "train 능력치 %s" % st)
        if k == "gameBoost":
            for st in v:
                if st not in STATS:
                    bad(where, "gameBoost 능력치 %s" % st)
        if k == "schedule" and v.get("event") not in event_ids:
            bad(where, "schedule 대상 %s 없음" % v.get("event"))
        if k == "partner" and v != "none" and not content["people"].get(v, {}).get("romance"):
            bad(where, "partner %s 는 연애 후보가 아니다" % v)
        if k == "proSns" and v not in sns_types:
            bad(where, "proSns 종류 %s 없음" % v)
        if k == "military" and v not in ("sangmu", "active", "delay"):
            bad(where, "military %s" % v)
        if k == "path" and v not in ("draft", "college"):
            bad(where, "path %s" % v)


# ---------- 이벤트 ----------
paths_total = 0


def enum_paths(nodes):
    """선택 경로 수 (갈래는 모든 쪽, 선택지는 모두)"""
    total = 1
    for n in nodes:
        if "branch" in n:
            total *= max(1, sum(enum_paths(a.get("then", [])) for a in n["branch"]))
        if "choices" in n:
            total *= max(1, sum(enum_paths(c.get("then", [])) for c in n["choices"]))
    return total


for e in events:
    eid = e["id"]
    where = "이벤트 " + eid
    for k in e:
        if k not in CONDITION_KEYS:
            bad(where, "모르는 키 %s" % k)
    for a in e.get("after", []):
        if a not in event_ids:
            bad(where, "after %s 없음" % a)
    if "when" in e:
        cond_check(where + " when", e["when"])
    for f in e.get("requires", []):
        if f not in flag_setters:
            bad(where, "requires %s 를 세우는 곳이 없다" % f)
    if e.get("place") and e["place"] not in places:
        bad(where, "place %s 없음" % e["place"])
    if e.get("speaker") not in people and e.get("speaker") != "partner":
        bad(where, "speaker %s" % e.get("speaker"))
    pro = "proYear" in e
    if pro and any(k in e for k in ("grade", "campWeek", "winter")):
        bad(where, "proYear 와 고교 조건(grade·campWeek·winter)을 같이 쓰면 열리지 않는다")
    if e.get("repeat") and not e.get("cooldownWeeks"):
        bad(where, "repeat 인데 cooldownWeeks 가 없다 — 조건이 맞는 동안 매주 뜬다")
    if e.get("scheduledOnly") and not any(
            fx.get("schedule", {}).get("event") == eid
            for x in events for _, fx, _ in effects_in(x)) and not any(
            r.get("effects", {}).get("schedule", {}).get("event") == eid for m in messages for r in m.get("replies", [])):
        bad(where, "scheduledOnly 인데 예약하는 곳이 없다")
    paths_total += enum_paths(script_of(e))

    def node_check(n, where=where, e=e):
        if "text" in n:
            text_check(where, n["text"])
            if n.get("ui") and n["ui"] not in UI_KINDS:
                bad(where, "ui %s" % n["ui"])
            sp = n.get("speaker", "")
            if sp and sp != "player" and sp.isascii() and sp not in people and sp != "partner":
                bad(where, "화자 %s 없음" % sp)
        for arm in n.get("branch", []):
            if "if" in arm:
                cond_check(where + " 갈래", arm["if"])
        if "choices" in n:
            if "id" in n and n["id"] not in vars_:
                bad(where, "선택 지점 id %s 가 변수가 아니다" % n["id"])
            shown = [c for c in n["choices"] if "if" not in c]
            if not shown:
                bad(where, "조건 없는 선택지가 하나도 없다 — 조건이 다 안 맞으면 고를 게 없다")
        if "_choice" in n:
            c = n["_choice"]
            text_check(where, c.get("label", ""))
            if "if" in c:
                cond_check(where + " 선택지 조건", c["if"])
            if e.get("card"):
                if not c.get("hint"):
                    bad(where, "카드 선택지 '%s' 에 힌트가 없다" % c.get("label"))
                if not c.get("effects") and not c.get("cost") and not c.get("outcomes"):
                    bad(where, "카드 선택지 '%s' 는 아무것도 안 바꾼다" % c.get("label"))
            fx = c.get("effects", {})
            if fx.get("money", 0) < 0 and not c.get("cost") and not e.get("minMoney"):
                bad(where, "선택지 '%s' 가 돈 %d 을 쓰는데 cost·minMoney 가 없다 (모자라도 막히지 않고 0 으로 깎인다)" % (c.get("label"), fx["money"]))
        if "do" in n:
            fx_check(where + " do", n["do"], EFFECT_KEYS)
        if "cutscene" in n and n["cutscene"] not in cutscenes:
            bad(where, "컷신 %s 없음" % n["cutscene"])
    walk(script_of(e), node_check)
    for kind, fx, c in effects_in(e):
        fx_check(where + (" 선택지 '%s'" % c.get("label") if c else ""), fx, EFFECT_KEYS)

# 이야기 순서: 순환
graph = {e["id"]: e.get("after", []) for e in events}
state = {}


def visit(n, stack):
    if state.get(n) == 1:
        bad("이야기 순서", "after 순환: %s" % " → ".join(stack + [n]))
        return
    if state.get(n) == 2:
        return
    state[n] = 1
    for m in graph.get(n, []):
        visit(m, stack + [n])
    state[n] = 2


for n in graph:
    visit(n, [])

# after 로 이어진 이야기의 학년·프로 구분: 고교 이벤트 뒤에 프로 이벤트는 되지만, 프로 이벤트 뒤에 고교 이벤트는 영영 안 열린다
by_id = {e["id"]: e for e in events}
for e in events:
    for a in e.get("after", []):
        if "proYear" in by_id[a] and "proYear" not in e:
            bad("이벤트 " + e["id"], "프로 이벤트 %s 뒤에 오는 고교 이벤트 — 열리지 않는다" % a)
        ga, ge = by_id[a].get("grade"), e.get("grade")
        if ga and ge and ga[0] > ge[1]:
            bad("이벤트 " + e["id"], "after %s 가 더 높은 학년(%s) — 열리지 않는다 (%s)" % (a, ga, ge))

# ---------- 문자 ----------
for m in messages:
    mid = m["id"]
    where = "문자 " + mid
    for k in m:
        if k not in MSG_KEYS:
            bad(where, "모르는 키 %s" % k)
    if m.get("speaker") not in people:
        bad(where, "speaker %s" % m.get("speaker"))
    for f in m.get("requires", []):
        if f not in flag_setters:
            bad(where, "requires %s 를 세우는 곳이 없다" % f)
    for a in m.get("after", []):
        if a not in event_ids:
            bad(where, "after %s 없음" % a)
        elif "proYear" in by_id[a] and "proYear" not in m:
            bad(where, "프로 이벤트 %s 뒤에 오는 고교 문자 — 안 온다" % a)
    if "proYear" in m and any(k in m for k in ("grade", "campWeek", "winter")):
        bad(where, "proYear 와 고교 조건을 같이 쓴다")
    lines = m.get("lines", []) + ([m["text"]] if "text" in m else [])
    for l in lines:
        text_check(where, l if isinstance(l, str) else l.get("text", ""))
    reps = m.get("replies", [])
    if len(reps) == 1 or len(reps) > 3:
        bad(where, "답장은 없거나 2~3개")
    for r in reps:
        text_check(where, r.get("label", ""))
        for l in r.get("then", []):
            text_check(where, l if isinstance(l, str) else l.get("text", ""))
        fx = r.get("effects", {})
        fx_check(where + " 답장 '%s'" % r.get("label"), fx, MSG_EFFECTS)
        if fx.get("money", 0) < 0 and not m.get("minMoney"):
            bad(where, "답장 '%s' 가 돈 %d 을 쓰는데 minMoney 가 없다 (모자라도 0 으로 깎인다)" % (r.get("label"), fx["money"]))
        elif fx.get("money", 0) < 0 and -fx["money"] > m.get("minMoney", 0):
            bad(where, "답장 '%s' 의 돈 %d 이 minMoney %d 보다 크다" % (r.get("label"), fx["money"], m["minMoney"]))
    fx_check(where + " ignore", m.get("ignore", {}), MSG_EFFECTS)
    if m.get("partner") == "chae" and "proYear" not in m:
        bad(where, "윤채아 문자는 프로에서만")

# ---------- 상황 컷신 · 프로 글 ----------
for mm in milestones:
    if not mm.get("cutscene") or not mm.get("title"):
        bad("기념 순간 " + mm.get("id", "?"), "제목·컷신 id")
print("이벤트 %d개 · 문자 %d통 · 선택 경로 %d개 점검" % (len(events), len(messages), paths_total))
if problems:
    print("문제 %d개:" % len(problems))
    for p in problems:
        print(" -", p)
    sys.exit(1)
print("문제 없음")
