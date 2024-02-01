Function BVM_InitStringConst_BVM_MAIN_CMD_SET_DEF_$()
	Local s$
	s = ".set "+Chr(34)+"rc_standard"+Chr(34)+""+Chr(10)
	s = s + ""+Chr(10)
	s = s + "!annotations   "+Chr(10)
	s = s + "!classes      "+Chr(10)
	s = s + "!serialization  "+Chr(10)
	s = s + "!scoping      "+Chr(10)
	s = s + "!funcpointers "+Chr(10)
	s = s + "!reflection   "+Chr(10)
	s = s + "!properties"+Chr(10)
	s = s + "!assertions"+Chr(10)
	s = s + "!typeInference"+Chr(10)
	s = s + ";!altsyntax   "+Chr(10)
	s = s + ".line 20"+Chr(10)
	s = s + "FlushDebugLog<BVM_FlushDebugLog>()"+Chr(10)
	s = s + "DebugLog<BVM_DebugLog>(txt$)"+Chr(10)
	s = s + ".line 36"+Chr(10)
	s = s + "Global hSI%"+Chr(10)
	s = s + ""+Chr(10)
	s = s + ""+Chr(10)
	s = s + "Global Host = 0"+Chr(10)
	s = s + "Global ServerPort"+Chr(10)
	s = s + ".line 44"+Chr(10)
	s = s + "Function REFRESHSCRIPTS<BVM_REFRESHSCRIPTS>()"+Chr(10)
	s = s + "Function ACTOR<BVM_ACTOR>%()"+Chr(10)
	s = s + "Function CONTEXTACTOR<BVM_CONTEXTACTOR>%()"+Chr(10)
	s = s + "Function PERSISTENT<BVM_PERSISTENT>(Param1%)"+Chr(10)
	s = s + "Function FINDACTOR<BVM_FINDACTOR>%(PARAM1$, ACTORTYPE% = 3)"+Chr(10)
	s = s + "Function ARMOURLEVEL<BVM_GETARMOURLEVEL>%(Param1%)"+Chr(10)
	s = s + "Function THREADEXECUTE<BVM_THREADEXECUTE>(NAME$, FUNC$, ACTOR%=0, CONTEXTACTOR%=0, PARAM$ = "+Chr(34)+""+Chr(34)+")"+Chr(10)
	s = s + "Function SAVESTATE<BVM_SAVESTATE>()"+Chr(10)
	s = s + "Function PLAYERACCOUNTNAME<BVM_PLAYERACCOUNTNAME>$(PARAM%)"+Chr(10)
	s = s + "Function PLAYERACCOUNTEMAIL<BVM_PLAYERACCOUNTEMAIL>$(PARAM%)"+Chr(10)
	s = s + "Function PLAYERISGM<BVM_PLAYERISGM>%(PARAM%)"+Chr(10)
	s = s + "Function PLAYERISDM<BVM_PLAYERISDM>%(PARAM%)"+Chr(10)
	s = s + "Function PLAYERISBANNED<BVM_PLAYERISBANNED>%(PARAM%)"+Chr(10)
	s = s + "Function BANPLAYER<BVM_BANPLAYER>(PARAM%)"+Chr(10)
	s = s + "Function KICKPLAYER<BVM_KICKPLAYER>(Param%)"+Chr(10)
	s = s + "Function ACTORX<BVM_ACTORX>#(PARAM1%)"+Chr(10)
	s = s + "Function ACTORY<BVM_ACTORY>#(PARAM1%)"+Chr(10)
	s = s + "Function ACTORZ<BVM_ACTORZ>#(PARAM1%)"+Chr(10)
	s = s + "Function ACTORAGGRESSIVENESS<BVM_ACTORAGGRESSIVENESS>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORINTRIGGER<BVM_ACTORINTRIGGER>%(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORSINZONE<BVM_ACTORSINZONE>%(PARAM1$, INSTANCE%=0)"+Chr(10)
	s = s + "Function PLAYERSINZONE<BVM_PLAYERSINZONE>%(PARAM1$, INSTANCE%=0)"+Chr(10)
	s = s + "Function ZONEINSTANCEEXISTS<BVM_ZONEINSTANCEEXISTS>%(PARAM1$, INSTANCE%)"+Chr(10)
	s = s + "Function CREATEZONEINSTANCE<BVM_CREATEZONEINSTANCE>%(PARAM1$, INSTANCE%=0)"+Chr(10)
	s = s + "Function REMOVEZONEINSTANCE<BVM_REMOVEZONEINSTANCE>(PARAM1$, INSTANCE%)"+Chr(10)
	s = s + "Function COUNTPARTYMEMBERS<BVM_COUNTPARTYMEMBERS>%(PARAM%)"+Chr(10)
	s = s + "Function PARTYMEMBER<BVM_PARTYMEMBER>%(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function KILLACTOR<BVM_KILLACTOR>(PARAM1%, PARAM2%=0)"+Chr(10)
	s = s + "Function CHANGEACTOR<BVM_CHANGEACTOR>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SPAWNITEM<BVM_SPAWNITEM>(PARAM1$, PARAM2%, PARAM3$, PARAM4#, PARAM5#, PARAM6#, PARAM7%=0)"+Chr(10)
	s = s + "Function SETACTORGENDER<BVM_SETACTORGENDER>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORBEARD<BVM_ACTORBEARD>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORBEARD<BVM_SETACTORBEARD>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORHAIR<BVM_ACTORHAIR>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORHAIR<BVM_SETACTORHAIR>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORCALLFORHELP<BVM_ACTORCALLFORHELP>(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORAISTATE<BVM_SETACTORAISTATE>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORAISTATE<BVM_ACTORAISTATE>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORTARGET<BVM_ACTORTARGET>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORTARGET<BVM_SETACTORTARGET>(PARAM1%, PARAM2%=0)"+Chr(10)
	s = s + "Function SETACTORDESTINATION<BVM_SETACTORDESTINATION>(PARAM1%, PARAM2#, PARAM3#)"+Chr(10)
	s = s + "Function GIVEKILLXP<BVM_GIVEKILLXP>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SPAWN<BVM_SPAWN>%(PARAM1%, PARAM2$, PARAM3#, PARAM4#, PARAM5#, PARAM6$ = "+Chr(34)+""+Chr(34)+", PARAM7$ = "+Chr(34)+""+Chr(34)+", PARAM8%=0)"+Chr(10)
	s = s + "Function PARAMETER<BVM_PARAMETER>$(PARAM1%)"+Chr(10)
	s = s + "Function ROTATEACTOR<BVM_ROTATEACTOR>(PARAM1%, PARAM2#)"+Chr(10)
	s = s + "Function MOVEACTOR<BVM_MOVEACTOR>(PARAM1%, PARAM2#, PARAM3#, PARAM4#, PARAM5%=0, PARAM6%=0)"+Chr(10)
	s = s + "Function CREATEFLOATINGNUMBER<BVM_CREATEFLOATINGNUMBER>(PARAM1%, PARAM2%, PARAM3%=255, PARAM4%=255, PARAM5%=255)"+Chr(10)
	s = s + "Function ACTORRIDER<BVM_ACTORRIDER>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORMOUNT<BVM_ACTORMOUNT>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMID<BVM_ITEMID>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMVALUE<BVM_ITEMVALUE>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMMASS<BVM_ITEMMASS>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMRANGE<BVM_ITEMRANGE>#(PARAM1%)"+Chr(10)
	s = s + "Function ITEMDAMAGE<BVM_ITEMDAMAGE>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMDAMAGETYPE<BVM_ITEMDAMAGETYPE>$(PARAM1%)"+Chr(10)
	s = s + "Function ITEMWEAPONTYPE<BVM_ITEMWEAPONTYPE>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMARMOR<BVM_ITEMARMOR>%(PARAM1%)"+Chr(10)
	s = s + "Function ITEMMISCDATA<BVM_ITEMMISCDATA>$(PARAM1%)"+Chr(10)
	s = s + "Function ITEMHEALTH<BVM_ITEMHEALTH>%(PARAM1%)"+Chr(10)
	s = s + "Function SETITEMHEALTH<BVM_SETITEMHEALTH>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ITEMATTRIBUTE<BVM_ITEMATTRIBUTE>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function PLAYERINGAME<BVM_PLAYERINGAME>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORISHUMAN<BVM_ACTORISHUMAN>%(PARAM1%)"+Chr(10)
	s = s + "Function SETLEADER<BVM_SETLEADER>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORLEADER<BVM_ACTORLEADER>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORPETS<BVM_ACTORPETS>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORDESTINATIONX<BVM_ACTORDESTINATIONX>#(PARAM1%)"+Chr(10)
	s = s + "Function ACTORDESTINATIONZ<BVM_ACTORDESTINATIONZ>#(PARAM1%)"+Chr(10)
	s = s + "Function ACTORUNDERWATER<BVM_ACTORUNDERWATER>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORGENDER<BVM_ACTORGENDER>%(PARAM1%)"+Chr(10)
	s = s + "Function SETOWNER<BVM_SETOWNER>(PARAM1%, PARAM2$, PARAM3%, PARAM4% = 0)"+Chr(10)
	s = s + "Function SCENERYOWNER<BVM_SCENERYOWNER>%(PARAM1$, PARAM2%, PARAM3%=0)"+Chr(10)
	s = s + "Function ACTORID<BVM_ACTORID>%(PARAM1$, PARAM2$)"+Chr(10)
	s = s + "Function ACTORIDFROMINSTANCE<BVM_ACTORIDFROMINSTANCE>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORCLOTHES<BVM_ACTORCLOTHES>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORCLOTHES<BVM_SETACTORCLOTHES>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORFACE<BVM_ACTORFACE>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORFACE<BVM_SETACTORFACE>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ITEMNAME<BVM_ITEMNAME>$(PARAM1%)"+Chr(10)
	s = s + "Function ACTORBACKPACK<BVM_ACTORBACKPACK>%(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function BACKPACKCOUNT<BVM_BACKPACKCOUNT>%(Param1%, Param2%)"+Chr(10)
	s = s + "Function ACTORHAT<BVM_ACTORHAT>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORWEAPON<BVM_ACTORWEAPON>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORSHIELD<BVM_ACTORSHIELD>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORCHEST<BVM_ACTORCHEST>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORHANDS<BVM_ACTORHANDS>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORBELT<BVM_ACTORBELT>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORFEET<BVM_ACTORFEET>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORLEGS<BVM_ACTORLEGS>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORRING<BVM_ACTORRING>%(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORAMULET<BVM_ACTORAMULET>%(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORGROUP<BVM_ACTORGROUP>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORGROUP<BVM_SETACTORGROUP>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORGUILD<BVM_ACTORGROUP>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORGUILD<BVM_SETACTORGROUP>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function FIREPROJECTILE<BVM_FIREPROJECTILE>(PARAM1%, PARAM2%, PARAM3$)"+Chr(10)
	s = s + "Function ACTOROUTDOORS<BVM_ACTOROUTDOORS>%(PARAM1%)"+Chr(10)
	s = s + "Function ZONEOUTDOORS<BVM_ZONEOUTDOORS>%(PARAM1$)"+Chr(10)
	s = s + "Function ADDACTOREFFECT<BVM_ADDACTOREFFECT>(PARAM1%, PARAM2$, PARAM3$, PARAM4%, PARAM5%, PARAM6%)"+Chr(10)
	s = s + "Function DELETEACTOREFFECT<BVM_DELETEACTOREFFECT>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function ACTORHASEFFECT<BVM_ACTORHASEFFECT>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SCREENFLASH<BVM_SCREENFLASH>(PARAM1%, PARAM2%, PARAM3%, PARAM4%, PARAM5%, PARAM6%, PARAM7%=0)"+Chr(10)
	s = s + "Function ADDABILITY<BVM_ADDABILITY>(PARAM1%, PARAM2$, PARAM3%=1)"+Chr(10)
	s = s + "Function DELETEABILITY<BVM_DELETEABILITY>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function ABILITYKNOWN<BVM_ABILITYKNOWN>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function ABILITYMEMORISED<BVM_ABILITYMEMORISED>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function ABILITYLEVEL<BVM_ABILITYLEVEL>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SETABILITYLEVEL<BVM_SETABILITYLEVEL>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function ANIMATEACTOR<BVM_ANIMATEACTOR>(PARAM1%, PARAM2$, PARAM3#, PARAM4%=0)"+Chr(10)
	s = s + "Function PLAYMUSIC<BVM_PLAYMUSIC>(PARAM1%, PARAM2%, PARAM3%=0)"+Chr(10)
	s = s + "Function PLAYSOUND<BVM_PLAYSOUND>(PARAM1%, PARAM2%, PARAM3%=0)"+Chr(10)
	s = s + "Function PLAYSPEECH<BVM_PLAYSPEECH>(PARAM1%, PARAM2%=0)"+Chr(10)
	s = s + "Function CREATEEMITTER<BVM_CREATEEMITTER>(PARAM1%, PARAM2$, PARAM3%, PARAM4%, PARAM5#=0, PARAM6#=0, PARAM7#=0, PARAM8%=0)"+Chr(10)
	s = s + "Function GETFACTION<BVM_GETFACTION>$(Param1%)"+Chr(10)
	s = s + "Function SETFACTIONRATING<BVM_SETFACTIONRATING>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function CHANGEFACTIONRATING<BVM_CHANGEFACTIONRATING>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function SETHOMEFACTION<BVM_SETHOMEFACTION>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function HOMEFACTION<BVM_HOMEFACTION>$(PARAM1%)"+Chr(10)
	s = s + "Function FACTIONRATING<BVM_FACTIONRATING>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function DEFAULTFACTIONRATING<BVM_DEFAULTFACTIONRATING>%(PARAM1$, PARAM2$)"+Chr(10)
	s = s + "Function SPLIT<BVM_SPLIT>$(PARAM1$, PARAM2%, PARAM3$="+Chr(34)+","+Chr(34)+")"+Chr(10)
	s = s + "Function FULLTRIM<BVM_FULLTRIM>$(PARAM1$)"+Chr(10)
	s = s + "Function DELETEFILE<BVM_DELETEFILE>(PARAM1$)"+Chr(10)
	s = s + "Function READFILE<BVM_READFILE>%(PARAM1$)"+Chr(10)
	s = s + "Function WRITEFILE<BVM_WRITEFILE>%(PARAM1$)"+Chr(10)
	s = s + "Function OPENFILE<BVM_OPENFILE>%(PARAM1$)"+Chr(10)
	s = s + "Function APPENDFILE<BVM_APPENDFILE>%(PARAM1$)"+Chr(10)
	s = s + "Function CREATEDIR<BVM_CREATEDIR>%(PARAM1$)"+Chr(10)
	s = s + "Function FILESIZE<BVM_FILESIZE>%(PARAM1$)"+Chr(10)
	s = s + "Function FILETYPE<BVM_FILETYPE>%(PARAM1$)"+Chr(10)
	s = s + "Function WARP<BVM_WARP>(PARAM1%, PARAM2$, PARAM3$, PARAM4%=0)"+Chr(10)
	s = s + "Function ACTORZONE<BVM_ACTORZONE>$(PARAM1%)"+Chr(10)
	s = s + "Function ACTORZONEINSTANCE<BVM_ACTORZONEINSTANCE>%(PARAM1%)"+Chr(10)
	s = s + "Function UPDATEXPBAR<BVM_UPDATEXPBAR>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function GIVEXP<BVM_GIVEXP>(PARAM1%, PARAM2%, PARAM3%=0)"+Chr(10)
	s = s + "Function ACTORXP<BVM_ACTORXP>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORXPMULTIPLIER<BVM_ACTORXPMULTIPLIER>%(PARAM1%)"+Chr(10)
	s = s + "Function SETACTORLEVEL<BVM_SETACTORLEVEL>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function ACTORLEVEL<BVM_ACTORLEVEL>%(PARAM1%)"+Chr(10)
	s = s + "Function ACTORDISTANCE<BVM_ACTORDISTANCE>#(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SCRIPTLOG<BVM_SCRIPTLOG>(PARAM1$="+Chr(34)+""+Chr(34)+")"+Chr(10)
	s = s + "Function RUNTIMEERROR<BVM_RUNTIMEERROR>(PARAM1$="+Chr(34)+""+Chr(34)+")"+Chr(10)
	s = s + "Function NEWQUEST<BVM_NEWQUEST>(PARAM1%, PARAM2$, PARAM3$, PARAM4%=255, PARAM5%=255, PARAM6%=255)"+Chr(10)
	s = s + "Function UPDATEQUEST<BVM_UPDATEQUEST>(PARAM1%, PARAM2$, PARAM3$, PARAM4%=255, PARAM5%=255, PARAM6%=255)"+Chr(10)
	s = s + "Function COMPLETEQUEST<BVM_COMPLETEQUEST>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function DELETEQUEST<BVM_DELETEQUEST>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function QUESTSTATUS<BVM_QUESTSTATUS>$(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function QUESTCOMPLETE<BVM_QUESTCOMPLETE>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SETREPUTATION<BVM_SETREPUTATION>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function REPUTATION<BVM_REPUTATION>%(PARAM1%)"+Chr(10)
	s = s + "Function SETRESISTANCE<BVM_SETRESISTANCE>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function RESISTANCE<BVM_RESISTANCE>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SETATTRIBUTE<BVM_SETATTRIBUTE>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function CHANGEATTRIBUTE<BVM_CHANGEATTRIBUTE>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function ATTRIBUTE<BVM_ATTRIBUTE>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SETMAXATTRIBUTE<BVM_SETMAXATTRIBUTE>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function CHANGEMAXATTRIBUTE<BVM_CHANGEMAXATTRIBUTE>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function MAXATTRIBUTE<BVM_MAXATTRIBUTE>%(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function RACE<BVM_RACE>$(PARAM1%)"+Chr(10)
	s = s + "Function CLASS<BVM_CLASS>$(PARAM1%)"+Chr(10)
	s = s + "Function SETNAME<BVM_SETNAME>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function NAME<BVM_NAME>$(PARAM1%)"+Chr(10)
	s = s + "Function SETTAG<BVM_SETTAG>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function TAG<BVM_TAG>$(PARAM1%)"+Chr(10)
	s = s + "Function GOLD<BVM_GOLD>%(PARAM1%)"+Chr(10)
	s = s + "Function MONEY<BVM_MONEY>%(PARAM1%)"+Chr(10)
	s = s + "Function CHANGEGOLD<BVM_CHANGEGOLD>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function CHANGEMONEY<BVM_CHANGEMONEY>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SETGOLD<BVM_SETGOLD>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SETMONEY<BVM_SETMONEY>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function YEAR<BVM_YEAR>%()"+Chr(10)
	s = s + "Function SEASON<BVM_SEASON>$()"+Chr(10)
	s = s + "Function DAY<BVM_DAY>%()"+Chr(10)
	s = s + "Function MONTH<BVM_MONTH>$()"+Chr(10)
	s = s + "Function HOUR<BVM_HOUR>%()"+Chr(10)
	s = s + "Function MINUTE<BVM_MINUTE>%()"+Chr(10)
	s = s + "Function NEXTACTOR<BVM_NEXTACTOR>%(PARAM1%=0)"+Chr(10)
	s = s + "Function FIRSTACTORINZONE<BVM_FIRSTACTORINZONE>%(PARAM1$, PARAM2%=0)"+Chr(10)
	s = s + "Function NEXTACTORINZONE<BVM_NEXTACTORINZONE>%(PARAM1%)"+Chr(10)
	s = s + "Function OPENTRADING<BVM_OPENTRADING>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SETACTORGLOBAL<BVM_SETACTORGLOBAL>(PARAM1%, PARAM2%, PARAM3$)"+Chr(10)
	s = s + "Function ACTORGLOBAL<BVM_ACTORGLOBAL>$(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function GIVEITEM<BVM_GIVEITEM>(PARAM1%, PARAM2$, PARAM3%=1)"+Chr(10)
	s = s + "Function HASITEM<BVM_HASITEM>%(PARAM1%, PARAM2$, PARAM3%=1)"+Chr(10)
	s = s + "Function BUBBLEOUTPUT<BVM_BUBBLEOUTPUT>(PARAM1%, PARAM2$, PARAM3%=255, PARAM4%=255, PARAM5%=255)"+Chr(10)
	s = s + "Function OUTPUT<BVM_OUTPUT>(PARAM1%, PARAM2$, PARAM3%=255, PARAM4%=255, PARAM5%=255)"+Chr(10)
	s = s + "Function MYSQLQUERY<BVM_MYSQLQUERY>$(PARAM1$)"+Chr(10)
	s = s + "Function MYSQLNUMROWS<BVM_MYSQLNUMROWS>%(PARAM1%)"+Chr(10)
	s = s + "Function MYSQLFETCHROW<BVM_MYSQLFETCHROW>$(PARAM1%)"+Chr(10)
	s = s + "Function MYSQLGETVAR<BVM_MYSQLGETVAR>$(PARAM1%,PARAM2$)"+Chr(10)
	s = s + "Function MYSQLFREEQUERY<BVM_MYSQLFREEQUERY>(PARAM1%)"+Chr(10)
	s = s + "Function MYSQLFREEROW<BVM_MYSQLFREEROW>(PARAM1%)"+Chr(10)
	s = s + "Function SQLACCOUNTID<BVM_SQLACCOUNTID>%(PARAM1%)"+Chr(10)
	s = s + "Function SQLACTORID<BVM_SQLACTORID>%(PARAM1%)"+Chr(10)
	s = s + "Function GETRUNTIMEID<BVM_GETRUNTIMEID>%(PARAM1%)"+Chr(10)
	s = s + "Function GETRNID<BVM_GETRNID>%(PARAM1%)"+Chr(10)
	s = s + "Function SETWAITSPEAK<BVM_SETWAITSPEAK>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SETWAITITEM<BVM_SETWAITITEM>(PARAM1%, PARAM2$, PARAM3%)"+Chr(10)
	s = s + "Function SETWAITKILL<BVM_SETWAITKILL>(PARAM1%, PARAM2%, PARAM3%)"+Chr(10)
	s = s + "Function SETWAITING<BVM_SETWAITING>(X%)"+Chr(10)
	s = s + "Function SETWAITINFO<BVM_SETWAITINFO>(PARAM1%, PARAM2%)"+Chr(10)
	s = s + "Function SETWAITTIME<BVM_SETWAITTIME>(PARAM1%)"+Chr(10)
	s = s + "Function SETWAITSTART<BVM_SETWAITSTART>(PARAM1%)"+Chr(10)
	s = s + "Function GETWAITRESULT<BVM_GETWAITRESULT>$()"+Chr(10)
	s = s + "Function SETSUPERGLOBAL<BVM_SETSUPERGLOBAL>(PARAM1%, PARAM2$)"+Chr(10)
	s = s + "Function SUPERGLOBAL<BVM_GETSUPERGLOBAL>$(PARAM1%)"+Chr(10)
	s = s + "Function RCE_SENDOPENDIALOG(HOST%, ARNID%, CARUNTIMEID%, BACKGROUNDTEXID%, TITLE$)"+Chr(10)
	s = s + "Function RCE_SENDCLOSEDIALOG(HOST%, ARNID%, DHANDLE%)"+Chr(10)
	s = s + "Function RCE_SENDDIALOGOUTPUT(HOST%, ARNID%, RED%, GREEN%, BLUE%, DHANDLE%, MESSAGE$)"+Chr(10)
	s = s + "Function RCE_SENDDIALOGINPUT(HOST%, ARNID%, DHANDLE%, OPTIONS$, DELIM$ = "+Chr(34)+","+Chr(34)+")"+Chr(10)
	s = s + "Function RCE_SENDINPUT(HOST%, ARNID%, ITYPE%, TITLE$, PROMPT$)"+Chr(10)
	s = s + "Function RCE_SENDCREATEPROGRESSBAR(HOST%, ARNID%, R%, G%, B%, X#, Y#, W#, H#, MAXIMUM%, VALUE%, LABEL$)"+Chr(10)
	s = s + "Function RCE_SENDDELETEPROGRESSBAR(HOST%, ARNID%, PBAR%)"+Chr(10)
	s = s + "Function RCE_SENDUPDATEPROGRESSBAR(HOST%, ARNID%, PBAR%, VAL%)"+Chr(10)
	s = s + "Function GOTO<BVM_GOTO>(PARAM$)"+Chr(10)
	s = s + "Function GOTOIF<BVM_GOTOIF>(PARAM$)"+Chr(10)
	s = s + "Function DEQUOTE<BVM_DEQUOTE>$(PARAM1$)"+Chr(10)
	s = s + "Function MOD<BVM_MOD>#(PARAM1#, PARAM2#)"+Chr(10)
	s = s + ""+Chr(10)
	s = s + ""+Chr(10)
	s = s + "Function CLOSEUDPSTREAM<BVM_CLOSEUDPSTREAM>(Param1%)"+Chr(10)
	s = s + "Function SENDUDPMSG<BVM_SENDUDPMSG>(Param1%, Param2%, Param3%)"+Chr(10)
	s = s + "Function RECVUDPMSG<BVM_RECVUDPMSG>$(Param1%)"+Chr(10)
	s = s + "Function UDPSTREAMIP<BVM_UDPSTREAMIP>%(Param1%)"+Chr(10)
	s = s + "Function UDPSTREAMPORT<BVM_UDPSTREAMPORT>%(Param1%)"+Chr(10)
	s = s + "Function UDPMSGIP<BVM_UDPMSGIP>%(Param1%)"+Chr(10)
	s = s + "Function UDPMSGPORT<BVM_UDPMSGPORT>%(Param1%)"+Chr(10)
	s = s + "Function UDPTIMEOUTS<BVM_UDPTIMEOUTS>(Param1%)"+Chr(10)
	s = s + "Function COUNTHOSTIPS<BVM_COUNTHOSTIPS>%(Param1%)"+Chr(10)
	s = s + "Function HOSTIP<BVM_HOSTIP>%(Param1%)"+Chr(10)
	s = s + "Function DOTTEDIP<BVM_DOTTEDIP>$(Param1%)"+Chr(10)
	s = s + "Function CREATEUDPSTREAM<BVM_CREATEUDPSTREAM>%(Param1%=0)"+Chr(10)
	s = s + ".line 280"+Chr(10)
	s = s + "Abs%(int%)"+Chr(10)
	s = s + "ACos#(float#)"+Chr(10)
	s = s + "Asc%(string$)"+Chr(10)
	s = s + "ASin#(float#)"+Chr(10)
	s = s + "ATan#(float#)"+Chr(10)
	s = s + "ATan2#(floata#,floatb#)"+Chr(10)
	s = s + "ATanII<Atan2>#(floata#,floatb#)"+Chr(10)
	s = s + "BankSize%(bank%)"+Chr(10)
	s = s + "Bin$(value%)"+Chr(10)
	s = s + "CallDLL%(dll_name$,func_name$,in_bank%,out_bank%)"+Chr(10)
	s = s + "Ceil#(float#)"+Chr(10)
	s = s + "Chr$(ascii%)"+Chr(10)
	s = s + "CloseDir(dir%)"+Chr(10)
	s = s + "CloseFile(file_stream%)"+Chr(10)
	s = s + "CopyBank(src_bank%,src_offset%,dest_bank%,dest_offset%,count%)"+Chr(10)
	s = s + "CopyFile(file$,to$)"+Chr(10)
	s = s + "Cos#(degrees#)"+Chr(10)
	s = s + "CreateBank%(size%=0)"+Chr(10)
	s = s + "CurrentDate$()"+Chr(10)
	s = s + "RealDate<CurrentDate>$()"+Chr(10)
	s = s + "CurrentDir$()"+Chr(10)
	s = s + "CurrentTime$()"+Chr(10)
	s = s + "RealTime<CurrentTime>$()"+Chr(10)
	s = s + "Eof%(stream%)"+Chr(10)
	s = s + "End<BVM_Abort>()"+Chr(10)
	s = s + "Exp#(float#)"+Chr(10)
	s = s + "FilePos%(file_stream%)"+Chr(10)
	s = s + "Float#(value$)"+Chr(10)
	s = s + "Floor#(float#)"+Chr(10)
	s = s + "FreeBank(bank%)"+Chr(10)
	s = s + "GetEnv$(env_var$)"+Chr(10)
	s = s + "Hex$(value%)"+Chr(10)
	s = s + "HostDebugLog<BVM_DebugLog>(message$)"+Chr(10)
	s = s + "Int%(value%)"+Chr(10)
	s = s + "Instr%(string$,find$,from%=1)"+Chr(10)
	s = s + "Left$(string$,count%)"+Chr(10)
	s = s + "Len%(string$)"+Chr(10)
	s = s + "Log#(float#)"+Chr(10)
	s = s + "Log10#(float#)"+Chr(10)
	s = s + "Lower$(string$)"+Chr(10)
	s = s + "LSet$(string$,size%)"+Chr(10)
	s = s + "Mid$(string$,start%,count%=65535)"+Chr(10)
	s = s + "MilliSecs%()"+Chr(10)
	s = s + "PeekByte%(bank%,offset%)"+Chr(10)
	s = s + "PeekFloat#(bank%,offset%)"+Chr(10)
	s = s + "PeekInt%(bank%,offset%)"+Chr(10)
	s = s + "PeekShort%(bank%,offset%)"+Chr(10)
	s = s + "PokeByte(bank%,offset%,value%)"+Chr(10)
	s = s + "PokeFloat(bank%,offset%,value#)"+Chr(10)
	s = s + "PokeInt(bank%,offset%,value%)"+Chr(10)
	s = s + "PokeShort(bank%,offset%,value%)"+Chr(10)
	s = s + "Print(string$)"+Chr(10)
	s = s + "Rand%(from%,to%)"+Chr(10)
	s = s + "ReadAvail%(stream%)"+Chr(10)
	s = s + "ReadByte%(stream%)"+Chr(10)
	s = s + "ReadBytes%(bank%,file%,offset%,count%)"+Chr(10)
	s = s + "ReadFloat#(stream%)"+Chr(10)
	s = s + "ReadInt%(stream%)"+Chr(10)
	s = s + "ReadLine$(stream%)"+Chr(10)
	s = s + "ReadShort%(stream%)"+Chr(10)
	s = s + "ReadString$(stream%)"+Chr(10)
	s = s + "Replace$(string$,from$,to$)"+Chr(10)
	s = s + "ResizeBank(bank%,size%)"+Chr(10)
	s = s + "Right$(string$,count%)"+Chr(10)
	s = s + "Rnd#(from#,to#)"+Chr(10)
	s = s + "RSet$(string$,size%)"+Chr(10)
	s = s + "SeekFile%(file_stream%,pos%)"+Chr(10)
	s = s + "SetEnv(env_var$,value$)"+Chr(10)
	s = s + "Sgn%(value%)"+Chr(10)
	s = s + "sign<sgn>%(value%)"+Chr(10)
	s = s + "Sin#(degrees#)"+Chr(10)
	s = s + "Sqr#(float#)"+Chr(10)
	s = s + "sqrt<sqr>#(float#)"+Chr(10)
	s = s + "String$(string$,repeat%)"+Chr(10)
	s = s + "Tan#(Float#)"+Chr(10)
	s = s + "Trim$(string$)"+Chr(10)
	s = s + "Upper$(string$)"+Chr(10)
	s = s + "Write(string$)"+Chr(10)
	s = s + "WriteByte(stream%,byte%)"+Chr(10)
	s = s + "WriteBytes%(bank%,file%,offset%,count%)"+Chr(10)
	s = s + "WriteFloat(stream%,float#)"+Chr(10)
	s = s + "WriteInt(stream%,int%)"+Chr(10)
	s = s + "WriteLine(stream%,string$)"+Chr(10)
	s = s + "WriteShort(stream%,short%)"+Chr(10)
	s = s + "WriteString(stream%,string$)"
	Return s
