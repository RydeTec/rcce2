; Updates system
Dim UpdatesList$(49)
UpdatesList$(0) = "Data\Areas"
UpdatesList$(1) = "Data\Emitter Configs"
UpdatesList$(2) = "Data\Game Data"
UpdatesList$(3) = "Data\UI"
UpdatesList$(4) = "Data\Meshes"
UpdatesList$(5) = "Data\Music"
UpdatesList$(6) = "Data\Sounds"
UpdatesList$(7) = "Data\Textures"
Type UpdateFile
	Field Name$, Checksum
End Type