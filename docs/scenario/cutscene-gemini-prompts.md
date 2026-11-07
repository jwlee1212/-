# 컷신 그림 — Gemini 프롬프트 (2026-11-05, 메인 브랜치 기준으로 다시 정리)

> 고교 3년 + 사이드 스토리 + 프로 편 컷신 전부의 그림 목록과 프롬프트. 그림을 만들면 `docs/refs/cutscene/<파일 이름>.png` 로 넣는다.
> Claude 가 `scripts/import_cutscene_stills.py` 로 생성 표시를 지워 `godot/art/cutscene_stills/` 에 원본 해상도 그대로 두고, `content.json cutscenes` 에 정지 그림 컷(`"still"`)으로 연결한다. 화면에서는 가운데 TV 틀에 뜬다. 카메라 이동·확대·번쩍임·흔들림·자막·소리는 컷신 엔진이 얹는다.
> **자막·대사는 게임이 넣는다. 그림에는 글자를 넣지 않는다.**

## 한눈에 보기

| 묶음 | 컷신 | 그림 (필수 + 선택) | 지금 |
|---|---|---|---|
| 인물 기준 그림 | — | 7장 + 새로 6장 | 12장 있음, `char_me_pro` 남음 |
| 1학년 EP01~08 (4장) | 8개 | 필수 19 + 선택 4 | ep01~04 그림 있음 (필수 6장 끝), ep05~08 은 도트 판으로 돈다 |
| 2학년 EP09~13 (5장) | 5개 | 필수 7 + 선택 1 | 자리 없음 (대본에 후보만) |
| 2학년 끝·3학년 EP14~22 (6장) | 6개 | 필수 11 + 선택 3 | 자리 없음 |
| 사이드 스토리 (7장) | 8개 (1학년 둘 포함) | 필수 8 + 선택 3 | 1학년 둘은 도트 판, 나머지는 자리 없음 |
| 프로 이야기 EP23~26 (8장) | 2개 (+ `pro_debut` 는 기념 순간과 같이 씀) | 필수 3 + 선택 1 | 자리 없음 |
| 프로 기념 순간 (9장) | 31개 | 필수 13 + 선택 11 (나머지는 그림을 같이 씀) | **이미 연결돼 있음** — 컷신이 없으면 "컷신 자리" 카드가 뜬다 |
| **합계** | **60개** | **필수 61장 + 선택 23장** | |

**만드는 순서 (추천)**
1. **프로 기념 순간 필수 13장.** 게임에 이미 연결돼 있어서 그림만 넣으면 "컷신 자리" 카드가 바로 컷신으로 바뀐다. 효과가 가장 빠르다.
2. **1학년 필수 19장 + 1학년 사이드 3장.** 첫 판에서 누구나 보는 장면이다.
3. **2·3학년 → 사이드 → 프로 이야기 순서.** 이 셋은 대본에 `CUTSCENE` 줄을 넣어야 틀린다 (그림이 오면 Claude 가 넣는다).
4. 선택 그림은 여유가 있을 때.

## 0. 넣기 전에 확인할 것

- **상업 이용**: Gemini 이용 약관에서 생성 이미지를 상업 게임에 써도 되는지 확인하고, `licenses/assets.md` 에 도구 이름·날짜·약관 확인 결과를 적는다 (CLAUDE.md 원칙 6 — 기록 없는 에셋은 넣지 않는다). **아직 확인 대기.**
- **주인공 얼굴은 보이지 않게** (원칙: 유저가 자기를 넣어 몰입). 뒷모습, 모자 챙 그늘, 역광 실루엣으로. 다른 인물은 얼굴이 보여도 된다.
- 실제 구단·학교·선수 이름, 로고, 실존 경기장은 넣지 않는다. 국가대표 장면에도 실제 국기·협회 엠블럼은 넣지 않는다 (색만).
- **프로 유니폼은 구단과 상관없이 한 벌** (임시 결정): 내 구단은 드래프트로 바뀌는데 구단 색은 아직 정하지 않았다(G8 "팀 색"). 그래서 프로 장면의 주인공은 **흰 유니폼 + 짙은 회색 테두리, 글자·번호 없음** 한 벌로 그리고, 조명·역광으로 색을 눌러 둔다. FA 이적(`pro_fa_move`)만 다른 색(진홍 테두리)이다. 구단 색을 정하면 바꾼다.