End Function
Global BVM_MAIN_CMD_SET_DEF_$ = BVM_InitStringConst_BVM_MAIN_CMD_SET_DEF_()
Global BVM_ALT_CMD_SET_DEF_$ = Replace(BVM_MAIN_CMD_SET_DEF_, ";!altsyntax", "!altsyntax")

Global BVM_gMainCmdSet_% = 0
Global BVM_gAltCmdSet_% = 0
Global BVM_gInteractiveSessionCounter% = 0
Global BVM_TempModuleCounter% = 0
Global BVM_CurrentProcess.BVM_TProcess
Global BVM_FirstMutex.BVM_TMutex

Type BVM_TInteractiveSession


	Field hModule%

	Field hContext%

	Field hCmdSet%

	Field tmpModuleName1$

	Field tmpModuleName2$

	Field mainProgCode$

End Type


Type BVM_TMutex


	Field hOwningContext%

	Field id$

	Field nextMutex.BVM_TMutex

End Type


Type BVM_TProcess


	Field hContext%

	Field hModule%

	Field frameDuration%

	Field pauseTimeOutTime%

	Field lockingMutex.BVM_TMutex

	Field lockTimeOutTime%

	Field lastExecTime%

	Field nextProcess.BVM_TProcess

	Field prevProcess.BVM_TProcess

	Field group.BVM_TProcessGroup

