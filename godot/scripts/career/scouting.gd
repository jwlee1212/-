class_name Scouting
extends RefCounted
## 스카우트 평가: 같은 학년 전국 유망주 풀 속 내 순위 → 예상 지명 순번, 주목 구단, 잠재력 범위.
##
## 평가값 = 종합 능력치 + 관심도 × perScout + (평판 − 50) × perReputation + (잠재력 평균 − 종합) × potentialSeen.
## 유망주 풀은 평가값만 있는 가상 선수들(이름 없음)이고 매주·겨울마다 자란다. 라이벌도 같은 식으로 평가해 순위에 넣는다.
## 수치는 balance.json career.scouting.

## 유망주 풀 평가값, 선수마다 주당 성장량
var pool: Array[float] = []
var growth: Array[float] = []
## 풀 선수 이름 (랭킹 표에 쓴다)
var names: Array[String] = []
## 주마다 남긴 내 순위·종합 능력치 (최근 것이 끝) — "최근 n주 변화" 표시용
var rank_history: Array[int] = []
var overall_history: Array[int] = []
## 구단이 관심을 보이는 순서 (pro_teams 번호). 스카우트 인물의 구단이 맨 앞
var team_order: Array[int] = []
## 잠재력 표시가 실제에서 어긋난 정도 (커리어마다 고정)
var potential_error := 0.0


static func create(cfg: CareerConfig, rng: RandomNumberGenerator) -> Scouting:
	var sc := Scouting.new()
	var c := cfg.scouting
	for i in int(c["poolSize"]):
		sc.pool.append(rng.randfn(float(c["poolStartMean"]), float(c["poolStartSd"])))
		sc.growth.append(maxf(0.0, rng.randfn(float(c["poolWeeklyGrowthMean"]), float(c["poolWeeklyGrowthSd"]))))
	for i in int(c["poolSize"]):
		sc.names.append(Team._random_name(cfg, rng))
	var order: Array[int] = []
	for i in cfg.pro_teams.size():
		order.append(i)
	# 시드 난수로 섞기 (Fisher–Yates)
	for i in range(order.size() - 1, 0, -1):
		var j := rng.randi_range(0, i)
		var t := order[i]
		order[i] = order[j]
		order[j] = t
	sc.team_order = order
	sc.potential_error = rng.randfn(0.0, float(c["potentialErrorSd"]))
	return sc


## 스카우트 인물의 구단 이름 (관심 순서 첫 구단)
func scout_team_name(cfg: CareerConfig) -> String:
	return cfg.pro_teams[team_order[0]]["name"]


static func value_of(cfg: CareerConfig, p: PlayerData) -> float:
	var c := cfg.scouting
	var ovr := float(p.overall())
	return ovr + p.scout_interest * float(c["perScout"]) + (p.reputation - 50) * float(c["perReputation"]) \
		+ (p.potential_average() - ovr) * float(c["potentialSeen"])


## 평가값 v 의 순위 (1위부터). others: 풀 밖에서 함께 비교할 평가값 (라이벌)
func rank_of(v: float, others: Array = []) -> int:
	var r := 1
	for x in pool:
		if x > v:
			r += 1
	for x in others:
		if float(x) > v:
			r += 1
	return r


## 한 주: 풀이 자란다
func weekly() -> void:
	for i in pool.size():
		pool[i] += growth[i]


## 겨울: 풀이 한 번 더 크게 자란다
func winter(cfg: CareerConfig) -> void:
	for i in pool.size():
		pool[i] += float(cfg.scouting["poolWinterGrowth"])


func record(rank: int, overall: int) -> void:
	rank_history.append(rank)
	overall_history.append(overall)


## 최근 n주 동안 변화 (값이 n주 전보다 몇 올랐나). 기록이 모자라면 처음 기록과 비교
static func delta(history: Array[int], weeks: int) -> int:
	if history.size() < 2:
		return 0
	var past := history[maxi(0, history.size() - 1 - weeks)]
	return history[-1] - past


## 예상 지명 순번 범위 [처음, 끝]
static func pick_range(cfg: CareerConfig, rank: int) -> Vector2i:
	var c := cfg.scouting
	var center := rank * float(c["picksPerRank"])
	var spread := maxf(float(c["rangeMin"]), center * float(c["rangeShare"]))
	return Vector2i(maxi(1, roundi(center - spread)), maxi(1, roundi(center + spread)))


## 예상 지명을 화면용으로: {"big": "3-8", "suffix": "순위 예상", "text": "1라운드 3-8순위"}
static func projection(cfg: CareerConfig, rank: int) -> Dictionary:
	var c := cfg.scouting
	var r := pick_range(cfg, rank)
	var per_round := int(c["picksPerRound"])
	var total := per_round * int(c["rounds"])
	if r.x > total:
		return {"big": "지명 밖", "suffix": "지금 평가로는", "text": "지금 평가로는 지명이 어렵다"}
	if r.y <= per_round:
		return {"big": "%d-%d" % [r.x, r.y], "suffix": "순위 예상", "text": "1라운드 %d-%d순위 예상" % [r.x, r.y]}
	var lo := (r.x - 1) / per_round + 1
	var hi := mini((r.y - 1) / per_round + 1, int(c["rounds"]))
	var big := str(lo) if lo == hi else "%d-%d" % [lo, hi]
	return {"big": big, "suffix": "라운드 예상", "text": "%s라운드 예상" % big}


## 주목하는 구단들 (관심도가 오를수록 늘어난다)
func interested_teams(cfg: CareerConfig, scout_interest: int) -> Array:
	var n := clampi(scout_interest / int(cfg.scouting["scoutPerTeam"]), 0, team_order.size())
	var out := []
	for i in n:
		out.append(cfg.pro_teams[team_order[i]])
	return out


## 잠재력 범위 "74-82" (학년이 오를수록 좁아진다. 가운데는 실제에서 조금 어긋나 있다)
func potential_range(cfg: CareerConfig, p: PlayerData) -> String:
	var widths: Array = cfg.scouting["potentialRangeByGrade"]
	var w := float(widths[clampi(p.grade - 1, 0, widths.size() - 1)])
	var center := p.potential_average() + potential_error * w / float(widths[0])
	var lo := maxi(p.overall(), roundi(center - w / 2.0))
	return "%d-%d" % [lo, maxi(lo, roundi(center + w / 2.0))]
