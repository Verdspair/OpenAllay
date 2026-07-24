# Complete command bridge

Use `roots: ["commands"]` in the `run_javascript` Tool input for every example
below. Call the binding as `commands`; do not write `mc.commands`.

If the command text contains JSON, component text, or both quote styles, use a
JavaScript template literal instead of manually escaping nested quote layers:

```javascript
var command = `tellraw @s {"text":"OpenAllay command feedback test"}`;
return commands.run(command);
```

## Modern item components

Modern `/give` item arguments use the current item-component grammar, not
legacy item NBT:

```text
<item-id>[<component-id>=<SNBT value>,<component-id>=<SNBT value>,...]
```

First confirm the outer path:

```javascript
var give = commands.describe("give <targets> <item>");
var item = `minecraft:paper[minecraft:custom_name={text:'Sample'}]`;
return {
  syntax: give,
  result: commands.run(`give @s ${item} 1`)
};
```

The item ID, component IDs, values, and count must come from the player's
request and current game, not from this sample. Mods may register their own
items and components. Do not copy a benchmark artifact or prewritten answer.
Do not use the removed legacy `{tag:...}` form.

If Minecraft returns an unknown-item, unknown-component, malformed-component,
or command-context message, inspect that exact feedback together with the
described `<item>` node, change only the rejected part, and retry at most once.
Permission feedback is terminal; changing spelling cannot grant permission.

## Discover the active registry

```javascript
var catalog = commands.list();
return catalog.nodes
  .filter(function (node) {
    return node.path.indexOf("examplemod") !== -1;
  })
  .map(function (node) {
    return {
      path: node.path,
      kind: node.kind,
      argumentType: node.argumentType,
      executable: node.executable,
      usage: node.usage
    };
  });
```

`path` uses literal names and angle-bracket argument names, for example
`give <targets> <item>`. `children` contains the next path segments. `redirect`
contains the target path when Brigadier redirects the node.

## Inspect one known path

```javascript
return commands.describe("time set <time>");
```

Use the exact `path` returned by `commands.list()`. `describe` does not parse a
concrete command line and does not guess aliases.

Do not call `commands.list()` for an exact command the player already supplied
or for a known unambiguous vanilla syntax. Never return its complete `nodes`
array to the model; filter to the relevant literal or mod prefix in JavaScript.

## Run in order and read the feedback

```javascript
var first = commands.run("time set day");
var second = commands.run("/weather clear");
return {
  time: {
    state: first.state,
    messages: first.messages
  },
  weather: {
    state: second.state,
    messages: second.messages
  }
};
```

Each result has:

- `sequence`: request-local execution order, beginning at 1;
- `actorId`: the player whose connection executed it;
- `command`: the executed text without one optional leading slash;
- `state`: `feedback` or `no_feedback`;
- `messages`: the ordered client-visible Minecraft feedback lines;
- `feedbackObserved`: whether at least one feedback line was captured;
- `durationMillis`: elapsed submission and feedback time.

`commands.run` is synchronous from JavaScript's point of view, but command
submission and feedback happen asynchronously with respect to the Minecraft
render thread. OpenAllay waits on its worker thread, so the script can use the
returned result immediately without promises or polling.

`commands.run` adds no OpenAllay allowlist, argument filter, or call-count cap.
Minecraft's command parser, connection state, registered command tree, and
player permission are final. Minecraft has no universal command-result packet,
so `messages` is the observable feedback rather than a fabricated success bit.
Already submitted commands are never rolled back.
