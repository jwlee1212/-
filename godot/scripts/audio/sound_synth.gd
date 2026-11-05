class_name SoundSynth
extends RefCounted
## 효과음 합성기. 음원 파일 없이 사인파·잡음·감쇠 곡선을 섞어 만든다 (저작권 걱정 없음, 용량 0).
## 잡음은 고정 시드 난수로 만든다 — 실행할 때마다 같은 소리가 난다.

enum Sfx {
	PITCH,        ## 투수가 공을 놓는 순간
	SWING,
	CRACK_SOLID,  ## 정타 "딱!"
	CRACK_HOME_RUN,  ## 홈런 "딱!!" (정타보다 크고 꽉 찬 소리)
	CRACK_FOUL,   ## 파울 "틱"
	MITT,         ## 포수 미트 "팡"
	CHEER_SMALL,  ## 안타 함성 (짧게)
	CHEER_BIG,    ## 홈런 함성
	CALL_STRIKE,     ## 심판 "스트라이크!" (파일만)
	CALL_STRIKEOUT,  ## 심판 "스트라익~ 아웃!" (파일만)
	STEP,     ## 미니게임: 트랙 발소리 "탁"
	WHISTLE,  ## 미니게임: 출발 호루라기 "삑—"
	CLANK,    ## 미니게임: 바벨 원판 "철컥"
	BEEP,     ## 미니게임: 신호등이 켜지는 "삑"
	BUZZ,     ## 미니게임: 실패 "부—"
}

## 실행 중 합성하지 않고 audio/sfx/ 파일로만 있는 소리 (scripts/make_bgm.py 가 만든다)
const FILE_ONLY: Array[Sfx] = [Sfx.CALL_STRIKE, Sfx.CALL_STRIKEOUT]

## 타격음·미트 소리의 높은 대역(딱! 하고 갈라지는 소리)을 살리려고 44.1kHz (2026-10-31, 22050 에서 올림)
const SAMPLE_RATE := 44100
const NOISE_SEED := 20261005


static func all() -> Dictionary:
	var rng := RandomNumberGenerator.new()
	rng.seed = NOISE_SEED
	var out := {}
	for sfx: Sfx in Sfx.values():
		if sfx not in FILE_ONLY:
			out[sfx] = _synth(sfx, rng)
	return out


## 재생용 AudioStreamWAV (16비트 모노)
static func to_stream(samples: PackedFloat32Array) -> AudioStreamWAV:
	var bytes := PackedByteArray()
	bytes.resize(samples.size() * 2)
	for i in samples.size():
		bytes.encode_s16(i * 2, int(clampf(samples[i], -1.0, 1.0) * 32767.0))
	var wav := AudioStreamWAV.new()
	wav.format = AudioStreamWAV.FORMAT_16_BITS
	wav.mix_rate = SAMPLE_RATE
	wav.stereo = false
	wav.data = bytes
	return wav


