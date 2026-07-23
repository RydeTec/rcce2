Strict
EnableGC

; Source-contract regression for the repository-owned Rust build policy. These
; files are consumed by CI and contributor shells, so test their committed
; contract without loading runtime/editor modules.

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

Function FileOccurrenceCount%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Count = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return 0
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0 Then Count = Count + 1
	Wend
	CloseFile F
	Return Count
End Function

Function FileContainsSequenceBefore%(Path$, StartNeedle$, FirstNeedle$, SecondNeedle$, EndNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0
			If Instr(Line$, StartNeedle$) > 0 Then Stage = 1
		Else
			If Stage = 1 And Instr(Line$, FirstNeedle$) > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, SecondNeedle$) > 0
				CloseFile F
				Return True
			EndIf
			If Instr(Line$, EndNeedle$) > 0
				CloseFile F
				Return False
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function FileContainsOrderedSequence10%(Path$, FirstNeedle$, SecondNeedle$, ThirdNeedle$, FourthNeedle$, FifthNeedle$, SixthNeedle$, SeventhNeedle$, EighthNeedle$, NinthNeedle$, TenthNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local Stage = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, FirstNeedle$) > 0
			Stage = 1
		ElseIf Stage = 1 And Instr(Line$, SecondNeedle$) > 0
			Stage = 2
		ElseIf Stage = 2 And Instr(Line$, ThirdNeedle$) > 0
			Stage = 3
		ElseIf Stage = 3 And Instr(Line$, FourthNeedle$) > 0
			Stage = 4
		ElseIf Stage = 4 And Instr(Line$, FifthNeedle$) > 0
			Stage = 5
		ElseIf Stage = 5 And Instr(Line$, SixthNeedle$) > 0
			Stage = 6
		ElseIf Stage = 6 And Instr(Line$, SeventhNeedle$) > 0
			Stage = 7
		ElseIf Stage = 7 And Instr(Line$, EighthNeedle$) > 0
			Stage = 8
		ElseIf Stage = 8 And Instr(Line$, NinthNeedle$) > 0
			Stage = 9
		ElseIf Stage = 9 And Instr(Line$, TenthNeedle$) > 0
			CloseFile F
			Return True
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Global OrderedSequenceTestPath$ = CurrentDir$() + "rust_toolchain_policy_ordered_sequence.tmp"

Test testOrderedSequenceRejectsMultipleStagesOnOneLine()
	If FileType(OrderedSequenceTestPath$) = 1 Then DeleteFile(OrderedSequenceTestPath$)
	Local F.BBStream = WriteFile(OrderedSequenceTestPath$)
	Assert(F <> Null)
	If F <> Null
		WriteLine F, "first second third fourth fifth sixth seventh eighth ninth tenth"
		CloseFile F
		Assert(FileContainsOrderedSequence10%(OrderedSequenceTestPath$, "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth") = False)
	EndIf
	If FileType(OrderedSequenceTestPath$) = 1 Then DeleteFile(OrderedSequenceTestPath$)
End Test

Test testDeclaredMSRVIsPinnedWithClippy()
	Assert(FileContains%("rust-toolchain.toml", "channel = " + Chr$(34) + "1.85.0" + Chr$(34)) = True)
	Assert(FileContains%("rust-toolchain.toml", "components = [" + Chr$(34) + "clippy" + Chr$(34) + "]") = True)
End Test

Test testRustCIUsesPolicyAndInvalidatesCachesWhenItChanges()
	; The explicit 1.85.0 setup steps stay pinned to their reviewed action
	; revision; the step names below retain the human-readable Rust policy.
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: dtolnay/rust-toolchain@98effd2fc0b766278e30ab86762dd5e9a8531399") = 2)
	Assert(FileContains%(".github\workflows\ci.yml", "- name: Report Rust toolchain (client)") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "- name: Report Rust toolchain (server)") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "key: cargo-${{ runner.os }}-${{ hashFiles('client-rs/Cargo.lock', 'rust-toolchain.toml') }}") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "key: cargo-server-${{ runner.os }}-${{ hashFiles('server-rs/Cargo.lock', 'rust-toolchain.toml') }}") = True)
End Test

Test testOptInReleaseBuildsKeepBothLockfilesLocked()
	Assert(FileContains%("compile.sh", "cargo build --release --locked -p rcce-client --bin client-window") = True)
	Assert(FileContains%("compile.sh", "cargo build --release --locked --bin rcce-server") = True)
	Assert(FileContains%("compile.bat", "cargo build --release --locked -p rcce-client --bin client-window") = True)
	Assert(FileContains%("compile.bat", "cargo build --release --locked --bin rcce-server") = True)
