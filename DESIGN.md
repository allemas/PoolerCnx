
## Overview

The pool wraps connection-like resources (typed `T extends Connection`) and
manages their lifecycle through a small state machine. Connections are
created lazily up to `maxSize`, with `minIdle` connections eagerly created
at startup. A `PoolEntity<T>` wraps each connection together with its state
and a recycling callback.

Inspired by HikariCP but deliberately simpler:

- One single list of entries (no separate idle/active queues yet)
- Synchronous release path
- No proxy on the underlying connection — users interact with `PoolEntity`
  directly via try-with-resources

---

## Decisions

### ADR-001 — Wrapper around the raw connection

**Context.** The pool needs to track per-connection state (idle, in-use,
closed) and lifecycle metadata. The raw `Connection` interface from JDBC
doesn't expose any of that.

**Options.**
- A. Track state in a parallel map (`Map<Connection, State>`)
- B. Wrap each connection in a dedicated `PoolEntity<T>` class

**Decision.** B. The pool stores a list of `PoolEntity<T>`. Each entity
holds the connection, its state, an identifier, and a callback for recycling.

**Consequences.** Users of the pool deal with `PoolEntity`, not raw
connections. Slightly less ergonomic than Hikari's transparent proxy, but
much simpler to reason about.

---

### ADR-002 — `close()` releases, `destroy()` actually closes

**Context.** A `close()` method is the natural fit for `AutoCloseable` and
try-with-resources. But "close" is ambiguous: does it return the entity to
the pool, or does it tear down the underlying resource?

**Options.**
- A. `close()` performs a real teardown (matches the JDBC convention)
- B. `close()` releases to the pool; a separate `destroy()` performs teardown

**Decision.** B. `close()` transitions the state from `IN_USE` back to
`IDLE` and notifies the pool. `destroy()` is the only method that actually
closes the underlying connection.

**Consequences.** Users can rely on try-with-resources for safe release.
Real teardown is reserved for the pool itself (eviction, shutdown). The
naming is slightly less standard than JDBC's, but the semantics are explicit.

---

### ADR-003 — State enum, no `UNKNOWN`

**Context.** Initial design used two booleans (`active`, `closed`). This
allowed combinations that didn't represent real states.

**Options.**
- A. Keep booleans
- B. Introduce a `PoolState` enum with `IDLE`, `IN_USE`, `CLOSED`,
  `UNKNOWN`
- C. Same enum, but drop `UNKNOWN`

**Decision.** C. An `UNKNOWN` state is a modeling smell — it means a case
hasn't been thought through. The three states are sufficient. Additional
states (e.g. `BROKEN`) will be added later if a concrete need appears.

**Consequences.** State transitions become explicit and verifiable. Each
transition method validates the current state and throws on invalid moves.

---

### ADR-004 — Entity ID lives on the pool instance, not as a static counter

**Context.** Each `PoolEntity` should have a unique identifier for logging,
metrics, and leak detection.

**Options.**
- A. `static final AtomicInteger ID_GENERATOR` in `PoolEntity`
- B. `AtomicInteger entityIdGenerator` field on `DefaultPool`, passed at
  entity construction
- C. UUID per entity

**Decision.** B. Each pool owns its own counter. IDs are local and
sequential within a pool, which makes logs readable and tests
deterministic.

**Consequences.** Multiple pools in the same JVM each restart from 1.
No global coupling between pool instances. Tests don't depend on
execution order.

**Why not A.** Static state mutates across tests and couples unrelated
pool instances together. Bad smell.

**Why not C.** UUIDs solve a problem we don't have (cross-process
uniqueness). They hurt log readability without compensating benefit.

---

### ADR-005 — Recycling callback as `Consumer<PoolEntity<T>>`

**Context.** When `close()` is called on an entity, it must signal back
to the pool to update bookkeeping. The entity needs to know "how to
return home".

**Options.**
- A. Pass the `Pool<T>` (or `DefaultPool<T>`) instance to the entity
- B. Pass a `Consumer<PoolEntity<T>>` callback at entity construction
- C. Use a `Function<PoolEntity<T>, X>` for symmetry with FP idioms

**Decision.** B. The entity is constructed with a recycling callback —
typically `pool::giveBack` — and invokes it on close.

**Consequences.**
- The entity is decoupled from the `Pool` interface entirely; it only
  knows about an effect to invoke.
- Trivial to test in isolation: pass any `Consumer` that records the
  recycled entity.
- Composable via `Consumer.andThen` for adding logging or metrics
  without modifying the entity.
- No circular type reference between `Pool` and `PoolEntity`.

**Why not C (Function).** Recycling has no meaningful return value.
`Function<X, Void>` would force a parasitic `null` return everywhere.
The `Consumer` type expresses intent: "side effect with no result",
which mirrors `IO[Unit]` in Scala.

---

### ADR-006 — Single list of entities, scan on acquire

**Context.** The pool needs to find idle entries on acquire and track
all entries for max-size enforcement.

**Options.**
- A. Single `List<PoolEntity<T>>` with state-based filtering
- B. Two structures: a `BlockingQueue` of idle entries plus a counter
  for total

**Decision.** A for now. `acquire()` streams the list, filters by
`isIdle()`, takes the first match. If none found and `size < maxSize`,
a new entity is created.

**Consequences.**
- Simple to read and reason about
- O(n) on acquire, acceptable for small pools

