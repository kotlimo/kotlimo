package io.kotlimo.logging

import org.slf4j.LoggerFactory

class Logger(name: String = "kotlimo") {
    private val logger = LoggerFactory.getLogger(name)

    fun debug(message: String) = logger.debug(message)
    fun info(message: String) = logger.info(message)
    fun warning(message: String) = logger.warn(message)
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) logger.error(message, throwable) else logger.error(message)
    }
}
