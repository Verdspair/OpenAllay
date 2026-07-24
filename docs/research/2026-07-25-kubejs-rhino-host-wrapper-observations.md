# KubeJS Rhino host-wrapper observations

Date: 2026-07-25

## Scope

This note records observations made while probing the KubeJS Rhino runtime used
by OpenAllay. It is research material for an upstream issue, patch, or a
security-focused fork. It is not an OpenAllay permission decision and does not
change the product's capability-first runtime design.

The tests used a fresh Rhino scope for every JavaScript execution and exposed a
single OpenAllay `HostObjectView` as `mc.fixture`.

## Confirmed observations

### Java bridge globals remain unavailable

Within the tested scope:

- `typeof Java` was `undefined`;
- `typeof Packages` was `undefined`;
- direct members such as `mc.fixture.get`, `mc.fixture.getIds`,
  `mc.fixture.defineProperty`, and `mc.fixture.getClass` were `undefined`.

The presence of the names `JavaException`, `Call`, and `CallSite` as functions
did not by itself provide a path to Java class lookup or method invocation.

### Built-in prototypes are mutable inside one execution

`Function.prototype.call` was writable and configurable. A script could replace
it and observe the replacement during the same execution. A second execution
using a fresh scope observed the ordinary built-in implementation again.

This is script-local prototype mutation, not evidence of cross-request state
leakage.

### `JSON.stringify` discloses the Java wrapper implementation

String coercion produced only `[object Object]`, but:

```javascript
JSON.stringify(mc.fixture)
```

serialized implementation details from the Java-backed wrapper. The output
included the class name:

```text
class dev.openallay.script.host.HostObjectView
```

and a long list of inherited Java method and field signatures, including
Scriptable-object methods such as `get`, `put`, `defineProperty`,
`getDefaultValue`, and related members.

Those disclosed names were not directly accessible as JavaScript properties in
the tested scope. The confirmed impact is implementation-information
disclosure and avoidable output amplification. No arbitrary Java call or code
execution was demonstrated.

## Minimal reproducer

Given a Java-backed Scriptable host object exposed as `target`:

```javascript
({
  directGet: typeof target.get,
  directGetClass: typeof target.getClass,
  text: String(target),
  json: JSON.stringify(target)
})
```

Expected secure-wrapper behavior is that `json` represents only the intended
script-visible data contract, or rejects serialization. It should not expose
the wrapper's Java class metadata.

To check scope isolation separately:

```javascript
Function.prototype.call = function () { return "changed"; };
(function () {}).call(null)
```

Run `Function.prototype.call.name` in a newly created scope. In the tested
runtime it returned `call`, confirming that the mutation did not persist.

## Upstream issue outline

Suggested title:

```text
Host Scriptable metadata is exposed by JSON.stringify
```

Suggested report:

1. Describe the Rhino/KubeJS version and how the host Scriptable is installed.
2. Include the minimal reproducer above.
3. Include only a short redacted excerpt of the serialized class metadata.
4. State that direct Java bridge globals and direct Java method properties were
   unavailable in the tested scope.
5. Classify the demonstrated behavior as implementation-information disclosure
   and serialization amplification, not sandbox escape.

## Candidate upstream hardening

- Give host wrappers an explicit JSON representation containing only declared
  script properties.
- Prevent Java reflection metadata from being treated as enumerable
  serialization input.
- Add regression coverage for `JSON.stringify`, `Object.keys`, `for...in`,
  property descriptors, constructor chains, and fresh-scope prototype
  isolation.
- Keep host capability exposure explicit; do not infer it from Java wrapper
  members.

OpenAllay intentionally does not add a local behavioral restriction for this
observation. If the runtime later needs stronger isolation, the preferred home
is a maintained Rhino patch/fork with focused regression tests.
