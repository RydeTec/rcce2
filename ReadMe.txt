RCCE2 Source Repo

You are limited to the following:
-No redistrubtion of source to users who do not already have access to the repo
-If your permission is revoked you must destory all copies of the source
-You are allowed to recompile your changes and use for your projects, however you are not allowed to give compiled or uncompiled versions of the enigine who do not already have an RCCE2 license.

If you have write privleges you are limited to the following:
-Do not commit large files like assets if they are unnecessary for compiling the project.
-If your commit is found unnecessary or broken it will be repealed.


How to use source:
1. Create a bitbucket account
2. Request source access on the RCCE forums
3. Download the program SourceTree and launch it
4. Click Settings
5. Click Advanced Tab
6. In User information put your name and EMail you used on bitbucket
7. Goto https://bitbucket.org/rcce/rcce2 and click "Clone in Source Tree"
8. You will now be able to download the latest stable source.
9. Copy the files in Decls/ to their appropriate blitz userlibs/ folder.
10. Copy your own source files for fastextension 1.17 into the fastextension_1_17_retail/
	10.1 You can obtain a copy from fastext.com
	10.2 Copy the Decls/ to the userlibs/ in your Blitz3D install
	10.2 Copy FastExt.bb from include/ to Client/Modules/ and GUE/Modules/
11. Compile Client.bb, Server.bp, GUE.exe, GUE Max.exe and Project Manager.bb
12. Copy those .exe you compiled to your project for use.

To Refresh:
Click Fetch and it will fetch the latest version.


For Developers

To Create a New Branch to work on a feature or bug:
1. Click Git Flow
2. Click "Start New Feature" or Hotfix for bug
3. Name your feature (keep latest development branch)
4. Click OK
5. It will create a new branch for you to work with for that feature.
6. Once you have the feature 100% working and ready to merge with the latest development branch:
	1. Make sure you have the correct branch selected
	2. Click Git Flow
	3. Click Finish Feature
	4. Keep the defaults
	5. Click OK

To start work on a new version:
1. Double click the development branch to switch to it
2. Click Branch
3. Name the version "version/*.*.*" without quotes and with numbers replacing the *
4. Click Specified Commit
5. Determine the commit you would like to turn into a new version
6. Click Create Branch
7. You can now submit that branch for possible release

To submit a version for release:
1. PM Ryandeanrocks on the forums which version is ready for release
2. Currently only he has access to push to the release branch

Notes for Developers:
-Always branch your features and bugs into the development branch but not until they are ready
-Always branch from the development branch to work on new features and bugs, otherwise you may be working with outdated code
-Always branch from the development branch to create new versions or update other versions no other branch should become a version
	

