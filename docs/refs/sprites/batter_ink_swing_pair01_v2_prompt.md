# 타격 2컷 시트 v2 생성 프롬프트

도구: 내장 GPT image_gen
설정: transparent_background=true

References:
/Users/ijaeu/Downloads/ChatGPT 이미지 2026년 11월 15일 오후 05_41_47.png
Actual output: 1774x887 px, PNG color type 6

```text
Use case: style-transfer / precise-object-edit.
Create a high resolution TWO-FRAME baseball sprite sheet faithfully redrawing ONLY the specified two cells from the attached 12-frame source. Requested output 3072x1536 pixels, transparent alpha PNG, exactly TWO equal square panels side by side, no borders or text. Maximize native detail; entire body and bat visible in each panel with safe margins.
SOURCE GRID: four columns by three rows. Numbering left to right top to bottom: row1=1,2,3,4; row2=5,6,7,8; row3=9,10,11,12. Each output panel corresponds to EXACTLY its specified source frame, no invented intermediate poses.
EXACT EDIT SCOPE: ONLY in source frames 1,2,3, rotate the HEAD and HELMET to MATCH the rear-facing head orientation of source frames 4 onward, especially 8-12. Show back of navy helmet, back hair and nape instead of side face/eye. Head stays aimed at the same target at screen TOP / 12 o'clock, AWAY from viewer, throughout animation. Do not rotate torso to match head. Keep frames 1-3 body pose, arms, hands, bat angle and leg/foot stance exactly as attached source. Frames 4-12: keep original head and body poses, just render larger with more detail. This is NOT a redesign of stance or camera.
ART LOCK: same young black-haired boy, navy batting helmet, ivory button baseball jersey and pants, navy undersleeves/belt/tall socks, ivory sneakers, tan wooden bat held in both hands. Same moderately large head proportions, hand-inked dark navy contours, warm subtle paperlike texture and gentle cel shading. No pixel art, no realism, no 3D.
REGISTRATION LOCK: fixed source rear/side camera, fixed anatomical scale, identical panel framing across all 6 pairs. Treat source cell boundaries as registration canvas: do NOT individually center or rescale each pose. Match original local body/foot placements, and preserve pivot/contact points within each pose. Use source cell baseline about y=.96 in every panel, exact same ground baseline; preserve original stance, rear heel pivot and planted foot. No new stepping, widened stance, jumping or foot sliding. Source framing retains bat clearance. Retain the original relationship of body, hands, bat and feet.
If a second input is supplied, it is generated pair 1 for consistent style/scale/ground baseline ONLY; source pose must still match input 1's specified numbered cell. Do not repeat pair 1's poses.
No baseball, scenery, floor, ground shadows, glow, grid, captions, labels, lettering, logos, watermarks or motion trails. Transparent background. Two full-body action sprites only.

LEFT PANEL = source frame 1 (row1 col1), ready stance, hands at shoulder, bat diagonal up-left; RIGHT PANEL = source frame 2 (row1 col2), subtle load. In BOTH panels change ONLY head/helmet direction to the rear-facing head view seen in later frames. KEEP original bent knees and feet overlapping in rear/side view; do not make a broad rear-facing body stance.
```
