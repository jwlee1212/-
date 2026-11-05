class_name FitnessTest
extends RefCounted
## 입부 첫날 체력 테스트 (로직만): 종목 등급 → 보상. 종목 하나하나는 RunTest · LiftTest · ReactTest, 화면은 FitnessView.
##
## 보상 (2026-11-01, 수치는 balance.json career.fitness):
##   ① 종목 등급마다 그 종목 능력치 +statGain (S +3 · A +2 · B +1 · C 0) — 50m 주력, 스쿼트 파워, 반응 선구안.
##      첫날 한 번뿐이라 반복 노가다가 없고, 잘한 종목이 곧 내 선수의 첫 개성이 된다
##   ② 세 종목 점수 합 → 종합 등급: 감독·코치 신뢰 · 평판, S 면 플래그 fitness_ace (뒤 이야기에서 "체력 테스트 1등"으로 쓸 수 있다)
##   못해도 깎이지 않는다 (첫날부터 벌주면 재미가 없다)
## 이벤트 효과 minigame: "fitness_test" 가 대화 끝에 CareerState.pending_minigame 을 세우면 홈이 FitnessView 를 띄운다.

const ID := "fitness_test"
## 미니게임 id 목록 (EventBook.validate 가 효과 값을 이걸로 검사한다)
## fitness_test = 세 종목을 한 번에, fitness_<종목> = 그 종목 하나 (시나리오가 종목 사이에 대사를 넣을 때)
const IDS := [ID, "fitness_run", "fitness_lift", "fitness_react"]
const KINDS := ["run", "lift", "react"]
const GRADES := ["S", "A", "B", "C"]


## 기록 → 등급. grades {"S": 값, "A": 값, "B": 값}, lower_is_better 면 값 이하일 때 그 등급, 아니면 값 이상일 때
static func grade_of(value: float, grades: Dictionary, lower_is_better: bool) -> String:
	if value < 0.0:
		return "C"
	for g: String in ["S", "A", "B"]:
		var limit := float(grades[g])
		if (lower_is_better and value <= limit) or (not lower_is_better and value >= limit):
			return g
	return "C"


## 종목 결과 {kind: {"grade", "value"}} 의 점수 합
static func points(cfg: Dictionary, results: Dictionary) -> int:
	var total := 0
	for kind: String in results:
		total += int(cfg["gradePoints"][results[kind]["grade"]])
	return total


## 종합 등급 줄 (balance.json career.fitness.overall 의 한 줄)
static func overall(cfg: Dictionary, results: Dictionary) -> Dictionary:
	var p := points(cfg, results)
	for row: Dictionary in cfg["overall"]:
		if p >= int(row["minPoints"]):
			return row
	return cfg["overall"][-1]


## 보상을 이벤트 효과 모양으로 (EventBook.apply_effects 에 그대로 넣는다)
static func reward_effects(cfg: Dictionary, results: Dictionary) -> Dictionary:
	var fx := {}
	for kind: String in cfg["order"]:
		if not results.has(kind):
			continue
		var gain := int(cfg["statGain"][results[kind]["grade"]])
		if gain != 0:
			var stat: String = cfg[kind]["stat"]
			fx[stat] = int(fx.get(stat, 0)) + gain
	var row := overall(cfg, results)
	for key in ["rel", "reputation"]:
		if row.has(key):
			fx[key] = row[key]
	fx["flag"] = ["fitness_done"] + ([row["flag"]] if row.has("flag") else [])
	return fx


## 종목별 보상만 (종목 등급 → 그 능력치 +statGain)
static func kind_effects(cfg: Dictionary, results: Dictionary) -> Dictionary:
	var fx := {}
	for kind: String in cfg["order"]:
		if not results.has(kind):
			continue
		var gain := int(cfg["statGain"][results[kind]["grade"]])
		if gain != 0:
			var stat: String = cfg[kind]["stat"]
			fx[stat] = int(fx.get(stat, 0)) + gain
	return fx


## 종합 등급 보상만 (세 종목이 다 모였을 때 한 번)
static func overall_effects(cfg: Dictionary, results: Dictionary) -> Dictionary:
	var fx := {}
	var row := overall(cfg, results)
	for key in ["rel", "reputation"]:
		if row.has(key):
			fx[key] = row[key]
	fx["flag"] = ["fitness_done"] + ([row["flag"]] if row.has("flag") else [])
	return fx


## 시나리오의 종합 등급 (docs/scenario EP01): 종목 등급을 high·mid·low 로 바꿔 점수 평균 → 기준 이상인 첫 등급
## (balance.json fitness.story: grades S→high…, points high 3…, overall [[2.5, high], [1.5, mid], [0, low]])
static func story_overall(cfg: Dictionary, results: Dictionary) -> String:
	var st: Dictionary = cfg["story"]
	var sum := 0.0
	for kind: String in cfg["order"]:
		sum += float(st["points"][st["grades"][results[kind]["grade"]]])
	var avg: float = sum / float((cfg["order"] as Array).size())
	for row: Array in st["overall"]:
		if avg >= float(row[0]):
			return row[1]
	return st["overall"][-1][1]


## 기록을 보기 좋게: "6.92초" · "110kg" · "312ms"
static func value_text(kind: String, value: float) -> String:
	match kind:
		"run":
			return "%.2f초" % value if value >= 0.0 else "기권"
		"lift":
			return "%dkg" % int(value) if value > 0.0 else "기록 없음"
		"react":
			return "%dms" % int(value)
	return str(value)


## 화면 없이 진행할 때의 결과 (테스트·빨리 감기): 모든 종목 autoGrade
static func auto_results(cfg: Dictionary, kinds: Array = []) -> Dictionary:
	var g: String = cfg["autoGrade"]
	var out := {}
	for kind: String in (kinds if not kinds.is_empty() else cfg["order"]):
		out[kind] = {"grade": g, "value": -1.0}
	return out
