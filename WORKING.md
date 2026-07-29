## Working mode

This document traces the design reflections and decisions made while building
this connection pool as a learning project.

The approach:

- **Real TDD**: write the test, see what it validates, then implement.
  No code without a test asking for it.
- **Single-threaded first**: get the lifecycle right before tackling
  concurrency. The multi-thread part will reuse the same building blocks.
- **Hand-coded primitives**: rather than reaching for `BlockingQueue` or
  similar high-level tools, code them by hand first to understand what they
  solve. Switch to standard tools once the underlying mechanics are clear.
- **Driven by intuition, validated by reasoning**: when something feels off
  in the design, that signal is taken seriously. We discuss why, even if
  the conclusion is "your gut was right".
- **Bugs are teachers**: each bug is an opportunity to internalize a concept.
  Don't shortcut the debugging — feel the failure mode.

### How the LLM is used in this project

The LLM is used as a **sounding board**, not as a decision-maker or code generator.
Every idea, design choice and decision comes exclusively from the human. The LLM never initiates, never proposes directions and never drives the work — it only reacts to what the human brings: asking questions, pointing out contradictions, or confirming that a reasoning holds.
This is a deliberate choice that shapes how the work happens:

- **No repo access given**. The LLM doesn't see the source tree. Code is
  pasted in selectively, only when discussion requires it. This keeps the
  LLM from drifting into "let me rewrite half your file" mode and forces
  reasoning to happen on the human side.
- **Bugs are described, not auto-fixed**. When something breaks, the
  symptom is shared and the LLM proposes hypotheses. The actual debugging
  — finding the line, fixing it, re-running the test — stays a human task.
  This preserves the learning loop.
- **Designs are debated before being coded**. Major decisions (state
  machine shape, recycling callback type, where to put the ID counter)
  are discussed with options and trade-offs, then a choice is made
  consciously. The LLM does not propose "the right answer" prematurely.
- **The LLM challenges, doesn't comply**. When an intuition seems off,
  it's pushed back on with reasoning, not validated to please. When an
  intuition is right, that's said too.
- **No prescriptive roadmaps**. The LLM avoids handing out staged plans
  ("first do X, then Y, then Z") that would replace genuine exploration.
  Direction is set by the human; the LLM helps walk one step at a time.

The intent is that the resulting code is fully understood by the human
who wrote it — not "AI-assisted" in the sense of being half-authored
externally, but "AI-rubber-ducked" in the sense of having had a sparring
partner during reflection.

- **Commit and work organisation**: the LLM helps review staged changes,
  suggest commit messages consistent with the project's convention, and
  flag anything worth splitting or unstaged before committing. The human
  always makes the final call on what goes in and when.