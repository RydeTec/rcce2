Strict
EnableGC

Include "Modules\MediaImport.bb"

Test testMediaImportTreatsProjectRelativeSelectionAsExistingMedia()
	Assert(MediaImportShouldCopy("Data\Textures\GUI\Button.png", "Data\Textures\", "C:\RCCE") = False)
	Assert(MediaImportRelativePath$("Data\Textures\GUI\Button.png", "Data\Textures\", "C:\RCCE") = "GUI\Button.png")
End Test

Test testMediaImportTreatsAbsoluteProjectSelectionAsExistingMedia()
	Assert(MediaImportShouldCopy("C:/RCCE/Data/Textures/GUI/Button.png", "Data\Textures\", "C:\RCCE") = False)
	Assert(MediaImportRelativePath$("C:/RCCE/Data/Textures/GUI/Button.png", "Data\Textures\", "C:\RCCE") = "GUI\Button.png")
End Test

Test testMediaImportKeepsAbsoluteSourcePathsForExternalSelections()
	Assert(MediaImportShouldCopy("D:/Downloads/Button.png", "Data\Textures\", "C:\RCCE") = True)
	Assert(MediaImportSourcePath$("D:/Downloads/Button.png", "C:\RCCE") = "D:\Downloads\Button.png")
	Assert(MediaImportRelativePath$("D:/Downloads/Button.png", "Data\Textures\", "C:\RCCE", "GUI") = "GUI\Button.png")
End Test

Test testMediaImportUsesFolderNameForExternalFolderSelections()
	Assert(MediaImportRelativePath$("D:\Downloads\Creatures\", "Data\Meshes\", "C:\RCCE") = "Creatures")
	Assert(MediaImportSourcePath$("Data\Meshes\Creatures\", "C:\RCCE") = "C:\RCCE\Data\Meshes\Creatures")
End Test
