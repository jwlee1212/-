# 컷신 그림 — Gemini 프롬프트 (2026-11-05)

> 1학년 컷신 10개, 그림 29장 + 인물 기준 그림 7장. 그림을 만들면 `docs/refs/cutscene/<파일 이름>.png` 로 넣는다.
> Claude 가 가공(대사창·글자 잘라 내기, 화면 비율·도트 크기·팔레트 맞추기)하고, 컷신 엔진이 카메라 이동·확대·번쩍임·흔들림·자막·소리를 얹는다.
> **자막·대사는 게임이 넣는다 — 그림에는 글자를 넣지 않는다.**

## 0. 넣기 전에 확인할 것

- **상업 이용**: Gemini 이용 약관에서 생성 이미지를 상업 게임에 써도 되는지 확인하고, `licenses/assets.md` 에 도구 이름·날짜·약관 확인 결과를 적는다 (CLAUDE.md 원칙 6 — 기록 없는 에셋은 넣지 않는다).
- **주인공 얼굴은 보이지 않게** (원칙: 유저가 자기를 넣어 몰입). 뒷모습, 모자 챙 그늘, 역광 실루엣으로. 다른 인물은 얼굴이 보여도 된다.
- 실제 구단·학교·선수 이름, 로고, 실존 경기장은 넣지 않는다.

## 1. 순서

1. **인물 기준 그림 7장을 먼저 만든다** (2장). 이후 모든 장면 프롬프트에 해당 인물 기준 그림을 같이 첨부한다 — 장면마다 얼굴·옷이 바뀌지 않게.
2. 장면 그림을 컷신별로 만든다 (3장). 한 컷신 안의 그림들은 **같은 대화창에서 이어서** 만들면 색감이 맞는다.
3. 마음에 안 드는 장면은 "같은 장면, 이 부분만 바꿔서" 로 다시 뽑는다.

## 2. 모든 프롬프트 앞에 붙이는 공통 문구

```
16-bit retro pixel art, game cutscene still, chunky visible pixels, limited palette (about 32 colors),
no anti-aliasing, no blur, no gradients except dithering, clean readable silhouettes,
cinematic composition, ultra-wide 21:9.
NO text, NO letters, NO numbers, NO UI, NO dialogue box, NO scoreboard digits, NO logos.
Keep important subjects inside the central 70% of the image height (top and bottom will be cropped).
Setting: a small high school baseball team in Suwon, South Korea (fictional school).
```

## 3. 인물 기준 그림 (먼저, 각 1장)

공통 문구 뒤에 붙인다. 배경은 단색 회색, 전신, 정면 + 옆모습 두 포즈.

| 파일 | 인물 | 프롬프트 |
|---|---|---|
| `char_me.png` | 주인공 (서림고 1학년 유격수) | `Character reference sheet. A slim 16-year-old Korean high school shortstop, seen from the BACK and from the side with his face hidden in the shadow of his cap brim. White baseball uniform with navy-blue trim and navy-blue cap, plain uniform with no letters or logos, black cleats, brown glove. Plain gray background, full body, two poses.` |
| `char_junsu.png` | 박준수 (동기 포수) | `Character reference sheet. A big, round-faced, cheerful 16-year-old Korean catcher, broad and heavy build, cap worn BACKWARDS, same white uniform with navy-blue trim, catcher's gear held in one hand. Plain gray background, full body, front and side.` |
| `char_coach.png` | 오 감독 | `Character reference sheet. A stern Korean baseball coach in his 50s, short graying hair, thick navy padded team jacket, navy cap, arms crossed, few words. Plain gray background, full body, front and side.` |
| `char_soyul.png` | 소율 (2학년 매니저) | `Character reference sheet. An energetic 17-year-old Korean girl, team manager, high ponytail, navy team windbreaker over school clothes, holding a video camera, laptop bag on shoulder. Plain gray background, full body, front and side.` |
| `char_harin.png` | 하린 (소꿉친구, 미대 입시생) | `Character reference sheet. A calm 16-year-old Korean girl, long straight black hair to mid-back, beige long coat, a cylindrical drawing tube slung diagonally on her back. Plain gray background, full body, front and back.` |
| `char_taeo.png` | 강태오 (청운고 라이벌) | `Character reference sheet. A tall, lean, expressionless 16-year-old Korean shortstop prodigy, white uniform with deep green and gold trim, deep green helmet. Plain gray background, full body, front and side.` |
| `char_seongho.png` | 김성호 (3학년 외야수) | `Character reference sheet. A thin, quiet 18-year-old Korean outfielder, same white uniform with navy-blue trim, navy helmet, a little worn and dusty. Plain gray background, full body, front and side.` |