## 1. 순서

1. **인물 기준 그림을 먼저 만든다** (3장). 이후 모든 장면 프롬프트에 그 장면에 나오는 인물의 기준 그림을 같이 첨부한다. 장면마다 얼굴·옷이 바뀌지 않게 하려는 것이다.
2. 장면 그림을 컷신별로 만든다 (4장~). 한 컷신 안의 그림들은 **같은 대화창에서 이어서** 만들면 색감이 맞는다.
3. 마음에 안 드는 장면은 "같은 장면, 이 부분만 바꿔서" 로 다시 뽑는다.

## 2. 모든 프롬프트 앞에 붙이는 공통 문구

```
16-bit retro pixel art, game cutscene still, chunky visible pixels, limited palette (about 32 colors),
no anti-aliasing, no blur, no gradients except dithering, clean readable silhouettes,
cinematic composition, ultra-wide 21:9.
NO text, NO letters, NO numbers, NO UI, NO dialogue box, NO scoreboard digits, NO logos.
Keep important subjects inside the central 70% of the image height (top and bottom will be cropped).
```

그 뒤에 장소 한 줄을 붙인다.
- **고교 (1~3학년·고교 사이드)**: `Setting: a small high school baseball team in Suwon, South Korea (fictional school).`
- **프로 (프로 이야기·기념 순간·윤채아)**: `Setting: a fictional Korean professional baseball league, modern stadiums, no real team names, logos or mascots.`

## 3. 인물 기준 그림

공통 문구 뒤에 붙인다. 배경은 단색 회색, 전신, 두 포즈.

### 3-1. 있는 것 (1학년, 7장)

| 파일 | 인물 | 프롬프트 |
|---|---|---|
| `char_me.png` | 주인공 (서림고 유격수) | `Character reference sheet. A slim 16-year-old Korean high school shortstop, seen from the BACK and from the side with his face hidden in the shadow of his cap brim. White baseball uniform with navy-blue trim and navy-blue cap, plain uniform with no letters or logos, black cleats, brown glove. Plain gray background, full body, two poses.` |
| `char_junsu.png` | 박준수 (동기 포수) | `Character reference sheet. A big, round-faced, cheerful 16-year-old Korean catcher, broad and heavy build, cap worn BACKWARDS, same white uniform with navy-blue trim, catcher's gear held in one hand. Plain gray background, full body, front and side.` |
| `char_coach.png` | 오 감독 | `Character reference sheet. A stern Korean baseball coach in his 50s, short graying hair, thick navy padded team jacket, navy cap, arms crossed, few words. Plain gray background, full body, front and side.` |
| `char_soyul.png` | 소율 (매니저 선배) | `Character reference sheet. An energetic 17-year-old Korean girl, team manager, high ponytail, navy team windbreaker over school clothes, holding a video camera, laptop bag on shoulder. Plain gray background, full body, front and side.` |
| `char_harin.png` | 하린 (소꿉친구, 미대 입시생) | `Character reference sheet. A calm 16-year-old Korean girl, long straight black hair to mid-back, beige long coat, a cylindrical drawing tube slung diagonally on her back. Plain gray background, full body, front and back.` |
| `char_taeo.png` | 강태오 (청운고 라이벌) | `Character reference sheet. A tall, lean, expressionless 16-year-old Korean shortstop prodigy, white uniform with deep green and gold trim, deep green helmet. Plain gray background, full body, front and side.` |
| `char_seongho.png` | 김성호 (3학년 외야수) | `Character reference sheet. A thin, quiet 18-year-old Korean outfielder, same white uniform with navy-blue trim, navy helmet, a little worn and dusty. Plain gray background, full body, front and side.` |

### 3-2. 새로 만들 것 (6장) — ✅ 5장 있음, `char_me_pro.png` 만 남음

