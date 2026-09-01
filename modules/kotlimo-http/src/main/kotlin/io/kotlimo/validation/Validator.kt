package io.kotlimo.validation

class ValidationException(
    val errors: Map<String, List<String>>,
    message: String = "The given data was invalid."
) : RuntimeException(message)

class Validator(
    private val data: Map<String, Any?>,
    private val rules: Map<String, List<String>>,
    private val customMessages: Map<String, String> = emptyMap()
) {
    private val failed = mutableMapOf<String, MutableList<String>>()

    fun passes(): Boolean {
        failed.clear()
        rules.forEach { (attribute, attributeRules) ->
            attributeRules.forEach { rule -> applyRule(attribute, rule) }
        }
        return failed.isEmpty()
    }

    fun fails(): Boolean = !passes()

    fun errors(): Map<String, List<String>> {
        if (failed.isEmpty()) passes()
        return failed.mapValues { it.value.toList() }
    }

    fun validated(): Map<String, Any?> {
        if (fails()) throw ValidationException(errors())
        return data.filterKeys { it in rules.keys }
    }

    private fun applyRule(attribute: String, rule: String) {
        val name = rule.substringBefore(':')
        val parameters = if (rule.contains(':')) rule.substringAfter(':').split(',') else emptyList()
        val value = data[attribute]
        val ok = when (name) {
            "required" -> !isEmpty(value)
            "nullable" -> true
            "string" -> value == null || value is String
            "integer", "int" -> value == null || value is Int || value is Long || (value is String && value.toIntOrNull() != null)
            "numeric" -> value == null || value is Number || (value is String && value.toDoubleOrNull() != null)
            "boolean" -> value == null || value is Boolean || (value is String && value.lowercase() in setOf("1", "0", "true", "false", "yes", "no"))
            "email" -> value == null || (value is String && EMAIL.matches(value))
            "min" -> checkMin(value, parameters.first().toInt())
            "max" -> checkMax(value, parameters.first().toInt())
            "in" -> value == null || value.toString() in parameters
            "confirmed" -> value == data["${attribute}_confirmation"]
            "same" -> value == data[parameters.first()]
            "url" -> value == null || (value is String && (value.startsWith("http://") || value.startsWith("https://")))
            "regex" -> value == null || (value is String && Regex(parameters.joinToString(":")).containsMatchIn(value))
            "array" -> value == null || value is Collection<*> || value is Array<*>
            else -> true
        }
        if (!ok) {
            failed.getOrPut(attribute) { mutableListOf() }.add(messageFor(attribute, name, parameters))
        }
    }

    private fun isEmpty(value: Any?): Boolean = when (value) {
        null -> true
        is String -> value.isBlank()
        is Collection<*> -> value.isEmpty()
        is Map<*, *> -> value.isEmpty()
        else -> false
    }

    private fun checkMin(value: Any?, min: Int): Boolean = when (value) {
        null -> true
        is String -> value.length >= min
        is Number -> value.toDouble() >= min
        is Collection<*> -> value.size >= min
        else -> true
    }

    private fun checkMax(value: Any?, max: Int): Boolean = when (value) {
        null -> true
        is String -> value.length <= max
        is Number -> value.toDouble() <= max
        is Collection<*> -> value.size <= max
        else -> true
    }

    private fun messageFor(attribute: String, rule: String, parameters: List<String>): String {
        customMessages["$attribute.$rule"]?.let { return it }
        val field = attribute.replace('_', ' ')
        return when (rule) {
            "required" -> "The $field field is required."
            "email" -> "The $field must be a valid email address."
            "min" -> "The $field must be at least ${parameters.first()}."
            "max" -> "The $field may not be greater than ${parameters.first()}."
            "confirmed" -> "The $field confirmation does not match."
            "in" -> "The selected $field is invalid."
            "integer", "int" -> "The $field must be an integer."
            "numeric" -> "The $field must be a number."
            "string" -> "The $field must be a string."
            "url" -> "The $field must be a valid URL."
            else -> "The $field is invalid."
        }
    }

    companion object {
        private val EMAIL = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun make(
            data: Map<String, Any?>,
            rules: Map<String, String>,
            messages: Map<String, String> = emptyMap()
        ): Validator = Validator(
            data,
            rules.mapValues { parseRules(it.value) },
            messages
        )

        fun parseRules(rules: String): List<String> =
            rules.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

fun validate(data: Map<String, Any?>, rules: Map<String, String>): Map<String, Any?> =
    Validator.make(data, rules).validated()
