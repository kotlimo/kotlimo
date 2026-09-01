package io.kotlimo.support

object Inflector {
    private val uncountable = setOf(
        "equipment", "information", "rice", "money", "species", "series", "fish", "sheep", "data"
    )

    private val irregular = mapOf(
        "child" to "children",
        "person" to "people",
        "man" to "men",
        "woman" to "women",
        "mouse" to "mice",
        "goose" to "geese",
        "ox" to "oxen",
        "index" to "indices",
        "matrix" to "matrices"
    )

    private val irregularSingular = irregular.entries.associate { it.value to it.key }

    fun pluralize(word: String): String {
        val lower = word.lowercase()
        if (lower in uncountable) return word
        irregular[lower]?.let { replacement -> return applyCase(word, replacement) }
        return when {
            lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") ||
                lower.endsWith("ch") || lower.endsWith("sh") -> word + "es"
            lower.endsWith("y") && lower.length > 1 && lower[lower.length - 2] !in "aeiou" ->
                word.dropLast(1) + "ies"
            lower.endsWith("f") -> word.dropLast(1) + "ves"
            lower.endsWith("fe") -> word.dropLast(2) + "ves"
            else -> word + "s"
        }
    }

    fun singularize(word: String): String {
        val lower = word.lowercase()
        if (lower in uncountable) return word
        irregularSingular[lower]?.let { replacement -> return applyCase(word, replacement) }
        return when {
            lower.endsWith("ies") && lower.length > 3 -> word.dropLast(3) + "y"
            lower.endsWith("ves") -> word.dropLast(3) + "f"
            lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes") ||
                lower.endsWith("ches") || lower.endsWith("shes") -> word.dropLast(2)
            lower.endsWith("s") && !lower.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    }

    fun tableize(className: String): String = pluralize(Str.snake(className))

    fun classify(table: String): String = Str.studly(singularize(table))

    fun foreignKey(className: String): String = Str.snake(className) + "_id"

    private fun applyCase(original: String, replacement: String): String =
        if (original.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceFirstChar { it.uppercase() }
        } else {
            replacement
        }
}
