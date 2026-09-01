package io.kotlimo.http

import io.kotlimo.validation.Validator

fun Request.validate(rules: Map<String, String>): Map<String, Any?> =
    Validator.make(all(), rules).validated()
