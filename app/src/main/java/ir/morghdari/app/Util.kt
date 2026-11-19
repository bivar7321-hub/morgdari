package ir.morghdari.app

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Util {
    const val ACTION_UPDATE = "ir.morghdari.app.UPDATE"
    fun last10(s: String): String = s.filter { it.isDigit() }.takeLast(10)
    fun now(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    // فرمان‌های در انتظار تأیید: هر خط «زمان|فرمان»
    fun addPending(p: SharedPreferences, cmd: String) {
        val list = pending(p).toMutableList()
        list.add(System.currentTimeMillis().toString() + "|" + cmd.trim().uppercase())
        p.edit().putString("pending", list.takeLast(10).joinToString("\n")).apply()
    }
    fun pending(p: SharedPreferences): List<String> {
        val t = System.currentTimeMillis() - 3 * 60 * 1000
        return (p.getString("pending", "") ?: "").split("\n").filter { it.contains("|") && (it.substringBefore("|").toLongOrNull() ?: 0L) > t }
    }
    fun isPending(p: SharedPreferences, relay: Int): Boolean = pending(p).any {
        val c = it.substringAfter("|")
        c == "R${relay}ON" || c.startsWith("R${relay}ON ") || c == "R${relay}OFF" || c == "AUTO$relay"
    }
    // وقتی دستگاه جواب داد، فرمان‌های در انتظار روی وضعیت ذخیره‌شده اعمال می‌شوند
    fun confirmPending(p: SharedPreferences) {
        val list = pending(p)
        p.edit().remove("pending").apply()
        for (l in list) applyCmd(p, l.substringAfter("|"))
        if (list.isNotEmpty()) p.edit().putString("last_ok", System.currentTimeMillis().toString() + "|" + describeAll(list)).apply()
    }
    // دستور رد شد (Invalid یا خطا)
    fun rejectPending(p: SharedPreferences) {
        val list = pending(p)
        p.edit().remove("pending").apply()
        if (list.isNotEmpty()) p.edit().putString("last_bad", System.currentTimeMillis().toString() + "|" + describeAll(list)).apply()
    }
    fun describeAll(list: List<String>): String = list.joinToString("، ") { describe(it.substringAfter("|")) }
    fun pendingText(p: SharedPreferences): String = describeAll(pending(p))
    // پیام اخیر (موفق یا ناموفق) فقط تا ۳ دقیقه نشان داده می‌شود
    fun recent(p: SharedPreferences, key: String): String? {
        val v = p.getString(key, null) ?: return null
        val t = v.substringBefore("|").toLongOrNull() ?: return null
        return if (System.currentTimeMillis() - t < 3 * 60 * 1000) v.substringAfter("|") else null
    }
    private fun relayName(n: Int): String = when (n) {
        1 -> "هیتر"; 2 -> "درب"; 3 -> "فن"; 4 -> "لامپ اصلی"; 5 -> "بستن درب"; else -> "کانال $n"
    }
    fun describe(cmd: String): String {
        val c = cmd.trim().uppercase()
        Regex("^R([1-8])(ON|OFF)(?: (\\d+))?$").find(c)?.let {
            val n = it.groupValues[1].toInt()
            val on = it.groupValues[2] == "ON"
            val t = it.groupValues[3]
            return relayName(n) + (if (on) " روشن" else " خاموش") + (if (on && t.isNotEmpty()) " ($t دقیقه)" else "")
        }
        Regex("^AUTO([1-8])$").find(c)?.let { return relayName(it.groupValues[1].toInt()) + " خودکار" }
        return when {
            c == "DOOROPEN" -> "باز کردن درب"
            c == "DOORCLOSE" -> "بستن درب"
            c == "LAMP ON" -> "لامپ LED روشن"
            c == "LAMP OFF" -> "لامپ LED خاموش"
            c == "LAMP AUTO" -> "لامپ خودکار"
            c.startsWith("LAMP ") -> "لامپ LED " + c.substring(5) + "٪"
            c.startsWith("TIME") -> "تنظیم ساعت"
            c == "STAT" -> "درخواست وضعیت"
            c.startsWith("SET ") -> "تنظیمات"
            else -> c
        }
    }
    fun applyCmd(p: SharedPreferences, cmd: String) {
        val st = p.getString("status", null) ?: return
        val c = cmd.trim().uppercase()
        val parts = st.split(" ").toMutableList()
        fun put(key: String, f: (String) -> String) {
            val i = parts.indexOfFirst { it.startsWith("$key:") }
            if (i >= 0) parts[i] = "$key:" + f(parts[i].substringAfter(":"))
        }
        val relay = Regex("^R([1-8])(ON|OFF)( \\d{1,4})?$").find(c)
        val auto = Regex("^AUTO([1-8])$").find(c)
        when {
            relay != null -> put("R" + relay.groupValues[1]) { "M" + (if (relay.groupValues[2] == "ON") "1" else "0") }
            auto != null -> put("R" + auto.groupValues[1]) { "A" + it.drop(1) }
            c == "DOOROPEN" -> put("R2") { "M0" }
            c == "DOORCLOSE" -> put("R2") { "M1" }
            c == "LAMP ON" -> put("LP") { "M100" }
            c == "LAMP OFF" -> put("LP") { "M0" }
            c == "LAMP AUTO" -> put("LP") { "A" + it.drop(1) }
            Regex("^LAMP \\d{1,3}$").matches(c) -> put("LP") { "M" + c.substring(5) }
            else -> return
        }
        p.edit().putString("status", parts.joinToString(" ")).apply()
    }
}
