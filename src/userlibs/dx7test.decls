.lib "dx7test.dll"

SetSystemProperties%(d3d,dev7,draw7,hwnd,instance):"_SetSystemProperties@20"

SetRenderState%(renderstate,param):"_SetRenderState@8"
GetRenderState%(renderstate):"_GetRenderState@4"

SetMipmapLODBias%(bias#,index):"_SetMipmapLODBias@8"
GetMipmapLODBias#(index):"_GetMipmapLODBias@4"

; *WORKING*
GetTextureStageState%(stage,index):"_GetTextureStageState@8"
SetTextureStageState%(stage,index,value):"_SetTextureStageState@12"

; For stage states that require Floats *WORKING*
GetTextureStageStateF#(stage,index):"_GetTextureStageStateF@8"
SetTextureStageStateF%(stage,index,value#):"_SetTextureStageStateF@12"


