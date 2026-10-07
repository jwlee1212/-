class_name PlayerData
extends RefCounted
## 내 선수. 능력치 0~99, 컨디션 0~100, 스카우트 관심도 0~100.

const STATS := ["contact", "power", "eye", "speed"]
const STAT_LABELS := {"contact": "컨택", "power": "파워", "eye": "선구안", "speed": "주력"}


## 시즌 누적 기록
class SeasonStats:
	var games := 0
	var pa := 0
	var ab := 0
	var hits := 0
	var doubles := 0
	var triples := 0
	var home_runs := 0
	var rbi := 0
	var walks := 0
	var strikeouts := 0
	var sac_flies := 0
	var sac_bunts := 0
	## 인플레이 타구 수와 타구 속도 합 (기록실 분할 기록의 평균 타구 속도용. 타구 물리가 있는 내 타석만 센다)
	var balls_in_play := 0
	var ev_sum := 0.0

	## sac_fly: 희생플라이였는가 (타수에 넣지 않는다)
	func add(outcome: SwingJudge.Outcome, runs_batted_in: int, sac_fly: bool = false) -> void:
		pa += 1
		rbi += runs_batted_in
		if sac_fly:
			sac_flies += 1
			return
		match outcome:
			SwingJudge.Outcome.WALK:
				walks += 1
				return
			SwingJudge.Outcome.SAC_BUNT:
				sac_bunts += 1
				return  # 희생번트는 타수에 넣지 않는다
			SwingJudge.Outcome.STRIKEOUT:
				strikeouts += 1
			SwingJudge.Outcome.DOUBLE:
				doubles += 1
			SwingJudge.Outcome.TRIPLE:
				triples += 1
			SwingJudge.Outcome.HOME_RUN:
				home_runs += 1
		ab += 1
		if SwingJudge.is_hit(outcome):
			hits += 1

	## 다른 기록을 더한다 (경기 한 줄 → 시즌)
	func merge(o: SeasonStats) -> void:
		games += o.games
		pa += o.pa
		ab += o.ab
		hits += o.hits
		doubles += o.doubles
		triples += o.triples
		home_runs += o.home_runs
		rbi += o.rbi
		walks += o.walks
		strikeouts += o.strikeouts
		sac_flies += o.sac_flies
		sac_bunts += o.sac_bunts
		balls_in_play += o.balls_in_play
		ev_sum += o.ev_sum

	## 인플레이 타구 하나의 속도를 더한다
	func add_ball(ev_kmh: float) -> void:
		balls_in_play += 1
		ev_sum += ev_kmh

	## 평균 타구 속도 (km/h, 타구가 없으면 -1)
	func avg_ev() -> float:
		return ev_sum / balls_in_play if balls_in_play > 0 else -1.0

	## 박스스코어 한 줄 → 기록 (한 경기)
	static func from_box(b: BoxScore.BatterLine) -> SeasonStats:
		var s := SeasonStats.new()
		s.games = 1
		s.pa = b.pa
		s.ab = b.ab
		s.hits = b.h
		s.doubles = b.doubles
		s.triples = b.triples
		s.home_runs = b.hr
		s.rbi = b.rbi
		s.walks = b.bb
		s.strikeouts = b.so
		s.sac_flies = b.sf
		s.sac_bunts = b.sh
		return s

	## "4타수 2안타 1홈런" 처럼 짧게
	func line_text() -> String:
		var parts := ["%d타수 %d안타" % [ab, hits]]
		if home_runs > 0:
			parts.append("%d홈런" % home_runs)
		if rbi > 0:
			parts.append("%d타점" % rbi)
		if walks > 0:
			parts.append("%d볼넷" % walks)
		return " ".join(parts)

	## 출루율 = (안타 + 볼넷) / (타수 + 볼넷 + 희생플라이). 몸에 맞는 공은 아직 없다
	func obp() -> float:
		var d := ab + walks + sac_flies
		return float(hits + walks) / float(d) if d > 0 else 0.0

	## 루타
	func total_bases() -> int:
		return hits + doubles + 2 * triples + 3 * home_runs

	func slg() -> float:
		return float(total_bases()) / float(ab) if ab > 0 else 0.0

	## OPS+ = 100 × (출루율/리그 출루율 + 장타율/리그 장타율 − 1). 타석이 없으면 -1
	func ops_plus(league: Dictionary) -> int:
		if ab == 0:
			return -1
		return roundi(100.0 * (obp() / float(league["obp"]) + slg() / float(league["slg"]) - 1.0))

	func average() -> String:
		return rate_text(float(hits) / ab) if ab > 0 else "-"

	func obp_text() -> String:
		return rate_text(obp()) if ab + walks + sac_flies > 0 else "-"

	func slg_text() -> String:
		return rate_text(slg()) if ab > 0 else "-"

	## OPS = 출루율 + 장타율 (몸에 맞는 공은 아직 없다)
	func ops() -> String:
		return rate_text(obp() + slg()) if ab > 0 else "-"

	func ops_plus_text(league: Dictionary) -> String:
		var v := ops_plus(league)
		return str(v) if v >= 0 else "-"

	## 비율 기록 글자: .312 / 1.045
	static func rate_text(v: float) -> String:
		var n := roundi(v * 1000.0)
		return "%d.%03d" % [n / 1000, n % 1000] if n >= 1000 else ".%03d" % n


