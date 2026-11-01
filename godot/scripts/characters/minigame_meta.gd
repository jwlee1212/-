class_name MinigameMeta
extends RefCounted
## scripts/make_minigame_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## art/pixel/minigame_bg.png: 줄 = 배경 · art/pixel/minigame.png: 줄 = 동작 묶음, 칸 = 장

const BG_SIZE := Vector2(200, 90)
const BG_ROWS := {"run_far": 0, "run_near": 1, "weight": 2, "react": 3}
const CELL := Vector2(56, 72)
## 칸 안 발 자리 (인물을 놓을 때 이 점을 바닥에 맞춘다)
const FOOT := Vector2(28, 66)
## 이름 -> [줄, 장 수]
const ROWS := {"runner": [0, 7], "buddy": [1, 6], "lifter": [2, 7], "batter": [3, 4], "lamp": [4, 3], "coach": [5, 2], "lines": [6, 2]}
