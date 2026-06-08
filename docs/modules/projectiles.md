<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Projectiles.bb**

This module defines the catalog of projectile templates used by ranged weapons and spell effects. Projectile records describe the mesh, particle emitters, damage profile, and homing behavior for in-flight objects the runtime spawns from these templates. The visual / 3D side lives in [Projectiles3D.bb](projectiles3d.md).

This module contains the following globals:

*   [ProjectileList.Projectile(5000)](#GProjectileList)

This module contains the following types:

*   [Projectile](#TProjectile)

This module contains the following functions:

*   [CreateProjectile](#FCreateProjectile)
*   [LoadProjectiles](#FLoadProjectiles)
*   [SaveProjectiles](#FSaveProjectiles)
*   [FindProjectile](#FFindProjectile)

  

* * *

  

**ProjectileList.Projectile(5000) (global)**

This global array indexes every Projectile object, with the array index being the ID for that object. It thus provides fast non-sequential access to any Projectile object. Indices outside `0..5000` are rejected at load time to prevent `Dim` out-of-range writes from a corrupted `Projectiles.dat`.

  

* * *

  

**Projectile (type)**

This type represents a projectile template. It stores:

*   `ID` — server-side identifier and array index into `ProjectileList`.
*   `Name$` — display name; matched by `FindProjectile`.
*   `MeshID` — mesh ID into the project's mesh table.
*   `Emitter1$`, `Emitter2$` — paths into `Data\Emitter Configs` (.rpc) for the two particle emitters attached to the projectile.
*   `Emitter1TexID`, `Emitter2TexID` — texture IDs the emitters bind at runtime.
*   `Homing` — boolean; if true the projectile tracks its target each frame.
*   `HitChance` — accuracy modifier (0-100).
*   `Damage`, `DamageType` — combat resolution data; `DamageType` indexes the global `DamageTypes$` table.
*   `Speed` — base travel speed.

  

* * *

  
  
  

**CreateProjectile.Projectile()**

Return value: A new `Projectile` reference, or `Null` if `ProjectileList` is full.

Parameters: None

This function allocates the next free slot in `ProjectileList`, assigns the slot's index as the new projectile's `ID`, and returns the new template for the caller to populate.

  

* * *

  

**LoadProjectiles(Filename$)**

Return value: Number of projectiles loaded, or `-1` if the file could not be opened.

Parameters:

*   _Filename$_ — Path to the projectile data file (typically `Data\Server Data\Projectiles.dat`).

Reads projectile templates from disk and registers each one in `ProjectileList`. Each record carries a 2-byte `ID` followed by the projectile's fields; an out-of-range `ID` or a malformed length-prefixed string aborts the load defensively rather than crashing the server. String fields go through `ReadBoundedString$` (see [Logging.bb](logging.md)) with a 256-byte cap.

  

* * *

  

**SaveProjectiles(Filename$)**

Return value: `True` on success, `False` if the file could not be opened.

Parameters:

*   _Filename$_ — Path to write the projectile data file.

Writes every loaded `Projectile` to disk via the atomic temp+rename helper in [Logging.bb](logging.md), so a crash or power loss mid-write cannot truncate the catalog.

  

* * *

  

**FindProjectile(Name$)**

Return value: The `ID` of the matching projectile, or `-1` if none was found.

Parameters:

*   _Name$_ — Case-insensitive name to look up.

Linear scan of `Projectile` records by name. Used by spell and weapon scripts to resolve a designer-supplied projectile name to an `ID` at runtime.

  

* * *

  
  
  
