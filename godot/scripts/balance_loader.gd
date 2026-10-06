class_name BalanceLoader
extends RefCounted
## config/balance.json 읽기·쓰기.
##
## - 맥(에디터·데스크톱 실행): 저장소의 원본 ../config/balance.json 을 바로 읽고 쓴다
## - 웹: 개발 서버(scripts/dev_server.py)가 원본을 balance.json 주소로 내보내므로 그걸 받아 온다 (BalanceSource).
##   파일을 고치고 폰에서 새로고침하면 다시 빌드하지 않아도 반영된다
## - 둘 다 안 되면 내보낼 때 넣어 둔 복사본 res://data/balance.json 을 읽는다

const BUNDLED := "res://data/balance.json"


## 원본 파일 경로 (맥에서만 의미 있음). name: "balance.json" | "content.json"
static func source_path(name: String = "balance.json") -> String:
	return ProjectSettings.globalize_path("res://").path_join("../config/" + name).simplify_path()


static func bundled_path(name: String) -> String:
	return "res://data/" + name


static func load_balance() -> Dictionary:
	return load_config("balance.json")


## config 폴더의 json 하나 (content.json 등)
static func load_config(name: String) -> Dictionary:
	var source := source_path(name)
	var path := source if FileAccess.file_exists(source) else bundled_path(name)
	return parse(FileAccess.get_file_as_string(path))


static func parse(text: String) -> Dictionary:
	var data: Variant = JSON.parse_string(text)
	assert(data is Dictionary, "balance.json 을 읽지 못했다")
	return data if data is Dictionary else {}


## patch = {"batting.timing.inputLatencyMs": 40, ...} 를 balance 에 덮어쓴다.
## 없는 경로는 만들지 않고 실패 목록으로 돌려준다 (오타로 엉뚱한 키가 생기는 걸 막으려고)
static func apply_patch(balance: Dictionary, patch: Dictionary) -> PackedStringArray:
	var missing := PackedStringArray()
	for path: String in patch:
		var keys := path.split(".")
		var cur: Variant = balance
		var ok := true
		for i in keys.size() - 1:
			if cur is Dictionary and (cur as Dictionary).has(keys[i]):
				cur = cur[keys[i]]
			else:
				ok = false
				break
		if ok and cur is Dictionary and (cur as Dictionary).has(keys[-1]):
			cur[keys[-1]] = patch[path]
		else:
			missing.append(path)
	return missing


## 맥에서 원본 파일에 바로 저장한다. 실패하면 이유를, 성공하면 빈 문자열을 돌려준다
static func save_patch_to_file(patch: Dictionary) -> String:
	var path := source_path()
	if not FileAccess.file_exists(path):
		return "원본 파일이 없다: %s" % path
	var balance := parse(FileAccess.get_file_as_string(path))
	var missing := apply_patch(balance, patch)
	if not missing.is_empty():
		return "없는 항목: %s" % ", ".join(missing)
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f == null:
		return "파일을 쓸 수 없다"
	f.store_string(JSON.stringify(balance, "  ", false) + "\n")
	return ""