End Type


Type BVM_TProcessGroup


	Field firstProcess.BVM_TProcess

	Field lastProcess.BVM_TProcess

	Field currentProcess.BVM_TProcess

End Type



Function BVM_Invoke%(withTimeOut% = 0)
	Local sparam5$
	Local iparam6%
	Local sparam6$
	Local sparam3$
	Local sparam4$
	Local fparam7#
	Local fparam8#
	Local iparam9%
	Local iparam10%
	Local sparam11$
	Local fparam3#
	Local fparam4#
	Local fparam5#
	Local fparam6#
	Local iparam7%
	Local fparam1#
	Local fparam2#
	Local sparam2$
	Local iparam3%
	Local iparam4%
	Local iparam5%
	Local iparam2%
	Local sparam0$
	Local iparam1%
	Local fparam0#
	Local iparam0%
	Local sparam1$
	Local hContext%
	Local ret%
	hContext% = BVM_GetSelectedContext()
	Repeat 
		If withTimeOut% Then
			ret% = BVM_RunWithTimeOut()
		Else
			ret% = BVM_Run()
		End If
		Select ret%
			Case 0
				BVM_FlushDebugLog()
				Return 1
			Case 1
				; If with time out, return special code '-1' to say we have yielded,
				If withTimeOut% Then
					Return -1
				Else
					BVM_ReportDebugError(BVM_GetLastErrorMsg())
					Return 0
				End If

			Case 30
				BVM_PushInt(BVM_OnDebugStep())

			Case 31
				BVM_CollectHostObjectGarbage()

			Case -1
				; Execution error
				BVM_ReportDebugError(BVM_GetLastErrorMsg())
				Return 0
			Case 256
				BVM_PushInt(Host%)
			Case 257
				BVM_PushInt(hSI%)
			Case 258
				BVM_PushInt(ServerPort%)
			Case 259
				Host% = BVM_PopInt()
			Case 260
				hSI% = BVM_PopInt()
			Case 261
				ServerPort% = BVM_PopInt()
			Case 262
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ABILITYKNOWN(iparam0%, sparam1$))
			Case 263
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ABILITYLEVEL(iparam0%, sparam1$))
			Case 264
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ABILITYMEMORISED(iparam0%, sparam1$))
			Case 265
				iparam0% = BVM_PopInt()
				BVM_PushInt(Abs(iparam0%))
			Case 266
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(ACos(fparam0#))
			Case 267
				BVM_PushInt(BVM_ACTOR())
			Case 268
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORAGGRESSIVENESS(iparam0%))
			Case 269
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORAISTATE(iparam0%))
			Case 270
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORAMULET(iparam0%, iparam1%))
			Case 271
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORBACKPACK(iparam0%, iparam1%))
			Case 272
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORBEARD(iparam0%))
			Case 273
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORBELT(iparam0%))
			Case 274
				iparam0% = BVM_PopInt()
				BVM_ACTORCALLFORHELP(iparam0%)
			Case 275
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORCHEST(iparam0%))
			Case 276
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORCLOTHES(iparam0%))
			Case 277
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORDESTINATIONX(iparam0%))
			Case 278
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORDESTINATIONZ(iparam0%))
			Case 279
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORDISTANCE(iparam0%, iparam1%))
			Case 280
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORFACE(iparam0%))
			Case 281
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORFEET(iparam0%))
			Case 282
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORGENDER(iparam0%))
			Case 283
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_ACTORGLOBAL(iparam0%, iparam1%))
			Case 284
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORGROUP(iparam0%))
			Case 285
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORGROUP(iparam0%))
			Case 286
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORHAIR(iparam0%))
			Case 287
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORHANDS(iparam0%))
			Case 288
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORHASEFFECT(iparam0%, sparam1$))
			Case 289
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORHAT(iparam0%))
			Case 290
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_ACTORID(sparam0$, sparam1$))
			Case 291
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORIDFROMINSTANCE(iparam0%))
			Case 292
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORINTRIGGER(iparam0%, iparam1%))
			Case 293
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORISHUMAN(iparam0%))
			Case 294
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORLEADER(iparam0%))
			Case 295
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORLEGS(iparam0%))
			Case 296
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORLEVEL(iparam0%))
			Case 297
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORMOUNT(iparam0%))
			Case 298
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTOROUTDOORS(iparam0%))
			Case 299
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORPETS(iparam0%))
			Case 300
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORRIDER(iparam0%))
			Case 301
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORRING(iparam0%, iparam1%))
			Case 302
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORSHIELD(iparam0%))
			Case 303
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_ACTORSINZONE(sparam0$, iparam1%))
			Case 304
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORTARGET(iparam0%))
			Case 305
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORUNDERWATER(iparam0%))
			Case 306
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORWEAPON(iparam0%))
			Case 307
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORX(iparam0%))
			Case 308
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORXP(iparam0%))
			Case 309
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORXPMULTIPLIER(iparam0%))
			Case 310
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORY(iparam0%))
			Case 311
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ACTORZ(iparam0%))
			Case 312
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_ACTORZONE(iparam0%))
			Case 313
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ACTORZONEINSTANCE(iparam0%))
			Case 314
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_ADDABILITY(iparam0%, sparam1$, iparam2%)
			Case 315
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				sparam2$ = BVM_PopString()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_ADDACTOREFFECT(iparam0%, sparam1$, sparam2$, iparam3%, iparam4%, iparam5%)
			Case 316
				iparam3% = BVM_PopInt()
				fparam2# = BVM_PopFloat()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_ANIMATEACTOR(iparam0%, sparam1$, fparam2#, iparam3%)
			Case 317
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_APPENDFILE(sparam0$))
			Case 318
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_GETARMOURLEVEL(iparam0%))
			Case 319
				sparam0$ = BVM_PopString()
				BVM_PushInt(Asc(sparam0$))
			Case 320
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(ASin(fparam0#))
			Case 321
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(ATan(fparam0#))
			Case 322
				fparam1# = BVM_PopFloat()
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(ATan2(fparam0#, fparam1#))
			Case 323
				fparam1# = BVM_PopFloat()
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Atan2(fparam0#, fparam1#))
			Case 324
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ATTRIBUTE(iparam0%, sparam1$))
			Case 325
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_BACKPACKCOUNT(iparam0%, iparam1%))
			Case 326
				iparam0% = BVM_PopInt()
				BVM_PushInt(BankSize(iparam0%))
			Case 327
				iparam0% = BVM_PopInt()
				BVM_BANPLAYER(iparam0%)
			Case 328
				iparam0% = BVM_PopInt()
				BVM_PushString(Bin(iparam0%))
			Case 329
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_BUBBLEOUTPUT(iparam0%, sparam1$, iparam2%, iparam3%, iparam4%)
			Case 330
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_PushInt(CallDLL(sparam0$, sparam1$, iparam2%, iparam3%))
			Case 331
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Ceil(fparam0#))
			Case 332
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_CHANGEACTOR(iparam0%, iparam1%)
			Case 333
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_CHANGEATTRIBUTE(iparam0%, sparam1$, iparam2%)
			Case 334
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_CHANGEFACTIONRATING(iparam0%, sparam1$, iparam2%)
			Case 335
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_CHANGEGOLD(iparam0%, iparam1%)
			Case 336
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_CHANGEMAXATTRIBUTE(iparam0%, sparam1$, iparam2%)
			Case 337
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_CHANGEMONEY(iparam0%, iparam1%)
			Case 338
				iparam0% = BVM_PopInt()
				BVM_PushString(Chr(iparam0%))
			Case 339
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_CLASS(iparam0%))
			Case 340
				iparam0% = BVM_PopInt()
				CloseDir(iparam0%)
			Case 341
				iparam0% = BVM_PopInt()
				CloseFile(iparam0%)
			Case 342
				iparam0% = BVM_PopInt()
				BVM_CLOSEUDPSTREAM(iparam0%)
			Case 343
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_COMPLETEQUEST(iparam0%, sparam1$)
			Case 344
				BVM_PushInt(BVM_CONTEXTACTOR())
			Case 345
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				CopyBank(iparam0%, iparam1%, iparam2%, iparam3%, iparam4%)
			Case 346
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				CopyFile(sparam0$, sparam1$)
			Case 347
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Cos(fparam0#))
			Case 348
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_COUNTHOSTIPS(iparam0%))
			Case 349
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_COUNTPARTYMEMBERS(iparam0%))
			Case 350
				iparam0% = BVM_PopInt()
				BVM_PushInt(CreateBank(iparam0%))
			Case 351
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_CREATEDIR(sparam0$))
			Case 352
				iparam7% = BVM_PopInt()
				fparam6# = BVM_PopFloat()
				fparam5# = BVM_PopFloat()
				fparam4# = BVM_PopFloat()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_CREATEEMITTER(iparam0%, sparam1$, iparam2%, iparam3%, fparam4#, fparam5#, fparam6#, iparam7%)
			Case 353
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_CREATEFLOATINGNUMBER(iparam0%, iparam1%, iparam2%, iparam3%, iparam4%)
			Case 354
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_CREATEUDPSTREAM(iparam0%))
			Case 355
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_CREATEZONEINSTANCE(sparam0$, iparam1%))
			Case 356
				BVM_PushString(CurrentDate())
			Case 357
				BVM_PushString(CurrentDir())
			Case 358
				BVM_PushString(CurrentTime())
			Case 359
				BVM_PushInt(BVM_DAY())
			Case 360
				sparam0$ = BVM_PopString()
				BVM_DebugLog(sparam0$)
			Case 361
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_DEFAULTFACTIONRATING(sparam0$, sparam1$))
			Case 362
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_DELETEABILITY(iparam0%, sparam1$)
			Case 363
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_DELETEACTOREFFECT(iparam0%, sparam1$)
			Case 364
				sparam0$ = BVM_PopString()
				BVM_DELETEFILE(sparam0$)
			Case 365
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_DELETEQUEST(iparam0%, sparam1$)
			Case 366
				sparam0$ = BVM_PopString()
				BVM_PushString(BVM_DEQUOTE(sparam0$))
			Case 367
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_DOTTEDIP(iparam0%))
			Case 368
				BVM_Abort()
			Case 369
				iparam0% = BVM_PopInt()
				BVM_PushInt(Eof(iparam0%))
			Case 370
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Exp(fparam0#))
			Case 371
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_FACTIONRATING(iparam0%, sparam1$))
			Case 372
				iparam0% = BVM_PopInt()
				BVM_PushInt(FilePos(iparam0%))
			Case 373
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_FILESIZE(sparam0$))
			Case 374
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_FILETYPE(sparam0$))
			Case 375
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_FINDACTOR(sparam0$, iparam1%))
			Case 376
				sparam2$ = BVM_PopString()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_FIREPROJECTILE(iparam0%, iparam1%, sparam2$)
			Case 377
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_FIRSTACTORINZONE(sparam0$, iparam1%))
			Case 378
				sparam0$ = BVM_PopString()
				BVM_PushFloat(Float(sparam0$))
			Case 379
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Floor(fparam0#))
			Case 380
				BVM_FlushDebugLog()
			Case 381
				iparam0% = BVM_PopInt()
				FreeBank(iparam0%)
			Case 382
				sparam0$ = BVM_PopString()
				BVM_PushString(BVM_FULLTRIM(sparam0$))
			Case 383
				sparam0$ = BVM_PopString()
				BVM_PushString(GetEnv(sparam0$))
			Case 384
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_GETFACTION(iparam0%))
			Case 385
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_GETRNID(iparam0%))
			Case 386
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_GETRUNTIMEID(iparam0%))
			Case 387
				BVM_PushString(BVM_GETWAITRESULT())
			Case 388
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_GIVEITEM(iparam0%, sparam1$, iparam2%)
			Case 389
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_GIVEKILLXP(iparam0%, iparam1%)
			Case 390
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_GIVEXP(iparam0%, iparam1%, iparam2%)
			Case 391
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_GOLD(iparam0%))
			Case 392
				sparam0$ = BVM_PopString()
				BVM_GOTO(sparam0$)
			Case 393
				sparam0$ = BVM_PopString()
				BVM_GOTOIF(sparam0$)
			Case 394
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_HASITEM(iparam0%, sparam1$, iparam2%))
			Case 395
				iparam0% = BVM_PopInt()
				BVM_PushString(Hex(iparam0%))
			Case 396
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_HOMEFACTION(iparam0%))
			Case 397
				sparam0$ = BVM_PopString()
				BVM_DebugLog(sparam0$)
			Case 398
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_HOSTIP(iparam0%))
			Case 399
				BVM_PushInt(BVM_HOUR())
			Case 400
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_PushInt(Instr(sparam0$, sparam1$, iparam2%))
			Case 401
				iparam0% = BVM_PopInt()
				BVM_PushInt(Int(iparam0%))
			Case 402
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMARMOR(iparam0%))
			Case 403
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMATTRIBUTE(iparam0%, sparam1$))
			Case 404
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMDAMAGE(iparam0%))
			Case 405
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_ITEMDAMAGETYPE(iparam0%))
			Case 406
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMHEALTH(iparam0%))
			Case 407
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMID(iparam0%))
			Case 408
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMMASS(iparam0%))
			Case 409
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_ITEMMISCDATA(iparam0%))
			Case 410
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_ITEMNAME(iparam0%))
			Case 411
				iparam0% = BVM_PopInt()
				BVM_PushFloat(BVM_ITEMRANGE(iparam0%))
			Case 412
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMVALUE(iparam0%))
			Case 413
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_ITEMWEAPONTYPE(iparam0%))
			Case 414
				iparam0% = BVM_PopInt()
				BVM_KICKPLAYER(iparam0%)
			Case 415
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_KILLACTOR(iparam0%, iparam1%)
			Case 416
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(Left(sparam0$, iparam1%))
			Case 417
				sparam0$ = BVM_PopString()
				BVM_PushInt(Len(sparam0$))
			Case 418
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Log(fparam0#))
			Case 419
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Log10(fparam0#))
			Case 420
				sparam0$ = BVM_PopString()
				BVM_PushString(Lower(sparam0$))
			Case 421
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(LSet(sparam0$, iparam1%))
			Case 422
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_MAXATTRIBUTE(iparam0%, sparam1$))
			Case 423
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(Mid(sparam0$, iparam1%, iparam2%))
			Case 424
				BVM_PushInt(MilliSecs())
			Case 425
				BVM_PushInt(BVM_MINUTE())
			Case 426
				fparam1# = BVM_PopFloat()
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(BVM_MOD(fparam0#, fparam1#))
			Case 427
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_MONEY(iparam0%))
			Case 428
				BVM_PushString(BVM_MONTH())
			Case 429
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				fparam3# = BVM_PopFloat()
				fparam2# = BVM_PopFloat()
				fparam1# = BVM_PopFloat()
				iparam0% = BVM_PopInt()
				BVM_MOVEACTOR(iparam0%, fparam1#, fparam2#, fparam3#, iparam4%, iparam5%)
			Case 430
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_MYSQLFETCHROW(iparam0%))
			Case 431
				iparam0% = BVM_PopInt()
				BVM_MYSQLFREEQUERY(iparam0%)
			Case 432
				iparam0% = BVM_PopInt()
				BVM_MYSQLFREEROW(iparam0%)
			Case 433
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_MYSQLGETVAR(iparam0%, sparam1$))
			Case 434
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_MYSQLNUMROWS(iparam0%))
			Case 435
				sparam0$ = BVM_PopString()
				BVM_PushString(BVM_MYSQLQUERY(sparam0$))
			Case 436
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_NAME(iparam0%))
			Case 437
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				sparam2$ = BVM_PopString()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_NEWQUEST(iparam0%, sparam1$, sparam2$, iparam3%, iparam4%, iparam5%)
			Case 438
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_NEXTACTOR(iparam0%))
			Case 439
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_NEXTACTORINZONE(iparam0%))
			Case 440
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_OPENFILE(sparam0$))
			Case 441
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_OPENTRADING(iparam0%, iparam1%)
			Case 442
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_OUTPUT(iparam0%, sparam1$, iparam2%, iparam3%, iparam4%)
			Case 443
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_PARAMETER(iparam0%))
			Case 444
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_PARTYMEMBER(iparam0%, iparam1%))
			Case 445
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(PeekByte(iparam0%, iparam1%))
			Case 446
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushFloat(PeekFloat(iparam0%, iparam1%))
			Case 447
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(PeekInt(iparam0%, iparam1%))
			Case 448
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(PeekShort(iparam0%, iparam1%))
			Case 449
				iparam0% = BVM_PopInt()
				BVM_PERSISTENT(iparam0%)
			Case 450
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_PLAYERACCOUNTEMAIL(iparam0%))
			Case 451
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_PLAYERACCOUNTNAME(iparam0%))
			Case 452
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_PLAYERINGAME(iparam0%))
			Case 453
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_PLAYERISBANNED(iparam0%))
			Case 454
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_PLAYERISDM(iparam0%))
			Case 455
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_PLAYERISGM(iparam0%))
			Case 456
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_PLAYERSINZONE(sparam0$, iparam1%))
			Case 457
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PLAYMUSIC(iparam0%, iparam1%, iparam2%)
			Case 458
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PLAYSOUND(iparam0%, iparam1%, iparam2%)
			Case 459
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PLAYSPEECH(iparam0%, iparam1%)
			Case 460
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				PokeByte(iparam0%, iparam1%, iparam2%)
			Case 461
				fparam2# = BVM_PopFloat()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				PokeFloat(iparam0%, iparam1%, fparam2#)
			Case 462
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				PokeInt(iparam0%, iparam1%, iparam2%)
			Case 463
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				PokeShort(iparam0%, iparam1%, iparam2%)
			Case 464
				sparam0$ = BVM_PopString()
				Print(sparam0$)
			Case 465
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_QUESTCOMPLETE(iparam0%, sparam1$))
			Case 466
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_QUESTSTATUS(iparam0%, sparam1$))
			Case 467
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_RACE(iparam0%))
			Case 468
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(Rand(iparam0%, iparam1%))
			Case 469
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDCLOSEDIALOG(iparam0%, iparam1%, iparam2%)
			Case 470
				sparam11$ = BVM_PopString()
				iparam10% = BVM_PopInt()
				iparam9% = BVM_PopInt()
				fparam8# = BVM_PopFloat()
				fparam7# = BVM_PopFloat()
				fparam6# = BVM_PopFloat()
				fparam5# = BVM_PopFloat()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDCREATEPROGRESSBAR(iparam0%, iparam1%, iparam2%, iparam3%, iparam4%, fparam5#, fparam6#, fparam7#, fparam8#, iparam9%, iparam10%, sparam11$)
			Case 471
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDDELETEPROGRESSBAR(iparam0%, iparam1%, iparam2%)
			Case 472
				sparam4$ = BVM_PopString()
				sparam3$ = BVM_PopString()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDDIALOGINPUT(iparam0%, iparam1%, iparam2%, sparam3$, sparam4$)
			Case 473
				sparam6$ = BVM_PopString()
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDDIALOGOUTPUT(iparam0%, iparam1%, iparam2%, iparam3%, iparam4%, iparam5%, sparam6$)
			Case 474
				sparam4$ = BVM_PopString()
				sparam3$ = BVM_PopString()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDINPUT(iparam0%, iparam1%, iparam2%, sparam3$, sparam4$)
			Case 475
				sparam4$ = BVM_PopString()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDOPENDIALOG(iparam0%, iparam1%, iparam2%, iparam3%, sparam4$)
			Case 476
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				RCE_SENDUPDATEPROGRESSBAR(iparam0%, iparam1%, iparam2%, iparam3%)
			Case 477
				iparam0% = BVM_PopInt()
				BVM_PushInt(ReadAvail(iparam0%))
			Case 478
				iparam0% = BVM_PopInt()
				BVM_PushInt(ReadByte(iparam0%))
			Case 479
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(ReadBytes(iparam0%, iparam1%, iparam2%, iparam3%))
			Case 480
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_READFILE(sparam0$))
			Case 481
				iparam0% = BVM_PopInt()
				BVM_PushFloat(ReadFloat(iparam0%))
			Case 482
				iparam0% = BVM_PopInt()
				BVM_PushInt(ReadInt(iparam0%))
			Case 483
				iparam0% = BVM_PopInt()
				BVM_PushString(ReadLine(iparam0%))
			Case 484
				iparam0% = BVM_PopInt()
				BVM_PushInt(ReadShort(iparam0%))
			Case 485
				iparam0% = BVM_PopInt()
				BVM_PushString(ReadString(iparam0%))
			Case 486
				BVM_PushString(CurrentDate())
			Case 487
				BVM_PushString(CurrentTime())
			Case 488
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_RECVUDPMSG(iparam0%))
			Case 489
				BVM_REFRESHSCRIPTS()
			Case 490
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_REMOVEZONEINSTANCE(sparam0$, iparam1%)
			Case 491
				sparam2$ = BVM_PopString()
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_PushString(Replace(sparam0$, sparam1$, sparam2$))
			Case 492
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_REPUTATION(iparam0%))
			Case 493
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_RESISTANCE(iparam0%, sparam1$))
			Case 494
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				ResizeBank(iparam0%, iparam1%)
			Case 495
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(Right(sparam0$, iparam1%))
			Case 496
				fparam1# = BVM_PopFloat()
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Rnd(fparam0#, fparam1#))
			Case 497
				fparam1# = BVM_PopFloat()
				iparam0% = BVM_PopInt()
				BVM_ROTATEACTOR(iparam0%, fparam1#)
			Case 498
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(RSet(sparam0$, iparam1%))
			Case 499
				sparam0$ = BVM_PopString()
				BVM_RUNTIMEERROR(sparam0$)
			Case 500
				BVM_SAVESTATE()
			Case 501
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				;BVM_PushInt(BVM_SCENERYOWNER(sparam0$, iparam1%, iparam2%)) {##}
			Case 502
				iparam6% = BVM_PopInt()
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SCREENFLASH(iparam0%, iparam1%, iparam2%, iparam3%, iparam4%, iparam5%, iparam6%)
			Case 503
				sparam0$ = BVM_PopString()
				BVM_SCRIPTLOG(sparam0$)
			Case 504
				BVM_PushString(BVM_SEASON())
			Case 505
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(SeekFile(iparam0%, iparam1%))
			Case 506
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SENDUDPMSG(iparam0%, iparam1%, iparam2%)
			Case 507
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETABILITYLEVEL(iparam0%, sparam1$, iparam2%)
			Case 508
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORAISTATE(iparam0%, iparam1%)
			Case 509
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORBEARD(iparam0%, iparam1%)
			Case 510
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORCLOTHES(iparam0%, iparam1%)
			Case 511
				fparam2# = BVM_PopFloat()
				fparam1# = BVM_PopFloat()
				iparam0% = BVM_PopInt()
				BVM_SETACTORDESTINATION(iparam0%, fparam1#, fparam2#)
			Case 512
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORFACE(iparam0%, iparam1%)
			Case 513
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORGENDER(iparam0%, iparam1%)
			Case 514
				sparam2$ = BVM_PopString()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORGLOBAL(iparam0%, iparam1%, sparam2$)
			Case 515
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORGROUP(iparam0%, iparam1%)
			Case 516
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORGROUP(iparam0%, iparam1%)
			Case 517
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORHAIR(iparam0%, iparam1%)
			Case 518
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORLEVEL(iparam0%, iparam1%)
			Case 519
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETACTORTARGET(iparam0%, iparam1%)
			Case 520
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETATTRIBUTE(iparam0%, sparam1$, iparam2%)
			Case 521
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				SetEnv(sparam0$, sparam1$)
			Case 522
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETFACTIONRATING(iparam0%, sparam1$, iparam2%)
			Case 523
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETGOLD(iparam0%, iparam1%)
			Case 524
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETHOMEFACTION(iparam0%, sparam1$)
			Case 525
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETITEMHEALTH(iparam0%, iparam1%)
			Case 526
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETLEADER(iparam0%, iparam1%)
			Case 527
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETMAXATTRIBUTE(iparam0%, sparam1$, iparam2%)
			Case 528
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETMONEY(iparam0%, iparam1%)
			Case 529
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETNAME(iparam0%, sparam1$)
			Case 530
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				;BVM_SETOWNER(iparam0%, sparam1$, iparam2%, iparam3%) {##}
			Case 531
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETREPUTATION(iparam0%, iparam1%)
			Case 532
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETRESISTANCE(iparam0%, sparam1$, iparam2%)
			Case 533
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETSUPERGLOBAL(iparam0%, sparam1$)
			Case 534
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETTAG(iparam0%, sparam1$)
			Case 535
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETWAITINFO(iparam0%, iparam1%)
			Case 536
				iparam0% = BVM_PopInt()
				BVM_SETWAITING(iparam0%)
			Case 537
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_SETWAITITEM(iparam0%, sparam1$, iparam2%)
			Case 538
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETWAITKILL(iparam0%, iparam1%, iparam2%)
			Case 539
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_SETWAITSPEAK(iparam0%, iparam1%)
			Case 540
				iparam0% = BVM_PopInt()
				BVM_SETWAITSTART(iparam0%)
			Case 541
				iparam0% = BVM_PopInt()
				BVM_SETWAITTIME(iparam0%)
			Case 542
				iparam0% = BVM_PopInt()
				BVM_PushInt(Sgn(iparam0%))
			Case 543
				iparam0% = BVM_PopInt()
				BVM_PushInt(sgn(iparam0%))
			Case 544
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Sin(fparam0#))
			Case 545
				iparam7% = BVM_PopInt()
				sparam6$ = BVM_PopString()
				sparam5$ = BVM_PopString()
				fparam4# = BVM_PopFloat()
				fparam3# = BVM_PopFloat()
				fparam2# = BVM_PopFloat()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_SPAWN(iparam0%, sparam1$, fparam2#, fparam3#, fparam4#, sparam5$, sparam6$, iparam7%))
			Case 546
				iparam6% = BVM_PopInt()
				fparam5# = BVM_PopFloat()
				fparam4# = BVM_PopFloat()
				fparam3# = BVM_PopFloat()
				sparam2$ = BVM_PopString()
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_SPAWNITEM(sparam0$, iparam1%, sparam2$, fparam3#, fparam4#, fparam5#, iparam6%)
			Case 547
				sparam2$ = BVM_PopString()
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(BVM_SPLIT(sparam0$, iparam1%, sparam2$))
			Case 548
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_SQLACCOUNTID(iparam0%))
			Case 549
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_SQLACTORID(iparam0%))
			Case 550
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Sqr(fparam0#))
			Case 551
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(sqr(fparam0#))
			Case 552
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushString(String(sparam0$, iparam1%))
			Case 553
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_GETSUPERGLOBAL(iparam0%))
			Case 554
				iparam0% = BVM_PopInt()
				BVM_PushString(BVM_TAG(iparam0%))
			Case 555
				fparam0# = BVM_PopFloat()
				BVM_PushFloat(Tan(fparam0#))
			Case 556
				sparam4$ = BVM_PopString()
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				sparam1$ = BVM_PopString()
				sparam0$ = BVM_PopString()
				BVM_THREADEXECUTE(sparam0$, sparam1$, iparam2%, iparam3%, sparam4$)
			Case 557
				sparam0$ = BVM_PopString()
				BVM_PushString(Trim(sparam0$))
			Case 558
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_UDPMSGIP(iparam0%))
			Case 559
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_UDPMSGPORT(iparam0%))
			Case 560
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_UDPSTREAMIP(iparam0%))
			Case 561
				iparam0% = BVM_PopInt()
				BVM_PushInt(BVM_UDPSTREAMPORT(iparam0%))
			Case 562
				iparam0% = BVM_PopInt()
				BVM_UDPTIMEOUTS(iparam0%)
			Case 563
				iparam5% = BVM_PopInt()
				iparam4% = BVM_PopInt()
				iparam3% = BVM_PopInt()
				sparam2$ = BVM_PopString()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_UPDATEQUEST(iparam0%, sparam1$, sparam2$, iparam3%, iparam4%, iparam5%)
			Case 564
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_UPDATEXPBAR(iparam0%, iparam1%)
			Case 565
				sparam0$ = BVM_PopString()
				BVM_PushString(Upper(sparam0$))
			Case 566
				iparam3% = BVM_PopInt()
				sparam2$ = BVM_PopString()
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				BVM_WARP(iparam0%, sparam1$, sparam2$, iparam3%)
			Case 567
				sparam0$ = BVM_PopString()
				Write(sparam0$)
			Case 568
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				WriteByte(iparam0%, iparam1%)
			Case 569
				iparam3% = BVM_PopInt()
				iparam2% = BVM_PopInt()
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				BVM_PushInt(WriteBytes(iparam0%, iparam1%, iparam2%, iparam3%))
			Case 570
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_WRITEFILE(sparam0$))
			Case 571
				fparam1# = BVM_PopFloat()
				iparam0% = BVM_PopInt()
				WriteFloat(iparam0%, fparam1#)
			Case 572
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				WriteInt(iparam0%, iparam1%)
			Case 573
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				WriteLine(iparam0%, sparam1$)
			Case 574
				iparam1% = BVM_PopInt()
				iparam0% = BVM_PopInt()
				WriteShort(iparam0%, iparam1%)
			Case 575
				sparam1$ = BVM_PopString()
				iparam0% = BVM_PopInt()
				WriteString(iparam0%, sparam1$)
			Case 576
				BVM_PushInt(BVM_YEAR())
			Case 577
				iparam1% = BVM_PopInt()
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_ZONEINSTANCEEXISTS(sparam0$, iparam1%))
			Case 578
				sparam0$ = BVM_PopString()
				BVM_PushInt(BVM_ZONEOUTDOORS(sparam0$))
			Default 
				BVM_SetLastError(BVM_ERR_ERROR%, "Fatal error : unknown command of id " + BVM_IntToStr(ret%) + ", the invoker seems to be out of sync with the 'rc_standard' command set")
				BVM_ReportDebugError(BVM_GetLastErrorMsg())
				Return 0
		End Select
	Forever
End Function


Function BVM_GetMainCommandSet%()
	If BVM_gMainCmdSet_% = 0 Then
		BVM_gMainCmdSet_% = BVM_CreateCommandSet(BVM_MAIN_CMD_SET_DEF_$)
		If BVM_gMainCmdSet_% = 0 Then
			BVM_SetLastError(4, "Execution context creation failed : unable to create associated command set 'rc_standard'. Reason : " + BVM_GetLastErrorMsg())
			Return 0
		End If
	End If
	Return BVM_gMainCmdSet_%
End Function

Function BVM_GetAltCommandSet%()
	If BVM_gAltCmdSet_% = 0 Then
		BVM_gAltCmdSet_% = BVM_CreateCommandSet(BVM_ALT_CMD_SET_DEF_$)
		If BVM_gAltCmdSet_% = 0 Then
			BVM_SetLastError(4, "Execution context creation failed : unable To create associated command set 'rc_standard'. Reason : " + BVM_GetLastErrorMsg())
			Return 0
		End If
	End If
	Return BVM_gAltCmdSet_%
End Function


Function BVM_FlushMem%(hContext% = 0)
	Local ret%
	Local hPrevCtxt%
	ret% = BVM_GarbageCollect_(hContext%)
	If ret% = -1 Then
		Return False
	Else
		If ret% = 1 Then
			hPrevCtxt% = BVM_GetSelectedContext()
			If hContext% = 0 Then
				hContext% = BVM_GetSelectedContext()
			End If
			If (BVM_Invoke(False) = 0) Then
				BVM_SelectContext(hPrevCtxt%)
				Return False
			End If
			BVM_SelectContext(hPrevCtxt%)
			Return True
		End If
	End If
	Return True
End Function


Function BVM_FlushMemAll%(ignoreErrors% = 0)
	Local count%
	Local i%
	Local hContext%
	Local ret%
	ret% = True
	count% = BVM_CountContexts()
	For i% = 0 To (count%) - (1)
		hContext% = BVM_GetContext(i%)
		ret% = ((ret%) <> 0) And ((BVM_FlushMem(hContext%)) <> 0)
		If (((ret% = 0)) <> 0) And (((ignoreErrors% = 0)) <> 0) Then
			Return False
		End If
	Next
	Return ret%
End Function


Function BVM_MapModule%(hContext%, hModule%, bStartHalted% = 0)
	Local hMainProgEntryPoint%
	Local hPrevContext%
	; Maps a module into a context
	; 	hContext : context to map the module in
	; 	hModule : the module to map
	; 	bStartHalted : in debug mode, start halted (in effect, add a breakpoint at the start of the main program)
	hPrevContext% = BVM_GetSelectedContext()

	If (BVM_SelectContext(hContext%) = 0) Then
		Return False
	End If

	If (BVM_MapModule_(hContext%, hModule%) = 0) Then
		Return False
	End If

	hMainProgEntryPoint% = BVM_FindEntryPoint(hContext%, hModule%, "_main")
	If (BVM_SelectEntryPoint(hMainProgEntryPoint%) = 0) Then
		BVM_SelectContext(hPrevContext%)
		Return False
	End If

	If bStartHalted% Then
		BVM_SetBreakPoint("", 0, True)
	End If
	If (BVM_Invoke() = 0) Then
		BVM_SelectContext(hPrevContext%)
		Return False
	End If
	; Pop the main program return value (which is always 0)
	BVM_PopInt()

	BVM_SelectContext(hPrevContext%)
	Return True
End Function


Function BVM_CreateContext%(bDynamic% = 1)
	Return BVM_CreateContext_(BVM_GetMainCommandSet(), bDynamic%, False)
End Function


Function BVM_HELPER_LoadAndInvoke%(moduleFileName$, functionName$ = "_main", bEnableDebug% = 0, bStartHalted% = 0, bDynamic% = 1)
	Local hEntryPoint% = -1
	Local hModule%
	Local hContext%
	Local hPrevContext%
	Local bRet% = 1

	hPrevContext% = BVM_GetSelectedContext()

	; We load the module
	hModule% = BVM_LoadModule(moduleFileName$)
	If hModule% = BVM_INVALID_MODULE% Then
		Return False
	End If

	; We create a new execution context. An execution context holds the current state of execution.
	hContext% = BVM_CreateContext(bDynamic%)
	If hContext% = BVM_INVALID_CONTEXT% Then
		bRet% = False
	Else
		; We select the current execution context
		If (BVM_SelectContext(hContext%) = 0) Then
			bRet% = False
		Else
			If bEnableDebug% Then
				BVM_StartDebug(hContext%)
			End If

			; ; We map the module into this context
			If (BVM_MapModule(hContext%, hModule%, bStartHalted%) = 0) Then
				bRet% = False
			Else
				; If we just want to run the main program, we're done (main prog already called by the mapping)
				If ((((BVM_StrIEq(functionName$, "_main")) <> 0) Or ((functionName$ = "") <> 0)) = 0) Then
					; We get an entry point handle for the function we want to execute
					hEntryPoint% = BVM_FindEntryPoint(hContext%, hModule%, functionName$)
					If hEntryPoint% = BVM_INVALID_ENTRY_POINT% Then
						bRet% = False
					Else
						; We select the entry point
						If (BVM_SelectEntryPoint(hEntryPoint%) = 0) Then
							bRet% = False
						Else
							; We run the code
							If (BVM_Invoke() = 0) Then
								bRet% = False
							End If
						End If
					End If
				End If
			End If
		End If
	End If

	; Clean up
	BVM_ReleaseModule(hModule%)
	BVM_DeleteContext(hContext%)
	BVM_SelectContext(hPrevContext%)

	Return bRet%
End Function


Function BVM_CreateInteractiveSession%(mainProgCode$ = "", hCmdSet% = 0)
	Local srcCode$
	Local session.BVM_TInteractiveSession
	session = New BVM_TInteractiveSession
	session\tmpModuleName1$ = ".test_interactive_console_tmp_old" + BVM_IntToStr(BVM_gInteractiveSessionCounter%) + ".bvm"
	session\tmpModuleName2$ = ".test_interactive_console_tmp_new" + BVM_IntToStr(BVM_gInteractiveSessionCounter%) + ".bvm"
	session\mainProgCode$ = mainProgCode$
	If hCmdSet% = BVM_INVALID_CMD_SET% Then
		hCmdSet% = BVM_GetMainCommandSet()
	End If
	session\hCmdSet% = hCmdSet%
	srcCode$ = session\mainProgCode$ + ""+Chr(10)+"" + "Function UpdateConsole__()" + ""+Chr(10)+"" + "End Function"
	session\hModule% = BVM_CompileString(srcCode$, session\hCmdSet%, session\tmpModuleName1$, 3)
	session\hContext% = BVM_CreateContext(True)
	BVM_MapModule(session\hContext%, session\hModule%)
	BVM_gInteractiveSessionCounter% = BVM_gInteractiveSessionCounter% + 1
	Return Handle (session)
End Function


Function BVM_UpdateInteractiveSession%(hSession%, newSourceCode$)
	Local srcCode$
	Local hTmpModule%
	Local session.BVM_TInteractiveSession
	Local hEntryPoint%
	session = Object.BVM_TInteractiveSession(hSession%)
	If session = Null Then
		BVM_SetLastError(1, "Invalid session handle")
		Return False
	End If
	srcCode$ = session\mainProgCode$ + ""+Chr(10)+"" + "Function UpdateConsole__()" + ""+Chr(10)+"" + newSourceCode$ + ""+Chr(10)+"" + "End Function"
	hTmpModule% = BVM_CompileString(srcCode$, session\hCmdSet%, session\tmpModuleName2$, 3)
	If hTmpModule% = 0 Then
		Return False
	End If
	If (BVM_ReplaceModule(session\hModule%, hTmpModule%) = 0) Then
		Return False
	End If
	hEntryPoint% = BVM_FindEntryPoint(session\hContext%, session\hModule%, "UpdateConsole__")
	BVM_SelectContext(session\hContext%)
	BVM_SelectEntryPoint(hEntryPoint%)
	BVM_Invoke()
	BVM_PopInt()
	Return True
End Function


Function BVM_DeleteInteractiveSession%(hSession%)
	Local session.BVM_TInteractiveSession
	session = Object.BVM_TInteractiveSession(hSession%)
	If session <> Null Then
		If session\hContext% <> 0 Then
			BVM_DeleteContext(session\hContext%)
			session\hContext% = 0
			BVM_ReleaseModule(session\hModule%)
		End If
	End If
	Return True
End Function


Function BVM_HELPER_InterpretFile%(sourceFileName$, functionName$ = "_main", bEnableDebug% = 0, hCmdSet% = 0, bStartHalted% = 0, bDynamic% = 1)
	Local compilationFlags%
	Local hEntryPoint% = -1
	Local hModule%
	Local hContext%
	Local hPrevContext%
	Local bRet% = 1

	If hCmdSet% = BVM_INVALID_CMD_SET% Then
		hCmdSet% = BVM_GetMainCommandSet()
	End If

	hPrevContext% = BVM_GetSelectedContext()

	If bEnableDebug% Then
		compilationFlags% = 3
	End If

	; We compile the temporary file
	hModule% = BVM_CompileFile(sourceFileName$, hCmdSet%, "___tmp_mod___" + BVM_IntToStr(BVM_TempModuleCounter%) + ".bvm", compilationFlags%)
	If hModule% = BVM_INVALID_MODULE% Then
		bRet% = False
	Else
		BVM_TempModuleCounter% = BVM_TempModuleCounter% + 1

		; We create a context
		hContext% = BVM_CreateContext(bDynamic%)
		If hContext% = BVM_INVALID_CONTEXT% Then
			bRet% = False
		Else
			; We Select the context
			If (BVM_SelectContext(hContext%) = 0) Then
				bRet% = False
			Else
				If bEnableDebug% Then
					BVM_StartDebug(hContext%)
				End If

				; We map the module
				If (BVM_MapModule(hContext%, hModule%, bStartHalted%) = 0) Then
					bRet% = False
				Else
					; If we just want to run the main program, we're done (main prog already called by the mapping)
					If ((((BVM_StrIEq(functionName$, "_main")) <> 0) Or ((functionName$ = "") <> 0)) = 0) Then
						; We get the entry point
						hEntryPoint% = BVM_FindEntryPoint(hContext%, hModule%, functionName$)
						If hEntryPoint% = BVM_INVALID_ENTRY_POINT% Then
							bRet% = False
						Else
							; We select the entry point
							If (BVM_SelectEntryPoint(hEntryPoint%) = 0) Then
								bRet% = False
							Else
								; We execute the code
								If (BVM_Invoke() = 0) Then
									bRet% = False
								End If
							End If
						End If
					End If
				End If
			End If
		End If
	End If

	; Cleanup
	BVM_ReleaseModule(hModule%)
	BVM_DeleteContext(hContext%)
	BVM_SelectContext(hPrevContext%)

	Return bRet%
End Function


Function BVM_GetCallByNameEntryPoint_%(hContext__%, modName__$, funcName__$)
	Local hModule%
	If hContext__% = BVM_INVALID_CONTEXT% Then
		hContext__% = BVM_GetSelectedContext()
	End If
	If modName__$ = "" Then
		hModule% = BVM_GetMappedModule(hContext__%, (BVM_CountMappedModules(hContext__%)) - (1))
	Else
		hModule% = BVM_FindMappedModule(hContext__%, modName__$)
	End If
	Return BVM_FindEntryPoint(hContext__%, hModule%, funcName__$)
End Function


Function BVM_ExecProcesses%(hProcessGroup%, duration%, minCount% = 0, maxCount% = 0)
	Local execThisProcess%
	Local remainingTime%
	Local timeOut%
	Local mutex.BVM_TMutex
	Local ret%
	Local finished%
	Local processGroup.BVM_TProcessGroup
	Local process.BVM_TProcess
	Local time%
	Local startTime%
	Local hPrevContext%
	Local execCount%
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS_GROUP%, "Invalid process group handle")
		Return False
	End If
	hPrevContext% = BVM_GetSelectedContext()
	startTime% = BVM_GetTime()
	execCount% = 0
	Repeat 
		time% = BVM_GetTime()
		process = processGroup\firstProcess
		execThisProcess% = False
		If (time%) - (process\pauseTimeOutTime%) >= 0 Then
			If process\lockingMutex = Null Then
				BVM_SelectContext(process\hContext%)
				execThisProcess% = True
			Else
				If process\lockingMutex\hOwningContext% = 0 Then
					process\lockTimeOutTime% = 0
					process\lockingMutex\hOwningContext% = process\hContext%
					process\lockingMutex = Null
					execThisProcess% = True
				Else
					If (time%) - (process\lockTimeOutTime%) >= 0 Then
						BVM_SelectContext(process\hContext%)
						process\lockingMutex = Null
						BVM_PopInt()
						BVM_PushInt(False)
						execThisProcess% = True
					End If
				End If
			End If
		End If
		finished% = False
		If execThisProcess% Then
			execCount% = execCount% + 1
			process\lastExecTime% = time%
			BVM_CurrentProcess = process
			remainingTime% = (duration%) - ((time%) - (startTime%))
			timeOut% = process\frameDuration%
			If execCount% >= minCount% Then
				If (process\frameDuration%) - (remainingTime%) < 0 Then
					timeOut% = timeOut% + ((remainingTime%) - (process\frameDuration%))
				End If
			End If
			BVM_StartTimeOut(timeOut%)
			ret% = BVM_Invoke(True)
			If ((ret% = 1) <> 0) Or ((ret% = 0) <> 0) Then
				mutex = BVM_FirstMutex
				While mutex <> Null
					If mutex\hOwningContext% = process\hContext% Then
						mutex\hOwningContext% = 0
					End If
					mutex = mutex\nextMutex
				Wend
				finished% = True
			Else
				If ret% = -1 Then
					; Do nothing
				End If
			End If
		End If
		BVM_DetachProcess_(process)
		If (finished% = 0) Then
			process\nextProcess = Null
			process\prevProcess = processGroup\lastProcess
			processGroup\lastProcess\nextProcess = process
			processGroup\lastProcess = process
		End If
	Until ((((((time%) - (startTime%)) - (duration%) >= 0) <> 0) And ((execCount% >= minCount%) <> 0)) <> 0) Or ((((maxCount% <> 0) <> 0) And ((execCount% >= maxCount%) <> 0)) <> 0)
	BVM_SelectContext(hPrevContext%)
	BVM_CurrentProcess = Null
	Return execCount%
End Function


Function BVM_WaitForMutex%(mutexId$, timeout% = 2000000000)
	Local mutex_.BVM_TMutex
	Local mutex.BVM_TMutex
	; Returns true if ownership was taken, false if time out
	mutex_ = BVM_FirstMutex
	While mutex_ <> Null
		If BVM_StrIEq(mutex_\id$, mutexId$) Then
			mutex = mutex_
			Exit
		End If
		mutex = mutex\nextMutex
	Wend
	If mutex = Null Then
		mutex = New BVM_TMutex
		mutex\id$ = mutexId$
		mutex\hOwningContext% = BVM_GetSelectedContext()
		Return True
	Else
		If mutex\hOwningContext% = BVM_GetSelectedContext() Then
			Return True
		Else
			If mutex\hOwningContext% = 0 Then
				mutex\hOwningContext% = BVM_GetSelectedContext()
				Return True
			Else
				If timeout% = 0 Then
					Return False
				Else
					BVM_StartTimeOut(-1)
					BVM_CurrentProcess\lockingMutex = mutex
					BVM_CurrentProcess\lockTimeOutTime% = BVM_GetTime() + timeout%
					Return True
				End If
			End If
		End If
	End If
	Return False
End Function


Function BVM_ReleaseMutex%(mutexId$)
	Local mutex.BVM_TMutex
	mutex = BVM_FirstMutex
	While mutex <> Null
		If BVM_StrIEq(mutex\id$, mutexId$) Then
			If BVM_CurrentProcess\hContext% = mutex\hOwningContext% Then
				mutex\hOwningContext% = 0
				Return True
			End If
			mutex = mutex\nextMutex
			Return False
		End If
	Wend
End Function


Function BVM_CreateProcessGroup%()
	Local processGroup.BVM_TProcessGroup
	processGroup = New BVM_TProcessGroup
	processGroup\firstProcess = Null
	processGroup\lastProcess = Null
	processGroup\currentProcess = Null
	Return Handle (processGroup)
End Function


Function BVM_CountProcesses%(hProcessGroup%)
	Local processGroup.BVM_TProcessGroup
	Local process.BVM_TProcess
	Local count%
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		Return 0
	End If
	process = processGroup\firstProcess
	While process <> Null
		count% = count% + 1
		process = process\nextProcess
		Return count%
	Wend
End Function


Function BVM_CountActiveProcesses%(hProcessGroup%)
	Local processGroup.BVM_TProcessGroup
	Local process.BVM_TProcess
	Local count%
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		Return 0
	End If
	process = processGroup\firstProcess
	While process <> Null
		If BVM_IsProcessPaused_(process) Then
			count% = count% + 1
		End If
		process = process\nextProcess
		Return count%
	Wend
End Function


Function BVM_DeleteProcessGroup%(hProcessGroup%)
	Local hProcess%
	Local processGroup.BVM_TProcessGroup
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS_GROUP%, "Invalid process group handle")
		Return False
	End If
	Repeat 
		hProcess% = BVM_GetFirstProcess(hProcessGroup%)
		If hProcess% = 0 Then
			Exit
		End If
		BVM_DeleteProcess(hProcess%)
	Forever
	Delete processGroup
	Return True
End Function


Function BVM_GetFirstProcess%(hProcessGroup%)
	Local processGroup.BVM_TProcessGroup
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		Return 0
	End If
	Return Handle (processGroup\firstProcess)
End Function


Function BVM_GetLastProcess%(hProcessGroup%)
	Local processGroup.BVM_TProcessGroup
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		Return 0
	End If
	Return Handle (processGroup\lastProcess)
End Function


Function BVM_GetNextProcess%(hProcess%)
	Local process.BVM_TProcess
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS%, "Invalid process handle")
		Return False
	End If
	Return Handle (process\nextProcess)
End Function


Function BVM_GetPrevProcess%(hProcess%)
	Local process.BVM_TProcess
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS%, "Invalid process handle")
		Return False
	End If
	Return Handle (process\prevProcess)
End Function


Function BVM_CreateProcess%(hProcessGroup%, hModule%, frameDuration% = 2, createPaused% = 0)
	Local processGroup.BVM_TProcessGroup
	Local hMainProgEntryPoint%
	Local hPrevContext%
	Local process.BVM_TProcess
	processGroup = Object.BVM_TProcessGroup(hProcessGroup%)
	If processGroup = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS_GROUP%, "Invalid process group handle")
		Return 0
	End If
	process = New BVM_TProcess
	process\hContext% = BVM_CreateContext()
	process\frameDuration% = frameDuration%
	process\lockingMutex = Null
	process\hModule% = hModule%
	process\lockTimeOutTime% = 0
	process\group = processGroup
	If createPaused% Then
		process\pauseTimeOutTime% = 2000000000
	Else
		process\pauseTimeOutTime% = 0
	End If
	process\lastExecTime% = 0
	process\prevProcess = processGroup\lastProcess
	process\nextProcess = Null
	If processGroup\firstProcess <> Null Then
		processGroup\lastProcess\nextProcess = process
	End If
	If processGroup\firstProcess = Null Then
		processGroup\firstProcess = process
	End If
	processGroup\lastProcess = process
	BVM_MapModule_(process\hContext%, hModule%)
	hMainProgEntryPoint% = BVM_FindEntryPoint(process\hContext%, hModule%, "_main")
	hPrevContext% = BVM_GetSelectedContext()
	BVM_SelectContext(process\hContext%)
	BVM_SelectEntryPoint(hMainProgEntryPoint%)
	BVM_SelectContext(hPrevContext%)
	Return Handle (process)