| 파일 | 인물 | 쓰는 곳 | 프롬프트 |
|---|---|---|---|
| `char_mom.png` | 엄마 (은하식당) | 3학년·드래프트·프로 | `Character reference sheet. A warm but tired Korean woman in her late 40s who runs a small family restaurant alone, short permed black hair, sleeves rolled up, a dark-green apron over a plain sweater, a dish towel over one shoulder. Plain gray background, full body, front and side.` |
| `char_junior.png` | 후배 (한 학년 아래 내야수) | 2·3학년 | `Character reference sheet. A small, eager 15-year-old Korean infielder with a buzz cut, the same white uniform with navy-blue trim and navy cap as the protagonist, a brand-new stiff glove. Plain gray background, full body, front and side.` |
| `char_jian.png` | 서지안 (다른 학교 방송부) | 사이드 | `Character reference sheet. A sharp-eyed 17-year-old Korean girl from another school's broadcasting club, short bob haircut, charcoal school blazer with a lanyard press badge (blank), a compact video camera with a lens cap on a strap. Plain gray background, full body, front and side.` |
| `char_chae.png` | 윤채아 (인플루언서) | 프로 사이드 | `Character reference sheet. A stylish 23-year-old Korean fashion influencer, long wavy light-brown hair, oversized cream knit cardigan, small shoulder bag, phone in hand. Second pose: the same woman with no makeup, hair tied up, gray hoodie. Plain gray background, full body, two poses.` |
| `char_me_pro.png` | 주인공 (프로) | 프로 전부 | `Character reference sheet. The same slim Korean shortstop, now about 20 and a little broader, seen from the BACK and from the side with his face hidden in the shadow of his helmet. Plain white professional baseball uniform with dark charcoal-gray trim, charcoal helmet, NO letters, NO numbers, NO logos. Plain gray background, full body, two poses.` |
| `char_me_school.png` | 주인공 (교복·사복) | 졸업·데이트 | `Character reference sheet. The same slim Korean teenage boy seen from the BACK and from the side, face not visible. Pose 1: navy school blazer and gray trousers. Pose 2: casual gray hoodie and jeans. Plain gray background, full body.` |

## 4. 1학년 (EP01~08)

공통 문구 + 고교 장소 줄 + 나오는 인물의 기준 그림을 첨부하고, 아래 문구를 붙인다. **(필수)** 는 꼭, **(선택)** 은 여유가 있으면 만든다.

### ep01_join — 입부 첫날 ✅ 그림 있음 (`ep01_join_1`, `ep01_join_2`)

| 파일 | 장면 |
|---|---|
| `ep01_join_3.png` (선택) | `Medium shot on the dirt field in the morning. The coach stands with arms crossed facing two first-year players: the slim shortstop (seen from behind, face not visible) and the big catcher with a backwards cap. Cold morning light.` |

### ep02_bench — 차례는 오지 않았다 (2장) ✅ 그림 있음

| 파일 | 장면 |
|---|---|
| `ep02_bench_1.png` (필수) | `Overcast April afternoon at a small public stadium with almost empty stands. Inside a simple concrete dugout, players sit on a bench seen from behind; the slim shortstop stands at the dugout edge with a bat on his shoulder, warming up, seen from behind. Gray, quiet mood.` |
| `ep02_bench_2.png` (필수) | `Wide shot, same overcast stadium, the game is over: the opposing team (gray uniforms) gathers to celebrate in the distance, our players in white walk back slowly. Muted colors.` |

### ep03_sink — 수돗가 (2장) ✅ 그림 있음

| 파일 | 장면 |
|---|---|
| `ep03_sink_1.png` (필수) | `A modern elite high school baseball complex in Seoul on a bright day: green artificial turf, a big blank scoreboard, a three-story dormitory, indoor practice hall. Small figures of our team in white stepping off a bus and stopping to stare.` |
| `ep03_sink_2.png` (필수) | `Late afternoon backlight behind the stadium at a long concrete outdoor sink with many faucets. The tall rival shortstop in deep green trim splashes water on his face; the slim shortstop (seen from behind, face hidden) stands a few steps away. Golden rim light, quiet tension.` |

### ep04_first — 고교 첫 타석 (2장) ✅ 그림 있음

| 파일 | 장면 |
|---|---|
| `ep04_first_1.png` (필수) | `Hazy May afternoon at a small high school field. Over-the-shoulder view from behind the slim batter in a navy helmet (face not visible), looking toward the opposing pitcher (red trim uniform) on the mound, catcher and umpire in front. Tense, first at-bat.` |
| `ep04_first_2.png` (필수) | `Low-angle shot of the opposing pitcher (red trim) in his windup against a hazy pale sky.` |

