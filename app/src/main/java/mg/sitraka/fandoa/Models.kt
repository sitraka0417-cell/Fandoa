package mg.sitraka.fandoa

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Tranche(val amount: Double, val date: Long, val note: String = "")

data class Person(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val phone: String = "",
    val salary: Double = 0.0,
    val createdAt: Long = 0L
)

enum class Status(val label: String) {
    UNPAID("À payer"), PARTIAL("Partiel"), PAID("Payé"), LATE("En retard")
}

data class Payment(
    val id: String = "",
    val name: String = "",
    val personId: String = "",
    val category: String = "Salaire",
    val amount: Double = 0.0,
    val reason: String = "",
    val note: String = "",
    val dueDate: Long? = null,
    val reminderAt: Long? = null,
    val alarm: Boolean = false,
    val notif: Boolean = false,
    val monthly: Boolean = false,
    val spawned: Boolean = false,
    val tranches: List<Tranche> = emptyList(),
    val createdAt: Long = 0L
) {
    val paid: Double get() = tranches.sumOf { it.amount }
    val remaining: Double get() = (amount - paid).coerceAtLeast(0.0)
    val isPaid: Boolean get() = amount > 0 && paid >= amount - 0.005

    fun status(): Status {
        if (isPaid) return Status.PAID
        val d = dueDate
        if (d != null && d < startOfDay(System.currentTimeMillis())) return Status.LATE
        return if (paid > 0) Status.PARTIAL else Status.UNPAID
    }
}

fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun addMonth(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.add(Calendar.MONTH, 1)
    return c.timeInMillis
}

fun localToUtc(ms: Long): Long {
    val l = Calendar.getInstance()
    l.timeInMillis = ms
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    u.clear()
    u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    return u.timeInMillis
}

fun utcToLocal(ms: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    u.timeInMillis = ms
    val l = Calendar.getInstance()
    l.clear()
    l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    return l.timeInMillis
}

fun Double.ar(): String {
    val nf = NumberFormat.getInstance(Locale.FRANCE)
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 0
    return nf.format(this).replace('\u202f', ' ').replace('\u00a0', ' ') + " Ar"
}

fun trimNum(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

fun Long.dateFr(): String = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(this))
fun Long.dateTimeFr(): String = SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRANCE).format(Date(this))
fun Long.hourFr(): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(this))

fun monthKey(p: Payment): String =
    SimpleDateFormat("yyyy-MM", Locale.US).format(Date(p.dueDate ?: p.createdAt))

fun monthLabel(key: String): String {
    val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(key) ?: return key
    return SimpleDateFormat("MMMM yyyy", Locale.FRANCE).format(d)
        .replaceFirstChar { it.uppercase() }
}

/** Ajuste la liste des tranches pour que le total payé soit égal à [target]. */
fun adjustPaid(tr: List<Tranche>, target: Double): List<Tranche> {
    val cur = tr.sumOf { it.amount }
    if (kotlin.math.abs(target - cur) < 0.005) return tr
    if (target > cur) return tr + Tranche(target - cur, System.currentTimeMillis())
    var excess = cur - target
    val out = tr.toMutableList()
    while (excess > 0.005 && out.isNotEmpty()) {
        val last = out.removeAt(out.lastIndex)
        if (last.amount > excess + 0.005) {
            out.add(last.copy(amount = last.amount - excess))
            excess = 0.0
        } else {
            excess -= last.amount
        }
    }
    return out
}