End Function


Function BVM_DetachProcess_%(process.BVM_TProcess)
	Local processGroup.BVM_TProcessGroup
	processGroup = process\group
	If processGroup <> Null Then
		If process\prevProcess <> Null Then
			process\prevProcess\nextProcess = process\nextProcess
		End If
		If process\nextProcess <> Null Then
			process\nextProcess\prevProcess = process\prevProcess
		End If
		If process = processGroup\firstProcess Then
			processGroup\firstProcess = process\nextProcess
		End If
		If process = processGroup\lastProcess Then
			processGroup\lastProcess = process\prevProcess
		End If
	End If
	Return True
End Function


Function BVM_DeleteProcess%(hProcess%)
	Local mutex.BVM_TMutex
	Local processGroup.BVM_TProcessGroup
	Local process.BVM_TProcess
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS%, "Invalid process handle")
		Return False
	End If
	mutex = BVM_FirstMutex
	While mutex <> Null
		If mutex\hOwningContext% = process\hContext% Then
			mutex\hOwningContext% = 0
		End If
		mutex = mutex\nextMutex
	Wend
	processGroup = process\group
	BVM_DeleteContext(process\hContext%)
	BVM_DetachProcess_(process)
	Delete process
	Return True
End Function


Function BVM_PauseProcess%(hProcess%, duration% = 0, bForce% = 0)
	Local process.BVM_TProcess
	Local hPrevContext%
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		Return True
	End If
	If (((bForce% = 0)) <> 0) And ((BVM_IsProcessPaused(hProcess%)) <> 0) Then
		Return True
	End If
	process\pauseTimeOutTime% = BVM_GetTime() + duration%
	hPrevContext% = BVM_GetSelectedContext()
	BVM_SelectContext(process\hContext%)
	BVM_StartTimeOut(-1)
	BVM_SelectContext(hPrevContext%)
	Return True
End Function


Function BVM_Sleep%(duration% = 0)
	BVM_PauseProcess(Handle (BVM_CurrentProcess), duration%)
	Return True
End Function


Function BVM_ResumeProcess%(hProcess%)
	Local process.BVM_TProcess
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		BVM_SetLastError(BVM_ERR_INVALID_PROCESS%, "Invalid process handle")
		Return False
	End If
	process\pauseTimeOutTime% = 0
	Return True
End Function


Function BVM_IsProcessPaused_%(process.BVM_TProcess)
	Return (process\pauseTimeOutTime%) - (process\lastExecTime%) > 0
End Function


Function BVM_IsProcessPaused%(hProcess%)
	Local process.BVM_TProcess
	process = Object.BVM_TProcess(hProcess%)
	If process = Null Then
		Return False
	End If
	Return BVM_IsProcessPaused_(process)
End Function


Function BVM_CollectHostObjectGarbage%()
	Local hObj%
	Local typeId%
	Repeat 
		typeId% = BVM_GetGarbageHostObjectTypeId()
		If typeId% < 0 Then
			Exit
		End If
		hObj% = BVM_ExtractGarbageHostObject()
		Select typeId%
			Default 
		End Select
	Forever
End Function