### ep05_seongho — 김성호의 마지막 타석 (필수 6장 + 선택 2장)

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

### ep06_dp — 6-4-3 (필수 2장 + 선택 1장)

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

## 5. 2학년 (EP09~13, `scenario_y2_ep09-13.md`)

대본에 컷신 후보만 있던 자리에 id 를 붙였다. 그림이 오면 그 장면 첫 줄에 `CUTSCENE <id>` 를 넣는다.

### y2_juniors — 선배가 된 봄 (EP09 `ep09_01`, 2장)

| 파일 | 장면 |
|---|---|
| `y2_juniors_1.png` (필수) | `Early March morning, the same small dirt high school field as a year ago (chain-link fence, shipping-container clubroom on the left, pale-blue sky). Four first-year players in white uniforms stand in a line; the big catcher with a backwards cap holds a silver stopwatch in front of them.` |
| `y2_juniors_2.png` (필수) | `Over-the-shoulder view from behind the slim shortstop (face not visible) standing at the edge of the field, watching the line of first-years from a distance. Morning sun, long shadows.` |

### y2_scout_gun — 다시 온 사람 (EP10 `ep10_01`, 1장)

| 파일 | 장면 |
|---|---|
| `y2_scout_gun_1.png` (필수) | `Low angle from the infield dirt looking up at the first-base stands of a small high school field on a bright April day. Two middle-aged scouts in different team jackets; one raises a radar speed gun that catches the sunlight.` |

### y2_first_win — 전국대회 1승 (EP11 `ep11_02`, 이긴 경우만, 필수 1장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `y2_first_win_1.png` (필수) | `July evening at a big stadium in Seoul. Players in white uniforms with navy trim vault over the dugout railing and run onto the field toward the mound, arms raised, sunset behind. Seen from the outfield side, joyful.` |
| `y2_first_win_2.png` (선택) | `Three senior players hanging on the dugout railing, heads down, shoulders shaking, crying with joy; backlit by the orange sky so faces stay in shadow.` |

### y2_sangbi — 상비군, 유격수 둘 (EP12 `ep12_02`, 2장)

| 파일 | 장면 |
|---|---|
| `y2_sangbi_1.png` (필수) | `Cold November morning at a national youth training complex with a large green field and a pale sky. Two shortstops stand side by side at the shortstop position, both in plain dark-blue training jerseys with no logos: the tall lean rival and the slim protagonist (seen from behind, face hidden). A small gap between them.` |
| `y2_sangbi_2.png` (필수) | `Closer shot from behind the slim shortstop: the tall rival beside him turns only his head toward him, expressionless, breath visible in the cold air.` |

### y2_winter_talk — 겨울, 갈림길 예고 (EP13 `ep13_01`, 1장)

| 파일 | 장면 |
|---|---|
| `y2_winter_talk_1.png` (필수) | `Winter dawn, dark blue sky, frost. Seen from outside, the shipping-container clubroom with one warm-lit window; through the glass, the coach in a navy padded jacket and the slim player (back to camera) stand facing each other.` |

## 6. 2학년 끝 · 3학년 (EP14~22, `scenario_y3_ep14-22.md`)

### y2_hundred — 백 번째 (EP14 `ep14_01`, 필수 1장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `y2_hundred_1.png` (필수) | `Night inside the small shipping-container clubroom, all lights off; the only light is a laptop screen glowing on the face and hands of the ponytailed manager, her finger pressing a key. The slim player sits beside her, seen from behind.` |
| `y2_hundred_2.png` (선택) | `Close-up of the laptop screen: a full grid of small video thumbnails of baseball at-bats, no readable text, the glow reflecting on a camera tripod beside it.` |

"100" 은 그림에 넣지 않는다. 게임이 자막으로 띄운다.

### y3_restaurant — 은하식당 (EP16 `ep16_01`, 2장)

| 파일 | 장면 |
|---|---|
| `y3_restaurant_1.png` (필수) | `11 p.m. on a quiet Suwon side street. A small Korean family restaurant with its metal shutter pulled halfway down; warm yellow light spills out underneath onto the wet pavement. A slim figure in a team jacket with a bat bag stands in front, seen from behind.` |
| `y3_restaurant_2.png` (필수) | `Inside the small restaurant after closing: the mother in a dark-green apron sits at a table under a single hanging bulb sorting papers, one sheet with a red stamp on top (no readable text). A bowl of soup steams on the next table.` |

