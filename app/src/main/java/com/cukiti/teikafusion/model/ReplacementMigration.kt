package com.cukiti.teikafusion.model

/** Retired flags are readable, but never resume their effects in the new level rules. */
object ReplacementMigration {
    fun restore(s: GameState): GameState {
                val grid = s.grid.mapValues { (p, c) ->
            if (c !is CellContent.Wood) c else c.copy(stubborn = false, magnet = false, barrel = false,
                dominoDir = -1)
        }
        fun ordinaryPiece(p: Piece) = p.copy(isMagnet = false, barrelMask = emptyList())
        val trip = s.clawTrip?.takeIf { ClawRules.valid(s,it) }
        val bodies=s.teikaBodySaves.map { b ->
            val cancelled=s.clawTrip?.takeIf { it.bodyId==b.id && trip==null }
            val legacy=s.elevatorTrip?.takeIf { it.bodyId==b.id }
            when {
                cancelled!=null && cancelled.fromX.isFinite() && cancelled.fromY.isFinite() -> b.copy(x=cancelled.fromX,y=cancelled.fromY)
                legacy!=null && legacy.x.isFinite() && legacy.fromY.isFinite() -> b.copy(x=legacy.x,y=legacy.fromY)
                else -> b
            }
        }

        val reserveActive=ReserveRules.appliesTo(s.level)
        val cutPieces = s.availablePieces + listOfNotNull(s.cutReserve).filter { half -> s.availablePieces.none { it.id == half.id } }
        val playable=if(reserveActive) cutPieces.take(3) else cutPieces
        val held=if(reserveActive) (s.reservePieces+cutPieces.drop(3)).distinctBy { it.id }.filter { p -> playable.none { it.id==p.id } } else s.reservePieces
        // Never discard conserved blocks, even if a compatibility queue survives outside its usual rule.
        val migrated=s.copy(grid=grid,availablePieces=playable, reservePieces=held, routes=emptyList(), replacementRulesVersion=12)
        return migrated.copy(grid = grid, replacementRulesVersion = 12,
            trampolineCell=null, trampolineCharged=false,
            pinballCharged=PinballRules.appliesTo(s.level) && (s.pinballCharged || s.trampolineCharged),
            pinballFlight=s.pinballFlight?.takeIf { f -> PinballRules.appliesTo(s.level) && PinballRules.valid(f) &&
                s.plantAbduction?.teikaId!=f.bodyId && s.teikaBodySaves.any { it.id==f.bodyId && it.type.level in 1..4 && it.frozenTurns==0 && it.bubble==0 } },
            reserveSwapRefill = s.reserveSwapRefill.coerceIn(-1,s.trayRefillCount.coerceAtLeast(0)),
            routes = emptyList(),
            portalSwapCharged = PortalSwapRules.appliesTo(s.level) && s.portalSwapCharged && !s.portalSwapPending,
            portalSwapPending = PortalSwapRules.appliesTo(s.level) && s.portalSwapPending,
            orbitCharged = OrbitRules.appliesTo(s.level) && s.orbitCharged && !s.orbitPending,
            orbitPending = OrbitRules.appliesTo(s.level) && s.orbitPending,
            routeAbilityCompanionId = s.routeAbilityCompanionId?.takeIf { PortalSwapRules.appliesTo(s.level) },
            routeSkillEarned = PortalSwapRules.appliesTo(s.level) && s.routeSkillEarned,
            routeFreeCompanionId = s.routeFreeCompanionId?.takeIf { PortalSwapRules.appliesTo(s.level) && s.routeSkillEarned },
            slidePendingCells = emptySet(),
            slideWave = 0,
            turbineCharged = TurbineRules.appliesTo(s.level) && s.turbineCharged && s.turbineGust==null,
            turbineGust = s.turbineGust?.takeIf { TurbineRules.appliesTo(s.level) && TurbineRules.valid(it.beam,s.gridW,s.gridH) && it.elapsedMs.isFinite() && it.elapsedMs in 0f..TurbineRules.DURATION_MS }?.let { gust ->
                val pairedIds=HashSet<String>()
                gust.copy(velocities=gust.velocities.filter { v -> v.vx.isFinite() && v.vy.isFinite() && s.teikaBodySaves.any { it.id==v.id } }.distinctBy { it.id }.take(64),
                    fusions=gust.fusions.distinct().take(32).filter { pair ->
                        val a=s.teikaBodySaves.find { it.id==pair.aId }; val b=s.teikaBodySaves.find { it.id==pair.bId }
                        val valid=a!=null && b!=null && a.id!=b.id && a.id !in pairedIds && b.id !in pairedIds &&
                            listOf(a.x,a.y,b.x,b.y).all { it.isFinite() } && a.type==b.type && a.type.level in 1..4 &&
                            kotlin.math.hypot(a.x-b.x,a.y-b.y)<=(a.type.level+b.type.level)*.5f+.1f &&
                            (!s.ninjaCutActive || (a.x<4f)==(b.x<4f)) &&
                            a.frozenTurns==0 && b.frozenTurns==0 && a.bubble==0 && b.bubble==0 &&
                            s.plantAbduction?.teikaId !in listOf(a.id,b.id)
                        if(valid) { pairedIds.add(pair.aId); pairedIds.add(pair.bId) }
                        valid
                    })
            },
            relayCharged = RelayRules.appliesTo(s.level) && s.relayCharged,
            relayPendingShot = if(RelayRules.appliesTo(s.level) && s.relayPendingFootprint.isNotEmpty()) s.relayPendingShot?.let {
                if(RelayRules.valid(it,s.relayPendingFootprint,s.gridW,s.gridH,if(s.ninjaCutActive) 4 else -1)) it
                else RelayShot(0,emptyList(),emptyList())
            } else null,
            relayPendingFootprint = if(RelayRules.appliesTo(s.level)) s.relayPendingFootprint.filterTo(HashSet()) { it in grid && it.x in 0 until s.gridW && it.y in 0 until s.gridH }.take(64).toSet() else emptySet(),
            relayProtectedCells = if(RelayRules.appliesTo(s.level) && s.relayPendingFootprint.isNotEmpty()) s.relayProtectedCells.filterTo(HashSet()) { it in grid }.take(64).toSet() else emptySet(),
            pinataCell = null, pinataHits = 0, pinataDir = 1, pinataWave = 0,
            glassCells = emptySet(), glassCracked = emptySet(), glassWave = 0,
            surfWave = if(SurfRules.appliesTo(s.level)) s.surfWave.coerceAtLeast(0) else 0,
            cometMark = null, cometWave = 0,
            eggCell = null, eggCracks = 0, eggWave = 0,
            elevatorCharged = false, elevatorTrip = null,
            clawCharged = ClawRules.appliesTo(s.level) && trip==null && (s.clawCharged || s.elevatorCharged || s.elevatorTrip!=null || s.clawTrip!=null),
            clawTrip = trip, teikaBodySaves = bodies,
            trailCells = emptyList(),
            ballCharged = WreckingBallRules.appliesTo(s.level) && s.ballCharged && !s.ballPending,
            ballPending = WreckingBallRules.appliesTo(s.level) && s.ballPending,
            forgeCharged = ForgeRules.appliesTo(s.level) && s.forgeCharged && !s.forgePending,
            forgePending = ForgeRules.appliesTo(s.level) && s.forgePending,
            reactorCharged = ReactorRules.appliesTo(s.level) && s.reactorCharged && !s.reactorPending,
            reactorPending = ReactorRules.appliesTo(s.level) && s.reactorPending,
            avalanchePending = AvalancheRules.appliesTo(s.level) && s.avalanchePending,
            avalancheCharge = if (AvalancheRules.appliesTo(s.level)) s.avalancheCharge.coerceIn(0,1) else 0,
            cutReserve = null, cutActiveId = null, cutRefill = -1,
            compactorCharged = CompactorRules.appliesTo(s.level) && s.compactorCharged && !s.compactorPending,
            compactorPending = CompactorRules.appliesTo(s.level) && s.compactorPending,
            availablePieces = playable.map(::ordinaryPiece), reservePieces = held.map(::ordinaryPiece),
            pieceHorizon = s.pieceHorizon.map(::ordinaryPiece), magnetTurnsLeft = 0,
            moldAt = null, moldShape = emptyList(), moldWave = 0,
            constellationPoints = if (ConstellationRules.appliesTo(s.level) && s.constellationPoints.isEmpty())
                ConstellationRules.points(s.level, s.constellationWave, grid.filterValues { it is CellContent.Empty }.keys - s.unplaceableCells, grid.filterValues { it is CellContent.Wood }.keys)
                else s.constellationPoints,
            )
    }
}
