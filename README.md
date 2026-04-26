# Proxy Pool

## Disclaimer
This is a toy project built for learning and experimentation.
Do not use it in production 🙂


This is a small personal project to explore how database connection pools work and how they could be tuned dynamically at runtime.
The goal is not to build a production-ready pool, but to better understand:
how connections are managed under load
how contention appears when resources are limited
how simple feedback loops can adjust system behavior

## What it does
Implements a very simple connection pool
Simulates concurrent access with multiple threads
Tracks basic metrics (active connections, wait time, etc.)
Applies a naive auto-tuning strategy to adjust pool size