// Any non-private member within UniverseController.java can be accessed directly


// 1. 1 worker  -> 2 workers
// 2. 2 workers -> 3 workers
// 3. farm
// 4. build factory together
// do some stuff

package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

import bc18bot.UniverseController;

public class EarthController extends UniverseController
{
	static int numStartingWorkers;
	static Set<Integer> loadedUnits;

	public static void runTurn() throws Exception
	{
		getUnitSets();
		checkForEnemyUnits();

		loadedUnits = new HashSet<Integer>();

		if (roundNum == 1)
		{
			gc.queueResearch(UnitType.Rocket);
			numStartingWorkers = numunits;
		}

		// act on factories
		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Factory && units.get(i).structureIsBuilt() > 0)
			{
				tryProduceRobot(units.get(i).id());
				tryToUnload(units.get(i).id());
			}
		}
		getUnitSets();

		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Rocket && units.get(i).structureIsBuilt() > 0)
			{
				tryToLoadRocket(units.get(i));
				// should I take off?
				if (units.get(i).health() <= 70 || // in danger of being destroyed
					(gc.orbitPattern().duration(roundNum)+roundNum <= gc.orbitPattern().duration(roundNum+1)+roundNum+1 && // not worth waiting any more
					(units.get(i).structureGarrison().size() == units.get(i).structureMaxCapacity() || // full
					roundNum >= 749))) // about to flood
				{
					MapLocation where;
					do
						where = new MapLocation(Planet.Mars, (int)(Math.abs(rand.nextInt())%MarsMap.getWidth()), (int)(Math.abs(rand.nextInt())%MarsMap.getHeight()));
					while (MarsMap.isPassableTerrainAt(where) == 0);
					gc.launchRocket(units.get(i).id(), where);
				}
			}
		}
		getUnitSets();

		boolean notYetReplicated = true;
		for (int i = 0;i < numunits;i++)
		{
			if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace() && !loadedUnits.contains(units.get(i).id()))
			{
				
				if (units.get(i).unitType() == UnitType.Ranger)
				{
					/*
					tryAttack(units.get(i).id());
					// calculate number of nearby friendly rangers
					int numNearbyFriendlyRangers = 0;
					VecUnit nearbyRangers = gc.senseNearbyUnitsByType(units.get(i).location().mapLocation(), units.get(i).attackRange(), UnitType.Ranger);
					for (long z = 0;z < nearbyRangers.size();z++) if (nearbyRangers.get(z).team() == myTeam)
						numNearbyFriendlyRangers++;
					if (numNearbyFriendlyRangers*8 >= EarthMap.getWidth()+EarthMap.getHeight())
					{
						tryMoveAttacker(units.get(i));
					} else
					{
						//tryMoveForFood(units.get(i));
					}*/

					runRanger(units.get(i));

				} else if (units.get(i).unitType() == UnitType.Worker)
				{
					runEarthWorker(units.get(i));
				}
			}
		}
	}

	private static void runEarthWorker (Unit unit) {
        boolean doneAction = false;
        boolean doneMovement = false;

        // try to replicate
        // TODO: limit to 8 workers
        // TODO: only replicate if there is enough money
        // TODO: improve metric of "enough money"
        //System.out.println("ability cooldown = " + unit.abilityCooldown() + ", heat = " + unit.abilityHeat());
        // check we don't have too many workers, and that we have enough money for a factory
        // TODO: make this logic better. e.g. we might need money for a factory in the future
        // TODO: Find out where cost constants are and replay this "100 + 15" with those xd.
        if (numWorkers < 7 && gc.karbonite() > 100 + 15) {
            shuffleDirOrder();
            for (int i = 0; i < 8; i++) {
                if (gc.canReplicate(unit.id(), directions[randDirOrder[i]])) {
                    gc.replicate(unit.id(), directions[randDirOrder[i]]);
                    // TODO: add created units to units list so they can do something immediately on the turn they're created
                    MapLocation loc = unit.location().mapLocation().add(directions[randDirOrder[i]]);
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                    // TODO: test on a rectangular map to make sure I didn't mess up any y, x orders anywhere
                    doneAction = true;
                    break;
                }
            }
        }

        // try to build an adjacent blueprint
        if (!doneAction) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if ((other.unitType() == UnitType.Factory || other.unitType() == UnitType.Rocket) && gc.canBuild(unit.id(), other.id())) {
                    gc.build(unit.id(), other.id());
                    doneAction = true;
                    doneMovement = true;
                    break;
                }
            }
        }

        // try to blueprint
        if (!doneAction) {
            // TODO: make this all directions
            // TODO: make this choose a square with more space
            Direction dir = directions[rand.nextInt(8)];
			UnitType toBlueprint = UnitType.Factory;
			if (gc.researchInfo().getLevel(UnitType.Rocket) >= 1)
			{
				toBlueprint = UnitType.Rocket;
			}
            if (factoriesBeingBuilt.isEmpty() && gc.canBlueprint(unit.id(), toBlueprint, dir)) {
                gc.blueprint(unit.id(), toBlueprint, dir);
                MapLocation loc = unit.location().mapLocation().add(dir);
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                doneAction = true;
                doneMovement = true;
                Unit other = gc.senseUnitAtLocation(loc);
                //System.out.println("The factory I just built is here: " + other.toString());
                // TODO: continue this
                // TODO: store ArrayList of current factory blueprints and implement workers moving towards them
                // TODO: implement worker replication
                factoriesBeingBuilt.add(gc.senseUnitAtLocation(loc));
            }
        }

        // if there is a factory blueprint somewhere, and you have less than 3 complete factories, try to move towards it to help build it
        if (!doneMovement && !factoriesBeingBuilt.isEmpty() && numFactories - factoriesBeingBuilt.size() < 3) {
            // set done to true so we don't move randomly
            doneMovement = true;

            // bfs to within distance 1 of every square because we want to move within distance 1 of a factory
            bfs(unit.location().mapLocation(), 1);

            // TODO: change this to closest factory
            Unit factory = factoriesBeingBuilt.get(0);
            MapLocation loc = factory.location().mapLocation();
            Direction dir = directions[bfsDirectionIndexTo[loc.getY()][loc.getX()]];
            //System.out.println("worker moving to factory in dir " + dir.toString());

            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                doMoveRobot(unit, dir);
            }
        }

        // otherwise, if you have 3 complete factories, try to move towards minerals
        // TODO: make this >= 3 thing better
        // TODO: ie make it not horrible in special case maps LuL
        if (!doneMovement && numFactories - factoriesBeingBuilt.size() >= 3) {
            // TODO: unify bfs logic so that you don't unnecessarily call it multiple times
            bfs(unit.location().mapLocation(), 1);

            if (bfsClosestKarbonite != null) {
                Direction dir = directions[bfsDirectionIndexTo[bfsClosestKarbonite.getY()][bfsClosestKarbonite.getX()]];

                System.out.println("worker moving towards minerals!");
                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                }
            }
        }

        // otherwise move randomly
        if (!doneMovement) {
            Direction dir = directions[rand.nextInt(8)];

            // Most methods on gc take unit IDs, instead of the unit objects themselves.
            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                doMoveRobot(unit, dir);
            }
        }

        if (!doneAction) {
            for (int i = 0; i < 9; i++) {
                if (gc.canHarvest(unit.id(), directions[i])) {
                    //System.out.println("harvesting!");
                    gc.harvest(unit.id(), directions[i]);
                }
            }
        }
    }

	static boolean tryRepairBlueprint (int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Worker) {
			throw new Exception("Non-worker tryRepairBlueprint() call");
		}

		int blueprintId = -1;
		Direction whereIsIt = null;

		// what an O(N) complexity
		for (int j = 0;j < numunits;j++)
		{
			Unit potentialStructure = units.get(j);
			if (potentialStructure.unitType() == UnitType.Factory || potentialStructure.unitType() == UnitType.Rocket)
			{
				if (potentialStructure.structureIsBuilt() > 0 && potentialStructure.health() < potentialStructure.maxHealth() && curLoc.isAdjacentTo(potentialStructure.location().mapLocation()))
				{
					blueprintId = potentialStructure.id();
					whereIsIt = curLoc.directionTo(potentialStructure.location().mapLocation());
					break;
				}
			}
		}
		if (blueprintId != -1)
		{
			if (gc.canRepair(unitId, blueprintId))
			{
				gc.repair(unitId, blueprintId);
				return true;
			}
		}
		return false;
	}

	static boolean tryBuildBlueprint(int unitId, boolean willingToMove) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Worker) {
			throw new Exception("Non-worker tryBuildBlueprint() call");
		}

		int blueprintId = -1;
		Direction whereIsIt = null;

		// what an O(N) complexity
		for (int j = 0;j < numunits;j++)
		{
			Unit potentialStructure = units.get(j);
			if (potentialStructure.unitType() == UnitType.Factory || potentialStructure.unitType() == UnitType.Rocket)
			{
				if (potentialStructure.structureIsBuilt() == 0)
				{
					if (blueprintId == -1)
					{
						blueprintId = potentialStructure.id();
						whereIsIt = curLoc.directionTo(potentialStructure.location().mapLocation());
					} else if (curLoc.distanceSquaredTo(potentialStructure.location().mapLocation()) < curLoc.distanceSquaredTo(potentialStructure.location().mapLocation()))
					{
						blueprintId = potentialStructure.id();
						whereIsIt = curLoc.directionTo(potentialStructure.location().mapLocation());
					}
				}
			}
		}
		if (blueprintId != -1)
		{
			if (gc.canBuild(unitId, blueprintId))
			{
				gc.build(unitId, blueprintId);
				return true;
			} else if (willingToMove && gc.isMoveReady(unitId) && gc.canMove(unitId, whereIsIt))
			{
				doMoveRobot(unit, whereIsIt);
				return true;
			}
		}
		return false;
	}

	static boolean tryToBlueprint(int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Worker) {
			throw new Exception("Non-worker tryToBlueprint() call");
		}

		UnitType toBlueprint = UnitType.Factory;
		if (gc.researchInfo().getLevel(UnitType.Rocket) >= 1)
			toBlueprint = UnitType.Rocket;
		Direction where = null;
		for (Direction d: directions)
		{
			if (gc.canBlueprint(unitId, toBlueprint, d))
			{
				where = d;
				break;
			}
		}
		if (where != null)
		{
			gc.blueprint(unitId, toBlueprint, where);

			MapLocation loc = unit.location().mapLocation().add(where);
			hasFriendlyUnit[loc.getY()][loc.getX()] = true;

			if (toBlueprint == UnitType.Factory)
			{
				numFactoryBlueprints++;

				factoriesBeingBuilt.add(gc.senseUnitAtLocation(loc));
			} else
			{
				numRocketBlueprints++;
			}
			return true;
		}
		return false;
	}

	static boolean tryProduceRobot(int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Factory) {
			throw new Exception("Non-factory tryProduceRobot() call");
		}

		UnitType toProduce = UnitType.Ranger;

		if (numWorkers == 0) {
			toProduce = UnitType.Worker;
		}

		if (gc.canProduceRobot(unitId, toProduce))
		{
			gc.produceRobot(unitId, toProduce);
			return true;
		}
		return false;
	}

	static void tryToLoadRocket (Unit unit) throws Exception
	{
		//possibly slow performance
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Rocket) {
			throw new Exception("Non-rocket tryToLoadRocket() call");
		}

		VecUnit loadableUnits = gc.senseNearbyUnitsByTeam(unit.location().mapLocation(), unit.visionRange(), myTeam);
		// bring along one worker
		if (unit.structureGarrison().size() == 0)
		{
			for (long i = 0;i < loadableUnits.size();i++) if (loadableUnits.get(i).unitType() == UnitType.Worker) {
				if (gc.canLoad(unit.id(), loadableUnits.get(i).id())) {
					gc.load(unit.id(), loadableUnits.get(i).id());
					loadedUnits.add(unit.id());
					break;
				}
			}
		}
		// bring along as many rangers as you can
		for (long i = 0;i < loadableUnits.size();i++) if (loadableUnits.get(i).unitType() == UnitType.Ranger) {
			if (gc.canLoad(unit.id(), loadableUnits.get(i).id())) {
				gc.load(unit.id(), loadableUnits.get(i).id());
				loadedUnits.add(unit.id());
			}
		}
	}
}
