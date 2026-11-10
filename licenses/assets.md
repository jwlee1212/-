# 에셋 출처와 상업 이용 가능 여부

CLAUDE.md 원칙 6: 외부·AI 생성 그림·소리를 넣을 때는 여기 기록한다. **기록 없는 에셋은 넣지 않는다.**

| 에셋 | 위치 | 출처·도구 | 라이선스 | 상업 이용 |
|---|---|---|---|---|
| Pretendard 글꼴 (Regular, Bold) | `godot/fonts/` | Kil Hyung-jin, github.com/orioncactus/pretendard | SIL OFL 1.1 (`licenses/Pretendard-OFL.txt`, `godot/fonts/Pretendard-OFL.txt`) | 가능 (글꼴 단독 판매 금지) |
| 효과음 전부 (투구·스윙·타격음·미트·함성) | `godot/scripts/audio/sound_synth.gd` | 외부 음원·AI 도구 없이 코드로 합성 (사인파·잡음·감쇠 곡선) | 이 프로젝트 자체 코드 | 가능 |
| 투수·야구장 그림 | `godot/scripts/batting/batting_view.gd` | 코드로 그린 단순 도형 (임시, G8 에서 타자와 같은 그림체로 교체) | 이 프로젝트 자체 코드 | 가능 |

| **(임시)** 인물 일러스트 9장 (주인공 그림은 2026-11-01 뺐다): 한소율(매니저 — 2026-11-01 새 그림 + 표정 3장 `manager_happy` `manager_pout` `manager_surprised`, 회색 배경과 ✦ 표시를 지웠다) · 감독 · 동기 · 코치 · 정다온(소꿉친구) · 선배 · 엄마 · 후배 · 서지안(방송부) | `godot/art/portraits/` 의 `manager` `coach` `buddy` `batting_coach` `neighbor` `senior` `mom` `junior` `reporter` `.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 흰 배경만 지움 (선배는 가로로 긴 그림이라 인물만 남게 잘랐다). 그림 오른쪽 아래에 Gemini 표시(✦)가 남아 있다 | Gemini 이용 약관을 따른다 | **확인 필요 — 출시 전에 유저가 쓴 요금제의 약관(생성물의 상업 이용, 표시·워터마크 조건)을 확인해 여기 적는다** |

| **(임시)** 배경 2장: 학교 운동장 · 더그아웃 | `godot/art/backgrounds/field.png` `dugout.png` | 유저가 **Google Gemini** 로 생성 (2026-10-11), 그대로 넣음 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 인물 일러스트 1장: 라이벌 | `godot/art/portraits/rival.png` | 유저가 준 그림 (2026-10-11). 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다. 흰 배경만 지움 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 3장: 관중석 · 집 앞 골목 · 우리 집 | `godot/art/backgrounds/stands.png` `street.png` `home.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 골목·집 그림 오른쪽 아래에 Gemini 표시(✦)가 있어 셋 다 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 1장: 교실 | `godot/art/backgrounds/classroom.png` | 유저가 준 그림 (2026-10-12), 그대로 넣음. 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 1학년 시나리오 인물 일러스트 9장 (2026-11-02, 세계관 인물로 교체·추가): 하린 · 엄마 · 오 감독 · 박준수 · 한도윤 · 정민재(새) · 나성만(새) · 김성호(새) · 윤재혁(새) | `godot/art/portraits/` 의 `neighbor` `mom` `coach` `buddy` `senior` `minjae` `scout` `seongho` `jaehyuk` `.png` (앞의 neighbor·mom·coach·buddy·senior 옛 그림을 덮어씀 — 옛 그림은 git 기록에) | 유저가 준 그림 (2026-11-02). 오른쪽 아래에 Gemini 표시(✦)가 있어 **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다. 회색·흰 배경을 지워 투명으로 (가장자리에서 이어진 배경만, 하린은 머리카락 사이 배경도). 준수·정민재·김성호는 ✦ 가 옷 위에 있어 남아 있다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

| **(임시)** 배경 1장: 야구부실 | `godot/art/backgrounds/clubroom.png` | 유저가 준 그림 (2026-11-02), 그대로 넣음. **Google Gemini** 생성으로 적는다 — 다른 도구면 고친다 | Gemini 이용 약관을 따른다 | **확인 필요** (위와 같음) |

