class_name BalanceLoader
extends RefCounted
## config/balance.json 읽기.
##
## 개발 중(에디터·데스크톱 실행)에는 저장소의 원본 ../config/balance.json 을 바로 읽는다 — 수치를 고치고 다시 실행하면 바로 반영된다.
## 내보낸 빌드(웹·폰)에는 원본이 없으므로, 내보내기 직전에 복사해 둔 res://data/balance.json 을 읽는다 (scripts/godot-web.sh).

const BUNDLED := "res://data/balance.json"


static func load_balance() -> Dictionary:
	var source := ProjectSettings.globalize_path("res://").path_join("../config/balance.json").simplify_path()
	var path := source if FileAccess.file_exists(source) else BUNDLED
	var text := FileAccess.get_file_as_string(path)
	var data: Variant = JSON.parse_string(text)
	assert(data is Dictionary, "balance.json 을 읽지 못했다: %s" % path)
	return data