### y3_taeo_final — 결승 (EP18 `ep18_01` 뒤 → 백호기 결승, 필수 2장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `y3_taeo_final_1.png` (필수) | `Packed stadium in Seoul on a hot July evening, the national high school championship final, stands full of tiny color dots and banners with no text, a blank scoreboard, golden light.` |
| `y3_taeo_final_2.png` (필수) | `Composition mirroring an earlier scene at a sink: the tall rival shortstop in deep green and gold trim crouched at shortstop in the foreground left, sharp; in the background at home plate, the slim batter in a navy helmet (seen from behind) stepping into the box. Golden rim light, tension.` |
| `y3_taeo_final_3.png` (선택) | `Close-up of the rival's glove hand slowly opening, ready, dust in the low sunlight.` |

### y3_last_summer — 마지막 여름 (EP19 `ep19_01`, 2장)

| 파일 | 장면 |
|---|---|
| `y3_last_summer_1.png` (필수) | `Sunset over the small high school dirt field, the shortstop position empty except for two figures sitting on the dirt: the buzz-cut younger infielder and the slim senior (seen from behind). Long orange shadows.` |
| `y3_last_summer_2.png` (필수) | `Close-up of two pairs of hands: the younger player's hands re-tying the leather laces of a worn brown glove held by the senior's hands, warm sunset light.` |

### y3_draft — 드래프트 (EP21 `ep21_01`~`ep21_02`, 3장, 지명/미지명 갈래)

| 파일 | 장면 |
|---|---|
| `y3_draft_1.png` (필수) | `Evening inside the small Korean family restaurant, closed for the day (a handwritten paper sign on the door, unreadable). Four silhouettes seen from behind sit facing a TV on the wall: the mother in an apron, the big catcher, the coach, and the slim player. The TV light glows blue on their backs.` |
| `y3_draft_2.png` (필수, 지명됐을 때) | `Close-up of the mother's hand letting a dish towel fall toward the floor, the TV glow behind, a frozen moment.` |
| `y3_draft_3.png` (필수, 미지명일 때) | `Same restaurant, later: the TV still glowing, the four figures sitting completely still, empty bowls on the table, quiet dim light.` |

### y3_graduation — 졸업 (EP22 `ep22_01`, 필수 1장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `y3_graduation_1.png` (필수) | `The same composition as the first-day opening image: the small empty dirt high school field, chain-link fence, shipping-container clubroom on the left, distant apartment blocks. Late February afternoon, pale winter sun. One figure in a navy school blazer stands in the middle, seen from behind, with a long thin shadow.` |
| `y3_graduation_2.png` (선택) | `Very wide shot pulling far back from the same field: the tiny figure, the clubroom, the town around the school, a few graduation flowers on the bench.` |

## 7. 사이드 스토리 (원하는 사람만)

1학년 둘(`side_harin_museum`, `side_soyul_folder`)은 4장 끝에 있는 그대로 두었다. 고백·윤채아 컷신은 그 이벤트 카드가 연다.

### side_harin_museum — 미술관 (1학년, 필수 2장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `harin_museum_1.png` (필수) | `Late February, a bright white art museum gallery, tall window light from the left, ceiling spotlights. Two figures seen from behind looking up at a large painting of a sun over a navy sea: the girl with long black hair and a drawing tube, and the slim boy in a casual hoodie (face not visible).` |
| `harin_museum_2.png` (선택) | `Close-up detail of a large painting: thick brushstrokes of navy, teal and cream around a golden sun.` |
| `harin_museum_3.png` (필수) | `The two sitting side by side on a gallery bench, seen from behind, soft window light, quiet warm mood.` |

### side_soyul_folder — 1학년 폴더 (1학년, 1장)

| 파일 | 장면 |
|---|---|
| `soyul_folder_1.png` (필수) | `Saturday afternoon inside a small shipping-container clubroom, dim, the only light from a laptop screen on a desk showing a grid of video thumbnails (no readable text). The ponytailed manager and the slim boy (seen from behind) sit side by side watching it.` |