## 4. 장면 그림 (컷신별)

공통 문구 + 나오는 인물의 기준 그림을 첨부하고 아래 문구를 붙인다. **(필수)** 는 꼭, **(선택)** 은 여유가 있으면.

### ep01_join — 입부 첫날 (3장)

| 파일 | 장면 |
|---|---|
| `ep01_join_1.png` (필수) | `Early March morning, cold clear pale-blue sky, low sun on the right. A small empty dirt baseball field of a modest high school, chain-link fence, a shipping-container clubroom on the left, distant apartment blocks faded in the haze. Two tiny figures (the slim shortstop seen from behind, and the big catcher) walking in from the right. Long thin shadows.` |
| `ep01_join_2.png` (선택) | `Close-up of the coach's weathered hand holding a silver stopwatch, out-of-focus dirt field and morning sky behind.` |
| `ep01_join_3.png` (필수) | `Medium shot on the dirt field in the morning. The coach stands with arms crossed facing two first-year players: the slim shortstop (seen from behind, face not visible) and the big catcher with a backwards cap. Cold morning light.` |

### ep02_bench — 차례는 오지 않았다 (2장)

| 파일 | 장면 |
|---|---|
| `ep02_bench_1.png` (필수) | `Overcast April afternoon at a small public stadium with almost empty stands. Inside a simple concrete dugout, players sit on a bench seen from behind; the slim shortstop stands at the dugout edge with a bat on his shoulder, warming up, seen from behind. Gray, quiet mood.` |
| `ep02_bench_2.png` (필수) | `Wide shot, same overcast stadium, the game is over: the opposing team (gray uniforms) gathers to celebrate in the distance, our players in white walk back slowly. Muted colors.` |

### ep03_sink — 수돗가 (2장)

| 파일 | 장면 |
|---|---|
| `ep03_sink_1.png` (필수) | `A modern elite high school baseball complex in Seoul on a bright day: green artificial turf, a big blank scoreboard, a three-story dormitory, indoor practice hall. Small figures of our team in white stepping off a bus and stopping to stare.` |
| `ep03_sink_2.png` (필수) | `Late afternoon backlight behind the stadium at a long concrete outdoor sink with many faucets. The tall rival shortstop in deep green trim splashes water on his face; the slim shortstop (seen from behind, face hidden) stands a few steps away. Golden rim light, quiet tension.` |

### ep04_first — 고교 첫 타석 (2장)

| 파일 | 장면 |
|---|---|
| `ep04_first_1.png` (필수) | `Hazy May afternoon at a small high school field. Over-the-shoulder view from behind the slim batter in a navy helmet (face not visible), looking toward the opposing pitcher (red trim uniform) on the mound, catcher and umpire in front. Tense, first at-bat.` |
| `ep04_first_2.png` (필수) | `Low-angle shot of the opposing pitcher (red trim) in his windup against a hazy pale sky.` |

### ep05_seongho — 김성호의 마지막 타석 (8장 → 필수 6장)

