<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**ClientAreas.bb**

This module contains the following constants:  

*   [MaxFogFar](#CMaxFogFar)

This module contains the following globals:  

*   [SkyEN, CloudEN, StarsEN](#GSkyEN)
*   [SkyTexID, CloudTexID, StormCloudTexID, StarsTexID](#GSkyTexID)
*   [FogR, FogG, FogB](#GFogR)
*   [FogNear, FogFar](#GFogNear)
*   [Outdoors](#GOutdoors)
*   [AmbientR, AmbientG, AmbientB](#GAmbientR)
*   [DefaultLightPitch, DefaultLightYaw](#GDefaultLightPitch)
*   [LoadingTexID](#GLoadingTexID)
*   [LoadingMusicID](#GLoadingMusicID)
*   [MapTexID](#GMapTexID)
*   [SlopeRestrict](#GSlopeRestrict)

This module contains the following types:  

*   [Remove\_Surf](#TRemoveSurf)
*   [Cluster](#TCluster)
*   [Scenery](#TScenery)
*   [Water](#TWater)
*   [ColBox](#TColBox)
*   [Emitter](#TEmitter)
*   [Terrain](#TTerrain)
*   [SoundZone](#TSoundZone)
*   [CatchPlane](#TCatchPlane)

This module contains the following functions:  

*   [CreateSubdividedPlane](#FCreateSubdividedPlane)
*   [LoadArea](#FLoadArea)
*   [SaveArea](#FSaveArea)
*   [UnloadArea](#FUnloadArea)
*   [SetViewDistance](#FSetViewDistance)
*   [ChunkTerrain](#FChunkTerrain)
*   [NearestPower](#FNearestPower)
*   [RemoveSurface](#FRemoveSurface)

  

* * *

  

**MaxFogFar# (constant)**  
  
This constant defines the maximum possible far view/fog distance. Its main use is GE's zone editor.

  

* * *

  

**SkyEN, CloudEN, StarsEN (globals)**  
  
These three globals store the entity handles for the sky, cloud and star spheres, which in the client are loaded by [ClientLoaders->LoadGame](clientloaders.md#LoadGame).

  

**SkyTexID, CloudTexID, StormCloudTexID, StarsTexID (globals)**  
  
These four globals store the media IDs for the sky, cloud, storm cloud and star textures of the current zone. They are set whenever [LoadArea](#FLoadArea) is called.

  

**FogR, FogG, FogB (globals)**  
  
These three globals store the fog colour for the current zone. They are set whenever [LoadArea](#FLoadArea) is called.

  

**FogNear, FogFar (globals)**  
  
These two globals store the fog ranges for the current zone. They are set whenever [LoadArea](#FLoadArea) is called, and used in calls to [SetViewDistance](#FSetViewDistance), particularly in the [Environment3D](environment3d.md) module.

  

**Outdoors (global)**  
  
This global stores a True/False flag for whether or not the current zone is considered outdoors. This is used in lighting and the radar.

  

**AmbientR, AmbientG, AmbientB (globals)**  
  
These three globals store the ambient light colour for the current zone. They are set whenever [LoadArea](#FLoadArea) is called.

  

**DefaultLightPitch, DefaultLightYaw (globals)**  
  
These two globals store the orientation of the "default light", which is shown whenever the scene is not being lit by a sun. They are set whenever [LoadArea](#FLoadArea) is called. Their values are assigned to the default light in the client after a new zone is loaded in [ClientNet->UpdateNetwork](clientnet.md#UpdateNetwork).

  

**LoadingTexID (global)**  
  
This global stores the media ID of the texture used for the zone's loading screen. By default, this is 65535, meaning that a blank screen will be shown.

  

**LoadingMusicID (global)**  
  
This global stores the media ID of the music played while the zone is loading. By default, this is 65535, meaning that no music will play.

  

**MapTexID (global)**  
  
This global stores the media ID of the texture used for the map in the current zone. This is displayed when the player presses M or clicks the map button on the action bar. The texture is loaded and assigned to the map window after loading the zone in [ClientNet->UpdateNetwork](clientnet.md#UpdateNetwork).

  

**SlopeRestrict (global)**  
  
This global stores the minimum slope steepness at which actor instances are prevented from climbing. It has a range of 0.0 - 1.0 where 0.0 is a vertical cliff and 1.0 is horizontal ground. The default is 0.6 although this will be overwritten as soon as a zone is loaded.

  

* * *

  

**Remove\_Surf (type)**  
  
This type represents a mesh surface which is due for removal, and is intended only to be used internally by the [ChunkTerrain](#FChunkTerrain) function.

  

**Cluster (type)**  
  
This type represents a section of a chunked terrain, and is intended only to be used internally by the [ChunkTerrain](#FChunkTerrain) function.

  

**Scenery (type)**  
  
This type represents a normal scenery mesh within a zone. It stores all required information about the scenery object as it is used by both the client and the GE. All scenery objects are created by [LoadArea](#FLoadArea), and freed by [UnloadArea](#FUnloadArea).

  

**Water (type)**  
  
This type represents a water plane within a zone. All Water objects have a counterpart [ServerAreas->ServerWater](serverareas.md#TServerWater) object, which stores information on damage applied to characters swimming in the water. The reason for the seperation is that water damage is applied by the server rather than the client, so the server must know the location, size, and damage level of every water plane.

  

**ColBox (type)**  
  
This type represents an invisible collision box within a zone. It stores only the entity handle and the size. All collision box objects are created by [LoadArea](#FLoadArea), and freed by [UnloadArea](#FUnloadArea).

  

**Emitter (type)**  
  
This type represents a particle emitter within a zone. It is distinct from the [RottParticles->RN\_Emitter](rottparticles.md#TRN_Emitter) type, which represents any type of emitter (scripted, actor/item, or a zone emitter). An actual RN\_Emitter is present as the EN (entity) field of this type.

  

**Terrain (type)**  
  
This type represents a Blitz LOD terrain within a zone. All terrain objects are created by [LoadArea](#FLoadArea), and freed by [UnloadArea](#FUnloadArea).

  

**SoundZone (type)**  
  
This type represents an invisible sound region within a zone. All sound zone objects are created by [LoadArea](#FLoadArea), and freed by [UnloadArea](#FUnloadArea).

  

**CatchPlane (type)**  
  
This type represents an invisible horizontal finite plane which will cause all rain and snow particles falling beneath it to be destroyed. All catch plane objects are created by [LoadArea](#FLoadArea), and freed by [UnloadArea](#FUnloadArea).

  

* * *

  
  
  

**CreateSubdividedPlane(XDivs, ZDivs, UScale#, VScale#, Parent)**  
  
Return value: The handle of the new entity  
  
Parameters:  

*   _XDivs_ - The number of subdivisions along the X axis of the plane
*   _ZDivs_ - The number of subdivisions along the Z axis of the plane
*   _UScale#_ - Scaling factor for U texture coordinates (defaults to 1.0)
*   _VScale#_ - Scaling factor for V texture coordinates (defaults to 1.0)
*   _Parent_ - Optional parent entity for the new mesh (defaults to 0)

  
This function creates a new finite plane mesh with a specified number of subdivisions. It is used to create the meshes for water areas within a zone.

  
  
  

**LoadArea(Name$, CameraEN, DisplayItems, UpdateRottNet)**  
  
Return value: Success flag  
  
Parameters:  

*   _Name$_ - The name of the zone to load
*   _CameraEN_ - The handle of the camera entity being used
*   _DisplayItems_ - True/False flag for whether to create visual representations of invisible items such as sound zones and collision boxes (defaults to False)
*   _UpdateRottNet_ - True/False flag for whether to call [RottNet->RN\_Update](rottnet.md#RN_Update) during the loading process

  
This function loads all client information about a zone from disk and creates it ready for rendering. The first stage is to lock the mesh and texture databases for fast access. The zone file is then opened and the loading screen details read in. The loading screen is created and rendered. Next the general zone settings are read and stored. Zone objects are then loaded one by one, starting with scenery, then water, collision boxes, emitters, terrains and finally sound zones. During this time, RottNet is periodically updated (if the flag is set to True) to ensure that network connections do not time out while the zone is loading. The loading screen is also refreshed. Finally the databases are unlocked and the loading screen is freed.

  
  
  

**SaveArea(Name$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Name$_ - The name of the zone to save

  
This function saves all client information about a zone to disk. If saving fails, False is returned.

  
  
  

**UnloadArea()**  
  
Return value: None  
  
Parameters: None  
  
This function frees all media connected with the current zone, and deletes all zone objects from memory.

  
  
  

**SetViewDistance(CameraEN, Near#, Far#)**  
  
Return value: None  
  
Parameters:  

*   _CameraEN_ - The handle of the camera entity to set the distance for
*   _Near#_ - The minimum fog range
*   _Far#_ - The maximum fog range

  
This function sets the camera view distance, the fog distance, and scales the sky spheres to fit. The camera minimum distance is fixed, and the maximum distance is set to be just outside the maximum fog range.

  
  
  

**ChunkTerrain(Mesh, chx#, chy#, chz#, XPos#, YPos#, ZPos#)**  
  
Return value: None  
  
Parameters:  

*   _Mesh_ - The handle of the mesh to split into chunks
*   _chx#_ - The size of each chunk on the X axis (defaults to 10)
*   _chy#_ - The size of each chunk on the Y axis (defaults to 10)
*   _chz#_ - The size of each chunk on the Z axis (defaults to 10)
*   _XPos#_ - The X position for the chunked mesh (defaults to 0)
*   _YPos#_ - The Y position for the chunked mesh (defaults to 0)
*   _ZPos#_ - The Z position for the chunked mesh (defaults to 0)

  
This function splits a mesh into multiple smaller chunks for improved rendering performance. It is used mostly for terrains exported from RCTE.

  
  
  

**NearestPower(N#, Snapper#)**  
  
Return value: Number "snapped" to the nearest multiple  
  
Parameters:  

*   _N#_ - The number to snap
*   _Snapper#_ - The number to snap to a multiple of

  
This function returns a number rounded to the nearest multiple of another number. It is used only as a helper function for [ChunkTerrain](#FChunkTerrain).

  
  
  

**RemoveSurface(Ent)**  
  
Return value: Handle of the new version of the mesh  
  
Parameters:  

*   _Ent_ - The entity to remove surfaces from

  
This function removes surfaces from a mesh entity. It is used only as a helper function for [ChunkTerrain](#FChunkTerrain).