### side_harin_swing — 놀이터 (2학년 `y2_side_harin_answer`, 1장)

| 파일 | 장면 |
|---|---|
| `harin_swing_1.png` (필수) | `Saturday evening at a Korean apartment-complex playground, one streetlamp, apartment windows lit behind. The long-haired girl in a beige coat sits on a swing, feet on the ground; the slim boy in a hoodie stands in front of her, seen from behind. Quiet, blue dusk.` |

### side_soyul_answer — 부실 (2학년 `y2_side_soyul_answer`, 1장)

| 파일 | 장면 |
|---|---|
| `soyul_answer_1.png` (필수) | `Late afternoon in the shipping-container clubroom, orange light through the small window. The ponytailed manager closes her laptop and looks up; the slim boy stands by the door, seen from behind. Dust in the light.` |

### side_jian_booth — 중계석 (2학년 `y2_side_jian_answer`, 필수 1장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `jian_booth_1.png` (필수) | `An empty broadcast booth high above an empty baseball stadium at dusk, the field lights off, a wide window over the diamond. The short-bob girl in a charcoal blazer stands by the window with her camera; the slim boy (seen from behind) stands at the booth door.` |
| `jian_booth_2.png` (선택) | `Close-up of her hands snapping a lens cap onto a compact video camera, the dusk field blurred through the window behind.` |

### side_chae_meet — 팬 사인회 (프로 `pro_side_chae_meet`, 선택 1장)

| 파일 | 장면 |
|---|---|
| `chae_meet_1.png` (선택) | `The end of a team fan-signing event in a stadium concourse, a long table with markers and a few leftover photo cards (blank). At the very end of the line stands a stylish woman with long wavy light-brown hair in a cream cardigan, holding only a phone. The player sits at the table, seen from behind.` |

### side_chae_store — 새벽 편의점 (프로 `pro_side_chae_truth`, 1장)

| 파일 | 장면 |
|---|---|
| `chae_store_1.png` (필수) | `1 a.m., seen from outside a Korean convenience store window: two figures at the window counter, the woman with her hair tied up in a gray hoodie and no makeup, and the player (seen from the side, face hidden by a cap). Steam rises from two cup noodles; neon reflections on the glass, an empty street.` |

### side_chae_river — 한강 둔치 (프로 `pro_side_chae_return`, 1장)

| 파일 | 장면 |
|---|---|
| `chae_river_1.png` (필수) | `Night at a riverside park in Seoul, a long lit bridge across the dark river, city lights on the water. The woman walks toward the camera holding two convenience-store plastic bags; the player sits on the steps in the foreground, seen from behind.` |

## 8. 프로 이야기 (EP23~26, `scenario_pro_ep23-26.md`)

여기부터는 프로 장소 줄을 붙이고, 주인공은 `char_me_pro.png` 를 첨부한다.

### pro_signing — 입단 (EP23 `ep23_01`, 2장, 지명/육성선수 갈래)

| 파일 | 장면 |
|---|---|
| `pro_signing_1.png` (필수, 지명) | `A team signing ceremony on a small stage, a plain backdrop with no text, many camera flashes from the press. The player in a plain white uniform with charcoal trim, seen from behind, putting on a cap, bright white flash light washing out the colors.` |
| `pro_signing_2.png` (필수, 육성선수) | `Dawn at an empty minor-league ballpark with small stands and worn turf, mist over the field. One player in a practice uniform stands alone at the edge of the infield, seen from behind, bat bag on his shoulder.` |

### pro_futures_bus — 퓨처스 원정 버스 (EP25 `ep25_01`, 필수 1장 + 선택 1장)

| 파일 | 장면 |
|---|---|
| `pro_futures_bus_1.png` (필수) | `5 a.m., a single team bus on an empty Korean highway at dawn, deep blue sky with a thin orange line on the horizon, mountains, the bus headlights.` |
| `pro_futures_bus_2.png` (선택) | `Inside the dark bus: a player's silhouette leaning his head on the window, a phone glowing faintly in his hand showing a tiny baseball broadcast; his reflection in the glass, highway lights streaking by.` |

`pro_debut` 은 기념 순간의 `debut` 와 같은 컷신이다 (9장).

