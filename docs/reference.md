# Module Reference

This page is the reference landing map for the documented RCCE modules under
[`docs/modules/`](modules/). Use it together with the live application
entrypoints in [`src/Client.bb`](../src/Client.bb),
[`src/Server.bb`](../src/Server.bb), [`src/GUE.bb`](../src/GUE.bb), and
[`src/Project Manager.bb`](../src/Project%20Manager.bb) when you are tracing a
runtime path through the current source tree.

Not every source module has a matching reference page yet. This index covers
the modules that already have docs and calls out the largest undocumented
surfaces so the page reflects the real repository state.

## How To Read The Current Tree

- The client currently includes [`ClientAreas_FE.bb`](../src/Modules/ClientAreas_FE.bb);
  the matching reference page is [`modules/clientareas.md`](modules/clientareas.md).
- The server and editor share a large slice of gameplay/content modules, but the
  server also owns authority, persistence, and account/update flows.
- Project Manager now lives on newer framework, graphics, IO, and component
  modules that do not yet have dedicated reference pages under `docs/modules/`.

## Application Map

### Client

- Startup and resource loading:
  [`ClientLoaders`](modules/clientloaders.md), [`MainMenu`](modules/mainmenu.md),
  [`Media`](modules/media.md), [`Language`](modules/language.md),
  [`Logging`](modules/logging.md)
- World and rendering:
  [`ClientAreas`](modules/clientareas.md), [`Environment`](modules/environment.md),
  [`Environment3D`](modules/environment3d.md), [`Actors`](modules/actors.md),
  [`Actors3D`](modules/actors3d.md), [`Animations`](modules/animations.md),
  [`Projectiles3D`](modules/projectiles3d.md),
  [`RottParticles`](modules/rottparticles.md), [`Radar`](modules/radar.md)
- Gameplay and interface:
  [`Items`](modules/items.md), [`Inventories`](modules/inventories.md),
  [`Spells`](modules/spells.md), [`ClientCombat`](modules/clientcombat.md),
  [`Interface`](modules/interface.md), [`Interface3D`](modules/interface3d.md),
  [`Gooey`](modules/gooey.md),
  [`Gooey_3D_Text`](modules/gooey_3d_text.md),
  [`CharacterEditorLoader`](modules/charactereditorloader.md)
- Networking:
  [`RottNet`](modules/rottnet.md), [`ClientNet`](modules/clientnet.md),
  [`Packets`](modules/packets.md), [`MD5`](modules/md5.md)

### Server

- Core game state:
  [`Actors`](modules/actors.md), [`Items`](modules/items.md),
  [`Inventories`](modules/inventories.md), [`Spells`](modules/spells.md),
  [`Projectiles`](modules/projectiles.md),
  [`Environment`](modules/environment.md), [`ServerAreas`](modules/serverareas.md)
- Scripting, networking, and persistence:
  [`Scripting`](modules/scripting.md), [`RottNet`](modules/rottnet.md),
  [`ServerNet`](modules/servernet.md), [`Packets`](modules/packets.md),
  [`MySQL`](modules/mysql.md), [`Logging`](modules/logging.md),
  [`Language`](modules/language.md)
- Server-only control surfaces:
  [`AccountsServer`](modules/accountsserver.md),
  [`GameServer`](modules/gameserver.md),
  [`UpdatesServer`](modules/updatesserver.md)

### GUE Editor

- Content databases and pickers:
  [`Media`](modules/media.md), [`MediaDialogs`](modules/mediadialogs.md),
  [`Items`](modules/items.md), [`Inventories`](modules/inventories.md),
  [`Animations`](modules/animations.md), [`Spells`](modules/spells.md)
- World and content editing:
  [`Actors`](modules/actors.md), [`Actors3D`](modules/actors3d.md),
  [`CharacterEditorLoader`](modules/charactereditorloader.md),
  [`Environment`](modules/environment.md), [`Interface`](modules/interface.md),
  [`ClientAreas`](modules/clientareas.md), [`ServerAreas`](modules/serverareas.md),
  [`Projectiles`](modules/projectiles.md), [`RCTrees`](modules/rctrees.md),
  [`RottParticles`](modules/rottparticles.md)
- Shared runtime plumbing:
  [`Language`](modules/language.md), [`RottNet`](modules/rottnet.md),
  [`Packets`](modules/packets.md), [`Logging`](modules/logging.md)

### Project Manager

