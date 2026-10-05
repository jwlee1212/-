package baseballgm.app.preview

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File

/** 360dp 폭, 2배 밀도 (아이폰 화면에서 보는 크기) */
private const val WIDTH_DP = 360
private const val SCALE = 2f

/** 비교 이미지에서 전/후 사이 여백(px) */
private const val GAP_PX = 24

/**
 * [screenCatalog] 의 모든 화면을 PNG 로 굽는다 (2026-10-01, 절제 작업).
 *
 * `./gradlew :app:renderScreens -Pphase=before|after` → `docs/screenshots/<set>/<phase>/<id>-<light|dark>.png`.
 * after 를 구울 때 before 가 있으면 `compare/` 에 전(왼쪽)·후(오른쪽)를 나란히 붙인 이미지도 만든다.
 * 인자 두 번째는 화면 id 필터 (예: `-Pscreens=roster,home`), 세 번째는 묶음 이름(`-Pset=...`, 기본 restraint).
 * 작업마다 묶음을 따로 두어 이전 작업의 전/후 사진을 덮어쓰지 않는다.
 */
fun main(args: Array<String>) {
    val phase = args.getOrNull(0) ?: "after"
    val only = args.getOrNull(1)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    val set = args.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "restraint"
    val base = File("docs/screenshots/$set")
    val outDir = File(base, phase).apply { mkdirs() }
    screenCatalog.filter { only.isEmpty() || it.id in only }.forEach { entry ->
        listOf(false, true).forEach { dark ->
            val name = "${entry.id}-${if (dark) "dark" else "light"}.png"
            val image = render(entry, dark)
            File(outDir, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            if (phase == "after") {
                val before = File(base, "before/$name")
                if (before.isFile) {
                    val compareDir = File(base, "compare").apply { mkdirs() }
                    File(compareDir, name).writeBytes(sideBySide(Image.makeFromEncoded(before.readBytes()), image))
                }
            }
            println("구움: $phase/$name")
        }
    }
    // ImageComposeScene 이 띄운 코루틴이 남아 JVM 이 안 끝나는 경우가 있다
    kotlin.system.exitProcess(0)
}

private fun render(entry: ScreenEntry, dark: Boolean): Image {
    val scene = ImageComposeScene(
        width = (WIDTH_DP * SCALE).toInt(),
        height = (entry.heightDp * SCALE).toInt(),
        density = Density(SCALE),
    ) { PreviewFrame(dark) { entry.content() } }
    // 글꼴·지연 목록이 자리 잡도록 몇 프레임 돌린 뒤 마지막 프레임을 쓴다 (애니메이션은 끝까지)
    var image = scene.render(0)
    listOf(16L, 100L, 400L, 800L, 2_000L).forEach { millis -> image = scene.render(millis * 1_000_000) }
    scene.close()
    return image
}

private fun sideBySide(before: Image, after: Image): ByteArray {
    val surface = Surface.makeRasterN32Premul(before.width + GAP_PX + after.width, maxOf(before.height, after.height))
    val canvas = surface.canvas
    canvas.clear(0xFF808080.toInt())
    canvas.drawImage(before, 0f, 0f)
    canvas.drawImage(after, (before.width + GAP_PX).toFloat(), 0f)
    canvas.drawRect(Rect.makeXYWH(before.width.toFloat(), 0f, GAP_PX.toFloat(), surface.height.toFloat()), Paint().apply { color = 0xFF808080.toInt() })
    return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}
