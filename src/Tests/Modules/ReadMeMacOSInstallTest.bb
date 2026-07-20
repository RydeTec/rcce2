Strict
EnableGC

; Source-contract regression for the public macOS install path. The README is
; the onboarding contract here, so read its bounded wording rather than pulling
; any runtime module into this focused test.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testWindowsReleaseInstructionsDoNotPromiseAnOSAgnosticBuild()
	Assert(FileContains%("ReadMe.md", "### Install a release (Windows)") = True)
	Assert(FileContains%("ReadMe.md", "Grab the latest build for your OS") = False)
	Assert(FileContains%("ReadMe.md", "Project Manager` on macOS") = False)
End Test

Test testMacOSInstructionsPointToTheSupportedSourceBuildPath()
	Assert(FileContains%("ReadMe.md", "### macOS (Apple Silicon, alpha)") = True)
	Assert(FileContains%("ReadMe.md", "macOS currently has no downloadable release package") = True)
	Assert(FileContains%("ReadMe.md", "[macOS Apple Silicon notes](docs/macos-apple-silicon.md)") = True)
End Test

Test testSourceBuildOnboardingDoesNotAdvertiseTheMacOSBootstrapForLinux()
	Assert(FileContains%("docs/start.md", "### macOS (Apple Silicon, alpha)") = True)
	Assert(FileContains%("docs/start.md", "### macOS / Linux") = False)
	Assert(FileContains%("docs/start.md", "bootstrap_macos.sh` is required on macOS") = True)
	Assert(FileContains%("CONTRIBUTING.md", "# macOS (Apple Silicon, alpha)") = True)
	Assert(FileContains%("CONTRIBUTING.md", "# macOS / Linux (alpha)") = False)
	Assert(FileContains%("CONTRIBUTING.md", "macOS equivalent") = True)
End Test

Test testSourceBuildOnboardingDocumentsOptionalRustTargets()
	Assert(FileContains%("ReadMe.md", "compile.bat -r") = True)
	Assert(FileContains%("ReadMe.md", "bin\ClientRS.exe") = True)
	Assert(FileContains%("ReadMe.md", "bin\ServerRS.exe") = True)
	Assert(FileContains%("ReadMe.md", "optional Rust client and server") = True)
	Assert(FileContains%("ReadMe.md", "Rust build is required") = False)
	Assert(FileContains%("ReadMe.md", "only build the Rust server") = False)
	Assert(FileContains%("docs/start.md", "compile.bat -r") = True)
	Assert(FileContains%("docs/start.md", "./compile.sh -r") = True)
	Assert(FileContains%("docs/start.md", "bin/ClientRS") = True)
	Assert(FileContains%("docs/start.md", "bin/ServerRS") = True)
	Assert(FileContains%("docs/start.md", "optional Rust client and server") = True)
	Assert(FileContains%("docs/start.md", "Rust build is required") = False)
	Assert(FileContains%("docs/start.md", "only build the Rust server") = False)
End Test

Test testContributorGeneratedDocsGuidanceMatchesCI()
	Assert(FileContains%("CONTRIBUTING.md", "./scripts/gen_packet_index.sh") = True)
	Assert(FileContains%("CONTRIBUTING.md", "./scripts/gen_bvm_reference.sh") = True)
	Assert(FileContains%("CONTRIBUTING.md", "Both checks run in the GitHub Actions workflow") = True)
	Assert(FileContains%("CONTRIBUTING.md", "neither is yet wired into the GitHub Actions workflow") = False)
	Assert(FileContains%(".github\workflows\ci.yml", "bash scripts/gen_packet_index.sh --check") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "bash scripts/gen_bvm_reference.sh --check") = True)
End Test

Test testPublicSourceBuildOnboardingListsLoomAsShippedBeta()
	Assert(FileContains%("ReadMe.md", "src/Loom.bb") = True)
	Assert(FileContains%("ReadMe.md", "bin/Loom(.exe)") = True)
	Assert(FileContains%("ReadMe.md", "Loom (Beta)") = True)
	Assert(FileContains%("ReadMe.md", "Launch Loom (Beta) from Project Manager") = True)
	Assert(FileContains%("docs/start.md", "src/Loom.bb") = True)
	Assert(FileContains%("docs/start.md", "bin/Loom(.exe)") = True)
	Assert(FileContains%("docs/start.md", "Loom (Beta)") = True)
	Assert(FileContains%("docs/start.md", "Loom (Beta), open Project Manager") = True)
	Assert(FileContains%("ReadMe.md", "Loom replacement for GUE") = False)
	Assert(FileContains%("docs/start.md", "Loom replacement for GUE") = False)
End Test

Test testDocumentationLandingLinksLoomAsShippedBeta()
	Assert(FileContains%("docs/index.md", "[`loom/README.md`](loom/README.md) - Loom (Beta) editor guide") = True)
	Assert(FileContains%("docs/index.md", "Client, Server, GUE, Loom (Beta), and Project Manager") = True)
	Assert(FileContains%("docs/index.md", "Loom replacement for GUE") = False)
End Test
