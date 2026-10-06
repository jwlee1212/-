class_name BatterSkills
extends RefCounted
## 내 타자의 능력치 (0~100). 프로토타입에서는 화면 슬라이더가 정한다

var contact: int
var power: int
var eye: int
## 주력: 내야안타·2루타·3루타를 가른다
var speed: int


func _init(p_contact: int = 50, p_power: int = 50, p_eye: int = 50, p_speed: int = 50) -> void:
	contact = p_contact
	power = p_power
	eye = p_eye
	speed = p_speed