---

## Overview

The pool wraps connection-like resources (typed `T extends Connection`) and
manages their lifecycle through a small state machine. Connections are
created lazily up to `maxSize`, with `minIdle` connections eagerly created
at startup. A `PoolEntity<T>` wraps each connection together with its state
and a recycling callback.

Inspired by HikariCP but deliberately simpler:

- One single list of entries (no separate idle/active queues yet)
- Synchronous release path
- No proxy on the underlying connection — users interact with `PoolEntity`
  directly via try-with-resources

---

## Decisions

### ADR-001 — Wrapper around the raw connection

**Context.** The pool needs to track per-connection state (idle, in-use,
closed) and lifecycle metadata. The raw `Connection` interface from JDBC
doesn't expose any of that.

**Options.**
- A. Track state in a parallel map (`Map<Connection, State>`)
- B. Wrap each connection in a dedicated `PoolEntity<T>` class

**Decision.** B. The pool stores a list of `PoolEntity<T>`. Each entity
holds the connection, its state, an identifier, and a callback for recycling.

**Consequences.** Users of the pool deal with `PoolEntity`, not raw
connections. Slightly less ergonomic than Hikari's transparent proxy, but
much simpler to reason about.

---

### ADR-002 — `close()` releases, `destroy()` actually closes

**Context.** A `close()` method is the natural fit for `AutoCloseable` and
try-with-resources. But "close" is ambiguous: does it return the entity to
the pool, or does it tear down the underlying resource?

**Options.**
- A. `close()` performs a real teardown (matches the JDBC convention)
- B. `close()` releases to the pool; a separate `destroy()` performs teardown

**Decision.** B. `close()` transitions the state from `IN_USE` back to
`IDLE` and notifies the pool. `destroy()` is the only method that actually
closes the underlying connection.

**Consequences.** Users can rely on try-with-resources for safe release.
Real teardown is reserved for the pool itself (eviction, shutdown). The
naming is slightly less standard than JDBC's, but the semantics are explicit.

---

### ADR-003 — State enum, no `UNKNOWN`

**Context.** Initial design used two booleans (`active`, `closed`). This
allowed combinations that didn't represent real states.

**Options.**
- A. Keep booleans
- B. Introduce a `PoolState` enum with `IDLE`, `IN_USE`, `CLOSED`,
  `UNKNOWN`
- C. Same enum, but drop `UNKNOWN`

**Decision.** C. An `UNKNOWN` state is a modeling smell — it means a case
hasn't been thought through. The three states are sufficient. Additional
states (e.g. `BROKEN`) will be added later if a concrete need appears.

**Consequences.** State transitions become explicit and verifiable. Each
transition method validates the current state and throws on invalid moves.

---

### ADR-004 — Entity ID lives on the pool instance, not as a static counter

**Context.** Each `PoolEntity` should have a unique identifier for logging,
metrics, and leak detection.

**Options.**
- A. `static final AtomicInteger ID_GENERATOR` in `PoolEntity`
- B. `AtomicInteger entityIdGenerator` field on `DefaultPool`, passed at
  entity construction
- C. UUID per entity

**Decision.** B. Each pool owns its own counter. IDs are local and
sequential within a pool, which makes logs readable and tests
deterministic.

**Consequences.** Multiple pools in the same JVM each restart from 1.
No global coupling between pool instances. Tests don't depend on
execution order.

**Why not A.** Static state mutates across tests and couples unrelated
pool instances together. Bad smell.

**Why not C.** UUIDs solve a problem we don't have (cross-process
uniqueness). They hurt log readability without compensating benefit.

---

### ADR-005 — Recycling callback as `Consumer<PoolEntity<T>>`

**Context.** When `close()` is called on an entity, it must signal back
to the pool to update bookkeeping. The entity needs to know "how to
return home".

**Options.**
- A. Pass the `Pool<T>` (or `DefaultPool<T>`) instance to the entity
- B. Pass a `Consumer<PoolEntity<T>>` callback at entity construction
- C. Use a `Function<PoolEntity<T>, X>` for symmetry with FP idioms

**Decision.** B. The entity is constructed with a recycling callback —
typically `pool::giveBack` — and invokes it on close.

**Consequences.**
- The entity is decoupled from the `Pool` interface entirely; it only
  knows about an effect to invoke.
- Trivial to test in isolation: pass any `Consumer` that records the
  recycled entity.
- Composable via `Consumer.andThen` for adding logging or metrics
  without modifying the entity.
- No circular type reference between `Pool` and `PoolEntity`.

**Why not C (Function).** Recycling has no meaningful return value.
`Function<X, Void>` would force a parasitic `null` return everywhere.
The `Consumer` type expresses intent: "side effect with no result",
which mirrors `IO[Unit]` in Scala.

---

### ADR-006 — Single list of entities, scan on acquire

**Context.** The pool needs to find idle entries on acquire and track
all entries for max-size enforcement.

**Options.**
- A. Single `List<PoolEntity<T>>` with state-based filtering
- B. Two structures: a `BlockingQueue` of idle entries plus a counter
  for total

**Decision.** A for now. `acquire()` streams the list, filters by
`isIdle()`, takes the first match. If none found and `size < maxSize`,
a new entity is created.

**Consequences.**
- Simple to read and reason about
- O(n) on acquire, acceptable for small pools