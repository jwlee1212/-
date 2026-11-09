# 로비 골목 내 선수 SD 도트 — 뽑는 법 (2026-11-09)

로비 골목(`StreetView`)에서 걷는 내 선수. 지금은 체력 테스트 50m 의 달리는 도트(`minigame` runner, 56×72 칸)를 빌려 쓴다.

## 해상도 계산

| 항목 | 값 |
|---|---|
| 게임 기준 화면 | 844×390 (늘이기 `canvas_items`) |
| 아이폰 가로 실제 픽셀 | 약 2532×1170 → 기준 화면 1px = 폰 **3px** |
| 골목 그림 높이 | 1170 (= 폰 실제 높이) |
| 내 선수 화면 키 목표 | 화면 높이의 20~24% = 기준 **80~94px** = 폰 240~280px |
| 도트 규칙 | 정수 배율·nearest 만 (CLAUDE.md §4-6). 기준 1배로 그리면 폰에서 도트 1칸 = 3×3px |

**그래서: 한 칸 128×128, 캐릭터 키 88~96px, 발은 칸 아래에서 4px 위에 고정.** 기준 화면에 1배로 그린다(폰 3배).

- 더 촘촘하게(키 180px) 뽑아 0.5배로 그리면 폰에서 1.5배라 도트가 뭉개진다 → 하지 않는다
- 골목 그림의 문 높이는 캐릭터 키의 1.15배쯤(그림 픽셀로 약 300px)이면 자연스럽다
- 오른쪽 보는 그림만 뽑는다. 왼쪽은 게임이 좌우를 뒤집는다

## 필요한 장 (같은 기준 그림에서)

| 동작 | 장 수 | 용도 |
|---|---|---|
| idle (서 있기, 숨쉬기) | 4 | 문 앞·가만히 |
| walk (오른쪽으로 걷기) | 8 | 방향키·누른 장소로 걸어가기 |
| enter (문으로 들어가는 뒷모습) | 3 | 들어가기 |

## 프롬프트 (도구 상관없이)

2026-11-09 SpriteCook 해지 — 아래 프롬프트는 도트 생성기(PixelLab 등)나 GPT 이미지에 그대로 쓴다. 배경 투명, 128×128. 유니폼 색은 인게임 타자 기준 그림 `docs/refs/sprites/base_batter_v4.png` 을 참고 그림으로 함께 준다. GPT 이미지처럼 도트 전용이 아닌 도구는 결과가 가짜 도트(칸이 고르지 않음)일 수 있어서, 받은 뒤 128 격자로 줄여 nearest 로 다시 맞춰야 한다.

### 1. 기준 그림 (idle 1장)

```
Pixel art game sprite, chibi SD baseball player, 2.5 heads tall, side view facing right, standing relaxed idle pose, full body.
High school baseball uniform: royal blue cap (#2f6bff) with plain front (no logo, no letters), white jersey with blue piping and blue undershirt sleeves, light gray pants, blue stirrup socks, black cleats, small black glove hanging from left hand.
Simple cute face mostly shaded by the cap brim, no detailed eyes.
Clean dark outline, 3-4 tone cel shading, light from upper left.
Character 92 px tall on a 128x128 canvas, feet 4 px above the bottom edge, horizontally centered, transparent background.
No text, no numbers, no team logos.
```

### 2. 걷기 (기준 그림에서 — 애니메이션 기능이 있는 도구, 없으면 장마다 기준 그림을 참고로)

```
8-frame walk cycle to the right, same character, same size and position, feet stay on the same baseline, arms swing naturally, slight head bob of 1-2 px, cheerful casual walk, loopable.
```

### 3. 숨쉬기 (기준 그림에서)

```
4-frame idle breathing loop, same character, shoulders rise 1 px, subtle cap bob, feet do not move, loopable.
```

### 4. 문으로 들어가기 (기준 그림을 참고로 새로)

```
Same chibi baseball player seen from behind, walking into a doorway away from the camera, 3 frames: turning from side view to back view, stepping forward, almost gone. Same uniform, same 92 px height, feet on the same baseline, transparent background, no number on the back.
```

## 받은 뒤

원본은 `docs/refs/sprites/` 에 두고, `scripts/tidy_sheet.py` 로 칸 정리 → 메타 `.gd` 를 만든 뒤 `StreetView._draw_player` 가 그 시트를 쓰게 바꾼다. 도구·상업 이용은 `licenses/assets.md` 에.
