
This file provides guidance when working with code in this repository.

## Project Overview

FlashBang is an alarm app that uses vocabulary flashcards as the mission to turn off the alarm, allowing users to revise the vocabulary they are trying to learn as part of their daily morning routine. It aims to provide a way for low-effort revision by tying studying to existing daily routines like waking up with an alarm.

## Repository Overview

This is a monorepo containing Kotlin code.

## Architecture
- Check @.claude/architecture.md for tech stack, repository structure, data models and full list of API endpoints


## Development Workflow
- Always start with the why of the development work  (refer to PRD.md)
- PROJECT_PLAN type md files contain the overarching scope for each release of the app (e.g. MVP, )
- Check (RELEASE NAME)_PROJECT_PLAN.md for each release's development state. A single PROJECT_PLAN.md contains multiple features and using checkboxes to track development progress.
- Then write the detailed documentation (requirements.md, design.md, and tasks.md) for each feature, and seek human approval on it
- Refer closely to this documentation when developing
- Spin up sub-agents to speed up development work, but ensure all agents follow the documentation as the source-of-truth
- When a task is completed, tasks.md should be updated.
- Keep an implementation-notes.md file. In this file, provide a brief overview of what was done and explain the underlying software engineering design patterns and concepts relevant to your work.
- If you hit an edge case that forces you to deviate from the plan, pick the conservative option, log it under "Deviations" in implementation-notes.md and keep going.
- Write your own tests and ensure that all tests pass before asking the human to review
- When the human has reviewed and approved the feature, update the PROJECT_PLAN to mark the feature as complete