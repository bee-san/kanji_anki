package dev.bee.kanjianki.baseline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.printToString
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Stable semantics and image assertions for the route catalog.
 *
 * Checked-in pixel goldens are recorded on the pinned emulator. A missing
 * golden fails with the exact expected path; `recordActual` writes an approval
 * candidate under the app's external files directory.
 */
internal object AndroidGoldenAssertions {
    data class PixelDiff(
        val width: Int,
        val height: Int,
        val mismatchedPixels: Int,
        val maximumChannelDelta: Int,
        val mismatchFraction: Double,
        val diffBitmap: Bitmap,
    )

    fun normalizedSemantics(root: SemanticsNodeInteraction): String =
        normalizeSemantics(root.printToString(maxDepth = 100))

    fun normalizeSemantics(raw: String): String =
        raw.lineSequence()
            .map { line ->
                line
                    .replace(SEMANTICS_NODE_WITH_BOUNDS, "Node")
                    .replace(SEMANTICS_NODE_ID, "Node")
                    .replace(GENERATED_ID, "$1=<id>")
                    .replace(OBJECT_IDENTITY, "@<id>")
                    .trimEnd()
            }
            .filter(String::isNotBlank)
            .joinToString("\n")

    fun assertSemanticAnchors(
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
        normalizedSemantics: String,
    ) {
        captureCase.semanticAnchors.forEach { anchor ->
            assertTrue(
                "${captureCase.id} semantics did not contain required anchor: $anchor\n$normalizedSemantics",
                normalizedSemantics.contains(anchor),
            )
        }
    }

    fun assertSemanticsGolden(
        testContext: Context,
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
        normalizedSemantics: String,
    ) {
        val expected = testContext.assets.open(captureCase.semanticsGolden)
            .use { String(it.readBytes(), StandardCharsets.UTF_8).trimEnd() }
        assertEquals("Semantics changed: ${captureCase.id}", expected, normalizedSemantics.trimEnd())
    }

    fun captureBitmap(root: SemanticsNodeInteraction): Bitmap =
        root.captureToImage().asAndroidBitmap()

    fun pixelFingerprint(bitmap: Bitmap): String {
        val pixels = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        return MessageDigest.getInstance("SHA-256")
            .digest(pixels.array())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun assertViewport(
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
        bitmap: Bitmap,
        expectedWidth: Int,
        expectedHeight: Int,
    ) {
        assertEquals("${captureCase.id} viewport width", expectedWidth, bitmap.width)
        assertEquals("${captureCase.id} viewport height", expectedHeight, bitmap.height)
    }

    fun assertImageGolden(
        targetContext: Context,
        testContext: Context,
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
        actual: Bitmap,
        perChannelTolerance: Int = DEFAULT_CHANNEL_TOLERANCE,
        maximumMismatchFraction: Double = DEFAULT_MISMATCH_FRACTION,
    ) {
        val expected = testContext.assets.open(captureCase.imageGolden).use(BitmapFactory::decodeStream)
        val diff = compare(actual, expected, perChannelTolerance)
        if (diff.mismatchFraction > maximumMismatchFraction) {
            val output = outputDirectory(targetContext)
            writePng(actual, File(output, "${captureCase.id}.actual.png"))
            writePng(diff.diffBitmap, File(output, "${captureCase.id}.diff.png"))
        }
        assertTrue(
            "${captureCase.id} pixel mismatch ${diff.mismatchFraction}; " +
                "allowed=$maximumMismatchFraction max-channel-delta=${diff.maximumChannelDelta}",
            diff.mismatchFraction <= maximumMismatchFraction,
        )
    }

    fun recordActual(
        targetContext: Context,
        captureCase: AndroidRouteBaselineCatalog.CaptureCase,
        bitmap: Bitmap,
        normalizedSemantics: String,
    ): File {
        val output = outputDirectory(targetContext)
        val image = File(output, captureCase.imageGolden)
        val semantics = File(output, captureCase.semanticsGolden)
        writePng(bitmap, image)
        semantics.parentFile?.mkdirs()
        semantics.writeText(normalizedSemantics.trimEnd() + "\n", StandardCharsets.UTF_8)
        return output
    }

    fun compare(actual: Bitmap, expected: Bitmap, perChannelTolerance: Int): PixelDiff {
        assertEquals("Golden image width", expected.width, actual.width)
        assertEquals("Golden image height", expected.height, actual.height)
        val width = actual.width
        val height = actual.height
        val actualPixels = IntArray(width * height)
        val expectedPixels = IntArray(width * height)
        val diffPixels = IntArray(width * height)
        actual.getPixels(actualPixels, 0, width, 0, 0, width, height)
        expected.getPixels(expectedPixels, 0, width, 0, 0, width, height)
        var mismatches = 0
        var maximumDelta = 0
        actualPixels.indices.forEach { index ->
            val a = actualPixels[index]
            val e = expectedPixels[index]
            val delta = maxOf(
                abs(Color.alpha(a) - Color.alpha(e)),
                abs(Color.red(a) - Color.red(e)),
                abs(Color.green(a) - Color.green(e)),
                abs(Color.blue(a) - Color.blue(e)),
            )
            maximumDelta = maxOf(maximumDelta, delta)
            if (delta > perChannelTolerance) {
                mismatches += 1
                diffPixels[index] = Color.MAGENTA
            } else {
                val grey = (Color.red(e) + Color.green(e) + Color.blue(e)) / 3
                diffPixels[index] = Color.argb(255, grey, grey, grey)
            }
        }
        val diffBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        diffBitmap.setPixels(diffPixels, 0, width, 0, 0, width, height)
        return PixelDiff(
            width = width,
            height = height,
            mismatchedPixels = mismatches,
            maximumChannelDelta = maximumDelta,
            mismatchFraction = mismatches.toDouble() / actualPixels.size.coerceAtLeast(1),
            diffBitmap = diffBitmap,
        )
    }

    private fun outputDirectory(context: Context): File =
        File(context.getExternalFilesDir(null), "goal165-ui-baselines").apply { mkdirs() }

    private fun writePng(bitmap: Bitmap, destination: File) {
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not write ${destination.absolutePath}"
            }
        }
    }

    private val SEMANTICS_NODE_WITH_BOUNDS =
        Regex("""Node #\d+ at \([^)]*\)px(?:, Tag: '[^']*')?""")
    private val SEMANTICS_NODE_ID = Regex("""Node #\d+""")
    private val GENERATED_ID = Regex("""\b(id|nodeId|paneId)=\d+\b""")
    private val OBJECT_IDENTITY = Regex("""@[0-9a-fA-F]+\b""")
    private const val DEFAULT_CHANNEL_TOLERANCE = 2
    private const val DEFAULT_MISMATCH_FRACTION = 0.001
}
