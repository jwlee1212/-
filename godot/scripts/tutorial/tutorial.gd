class_name Tutorial
extends RefCounted
## 튜토리얼 (2026-11-02): 조작을 처음 만나는 순간 한 번만 팁 카드를 띄운다.
## 팁 내용은 content.json "tutorial" (id → 제목·단계 몇 줄), 이미 본 팁은 기기 설정(Settings.seen_tips)에 남는다 —
## 커리어를 새로 시작해도 다시 나오지 않고, 설정의 [튜토리얼 다시 보기]로 지운다.
## 팁이 뜨는 자리 (화면이 부른다):
##   HOME     — 홈 화면에 처음 왔을 때 (입부 이야기·체력 테스트가 끝난 뒤)
##   BATTING  — 첫 내 타석 직전
##   FIELDING — 첫 유격수 수비 직전
##   STEAL    — 첫 도루 기회 직전
##   RUNNING  — 루상에서 처음 내 주루 판단이 올 때

const HOME := "home"
const BATTING := "batting"
const FIELDING := "fielding"
const STEAL := "steal"
const RUNNING := "running"
const IDS := [HOME, BATTING, FIELDING, STEAL, RUNNING]
## 카드 한 장에 들어가는 단계 수 (가로 390px 화면에 넘치지 않게)
const MAX_STEPS := 4


## 지금 이 팁을 보여 줘야 하나: 내용이 있고, 아직 안 봤다
static func pending(tips: Dictionary, settings: Settings, id: String) -> bool:
	return tips.has(id) and not settings.has_seen(id)


## 팁 내용 검사: 문제 목록 (비어 있으면 통과)
static func validate(tips: Dictionary) -> Array[String]:
	var errors: Array[String] = []
	for id: String in IDS:
		if not tips.has(id):
			errors.append("팁 없음: %s" % id)
	for id: String in tips:
		if id.begins_with("_"):
			continue
		if not id in IDS:
			errors.append("쓰이지 않는 팁: %s" % id)
			continue
		var t: Variant = tips[id]
		if not t is Dictionary or str(t.get("title", "")) == "":
			errors.append("%s: 제목이 없다" % id)
			continue
		var steps: Variant = t.get("steps", [])
		if not steps is Array or steps.is_empty() or steps.size() > MAX_STEPS:
			errors.append("%s: 단계는 1~%d줄" % [id, MAX_STEPS])
			continue
		for s: Variant in steps:
			if not s is String or (s as String).strip_edges() == "":
				errors.append("%s: 빈 단계" % id)
	return errors
