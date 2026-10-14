package io.kotlimo.auth

import io.kotlimo.http.HttpException
import kotlin.reflect.KClass

fun interface Policy {
    fun allows(user: Authenticatable?, ability: String, model: Any?): Boolean
}

class Gate {
    private val policies = mutableMapOf<KClass<*>, Policy>()
    private val before = mutableListOf<(Authenticatable?, String, Any?) -> Boolean?>()

    fun policy(type: KClass<*>, policy: Policy) {
        policies[type] = policy
    }

    fun before(callback: (Authenticatable?, String, Any?) -> Boolean?) {
        before += callback
    }

    fun allows(user: Authenticatable?, ability: String, model: Any? = null): Boolean {
        before.forEach { callback ->
            callback(user, ability, model)?.let { return it }
        }
        if (model == null) return false
        val policy = policies[model::class] ?: return false
        return policy.allows(user, ability, model)
    }

    fun authorize(user: Authenticatable?, ability: String, model: Any? = null) {
        if (!allows(user, ability, model)) {
            throw HttpException(403, "This action is unauthorized. Add Gate.policy(${model?.let { it::class.simpleName } ?: "Model"}::class) { ... }.")
        }
    }
}
