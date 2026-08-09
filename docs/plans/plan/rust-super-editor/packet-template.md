# Capability packet template

## Identity

- **Packet**: `SE-M?-P??`
- **Title**: `TBD`
- **Milestone**: `M?`
- **Canonical tasks**: `TBD`
- **Status**: `Planned`
- **Owner**: `Unassigned`
- **Owned-path lease**: `Unassigned`; packet cannot become `Ready` until recorded

## Outcome

Describe the one reviewable state change this packet makes true. Do not describe general effort.

## Prerequisites

- Accepted packet IDs:
- Compatibility rows/levels:
- Client/server parity checkpoint:
- Required decisions/ADRs:

## Owned paths

- Create:
- Modify:
- Generated:
- Explicitly excluded:

## Baseline

- Exact commands:
- Expected project/corpus state:
- Existing failures that must remain separate:

## RED proof

- Test/fixture introduced first:
- Exact command:
- Expected failing observation:
- If test-first is impossible, state the alternate falsifiable baseline.

## Implementation requirements

1. `TBD`

## Verification

- Focused:
- Workspace:
- Cross-consumer:
- Static/security:
- Visual/manual, when applicable:

## Evidence produced

- Matrix rows advanced:
- Compatibility level changes:
- Screenshots/logs/reports:
- QA note entry:

## Rollback

State the bounded revert path and what persisted artifacts or migrations require special handling.

## Independent review focus

Name the most likely correctness, data-loss, security, architecture, or UX failure the reviewer must attack.

## Done when

- [ ] Prerequisites were current at implementation start.
- [ ] Baseline and RED evidence were recorded.
- [ ] Focused and required full gates passed.
- [ ] No unrelated paths changed.
- [ ] Matrices and QA evidence were updated.
- [ ] Independent reviewer accepted the packet.
