package sk.fold.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Udalosť z kalendára (jeden výskyt). */
data class CalEvent(
    val id: Long, val title: String, val begin: Long, val end: Long,
    val allDay: Boolean, val location: String, val color: Int,
) {
    /** Deň udalosti – celodenné udalosti sú v UTC, ostatné v miestnom čase. */
    val day: LocalDate get() =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
}

object Agenda {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Udalosti od teraz na [days] dní dopredu, zoradené podľa začiatku (len viditeľné kalendáre). */
    fun load(ctx: Context, days: Int = 60): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now + days * 24L * 3600 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 24L * 3600 * 1000)
            ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.VISIBLE,
        )
        val out = ArrayList<CalEvent>()
        runCatching {
            ctx.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(7) == 0) continue
                    val allDay = c.getInt(4) == 1
                    val b = c.getLong(2)
                    var e = c.getLong(3)
                    if (e <= b) e = b + if (allDay) 24L * 3600 * 1000 else 3600 * 1000
                    // skončené udalosti preskočíme (celodenné platia do konca svojho dňa)
                    val endLocal = if (allDay) Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else e
                    if (endLocal <= now) continue
                    out += CalEvent(c.getLong(0), c.getString(1)?.ifBlank { null } ?: "(bez názvu)", b, e, allDay,
                        c.getString(5) ?: "", c.getInt(6))
                }
            }
        }
        return out.sortedWith(compareBy({ it.day }, { !it.allDay }, { it.begin }))
    }

    fun relative(day: LocalDate): String {
        val n = ChronoUnit.DAYS.between(LocalDate.now(), day)
        return when {
            n <= 0L -> "dnes"
            n == 1L -> "zajtra"
            n in 2..4 -> "o $n dni"
            else -> "o $n dní"
        }
    }

    fun open(ctx: Context, e: CalEvent) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun newEvent(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
