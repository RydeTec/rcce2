Strict
EnableGC

Type ActorInstance
	Field SourceSP, ServerArea
End Type

Type Area
End Type

Type AreaInstance
	Field Area.Area
	Field SpawnLast[999], Spawned[999]
End Type

Include "Modules\SpawnTracking.bb"

Test testSyncAreaSpawnCountsRebuildsOccupancyFromLiveActors()
	Local area.Area = New Area()

	Local firstZone.AreaInstance = New AreaInstance()
	firstZone\Area = area
	firstZone\Spawned[4] = 9

	Local secondZone.AreaInstance = New AreaInstance()
	secondZone\Area = area
	secondZone\Spawned[2] = 7
	secondZone\Spawned[9] = 3

	Local actorOne.ActorInstance = New ActorInstance()
	actorOne\SourceSP = 4
	actorOne\ServerArea = Handle(firstZone)

	Local actorTwo.ActorInstance = New ActorInstance()
	actorTwo\SourceSP = 4
	actorTwo\ServerArea = Handle(firstZone)

	Local actorThree.ActorInstance = New ActorInstance()
	actorThree\SourceSP = 2
	actorThree\ServerArea = Handle(secondZone)

	Local ignored.ActorInstance = New ActorInstance()
	ignored\SourceSP = -1
	ignored\ServerArea = Handle(secondZone)

	Local detached.ActorInstance = New ActorInstance()
	detached\SourceSP = 1
	detached\ServerArea = 0

	SyncAreaSpawnCounts()

	Assert(firstZone\Spawned[4] = 2)
	Assert(secondZone\Spawned[2] = 1)
	Assert(firstZone\Spawned[0] = 0)
	Assert(secondZone\Spawned[9] = 0)

	Delete Each ActorInstance
	Delete Each AreaInstance
	Delete Each Area
End Test
