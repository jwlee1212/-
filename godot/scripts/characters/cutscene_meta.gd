class_name CutsceneMeta
extends RefCounted
## scripts/make_cutscene_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## art/pixel/cutscene_bg.png: 줄 = 판 (높이 PANEL_H, 넓이는 판마다) · art/pixel/cutscene.png: 줄 = 인물 동작, 칸 = 장

## 카메라가 보는 넓이·높이 (그림 픽셀, 화면에서 SCALE 배)
const VIEW := Vector2(211, 98)
const SCALE := 4
const PANEL_H := 98
## 판 이름 -> [줄, 넓이]
const PANELS := {"dugout": [0, 320], "rail": [1, 240], "plate": [2, 260], "baseline": [3, 480], "bag": [4, 240], "sky": [5, 240], "wide": [6, 260], "school": [7, 300], "cheongun": [8, 320], "sink": [9, 240], "cage_night": [10, 240], "clubroom_night": [11, 240], "stands": [12, 260], "museum": [13, 300], "clubroom_in": [14, 240], "school_morning": [15, 300], "watch_bg": [16, 240], "museum_white": [17, 300], "painting_close": [18, 320]}
const CELL := Vector2(96, 96)
## 칸 안 발 자리 (인물을 판에 놓을 때 이 점을 맞춘다)
const FOOT := Vector2(48, 90)
## 인물 이름 -> [줄, 장 수]
const ROWS := {"coach": [0, 1], "bench": [1, 2], "rise": [2, 1], "rail": [3, 1], "batter": [4, 2], "run": [5, 2], "helmet": [6, 1], "foot": [7, 1], "mitt": [8, 1], "ump": [9, 2], "tiny": [10, 2], "tiny_helmet": [11, 1], "tiny_walk": [12, 2], "coach_front": [13, 1], "sink": [14, 2], "pitcher": [15, 2], "toss": [16, 1], "pivot": [17, 1], "ball": [18, 1], "soyul_window": [19, 1], "scouts": [20, 2], "pair_stand": [21, 1], "pair_sit": [22, 1], "me_field": [23, 1], "junsu": [24, 1], "coach_arms": [25, 1], "tiny_pair_walk": [26, 2], "hand_watch": [27, 1], "harin_back": [28, 1], "me_back": [29, 1], "harin_sit": [30, 1], "me_sit": [31, 1]}
