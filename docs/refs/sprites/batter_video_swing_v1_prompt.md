# 영상 참조 SD 타자 스윙 v1

- 도구: 내장 GPT 이미지 생성(image_gen), 투명 배경 요청.
- 동작·시점: 사용자 제공 `화면 기록 2026-11-10 오후 2.58.12.mov`(10.935초). 0.0 / 3.0 / 5.0 / 5.4 / 5.6 / 5.8 / 6.0 / 6.2 / 6.6 / 7.0 / 7.4 / 8.0초를 순서대로 참고했다.
- 화풍: `docs/refs/sprites/sd_pitcher_throw_v1_source.png`. 기존 투수의 SD 비율·윤곽·셀 채색을 따르고 파란 타자 유니폼과 헬멧으로 변경.
- 영상은 자세와 시점만 참고. 실제 선수·구단·로고·등번호·UI는 사용하지 않음.

## 생성 프롬프트

Use case: stylized-concept
Asset type: production baseball batting animation sprite sheet, transparent RGBA PNG.
Create EXACTLY 12 full-body frames in a strict 4-column x 3-row grid, reading left to right then top to bottom, ideally 4096x3072 with square cells. One and only one character per cell.
REFERENCE 1 (12-frame video contact sheet): binding CAMERA and POSE SEQUENCE ONLY. Follow each corresponding numbered video pose one-to-one, adapting anatomy to the SD body. Do not copy any video costume, logo, number, real person's likeness, stadium, shadows, UI or lettering.
REFERENCE 2 (red-uniform SD pitcher): binding STYLE, character anatomy, dark brown hair, skin tone, 2.5-head body proportions, thick dark navy outlines, crisp smooth cel-shaded high resolution 2D illustration. Draw this same character as a right-handed BATTER with blue batting helmet instead of red cap, blue sleeves/socks/piping instead of red, white uniform, dark cleats, white batting gloves, and one plain straight tan wooden bat. No glove/mitt.
CAMERA: precisely keep reference 1 fixed catcher-side batting-game camera behind home plate looking toward pitcher. Batter faces screen-right and away into scene. No camera orbit. In ready position helmet, raised hands and right shoulder partially obscure face; do not turn face toward viewer. Preserve the projected shoulder/hip axes of each video frame. As torso rotates from frame 6 onward, progressively reveal the broad BACK of jersey, as in video. Final frames mostly BACK toward viewer and head looking away toward pitcher. Do not draw a set of generic side-profile swings or a front-facing character.
12 corresponding poses:
1 ready, bent knees, both feet grounded depth-staggered, hands high beside rear shoulder, bat diagonally up-left.
2 load and front knee starts lifting.
3 high front-knee lift, weight over planted back leg, hands held back.
4 raised front knee starts extending into stride.
5 front foot lands ahead in depth, hands stay back.
6 hips rotate and rear heel begins pivoting, hands driving forward, bat lagged behind head.
7 middle-height contact: torso turned with BACK increasingly visible, both hands together, bat projects screen-right away into depth with natural foreshortening.
8 arms extend after contact, bat extended screen-right.
9 early follow-through, arms cross and bat rises to screen-left, rear foot pivots on toe.
10 high follow-through, bat lifted near vertical on screen-left, back visible.
11 bat wraps over left shoulder behind helmet, back visible.
12 completed high follow-through and balanced finish, back mainly facing camera and rear foot pivoted, as video.
GRID: equal square cells, same head size and character scale in all frames, fixed registration and common ground line at 90% cell height in each row. Only natural stride/pivot changes feet, no independent recentering per frame. Body around 65-70% cell height to accommodate bat; entire figure PLUS entire bat always inside each cell with generous clear padding. Never let bat overlap another cell or canvas boundary.
BAT ANATOMY CRITICAL: one rigid, unbent, single straight continuous wooden bat, knob-handle-barrel collinear. Grip anatomically correct right-handed swing: left hand lower near knob and right hand above toward barrel. No floating bat segments, no bent bat, no duplicate bat. Hands and limbs stay coherent.
True fully transparent background, clean alpha, no floor, no ground shadow, no aura, no colored fringe, no effects, no motion blur, no speed lines, no swing trails, no baseball, no text, no logos, no jersey number, no borders, no grid lines. Not pixel art, not 3D, not realistic.
