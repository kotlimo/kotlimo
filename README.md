# Kotlimo

The Kotlin Framework for Web Artisans.

Kotlimo is an open-source, batteries-included web framework inspired by [Laravel](https://laravel.com/). It gives Kotlin the same developer experience Laravel gave PHP: an IoC container, expressive routing, Blade-like views, an Eloquent-style ORM, sessions, CSRF, auth, validation, migrations, queues, mail, and an Artisan-style CLI.

Site: [kotlimo.github.io](https://kotlimo.github.io) · Source: [github.com/kotlimo/kotlimo](https://github.com/kotlimo/kotlimo)

```bash
git clone https://github.com/kotlimo/kotlimo.git
cd kotlimo
./gradlew test
./gradlew :website:run
```

Then open [http://127.0.0.1:8000](http://127.0.0.1:8000). The documentation site is a Kotlimo application.

## Create an app

```bash
./gradlew :kotlimo-console:run --args='new blog'
# equivalent:
./kotlimo new blog
cd blog
./gradlew run
```

The generator writes Gradle files that depend on `io.kotlimo:kotlimo-foundation:0.1.0`. Next to this source tree it also adds a composite `includeBuild`, so you do not need published JARs to get started.

## Depend on Kotlimo

```kotlin
dependencies {
    implementation("io.kotlimo:kotlimo-foundation:0.1.0")
}
```

Composite build (from another Gradle project):

```kotlin
// settings.gradle.kts
includeBuild("/path/to/kotlimo")
```

GitHub Packages (`./gradlew publish` from this repo, with `GITHUB_ACTOR` / `GITHUB_TOKEN`):

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/kotlimo/kotlimo")
    credentials {
        username = providers.environmentVariable("GITHUB_ACTOR").get()
        password = providers.environmentVariable("GITHUB_TOKEN").get()
    }
}
```

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
| `kotlimo-core` | Support helpers, container, config, env, pipeline, events, cache, hash, filesystem, queue, mail, scheduler |
| `kotlimo-http` | Request/Response, routing, middleware, kernel, validation, sessions, CSRF, auth |
| `kotlimo-database` | Query builder, models, schema, migrations (H2, SQLite, PostgreSQL, MySQL drivers) |
| `kotlimo-view` | Kote templates (`{{ }}`, `@if`, `@foreach`, `@extends`) |
| `kotlimo-foundation` | Application, providers, facades, Craft CLI |
| `kotlimo-testing` | HTTP assertions (`get("/").assertSee(...)`) |
| `kotlimo-console` | `new` app generator |
| `website` | Documentation site (also used to export `docs/` for GitHub Pages) |

## Tests

```bash
./gradlew test
```

## Craft CLI

From this repository, scaffold with the console module. From an application directory, invoke Craft through that app's `run` task:

```bash
./gradlew :kotlimo-console:run --args='new blog'
./gradlew :website:run --args='list'
./gradlew :website:run --args='route:list'
```

## Requirements

- JDK 21+
- The Gradle wrapper (`./gradlew`)

## License

[MIT](LICENSE)