static func _synth(sfx: Sfx, rng: RandomNumberGenerator) -> PackedFloat32Array:
	match sfx:
		Sfx.PITCH:
			# 공을 놓는 순간: 팔·유니폼이 휘두르는 낮은 '쉭'(0.06초에 가장 크다) + 공이 공기를 가르는 높은 '쓩'(실밥 회전 소리, 5~7kHz)
			return _normalized(_mix(0.3, [
				_scale(_swept(_noise(0.2, 0.001, 1.0, rng), 500.0, 1400.0, 700.0, 1.0, 0.06, 0.03), 1.6),
				_scale(_swept(_noise(0.3, 0.001, 1.0, rng), 4500.0, 6500.0, 3500.0, 3.0, 0.10, 0.06), 0.45),
			]), 0.35)
		Sfx.SWING:
			# 배트가 공기를 가르는 "부웅—휙": 배트가 다가오며 대역이 올라가고(도플러) 지나가면 떨어진다.
			# 0.12초(배트가 존을 지나는 때)에 가장 크고 바로 사라진다. 낮은 몸통 바람 + 높은 날 바람
			return _normalized(_mix(0.3, [
				_swept(_noise(0.3, 0.001, 1.0, rng), 180.0, 650.0, 250.0, 1.5, 0.12, 0.04),
				_scale(_swept(_noise(0.3, 0.001, 1.0, rng), 1100.0, 2600.0, 1300.0, 2.5, 0.12, 0.035), 0.25),
			]), 0.6)
		Sfx.CRACK_SOLID:
			# 나무 배트 정타 "딱!": 아주 날카로운 순간음 + 2~4kHz '딱' + 짧게 울리는 맑은 음(2.1·3.3kHz, 10~18ms)이 경쾌함을 만든다.
			# 낮은 소리는 거의 없다 (스위트 스폿에 맞으면 배트가 안 떨린다). 뒤에 경기장 반사음
			var dry := _mix(0.12, [
				_scale(_biquad(_noise(0.02, 0.0002, 0.002, rng), "high", 1500.0, 0.7), 1.2),
				_scale(_biquad(_noise(0.05, 0.0003, 0.008, rng), "band", 2600.0, 3.0), 2.2),
				_scale(_biquad(_noise(0.04, 0.0003, 0.005, rng), "band", 4200.0, 3.0), 1.2),
				_tone(2100.0, 0.018, 0.45),
				_tone(3300.0, 0.012, 0.3),
				_tone(1450.0, 0.010, 0.2),
				_tone(160.0, 0.008, 0.1),
			])
			return _normalized(_stadium(_normalized(dry, 1.0), 0.3, rng), 0.95)
		Sfx.CRACK_HOME_RUN:
			# 홈런 "딱!!": 정타처럼 맑고 경쾌하게, 더 크고 더 오래 울린다 (2026-10-31 유저 "좀 둔탁해. 더 경쾌한 딱!").
			# 낮은 몸통(140·420·900Hz)을 빼고, 2.6·4.2kHz '딱'과 맑은 울림 2.3·3.5kHz(25~30ms)를 정타보다 길게, 경기장 반사음을 크게
			var dry := _mix(0.2, [
				_scale(_biquad(_noise(0.02, 0.0002, 0.002, rng), "high", 1800.0, 0.7), 1.4),
				_scale(_biquad(_noise(0.06, 0.0003, 0.010, rng), "band", 2600.0, 3.0), 2.4),
				_scale(_biquad(_noise(0.05, 0.0003, 0.007, rng), "band", 4200.0, 3.0), 1.5),
				_tone(2300.0, 0.03, 0.5),
				_tone(3500.0, 0.025, 0.35),
				_tone(1600.0, 0.015, 0.2),
			])
			return _normalized(_stadium(_normalized(dry, 1.0), 0.5, rng), 0.98)
		Sfx.CRACK_FOUL:
			# 파울 "틱!": 배트 가장자리를 스친 가늘고 짧은 소리. 너무 짧으면(2~3ms) 귀에 안 들려 스윙 소리에 묻힌다 —
			# 순간음 뒤에 2.2·3.4kHz 울림을 6~8ms 붙여 또렷하게 (2026-10-31 유저 "스윙소리보다 작네")
			var dry := _mix(0.06, [
				_scale(_biquad(_noise(0.02, 0.0002, 0.002, rng), "high", 2500.0, 0.7), 1.2),
				_scale(_biquad(_noise(0.04, 0.0002, 0.006, rng), "band", 3400.0, 3.0), 1.4),
				_scale(_biquad(_noise(0.04, 0.0002, 0.008, rng), "band", 2200.0, 3.0), 1.0),
				_tone(2900.0, 0.008, 0.35),
			])
			return _normalized(_stadium(_normalized(dry, 1.0), 0.2, rng), 0.95)
		Sfx.MITT:
			# 포수 미트 "팡!": 가죽이 터지듯 넓은 대역의 짧은 순간음 + 높은 '짝' + 주머니가 받는 낮은 '퍽' + 가죽 끌림. 뒤에 경기장 반사음
			var dry := _mix(0.08, [
				_scale(_biquad(_noise(0.03, 0.0002, 0.004, rng), "band", 1400.0, 0.8), 3.0),
				_scale(_biquad(_noise(0.01, 0.0002, 0.0015, rng), "high", 3000.0, 0.7), 0.8),
				_tone(260.0, 0.008, 0.3),
				_tone(110.0, 0.015, 0.18),
				_scale(_lowpass_hz(_noise(0.08, 0.001, 0.02, rng), 1200.0), 0.15),
			])
			return _normalized(_stadium(_normalized(dry, 1.0), 0.35, rng), 0.85)
		Sfx.CHEER_SMALL:
			return _scale(_crowd(0.9, 0.08, rng), 0.45)
		Sfx.CHEER_BIG:
			return _scale(_crowd(2.2, 0.25, rng), 0.7)
		Sfx.STEP:
			# 트랙을 차는 짧은 "탁": 낮은 몸통(90Hz) + 고무 바닥 마찰 잡음 (연타라 아주 짧게)
			return _normalized(_mix(0.06, [
				_tone(90.0, 0.012, 0.6),
				_scale(_biquad(_noise(0.04, 0.0005, 0.008, rng), "band", 900.0, 1.2), 1.4),
			]), 0.55)
		Sfx.WHISTLE:
			# 호루라기 "삑—": 2.9kHz 에 빠른 떨림(구슬이 굴러 생기는 30Hz 진폭 떨림) + 바람 잡음
			var n := _length(0.42)
			var out := PackedFloat32Array()
			out.resize(n)
			for i in n:
				var t := float(i) / SAMPLE_RATE
				var env := minf(t / 0.015, 1.0) * minf((0.42 - t) / 0.06, 1.0)
				out[i] = sin(TAU * (2900.0 * t + 25.0 * sin(TAU * 30.0 * t) / (TAU * 30.0))) * env * (0.75 + 0.25 * sin(TAU * 30.0 * t))
			return _normalized(_mix(0.42, [out, _scale(_biquad(_noise(0.42, 0.01, 0.3, rng), "band", 3000.0, 2.0), 0.3)]), 0.5)
		Sfx.CLANK:
			# 원판끼리 부딪는 "철컥": 금속 울림 여러 개(배음이 정수배가 아니라 쇳소리) + 짧은 타격 잡음
			return _normalized(_mix(0.4, [
				_scale(_biquad(_noise(0.02, 0.0002, 0.003, rng), "high", 2000.0, 0.7), 1.0),
				_tone(620.0, 0.08, 0.5),
				_tone(1370.0, 0.05, 0.35),
				_tone(2310.0, 0.03, 0.25),
				_tone(3650.0, 0.02, 0.15),
			]), 0.8)
		Sfx.BEEP:
			# 신호음 "삑": 1.6kHz 짧은 사각파 느낌(기음 + 3배음)
			var n := _length(0.09)
			var out := PackedFloat32Array()
			out.resize(n)
			for i in n:
				var t := float(i) / SAMPLE_RATE
				var env := minf(t / 0.003, 1.0) * minf((0.09 - t) / 0.01, 1.0)
				out[i] = (sin(TAU * 1600.0 * t) + 0.3 * sin(TAU * 4800.0 * t)) * env
			return _normalized(out, 0.45)
		Sfx.BUZZ:
			# 실패 "부—": 낮은 두 음(150·158Hz)이 맥놀이 + 배음
			var n := _length(0.3)
			var out := PackedFloat32Array()
			out.resize(n)
			for i in n:
				var t := float(i) / SAMPLE_RATE
				var env := minf(t / 0.01, 1.0) * minf((0.3 - t) / 0.05, 1.0)
				out[i] = (sin(TAU * 150.0 * t) + sin(TAU * 158.0 * t) + 0.5 * sin(TAU * 450.0 * t) + 0.3 * sin(TAU * 750.0 * t)) * env
			return _normalized(out, 0.5)
	return PackedFloat32Array()


