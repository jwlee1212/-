class_name BatterSkills
extends RefCounted
## 내 타자의 능력치 (0~100). 프로토타입에서는 화면 슬라이더가 정한다

var contact: int
var power: int
var eye: int


func _init(p_contact: int = 50, p_power: int = 50, p_eye: int = 50) -> void:
	contact = p_contact
	power = p_power
	eye = p_eye


func copy_with(p_contact: int, p_power: int, p_eye: int) -> BatterSkills:
	return BatterSkills.new(p_contact, p_power, p_eye)
