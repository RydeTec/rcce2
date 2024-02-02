; league Fixture generator
; fairly simple routine for creating a 2 leg league fixture table.

; (c) Graham Kennedy 2001




Const teams=16

Dim meets(teams,teams)
Dim fixture(teams)
Dim work(teams)
Dim stack(teams)

; generate single Fixture

; team : team to generate fixture for
; all  : 0 = generate legal fixture (ie. team not playing this week)
;        1 = generate any fixture (ie. even if other team is playing this week)
Function GenerateFixture(team,all)
	result = 0
	maxfix = 0
	; first generate list of available fixtures
	For i = 1 To teams
		If meets(team,i) = 0 
			If (all = 1) Or (fixture(i) = 0) Then
				maxfix = maxfix+1
				work(maxfix) = i
			End If
		End If
	Next
	
	; if there are any, select one as the fixture
	If maxfix > 0 Then 
		result = work(Rnd(1,maxfix))
	End If
	
	Return result

End Function

; push a team onto the work stack
Function PushTeam(team)
	i = 1
	While i <= teams 
		If stack(i) = 0 Then 
			stack(i) = team
			i = teams
		End If
		
		i = i + 1
	Wend
	
End Function

; pop a team off the work stack
Function PopTeam(team)
	i = 1
	mode = 0
	While i <= teams
		Select mode
			Case 0		  
				If stack(i) = team Then 
					mode = 1
				End If
			Case 1
				stack(i-1) = stack(i)
		End Select
		i = i + 1
	Wend
	stack(teams) = 0
End Function

; generate a list of fixtures
Function GenerateFixtures()
	; first clear any previous fixtures, and create work stack
	
	; to stop the first teams in the league getting the first home ties, 
	rebuild = 1
	While rebuild = 1
		rebuild = 0
		
		
		For i = 1 To teams
			fixture(i) = 0		
			pushteam(i)
		Next
		
		; quick and dirty way of making sure that the lower numbered teams are
		; not guarenteed to get home ties at first, this just randomises the
		; sequence of teams on the work stack
		For i = 1 To teams*2
			f = Rnd(1,teams)
			popteam(f)
			pushteam(f)
		Next

		; loop while there are still teams to process
		While stack(1) > 0 
			i = stack(1)
			
			f = generatefixture(i,0)
			If f > 0 Then
			  ; ok found a fixture, store it off and remove the teams involved from the work stack
			
			  fixture(i) = f
			  fixture(f) = -i
			
			  popteam(i)
			  popteam(f)
			  failcount = 0
			Else
				;oh ho, no fixture can be generated for this one...
				
				; find someone this team can play
				f = generatefixture(i,1)
				If f > 0 Then
					; see who this team is currently playing
					f2 = Abs(fixture(f))
					; take this fixture, and put the other team back on the work stack
					fixture(f2) = 0
					pushteam(f2)
					fixture(i) = f
					fixture(f) = -i
					popteam(i)
					failcount = 0
				Else
					; throw it to the back of the queue
					popteam(i)
					pushteam(i) 
					failcount = failcount + 1
					; there is a situation where the fixtures are not falling right, so
					; if this happens, rebuild the fixture list from scratch...
					If stack(1) = i Or failcount > teams Then 
						popteam(i)
						rebuild = 1
					End If
	
				End If
			End If
		
		Wend
		
	Wend 

	; record these fixtures, so that they cannot be generated again.
	For i = 1 To teams
		If fixture(i) > 0 Then 
			meets(i,fixture(i)) = 1
		End If
	
	Next

End Function


; first make sure no team can play itself by marking the fixture as played
For i = 1 To teams 
	meets(i,i) = 1
Next 

; generate each weeks fixtures
For week = 1 To (teams-1)*2

	; ok generate a weeks fixtures
	generatefixtures()
	
	Print "Week:"+week
	For i = 1 To teams
	
		; if the fixture > 0 then this is a real fixture (not the opposing team fixture)
		If fixture(i)>0 Then 
			; only print real fixtures
			Print "Team:"+i+" Plays Team:"+fixture(i)
		End If
		
		
	
	Next
	Print "-----------------------------------"
	
	a$ = WaitKey()
Next