End Test

Test testExplicitRustBuildsRejectMissingCargo()
	Assert(FileContains%("compile.sh", "Skipping ClientRS/ServerRS.") = False)
	Assert(FileContains%("compile.bat", "Skipping ClientRS.exe/ServerRS.exe.") = False)
	Assert(FileContainsSequenceBefore%("compile.sh", "if ! command -v cargo >/dev/null 2>&1; then", "Cannot build ClientRS/ServerRS.", "exit 1", "  else") = True)
	Assert(FileContainsSequenceBefore%("compile.bat", "if errorlevel 1 (", "Cannot build ClientRS.exe/ServerRS.exe.", "exit /b 1", ")") = True)
End Test

Test testWindowsCIExercisesRustReleasePackaging()
	Assert(FileContainsSequenceBefore%(".github\workflows\ci.yml", "- name: Package Rust apps for Windows", "call compile.bat -e -t -r", "if errorlevel 1 exit /b %errorlevel%", "- name: Run Rust client tests (logic crates)") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "if not exist bin\ClientRS.exe (") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "if not exist bin\ServerRS.exe (") = True)
End Test

Test testWindowsRustOnlyBuildCreatesOutputDirectoryBeforeCopies()
	Local BinDirectory$ = "if not exist " + Chr$(34) + "%ROOTDIR%\bin" + Chr$(34) + " mkdir " + Chr$(34) + "%ROOTDIR%\bin" + Chr$(34)
	Assert(FileContainsSequenceBefore%("compile.bat", "if not %BUILD_RUST%==1 goto skip_rust", BinDirectory$, "copy /Y " + Chr$(34) + "%ROOTDIR%\client-rs\target\release\client-window.exe" + Chr$(34), ":skip_rust") = True)
	Assert(FileContainsSequenceBefore%("compile.bat", "if not %BUILD_RUST%==1 goto skip_rust", BinDirectory$, "copy /Y " + Chr$(34) + "%ROOTDIR%\server-rs\target\release\rcce-server.exe" + Chr$(34), ":skip_rust") = True)
End Test

Test testLinuxCIExercisesRustReleasePackaging()
	Assert(FileContainsOrderedSequence10%(".github\workflows\ci.yml", "- name: Install Linux audio build dependency", "sudo apt-get install --yes libasound2-dev", "- name: Package Rust apps for Linux", "./compile.sh -e -t -r", "test -x bin/ClientRS", "test -x bin/ServerRS", "- name: Build + test (server workspace, locked)", "- name: Build Rust server Docker image", "- name: Smoke-test Rust server Docker startup", "- name: Clippy (server workspace, -D warnings)") = True)
End Test

Test testRustServerContainerBuilderKeepsDeclaredToolchainAndLockfile()
	Assert(FileContains%("server-rs\Dockerfile", "FROM rust:1.85.0-bookworm AS build") = True)
	Assert(FileContains%("server-rs\Dockerfile", "RUN cargo build --release --locked --bin rcce-server") = True)
	Assert(FileContains%("server-rs\README.md", "cargo build --release --locked") = True)
	Assert(FileContains%("server-rs\README.md", "cargo test --workspace --locked") = True)
	Assert(FileContains%("server-rs\README.md", "cargo clippy --workspace --all-targets --locked -- -D warnings") = True)
End Test

Test testRustServerCIBuildsContainerImage()
	Assert(FileContains%(".github\workflows\ci.yml", "- name: Build Rust server Docker image") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "docker build -f server-rs/Dockerfile -t rcce-server .") = True)
End Test

Test testRustServerCISmokeTestsContainerStartup()
	Assert(FileContains%(".github\workflows\ci.yml", "- name: Smoke-test Rust server Docker startup") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "docker run --detach --name " + Chr$(34) + "$container_name" + Chr$(34) + " -p 25000:25000/udp -v " + Chr$(34) + "$GITHUB_WORKSPACE/data:/data:ro" + Chr$(34) + " rcce-server") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "Listening on UDP 25000.") = True)
	Assert(FileContains%(".github\workflows\ci.yml", "docker logs " + Chr$(34) + "$container_name" + Chr$(34)) = True)
	Assert(FileContains%(".github\workflows\ci.yml", "trap cleanup EXIT") = True)
	Assert(FileContains%("docs\rust-server\ACCEPTANCE.md", "Smoke-test Rust server Docker startup") = True)
End Test
