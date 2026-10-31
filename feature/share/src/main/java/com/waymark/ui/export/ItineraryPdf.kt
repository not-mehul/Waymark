package com.waymark.ui.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import com.waymark.domain.logic.ItineraryDocument.Block
import java.io.OutputStream

/**
 * Lays an itinerary out on A4 pages, in ink that prints: dark type on white,
 * one accent, nothing that needs colour to read. Each block is measured
 * before it is drawn, so a booking is never split across a page break.
 */
object ItineraryPdf {

    fun write(blocks: List<Block>, footer: String, out: OutputStream) {
        val document = PdfDocument()
        try {
            val pages = Pages(document, footer)
            blocks.forEach(pages::draw)
            pages.finish()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}

private class Pages(private val document: PdfDocument, private val footer: String) {

    private var page: PdfDocument.Page? = null
    private var number = 0
    private var y = 0f

    private val canvas: Canvas get() = requireNotNull(page).canvas

    fun draw(block: Block) {
        when (block) {
            is Block.Title -> title(block)
            is Block.Section -> section(block.text)
            is Block.Day -> day(block)
            is Block.Entry -> entry(block)
            is Block.Row -> row(block)
            is Block.Note -> place(layout(block.text, NOTE, CONTENT_WIDTH), gapAfter = 10f)
        }
    }

    fun finish() {
        closePage()
    }

    private fun title(block: Block.Title) {
        val name = layout(block.name, TITLE, CONTENT_WIDTH)
        val summary = block.summary.takeIf { it.isNotBlank() }?.let { layout(it, SUBTITLE, CONTENT_WIDTH) }
        val dates = layout(block.dates, DATES, CONTENT_WIDTH)
        val party = block.party?.let { layout(it, BODY_DIM, CONTENT_WIDTH) }
        val height = listOfNotNull(name, summary, dates, party).sumOf { it.height + 6 }.toFloat() + 24f
        room(height)
        listOfNotNull(name, summary, dates, party).forEach { place(it, gapAfter = 6f) }
        rule(ACCENT_RULE)
        y += 18f
    }

    private fun section(text: String) {
        val label = layout(text.uppercase(), SECTION, CONTENT_WIDTH)
        // A heading is never left alone at the foot of a page.
        room(label.height + 60f)
        y += 8f
        place(label, gapAfter = 4f)
        rule(RULE)
        y += 10f
    }

    private fun day(block: Block.Day) {
        val heading = layout(block.text, DAY, CONTENT_WIDTH * 0.55f)
        val weather = block.weather?.let { layout(it, WEATHER, CONTENT_WIDTH * 0.45f, Layout.Alignment.ALIGN_OPPOSITE) }
        room(heading.height + 50f)
        y += 6f
        draw(weather, MARGIN + CONTENT_WIDTH * 0.55f, y + 3f)
        place(heading, gapAfter = 6f)
    }

    private fun entry(block: Block.Entry) {
        val textWidth = CONTENT_WIDTH - TIME_COLUMN
        val time = layout(block.time, TIME, TIME_COLUMN - 8f)
        val lines = listOfNotNull(
            layout(block.title, ENTRY_TITLE, textWidth),
            block.details.takeIf { it.isNotEmpty() }?.let { layout(it.joinToString("  ·  "), BODY, textWidth) },
            block.people?.let { layout("Only $it", BODY_DIM, textWidth) },
            block.reference?.let { layout("Ref. $it", REFERENCE, textWidth) },
            block.note?.let { layout(it, NOTE, textWidth) },
        )
        val height = maxOf(time.height.toFloat(), lines.sumOf { it.height + 2 }.toFloat())
        room(height + 10f)
        draw(time, MARGIN, y)
        var lineY = y
        lines.forEach { line ->
            draw(line, MARGIN + TIME_COLUMN, lineY)
            lineY += line.height + 2
        }
        y += height + 10f
    }

    private fun row(block: Block.Row) {
        val label = layout(block.label, BODY, CONTENT_WIDTH * 0.65f)
        val value = layout(block.value, VALUE, CONTENT_WIDTH * 0.35f, Layout.Alignment.ALIGN_OPPOSITE)
        val height = maxOf(label.height, value.height).toFloat()
        room(height + 6f)
        draw(label, MARGIN, y)
        draw(value, MARGIN + CONTENT_WIDTH * 0.65f, y)
        y += height + 6f
    }

    /** Start a new page unless [height] still fits on this one. */
    private fun room(height: Float) {
        if (page == null || y + height > PAGE_HEIGHT - MARGIN - FOOTER_SPACE) {
            closePage()
            number += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
            y = MARGIN
        }
    }

    private fun closePage() {
        val current = page ?: return
        val text = "$footer  ·  page $number"
        current.canvas.drawText(text, MARGIN, PAGE_HEIGHT - MARGIN / 2, FOOTER)
        document.finishPage(current)
        page = null
    }

    private fun place(layout: StaticLayout, gapAfter: Float) {
        room(layout.height.toFloat())
        draw(layout, MARGIN, y)
        y += layout.height + gapAfter
    }

    private fun draw(layout: StaticLayout?, x: Float, top: Float) {
        layout ?: return
        canvas.withTranslation(x, top) { layout.draw(this) }
    }

    private fun rule(paint: Paint) {
        canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, paint)
    }

    private fun layout(
        text: String,
        paint: TextPaint,
        width: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
        .setAlignment(alignment)
        .setLineSpacing(0f, 1.12f)
        .setIncludePad(false)
        .build()

    private companion object {
        // A4 in points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val TIME_COLUMN = 92f
        const val FOOTER_SPACE = 28f

        val INK = Color.rgb(0x22, 0x1C, 0x16)
        val DIM = Color.rgb(0x6E, 0x62, 0x55)
        val ACCENT = Color.rgb(0x9A, 0x66, 0x2E)

        val SERIF: Typeface = Typeface.SERIF
        val SANS: Typeface = Typeface.SANS_SERIF
        val MONO: Typeface = Typeface.MONOSPACE

        fun paint(size: Float, color: Int, face: Typeface, style: Int = Typeface.NORMAL) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(face, style)
            }

        val TITLE = paint(24f, INK, SERIF, Typeface.BOLD)
        val SUBTITLE = paint(12f, DIM, SERIF, Typeface.ITALIC)
        val DATES = paint(11f, ACCENT, SANS, Typeface.BOLD)
        val SECTION = paint(9f, DIM, SANS, Typeface.BOLD).apply { letterSpacing = 0.18f }
        val DAY = paint(13f, INK, SERIF, Typeface.BOLD)
        val WEATHER = paint(9f, DIM, SANS)
        val TIME = paint(10f, ACCENT, MONO, Typeface.BOLD)
        val ENTRY_TITLE = paint(11f, INK, SANS, Typeface.BOLD)
        val BODY = paint(10f, INK, SANS)
        val BODY_DIM = paint(10f, DIM, SANS)
        val REFERENCE = paint(10f, INK, MONO)
        val NOTE = paint(9.5f, DIM, SERIF, Typeface.ITALIC)
        val VALUE = paint(10f, INK, MONO, Typeface.BOLD)
        val FOOTER = paint(8f, DIM, SANS)

        val RULE = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD9, 0xCF, 0xC0); strokeWidth = 0.6f }
        val ACCENT_RULE = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; strokeWidth = 1.2f }
    }
}
