package io.kotlimo.container

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

/**
 * Laravel-style IoC container with bindings, singletons, aliases, and
 * constructor auto-wiring via Kotlin reflection.
 */
open class Container {
    private val bindings = mutableMapOf<String, Binding>()
    private val instances = mutableMapOf<String, Any>()
    private val aliases = mutableMapOf<String, String>()
    private val extenders = mutableMapOf<String, MutableList<(Any, Container) -> Any>>()
    private val resolving = ThreadLocal.withInitial { mutableSetOf<String>() }

    fun <T : Any> bind(type: KClass<T>, shared: Boolean = false, concrete: (Container) -> T) {
        bind(key(type), shared, concrete)
    }

    fun bind(abstract: String, shared: Boolean = false, concrete: (Container) -> Any) {
        bindings[abstract] = Binding(concrete, shared)
        instances.remove(abstract)
    }

    fun <T : Any> bind(type: KClass<T>, implementation: KClass<out T>, shared: Boolean = false) {
        bind(key(type), shared) { make(implementation) }
    }

    fun <T : Any> singleton(type: KClass<T>, concrete: (Container) -> T) {
        bind(type, shared = true, concrete)
    }

    fun singleton(abstract: String, concrete: (Container) -> Any) {
        bind(abstract, shared = true, concrete)
    }

    fun <T : Any> instance(type: KClass<T>, instance: T) {
        instance(key(type), instance)
    }

    fun instance(abstract: String, instance: Any) {
        instances[abstract] = instance
    }

    fun alias(abstract: String, alias: String) {
        aliases[alias] = abstract
    }

    fun bound(type: KClass<*>): Boolean = bound(key(type))

    fun bound(abstract: String): Boolean {
        val resolved = resolveAlias(abstract)
        return instances.containsKey(resolved) || bindings.containsKey(resolved)
    }

    fun <T : Any> extend(type: KClass<T>, callback: (T, Container) -> T) {
        val k = key(type)
        val list = extenders.getOrPut(k) { mutableListOf() }
        @Suppress("UNCHECKED_CAST")
        list.add { value, container -> callback(value as T, container) as Any }
        instances[k]?.let { instances[k] = callback(it as T, this) as Any }
    }

    inline fun <reified T : Any> make(): T = make(T::class)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> make(type: KClass<T>): T = make(key(type)) as T

    fun make(abstract: String): Any {
        val key = resolveAlias(abstract)
        instances[key]?.let { return it }

        val stack = resolving.get()
        if (!stack.add(key)) {
            throw BindingResolutionException("Circular dependency detected while resolving [$key]")
        }
        try {
            val resolved = resolve(key)
            val extended = applyExtenders(key, resolved)
            if (isShared(key)) {
                instances[key] = extended
            }
            return extended
        } finally {
            stack.remove(key)
        }
    }

    fun forgetInstance(type: KClass<*>) {
        instances.remove(key(type))
    }

    fun flush() {
        bindings.clear()
        instances.clear()
        aliases.clear()
        extenders.clear()
    }

    protected open fun key(type: KClass<*>): String = type.qualifiedName ?: type.simpleName ?: type.toString()

    private fun resolveAlias(abstract: String): String {
        var current = abstract
        val seen = mutableSetOf<String>()
        while (aliases.containsKey(current) && seen.add(current)) {
            current = aliases.getValue(current)
        }
        return current
    }

    private fun isShared(key: String): Boolean = bindings[key]?.shared == true || instances.containsKey(key)

    private fun resolve(key: String): Any {
        val binding = bindings[key]
        if (binding != null) {
            return binding.concrete(this)
        }
        return build(findClass(key))
    }

    private fun findClass(key: String): KClass<*> =
        try {
            Class.forName(key).kotlin
        } catch (e: ClassNotFoundException) {
            throw BindingResolutionException("Unable to resolve unbound type [$key]", e)
        }

    private fun build(type: KClass<*>): Any {
        if (type.java.isInterface || type.isAbstract) {
            throw BindingResolutionException(
                "Cannot instantiate ${if (type.java.isInterface) "interface" else "abstract class"} [${type.qualifiedName}] without a binding"
            )
        }
        val constructor = type.primaryConstructor
            ?: type.constructors.minByOrNull { it.parameters.size }
            ?: throw BindingResolutionException("No constructor available for [${type.qualifiedName}]")
        constructor.isAccessible = true
        val args = constructor.parameters.map { parameter ->
            val classifier = parameter.type.classifier
            if (classifier !is KClass<*>) {
                if (parameter.isOptional) {
                    return@map DefaultConstructorValue
                }
                throw BindingResolutionException(
                    "Cannot resolve parameter [${parameter.name}] of [${type.qualifiedName}]"
                )
            }
            try {
                make(classifier)
            } catch (e: BindingResolutionException) {
                if (parameter.type.isMarkedNullable) {
                    null
                } else if (parameter.isOptional) {
                    DefaultConstructorValue
                } else {
                    throw BindingResolutionException(
                        "Unable to resolve parameter [${parameter.name}: ${classifier.qualifiedName}] of [${type.qualifiedName}]",
                        e
                    )
                }
            }
        }
        val callArgs = constructor.parameters.zip(args)
            .filter { it.second !== DefaultConstructorValue }
            .associate { it.first to it.second }
        return constructor.callBy(callArgs)
    }

    private fun applyExtenders(key: String, value: Any): Any {
        var current = value
        extenders[key]?.forEach { extender ->
            current = extender(current, this)
        }
        return current
    }

    private object DefaultConstructorValue
}
