---
name: guide-ftb-progression
description: Use when a player asks about visible FTB Quests progression, prerequisites, rewards, or branches.
metadata:
  openallay/version: "0.2.1"
  openallay/required-mods: "ftbquests"
allowed-tools: "openallay:run_javascript"
---
Use only visible, player-authorized quest documents captured in `mc.knowledge`.
Identify the requested goal, select its documents, and correlate prerequisites
before rewards or optional branches.
Never reveal hidden quest text or infer hidden dependencies. If the FTB Quests
source is unavailable for this version, report that and fall back only to other
visible guide evidence.
