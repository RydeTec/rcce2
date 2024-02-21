
#include "userlibs.h"

#include <windows.h>

#include <vector>
#include <string>

BBUserLibs bbUserLibs;

static std::vector<HMODULE> _mods;

static void libNotFound(){
	bbError( "User lib not found" );
}

static void procNotFound(){
	bbError( "User lib function not found" );
}

static std::string getCurrentWorkingDirectory() {
	char buffer[MAX_PATH];
	DWORD dwRet = GetCurrentDirectory(MAX_PATH, buffer);
	if (dwRet > 0) {
		return std::string(buffer);
	}
	// Handle error or return empty string if the directory cannot be obtained
	return std::string();
}

static std::string getAppDir() {
	char buff[MAX_PATH];
	if (GetModuleFileName(0, buff, MAX_PATH)) {
		std::string t = buff;
		int n = t.find_last_of('\\');
		if (n != std::string::npos) t = t.substr(0, n);
		return t+"\\..";
	}
	return "";
}

void _bbLoadLibs( char *p ){

	std::string cwd = getCurrentWorkingDirectory();
	const char* home = getAppDir().c_str();

	while( *p ){
		HMODULE mod=LoadLibrary( p );
		if (!mod) {
			mod = LoadLibrary((cwd + "/bin/" + std::string(p)).c_str());
		}
		if (!mod) {
			mod = LoadLibrary((cwd + "/../" + std::string(p)).c_str());
		}
		if (!mod) {
			mod = LoadLibrary((cwd + "/../bin/" + std::string(p)).c_str());
		}
		if (!mod) {
			mod = LoadLibrary((cwd + "/../../bin/" + std::string(p)).c_str());
		}
		if( !mod && home ){
			char buff[MAX_PATH];
			strcpy( buff,home );
			strcat( buff,"/userlibs/" );
			strcat( buff,p );
			mod=LoadLibrary( buff );
		}
		p+=strlen(p)+1;
		if( mod ){
			_mods.push_back( mod );
			while( *p ){
				void *proc=GetProcAddress( mod,p );
				p+=strlen(p)+1;
				void *ptr=*(void**)p;
				p+=4;
				*(void**)ptr=proc ? proc : procNotFound;
			}
		}else{
			while( *p ){
				p+=strlen(p)+1;
				void *ptr=*(void**)p;
				p+=4;
				*(void**)ptr=libNotFound;
			}
		}
		++p;
	}
}

BBUserLibs::BBUserLibs(){
	reg( "BBUserLibs" );
}

bool BBUserLibs::startup(){
	return true;
}

void BBUserLibs::shutdown(){
	for( ;_mods.size();_mods.pop_back() ) FreeLibrary( _mods.back() );
}

