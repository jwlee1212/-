class_name CareerRecords
extends RefCounted
## 기록실: 경기별 기록(게임 로그), 대회별 내 성적, 분할 기록(투수 성향·카운트·타구 종류·방향), 최고 기록.
## CareerState 가 경기가 끝날 때마다 add_game 으로 채운다. 화면은 읽기만 한다 (원칙 1).
## 시즌 구분은 season_no(1부터)로 한다. 학년은 3에서 멈추지만 시즌 번호는 계속 오른다.

## 분할 기록 종류와 칸 순서 (투수 성향 칸은 balance.json 투수 순서를 따르므로 여기 없다)
const COUNT_KEYS := ["first", "ahead", "even", "two_strikes"]
const COUNT_LABELS := {"first": "초구 (0-0)", "ahead": "타자 유리", "even": "0-1 · 1-1", "two_strikes": "2스트라이크"}
const BATTED_KEYS := ["ground", "line", "fly"]
const BATTED_LABELS := {"ground": "땅볼", "line": "라인드라이브", "fly": "뜬공"}
const DIRECTION_KEYS := ["pull", "center", "oppo"]
const DIRECTION_LABELS := {"pull": "당겨친 타구", "center": "가운데", "oppo": "밀어친 타구"}


## 한 경기 기록
class GameEntry:
	var season_no := 1
	var grade := 1
	var week := 1
	## 대회 이름 ("봄 주말리그" 등)과 라운드 ("3/6", "8강")
	var phase_name := ""
	var round := ""
	var national := false
	var opponent := ""
	var home := false
	var my_score := 0
	var opp_score := 0
	## 1 승, 0 무, -1 패
	var result := 0
	## 내가 나왔나 (부상이면 결장)
	var playing := true
	var line := PlayerData.SeasonStats.new()

	## "승 5:3"
	func score_text() -> String:
		return "%s %d:%d" % [["패", "무", "승"][result + 1], my_score, opp_score]

	## "4타수 2안타 1타점" (결장이면 "결장")
	func line_text() -> String:
		return line.line_text() if playing else "결장"


## 한 시즌의 한 단계 (주말리그 전반기, 전국대회 …) 의 내 성적
class PhaseEntry:
	var season_no := 1
	var grade := 1
	var name := ""
	var national := false
	var line := PlayerData.SeasonStats.new()
	## 팀 경기 수·승·패·무
	var team_games := 0
	var wins := 0
	var losses := 0
	var draws := 0
	## 단계 결과 ("2위 (4승 2패 0무)", "우승!", "8강 탈락"). "" 이면 아직 진행 중
	var result := ""


var games: Array[GameEntry] = []
var phases: Array[PhaseEntry] = []
## season_no -> {"pitcher" | "count" | "batted" | "direction" -> {칸 -> SeasonStats}}
var splits := {}

## 가운데로 보는 타구 방향 폭 (도)
var _center_deg := 15.0


func _init(cfg: CareerConfig = null) -> void:
	if cfg != null:
		_center_deg = float(cfg.records["centerSprayDeg"])


## 경기 하나를 넣는다. pas: GameRunner.my_pas (내 타석들)
func add_game(entry: GameEntry, pas: Array[Dictionary]) -> void:
	games.append(entry)
	var ph := _phase_for(entry)
	ph.team_games += 1
	match entry.result:
		1: ph.wins += 1
		-1: ph.losses += 1
		_: ph.draws += 1
	if not entry.playing:
		return
	ph.line.merge(entry.line)
	for pa: Dictionary in pas:
		_add_split(entry.season_no, pa)


## 지금 단계가 끝났다 (결과 문구를 남긴다)
func close_phase(result_text: String) -> void:
	if not phases.is_empty():
		phases[-1].result = result_text


func _phase_for(entry: GameEntry) -> PhaseEntry:
	if not phases.is_empty():
		var last: PhaseEntry = phases[-1]
		if last.result == "" and last.season_no == entry.season_no and last.name == entry.phase_name:
			return last
	var ph := PhaseEntry.new()
	ph.season_no = entry.season_no
	ph.grade = entry.grade
	ph.name = entry.phase_name
	ph.national = entry.national
	phases.append(ph)
	return ph


# ---------- 분할 기록 ----------

