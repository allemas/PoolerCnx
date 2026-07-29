# PoolerCnx

A hand-built JDBC connection pool, written from scratch as a learning exercise.
Not production-ready — that's the point.

## Why build this

Connection pools are everywhere, but few developers have looked inside one.
This project is an attempt to understand what's actually happening: how connections
are tracked, what breaks under concurrency, how a pool recovers from failures,
and what it takes to make something reliable under load.

The reference is HikariCP — one of the fastest and most widely used pools in the
JVM ecosystem. The goal isn't to replicate it, but to rediscover its mechanics
from first principles and understand why each design choice exists.

## What's implemented

- Pool lifecycle with configurable `minIdle` and `maxSize`
- Explicit state machine per connection (`IDLE`, `ACQUIRED`, `CLOSED`)
- Background scanner to detect connections closed externally (database restart,
  network drop, server timeout)
- Vacuum task that removes dead connections and recreates them to maintain `minIdle`
- Acquisition enforced — throws immediately when the pool is saturated

> ⚠️ Single-threaded only. The implementation is not thread-safe yet —
> concurrent acquisition is the next problem to solve.

## What's next

- Thread-safe acquisition without a global lock
- Blocking acquire with configurable timeout
- Connection validation before handing out
- Basic metrics (wait time, saturation rate)
- Dynamic pool sizing based on observed load

## How it's built

Every decision is made consciously before being coded. Design choices and the
alternatives that were considered are tracked in [`DESIGN.md`](./DESIGN.md).

Development follows strict TDD: the test comes first, defines what's needed,
then the implementation follows. No code without a failing test asking for it.

The process is documented in [`WORKING.md`](./WORKING.md).
