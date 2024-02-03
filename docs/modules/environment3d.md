<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Environment3D.bb**

This module contains the following globals:  

*   [CurrentWeather](#GCurrentWeather)
*   [RainEmitter, SnowEmitter](#GRainEmitter)
*   [FogNearDest#, FogFarDest#](#GFogNearDest)
*   [FogNearNow#, FogFarNow#](#GFogNearNow)
*   [SkyAlpha#, StarsAlpha#, CloudAlpha#](#GSkyAlpha)
*   [SkyChange](#GSkyChange)
*   [CloudChange](#GCloudChange)
*   [FogChange](#GFogChange)
*   [LightningToDo](#GLightningToDo)
*   [Snd\_Rain, Snd\_Wind](#GSndRain)
*   [SndC\_Rain, SndC\_Wind](#GSndCRain)
*   [Snd\_Thunder(2)](#GSndThunder)
*   [CameraUnderwater](#GCameraUnderwater)
*   [TotalFlares](#GTotalFlares)
*   [Flares(10)](#GFlares)
*   [LensFlareSize(10)](#GLensFlareSize)

This module contains the following types:  

*   [ScriptedEmitter](#TScriptedEmitter)
*   [Light](#TLight)

This module contains the following functions:  

*   [CreateSuns](#FCreateSuns)
*   [LoadWeather3D](#FLoadWeather3D)
*   [SetWeather](#FSetWeather)
*   [UpdateEnvironment3D](#FUpdateEnvironment3D)
*   [UpdateLensFlare](#FUpdateLensFlare)
*   [UpdateLights](#FUpdateLights)
*   [RemoveUnderwaterParticles](#FRemoveUnderwaterParticles)

  

* * *

  

**CurrentWeather (global)**  
  
This global stores the current weather mode as defined in the [Environment->W\_...](environment.md#CW) constants.

  

**RainEmitter, SnowEmitter (globals)**  
  
These globals store the entity handles for the rain and snow weather emitters. They are loaded in the [LoadWeather3D](#FLoadWeather3D) function.

  

**FogNearDest#, FogFarDest# (globals)**  
  
These globals store the destination fog ranges to which the current fog transition is heading.

  

**FogNearNow#, FogFarNow# (globals)**  
  
These globals store the current fog ranges of the current fog transition.

  

**SkyAlpha#, StarsAlpha#, CloudAlpha# (globals)**  
  
These globals store the current alpha values of the sky spheres as part of a sky transition (e.g. fading the sky sphere in, or fading between sky and stars).

  

**SkyChange (global)**  
  
This global stores the current mode for the transition of the sky/star spheres. If the value is 0, no transition is in progress. If the value is -1, the sky is fading in and the stars out. If the value is 1, the stars are fading in and the sky out. If 2, the appropriate sky for the current time of day (whether sky or stars) is fading out. If the value is 3, the appropriate sky is fading in.

  

**CloudChange (global)**  
  
This global stores the current mode for the transition of the cloud sphere. If the value is 0, no transition is in progress. If the value is 1, the cloud sphere is fading to an alpha of 0.5 (partly transparent but visible). If 2, the clouds are fading in fully. If 3, the clouds are fading out fully.

  

**FogChange (global)**  
  
This global stores the current mode for the transition of fog. It can either be True or False. If True, the fog range will move towards the values in the [FogNearDest#, FogFarDest#](#GFogNearDest) globals.

  

**LightningToDo (global)**  
  
This global stores the number of lightning flashes currently queued up for display. It is set to a random number between 1 and 3 when a lightning display is initiated.

  

**Snd\_Rain, Snd\_Wind (globals)**  
  
These globals store the sound handles for the rain and wind sounds. They are loaded in the [LoadWeather3D](#FLoadWeather3D) function.

  

**SndC\_Rain, SndC\_Wind (globals)**  
  
These globals store the sound handles for the rain and wind sound channels. They are assigned a value whenever the sounds are played, and are kept so that it can be known whether the sounds are still playing or not, and so that the sounds can be stopped when required.

  

**Snd\_Thunder(2) (global)**  
  
This global stores the sound handles for the three thunder sounds. They are loaded in the [LoadWeather3D](#FLoadWeather3D) function. Three are used for variety, and When played one is picked at random.

  

**CameraUnderwater (global)**  
  
This global holds a True/False flag for whether the camera is currently underwater. It is used to ensure the correct fog is displayed.

  

**TotalFlares (global)**  
  
This global stores the number of flare textures loaded by the [CreateSuns](#FCreateSuns) function, up to a maximum of 10.

  

**Flares(10) (global)**  
  
This global stores the texture handles of up to 10 lens flare elements.

  

**LensFlareSize(10) (global)**  
  
This global stores the scaling factors for each possible lens flare element. All ten values are currently hard coded.  

* * *

  

**ScriptedEmitter (type)**  
  
This type represents a temporary emitter created by a script. It stores the [RottParticles->RP\_Emitter](rottparticles.md#TRP_Emitter) entity, the length of time for which the emitter is active, the time it was created at, and a True/False flag for whether the emitter is attached to an actor instance.

  

**Light (type)**  
  
This type represents a dynamic (DirectX) light, usually directional. Most of these are attached to a sun, the except being the default light. These lights can be coloured (even negatively) but do not cast shadows. The [UpdateLights](#FUpdateLights) function allows them to fade in and out smoothly.

  

* * *

  
  
  

**CreateSuns()**  
  
Return value: None  
  
Parameters: None  
  
This function loads all available lens flare textures, then loops through every [Environment->Sun](environment.md#TSun) object and for each one creates the 3D mesh, the [Light](#TLight) object and the lens flare element meshes. It is called by the [ClientLoaders->LoadGame](clientloaders.md#FLoadGame) function.

  
  
  

**LoadWeather3D()**  
  
Return value: None  
  
Parameters: None  
  
This function loads all media required for weather effects -- the textures and emitters for rain and snow particles, and the wind, rain and thunder sound effects.

  
  
  

**SetWeather(Num)**  
  
Return value: None  
  
Parameters:  

*   _Num_ - The weather type to set

  
This function changes the current weather to one of the types defined in by the [Environment->W\_...](environment.md#CW) constants. First everything is set to default values -- the rain and snow emitters hidden, and the camera fog and background colours set to the zone's default. Then new settings for the selected weather type are applied. Finally the [FogChange](#GFogChange) global variable is set to False if the camera is underwater, to ensure that the "underwater fog" doesn't get overwritten with any new settings.

  
  
  

**UpdateEnvironment3D()**  
  
Return value: None  
  
Parameters: None  
  
This function updates all the parts of the environment which are displayed to the player. First scripted emitters are updated and removed if their time has expired. Water textures are scrolled to create an impression of movement. The sky spheres are kept fixed at the camera position, and the cloud sphere slowly rotated for movement. The rain and snow emitters are updated and any underwater particles removed. If the current weather is stormy, lightning is randomly displayed. Each lightning flash actually has between 1 and 3 consecutive flashes displayed at random intervals, each accompanied by one of the 3 thunder sounds. If the fog is currently undergoing a transition, the current fog ranges are updated. The same applies to the visibility of the cloud sphere. Next is the day/night cycle, which is updated each time the current hour of the day changes (unless it's snowing or foggy, in which case the sky and suns are invisible anyway). If the current hour of the day is the dawn or dusk hour, the visible sky sphere fades between stars and sky, or vice versa. Next the suns are updated, which involves updating the position of each one currently visible in the sky. Each visible sun's light object is also updated to be visible in in the correct position. These lights are pointed towards the player. The final section of this function shows or hides the default light depending on whether at least one of the suns is visible in the sky. This gives shading to scenes when not lit by a sun, such as indoors scenes. When in an indoors zone, suns are visible (so they can be seen from windows, skylights etc.) but they do not cast any light.

  
  
  

**UpdateLights()**  
  
Return value: None  
  
Parameters: None  
  
This function loops through every Light object and interpolates its colour towards a destination colour if required, to create smooth transitions when lights are shown or hidden.

  
  
  

**RemoveUnderwaterParticles(E)**  
  
Return value: None  
  
Parameters:  

*   _E_ - The emitter from which particles should be removed

  
This function loops through every [RottParticles->RP\_Particle](rottparticles.md#TRP_Particle) belonging to a given emitter, and removes them if they are within the boundaries of any water area. This is used to prevent rain or snow from appearing underwater. It also does the same thing for each [ClientAreas->CatchPlane](clientareas.md#TCatchPlane), making it possible to prevent rain or snow from appearing within buildings or beneath shelters.