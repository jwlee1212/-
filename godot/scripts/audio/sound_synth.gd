class_name SoundSynth
extends RefCounted
## 효과음 합성기. 음원 파일 없이 사인파·잡음·감쇠 곡선을 섞어 만든다 (저작권 걱정 없음, 용량 0).
## 잡음은 고정 시드 난수로 만든다 — 실행할 때마다 같은 소리가 난다.

enum Sfx {
	PITCH,        ## 투수가 공을 놓는 순간
	SWING,
	CRACK_SOLID,  ## 정타 "딱!"
	CRACK_WEAK,   ## 빗맞음 "퍽"
	CRACK_FOUL,   ## 파울 "틱"
	MITT,         ## 포수 미트 "팡"
	CHEER_SMALL,  ## 안타 함성 (짧게)
	CHEER_BIG,    ## 홈런 함성
}

const SAMPLE_RATE := 22050
const NOISE_SEED := 20261005


static func all() -> Dictionary:
	var rng := RandomNumberGenerator.new()
	rng.seed = NOISE_SEED
	var out := {}
	for sfx: Sfx in Sfx.values():
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
		Sfx.PITCH:  # 짧고 부드러운 바람 소리
			return _scale(_whoosh(0.14, 0.15, 0.35, rng), 0.35)
		Sfx.SWING:  # 배트가 공기를 가르는 소리: 점점 높아지는 바람
			return _scale(_whoosh(0.22, 0.08, 0.5, rng), 0.6)
		Sfx.CRACK_SOLID:  # 나무 배트 정타: 아주 짧은 딸깍 + 높은 공명음 몇 개 + 낮은 몸통 울림
			return _normalized(_mix(0.32, [
				_click(0.004, rng),
				_tone(1850.0, 0.045, 0.6),
				_tone(2650.0, 0.035, 0.45),
				_tone(3900.0, 0.025, 0.3),
				_tone(190.0, 0.07, 0.55),
			]), 0.95)
		Sfx.CRACK_WEAK:  # 빗맞음: 높은 공명 없이 둔탁하게
			return _normalized(_mix(0.2, [
				_scale(_lowpass(_click(0.012, rng), 0.25), 0.9),
				_tone(320.0, 0.035, 0.7),
				_tone(720.0, 0.02, 0.3),
			]), 0.7)
		Sfx.CRACK_FOUL:  # 파울 끝에 스친 소리: 가늘고 짧게
			return _normalized(_mix(0.12, [
				_scale(_click(0.003, rng), 0.6),
				_tone(2400.0, 0.015, 0.6),
				_tone(1200.0, 0.02, 0.3),
			]), 0.6)
		Sfx.MITT:  # 미트에 꽂히는 "팡": 낮은 울림 + 가죽 잡음
			return _normalized(_mix(0.14, [
				_tone(115.0, 0.03, 0.9),
				_scale(_lowpass(_click(0.015, rng), 0.35), 0.8),
			]), 0.8)
		Sfx.CHEER_SMALL:
			return _scale(_crowd(0.9, 0.08, rng), 0.45)
		Sfx.CHEER_BIG:
			return _scale(_crowd(2.2, 0.25, rng), 0.7)
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


## 아주 짧은 잡음 덩어리 (타격 순간의 "딸깍")
static func _click(seconds: float, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var n := _length(seconds)
	var out := PackedFloat32Array()
	out.resize(n)
	for i in n:
		out[i] = (rng.randf() * 2.0 - 1.0) * (1.0 - float(i) / n)
	return out


## 바람 소리: 잡음을 저역 통과시키되 통과 대역을 시간에 따라 바꾸고, 가운데가 불룩한 볼륨 곡선을 씌운다
static func _whoosh(seconds: float, center_start: float, center_end: float, rng: RandomNumberGenerator) -> PackedFloat32Array:
	var n := _length(seconds)
	var out := PackedFloat32Array()
	out.resize(n)
	var y := 0.0
	for i in n:
		var p := float(i) / n
		var a := lerpf(center_start, center_end, p)
		y += a * ((rng.randf() * 2.0 - 1.0) - y)
		out[i] = y * sin(PI * p) * 2.2
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
		lp1 += 0.35 * (noise - lp1)
		lp2 += 0.08 * (noise - lp2)
		var env := minf(t / attack, 1.0) * exp(-maxf(0.0, t - attack) / (seconds * 0.45))
		var mod := 0.0
		for w in wobble:
			mod += sin(TAU * w * t + w)
		mod = 0.75 + 0.25 * mod / wobble.size()
		out[i] = (lp1 - lp2) * env * mod * 3.0
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
