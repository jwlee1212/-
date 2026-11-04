class_name CutscenePlan
extends RefCounted
## 컷신 진행 (로직만 — 화면은 CutsceneView). 컷신 정의(content.json cutscenes)와 지난 시간(초)만으로
## "지금 무엇을 그릴지"를 낸다: 어느 컷(판)인지, 카메라 자리, 인물 칸·자리, 번쩍임·암전·흔들림, 자막, 소리.
## 같은 시간이면 언제나 같은 그림이다 (프레임 수를 세지 않는다 — 원칙 4, 흔들림도 시간으로 정한 가짜 난수 — 원칙 2).
##
## 컷 하나: {"panel": 판 이름, "dur": 초, "cam": [[t, x, y]…] (판 위 카메라 왼쪽 위, 그림 픽셀 — 키 사이는 부드럽게 잇는다),
##   "actors": [{"row": 인물 줄, "at": [x, y] 또는 "path": [[t, x, y]…] (발 자리), "frames": [[t, 장]…] (그 시각부터 그 장)
##               또는 "fps": n + "loop": [장…] (되풀이), "from"/"to": 이 시각 사이에만 보인다}],
##   "captions": [{"from", "to", "who"?, "text"}], "hold": [t, 초] (멈춤 — 맞는 순간 히트스톱: 그동안 인물·카메라 시간이 멈춘다),
##   "flash": [t, 초] (번쩍임이 1 → 0), "shake": [t, 초, 세기(그림 픽셀)], "fadeIn"/"fadeOut": 초 (컷 앞뒤 암전),
##   "sfx": [[t, 소리 이름(SoundSynth.Sfx), 크기?, 빠르기?]…],
##   "camLinear": true (카메라 키 사이를 같은 빠르기로 — 달리는 사람을 따라 흐를 때), 인물의 "linear": true (그 인물 path 도 같은 빠르기로)}
## t 는 모두 컷 시작부터의 초. hold 가 있으면 인물·카메라는 "멈춘 시간을 뺀 시각"을 본다 (자막·소리·번쩍임은 실제 시각).

## 정의 전체 길이 (초)
static func duration(cs: Dictionary) -> float:
	var total := 0.0
	for shot: Dictionary in cs["shots"]:
		total += float(shot["dur"])
	return total


## 시각 t 가 몇 번째 컷의 몇 초인가 → [컷 번호, 컷 안 시각]. 끝을 넘으면 마지막 컷 끝
static func locate(cs: Dictionary, t: float) -> Array:
	var shots: Array = cs["shots"]
	var start := 0.0
	for i in shots.size():
		var d := float(shots[i]["dur"])
		if t < start + d or i == shots.size() - 1:
			return [i, clampf(t - start, 0.0, d)]
		start += d
	return [0, 0.0]


## 컷이 시작하는 시각 (초)
static func shot_start(cs: Dictionary, index: int) -> float:
	var start := 0.0
	for i in index:
		start += float(cs["shots"][i]["dur"])
	return start


## 지금 그릴 것. 돌려주는 값:
##   {"shot", "panel", "cam": Vector2i, "actors": [{"row", "frame", "pos": Vector2i}], "flash": 0~1, "fade": 0~1 (암전), "captions": [{"who", "text"}]}
static func frame(cs: Dictionary, t: float) -> Dictionary:
	var at := locate(cs, t)
	var shot: Dictionary = cs["shots"][at[0]]
	var lt: float = at[1]
	var mt := motion_time(shot, lt)
	var cam := _path(shot.get("cam", [[0, 0, 0]]), mt, bool(shot.get("camLinear", false)))
	if shot.has("shake"):
		var sk: Array = shot["shake"]
		var k := (lt - float(sk[0])) / float(sk[1])
		if k >= 0.0 and k < 1.0:
			var amp := float(sk[2]) * (1.0 - k)
			var n := int(lt * 40.0)  # 1/40 초마다 다른 칸 — 시간으로 정한 가짜 난수
			cam += Vector2(roundf(amp * _wiggle(n, 1)), roundf(amp * _wiggle(n, 2)))
	var actors := []
	for a: Dictionary in shot.get("actors", []):
		if lt < float(a.get("from", 0.0)) or lt >= float(a.get("to", 1e9)):
			continue
		var pos: Vector2 = _path(a["path"], mt, bool(a.get("linear", false))) if a.has("path") else Vector2(float(a["at"][0]), float(a["at"][1]))
		actors.append({"row": a["row"], "frame": _cell(a, mt), "pos": Vector2i(roundi(pos.x), roundi(pos.y))})
	var flash := 0.0
	if shot.has("flash"):
		var fl: Array = shot["flash"]
		var k := (lt - float(fl[0])) / float(fl[1])
		if k >= 0.0 and k < 1.0:
			flash = 1.0 - k
	var fade := 0.0
	var fin := float(shot.get("fadeIn", 0.0))
	var fout := float(shot.get("fadeOut", 0.0))
	if fin > 0.0 and lt < fin:
		fade = 1.0 - lt / fin
	if fout > 0.0 and lt > float(shot["dur"]) - fout:
		fade = maxf(fade, (lt - (float(shot["dur"]) - fout)) / fout)
	var caps := []
	for c: Dictionary in shot.get("captions", []):
		if lt >= float(c["from"]) and lt < float(c["to"]):
			caps.append({"who": c.get("who", ""), "text": c["text"]})
	return {"shot": at[0], "panel": shot["panel"], "cam": Vector2i(roundi(cam.x), roundi(cam.y)), "actors": actors,
		"flash": flash, "fade": clampf(fade, 0.0, 1.0), "captions": caps}


