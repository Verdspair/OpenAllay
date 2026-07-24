---
name: run-game-commands
description: Discover and run Minecraft commands, including commands registered by the server, loader, or installed mods, through the enabled experimental commands object.
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

Load `references/commands.md` before composing a discovery or execution
program. Prefer one JavaScript call: inspect the detached catalog only when the
syntax is unknown, then submit commands in the required order in the same
program.

For a command-only `run_javascript` call, set the Tool input `roots` to
`["commands"]`. The binding is named `commands` directly; there is no
`mc.commands`.

If the player supplied an exact command, or the required vanilla command and
syntax are already unambiguous, call `commands.run` directly. Do not list the
whole command tree first. When discovery is genuinely needed, filter
`commands.list().nodes` inside the same program by the relevant literal/mod
prefix and return only those candidates; never return the unfiltered catalog.

Always inspect and return the `commands.run` result. `state: "feedback"` means
Minecraft emitted one or more messages in the command feedback window;
`state: "no_feedback"` means the command produced no observable message before
that window closed. Do not describe a command as successful merely because it
was submitted.

Minecraft remains authoritative for parsing and permissions. Submissions are
not transactional: if a later statement fails or the Agent is cancelled,
commands already submitted stay submitted and are never rolled back.

Do not invent a command path. If an exact player-visible path cannot be found,
report that it is unavailable instead of trying similarly named mutations.
