package com.tehoubi.ddrtracker.ocr

import com.tehoubi.ddrtracker.core.ocr.Box
import com.tehoubi.ddrtracker.core.ocr.ResultScreenParser
import com.tehoubi.ddrtracker.core.ocr.StatLabel
import com.tehoubi.ddrtracker.core.songs.SongCatalog
import org.junit.Assume.assumeTrue
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.test.Test

/**
 * Renders docs/scan.gif: the real pipeline reading samples/sample1.jpg, step
 * by step. Every box and number in it comes from this run, not from a mock.
 * Runs only when asked:
 *
 *     SCAN_GIF=docs/scan.gif ./gradlew :ocr:test --tests '*ScanGif*' --rerun
 */
class ScanGif {

    private val bg = Color(0x0F, 0x0B, 0x1E)
    private val accent = Color(0x3F, 0xD0, 0xF5)
    private val ok = Color(0x5B, 0xE3, 0x9A)
    private val text = Color(0xEE, 0xEA, 0xF8)
    private val dim = Color(0xA9, 0xA3, 0xC2)

    private val w = 480
    private val photoH = 600
    private val captionH = 72
    private val h = photoH + captionH

    @Test
    fun render() {
        val target = System.getenv("SCAN_GIF")
        assumeTrue("set SCAN_GIF to render the GIF", target != null)
        val parser = ResultScreenParser(SongCatalog.bundled())
        val photo = Samples.photo("sample1.jpg")
        val reading = ResultScreenReader(Samples.ocr).read(photo, parser)
        val page = reading.page
        val panel = parser.statsRegion(page) ?: error("no stats panel")
        val r = reading.scan.result

        // The photo, fitted into the frame; page coordinates map onto it.
        val fit = minOf(w / page.width.toDouble(), photoH / page.height.toDouble())
        val ox = (w - page.width * fit) / 2
        val oy = (photoH - page.height * fit) / 2
        val shown = toBuffered(photo).scaledTo((page.width * fit).toInt(), (page.height * fit).toInt())
        fun Box.onScreen() = java.awt.Rectangle(
            (ox + left * fit).toInt(), (oy + top * fit).toInt(),
            maxOf(1, (width * fit).toInt()), maxOf(1, (height * fit).toInt()),
        )

        val frames = mutableListOf<Pair<BufferedImage, Int>>()
        fun frame(caption: String, sub: String, delayCs: Int, draw: Graphics2D.() -> Unit) {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            img.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                color = bg; fillRect(0, 0, w, h)
                draw()
                color = bg; fillRect(0, photoH, w, captionH)
                color = text; font = Font(Font.SANS_SERIF, Font.BOLD, 19); drawString(caption, 18, photoH + 30)
                color = dim; font = Font(Font.SANS_SERIF, Font.PLAIN, 14); drawString(sub, 18, photoH + 54)
                dispose()
            }
            frames += img to delayCs
        }
        fun Graphics2D.photo() = drawImage(shown, ox.toInt(), oy.toInt(), null)
        fun Graphics2D.boxes(lines: List<Box>, c: Color) {
            color = c; stroke = BasicStroke(1.6f)
            lines.forEach { draw(it.onScreen()) }
        }

        frame("1. A photo of the result screen", "sample1.jpg, ${photo.width}×${photo.height}, straight from a phone", 140) { photo() }

        val sorted = page.lines.sortedBy { it.box.top }.map { it.box }
        for (step in 1..4) {
            val upTo = sorted.size * step / 4
            frame("2. PP-OCRv6 finds every line of text", "${sorted.size} lines, on the phone with ONNX Runtime", if (step == 4) 120 else 35) {
                photo(); boxes(sorted.take(upTo), accent)
            }
        }

