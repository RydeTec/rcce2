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

Test testDeclaredMSRVIsPinnedWithClippy()
	Assert(FileContains%("rust-toolchain.toml", "channel = " + Chr$(34) + "1.85.0" + Chr$(34)) = True)
	Assert(FileContains%("rust-toolchain.toml", "components = [" + Chr$(34) + "clippy" + Chr$(34) + "]") = True)
End Test

Test testRustCIUsesPolicyAndInvalidatesCachesWhenItChanges()
	Assert(FileOccurrenceCount%(".github\workflows\ci.yml", "uses: dtolnay/rust-toolchain@1.85.0") = 2)
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
