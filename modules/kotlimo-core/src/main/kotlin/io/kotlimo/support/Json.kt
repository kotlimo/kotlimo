package io.kotlimo.support

object Json {
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        .findAndRegisterModules()

    fun encode(value: Any?): String = mapper.writeValueAsString(value)

    fun decode(json: String): Any? = mapper.readValue(json, Any::class.java)

    fun <T> decode(json: String, type: Class<T>): T = mapper.readValue(json, type)

    fun encodePretty(value: Any?): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
}
