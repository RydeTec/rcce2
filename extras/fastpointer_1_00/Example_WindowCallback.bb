;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;

;
; WARNING! DON'T USE 'pointer' functions for window-callbacks, it's incorrect for Blitz3D
; Blitz3D generate window messages in self code and your callback goto infinite loop (stack overflow and MAV)!
;