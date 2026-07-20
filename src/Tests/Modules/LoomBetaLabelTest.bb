Strict
EnableGC

; Source-contract regression for Loom's current-facing beta identity. Loom and
; Project Manager pull the full editor/UI graph, so this focused test reads the
; bounded labels instead of including either executable entry point.

Function FileContains%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	If F = Null Then F = ReadFile("..\\" + Path$)
	If F = Null Then F = ReadFile("..\\..\\" + Path$)
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

Test testLoomLauncherAndWindowIdentifyTheShippedBeta()
	Assert(FileContains%("Project Manager.bb", "Loom (Beta)") = True)
	Assert(FileContains%("Loom.bb", "AppTitle(" + Chr$(34) + "Loom -- World Editor (Beta) -- Realm Crafter ") = True)
End Test

Test testLoomGuidanceMatchesTheBetaLauncher()
	Assert(FileContains%("docs\\loom\\README.md", "**Loom (Beta)**") = True)
	Assert(FileContains%("CLAUDE.md", "## Loom (beta redesigned editor)") = True)
End Test

Test testCurrentFacingGuidanceCannotRegressToAlpha()
	Assert(FileContains%("Project Manager.bb", "Loom (Alpha)") = False)
	Assert(FileContains%("Loom.bb", "AppTitle(" + Chr$(34) + "Loom -- World Editor (Alpha) -- Realm Crafter ") = False)
	Assert(FileContains%("Loom.bb", "Read-only in this alpha") = False)
	Assert(FileContains%("docs\\loom\\README.md", "**Loom (Alpha)**") = False)
	Assert(FileContains%("CLAUDE.md", "## Loom (alpha redesigned editor)") = False)
	Assert(FileContains%("CLAUDE.md", "Read-only in the alpha; editing is a beta concern") = False)
End Test

Test testLoomGuidanceIdentifiesTheShippedWorldMode()
	Assert(FileContains%("docs\\loom\\README.md", "World mode renders the focused zone's real terrain, scenery, and water") = True)
	Assert(FileContains%("docs\\loom\\README.md", "falls back to schematic mode when a zone has no visual `.dat`") = True)
	Assert(FileContains%("docs\\loom\\README.md", "Terrain sculpting, water-volume editing, weather/environment editing, or scenery texture painting.") = True)
	Assert(FileContains%("docs\\loom\\architecture.md", "Loom can render the 3D zone mesh in World mode") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "Out-of-scope items (walk-in playtest and multi-cursor) remain in the deferred list below") = True)
	Assert(FileContains%("docs\\loom\\README.md", "Render zones in 3D.") = False)
	Assert(FileContains%("docs\\loom\\architecture.md", "Loom cannot render the 3D zone mesh.") = False)
	Assert(FileContains%("docs\\loom\\roadmap.md", "Out-of-scope items (3D viewport, walk-in playtest, multi-cursor)") = False)
End Test

Test testLoomAssetPreviewRoadmapReflectsShippedSurfaces()
	Assert(FileContains%("docs\\loom\\roadmap.md", "**Browser-card thumbnails (Item + Spell)**") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "**Shipped: 3D previews cover the actor base mesh, item `MMeshID`, and mesh-catalog entries.**") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "For actor previews, it applies available male body/face textures and supports drag-to-orbit plus wheel zoom; animation playback remains deferred.") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "First cut applies available male body/face textures, supports drag-to-orbit plus wheel zoom, and leaves animation playback deferred.") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "**Still deferred:** previews for item `FMeshID`, other actor appearance slots, and the full-project texture grid.") = True)
	Assert(FileContains%("docs\\loom\\roadmap.md", "**Still deferred:** mesh preview (MMeshID / FMeshID on items; MeshIDs on actors), browser-card thumbnails") = False)
	Assert(FileContains%("docs\\loom\\roadmap.md", "it does not yet apply textures, tick animation, or support manual orbit.") = False)
	Assert(FileContains%("docs\\loom\\roadmap.md", "First cut: no textures applied to the mesh (face/body texture support is a follow-up), no animation tick, no manual orbit (auto-spin only).") = False)
End Test

Test testLoomGuidanceDocumentsEveryShippedBrowserCategory()
	Local CategoryMap$ = "Actors / Items / Spells / Projectiles / Particles / Zones / Factions / Animation Sets / Tools / Scripts / Textures / Meshes / Sounds / Music / Stats / Days & Seasons / Interface / Settings"
	Assert(FileContains%("docs\\loom\\README.md", "**Browser** with 18 categories") = True)
	Assert(FileContains%("docs\\loom\\README.md", CategoryMap$) = True)
	Assert(FileContains%("docs\\loom\\architecture.md", "**`Browser.bb`** — The boot surface with 18 categories") = True)
	Assert(FileContains%("docs\\loom\\architecture.md", CategoryMap$) = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"actor\",   \"Actors\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"item\",    \"Items\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"spell\",   \"Spells\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"projectile\", \"Projectiles\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"particle\", \"Particles\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"zone\",    \"Zones\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"faction\", \"Factions\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"animset\", \"Animation Sets\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"tools\",   \"Tools\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"script\",  \"Scripts\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"texture\", \"Textures\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"mesh\",    \"Meshes\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"sound\",   \"Sounds\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"music\",   \"Music\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"stats\",   \"Stats\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"environment\", \"Days & Seasons\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"interface\", \"Interface\")") = True)
	Assert(FileContains%("Modules\\Loom\\Browser.bb", "Browser::addCategory(self, \"settings\", \"Settings\")") = True)
	Assert(FileContains%("docs\\loom\\README.md", "with seven categories") = False)
	Assert(FileContains%("docs\\loom\\architecture.md", "Seven categories") = False)
End Test
