<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Environment.bb**

This module contains the following constants:  

*   [W\_...](#CW)

This module contains the following globals:  

*   [SeasonName$(11)](#GSeasonName)
*   [SeasonStartDay(11)](#GSeasonStartDay)
*   [SeasonDuskH(11)](#GSeasonDuskH)
*   [SeasonDawnH(11)](#GSeasonDawnH)
*   [MonthName$(19)](#GMonthName)
*   [MonthStartDay(19)](#GMonthStartDay)
*   [CurrentSeason](#GCurrentSeason)
*   [Year](#GYear)
*   [Day](#GDay)
*   [TimeH](#GTimeH)
*   [TimeM](#GTimeM)
*   [TimeFactor](#GTimeFactor)
*   [TimeUpdate](#GTimeUpdate)
*   [HourChanged](#GHourChanged)
*   [MinuteChanged](#GMinuteChanged)

This module contains the following types:  

*   [Sun](#TSun)

This module contains the following functions:  

*   [LoadEnvironment](#FLoadEnvironment)
*   [SaveEnvironment](#FSaveEnvironment)
*   [GetSeason](#FGetSeason)
*   [GetMonth](#FGetMonth)
*   [UpdateEnvironment](#FUpdateEnvironment)
*   [LoadSuns](#FLoadSuns)
*   [SaveSuns](#FSaveSuns)
*   [TimeDelta](#FTimeDelta)

  

* * *

  

**W\_... (constant)**  
  
This list of constants specifies the different weather types built into the engine.

  

* * *

  

**SeasonName$(11) (global)**  
  
This global array stores the names of up to 12 seasons.

  

**SeasonStartDay(11) (global)**  
  
This global array stores the day of the year for the start of up to 12 seasons.

  

**SeasonDuskH(11) (global)**  
  
This global array stores the hour of dusk for up to 12 seasons.

  

**SeasonDawnH(11) (global)**  
  
This global array stores the hour of dawn for up to 12 seasons.

**MonthName$(19) (global)**  
  
This global array stores the names of up to 20 months.

  

**MonthStartDay(19) (global)**  
  
This global array stores the day of the year for the start of up to 20 months.

  

**CurrentSeason (global)**  
  
This global variable stores the number of the current game season, numbered 0 to 11.

  

**Year (global)**  
  
This global variable stores the current game year.

  

**Day (global)**  
  
This global variable stores the current game day of the year (starting from 0).

  

**TimeH (global)**  
  
This global variable stores the hour of the current game time of day. The clock is a 24 hour one so values range from 0 to 23.

  

**TimeM (global)**  
  
This global variable stores the minute of the current game time of day. Values range from 0 to 59.

  

**TimeFactor (global)**  
  
This global variable stores the speed at which time passes in the game. A factor of 10 would mean that time progresses in the game 10x faster than real time, i.e. 1 minute of real time equals 6 seconds of game time.

  

**TimeUpdate (global)**  
  
This global variable stores the system time in milliseconds (from the Blitz Millisecs command) at which the game time was last updated. This is used by the [UpdateEnvironment](#FUpdateEnvironment) function for knowing when to increment the game time by a minute.

  

**HourChanged (global)**  
  
This global variable contains either True or False depending on whether the hour component of the game time changed during the last call to [UpdateEnvironment](#FUpdateEnvironment).

  

**MinuteChanged (global)**  
  
This global variable contains either True or False depending on whether the minute component of the game time changed during the last call to [UpdateEnvironment](#FUpdateEnvironment).

  

* * *

  

**Sun (type)**  
  
This type represents a sun (or a moon -- all such objects are referred to as suns in the code). It stores an entity handle for the sun itself, another for a light, another 10 for lens flare meshes, the media ID of the sun texture, a size, the colour of the light, and the path the sun takes through the sky. This is described by a start (rise) time, an end (set) time, and a yaw angle.

  

* * *

  
  
  

**LoadEnvironment()**  
  
Return value: Success flag  
  
Parameters: None  
  
This function loads all environment settings from the Environment.dat file, then calculates the current season and sets an initial value for [TimeUpdate](#GTimeUpdate). If loading fails, False is returned.

  
  
  

**SaveEnvironment(FullSave)**  
  
Return value: Success flag  
  
Parameters:  

*   _FullSave_ - Flag for whether to include all settings in the save (defaults to False)

  
This function saves environment settings to the Environment.dat file. This is split into two parts. The first is the current game year, day, and time. The second part, which is only saved if the FullSave parameter is True, includes all environment settings which do not change while the server runs, and are usually altered only by an editor such as GE. Thus the FullSave parameter is really only intended for use with editors. If saving fails, False is returned.

  
  
  

**GetSeason()**  
  
Return value: The number of the current game season  
  
Parameters: None  
  
This function calculates and returns the current game season (numbered 0 to 11). Unless the game day is being changed outside of [UpdateEnvironment](#FUpdateEnvironment), it is faster to use the [CurrentSeason](#GCurrentSeason) global variable instead, which is updated whenever the season changes in the UpdateEnvironment function.

  
  
  

**GetMonth()**  
  
Return value: The number of the current game month  
  
Parameters: None  
  
This function calculates and returns the current game month (numbered 0 to 19).

  
  
  

**UpdateEnvironment()**  
  
Return value: None  
  
Parameters: None  
  
This function updates the game time, day and year based on elapsed time since it was last called. It is called each frame from the server's main loop.

  
  
  

**LoadSuns()**  
  
Return value: Success flag  
  
Parameters: None  
  
This function loads all suns and their settings from the Suns.dat file. If loading fails, False is returned.

  
  
  

**SaveSuns()**  
  
Return value: Success flag  
  
Parameters: None  
  
This function saves all suns and their settings to the Suns.dat file. If loading fails, False is returned.

  
  
  

**TimeDelta(StartH, StartM, EndH, EndM)**  
  
Return value: Difference in minutes between the two specified times  
  
Parameters:  

*   _StartH_ - Hour component of start time
*   _StartM_ - Minute component of start time
*   _EndH_ - Hour component of end time
*   _EndM_ - Minute component of end time

  
This function returns the difference in minutes between two 24 hour times. It includes wrapping around midnight, so if the start time is 23:30 and the end time 00:45, this function will return 75. Its main use is calculating the positions of suns in the [Environment3D](environment3d.md) module.