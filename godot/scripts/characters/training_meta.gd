class_name TrainingMeta
extends RefCounted
## scripts/make_training_art.py 가 만든 파일 — 손으로 고치지 말고 스크립트를 고쳐 다시 만든다.
## art/pixel/training.png: 줄 = 장면 (훈련 id), 칸 = 장 (왼쪽부터 되풀이)

const SIZE := Vector2(120, 72)
const FRAMES := 4
const ROWS := {"contact": 0, "power": 1, "eye": 2, "speed": 3, "rest": 4, "fielding": 5}
