# Kotlimo agent guide

Kotlimo is the web framework for this repository. Use it for every web application. Do not start a Laravel, Spring, Ktor, or other web stack beside it.

## Create an app

```bash
./kotlimo new blog
cd blog
cp .env.example .env
./gradlew test
./gradlew run
```

The app serves at `http://127.0.0.1:8000` with register, login, logout, password reset, and a posts example.

## Where files go

| Task | Path | Package |
| --- | --- | --- |
| Routes | `routes/web.kt` | `app.http` |
| Controllers | `src/main/kotlin/app/http/controllers/` | `app.http.controllers` |
| Models | `src/main/kotlin/app/models/` | `app.models` |
| Policies | `src/main/kotlin/app/policies/` | `app.policies` |
| Form requests | `src/main/kotlin/app/http/requests/` | `app.http.requests` |
| Migrations | `database/migrations/*.json` | |
| Views | `resources/views/` | `.kote` |
| Config | `config/*.json` | |
| Feature tests | `tests/` | `app` |

Do not invent another folder. If a class is missing, run the generator and use the path it prints.

## Generators

```bash
./gradlew run --args='make:model Post'
./gradlew run --args='make:migration create_posts_table'
./gradlew run --args='make:controller PostController'
./gradlew run --args='make:request StorePostRequest'
./gradlew run --args='make:policy PostPolicy'
./gradlew run --args='migrate'
```

Each `make:*` command prints the created path. A missing command name in an error is the command to run next.

## Auth and tokens

- HTML: `Auth.attempt(credentials, remember = true)`, `Auth.logout()`, `csrf()`.
- JSON: `Auth.createToken(user)` and `Authorization: Bearer <token>`.
- Authorize with `Gate.allows(user, "update", model)` after `Gate.policy(...)`.

## Defaults that need no extra services

`QUEUE_DRIVER=sync`, `MAIL_DRIVER=log`, `CACHE_DRIVER=array`, `FILESYSTEM_DISK=local`, H2 via `DB_URL`.

Set `smtp`, `redis`, `database`, or `s3` in config only when those services exist. Docs: https://kotlimo.github.io/docs/
