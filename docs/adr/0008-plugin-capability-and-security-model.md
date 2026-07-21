# ADR 0008 — Plugin capability and security model

- **Status:** Accepted
- **Date:** 2026-07-20

## Context and evidence

Specialist tools cover terrain, procedural meshes, caves, rocks, trees, gubbin
calibration, fonts, audio, vegetation, and partly opaque binary utilities. Some
capabilities may be recovered faster or more safely as isolated producers than
as native editor modules. Giving extensions ambient project paths, process
access, network access, or in-process memory would bypass root confinement,
commands, state classifications, validation, and recovery.

## Decision

Versioned or untrusted plugins execute out of process from a versioned,
declarative capability manifest. All capabilities are denied by default,
including project filesystem access, secret/dynamic state, arbitrary host paths,
network, database, process spawn, environment inheritance, and persistent host
storage.

A plugin capability is unavailable unless the platform's mandatory isolation
profile passes executable startup and per-invocation checks for filesystem and
handle confinement, process-tree containment/termination, network denial,
environment sanitization, resource quotas, and authenticated host IPC. Missing,
unverifiable, or degraded primitives fail closed; a warning is not authority to
run. The UI reports the capability unavailable and names only the failed
primitive, without launching the plugin.

The host may grant only operation-scoped capabilities:

- selected input bytes or bounded read-only handles;
- isolated scratch storage with quota;
- isolated output handles with declared size/count/type limits;
- deterministic parameters and an explicit time/resource budget.

The plugin never receives a writable canonical project path. Returned output is
untrusted staged data. The host verifies manifest identity/version, quotas,
declared types, state class, root policy, format reparse/validation, and command
preflight. Only an accepted `ProjectCommand` and ADR-0005 promotion can make the
artifact project state.

Each launch has a host-generated unpredictable invocation nonce. The immutable
invocation record binds that nonce to cryptographic digests of the plugin binary,
capability manifest, exact input artifacts, canonicalized parameters, and
granted capabilities. Authenticated IPC and the result envelope carry the nonce
and record digest; every output artifact is opened through its invocation-owned
handle and bound by content digest into that envelope. Missing/mismatched
bindings, duplicate nonce, output from another invocation, or replayed result
fails validation and is quarantined.

Crash, hang, malformed output, denied-capability attempt, protocol violation,
or resource exhaustion terminates and quarantines the operation without project
mutation. Audit records omit secrets and distinguish host validation failure
from plugin execution failure.

Any future trusted in-process extension tier is a separately named security
class with a new ADR and may not claim the containment guarantees of this model.

## Alternatives considered

1. Load dynamic libraries in process. Rejected because a plugin could mutate
   memory/files and crash the editor outside capability enforcement.
2. Pass a project root plus convention-based restrictions. Rejected because
   convention is not a security boundary.
3. Let plugins write exports directly and inventory afterward. Rejected because
   validation after mutation cannot ensure confinement or recovery.
4. Ban plugins entirely. Rejected because opaque/recoverable specialist
   capabilities may need an isolated migration path without contaminating core.

## Consequences

- Plugins are artifact producers, not alternate project models or storage paths.
- Some legacy utilities cannot run unchanged because they expect ambient paths
  or unrestricted APIs.
- The host protocol, sandbox backend, quotas, cancellation, and quarantine need
  platform-specific implementation and evidence.
- Native modules and plugins share the same command/validation/storage boundary.

## Invariants

- Denied-by-default is applied per invocation, not only at installation.
- No untrusted plugin receives secrets, dynamic-private state, canonical write
  access, network/database access, or process spawn.
- Plugin output cannot bypass reparse, validation, preflight, and commands.
- A plugin is unavailable unless every mandatory isolation primitive passes;
  degraded execution never inherits the sandboxed label.
- Timeout or crash leaves canonical project fingerprints unchanged.
- Manifest version, plugin binary identity, granted capabilities, and result are
  auditable with invocation, input, parameter, and output bindings without
  recording secret content.
- A trusted tier never inherits “sandboxed” labeling.

## Verification and exit evidence

A malicious-plugin harness attempts traversal, canonical writes, link escapes,
secret and dynamic-state reads, network/database access, process spawn,
environment scraping, oversized/count-bomb/malformed output, protocol spoofing,
crash, hang, cancellation races, stale-output replay, nonce reuse, binary or
manifest replacement, input/parameter/grant substitution, cross-invocation
output swapping, and missing/mismatched digests. Isolation-backend fixtures
disable each mandatory primitive in turn and prove the capability is unavailable
and the plugin never launches. Outside-root and
project canaries prove no read or mutation. The editor host remains responsive,
terminates the process tree, enforces quota, quarantines output, and accepts only
validated staged artifacts through a command. Tests run on every supported
creator platform and record sandbox limitations explicitly.

## Dependencies

- [ADR-0003](0003-project-root-confinement-and-link-policy.md)
- [ADR-0004](0004-command-only-project-mutations.md)
- [ADR-0005](0005-durable-journal-and-recovery.md)
- [Editor capability matrix specialist rows](../compat/editor-capability-matrix.md)

## Supersession and change process

New capability kinds or sandbox exceptions require threat-model review and an
ADR amendment only when they preserve every invariant. Ambient filesystem,
network, process, database, secret, or in-process authority requires a new
superseding ADR and a distinct trust label.
