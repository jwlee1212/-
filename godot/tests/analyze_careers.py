#!/usr/bin/env python3
"""calibrate_career_many.gd 출력(줄 앞 "CAREER ")을 모아 밸런스 보고서용 표를 뽑는다.

    python3 godot/tests/analyze_careers.py out1.txt out2.txt ... > tables.md

표준 라이브러리만 쓴다. 표는 마크다운으로 찍는다 (docs/reports/ 보고서에 옮겨 쓴다).
"""
import json
import statistics as st
import sys
from collections import Counter

STATS = ["contact", "power", "eye", "speed"]
LABEL = {"contact": "컨택", "power": "파워", "eye": "선구안", "speed": "주력"}


def load(paths):
    out = []
    for p in paths:
        for line in open(p, encoding="utf-8"):
            if line.startswith("CAREER "):
                out.append(json.loads(line[7:]))
    out.sort(key=lambda c: c["i"])
    return out


def q(xs, f):
    xs = sorted(xs)
    if not xs:
        return float("nan")
    k = (len(xs) - 1) * f
    lo, hi = int(k), min(int(k) + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (k - lo)


def dist_row(name, xs, fmt="{:.1f}"):
    return "| %s | %s | %s | %s | %s | %s | %s |" % (
        name, fmt.format(st.mean(xs)), fmt.format(q(xs, 0.1)), fmt.format(q(xs, 0.25)), fmt.format(q(xs, 0.5)),
        fmt.format(q(xs, 0.75)), fmt.format(q(xs, 0.9)))


DIST_HEAD = "| | 평균 | p10 | p25 | 중앙값 | p75 | p90 |\n|---|---|---|---|---|---|---|"


def round_of(c_season):
    """예상 지명 순번 범위의 가운데 → 라운드 (10순번 = 1라운드, 110 넘으면 지명 밖)"""
    lo, hi = c_season["pick_range"]
    center = (lo + hi) / 2
    if lo > 110:
        return "지명 밖"
    r = int((center - 1) // 10) + 1
    return "%d라운드" % r if r <= 11 else "지명 밖"


def round_num(c_season):
    lo, hi = c_season["pick_range"]
    if lo > 110:
        return 12
    return min(12, int(((lo + hi) / 2 - 1) // 10) + 1)


def pot_avg(c):
    return sum(c["potential"][s] for s in STATS) / 4


def rate(line, key):
    ab, pa = line["ab"], line["pa"]
    h, d, t, hr, bb, so = line["h"], line["d"], line["t"], line["hr"], line["bb"], line["so"]
    if key == "avg":
        return h / ab if ab else 0
    if key == "obp":
        return (h + bb) / pa if pa else 0
    if key == "slg":
        return (h + d + 2 * t + 3 * hr) / ab if ab else 0
    if key == "ops":
        return rate(line, "obp") + rate(line, "slg")
    if key == "k":
        return so / pa if pa else 0
    if key == "bb":
        return bb / pa if pa else 0
    if key == "hr_pa":
        return hr / pa if pa else 0


def spearman(xs, ys):
    def ranks(v):
        order = sorted(range(len(v)), key=lambda i: v[i])
        r = [0.0] * len(v)
        i = 0
        while i < len(order):
            j = i
            while j + 1 < len(order) and v[order[j + 1]] == v[order[i]]:
                j += 1
            for k in range(i, j + 1):
                r[order[k]] = (i + j) / 2
            i = j + 1
        return r
    rx, ry = ranks(xs), ranks(ys)
    mx, my = st.mean(rx), st.mean(ry)
    cov = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    return cov / (sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry)) ** 0.5


def money(won):
    return "%.1f만" % (won / 10000)


def main():
    cs = load(sys.argv[1:])
    n = len(cs)
    print("# 자동 커리어 %d개 (시드 %d~%d)\n" % (n, cs[0]["seed"], cs[-1]["seed"]))

    print("## 1. 학년 끝 종합 능력치·능력치\n")
    print(DIST_HEAD)
    print(dist_row("시작 종합", [sum(c["start"][s] for s in STATS) / 4 for c in cs]))
    for g in range(3):
        print(dist_row("%d학년 끝 종합" % (g + 1), [c["seasons"][g]["overall"] for c in cs]))
    print(dist_row("잠재력 평균", [pot_avg(c) for c in cs]))
    print()
    print(DIST_HEAD)
    for s in STATS:
        print(dist_row("%s 시작" % LABEL[s], [c["start"][s] for c in cs], "{:.0f}"))
        print(dist_row("%s 3학년 끝" % LABEL[s], [c["seasons"][2]["stats"][s] for c in cs], "{:.0f}"))
        print(dist_row("%s 잠재력" % LABEL[s], [c["potential"][s] for c in cs], "{:.0f}"))
    print()
    print("### 잠재력 도달률 (3학년 끝 능력치 / 잠재력, 성장 여지 = 잠재력 − 시작 중 실제로 오른 비율)\n")
    print(DIST_HEAD)
    for s in STATS:
        print(dist_row("%s 도달률" % LABEL[s], [100 * c["seasons"][2]["stats"][s] / c["potential"][s] for c in cs]))
    for s in STATS:
        xs = []
        for c in cs:
            room = c["potential"][s] - c["start"][s]
            if room > 0:
                xs.append(100 * (c["seasons"][2]["stats"][s] - c["start"][s]) / room)
        print(dist_row("%s 여지 소화율" % LABEL[s], xs))
    print()
    for s in STATS:
        at = sum(1 for c in cs if c["seasons"][1]["stats"][s] >= c["potential"][s] - 1)
        at3 = sum(1 for c in cs if c["seasons"][2]["stats"][s] >= c["potential"][s] - 1)
        print("- %s: 2학년 끝에 잠재력−1 이상 %d%% · 3학년 끝 %d%%" % (LABEL[s], 100 * at / n, 100 * at3 / n))
    print()

    print("## 2. 유망주 랭킹·예상 지명\n")
    print(DIST_HEAD)
    for g in range(3):
        print(dist_row("%d학년 끝 랭킹" % (g + 1), [c["seasons"][g]["rank"] for c in cs], "{:.0f}"))
    print()
    order = ["%d라운드" % r for r in range(1, 12)] + ["지명 밖"]
    print("| 3학년 끝 예상 지명 | 커리어 | 비율 |\n|---|---|---|")
    cnt = Counter(round_of(c["seasons"][2]) for c in cs)
    for k in order:
        if cnt[k]:
            print("| %s | %d | %.0f%% |" % (k, cnt[k], 100 * cnt[k] / n))
    print()
    pots = [pot_avg(c) for c in cs]
    t1, t2 = q(pots, 1 / 3), q(pots, 2 / 3)
    print("잠재력 평균 3등분 (낮음 < %.1f ≤ 보통 < %.1f ≤ 높음) → 3학년 끝 예상 지명\n" % (t1, t2))
    print("| 잠재력 | n | 1라운드 | 2~5라운드 | 6~11라운드 | 지명 밖 | 랭킹 중앙값 |\n|---|---|---|---|---|---|---|")
    for name, f in [("낮음", lambda p: p < t1), ("보통", lambda p: t1 <= p < t2), ("높음", lambda p: p >= t2)]:
        sub = [c for c in cs if f(pot_avg(c))]
        rn = [round_num(c["seasons"][2]) for c in sub]
        print("| %s | %d | %.0f%% | %.0f%% | %.0f%% | %.0f%% | %.0f |" % (
            name, len(sub), 100 * sum(1 for r in rn if r == 1) / len(sub), 100 * sum(1 for r in rn if 2 <= r <= 5) / len(sub),
            100 * sum(1 for r in rn if 6 <= r <= 11) / len(sub), 100 * sum(1 for r in rn if r == 12) / len(sub),
            q([c["seasons"][2]["rank"] for c in sub], 0.5)))
    print()
    print("- 잠재력 평균 ↔ 3학년 끝 랭킹 스피어만 상관: %.2f (음수일수록 잠재력이 높으면 랭킹이 높다)" % spearman(pots, [c["seasons"][2]["rank"] for c in cs]))
    print("- 3학년 끝 종합 ↔ 랭킹: %.2f" % spearman([c["seasons"][2]["overall"] for c in cs], [c["seasons"][2]["rank"] for c in cs]))
    print("- 3학년 끝 관심도 ↔ 랭킹: %.2f" % spearman([c["seasons"][2]["scout"] for c in cs], [c["seasons"][2]["rank"] for c in cs]))
    for g in range(3):
        sc = [c["seasons"][g]["scout"] for c in cs]
        print("- %d학년 끝 관심도: 중앙값 %.0f, 100 에 붙은 커리어 %d%%" % (g + 1, q(sc, 0.5), 100 * sum(1 for x in sc if x >= 100) / n))
    print()

    print("## 3. 시즌 성적 (내 선수, 자동 타석)\n")
    print("| | 타석 | 타율 | 출루율 | 장타율 | OPS | 홈런 | 삼진% | 볼넷% |\n|---|---|---|---|---|---|---|---|---|")
    for g in range(3):
        ls = [c["seasons"][g]["line"] for c in cs]
        tot = {k: sum(l[k] for l in ls) for k in ls[0]}
        print("| %d학년 (합산) | %.0f/시즌 | %.3f | %.3f | %.3f | %.3f | %.1f/시즌 | %.1f | %.1f |" % (
            g + 1, tot["pa"] / n, rate(tot, "avg"), rate(tot, "obp"), rate(tot, "slg"), rate(tot, "ops"), tot["hr"] / n,
            100 * rate(tot, "k"), 100 * rate(tot, "bb")))
    print()
    print(DIST_HEAD)
    for g in range(3):
        ls = [c["seasons"][g]["line"] for c in cs]
        print(dist_row("%d학년 타율" % (g + 1), [rate(l, "avg") for l in ls], "{:.3f}"))
        print(dist_row("%d학년 OPS" % (g + 1), [rate(l, "ops") for l in ls], "{:.3f}"))
        print(dist_row("%d학년 홈런" % (g + 1), [l["hr"] for l in ls], "{:.1f}"))
        print(dist_row("%d학년 삼진%%" % (g + 1), [100 * rate(l, "k") for l in ls], "{:.1f}"))
    print()
    rl = [c["seasons"][g]["rival"]["line"] for c in cs for g in range(3)]
    tot = {k: sum(l[k] for l in rl) for k in rl[0]}
    print("- 라이벌 3년 합산: 타율 %.3f · OPS %.3f · 홈런 %.1f/시즌 · 삼진 %.1f%%" % (
        rate(tot, "avg"), rate(tot, "ops"), tot["hr"] / (3 * n), 100 * rate(tot, "k")))
    print()

    print("## 4. 돈·장비·이벤트·연애·부상\n")
    for strat in ["gear", "dating", None]:
        sub = [c for c in cs if strat is None or c["strategy"] == strat]
        name = {"gear": "장비·레슨형 (짝수)", "dating": "연애형 (홀수)", None: "전체"}[strat]
        m = [c["money"] for c in sub]
        print("**%s** (n=%d)\n" % (name, len(sub)))
        print(DIST_HEAD)
        print(dist_row("3년 수입", [x["income"] / 10000 for x in m]))
        print(dist_row("이벤트로 받은 돈", [x["event_gain"] / 10000 for x in m]))
        print(dist_row("장비", [x["gear"] / 10000 for x in m]))
        print(dist_row("레슨·관리", [x["service"] / 10000 for x in m]))
        print(dist_row("데이트·선물", [x["social"] / 10000 for x in m]))
        print(dist_row("이벤트 비용", [x["event_cost"] / 10000 for x in m]))
        print(dist_row("남은 돈", [c["final_money"] / 10000 for c in sub]))
        print()
    inc = Counter()
    for c in cs:
        for k, v in c["money"]["income_by"].items():
            inc[k] += v
    tot = sum(inc.values())
    print("수입 구성 (전체 합): " + " · ".join("%s %.0f%%" % (k, 100 * v / tot) for k, v in inc.most_common()))
    print()
    gear = [c for c in cs if c["strategy"] == "gear"]
    gc = Counter(g for c in gear for g in c["gear"])
    print("장비형 %d명이 산 장비: %s" % (len(gear), " · ".join("%s %d%%" % (k, 100 * v / len(gear)) for k, v in gc.most_common())))
    print("장비형 레슨 횟수 중앙값 %.0f회" % q([c["lessons"] for c in gear], 0.5))
    print()
    ev = [len(c["log"]) for c in cs]
    epw = [max(c["events_per_week"]) for c in cs]
    dpw = [x for c in cs for x in c["decisions_per_week"]]
    print(DIST_HEAD)
    print(dist_row("3년 이벤트 수", ev, "{:.0f}"))
    print(dist_row("스토리 이벤트", [sum(1 for e in c["log"] if e[4]) for c in cs], "{:.0f}"))
    print(dist_row("무작위 이벤트", [sum(1 for e in c["log"] if not e[4]) for c in cs], "{:.0f}"))
    print()
    print("- 한 주 이벤트 최대: %d (커리어마다 최대값의 분포 %s)" % (max(epw), dict(Counter(epw))))
    dc = Counter(dpw)
    print("- 한 주 꼭 할 결정(훈련 1 + 이벤트) 분포: " + " · ".join("%d개 %.1f%%" % (k, 100 * dc[k] / len(dpw)) for k in sorted(dc)))
    dating = [c for c in cs if c["strategy"] == "dating"]
    print("- 연애형 중 사귄 비율 %.0f%% (%s)" % (100 * sum(1 for c in dating if c["partner"]) / len(dating), dict(Counter(c["partner"] for c in dating if c["partner"]))))
    print("- 장비형 중 사귄 비율 %.0f%%" % (100 * sum(1 for c in gear if c["partner"]) / len(gear)))
    inj = [c["injury_starts"] for c in cs]
    print("- 부상: 3년 평균 %.2f번, 결장 %.1f주, 한 번도 안 다친 커리어 %.0f%%" % (st.mean(inj), st.mean([c["injured_weeks"] for c in cs]), 100 * sum(1 for x in inj if x == 0) / n))
    print("- 진로: %s" % dict(Counter(c["path"] for c in cs)))
    fired = Counter(e[2] for c in cs for e in c["log"])
    print("- 가장 자주 나온 이벤트 제목: " + " · ".join("%s %d" % kv for kv in fired.most_common(8)))
    print("- 가장 드문 제목: " + " · ".join("%s %d" % kv for kv in sorted(fired.items(), key=lambda kv: kv[1])[:10]))
    print()

    print("## 5. 라이벌과 비교 (3학년 끝)\n")
    print(DIST_HEAD)
    print(dist_row("내 종합", [c["seasons"][2]["overall"] for c in cs], "{:.0f}"))
    print(dist_row("라이벌 종합", [c["seasons"][2]["rival"]["overall"] for c in cs], "{:.0f}"))
    print(dist_row("내 랭킹", [c["seasons"][2]["rank"] for c in cs], "{:.0f}"))
    print(dist_row("라이벌 랭킹", [c["seasons"][2]["rival"]["rank"] for c in cs], "{:.0f}"))
    print()
    win = sum(1 for c in cs if c["seasons"][2]["rank"] < c["seasons"][2]["rival"]["rank"])
    print("- 3학년 끝에 라이벌보다 랭킹이 높은 커리어: %d%%" % (100 * win / n))
    print("- 3학년 끝에 라이벌보다 종합이 높은 커리어: %d%%" % (100 * sum(1 for c in cs if c["seasons"][2]["overall"] > c["seasons"][2]["rival"]["overall"]) / n))
    for g in range(3):
        print("- %d학년 끝 OPS 가 라이벌보다 높은 커리어: %d%%" % (g + 1, 100 * sum(1 for c in cs if rate(c["seasons"][g]["line"], "ops") > rate(c["seasons"][g]["rival"]["line"], "ops")) / n))
    print()

    print("## 6. 시드에 따른 퍼짐\n")
    fr = [c["seasons"][2]["rank"] for c in cs]
    print("- 3학년 끝 랭킹 표준편차 %.1f, 범위 %d~%d" % (st.pstdev(fr), min(fr), max(fr)))
    best = sorted(cs, key=lambda c: c["seasons"][2]["rank"])
    print("- 최고 5: " + " · ".join("시드%d 잠재%.0f→%d위" % (c["seed"], pot_avg(c), c["seasons"][2]["rank"]) for c in best[:5]))
    print("- 최저 5: " + " · ".join("시드%d 잠재%.0f→%d위" % (c["seed"], pot_avg(c), c["seasons"][2]["rank"]) for c in best[-5:]))
    jumps = [abs(c["seasons"][2]["rank"] - c["seasons"][1]["rank"]) for c in cs]
    print("- 2→3학년 랭킹 변화 |Δ| 중앙값 %.0f, 10칸 넘게 움직인 커리어 %d%%" % (q(jumps, 0.5), 100 * sum(1 for j in jumps if j > 10) / n))
    sc3 = [c["seasons"][2]["scout"] for c in cs]
    print("- 3학년 끝 관심도 표준편차 %.1f" % st.pstdev(sc3))
    team = [c["seasons"][g]["team"][0] / max(1, sum(c["seasons"][g]["team"])) for c in cs for g in range(3)]
    print("- 팀 승률(시즌) 중앙값 %.3f, p10 %.3f, p90 %.3f" % (q(team, 0.5), q(team, 0.1), q(team, 0.9)))
    champ = sum(1 for c in cs for g in range(3) for ph in c["seasons"][g]["phases"] if ph["text"].startswith("우승"))
    print("- 전국대회 우승: 커리어당 %.2f번 (시즌 %d개 중 %d)" % (champ / n, 3 * n, champ))


if __name__ == "__main__":
    main()
