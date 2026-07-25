# Agent Tool Reliability 0.2.2 Design

## Context

Two real-client exports from `minimax-m2.7` completed ten of eleven requests,
but 14 of 34 JavaScript calls failed. The successful calls prove that the
existing Rhino host graph, command bridge, and world observation runtime work.
The failures cluster around the Agent rediscovering already-defined APIs:

- a later `retry` forgot that commands are invoked with `commands.run(...)`;
- spatial verification tried several undocumented world access shapes before
  reaching only the player's coordinates;
- an all-installed-items query was described as vanilla-only and then repeated;
- command submission or feedback was sometimes narrated as verified final
  world state;
- a cancelled streamed response appeared as abruptly truncated prose in the
  exported conversation.

The goal is to make the existing general capability reliable without adding a
second tool family or another orchestration path.

## Considered approaches

### Prompt-only patch

Add more prohibitions to the system prompt. This is the smallest change, but it
duplicates domain syntax in a growing prompt and does not give the command Skill
or export formatter an independently testable contract.

### Canonical interaction recipes

Keep the existing APIs and add a few exact, tested interaction recipes at the
boundary where the Agent learns each capability. Core JavaScript guidance owns
collection and world examples; the command Skill owns `commands.run`. The
system prompt owns retry continuity and evidence wording. The exporter owns an
explicit interruption marker.

This is the selected approach. It fixes the observed failure modes while
preserving the direct Rhino architecture and existing state decisions.

### New convenience tools or automatic planners

Add separate tools for structure verification, axes, attributes, or command
plans. This would make the current examples easier but reverse the 0.2
architecture by growing one-purpose tools and hiding the reusable JavaScript
surface. It is rejected.

## Design

### Core collection contract

The system prompt states that `mc.items`, `mc.recipes`, and other captured
registry roots already cover the installed instance represented by their
evidence. Once a complete root has been analysed, the Agent must not call the
result "vanilla-only", ask which mods are installed, or repeat the same scan
unless the result itself reports incomplete evidence.

A short canonical batch recipe shows the intended form: filter, map, sort, and
return answer-sized rows in one `run_javascript` call.

### Command workflow

The command Skill begins with the canonical invocation:

```javascript
var result = commands.run("command without a leading slash");
return result;
```

It explicitly says that `commands.run` is synchronous to JavaScript and returns
the feedback object directly. The Agent must not call `commands` as a function,
poll it, or execute a separate call merely to retrieve the result.

A continuation such as "retry", "continue", or "do it again" inherits the
active workflow. If that workflow is experimental commands and the exact Skill
instructions are not visible in retained context, the Agent reloads
`run-game-commands` before writing JavaScript. It does not rediscover the API by
trial.

The final answer distinguishes:

- parser or permission feedback: rejected;
- `feedback` with a Minecraft message: report exactly what the message confirms;
- `no_feedback`: submitted without observable confirmation;
- independent `world.inspect` evidence: verified resulting world state.

### Spatial observation

The core contract includes one exact relative-region example based on
`mc.player.position`. It selects `roots: ["player", "world"]`, computes a
task-focused absolute cuboid, calls `world.inspect(...)`, and returns matching
blocks plus coverage. The Agent uses this for requests such as checking a
structure above the player instead of inventing `mc.world`, `getBlock`, or
client objects.

The example preserves the current API and authority model. It does not add a
volume cap, mutation, or live world object.

### Cancelled export

The plain-text export remains chronology-only and continues to exclude raw Tool
arguments, normalized results, failures, and diagnostics. After a cancelled or
interrupted request, it appends a fixed human-readable marker:

```text
[This request ended before the response completed.]
```

This makes a partial stream distinguishable from file corruption without
weakening the existing export privacy decision.

## Testing

Deterministic tests verify:

- the system prompt contains the continuation, installed-instance, command
  evidence, and no-repeat rules;
- the descriptor-derived core contract contains a valid player-relative
  `world.inspect` program and the correct root selection;
- the bundled command Skill teaches direct synchronous result use and forbids
  calling `commands` as a function;
- cancelled/interrupted exports contain the marker, while completed exports do
  not;
- existing command bridge and world bridge tests continue to pass unchanged.

Both loader artifacts must build before tagging `v0.2.2`. The existing release
workflow remains responsible for GitHub and Modrinth publication.
