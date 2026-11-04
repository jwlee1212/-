class_name CutsceneMeta
extends RefCounted
## scripts/make_cutscene_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## art/pixel/cutscene_bg.png: 줄 = 판 (높이 PANEL_H, 넓이는 판마다) · art/pixel/cutscene.png: 줄 = 인물 동작, 칸 = 장

## 카메라가 보는 넓이·높이 (그림 픽셀, 화면에서 SCALE 배)
const VIEW := Vector2(211, 98)
const SCALE := 4
const PANEL_H := 98
## 판 이름 -> [줄, 넓이]
const PANELS := {"dugout": [0, 320], "dark": [1, 240], "plate": [2, 260], "baseline": [3, 480], "bag": [4, 240], "sky": [5, 240], "wide": [6, 260]}
const CELL := Vector2(96, 96)
## 칸 안 발 자리 (인물을 판에 놓을 때 이 점을 맞춘다)
const FOOT := Vector2(48, 90)
## 인물 이름 -> [줄, 장 수]
const ROWS := {"coach": [0, 2], "bench": [1, 2], "face": [2, 3], "batter": [3, 4], "run": [4, 8], "helmet": [5, 3], "foot": [6, 1], "mitt": [7, 2], "ump": [8, 2], "tiny": [9, 3], "tiny_helmet": [10, 1]}
