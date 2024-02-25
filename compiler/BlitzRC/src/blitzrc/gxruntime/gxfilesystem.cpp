
#include "std.h"
#include "gxfilesystem.h"

static set<gxDir*> dir_set;

gxFileSystem::gxFileSystem(){
	dir_set.clear();
}

gxFileSystem::~gxFileSystem(){
	while( dir_set.size() ) closeDir( *dir_set.begin() );
}

bool gxFileSystem::createDir( const std::string &dir ){
	return CreateDirectory( dir.c_str(),0 ) ? true : false;
}

bool gxFileSystem::deleteDir( const std::string &dir ){
	return RemoveDirectory( dir.c_str() ) ? true : false;
}

bool gxFileSystem::createFile( const std::string &file ){
	return false;
}

bool gxFileSystem::deleteFile( const std::string &file ){
	return DeleteFile( file.c_str() ) ? true : false;
}

bool gxFileSystem::copyFile( const std::string &src,const string &dest ){
	return CopyFile( src.c_str(),dest.c_str(),false ) ? true : false;
}

bool gxFileSystem::renameFile( const std::string &src,const std::string &dest ){
	return MoveFile( src.c_str(),dest.c_str() ) ? true : false;
}

bool gxFileSystem::setCurrentDir( const std::string &dir ){
	return SetCurrentDirectory( dir.c_str()) ? true : false;
}

string gxFileSystem::getCurrentDir()const{
	char buff[MAX_PATH];
	if( !GetCurrentDirectory( MAX_PATH,buff ) ) return "";
	string t=buff;if( t.size() && t[t.size()-1]!='\\' ) t+='\\';
	return t;
}

int gxFileSystem::getFileSize( const std::string &name )const{
	WIN32_FIND_DATA findData;
	HANDLE h=FindFirstFile( name.c_str(),&findData );
	if( h==INVALID_HANDLE_VALUE ) return 0;
	int n=findData.dwFileAttributes,sz=findData.nFileSizeLow;
	FindClose( h );return n & FILE_ATTRIBUTE_DIRECTORY ? 0 : sz;
}

int gxFileSystem::getFileType( const std::string &name )const{
	DWORD t=GetFileAttributes( name.c_str() );
	return t==-1 ? FILE_TYPE_NONE :
	(t & FILE_ATTRIBUTE_DIRECTORY ? FILE_TYPE_DIR : FILE_TYPE_FILE);
}

gxDir *gxFileSystem::openDir( const std::string &name,int flags ){
	string t=name;
	if( t[t.size()-1]=='\\' ) t+="*";
	else t+="\\*";
	WIN32_FIND_DATA f;
	HANDLE h=FindFirstFile( t.c_str(),&f );
	if( h!=INVALID_HANDLE_VALUE ){
		gxDir *d=d_new gxDir( h,f );
		dir_set.insert( d );
		return d;
	}
	return 0;
}
gxDir *gxFileSystem::verifyDir( gxDir *d ){
	return dir_set.count(d) ? d : 0;
}

void gxFileSystem::closeDir( gxDir *d ){
	if( dir_set.erase( d ) ) delete d;
}

bool gxFileSystem::copyDir(const std::string& src, const std::string& dest) {
    WIN32_FIND_DATA ffd;
    HANDLE hFind = INVALID_HANDLE_VALUE;
    DWORD dwError = 0;

    // Create the destination directory if it does not exist
    if (!CreateDirectory(dest.c_str(), NULL)) {
        if (GetLastError() != ERROR_ALREADY_EXISTS) {
            return false; // Failed to create the directory and it doesn't exist already
        }
    }

    // Add a wildcard to search for all files/directories in the source
    std::string searchPath = src + "\\*";

    hFind = FindFirstFile(searchPath.c_str(), &ffd);

    if (INVALID_HANDLE_VALUE == hFind) {
        return false; // Failed to find the first file
    }

    do {
        if (strcmp(ffd.cFileName, ".") != 0 && strcmp(ffd.cFileName, "..") != 0) {
            std::string srcPath = src + "\\" + ffd.cFileName;
            std::string destPath = dest + "\\" + ffd.cFileName;

            if (ffd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                // If the item is a directory, recursively call copyDir
                if (!copyDir(srcPath, destPath)) {
                    FindClose(hFind);
                    return false; // Failed to copy directory
                }
            }
            else {
                // If the item is a file, use CopyFile to copy it
                if (!CopyFile(srcPath.c_str(), destPath.c_str(), FALSE)) {
                    FindClose(hFind);
                    return false; // Failed to copy file
                }
            }
        }
    } while (FindNextFile(hFind, &ffd) != 0);

    dwError = GetLastError();
    FindClose(hFind);

    if (dwError != ERROR_NO_MORE_FILES) {
        return false; // Encountered an error during the copy
    }

    return true; // Successfully copied all contents
}