        frame("3. The stats panel is read again", "cut from the full-resolution photo, where small digits are sharp", 140) {
            photo()
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f)
            color = bg
            val p = panel.onScreen()
            fillRect(0, 0, w, p.y); fillRect(0, p.y + p.height, w, photoH - p.y - p.height)
            fillRect(0, p.y, p.x, p.height); fillRect(p.x + p.width, p.y, w - p.x - p.width, p.height)
            composite = AlphaComposite.SrcOver
            color = accent; stroke = BasicStroke(3f); draw(p)
        }

        // The judgments table (the panel's left part) blown up, with what was read on each line.
        val table = Box(panel.left, panel.top, panel.left + panel.width * 2 / 5, panel.bottom)
        val inTable = page.lines.filter { table.contains(it.box.centerX, it.box.centerY) }
        val crop = toBuffered(photo.crop(Box(
            (table.left * photo.width / page.width), (table.top * photo.height / page.height),
            (table.right * photo.width / page.width), (table.bottom * photo.height / page.height),
        )))
        val zoom = minOf((w - 40) / crop.width.toDouble(), (photoH - 60) / crop.height.toDouble())
        val zx = (w - crop.width * zoom) / 2; val zy = (photoH - crop.height * zoom) / 2
        val zoomed = crop.scaledTo((crop.width * zoom).toInt(), (crop.height * zoom).toInt())
        val zf = zoom * photo.width / page.width
        frame("3. The stats panel is read again", "each box is one line, labelled with what the recogniser read", 240) {
            drawImage(zoomed, zx.toInt(), zy.toInt(), null)
            font = Font(Font.SANS_SERIF, Font.BOLD, 15)
            for (l in inTable) {
                val x = (zx + (l.box.left - table.left) * zf).toInt(); val y = (zy + (l.box.top - table.top) * zf).toInt()
                val bw = (l.box.width * zf).toInt(); val bh = (l.box.height * zf).toInt()
                color = accent; stroke = BasicStroke(1.6f); drawRect(x, y, bw, bh)
                val label = l.text.take(14)
                val tw = fontMetrics.stringWidth(label)
                color = accent; fillRect(x, y - 18, tw + 6, 18)
                color = bg; drawString(label, x + 3, y - 4)
            }
        }

        frame("4. Checked against DDR's scoring rules", checkLine(reading), 400) {
            val x = 28; var y = 120
            color = text; font = Font(Font.SANS_SERIF, Font.BOLD, 30); drawString(r.title?.value ?: "?", x, y)
            y += 30; color = dim; font = Font(Font.SANS_SERIF, Font.PLAIN, 17)
            drawString("${r.style?.value?.name?.lowercase()?.replaceFirstChar { it.uppercase() }} · ${r.difficulty?.value?.name} ${r.level?.value}", x, y)
            y += 62; color = text; font = Font(Font.SANS_SERIF, Font.BOLD, 46)
            drawString("%,d".format(r.score?.value ?: 0), x, y)
            font = Font(Font.SANS_SERIF, Font.BOLD, 18)
            pill("${r.grade?.value?.label}", x, y + 18)
            pill("${r.lamp?.value?.label}", x + 92, y + 18)
            y += 92
            val rows = listOf(
                "MARVELOUS" to StatLabel.MARVELOUS, "PERFECT" to StatLabel.PERFECT, "GREAT" to StatLabel.GREAT,
                "GOOD" to StatLabel.GOOD, "OK" to StatLabel.OK, "MISS" to StatLabel.MISS,
                "EX SCORE" to StatLabel.EX_SCORE, "MAX COMBO" to StatLabel.MAX_COMBO,
                "FAST" to StatLabel.FAST, "SLOW" to StatLabel.SLOW,
            )
            font = Font(Font.SANS_SERIF, Font.PLAIN, 17)
            rows.forEachIndexed { i, (name, label) ->
                val cx = if (i % 2 == 0) x else x + 220
                val cy = y + (i / 2) * 34
                val worked = label.name.lowercase().replace('_', ' ') in reading.scan.inferred ||
                    reading.scan.issues.any { label.name.lowercase() in it.message.lowercase() }
                color = dim; drawString(name, cx, cy)
                color = if (worked) ok else text
                drawString("${r.stats[label]?.value ?: "–"}${if (worked) " *" else ""}", cx + 120, cy)
            }
            y += 5 * 34 + 10
            color = ok; font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            drawString("* not read off the screen: worked out from the rest", x, y)
        }

        writeGif(frames, File(target))
    }

    private fun checkLine(reading: ResultScreenReader.Reading) =
        if (reading.scan.isConsistent) "score and EX score both follow from the judgments ✓" else "doesn't add up: you'd be asked to check it"

    private fun Graphics2D.pill(label: String, x: Int, y: Int) {
        val tw = fontMetrics.stringWidth(label)
        color = Color(0xFF, 0xD5, 0x4A); fillRoundRect(x, y, tw + 20, 30, 12, 12)
        color = Color(0x1A, 0x14, 0x05); drawString(label, x + 10, y + 21)
    }

    private fun toBuffered(img: RgbImage) = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).apply {
        setRGB(0, 0, img.width, img.height, img.argb, 0, img.width)
    }

    private fun BufferedImage.scaledTo(tw: Int, th: Int): BufferedImage {
        // Halve repeatedly first: one big bilinear step from a 12 MP photo aliases badly.
        var cur = this
        while (cur.width / 2 >= tw && cur.height / 2 >= th) cur = cur.resized(cur.width / 2, cur.height / 2)
        return cur.resized(tw, th)
    }

    private fun BufferedImage.resized(tw: Int, th: Int) = BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB).also {
        it.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(this@resized, 0, 0, tw, th, null)
            dispose()
        }
    }

    private fun writeGif(frames: List<Pair<BufferedImage, Int>>, file: File) {
        file.parentFile?.mkdirs()
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        ImageIO.createImageOutputStream(file).use { out ->
            writer.output = out
            writer.prepareWriteSequence(null)
            frames.forEachIndexed { i, (img, delay) ->
                val params = writer.defaultWriteParam
                val meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), params)
                val format = meta.nativeMetadataFormatName
                val root = meta.getAsTree(format) as IIOMetadataNode
                root.appendChild(IIOMetadataNode("GraphicControlExtension").apply {
                    setAttribute("disposalMethod", "none"); setAttribute("userInputFlag", "FALSE")
                    setAttribute("transparentColorFlag", "FALSE"); setAttribute("transparentColorIndex", "0")
                    setAttribute("delayTime", delay.toString())
                })
                if (i == 0) root.appendChild(IIOMetadataNode("ApplicationExtensions").apply {
                    appendChild(IIOMetadataNode("ApplicationExtension").apply {
                        setAttribute("applicationID", "NETSCAPE"); setAttribute("authenticationCode", "2.0")
                        userObject = byteArrayOf(1, 0, 0) // loop forever
                    })
                })
                meta.setFromTree(format, root)
                writer.writeToSequence(IIOImage(img, null, meta), params)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }
}