## 9. 프로 기념 순간 (`content.json pro.milestones`, 31개)

**이미 게임에 연결돼 있다.** 그 순간이 오면 프로 허브가 컷신 `pro_<id>` 를 찾고, 없으면 "컷신 자리" 카드를 띄운다. 그림이 오면 Claude 가 `cutscenes` 에 정의만 더한다.
기념 순간 컷신은 짧게 (그림 1장, 3~4초: 천천히 확대 + 자막 한 줄). 그래서 그림은 1장씩이다.
**그림을 같이 쓰는 것**: "같이 씀" 칸에 적힌 그림을 자막만 바꿔 다시 쓴다. 따로 만들고 싶으면 그 줄의 프롬프트로 만든다.

| 컷신 | 언제 | 파일 | 등급 | 장면 |
|---|---|---|---|---|
| `pro_debut` | 1군 첫 경기 | `pro_debut.png` | **필수** | `Night stadium under bright floodlights, crowd as soft color dots. The player in a plain white uniform with charcoal trim and helmet, seen from behind, walking from the dugout toward home plate, warm light rim on the helmet.` |
| `pro_callup` | 퓨처스 → 1군 | `pro_callup.png` | **필수** | `Dim minor-league locker room at night, one bulb. A duffel bag being packed on a bench, a phone screen glowing on top of it (no readable text), the player seen from behind.` |
| `pro_first_hit` | 통산 1안타 | `pro_first_hit.png` | **필수** | `Low angle at first base at night: the runner (seen from behind) standing on the bag looking back toward the dugout, where a teammate raises a baseball high. Floodlights.` |
| `pro_first_rbi` | 통산 1타점 | `pro_first_rbi.png` | 선택 | `A runner crossing home plate taps the helmet of the batter waiting nearby (seen from behind), dust at the plate, night floodlights.` |
| `pro_first_hr` | 통산 1홈런 | `pro_first_hr.png` | **필수** | `Wide shot from behind home plate at night: the ball a tiny bright dot flying between two light towers, the batter in mid follow-through, the crowd a blur of light.` |
| `pro_first_sb` | 통산 1도루 | `pro_first_sb.png` | 선택 | `Ground-level shot at second base: a head-first slide in a cloud of dust, the second-base umpire's arms spread wide for safe. Night lights.` |
| `pro_four_hit` | 한 경기 4안타 | 같이 씀 `pro_first_hit` | 선택 | (따로 만들면) `The first-base coach laughing and shaking his head beside the runner at first base, night floodlights.` |
| `pro_multi_hr` | 한 경기 2홈런 | `pro_multi_hr.png` | 선택 | `Teammates hanging over the dugout railing cheering, arms reaching out toward the field, night floodlights behind.` |
| `pro_hr10` | 시즌 10홈런 | 같이 씀 `pro_hr_crowd` | — | (자막만 다르다) |
| `pro_hr20` | 시즌 20홈런 | 같이 씀 `pro_hr_crowd` | — | (따로 만들면) `The opposing pitcher on the mound taking off his cap and looking up at the night sky.` |
| `pro_hr30` | 시즌 30홈런 | `pro_hr_crowd.png` | 선택 | `The outfield bleachers at night rising to their feet all at once, thousands of tiny figures and light sticks, the ball dropping into the crowd.` |
| `pro_hits150` | 시즌 150안타 | 같이 씀 `pro_first_hit` | — | |
| `pro_hits100` | 통산 100안타 | 같이 씀 `pro_first_hit` | — | (따로 만들면) `A team staff member in a polo shirt receiving a commemorative ball with both hands in the dugout.` |
| `pro_hr100` | 통산 100홈런 | `pro_hr100.png` | 선택 | `In the night stands, a middle-aged woman in a dark-green apron raises a light stick high among the cheering crowd.` (엄마 기준 그림 첨부) |
| `pro_post_debut` | 첫 포스트시즌 | `pro_post_debut.png` | 선택 | `A dugout dressed for the postseason: bunting along the railing (no text), a packed stadium under evening floodlights, the player (seen from behind) on the top step.` |
| `pro_ks_champion` | 한국시리즈 우승 | `pro_ks_champion.png` | **필수** | `Infield at night, players running from everywhere toward the pitcher's mound into a pile, confetti and streamers falling through the floodlights.` |
| `pro_ks_mvp` | 한국시리즈 MVP | `pro_ks_mvp.png` | 선택 | `A locker room celebration, champagne spray in the air, the player (seen from behind) being lifted by teammates.` |
| `pro_roy` | 신인왕 | `pro_roy.png` | **필수** | `An award ceremony stage with a single spotlight, the player in a dark suit seen from behind holding a trophy, the audience as dark rows; in the audience one tall figure stands out in silhouette.` |
| `pro_gg` | 골든글러브 | `pro_gg.png` | 선택 | `Close-up of a hand holding a golden glove trophy under stage lights, an old worn brown glove lace tied around the wrist.` |
| `pro_mvp` | 정규시즌 MVP | `pro_mvp.png` | 선택 | `A big trophy on a stage in the foreground; through the stage light, a small family restaurant table appears faintly like a memory, a woman in an apron at it.` |
| `pro_hr_king` | 홈런왕 | 같이 씀 `pro_hr_crowd` | — | |
| `pro_batting_title` | 타격왕 | `pro_batting_title.png` | 선택 | `The official scorer's booth above home plate, an older man nodding to himself with a pencil, the field below at night.` |
| `pro_national` | 첫 국가대표 | `pro_national.png` | **필수** | `A national team locker room, a white jersey with red and blue trim hanging in an open locker (no letters, no numbers, no flag), the player seen from behind looking at it.` |
| `pro_asian_gold` | 아시안게임 금메달 | `pro_asian_gold.png` | **필수** | `A medal podium in a foreign stadium at night, a row of players in white uniforms with red and blue trim seen from behind, gold medals catching the light, confetti.` |
| `pro_olympic_medal` | 올림픽 메달 | 같이 씀 `pro_asian_gold` | 선택 | (따로 만들면) `A player silhouette under huge stadium lights biting a medal, the bright ring of the stadium behind.` |
| `pro_exempt` | 병역 특례 | `pro_exempt.png` | 선택 | `A phone lying on the counter of a small family restaurant at night, its screen glowing with one short message bubble (no readable text), a dish towel beside it.` |
| `pro_enlist` | 입대 | `pro_enlist.png` | **필수** | `Morning outside a military training center gate, a young man with a fresh buzz cut seen from behind walking in; at a distance behind him, a woman in a dark-green apron waves.` (엄마 기준 그림 첨부) |
| `pro_discharge` | 전역 | `pro_discharge.png` | **필수** | `A military base gate in bright spring light, a figure in civilian clothes walking out seen from behind, a bat bag over his shoulder, cherry blossoms.` |
| `pro_fa_stay` | FA 잔류 (엔딩) | `pro_fa_stay.png` | **필수** | `A team office with a window over the home stadium at sunset, the player seen from behind putting on the same white uniform with charcoal trim, a signed contract on the desk (no readable text).` |
| `pro_fa_move` | FA 이적 (엔딩) | `pro_fa_move.png` | **필수** | `A different, unfamiliar stadium under floodlights, the player seen from behind in a new white uniform with deep crimson trim, standing alone at shortstop.` |
| `pro_retire` | 은퇴식 | `pro_retire.png` | **필수** | `A full stadium at night, the player in his white uniform standing alone on home plate with his helmet off and bowing deeply, seen from behind; in the front row of the stands a woman in a dark-green apron.` (엄마 기준 그림 첨부) |

## 10. 자주 생기는 문제

- **글자가 생긴다** → "Remove all text, letters and signs" 로 다시. 남은 글자는 Claude 가 지우거나 가린다.
- **주인공 얼굴이 보인다** → "seen from behind, face hidden in cap shadow" 를 앞으로 옮겨 다시.
- **인물이 장면마다 달라진다** → 기준 그림을 꼭 첨부하고, 같은 대화창에서 이어서 만든다.
- **너무 매끈하다 (도트 느낌이 없다)** → "chunky visible pixels, 16-bit, no anti-aliasing" 를 맨 앞에.
- **국가대표 장면에 국기가 생긴다** → "no flags, no emblems, only red and blue trim" 를 더한다.
- **프로 유니폼에 번호·구단 글자가 생긴다** → "plain uniform, NO numbers on the back" 를 앞으로.
