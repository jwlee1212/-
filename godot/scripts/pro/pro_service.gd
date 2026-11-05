class_name ProService
extends RefCounted
## 병역 (docs/worldview.md 5-5, balance.json career.pro.military). 상태는 ProCareer.military 에 있고, 이 파일은 규칙만 (원칙 1).
##   none → (시즌 결산 카드: 상무 지원 · 현역 · 미루기) → 다음 시즌 시작에 입대 → sangmu | active → serviceWeeks 주 → done
##   아시안게임 금·올림픽 메달 → exempt (복무 중이면 조기 전역)
## 상무: 상무 팀(퓨처스 수준)으로 날마다 경기 (직접 하거나 시뮬레이션). 현역: 경기 없음, 주마다 능력치가 조금씩 녹슨다.
## 복무 중인 시즌은 FA 등록 시즌으로 세지 않는다 (ProFA).

const NONE := "none"
const ENLISTING := "enlisting"
const SANGMU := "sangmu"
const ACTIVE := "active"
const DONE := "done"
const EXEMPT := "exempt"
const STATUSES := [NONE, ENLISTING, SANGMU, ACTIVE, DONE, EXEMPT]


static func serving(pro: ProCareer) -> bool:
	return pro.military == SANGMU or pro.military == ACTIVE


## 아직 병역을 마치지 않았다 (면제·전역 아님)
static func due(pro: ProCareer) -> bool:
	return pro.military == NONE or pro.military == ENLISTING or serving(pro)


## 이야기 카드의 선택(이벤트 효과 military): "sangmu" 지원 · "active" 현역 · "delay" 미루기. 돌려주는 값: 변화 설명.
## 상무는 1군 통산 경기 수나 감독 신뢰로 심사한다 — 떨어지면 아무것도 안 바뀌고 다음 해에 다시 고른다
static func decide(career: CareerState, pro: ProCareer, kind: String) -> String:
	var mc: Dictionary = career.cfg.pro["military"]
	match kind:
		SANGMU:
			var games := pro.me.career.games + pro.me.season.games
			if games >= int(mc["sangmuMinGames"]) or pro.trust >= int(mc["sangmuMinTrust"]):
				pro.enlist_kind = SANGMU
				_set_status(career, pro, ENLISTING)
				return "상무 합격 — 다음 시즌 입대"
			return "상무 불합격 (1군 %d경기 · 신뢰 %d) — 내년에 다시" % [games, pro.trust]
		ACTIVE:
			pro.enlist_kind = ACTIVE
			_set_status(career, pro, ENLISTING)
			return "현역 입대 — 다음 시즌"
	return "입대를 미뤘다"


## 새 시즌 시작 (ProCareer.start_next_season): 예정된 입대 · 나이가 차서 강제 입대
static func season_start(career: CareerState, pro: ProCareer, notes: Array[String]) -> void:
	var mc: Dictionary = career.cfg.pro["military"]
	if pro.military == NONE and pro.me.age >= int(mc["forcedAge"]):
		pro.enlist_kind = ACTIVE
		_set_status(career, pro, ENLISTING)
		notes.append("만 %d세 — 현역 입대 통지" % int(mc["forcedAge"]))
	if pro.military == ENLISTING:
		_set_status(career, pro, pro.enlist_kind)
		pro.service_weeks_left = int(mc["serviceWeeks"])
		pro.level = ProCareer.LEVEL_MILITARY
		pro.trust = int(career.cfg.pro["trust"]["afterService"])
		notes.append("%s 입대" % ("국군체육부대(상무)" if pro.military == SANGMU else "현역"))
		career.player.flags["enlisted"] = true
		pro.league.week_events.append({"kind": "enlist", "title": "상무 입대" if pro.military == SANGMU else "현역 입대"})
		Milestones.fire_event(career, pro, "enlist")


## 복무 중 한 주 (정규시즌 주만 — 한 주의 마지막 경기 날 뒤). 상무 경기는 날마다 내 경기로 (ProCareer.game_today "sangmu"),
## 여기서는 현역의 녹슮과 남은 복무 주만 센다
static func week(career: CareerState, pro: ProCareer, notes: Array[String]) -> void:
	if not serving(pro):
		return
	var cfg := career.cfg
	var mc: Dictionary = cfg.pro["military"]
	var p := career.player
	if pro.military == ACTIVE:
		var rng := career.rng_for("active-duty")
		if rng.randf() < float(mc["activeDecayChance"]):
			var st: String = PlayerData.STATS[rng.randi_range(0, PlayerData.STATS.size() - 1)]
			p.add_stat(st, -1, cfg)
			notes.append("훈련소·부대 생활 — %s −1" % PlayerData.STAT_LABELS[st])
	pro.service_weeks_left -= 1
	if pro.service_weeks_left <= 0:
		discharge(career, pro, notes, "전역")


## 전역 (복무를 마쳤거나 메달로 면제). 퓨처스로 돌아와 감독 신뢰로 다시 올라간다
static func discharge(career: CareerState, pro: ProCareer, notes: Array[String], why: String) -> void:
	_set_status(career, pro, EXEMPT if why != "전역" else DONE)
	pro.service_weeks_left = 0
	pro.level = ProCareer.LEVEL_FUTURES
	pro.watch_games = 0
	notes.append("%s! 퓨처스에서 다시 시작한다" % why)
	career.player.flags["discharged"] = true
	pro.league.week_events.append({"kind": "discharge", "title": why})
	Milestones.fire_event(career, pro, "discharge")


## 메달로 면제 (ProNational): 아직 안 갔으면 면제, 복무 중이면 조기 전역
static func exempt(career: CareerState, pro: ProCareer, notes: Array[String]) -> void:
	if not due(pro):
		return
	if serving(pro):
		discharge(career, pro, notes, "병역 특례 — 조기 전역")
	else:
		_set_status(career, pro, EXEMPT)
		pro.enlist_kind = ""
		notes.append("병역 특례 — 면제")
	career.player.flags["military_exempt"] = true
	Milestones.fire_event(career, pro, "exempt")


## 상무 팀 (리그와 함께 저장 — 퓨처스 수준 명단)
static func sangmu_school(career: CareerState, pro: ProCareer) -> School:
	if pro.sangmu == null:
		pro.sangmu = School.create("sangmu", "국군체육부대", "상무", float(career.cfg.pro["military"]["sangmuStrength"]), "balanced",
			career.cfg, career.rng_for("sangmu-roster"))
	return pro.sangmu


static func _set_status(career: CareerState, pro: ProCareer, s: String) -> void:
	pro.military = s
	career.player.vars["military_status"] = s
