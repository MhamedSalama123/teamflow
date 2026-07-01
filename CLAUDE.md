# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

TeamFlow is a monorepo with two independent applications:

- `backend/` — Spring Boot 4.1 REST/WebSocket service (Java 21, Gradle)
- `frontend/` — Angular 21 single-page app (npm, Tailwind CSS v4, Vitest)

Both are currently scaffolds (no domain code yet): the backend is bare `BackendApplication`, the frontend is the default `App` shell with an empty route table. Treat the dependency set already wired into each as the intended architecture (see below) when adding features.

## Backend (`backend/`)

Commands run from `backend/`. Use `./gradlew` (Bash) or `gradlew.bat` (PowerShell).

- Build: `./gradlew build`
- Run (needs an external Postgres on the configured datasource): `./gradlew bootRun`
- **Run locally with a throwaway Postgres**: `./gradlew bootTestRun` — boots `TestBackendApplication`, which starts a Postgres Testcontainer and wires it in via `@ServiceConnection`. This is the normal way to run the app locally; Docker must be running.
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests "com.teamflow.backend.BackendApplicationTests"`
- Single test method: `./gradlew test --tests "com.teamflow.backend.BackendApplicationTests.contextLoads"`

Architecture notes for new work:
- **Persistence**: Spring Data JPA + PostgreSQL. Schema changes go through **Flyway** migrations (`src/main/resources/db/migration/V*__*.sql`) — do not rely on Hibernate auto-DDL.
- **Tests use Testcontainers Postgres**, not H2 — every integration test runs against real Postgres. Docker is required to run the test suite.
- **Security**: Spring Security with OAuth2 client is on the classpath; expect auth to be required by default once a `SecurityFilterChain` is added.
- **Realtime**: STOMP-over-WebSocket is wired up (`com.teamflow.backend.realtime`). Clients connect to `/ws` (authenticated by a JWT in the STOMP `CONNECT` frame's `Authorization` header — the HTTP handshake is permitted in `SecurityConfig`). Two streams: `/topic/projects/{projectId}` for live task events (`TaskEvent` via `TaskEventPublisher`) and the per-user `/user/queue/notifications` for live in-app notifications (`NotificationPublisher`, sent by `NotificationService`). The simple broker serves `/topic` and `/queue`; `StompAuthChannelInterceptor` enforces workspace membership on project `SUBSCRIBE`s and allows a session's own `/user/**` queue. Both publishers send after commit.
- **Lombok** is enabled (annotation processor) — annotations like `@Getter`/`@RequiredArgsConstructor` are available.
- Config lives in `src/main/resources/application.properties` (minimal today). DevTools is on for hot reload during `bootRun`.
- Docker image: multi-stage `Dockerfile` builds with `gradle bootJar -x test` and runs on `eclipse-temurin:21-jre-alpine` as a non-root user, exposing 8080.

## Frontend (`frontend/`)

Commands run from `frontend/`.

- Install: `npm install`
- Dev server: `npm start` (`ng serve`, defaults to `http://localhost:4200`)
- Build: `npm run build` (production by default; dev build via `npm run watch`)
- Tests: `npm test` (`ng test` → Vitest)

Architecture notes for new work:
- **Angular 21, standalone, zoneless-style** with **signals** (see `App.title` using `signal()`); use signals and standalone components, not NgModules.
- App bootstraps from `src/main.ts` → `appConfig` (`src/app/app.config.ts`); routes are registered in `src/app/app.routes.ts` (currently empty).
- **Styling is Tailwind CSS v4** via the PostCSS plugin (`.postcssrc.json`), imported through `src/styles.css`. There is no `tailwind.config.js`.
- Formatting: Prettier (`.prettierrc`), configured with the Angular HTML parser for `*.html`.

## Conventions

- Backend package root is `com.teamflow.backend`; the Gradle project name is `backend`.
- No root-level build orchestration — build/test each app from its own directory.