Project Manager is part of the live source tree, but its current implementation
leans on framework, graphics, IO, and UI-component modules that do not yet have
matching pages in [`docs/modules/`](modules/). For now, start with
[`src/Project Manager.bb`](../src/Project%20Manager.bb) and the contributor
workflow in [`start.md`](start.md).

## Documented Module Index

### World, Gameplay, And Content

- [`Actors`](modules/actors.md) - actor templates and live actor instances
- [`Actors3D`](modules/actors3d.md) - client-side actor rendering state
- [`Animations`](modules/animations.md) - animation set loading and save flows
- [`CharacterEditorLoader`](modules/charactereditorloader.md) - character editor
  render data loading
- [`ClientAreas`](modules/clientareas.md) - client-side area loading and save
  flows
- [`ClientCombat`](modules/clientcombat.md) - combat UI updates and combat
  animation/display logic
- [`Environment`](modules/environment.md) - world time, weather, and other
  environment settings
- [`Environment3D`](modules/environment3d.md) - 3D weather, suns, and other
  client presentation for the environment
- [`Inventories`](modules/inventories.md) - inventory ownership and item storage
- [`Items`](modules/items.md) - item templates and persistence
- [`Projectiles`](modules/projectiles.md) - projectile definitions and server
  updates
- [`Projectiles3D`](modules/projectiles3d.md) - client-side projectile
  rendering
- [`Radar`](modules/radar.md) - minimap creation and updates
- [`RCTrees`](modules/rctrees.md) - tree assets built by the tree editor
- [`RottParticles`](modules/rottparticles.md) - 3D particle effect assets and
  runtime behavior
- [`ServerAreas`](modules/serverareas.md) - server-side area data and weather
- [`Spells`](modules/spells.md) - ability definitions and related data

### Interface, Assets, And Client Support

- [`ClientLoaders`](modules/clientloaders.md) - client startup data loading
- [`Gooey`](modules/gooey.md) - resolution-independent in-game UI framework
- [`Gooey_3D_Text`](modules/gooey_3d_text.md) - 3D text support for Gooey
- [`Interface`](modules/interface.md) - saved interface layout/settings data
- [`Interface3D`](modules/interface3d.md) - live in-game interface widgets and
  player input handling
- [`Language`](modules/language.md) - localized string loading and lookup
- [`MainMenu`](modules/mainmenu.md) - client startup menu flow
- [`Media`](modules/media.md) - mesh, texture, sound, and music database access
- [`MediaDialogs`](modules/mediadialogs.md) - standard media selection dialogs

### Networking, Services, And Server Control

- [`AccountsServer`](modules/accountsserver.md) - account creation and account
  management windows
- [`ClientNet`](modules/clientnet.md) - client message handling and outbound
  updates
- [`GameServer`](modules/gameserver.md) - server-side game logic update loop and
  server window management
- [`Logging`](modules/logging.md) - log creation and log writing
- [`MD5`](modules/md5.md) - MD5 checksum helper used by password flows
- [`MySQL`](modules/mysql.md) - MySQL integration points
- [`Packets`](modules/packets.md) - network packet type constants. The wire-side reference (encoding, handler conventions, per-packet field layouts) lives under [`protocol/`](protocol/index.md), generated by `scripts/gen_packet_index.sh`.
- [`RottNet`](modules/rottnet.md) - low-level networking library
- [`Scripting`](modules/scripting.md) - gameplay script interpreter (entry-point lifecycle, privilege model, soft-fail / clamp conventions). The per-function BVM API catalog is auto-generated at [`bvm-reference.md`](bvm-reference.md).
- [`ServerNet`](modules/servernet.md) - server message processing and broadcast
  flows
- [`UpdatesServer`](modules/updatesserver.md) - update file metadata and update
  distribution window

## Current Gaps

These source-tree surfaces are part of the live code but still lack dedicated
reference pages here:

- Project Manager framework and component modules under
  [`src/Modules/Framework/`](../src/Modules/Framework/) and
  [`src/Modules/Graphics/`](../src/Modules/Graphics/)
- Server helpers such as [`SpawnTracking.bb`](../src/Modules/SpawnTracking.bb)
- Client/editor helpers such as [`F-UI.bb`](../src/Modules/F-UI.bb),
  [`FastExt.bb`](../src/Modules/FastExt.bb), and
  [`ShadowsSimple.bb`](../src/Modules/ShadowsSimple.bb)

When you need those areas today, read the source entrypoint plus the
corresponding module files directly.
