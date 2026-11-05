class_name ProTeam
extends RefCounted
## KBA 구단 하나: 1군 명단(타자 9 + 후보, 선발 5 + 불펜)과 퓨처스(2군) 팀, 시즌 성적과 구단 역사.
## 경기는 GameRunner 로 돌리므로 School 을 하나 들고 있고, 경기마다 오늘 선발·타순을 School.team 에 꽂아 넣는다.

var id := ""
var name := ""
var short := ""
var city := ""
## 세계관 전력 구분 ("강" | "중" | "약") · 팀 성격 한 줄
var tier := ""
var style := ""
var strength := 60.0
## 경기에 쓰는 학교 객체 (이름·전력·명단) — GameRunner 가 받는다
var school: School
## 퓨처스 팀 (내가 2군일 때 상대·소속). 명단은 이름 없는 유망주들이고 기록은 내 것만 센다
var futures: School
## 주전 타자 9명 (타순 순서, ProPlayer) · 후보 타자 · 선발 투수 5 · 불펜
var lineup: Array = []
var bench: Array = []
var starters: Array = []
var bullpen: Array = []
## 다음 선발 차례 (0~4)
var rotation := 0
## 이번 시즌 [승, 패, 무] · 득점 · 실점 · 연승(+)·연패(−)
var w := 0
var l := 0
var d := 0
var runs := 0
var runs_allowed := 0
var streak := 0
## 지난 시즌 [{"year", "w", "l", "d", "rank", "post"}]
var history: Array = []
## 구단 한 시즌 최고 기록 {"hr"|"hits"|"avg"|"wins"|"so": {"name", "value", "year"}}
var bests := {}


func games() -> int:
	return w + l + d


## 승률 (무승부는 빼고 계산 — KBO 방식)
func pct() -> float:
	return float(w) / (w + l) if w + l > 0 else 0.0


func pct_text() -> String:
	return PlayerData.SeasonStats.rate_text(pct())


func all_players() -> Array:
	return lineup + bench + starters + bullpen


## 오늘 경기 명단을 School.team 에 꽂는다. me: 내가 1군 선발로 나오면 그 ProPlayer (타순 me_slot 자리), 아니면 null.
## 내가 직접 하는 경기는 GameRunner(for_game) 가 내 자리를 PlayerData 로 만든 타자로 바꾼다 — 내 기록은 game.my_line 에서 받는다.
## 돌려주는 값: 이 경기 타순 9명의 ProPlayer (박스스코어 줄 → 선수)
func set_for_game(me: ProPlayer, me_slot: int) -> Array:
	var order: Array = lineup.duplicate()
	if me != null and me_slot >= 1:
		# 내 포지션(유격수) 주전이 빠지고, 나머지는 순서를 지킨 채 내가 me_slot 번에 들어간다
		var out := me_slot - 1
		for i in order.size():
			if (order[i] as ProPlayer).position == me.position:
				out = i
				break
		order.remove_at(out)
		order.insert(me_slot - 1, me)
	var bats: Array[Team.Batter] = []
	for p: ProPlayer in order:
		bats.append(p.batter)
	school.team.lineup = bats
	var arms: Array[Team.Pitcher] = [(starters[rotation % starters.size()] as ProPlayer).arm]
	for p: ProPlayer in bullpen:
		arms.append(p.arm)
	school.team.pitchers = arms
	school.strength = strength
	return order


## 오늘 나온 투수들 (박스스코어 투수 번호 → 선수): 오늘 선발 + 불펜 순서
func pitchers_for_game() -> Array:
	return [starters[rotation % starters.size()]] + bullpen


func next_rotation() -> void:
	rotation = (rotation + 1) % maxi(1, starters.size())


func add_result(result: int, scored: int, allowed: int) -> void:
	runs += scored
	runs_allowed += allowed
	if result > 0:
		w += 1
		streak = streak + 1 if streak > 0 else 1
	elif result < 0:
		l += 1
		streak = streak - 1 if streak < 0 else -1
	else:
		d += 1


## 시즌을 닫는다 (순위·포스트시즌 결과를 역사에 남기고 성적을 비운다)
func close_season(year: int, rank: int, post_text: String) -> void:
	history.append({"year": year, "w": w, "l": l, "d": d, "rank": rank, "post": post_text})
	w = 0
	l = 0
	d = 0
	runs = 0
	runs_allowed = 0
	streak = 0
	rotation = 0


## 구단 한 시즌 최고 기록을 고친다 (시즌을 닫기 전에)
func update_bests(year: int, qualified_pa: int) -> void:
	for p: ProPlayer in all_players():
		if p.is_pitcher:
			_best("wins", p.name, p.pitching.wins, year)
			_best("so", p.name, p.pitching.so, year)
		else:
			_best("hr", p.name, p.season.home_runs, year)
			_best("hits", p.name, p.season.hits, year)
			if p.season.pa >= qualified_pa and p.season.ab > 0:
				_best("avg", p.name, float(p.season.hits) / p.season.ab, year)


func _best(key: String, who: String, v: float, year: int) -> void:
	if v <= 0:
		return
	if not bests.has(key) or v > float(bests[key]["value"]):
		bests[key] = {"name": who, "value": v, "year": year}
