# Kotlimo

The Kotlin Framework for Web Artisans.

Kotlimo is an open-source, batteries-included web framework inspired by [Laravel](https://laravel.com/). It gives Kotlin the same developer experience Laravel gave PHP: an IoC container, expressive routing, Blade-like views, an Eloquent-style ORM, validation, migrations, and an Artisan-style CLI.

Site: [kotlimo.github.io](https://kotlimo.github.io) · Source: [github.com/kotlimo/kotlimo](https://github.com/kotlimo/kotlimo)

```bash
git clone https://github.com/kotlimo/kotlimo.git
cd kotlimo
./gradlew :website:run
```

Then open [http://127.0.0.1:8000](http://127.0.0.1:8000). The documentation site is a Kotlimo application.

GitHub Pages cannot run the JVM app. Publishable static HTML lives in `docs/` and is regenerated with:

```bash
./gradlew :website:run --args='site:export'
```

## Hello world

```kotlin
val app = Application.create(basePath)
    .withDefaultProviders()
    .boot()

Route.get("/users/{id}") { request ->
    val user = User.findOrFail(request.route("id")!!)
    view("users.show", mapOf("user" to user))
}

app.run(arrayOf("serve"))
```

## Repository layout

The framework is a Gradle multi-module project, mirroring Laravel's Illuminate packages:

| Module | Responsibility |
| --- | --- |
| `kotlimo-core` | Support helpers, container, config, env, pipeline, events, cache |
| `kotlimo-http` | Request/Response, routing, middleware, kernel, validation, server |
| `kotlimo-database` | Query builder, models, schema, migrations |
| `kotlimo-view` | Kote templates (`{{ }}`, `@if`, `@foreach`, `@extends`) |
| `kotlimo-foundation` | Application, providers, facades, Craft CLI |
| `kotlimo-testing` | HTTP assertions (`get("/").assertSee(...)`) |
| `website` | Documentation site (also used to export `docs/` for GitHub Pages) |

## Tests

```bash
./gradlew test
```

## Craft CLI

There is no published `kotlimo` binary. Invoke Craft through Gradle:

```bash
./gradlew :website:run --args='list'
./gradlew :website:run --args='route:list'
./gradlew :website:run --args='make:controller PostsController'
```

## Requirements

- JDK 21+
- The Gradle wrapper (`./gradlew`)

## License

[MIT](LICENSE)
