package com.bbplayer.android.data

/**
 * 语义版本比较工具（纯函数，可单测）。
 *
 * 支持 `1.2.3` / `v0.1.0` / `V1.2` / `1.2.3-rc.1` / `1.2.3+build`；
 * 预发布版本（`-rc.1` 等）低于同号正式版，符合 SemVer 语义。
 */
internal object AppVersion {

    private val CORE_SEGMENT = Regex("""^\d+$""")

    /** 去掉前缀 v/V 与首尾空白；保留预发布/构建元数据。 */
    fun normalize(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("v", ignoreCase = true)) text = text.substring(1)
        return text.trim()
    }

    /** 返回 -1 / 0 / 1；无法解析时按相等处理（返回 0，避免误报更新）。 */
    fun compare(left: String, right: String): Int {
        val a = parse(left) ?: return 0
        val b = parse(right) ?: return 0
        val coreSize = maxOf(a.core.size, b.core.size)
        for (i in 0 until coreSize) {
            val x = a.core.getOrElse(i) { 0 }
            val y = b.core.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        if (a.pre.isEmpty() && b.pre.isEmpty()) return 0
        if (a.pre.isEmpty()) return 1
        if (b.pre.isEmpty()) return -1
        val preSize = maxOf(a.pre.size, b.pre.size)
        for (i in 0 until preSize) {
            val x = a.pre.getOrNull(i) ?: return -1
            val y = b.pre.getOrNull(i) ?: return 1
            val cmp = compareIdentifier(x, y)
            if (cmp != 0) return cmp
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /** 展示用版本号：去掉 v 前缀。 */
    fun display(raw: String): String = normalize(raw)

    // ------------------------------------------------------------ 内部实现

    private data class Parsed(val core: List<Int>, val pre: List<String>)

    private fun parse(raw: String): Parsed? {
        val text = normalize(raw).substringBefore('+').trim()
        if (text.isEmpty()) return null
        val corePart = text.substringBefore('-')
        val prePart = if (text.contains('-')) text.substringAfter('-') else ""
        val core = corePart.split('.').map { segment ->
            val digits = segment.trim()
            if (!CORE_SEGMENT.matches(digits)) return null
            digits.toIntOrNull() ?: return null
        }
        if (core.isEmpty()) return null
        val pre = prePart.split('.', '-')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Parsed(core = core, pre = pre)
    }

    /** SemVer：数字标识符按数值比较且在字母标识符之前；字母按字典序。 */
    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumber = left.toLongOrNull()
        val rightNumber = right.toLongOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }
}
