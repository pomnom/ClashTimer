package id.clashtimer.core

/**
 * Parser JSON kecil tanpa dependensi, supaya logika inti bisa dipakai
 * (dan diuji) di luar Android. Hasil: Map<String, Any?>, List<Any?>,
 * String, Long, Double, Boolean, atau null.
 */
object Json {
    fun parse(text: String): Any? {
        val p = P(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw IllegalArgumentException("Sisa teks setelah JSON di posisi ${p.i}")
        return v
    }

    private class P(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw IllegalArgumentException("JSON terpotong")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw IllegalArgumentException("Karakter tak terduga '$c' di posisi $i")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw IllegalArgumentException("Nilai tak dikenal di posisi $i")
            i += word.length
            return v
        }

        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw IllegalArgumentException("Kunci objek diharapkan di posisi $i")
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') throw IllegalArgumentException("':' diharapkan di posisi $i")
                i++
                ws()
                m[k] = value()
                ws()
                if (i >= s.length) throw IllegalArgumentException("JSON terpotong")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("',' atau '}' diharapkan di posisi $i")
                }
            }
        }

        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                ws()
                l.add(value())
                ws()
                if (i >= s.length) throw IllegalArgumentException("JSON terpotong")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw IllegalArgumentException("',' atau ']' diharapkan di posisi $i")
                }
            }
        }

        fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("Teks terpotong")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw IllegalArgumentException("Teks terpotong")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw IllegalArgumentException("Escape terpotong")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Escape tak dikenal \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var isDouble = false
            if (i < s.length && s[i] == '.') {
                isDouble = true
                i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val t = s.substring(start, i)
            return if (isDouble) t.toDouble() else (t.toLongOrNull() ?: t.toDouble())
        }
    }
}

// ---- Pembantu akses yang toleran terhadap tipe ----

@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.arr(): List<Any?>? = this as? List<Any?>

fun Any?.long(default: Long = 0L): Long = when (this) {
    is Long -> this
    is Int -> this.toLong()
    is Double -> this.toLong()
    is String -> this.toLongOrNull() ?: default
    is Boolean -> if (this) 1L else 0L
    else -> default
}

fun Any?.int(default: Int = 0): Int = long(default.toLong()).toInt()

fun Any?.str(default: String = ""): String = when (this) {
    null -> default
    is String -> this
    else -> this.toString()
}

fun Any?.bool(): Boolean = when (this) {
    is Boolean -> this
    is Long -> this != 0L
    is Int -> this != 0
    is String -> this == "1" || this.equals("true", true)
    else -> false
}