~~타자·투수 캐릭터 포즈 그림 (Google Gemini, `godot/art/characters/`)~~ — 2026-10-31 도트 확정으로 **지웠다** (게임에 들어 있지 않음. 커밋 `809d631` 까지의 기록에만 있다).

AI 생성 에셋: 위 인물 일러스트 15장(옛 그림 중 덮어쓴 5장 제외), 배경 7장 (Google Gemini).

> 그림체는 원칙 6에 맞다 (대화 화면 일러스트는 애니메이션풍, 2026-10-11 결정). 남은 것은 상업 이용 약관 확인 하나다 — 확인 전까지는 임시로 본다.

## 인게임 도트 (2026-10-30 시험, 2026-10-31 확정)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| `godot/art/pixel/*.png` (경기장·타자·투수·포수·미트·수비 화면 선수) | 이 저장소의 `scripts/make_pixel_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |
| `godot/art/pixel/training.png` (훈련 장면 5개: 티 배팅 · 헬스장 스쿼트 · 피칭머신 선구안 · 트랙 달리기 · 침대에서 잠, 2026-11-01) | 이 저장소의 `scripts/make_training_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |
| `docs/refs/cutscene/*.png` (컷신 장면·인물 기준 그림 원본) → `godot/art/pixel/stills/*.png` (`scripts/import_cutscene_stills.py` 로 줄이고 색을 정리한 컷신 판, 2026-11-05) | 유저가 **Google Gemini** 로 생성 (프롬프트: `docs/scenario/cutscene-gemini-prompts.md`). 생성 도구 표시(반짝이)는 가공 때 지운다 | **확인 대기** — Gemini 이용 약관의 상업 이용 허용 여부를 유저가 확인한 뒤 적는다. 확인 전에는 출시 빌드에 넣지 않는다 |
| `godot/art/pixel/batter_ext.png`, `pitcher_ext.png` (인게임 타자 풀스윙·투수 투구, 2026-11-07 인게임 갈아엎기) ← 원본 `docs/refs/sprites/*.png` | **SpriteCook** (Claude 가 SpriteCook MCP 로 생성: 기준 그림 Nano Banana 2.1, 애니메이션 Pixel Engine v1.5). 타자 시점은 유저가 준 참고 그림(`docs/refs/sprites/user_batter_view_ref.webp`)을 고쳐 그렸다. `scripts/tidy_sheet.py` 가 칸 정리만 한다(색·윤곽 그대로). asset_id 는 `spritecook-assets.json` | **확인 대기** — SpriteCook 요금제(Apprentice)의 상업 이용 조건을 유저가 확인한 뒤 적는다. 확인 전에는 출시 빌드에 넣지 않는다 |
| `docs/refs/batter/*` (옛 시험: SpriteCook 옆 시점 스윙·도트 변환 미리보기) | SpriteCook (유저가 웹에서 생성) | 확인 대기 — 게임에는 안 들어간다 |
| `godot/art/pixel/field_ext.png` (타석 배경), `fielders_ext*.png` (수비수·주자 SD 16동작 + 팀 색·줄인 판, 2026-11-07) ← 원본 `docs/refs/sprites/field_bg_v2.png`·`fielders_sd_grid_v1.png` | **SpriteCook** (Claude 가 MCP 로 생성, Nano Banana 2.1). `scripts/tidy_sheet.py` 가 자르기·발 맞추기·색 줄이기(32색)·팀 색 돌리기만 한다 | **확인 대기** — 위 SpriteCook 줄과 같다 |
| `docs/refs/sprites/` 의 그림체 실험(일러스트·16비트 SD·그림체 비교, GPT 2.5·Nano Banana 2.1)과 유저가 준 참고 그림(`user_*`, Gemini 생성) | SpriteCook / 유저(Gemini) | 확인 대기 — 게임에는 안 들어간다 |
| `godot/art/pixel/cutscene.png`, `cutscene_bg.png` (컷신: 덕아웃·얼굴 클로즈업·타석·1루 가는 길·1루 베이스·하늘·운동장 전체 판, 감독·덕아웃 실루엣·김성호 얼굴·타격·달리기·날아가는 헬멧·발·미트·루심·먼 인물 칸, 2026-11-05) | 이 저장소의 `scripts/make_cutscene_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |
| `godot/art/pixel/minigame.png`, `minigame_bg.png` (입부 체력 테스트: 트랙·웨이트장·신호등 배경, 달리는 나·동기, 스쿼트, 반응 스윙, 신호등, 감독, 출발·결승선, 2026-11-01) | 이 저장소의 `scripts/make_minigame_art.py` 가 코드로 찍어 만든 그림 (AI 생성 아님, 외부 에셋 없음) | 가능 (자체 제작) |

## ElevenLabs 소리 (2026-11-07)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| `godot/audio/bgm/lobby.ogg` `home.ogg` (배경음악) | ElevenLabs Music (유저가 웹에서 생성, 보컬 없음). 원본 WAV 는 유저 맥(`~/Desktop/audio`)에 두고 `scripts/import_audio.py` 가 루프 이음매·크기만 맞춰 OGG 로 압축 | **확인 대기** — ElevenLabs 무료 플랜 생성물은 상업 이용 불가·출처 표기 필요. 유료 플랜(Starter 이상)에서 만든 것이어야 출시에 쓸 수 있다. 출시 전 플랜·약관 확인 |
| `godot/audio/bgm/game.ogg` (경기장 웅성거림) | ElevenLabs Sound Effects (유저가 웹에서 생성), 위와 같이 가공 | 확인 대기 (위와 같음) |
| 심판 콜 `godot/audio/sfx/call_strike.wav` `call_strikeout.wav` `call_ball.wav` | ElevenLabs 음성 (유저가 웹에서 생성, 디자인한 목소리 — 실존 인물 복제 아님), `scripts/import_audio.py` 가 앞 무음만 자르고 크기 맞춤 | 확인 대기 (위와 같음) |

## 배경음악 (2026-10-31)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| (옛, 2026-11-07 에 아래 ElevenLabs 곡으로 바뀜) `godot/audio/bgm/lobby.wav` `home.wav` `game.wav` | 이 저장소의 `scripts/make_bgm.py` 가 사인파·펄스파·톱니파·잡음을 코드로 합성한 곡 (관중 목소리도 톱니파 + 모음 공명 필터로 합성, 녹음 없음) (작곡·합성 모두 자체, AI 생성 아님, 샘플·외부 음원 없음). 기존 곡의 멜로디를 옮기지 않았다 | 가능 (자체 제작) |
| 효과음 (타격음·미트·바람·함성) | `godot/scripts/audio/sound_synth.gd` 가 실행 중에 코드로 합성 (파일 없음, 녹음·AI 생성 아님) | 가능 (자체 제작) |
| 관중 함성 `godot/audio/sfx/cheer_small.wav` `cheer_big.wav` | `scripts/make_bgm.py` 가 목소리 수십 개(톱니파 + 모음 공명 필터)·휘파람·박수를 코드로 합성 (녹음·AI 생성 아님) | 가능 (자체 제작) |
| (옛, 2026-11-07 에 아래 ElevenLabs 콜로 바뀜) 심판 콜 `godot/audio/sfx/call_strike.wav` `call_strikeout.wav` | `scripts/make_bgm.py` 가 포먼트 합성(성대 펄스 + 공명 4개를 시간에 따라 옮김)으로 만든 목소리 (녹음·TTS·AI 생성 아님) | 가능 (자체 제작) |

## 주자 전용 방향·헤드퍼스트 시트 (2026-11-10)
- 원본 `docs/refs/sprites/runners_directions_headfirst_v1.png`, 생성 지시 `runners_directions_headfirst_v1_prompt.md`. 내장 GPT 이미지 생성 도구 사용. 기존 프로젝트 SD 주자 그림을 참고했다.
- 게임용 `godot/art/pixel/runners_ext*.png`는 `scripts/tidy_sheet.py runners`로 칸 정리·축소·팀 색 변환. 상업 이용 약관의 별도 확인은 대기 상태.

## 인게임 타자·투수 고해상도 일러스트 (2026-11-10)

| 파일 | 원본 | 도구 | 상업 이용 |
|---|---|---|---|
| `godot/art/pixel/batter_ext.png` | `docs/refs/sprites/ingame_hitter_source.png` (유저 제공 `ingame_hitter.png`, 4x3 격자) | **확인 필요** (유저가 만든 AI 그림 — 쓴 도구를 유저에게 물어 적을 것) | 확인 필요 |
| `godot/art/pixel/pitcher_ext.png` | `docs/refs/sprites/ingame_pitcher_source.png` (유저 제공 `ingame_pitcher.png`, 4x4 격자) | **확인 필요** | 확인 필요 |

정리만 스크립트로 했다 (`scripts/grid_to_strip.py` 바닥 맞춤, `scripts/tidy_sheet.py` 칸 순서) — 색·윤곽은 그대로.

## SD 타자·투수 (2026-11-10)

| 파일 | 원본 | 도구 | 상업 이용 |
|---|---|---|---|
| `godot/art/pixel/batter_ext.png` | `docs/refs/sprites/sd_batter_swing_v1_source.png` (4x3 격자), 기준 그림 `sd_batter_ref_v1.png` | GPT 이미지 (ChatGPT, 유저가 생성) | OpenAI 약관상 생성물 권리는 사용자에게 — 출시 전 최신 약관 다시 확인 |
| `godot/art/pixel/pitcher_ext.png` | `docs/refs/sprites/sd_pitcher_throw_v1_source.png` (4x4 격자) | GPT 이미지 (ChatGPT, 유저가 생성) | 위와 같음 |

정리만 스크립트로 (`grid_to_strip.py` 바닥 맞춤, `tidy_sheet.py` 칸 순서) — 색·윤곽 그대로.
## 대화창 인물 일러스트 셀 채색 (2026-11-09)
| 파일 | 출처 | 상업 이용 |
|---|---|---|
| `godot/art/portraits/*.png` 17장 (인물 14명과 소율의 추가 표정 3장) | Codex GPT 이미지 생성. 하린 1번 시안을 화풍 기준으로, 교체 전 게임 일러스트를 인물 디자인 참고용으로 사용. 원본과 프롬프트는 `docs/refs/portraits/`. 게임용 PNG는 투명 배경을 유지해 크기만 줄임 | OpenAI 약관상 생성 결과물은 이용자에게 귀속되어 상업 이용 가능. 참고 이미지의 권리와 이용 조건은 별도 적용. [OpenAI 이용 약관](https://openai.com/policies/terms-of-use/) |
## 고교 로비 2D 골목 (2026-11-10)

- 원본은 `docs/refs/sprites/lobby_buildings_v1.png`와 `lobby_player_v1.png`, 생성 지시는 `lobby_2d_v1_prompt.md`에 있다. 내장 GPT 이미지 생성 도구를 사용했다. 상업 이용 약관 별도 확인 대기.
- 게임용 `godot/art/lobby/hs_facades.png`는 일곱 출입구를 기존 골목 좌표에 맞춰 합친 그림이다. `player.png`는 대기와 걷기 칸을 같은 크기로 정리한 시트다.

## SD 선수·경기장·메뉴 시안 (2026-11-10)

- 이번 대화에서 생성한 `batter_sd_v1~v3_source`, `pitcher_sd_reference_v1`, `pitcher_sd_v1_source`, `fielder_runner_2d_reference_v1`, `fielder_2d_poses_v1`, `fielder_2d_run_v1`, `runner_2d_actions_v1`, `runner_2d_run_away_left_v1`, `batting_stadium_2d_v1`는 내장 GPT 이미지 생성 도구로 제작했다. 원본과 프롬프트는 `docs/refs/sprites/`에 보관한다.
- `docs/mockups/main_menu_v1.png`와 `title_screen_v1.png`도 같은 도구로 만든 시안이다. 상업 이용 약관 별도 확인 대기.

## 경기 2D 일러스트 적용 (2026-11-10)

- `godot/art/illustrated/`의 수비수, 주자, 타석 배경은 위 GPT 이미지 시안을 게임 좌표에 맞춘 것이다. `docs/refs/sprites/pack_field_art.py`로 칸과 배경을 정리하고, 상대 팀 유니폼은 기존 파랑 계열 색 변환을 적용했다. 상업 이용 약관 별도 확인 대기.
