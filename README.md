# PoolerCnx

## Disclaimer
This is a toy project built for learning and experimentation.
Do not use it in production 🙂

## About
A small personal project to explore how database connection pools work
and how they could be tuned dynamically at runtime.

The goal is not to build a production-ready pool, but to better understand:
- how connections are managed under load
- how contention appears when resources are limited
- how simple feedback loops can adjust system behavior

## What it does
- Implements a minimal connection pool
- Simulates concurrent access with multiple threads
- Tracks basic metrics (active connections, wait time, etc.)
- Applies a naive auto-tuning strategy to adjust pool size

## Why
Built as a hands-on way to dig into concurrent programming patterns
(locks, conditions, atomic state) and pool design trade-offs
(min/max sizing, saturation handling, recycling vs destruction).

Inspired by HikariCP, but with a deliberately simpler design
to keep the code readable.

## Status
Work in progress. Currently focused on:
- [x] Basic acquire / release lifecycle
- [x] Eager initialization up to minIdle
- [ ] Blocking acquire with timeout
- [ ] Concurrent stress tests
- [ ] Auto-tuning strategy