| 파일 | 장면 |
|---|---|
| `ep05_seongho_1.png` (필수) | `July sunset at a big stadium in Seoul, mostly empty stands. Inside the dugout looking out at the bright field: a row of players seen from behind on the bench, the coach standing at the far end pointing toward the end of the bench.` |
| `ep05_seongho_2.png` (필수) | `Inside the dugout at sunset, the thin third-year outfielder slowly rises from the end of the bench, backlit by the orange sky; face shown in soft shadow, surprised.` |
| `ep05_seongho_3.png` (선택) | `The third-year players lined up leaning over the dugout railing, shouting, backlit by the sunset.` |
| `ep05_seongho_4.png` (필수) | `Side view at home plate at sunset: the thin outfielder in a navy helmet making contact, bat meeting ball, a burst of light, the red-trim catcher behind. Dramatic.` |
| `ep05_seongho_5.png` (필수) | `Side tracking shot: the outfielder sprinting toward first base along the foul line, his navy helmet flying off behind him, dust kicking up, sunset sky and stands behind.` |
| `ep05_seongho_6.png` (필수) | `Extreme ground-level close-up at first base: a cleat landing on the white base at the same instant the ball smacks into the first baseman's mitt. Frozen moment, dust.` |
| `ep05_seongho_7.png` (선택) | `The first-base umpire (navy shirt, gray pants) punching the air with an out call, silhouetted against the setting sun.` |
| `ep05_seongho_8.png` (필수) | `Very wide shot of the whole stadium at dusk: the tiny outfielder standing near first base with his helmet on the dirt, bowing toward the dugout.` |

### ep06_dp — 6-4-3 (3장)

| 파일 | 장면 |
|---|---|
| `ep06_dp_1.png` (필수) | `Bright September afternoon, infield action: the slim shortstop (back to camera) flips the ball underhand to the second baseman covering second base, runner sliding in, dust.` |
| `ep06_dp_2.png` (필수) | `Ground-level close-up at first base: the ball arriving into the first baseman's mitt just before the runner's foot. Bright daylight.` |
| `ep06_dp_3.png` (선택) | `The first-base umpire making an emphatic out call, bright blue sky.` |

### ep07_sound — 소리가 다르다 (3장)

| 파일 | 장면 |
|---|---|
| `ep07_sound_1.png` (필수) | `Late January winter night, an outdoor batting cage lit by a single warm lamp, thick netting, frost on the ground, visible breath. The slim batter (back to camera) alone in the cage.` |
| `ep07_sound_2.png` (필수) | `Same cage, the moment of perfect contact: the ball shooting into the far net, a crisp burst of light from the bat, the batter's silhouette in follow-through, breath cloud.` |
| `ep07_sound_3.png` (필수) | `Winter night, the shipping-container clubroom with one bright window; inside, the ponytailed manager leans toward the glass holding a video camera, excited.` |

### ep08_name — 이름 (2장)

| 파일 | 장면 |
|---|---|
| `ep08_name_1.png` (필수) | `Cold February afternoon at a university baseball field. In the first-base stands, two middle-aged professional scouts in team jackets: one with a radar speed gun, one writing in a notebook, leaning forward.` |
| `ep08_name_2.png` (필수) | `Wide shot of the university field at golden hour: the tiny slim shortstop standing on first base after a hit, seen from far away.` |

### side_harin_museum — 미술관 (3장, 연애)

| 파일 | 장면 |
|---|---|
| `harin_museum_1.png` (필수) | `Late February, a bright white art museum gallery, tall window light from the left, ceiling spotlights. Two figures seen from behind looking up at a large painting of a sun over a navy sea: the girl with long black hair and a drawing tube, and the slim boy in a casual hoodie (face not visible).` |
| `harin_museum_2.png` (선택) | `Close-up detail of a large painting: thick brushstrokes of navy, teal and cream around a golden sun.` |
| `harin_museum_3.png` (필수) | `The two sitting side by side on a gallery bench, seen from behind, soft window light, quiet warm mood.` |

### side_soyul_folder — 1학년 폴더 (1장, 연애)

| 파일 | 장면 |
|---|---|
| `soyul_folder_1.png` (필수) | `Saturday afternoon inside a small shipping-container clubroom, dim, the only light from a laptop screen on a desk showing a grid of video thumbnails (no readable text). The ponytailed manager and the slim boy (seen from behind) sit side by side watching it.` |

## 5. 자주 생기는 문제

- **글자가 생긴다** → "Remove all text, letters and signs" 로 다시. 남은 글자는 Claude 가 지우거나 가린다.
- **주인공 얼굴이 보인다** → "seen from behind, face hidden in cap shadow" 를 앞으로 옮겨 다시.
- **인물이 장면마다 달라진다** → 기준 그림을 꼭 첨부하고, 같은 대화창에서 이어서 만든다.
- **너무 매끈하다 (도트 느낌이 없다)** → "chunky visible pixels, 16-bit, no anti-aliasing" 를 맨 앞에.
