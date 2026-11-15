# 투수 투구 시트 v1 생성 프롬프트

도구: 내장 GPT image_gen
설정: transparent_background=true, 첨부 두 파일 참조
실제 출력: 1254×1254 PNG (알파 채널 있음)

```text
Use case: style-transfer
Asset type: production 2D baseball pitcher animation sprite sheet.
Input image 1 is the ONLY character identity, costume, proportions and drawing style reference. Input image 2 is ONLY the 16-pose animation choreography and 4 by 4 layout reference; do not retain its pixel art, realistic anatomy, red uniform, background or edge artifacts.
Redraw the entire second sheet using exactly the young illustrated baseball boy from image 1: same large expressive dark eyes, tousled black hair, navy blue cap, ivory button-front uniform and pants, navy undersleeves, belt and tall socks, ivory sneakers, warm peach skin. Preserve image 1's body proportions, dark navy hand-inked outlines, warm subtle paperlike color texture and gentle cel shading. Do not exaggerate into a tiny baby chibi. Add a brown glove on his LEFT hand and baseball in his RIGHT hand. Consistent same character throughout.
Square 2048 x 2048 canvas, precisely four columns and four rows, 16 equal 512 x 512 cells, one full body pose in each cell. Transparent alpha background with no shadows, glow, scenery, grid lines, captions, numbers, logos or text. Consistent scale and camera, same local ground baseline near each cell bottom. Entire bodies, shoes, cap, glove and hands visible with safe padding. Maintain the three-quarter side camera of image 2 throughout, with pitcher delivering toward the lower foreground as in image 2, not a turnaround.
Read animation frames left-to-right then top-to-bottom. Match these chronological poses closely to corresponding source cells:
Row 1: (1) upright set, both hands together at chest; (2) slight knee bend preparing; (3) left knee lifting to waist; (4) left knee at peak, balance on right leg.
Row 2: (5) peak knee lift and torso coil; (6) left leg begins extending down forward and hands separating; (7) left foot strides forward, right throwing elbow draws back; (8) long stride, left glove extends toward target, right arm cocked behind.
Row 3: (9) front left foot planted, hips rotate and right arm accelerates; (10) right overhand throwing arm reaches high release with a small ball just leaving hand; (11) arm swings down across body, chest follows forward; (12) deeper follow-through, right rear leg lifts.
Row 4: (13) torso leaning forward, throwing arm low and rear leg bent up; (14) fullest follow-through rear leg swinging high; (15) rear leg lowers, torso begins recovering; (16) balanced finish starting to stand.
Anatomically coherent right-handed pitch, left glove stays on left hand and never switches. Distinct successive poses, no duplicates, consistent costume and face. Smooth high resolution illustration, not pixel art, not 3D or photorealistic. Recreate action as new original drawings; preserve the first reference's visual personality.
```
