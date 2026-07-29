# TIL — H2 does not throw when a `Statement` is shared across threads

## Context

While building a JDBC connection pool (`PoolEntity` / `DefaultPool`), I wanted to prove
that you can't share the same `Connection`/`Statement` between two threads at the same time —
to justify why the pool needs to hand out a **dedicated** connection per thread.

## The test

Two threads, synchronized via a `CountDownLatch`, run the **same heavy query**
(`SELECT ... FROM SYSTEM_RANGE(1, 10000000) ...`) on the **same `Statement`**:

```java
Statement st = entity.getConnexion().createStatement();
CountDownLatch start = new CountDownLatch(1);

Thread a = createThread(st, start, ex, null);
Thread b = createThread(st, start, ex, null);
a.start();
b.start();
start.countDown();

a.join();
b.join();
```

## Expected result

An `org.h2.jdbc.JdbcSQLNonTransientException` ("Object already closed" or similar),
consistent with the JDBC spec: a `Statement` (and its associated `ResultSet`) is
**not thread-safe** and shouldn't be used concurrently by multiple threads.

## Actual result

**No exception at all.** Both threads finish normally, each correctly reading its own
10,000,000 rows:

```
11:27:34.415 [Thread-5] AFTER executeQuery
11:27:34.422 [Thread-4] AFTER executeQuery
11:27:34.484 [Thread-5] Thread read 10000000 rows
11:27:34.488 [Thread-4] Thread read 10000000 rows
```

H2's internal logs also show two distinct commands (`jdbc[6]` and `jdbc[5]`), each with
its own complete `ResultSet` — no row mixing, no collision.

## What this seems to indicate

- H2 doesn't share a single cursor per `Statement` the way I expected: each call to
  `executeQuery()` seems to create its own execution context server-side, rather than
  overwriting some shared internal state.
- This lenient behavior is probably **H2-specific** (and possibly even mode-specific —
  in-memory vs file vs server). Nothing guarantees Postgres, MySQL, or Oracle behave
  the same way.

## Word of caution

⚠️ The fact that it doesn't blow up on H2 **doesn't mean it's safe**. The JDBC spec is
clear: a `Statement` isn't designed to be used by multiple threads at once. The fact
that H2 doesn't enforce that here is an implementation detail, not a guarantee — which
makes it arguably more dangerous, since it can give a false sense of safety in dev/test
while another driver in prod could behave differently (or silently corrupt results).

## To dig into later

- [ ] Re-run the same test against Postgres or MySQL to compare.
- [ ] Check whether H2 internally serializes calls on the same `Statement`
  (some internal lock?) rather than rejecting them.
- [ ] Test with two active `ResultSet`s on the same `Statement` at once
  (`st.executeQuery()` called twice without closing the first `rs`) to see if
  *that*, at least, triggers something.