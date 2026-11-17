# 메인 캐릭터 골목 걷기 — 오른쪽 옆모습 v1

- 도구: 내장 GPT 이미지 생성
- 기준: docs/refs/sprites/main_player_tpose_v2.png
- 요청: 오른쪽 걷기 8프레임, 4열×2행, 투명 배경. 실제 생성 해상도·칸 크기는 인계 메모에 기록.

Use case: identity-preserve
Create ONE production 2D WALK-CYCLE SPRITE SHEET based on the attached approved T-pose reference character. This is animation art for a side-scrolling school neighborhood in a Korean baseball game. Use the reference as EXACT CHARACTER IDENTITY AND COSTUME, not as a pose to keep.
CHARACTER LOCK: same fictional teenage male baseball player, short tousled black hair, unbranded muted navy cap with forward brim, dark simple eyes, same head/body ratio and compact stylized teenage build, off-white button-front baseball uniform with thin navy piping, navy undershirt sleeves, dark belt, off-white pants, navy high socks, worn ivory lace-up sneakers with dark soles. Preserve the hand-inked navy outlines, matte flat colors, sparing texture, one understated shadow tone. Do NOT change proportions, cap size, uniform details or face across frames. No bag, bat, ball, glove or extra accessories.
OUTPUT FORMAT: landscape 3:2 canvas, ideally 3072×2048 pixels. EXACTLY FOUR columns and TWO rows of equal rectangular cells, eight frames total. On that ideal canvas each cell is 768×1024. True transparent background. Absolutely no grid lines, borders, labels, numbers, text, background, floor line, shadow or glow. Cell boundaries are invisible.
FRAME ORDER row-major: top row frames 1 2 3 4; bottom row frames 5 6 7 8. All eight cells contain ONE FULL-BODY SAME BOY in strict RIGHT-FACING SIDE PROFILE, facing screen right in every cell. Both feet oriented right. Never front view, never turns around, no three-quarter front view. Side profile face and cap brim clearly face RIGHT.
WALK LOOP eight evenly spaced phases:
1 left leg forward heel strike, right leg trailing toe, right arm forward left arm back.
2 weight moves over left foot, right heel lifts, slight body downward.
3 left leg supports, right knee passes forward, opposite arms passing neutral.
4 left heel rises, right knee forward and foot approaching next contact, slight body upward.
5 right leg forward heel strike, left leg trailing toe, left arm forward right arm back.
6 weight over right foot, left heel lifts, slight body downward.
7 right leg supports, left knee passes forward, opposite arms neutral.
8 right heel rises, left knee forward approaching frame 1 contact, slight body upward.
Walking is easy relaxed unhurried everyday walk, arms gently swinging with loose relaxed hands, small natural elbow bend, no exaggerated high knees or marching, no jogging. Opposite arm and leg forward. Show subtly darker far arm and far leg for clarity while preserving same costume. NO motion blur, afterimages or speed lines. Feet clear and well-formed, exactly two arms two legs each frame.
REGISTRATION IS CRITICAL: same sprite scale in every cell, same head size, same fixed horizontal pelvis x = center of its cell. Shared virtual ground at 92% of each cell height; grounded foot sole aligns to this baseline consistently. Full figure height about 82% of each cell, tiny natural body bob only 1-2% height, no size breathing. Keep cap, hands and both feet within each cell with comfortable padding; no inter-cell overlap. This is walking IN PLACE: character does not translate across cell. Foot poses vary meaningfully across all eight drawings, not copies, and frame 8 loops seamlessly into frame 1.
Use transparent alpha, crisp normal antialiased cutout silhouette. NO soft halo, no vignette, no drop shadow, no scenery. Exactly 8 complete silhouettes, no ninth frame, no duplicated front-view T-pose. Reference supplies character only; replace pose with right-profile walking in every cell.
