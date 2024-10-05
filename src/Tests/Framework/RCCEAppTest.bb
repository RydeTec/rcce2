Strict

Include "Modules\Framework\RCCEApp.bb"

Test testVersion()
    Local app.RCCEApp = new RCCEApp("test", ".")

    app\semMajor = 2
    app\semMinor = 12
    app\semPatch = 1
    app\preReleasePhase = "beta"
    app\preReleaseCount = 7

    Local version$ = RCCEApp::version(app)
    DebugLog(version)
    Assert(version = "2.12.1-beta.7")

    app\preReleasePhase = 0
    app\preReleaseCount = 0

    version$ = RCCEApp::version(app)
    DebugLog(version)
    Assert(version = "2.12.1")
End Test