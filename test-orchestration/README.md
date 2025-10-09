# Test Orchestration Module

## Overview
The `test-orchestration` module provides the infrastructure that runs generated
unit tests, gathers structured failure information, and collaborates with
Gigachat to request focused fixes. It is designed as a standalone library that
other tooling can embed when they need to execute the feedback loop of
"run–diagnose–repair" for Java test suites.

## Architecture
The module is organised into four packages that mirror the end-to-end flow:

- **`execution`** – wraps Gradle in a `TestSuiteRunner` abstraction. The default
  implementation (`GradleTestSuiteRunner`) executes `TestRunRequest`s and
  captures the raw console output inside a `TestRunResult` for later analysis.
- **`reporting`** – converts Gradle output into actionable structures. The
  `GradleTestFailureParser` extracts individual failure blocks which the
  `TestFailureCollector` turns into `TestFailureDetail` instances for downstream
  consumers.
- **`gigachat`** – builds the prompts sent to Gigachat and keeps conversation
  state so future iterations can be modelled as a dialogue. The
  `GigachatFixRequestBuilder` composes requests from the documented production
  APIs plus the failure payload, while `FixConversationSession` and
  `GigachatFixGateway` hide the transport details.
- **`fix`** – applies the suggestions returned by Gigachat. The
  `GigachatResponseParser` extracts Java code blocks from model responses and the
  `TestFixApplier` writes them back to the target test sources.

`TestFixIterationCoordinator` in the root package wires these pieces together so
clients can run suites, aggregate failures, ask Gigachat for fixes, and apply the
results. The coordinator keeps the conversation session alive, which makes it
ready for multi-step clarifications in future extensions.

## Usage
1. Add the module to your build by including `project(":test-orchestration")` in
   your Gradle settings (already done in this repository).
2. Construct the collaborating components that fit your environment (for
   example, `new GradleTestSuiteRunner()`, a `TestFailureCollector` backed by the
   provided parser, and implementations of the Gigachat gateway and fix
   applier).
3. Create a `TestContextSnapshot` describing the test class under repair and the
   documented API surface that Gigachat may reference.
4. Invoke `TestFixIterationCoordinator.executeAndAttemptFix`, supplying the
   `TestRunRequest` (which identifies the Gradle project, tasks, and JVM options)
   together with the snapshot from step 3.
5. Inspect the updated sources or run the suite again to confirm whether the
   fix resolved the failure. The coordinator currently runs a single pass over
   the collected failures but its conversation session support makes it simple to
   introduce multi-iteration dialogues.

## Extensibility
The abstractions in this module intentionally isolate the orchestration
responsibilities:

- Swap out `TestSuiteRunner` to execute tests via other build tools.
- Extend `GigachatFixRequestBuilder` to enrich prompts with additional context or
  to support other LLM providers.
- Replace `TestFixApplier` if fixes need to be applied through refactoring APIs
  instead of simple file writes.

These seams allow the orchestration layer to evolve towards interactive,
multi-round repair workflows without changing its public contracts.
