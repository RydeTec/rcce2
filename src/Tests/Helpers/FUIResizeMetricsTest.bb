Strict
EnableGC

Include "Modules\Helpers\FUIResizeMetrics.bb"

Test testFUIWindowAspectTracksWindowRatio()
	Local aspect# = FUI_WindowAspect#(1600, 900)
	Assert(aspect > 0.56 And aspect < 0.57)
End Test

Test testFUIWindowScaleTracksWindowWidth()
	Local scale# = FUI_WindowScale#(1280)
	Assert(scale > 0.0015 And scale < 0.0016)
End Test

Test testFUIWindowMetricsGuardZeroWidth()
	Assert(FUI_WindowAspect#(0, 900) = 0.0)
	Assert(FUI_WindowScale#(0) = 0.0)
End Test