## 인물·카메라가 보는 시각: 멈춤(hold) 동안은 시간이 흐르지 않는다
static func motion_time(shot: Dictionary, lt: float) -> float:
	if not shot.has("hold"):
		return lt
	var h: Array = shot["hold"]
	return lt - clampf(lt - float(h[0]), 0.0, float(h[1]))


## (t0, t1] 사이에 울릴 소리들 (전체 시각 기준). [{"sfx": 이름, "volume", "pitch"}]
static func cues(cs: Dictionary, t0: float, t1: float) -> Array:
	var out := []
	var shots: Array = cs["shots"]
	var start := 0.0
	for shot: Dictionary in shots:
		for s: Array in shot.get("sfx", []):
			var at := start + float(s[0])
			if at > t0 and at <= t1:
				out.append({"sfx": s[1], "volume": float(s[2]) if s.size() > 2 else 1.0, "pitch": float(s[3]) if s.size() > 3 else 1.0})
		start += float(shot["dur"])
	return out


## 정의 검사: 판·인물 줄·장 번호가 그림에 있고, 시각이 컷 길이 안, 소리 이름이 있다. panels/rows 는 CutsceneMeta 의 것
static func validate(id: String, cs: Dictionary, panels: Dictionary, rows: Dictionary) -> Array[String]:
	var errors: Array[String] = []
	var shots: Array = cs.get("shots", [])
	if shots.is_empty():
		errors.append("컷신 %s: 컷이 없다" % id)
	for i in shots.size():
		var shot: Dictionary = shots[i]
		var where := "컷신 %s 컷 %d" % [id, i + 1]
		var dur := float(shot.get("dur", 0.0))
		if dur <= 0.0:
			errors.append("%s: dur" % where)
		if not panels.has(shot.get("panel", "")):
			errors.append("%s: 판 %s 없음" % [where, shot.get("panel", "")])
		for a: Dictionary in shot.get("actors", []):
			var row: String = a.get("row", "")
			if not rows.has(row):
				errors.append("%s: 인물 줄 %s 없음" % [where, row])
				continue
			var count := int(rows[row][1])
			var used: Array = a.get("loop", [])
			for f: Array in a.get("frames", []):
				used.append(f[1])
			for f in used:
				if int(f) < 0 or int(f) >= count:
					errors.append("%s: %s 장 %d 없음 (%d장)" % [where, row, int(f), count])
			if not a.has("at") and not a.has("path"):
				errors.append("%s: %s 자리(at·path) 없음" % [where, row])
		for c: Dictionary in shot.get("captions", []):
			if float(c["from"]) >= float(c["to"]) or float(c["to"]) > dur + 0.001 or str(c.get("text", "")) == "":
				errors.append("%s: 자막 시각·글자" % where)
		for s: Array in shot.get("sfx", []):
			if not SoundSynth.Sfx.has(str(s[1])):
				errors.append("%s: 소리 %s 없음" % [where, s[1]])
			if float(s[0]) < 0.0 or float(s[0]) > dur:
				errors.append("%s: 소리 시각" % where)
	return errors


# ---------- 안쪽 ----------

## 키 [[t, x, y]…] 사이를 부드럽게 (가속·감속) 잇는다 — linear 면 같은 빠르기로. 키가 하나면 그 자리
static func _path(keys: Array, t: float, linear := false) -> Vector2:
	var first: Array = keys[0]
	if keys.size() == 1 or t <= float(first[0]):
		return Vector2(float(first[1]), float(first[2]))
	for i in range(1, keys.size()):
		var b: Array = keys[i]
		if t <= float(b[0]):
			var a: Array = keys[i - 1]
			var k := (t - float(a[0])) / maxf(float(b[0]) - float(a[0]), 0.0001)
			if not linear:
				k = k * k * (3.0 - 2.0 * k)
			return Vector2(lerpf(float(a[1]), float(b[1]), k), lerpf(float(a[2]), float(b[2]), k))
	var last: Array = keys[-1]
	return Vector2(float(last[1]), float(last[2]))


## 지금 장: frames [[t, 장]…] 이면 그 시각을 지난 마지막 것, fps + loop 이면 되풀이
static func _cell(a: Dictionary, t: float) -> int:
	if a.has("loop"):
		var loop: Array = a["loop"]
		return int(loop[int(floorf(t * float(a.get("fps", 8)))) % loop.size()])
	var got := 0
	for f: Array in a.get("frames", [[0, 0]]):
		if t >= float(f[0]):
			got = int(f[1])
	return got


## -1~1 사이 가짜 난수 (번호로 정해진다)
static func _wiggle(n: int, salt: int) -> float:
	var h := (n * 73856093) ^ (salt * 19349663)
	h = (h ^ (h >> 13)) * 1274126177
	return float(absi(h) % 2001) / 1000.0 - 1.0
