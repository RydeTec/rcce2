# Update channel hardening

The client's auto-update flow has historically given the server unconditional
write access to any path on the client machine. Round 3's audit surfaced this
as the highest-severity finding across all three rounds: a compromised or
malicious server, or any network-positioned attacker (the channel is plain
HTTP), could replace the running game .exe on every connecting client.

`security/update-channel-rce` (Track L) closes the immediate primitives — path
traversal, post-download tamper, and parse-length corruption. This document
captures the work that's **still needed** to close the channel properly. None
of it can be done client-side alone; each item needs a coordinated server-side
change.

## What's fixed in Track L

- **Path containment.** `UpdateFileNameIsSafe%` rejects empty names, names
  longer than 240 chars, names starting with `\` or `/`, names with `:` at
  position 2 (drive letter), names containing `..` anywhere, and names with
  control bytes (`<32` or `127`). Applied at packet parse and at the apply
  loop's CreateDir step.

- **Post-download checksum re-verification.** The apply loop now re-runs
  `CountChecksum` on the decompressed payload and refuses to apply if it
  doesn't match the announced checksum. Without this, a corrupt download
  silently overwrote the local file (the prior code only checksummed the
  *existing* local file to decide whether to download).

- **`RequiredFiles` length parsing.** Was `If Len(Pa$) = Offset + 1` (reads
  2 bytes when 1 remained, off-end-of-string corrupting the count). Changed
  to `>= Offset + 1`.

## What still needs to happen

### 1. Replace the additive checksum with a real cryptographic hash

`CountChecksum` is a 32-bit additive `Sum(ReadInt)` — trivially forgeable.
Any byte permutation that preserves the sum passes. To replace:

- **Server side**: when generating the manifest, compute SHA-256 of each
  update payload and include it in the `P_FetchUpdateFiles` reply alongside
  (or instead of) the 4-byte checksum field.
- **Client side**: change the `U\Checksum` field to a 32-byte string, update
  the parse offsets, and replace `CountChecksum` with a SHA-256 implementation
  (or wrap a userlib).
- **Wire-format bump**: bump the protocol version and reject older servers.

### 2. Sign the manifest itself

Even with a real hash, the *manifest* (the list of filenames + hashes the
server announces) is still untrusted. A signature over the manifest using a
public key pinned in the client binary closes the MITM hole even on plaintext
HTTP. Sketch:

- Generate an Ed25519 or RSA-2048 keypair. Publish the public key in
  `src/Modules/MainMenu.bb` (or, better, a header file the build embeds).
- Server signs the manifest blob with the private key, sends signature
  alongside the manifest.
- Client verifies signature before treating any entry as authoritative.

### 3. Move the update channel to HTTPS

The current HTTP fetch at `OpenTCPStream(WebHost$, 80)` can be sniffed and
tampered. Item 2 (manifest signing) prevents tampering even on HTTP, but
HTTPS adds confidentiality. Either is acceptable; both is best.

Blitz3D doesn't ship TLS; options:
- Wrap a userlib (`bb_https.dll` / similar) that bridges to Schannel/WinHTTP.
- Route updates through a small launcher process that handles TLS and writes
  to a known temp dir the game then verifies.
- Sign-only (item 2) and accept the cleartext exposure as a non-goal.

### 4. Remove `?LIST` from `UpdateServer.php` or gate behind auth

`src/UpdateServer.php` happily returns `opendir(".")` for any request with
`?LIST` set, no auth. Any file dropped in the update directory (`.env`,
backups, server-side data files mis-placed) is remotely enumerable and
downloadable. Either delete the `?LIST` path entirely or require an
authentication token.

### 5. Gate `ExecFile("Data\Patch.exe " + GameName$)` behind signature

Track L's path-containment refuses arbitrary write paths, but if `U\Name$`
legitimately matches the game .exe, the client still ExecFile's `Patch.exe`
with the new binary as argument — and `Patch.exe` is itself a downloaded
artifact under the same flawed channel. After item 2 (signed manifest) lands,
also require the manifest entry for the .exe to carry a separate
"executable-authorisation" flag the server cannot fabricate without the
signing key.

## Order of operations

1. Track L (this PR) — immediate path containment + post-download hash check
2. Build a signed-manifest format on the server, ship a version-bumped client
   that parses it. Keep the old format readable behind a deprecation flag.
3. Once enough deployments have upgraded, remove the old format path entirely.
4. Optionally migrate the HTTP transport to HTTPS or a launcher-bridged
   download path.

Tracking item 1 is closed by `security/update-channel-rce`. Items 2-5 are
outside this PR's scope.