func _add_split(season_no: int, pa: Dictionary) -> void:
	if not splits.has(season_no):
		splits[season_no] = {"pitcher": {}, "count": {}, "batted": {}, "direction": {}}
	var s: Dictionary = splits[season_no]
	var keys := {"pitcher": String(pa.get("pitcher", "")), "count": count_key(int(pa.get("balls", -1)), int(pa.get("strikes", -1)))}
	var batted: SwingJudge.BattedBall = pa.get("batted", SwingJudge.BattedBall.NONE)
	if batted != SwingJudge.BattedBall.NONE:
		keys["batted"] = batted_key(batted)
		keys["direction"] = direction_key(float(pa.get("spray", 0.0)), _center_deg)
	for cat: String in keys:
		var k: String = keys[cat]
		if k == "":
			continue
		if not s[cat].has(k):
			s[cat][k] = PlayerData.SeasonStats.new()
		var line: PlayerData.SeasonStats = s[cat][k]
		line.add(pa["outcome"], int(pa.get("rbi", 0)), bool(pa.get("sac_fly", false)))
		if cat in ["batted", "direction"]:
			line.add_ball(float(pa.get("ev", 0.0)))


## 결정구 때 카운트 → 칸. 모르면 ""
static func count_key(balls: int, strikes: int) -> String:
	if balls < 0 or strikes < 0:
		return ""
	if balls == 0 and strikes == 0:
		return "first"
	if strikes >= 2:
		return "two_strikes"
	if balls > strikes:
		return "ahead"
	return "even"


static func batted_key(b: SwingJudge.BattedBall) -> String:
	match b:
		SwingJudge.BattedBall.GROUND: return "ground"
		SwingJudge.BattedBall.LINE: return "line"
		SwingJudge.BattedBall.FLY: return "fly"
	return ""


## 타구 방향 (음수 = 3루 쪽 = 우타자가 당겨친 타구)
static func direction_key(spray_deg: float, center_deg: float) -> String:
	if spray_deg < -center_deg:
		return "pull"
	if spray_deg > center_deg:
		return "oppo"
	return "center"


## 분할 기록 표. season_no = 0 이면 통산. order: 칸 순서. 돌려주는 값: [[칸, SeasonStats], ...] (기록이 없는 칸도 빈 줄로)
func split_rows(season_no: int, category: String, order: Array) -> Array:
	var rows := []
	for k: String in order:
		var line := PlayerData.SeasonStats.new()
		for sn: int in splits:
			if season_no == 0 or sn == season_no:
				var cat: Dictionary = splits[sn][category]
				if cat.has(k):
					line.merge(cat[k])
		rows.append([k, line])
	return rows


# ---------- 경기·대회 ----------

## 경기 기록 (season_no = 0 이면 전부). 최근 경기부터
func game_log(season_no: int = 0) -> Array[GameEntry]:
	var out: Array[GameEntry] = []
	for i in range(games.size() - 1, -1, -1):
		if season_no == 0 or games[i].season_no == season_no:
			out.append(games[i])
	return out


## 대회별 기록 중 전국대회만 (national_only) 또는 전부. 최근 것부터
func phase_log(national_only: bool = false) -> Array[PhaseEntry]:
	var out: Array[PhaseEntry] = []
	for i in range(phases.size() - 1, -1, -1):
		if not national_only or phases[i].national:
			out.append(phases[i])
	return out


# ---------- 최고 기록 ----------

## 커리어 최고 기록: {"hits": GameEntry, "rbi": GameEntry, "home_runs": GameEntry (없으면 null),
##   "multi_hit": 멀티히트 경기 수, "streak": 최장 연속 경기 안타, "current_streak": 지금 이어지는 연속 경기 안타}
## 연속 경기 안타는 나온 경기 기준이고, 타수가 없는 경기(볼넷만)는 끊지도 늘리지도 않는다
func bests() -> Dictionary:
	var out := {"hits": null, "rbi": null, "home_runs": null, "multi_hit": 0, "streak": 0, "current_streak": 0}
	var streak := 0
	for g: GameEntry in games:
		if not g.playing:
			continue
		var l := g.line
		for stat: String in ["hits", "rbi", "home_runs"]:
			var v: int = l.get(stat)
			var best: GameEntry = out[stat]
			if v > 0 and (best == null or v > int(best.line.get(stat))):
				out[stat] = g
		if l.hits >= 2:
			out["multi_hit"] += 1
		if l.ab == 0:
			continue
		streak = streak + 1 if l.hits > 0 else 0
		out["streak"] = maxi(out["streak"], streak)
	out["current_streak"] = streak
	return out
