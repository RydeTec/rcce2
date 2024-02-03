<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Animations.bb**

This module contains the following constants:  

*   [Anim\_...](#CAnim)

This module contains the following globals:  

*   [AnimList.AnimSet(999)](#GAnimList)

This module contains the following types:  

*   [AnimSet](#TAnimSet)

This module contains the following functions:  

*   [PlayAnimation](#FPlayAnimation)
*   [CreateAnimSet](#FCreateAnimSet)
*   [LoadAnimSets](#FLoadAnimSets)
*   [SaveAnimSets](#FSaveAnimSets)
*   [FindAnimation](#FFindAnimation)
*   [CurrentSeq](#FCurrentSeq)

  

* * *

  

**Anim\_... (constant)**  
  
This list of constants specifies the animation numbers within a set for all the hard coded animations required by the engine.

  

* * *

  

**AnimList.AnimSet(999) (global)**  
  
This global array indexes every AnimSet object, with the array index being the ID for that object. It thus provides fast non-sequential access to any AnimSet object.

  

* * *

  

**AnimSet (type)**  
  
This type represents a set of animations, which can be used by one or more actors. It stores the set name, ID, and the name, start frame, end frame and speed for 150 different animations.

  

* * *

  
  
  

**PlayAnimation(AI.ActorInstance, Mode, Speed#, Seq, FixedSpeed)**  
  
Return value: None  
  
Parameters:  

*   _AI.ActorInstance_ - The actor instance to animate
*   _Mode_ - The animation mode to use (see the Blitz documentation for the Animate() command)
*   _Speed#_ - The speed at which to play the animation
*   _Seq_ - The ID of the animation to play, from 0 to 149
*   _FixedSpeed_ - Flag for whether to play the animation at a fixed speed regardless of length (default True)

  
This function makes a character perform any animation present in its animation set. If the specified animation number has no end frame set, it is considered not to exist and the function will not continue. The FixedSpeed parameter is used to ensure that an animation always takes a fixed amount of time to play, even if its length in frames differs between animation sets. To do this the speed is multiplied by the animation length, as long as the length is at least 1 frame.

  
  
  

**CreateAnimSet()**  
  
Return value: ID of new AnimSet  
  
Parameters: None  
  
This function creates a new, blank animation set and returns the ID number. If no more IDs are available, -1 is returned. It finds an ID, creates the set and adds it to the list, sets the default hard coded animations, and finally assigns each animation a default speed of 1.0. This function is not called from anywhere in the client or server and is intended for use in editors only.

  
  
  

**LoadAnimSets(Filename$)**  
  
Return value: Total number of sets loaded  
  
Parameters:  

*   _Filename$_ - The path/name of the file from which to load AnimSets

  
This function loads a complete collection of AnimSets from file. It should not be called when sets are already loaded as this would cause a memory leak. If the file is not found, -1 is returned.

  
  
  

**SaveAnimSets(Filename$)**  
  
Return value: Success flag  
  
Parameters:  

*   _Filename$_ - The path/name of the file to which AnimSets will be written

  
This function saves all AnimSets to a file. If the file is not found, False is returned, or if the function completes successfully it will return True.

  
  
  

**FindAnimation(A.AnimSet, AnimName$)**  
  
Return value: Animation number  
  
Parameters:  

*   _A.AnimSet_ - AnimSet object to search through
*   _AnimName$_ - Name of the animation to find

  
This function searches an AnimSet for thee first animation with a specified name. If the specified animation is not found within the set, -1 is returned. The search is not case sensitive.

  
  
  

**CurrentSeq(AI.ActorInstance)**  
  
Return value: Animation number  
  
Parameters:  

*   _AI.ActorInstance_ - ActorInstance to find the current sequence for

  
This function finds which animation sequence an actor is currently running, if any. It searches in reverse since the most commonly used animations (the hard coded ones) occupy the higher numbers.