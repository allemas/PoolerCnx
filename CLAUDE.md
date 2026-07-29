# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

A learning project to explore how JDBC connection pools work. Not production-ready by design.

**Read `WORKING.md` before starting any session.** It defines the collaboration contract:
- Act as a thinking partner, not a code generator — no unsolicited rewrites
- Bugs are described, not auto-fixed; hypotheses are proposed, the human debugs
- Designs are debated before being coded; don't propose "the right answer" prematurely
- Challenge intuitions with reasoning; don't validate just to please
- No prescriptive roadmaps ("first do X, then Y") that replace genuine exploration
- Help review staged changes, suggest commit messages, and flag unstaged modifications before committing

## Commands

```bash
# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=PoolSpecs

# Run a single test method
mvn test -Dtest=PoolSpecs#verifyConnectionAcquiredAndRemovedFromPool

# Build without running tests
mvn compile
```

## Architecture

The pool is generic over `T extends Connection`. The core types:

- `Pool<T>` — interface: `acquire()` and `size()`
- `DefaultPool<T>` — implementation: holds a single `List<PooledEntity<T>>`, scans it on acquire (O(n)), creates new entities up to `maxSize`, eagerly pre-creates `initIdleConnexions` at startup
- `PooledEntity<T>` — wraps one connection with a `State` and a recycling `Consumer<PooledEntity<T>>`. Implements `AutoCloseable`: `close()` releases back to the pool (state `IN_USE → IDLE`); there is a separate `destroy()` for real teardown
- `State` — enum: `IDLE`, `IN_USE`, `CLOSED`
- `PoolConfig` — record: `initIdleConnexions`, `maxSize`, `acquireTimeout`; `PoolConfig.auto()` returns `(2, 4, 200)`

Entity IDs are assigned by `DefaultPool` (a per-pool counter), not a static field on `PooledEntity`, so tests are deterministic and pools are independent.

The recycling callback (`pool::recycle`) is injected at entity construction, which keeps `PooledEntity` decoupled from the `Pool` interface (ADR-005 in `DESIGN.md`).

## Tests

Tests use H2 in-memory (`jdbc:h2:mem:test;DB_CLOSE_DELAY=-1`) as the real `Connection` implementation. `StubCnx` is a minimal stub for unit-level pool tests that don't need SQL. `PoolConfigurationTools` is a shared test helper that provides an H2 `Supplier<Connection>` and a thread factory for concurrency tests. Test classes extend it or instantiate it directly.

Acquire a connection with try-with-resources on `PooledEntity`, never on the raw `Connection` — closing the raw connection destroys it permanently.

## Commit style

Conventional commits: `type: short description` (e.g. `feat:`, `test:`, `refactor:`, `docs:`). The LLM helps review staged changes, suggest messages, and flag unstaged modifications before committing.
