Strict
Include "Modules\IO\File.bb"

// Will manage the reading and writing of options.dat
Type OptionsDataManager

	Field Width%
	Field Height%
	Field Depth%
	Field AA%
	Field DefaultVolume#
	Field GrassEnabled%
	Field AnisotropyLevel%
	Field FullScreen%
	Field VSync%
	Field Bloom%
	Field Rays%
	Field AWater%
	Field ShadowC%
	Field ShadowQ%
	Field ShadowR%
	Field DOF%

	Method Load(obfuscated=False)
		if obfuscated
			OptionsDataManager::ReadObfuscated(self)
		else
			OptionsDataManager::ReadFast(self)
		end if
	End Method

	Method Save(obfuscated=False)
		if obfuscated
			OptionsDataManager::WriteObfuscated(self)
		else
			OptionsDataManager::WriteFast(self)
		end if
	End Method

	// Read the obfuscated options.dat file
	Method ReadObfuscated()
		local f.File = new File("Data\Options.dat")

		self\Width = File::readShort(f)
		self\Height = File::readShort(f)
		self\Depth = File::readByte(f)
		self\AA = File::readByte(f)
		self\DefaultVolume = File::readFloat(f)
		self\GrassEnabled = File::readByte(f)
		self\AnisotropyLevel = File::readByte(f)
		self\FullScreen = File::readByte(f)
		self\VSync = File::readByte(f)
		self\Bloom = File::readByte(f)
		self\Rays = File::readByte(f)
		self\AWater = File::readByte(f)
		self\ShadowC = File::readByte(f)
		self\ShadowQ = File::readByte(f)
		self\ShadowR = File::readByte(f)
		self\DOF = File::readByte(f)

		File::close(f)
		Delete f
	End Method

	// Write the obfuscated options.dat file
	Method WriteObfuscated()
		local f.File = new File("Data\Options.dat")

		File::writeShort(f, self\Width)
		File::writeShort(f, self\Height)
		File::writeByte(f, self\Depth)
		File::writeByte(f, self\AA)
		File::writeFloat(f, self\DefaultVolume)
		File::writeByte(f, self\GrassEnabled)
		File::writeByte(f, self\AnisotropyLevel)
		File::writeByte(f, self\FullScreen)
		File::writeByte(f, self\VSync)
		File::writeByte(f, self\Bloom)
		File::writeByte(f, self\Rays)
		File::writeByte(f, self\AWater)
		File::writeByte(f, self\ShadowC)
		File::writeByte(f, self\ShadowQ)
		File::writeByte(f, self\ShadowR)
		File::writeByte(f, self\DOF)

		File::close(f)
		Delete f
	End Method

	// Read the un-obfuscated options.dat file
	Method ReadFast()
		local f.File = new File("Data\Options.dat")

		self\Width = Int(File::readLine(f))
		self\Height = Int(File::readLine(f))
		self\Depth = Int(File::readLine(f))
		self\AA = Int(File::readLine(f))
		self\DefaultVolume = Float(File::readLine(f))
		self\GrassEnabled = Int(File::readLine(f))
		self\AnisotropyLevel = Int(File::readLine(f))
		self\FullScreen = Int(File::readLine(f))
		self\VSync = Int(File::readLine(f))
		self\Bloom = Int(File::readLine(f))
		self\Rays = Int(File::readLine(f))
		self\AWater = Int(File::readLine(f))
		self\ShadowC = Int(File::readLine(f))
		self\ShadowQ = Int(File::readLine(f))
		self\ShadowR = Int(File::readLine(f))
		self\DOF = Int(File::readLine(f))

		File::close(f)
		Delete f
	End Method

	// Write the un-obfuscated options.dat file
	Method WriteFast()
		local f.File = new File("Data\Options.dat")

		File::writeLine(f, self\Width)
		File::writeLine(f, self\Height)
		File::writeLine(f, self\Depth)
		File::writeLine(f, self\AA)
		File::writeLine(f, self\DefaultVolume)
		File::writeLine(f, self\GrassEnabled)
		File::writeLine(f, self\AnisotropyLevel)
		File::writeLine(f, self\FullScreen)
		File::writeLine(f, self\VSync)
		File::writeLine(f, self\Bloom)
		File::writeLine(f, self\Rays)
		File::writeLine(f, self\AWater)
		File::writeLine(f, self\ShadowC)
		File::writeLine(f, self\ShadowQ)
		File::writeLine(f, self\ShadowR)
		File::writeLine(f, self\DOF)

		File::close(f)
		Delete f
	End Method

End Type