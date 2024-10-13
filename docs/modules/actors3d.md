<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Actors3D.bb**

This module contains the following globals:  

*   [GubbinJoints$(5)](#GGubbinJoints)
*   [HideNametags](#GHideNametags)
*   [DisableCollisions](#GDisableCollisions)

This module contains the following functions:  

*   [LoadGubbinNames](#FLoadGubbinNames)
*   [SetActorWeapon](#FSetActorWeapon)
*   [SetActorShield](#FSetActorShield)
*   [SetActorChestArmour](#FSetActorChestArmour)
*   [SetActorHat](#FSetActorHat)
*   [LoadActorInstance3D](#FLoadActorInstance3D)
*   [CreateActorNametag](#FCreateActorNametag)
*   [FreeActorInstance3D](#FFreeActorInstance3D)
*   [SafeFreeActorInstance](#FSafeFreeActorInstance)
*   [ShowGubbin](#FShowGubbin)
*   [HideGubbin](#FHideGubbin)
*   [CreateEntityEmitters](#FCreateEntityEmitters)
*   [FreeEntityEmitters](#FFreeEntityEmitters)
*   [UpdateActorItems](#FUpdateActorItems)
*   [PlayActorSound](#FPlayActorSound)

  

* * *

  

**GubbinJoints$(5) (global)**  
  
This global array stores the attachment point joint (bone) names for each of the 6 gubbin slots. The values are loaded in the [LoadGubbinNames](#FLoadGubbinNames) function.

  

**HideNametags (global)**  
  
This global stores the setting for the display of character nametags, as set in GE. It is set once when the game is loaded and doesn't change again. Valid settings are 1 (no nametags), 2 (show for selected actor only) or 3 (always show).

  

**DisableCollisions (global)**  
  
This global stores the setting for collisions between actor instances. Valid settings are True or False.

  

* * *

  
  
  

**LoadGubbinNames()**  
  
Return value: None  
  
Parameters: None  
  
This function loads joint (bone) names for gubbins from disk and stores them for later use. This allows gubbin joints to be 'remapped' from the defaults (editor support for this file is included in RC 2).

  
  
  

**SetActorWeapon(AI.ActorInstance, MeshID)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to set the weapon mesh for
*   _MeshID_ - The media ID of the weapon mesh

  
This function sets the weapon mesh attached to an actor instance. If the MeshID parameter is -1 the weapon will be removed. The mesh is attached to the R\_Hand joint.

  
  
  

**SetActorShield(AI.ActorInstance, MeshID)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to set the shield mesh for
*   _MeshID_ - The media ID of the shield mesh

  
This function sets the shield mesh attached to an actor instance. If the MeshID parameter is -1 the shield will be removed. The mesh is attached to the L\_Hand joint.

  
  
  

**SetActorChestArmour(AI.ActorInstance, MeshID)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to set the chest mesh for
*   _MeshID_ - The media ID of the chest mesh

  
This function sets the chest armour mesh attached to an actor instance. If the MeshID parameter is -1 the chest armour will be removed. The mesh is attached to the Chest joint.

  
  
  

**SetActorHat(AI.ActorInstance, MeshID)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to set the hat mesh for
*   _MeshID_ - The media ID of the hat mesh

  
This function sets the hat mesh attached to an actor instance. If the MeshID parameter is -1 the hat will be removed and replaced with the actor's hair. The mesh is attached to the Head joint and will replace the hair mesh, if present.

  
  
  

**LoadActorInstance3D(A.ActorInstance, Scale#, SkipAttachments, AutoFade)**  
  
Return value: Success flag  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to set the hat mesh for
*   _Scale#_ - The scaling factor for the actor instance, default 1.0 (100%)
*   _SkipAttachments_ - Flag to skip loading of emitters, hair, shadow, etc., default False
*   _AutoFade_ - Flad to auto fade actors when they are too far or too close to the camera, default True

  
This function loads all meshes etc. for an existing actor instance. The first level is the character's pivot point, stored in the ActorInstance type's CollisionEN field. Next the main body mesh is loaded (based on the character's gender), and stored in the EN field. Scaling is applied, as are the face and body textures. If the gender is male, the beard mesh is also loaded and attached if required. Next the approximate radius of the character is calculated and stored. This marks the end of gender specific loading. The final sections extract all animation sequences from the main mesh, create any emitters on joints, load the hair using [SetActorHat](#FSetActorHat), create the shadow, set the collision ellipsoid and box, load any items the character has equipped, and create the nametag if required. Finally the pivot point and main enities are named with the handle of the actor instance. Note that this function should not be called twice in succession since any meshes already present are not freed.

  
  
  

**CreateActorNametag(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to load the nametag for

  
This function creates and attaches a nametag to an actor instance, replacing the existing one if found. It uses [Media->MeshMinMaxVertices](media.md#FMeshMinMaxVertices) to find the total height of the character mesh.

  
  
  

**FreeActorInstance3D(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to free all 3D meshes etc. from

  
This function frees all 3D objects from an actor instance. If the actor instance passed is the only one currently using the body mesh, it will be unloaded from memory entirely. This function should only be called if you do not want to free the actor instance entirely, otherwise use [SafeFreeActorInstance](#FSafeFreeActorInstance).

  
  
  

**SafeFreeActorInstance(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to free

  
This function frees an actor instance entirely, including 3D objects, and should always be used to remove actor instances from the game rather than doing it manually.

  
  
  

**ShowGubbin(A.ActorInstance, Num)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to show a gubbin for
*   _Num_ - The gubbin number to show

  
This function displays a gubbin on an actor instance. The Num parameter should be from 0 to 5. Attached emitters are loaded for the gubbin mesh if present. If the gubbin mesh is already loaded but hidden it is shown, otherwise it is loaded from disk.

  
  
  

**HideGubbin(A.ActorInstance, Num)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The actor instance to hide a gubbin for
*   _Num_ - The gubbin number to hide

  
This function hides a gubbin on an actor instance. The Num parameter should be from 0 to 5. Attached emitters are freed if present, but the gubbin mesh itself is only hidden and not freed.

  
  
  

**CreateEntityEmitters(E)**  
  
Return value: None  
  
Parameters:  

*   _E_ - The handle of the entity to load emitters for

  
This function searches recursively through all children of an entity. Any with a name beginning with E\_, followed by a valid emitter name and texture ID, have emitters loaded and attached to them. This is used to attach emitters to actor, item and gubbin meshes.

  
  
  

**FreeEntityEmitters(E)**  
  
Return value: None  
  
Parameters:  

*   _E_ - The handle of the entity to free emitters from

  
This function searches recursively through all children of an entity. Any emitters found are freed.

  
  
  

**UpdateActorItems(A.ActorInstance)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The handle of the actor instance

  
This function updates displayed meshes for all of an actor instance's equipped items. This includes both gubbins and item meshes.

  
  
  

**PlayActorSound(A.ActorInstance, Speech)**  
  
Return value: None  
  
Parameters:  

*   _A.ActorInstance_ - The handle of the actor instance
*   _Speech_ - The ID of the speech sound to play

  
This function plays a speech sound for an actor instance, using 3D sound emitted from the Head joint. The Speech parameter should be a constant from [Actors->Speech\_...](actors.md#CSpeech).