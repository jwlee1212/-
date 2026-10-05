class_name SoundPlayer
extends Node
## 효과음 재생기. 소리마다 플레이어를 몇 개씩 두어 같은 소리가 겹쳐 울릴 수 있게 한다.
## 웹에서 브라우저가 "사용자가 만진 뒤에만 소리"를 요구하는 건 Godot 가 알아서 처리한다.

const POOL := 3

var _players := {}  # Sfx -> Array[AudioStreamPlayer]
var _next := {}


func _ready() -> void:
	var sounds := SoundSynth.all()
	for sfx: SoundSynth.Sfx in sounds:
		var stream := SoundSynth.to_stream(sounds[sfx])
		var pool: Array[AudioStreamPlayer] = []
		for i in POOL:
			var p := AudioStreamPlayer.new()
			p.stream = stream
			add_child(p)
			pool.append(p)
		_players[sfx] = pool
		_next[sfx] = 0


## volume 0~1
func play(sfx: SoundSynth.Sfx, volume: float = 1.0) -> void:
	var pool: Array[AudioStreamPlayer] = _players[sfx]
	var index: int = _next[sfx]
	_next[sfx] = (index + 1) % pool.size()
	var p := pool[index]
	p.volume_db = linear_to_db(maxf(volume, 0.0001))
	p.play()
