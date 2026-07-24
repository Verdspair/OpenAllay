---
name: run-game-commands
description: Use when a player explicitly asks to discover or execute a Minecraft command through the enabled experimental command capability.
metadata:
  openallay/version: "0.2.1"
allowed-tools: "openallay:run_javascript"
---
Use this Skill when the player explicitly asks OpenAllay to execute a Minecraft
command, or when the task must discover the exact syntax of an installed
mod's command before executing it.

The `commands` object exists only because the player enabled the experimental
command capability for this request:

- `commands.list()` returns the complete Brigadier tree visible to the current
  player. It contains vanilla, server, loader, and mod-registered nodes.
- `commands.describe(path)` returns one exact literal/argument path from that
  detached tree.
- `commands.run(command)` submits the exact command as the requesting player,
  waits off the render thread for the associated client-visible feedback window,
  and returns the messages that Minecraft produced. A single optional leading
  `/` is removed; the remaining string is unchanged.

For a command-only `run_javascript` call, set the Tool input `roots` to
`["commands"]`. The binding is named `commands` directly; there is no
`mc.commands`.

If the player supplied an exact command, or the required vanilla command and
syntax are already unambiguous, this main document is enough: call
`commands.run` directly. Do not list the whole command tree and do not load the
reference merely to confirm a known command.

Load `references/commands.md` before writing the program only when the syntax
is unknown, the command contains a modern item/text component, or the task
needs several ordered commands. For unknown syntax, filter
`commands.list().nodes` inside the same program by the relevant literal/mod
prefix, use `commands.describe(path)` on the exact candidate, and return only
the focused candidates; never return the unfiltered catalog.

Always inspect and return the `commands.run` result. `state: "feedback"` means
Minecraft emitted one or more messages in the command feedback window;
`state: "no_feedback"` means the command produced no observable message before
that window closed. Do not describe a command as successful merely because it
was submitted.

Minecraft remains authoritative for parsing and permissions. Submissions are
not transactional: if a later statement fails or the Agent is cancelled,
commands already submitted stay submitted and are never rolled back.

Treat parser errors and permission errors in `messages` as rejection, not
success. Correct a parser error at most once after inspecting its actual
message and the matching described path; never make blind variants. If an
exact player-visible path cannot be found, report that it is unavailable
instead of trying similarly named mutations.