static func _length(seconds: float) -> int:
	return int(seconds * SAMPLE_RATE)


## 지수 감쇠 사인파. tau 초마다 1/e 로 줄어든다
static func _tone(freq: float, tau: float, gain: float) -> PackedFloat32Array:
	var n := _length(tau * 6.0)
	var out := PackedFloat32Array()
	out.resize(n)
	for i in n:
		var t := float(i) / SAMPLE_RATE
		out[i] = sin(TAU * freq * t) * exp(-t / tau) * gain
	return out


## 관중 함성: 잡음을 두 번 걸러 사람 목소리 근처 대역만 남기고, 천천히 일렁이게 한다
static func _crowd(seconds: float, attack: float, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var n := _length(seconds)
	var out := PackedFloat32Array()
	out.resize(n)
	var lp1 := 0.0
	var lp2 := 0.0
	var wobble: Array[float] = []
	for k in 6:
		wobble.append(rng.randf_range(3.0, 9.0))
	for i in n:
		var t := float(i) / SAMPLE_RATE
		var noise := rng.randf() * 2.0 - 1.0
		lp1 += _coef22(0.35) * (noise - lp1)
		lp2 += _coef22(0.08) * (noise - lp2)
		var env := minf(t / attack, 1.0) * exp(-maxf(0.0, t - attack) / (seconds * 0.45))
		var mod := 0.0
		for w in wobble:
			mod += sin(TAU * w * t + w)
		mod = 0.75 + 0.25 * mod / wobble.size()
		out[i] = (lp1 - lp2) * env * mod * 3.0
	return out


## 22050Hz 에서 정한 1차 저역 통과 계수를 지금 SAMPLE_RATE 에서 같은 차단 주파수가 되게 바꾼다 (바람·함성 소리는 그대로)
static func _coef22(a: float) -> float:
	return 1.0 - pow(1.0 - a, 22050.0 / SAMPLE_RATE)


## 잡음: attack 초 동안 커졌다가 tau 초마다 1/e 로 줄어든다
static func _noise(seconds: float, attack: float, tau: float, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var n := _length(seconds)
	var out := PackedFloat32Array()
	out.resize(n)
	for i in n:
		var t := float(i) / SAMPLE_RATE
		out[i] = (rng.randf() * 2.0 - 1.0) * minf(t / attack, 1.0) * exp(-maxf(0.0, t - attack) / tau)
	return out


## 2차 필터 (RBJ). kind: "low" | "high" | "band"(가운데 주파수 근처만, q 가 클수록 좁다)
static func _biquad(input: PackedFloat32Array, kind: String, freq: float, q: float) -> PackedFloat32Array:
	var w := TAU * freq / SAMPLE_RATE
	var cw := cos(w)
	var alpha := sin(w) / (2.0 * q)
	var b0 := alpha
	var b1 := 0.0
	var b2 := -alpha
	if kind == "low":
		b0 = (1.0 - cw) / 2.0
		b1 = 1.0 - cw
		b2 = b0
	elif kind == "high":
		b0 = (1.0 + cw) / 2.0
		b1 = -(1.0 + cw)
		b2 = b0
	var a0 := 1.0 + alpha
	var a1 := -2.0 * cw / a0
	var a2 := (1.0 - alpha) / a0
	b0 /= a0
	b1 /= a0
	b2 /= a0
	var out := PackedFloat32Array()
	out.resize(input.size())
	var x1 := 0.0
	var x2 := 0.0
	var y1 := 0.0
	var y2 := 0.0
	for i in input.size():
		var x := input[i]
		var y := b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
		x2 = x1
		x1 = x
		y2 = y1
		y1 = y
		out[i] = y
	return out


## 움직이는 바람 소리: 대역 통과의 가운데 주파수가 f_from → (peak_s 초에) f_peak → f_to 로 움직이고,
## 크기는 peak_s 까지 가파르게 커졌다가(세제곱) tau 초마다 1/e 로 줄어든다
static func _swept(input: PackedFloat32Array, f_from: float, f_peak: float, f_to: float, q: float, peak_s: float, tau: float) -> PackedFloat32Array:
	var out := PackedFloat32Array()
	out.resize(input.size())
	var end_s := float(input.size()) / SAMPLE_RATE
	var x1 := 0.0
	var x2 := 0.0
	var y1 := 0.0
	var y2 := 0.0
	for i in input.size():
		var t := float(i) / SAMPLE_RATE
		var f := lerpf(f_from, f_peak, t / peak_s) if t < peak_s else lerpf(f_peak, f_to, (t - peak_s) / maxf(end_s - peak_s, 0.001))
		var w := TAU * f / SAMPLE_RATE
		var alpha := sin(w) / (2.0 * q)
		var a0 := 1.0 + alpha
		var x := input[i]
		var y := (alpha * x - alpha * x2 + 2.0 * cos(w) * y1 - (1.0 - alpha) * y2) / a0
		x2 = x1
		x1 = x
		y2 = y1
		y1 = y
		var env := pow(t / peak_s, 3.0) if t < peak_s else exp(-(t - peak_s) / tau)
		out[i] = y * env
	return out


static func _lowpass_hz(input: PackedFloat32Array, cutoff_hz: float) -> PackedFloat32Array:
	return _lowpass(input, 1.0 - exp(-TAU * cutoff_hz / SAMPLE_RATE))


## 경기장 울림: 관중석·더그아웃 벽에서 돌아오는 반사음 몇 번(뒤로 갈수록 작고 어둡다) + 넓게 퍼지는 잔향.
## wet 은 원음(최대 1) 대비 반사음 크기
const STADIUM_ECHOES := [[0.085, 0.30, 5000.0], [0.16, 0.18, 3500.0], [0.27, 0.11, 2500.0], [0.41, 0.06, 1800.0]]
const STADIUM_TAIL_S := 1.0

static func _stadium(dry: PackedFloat32Array, wet: float, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var out := PackedFloat32Array()
	out.resize(dry.size() + _length(STADIUM_TAIL_S))
	for i in dry.size():
		out[i] = dry[i]
	for e: Array in STADIUM_ECHOES:
		var echo := _lowpass_hz(dry, e[2])
		var d := _length(e[0])
		for i in echo.size():
			if i + d < out.size():
				out[i + d] += echo[i] * e[1] * wet / 0.35
	var tail := _biquad(_noise(STADIUM_TAIL_S, 0.03, 0.3, rng), "band", 1500.0, 0.7)
	var start := _length(0.02)
	for i in tail.size():
		if i + start < out.size():
			out[i + start] += tail[i] * wet * 0.25
	return out


static func _lowpass(input: PackedFloat32Array, a: float) -> PackedFloat32Array:
	var out := PackedFloat32Array()
	out.resize(input.size())
	var y := 0.0
	for i in input.size():
		y += a * (input[i] - y)
		out[i] = y
	return out


static func _mix(seconds: float, parts: Array) -> PackedFloat32Array:
	var out := PackedFloat32Array()
	out.resize(_length(seconds))
	for part: PackedFloat32Array in parts:
		for i in mini(part.size(), out.size()):
			out[i] += part[i]
	return out


static func _scale(input: PackedFloat32Array, gain: float) -> PackedFloat32Array:
	var out := input.duplicate()
	for i in out.size():
		out[i] *= gain
	return out


static func _normalized(input: PackedFloat32Array, peak: float) -> PackedFloat32Array:
	var m := 0.0
	for v in input:
		m = maxf(m, absf(v))
	return input if m == 0.0 else _scale(input, peak / m)