var name := "신인"
var grade := 1
var contact := 40
var power := 40
var eye := 40
## 주력 (아직 훈련으로 오르지 않음)
var speed := 50
var condition := 80
var scout_interest := 5
## 평판 (인성·언론 이미지, 0~100). 스카우트 평가에 들어간다
var reputation := 50
## 돈 (원)
var money := 0
## 산 장비 (상점 gear id -> true)
var gear := {}
## 타순 (1~9)
var lineup_slot := 7
## 이벤트가 남긴 표시 (나중에 결과가 돌아오는 이벤트용)
var flags := {}
var season := SeasonStats.new()
## 숨겨진 잠재력 (능력치 → 최대치). 화면에는 "성장 여력" 말로만 보인다
var potential := {}
## 소수점 성장 누적 (능력치 → 0~1)
var progress := {}
## 폼 (−3 슬럼프 ~ +3 상승세)
var form := 0.0
## 남은 부상 주 수 (0 이면 건강)
var injury_weeks := 0
## 시즌 시작 때 능력치 (시즌 결산에서 성장 비교)
var season_start := {}


static func create(player_name: String, cfg: CareerConfig) -> PlayerData:
	var p := PlayerData.new()
	p.name = player_name
	for stat in STATS:
		p.set(stat, int(cfg.start[stat]))
	p.speed = int(cfg.start.get("speed", 50))
	p.condition = int(cfg.start["condition"])
	p.scout_interest = int(cfg.start["scoutInterest"])
	p.lineup_slot = int(cfg.start["lineupSlot"])
	return p


## 잠재력 정하기 (커리어 시작 때 한 번)
func roll_potentials(cfg: CareerConfig, rng: RandomNumberGenerator) -> void:
	for s in STATS:
		potential[s] = Growth.roll_potential(cfg, stat(s), rng)
	mark_season_start()


func mark_season_start() -> void:
	for s in STATS:
		season_start[s] = stat(s)


func is_injured() -> bool:
	return injury_weeks > 0


## 종합 능력치 (네 능력치 평균, 반올림)
func overall() -> int:
	var sum := 0
	for s in STATS:
		sum += stat(s)
	return roundi(float(sum) / STATS.size())


func stat(id: String) -> int:
	return get(id)


func add_stat(id: String, delta: int, cfg: CareerConfig) -> void:
	set(id, clampi(stat(id) + delta, 1, cfg.stat_max))


func add_condition(delta: int) -> void:
	condition = clampi(condition + delta, 0, 100)


func add_scout(delta: int) -> void:
	scout_interest = clampi(scout_interest + delta, 0, 100)


func add_reputation(delta: int) -> void:
	reputation = clampi(reputation + delta, 0, 100)


## 잠재력 평균 (숨김 값 — 스카우트 평가·잠재력 범위 표시에만 쓴다)
func potential_average() -> float:
	var sum := 0.0
	for s in STATS:
		sum += float(potential.get(s, stat(s)))
	return sum / STATS.size()


## 경기에서 쓰는 능력치. 컨디션·폼이 좋으면 조금 오르고 나쁘면 내려간다 — 판정 폭으로 손에 느껴진다. 장비 보정도 더한다
func skills_for_game(cfg: CareerConfig) -> BatterSkills:
	var bonus := (condition - cfg.condition_pivot) * cfg.condition_per_point + form * float(cfg.form["skillPerPoint"])
	var g := gear_bonus(cfg)
	return BatterSkills.new(
		clampi(roundi(contact + bonus + g.get("contact", 0)), 1, 99), clampi(roundi(power + bonus + g.get("power", 0)), 1, 99),
		clampi(roundi(eye + bonus + g.get("eye", 0)), 1, 99), clampi(speed + int(g.get("speed", 0)), 1, 99))


## 장비 보정 {능력치: +n}. 같은 자리(slot)는 등급(tier)이 가장 높은 것 하나만
func gear_bonus(cfg: CareerConfig) -> Dictionary:
	var best := {}  # slot -> 장비 정의
	for id: String in gear:
		var item: Dictionary = cfg.shop["gear"][id]
		var slot: String = item["slot"]
		if not best.has(slot) or int(item["tier"]) > int(best[slot]["tier"]):
			best[slot] = item
	var out := {}
	for slot: String in best:
		var b: Dictionary = best[slot]["bonus"]
		for s: String in b:
			out[s] = int(out.get(s, 0)) + int(b[s])
	return out


## "12.5만원", "8천원"
static func money_text(won: int) -> String:
	var sign := "-" if won < 0 else ""
	var w := absi(won)
	if w >= 10000:
		var man := w / 10000.0
		return sign + ("%d만원" % int(man) if w % 10000 == 0 else "%.1f만원" % man)
	if w >= 1000:
		return sign + "%d천원" % (w / 1000)
	return sign + "%d원" % w
