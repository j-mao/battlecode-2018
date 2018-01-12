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
	static VecUnit units;
	static VecUnit allUnits;
	static int numunits;
	static int numallunits;
	static int numStartingWorkers;

	static MapLocation[][] initialWorkers;

	static MapLocation lastSeenEnemy;

	public static void runTurn() throws Exception
	{
		units = gc.myUnits();
		numunits = (int)units.size();
		allUnits = gc.units();
		numallunits = (int)allUnits.size();

		for (int i = 0;i < numallunits;i++) if (allUnits.get(i).team() == enemyTeam)
		{
			if (!allUnits.get(i).location().isInGarrison() && !allUnits.get(i).location().isInSpace())
			{
				lastSeenEnemy = allUnits.get(i).location().mapLocation();
				break;
			}
		}

		if (roundNum == 1)
		{
			//gc.queueResearch(UnitType.Rocket);
			VecUnit initialUnits = EarthMap.getInitial_units();
			for (long i = 0;i < initialUnits.size();i++) if (initialUnits.get(i).team() == enemyTeam)
			{
				lastSeenEnemy = initialUnits.get(i).location().mapLocation();
				break;
			}
			initialWorkers = new MapLocation[3][numunits];
			numStartingWorkers = numunits;
			for (int i = 0;i < numunits;i++)
			{
				initialWorkers[0][i] = units.get(i).location().mapLocation();
				initialWorkers[1][i] = tryReplicate(units.get(i).id());
				if (initialWorkers[1][i] == null) System.out.println("replication failed");
			}
			return;
		}
		if (roundNum == 2)
		{
			for (int i = 0;i < numStartingWorkers;i++)
			{
				if (initialWorkers[1][i] != null)
				{
					initialWorkers[2][i] = tryReplicate(gc.senseUnitAtLocation(initialWorkers[1][i]).id());
					if (initialWorkers[2][i] == null) System.out.println("replication failed");
				}
			}
			return;
		}
		if (roundNum <= 30)
		{
			for (int i = 0;i < numStartingWorkers;i++)
			{
				int done = 0;
				done += tryBuildBlueprint(gc.senseUnitAtLocation(initialWorkers[0][i]).id(), false)?1:0;
				if (initialWorkers[1][i] != null)
					done = done += tryBuildBlueprint(gc.senseUnitAtLocation(initialWorkers[1][i]).id(), false)?1:0;
				if (initialWorkers[2][i] != null)
					done = done += tryBuildBlueprint(gc.senseUnitAtLocation(initialWorkers[2][i]).id(), false)?1:0;
				if (done > 0)
				{
					System.out.println("Round "+roundNum+": build blueprint x"+done);
					continue;
				}
				if (tryToBlueprint(gc.senseUnitAtLocation(initialWorkers[0][i]).id()))
				{
					if (initialWorkers[1][i] != null)
						tryBuildBlueprint(gc.senseUnitAtLocation(initialWorkers[1][i]).id(), false);
					if (initialWorkers[2][i] != null)
						tryBuildBlueprint(gc.senseUnitAtLocation(initialWorkers[2][i]).id(), false);
					continue;
				}
				done += tryHarvest(gc.senseUnitAtLocation(initialWorkers[0][i]).id())?1:0;
				if (initialWorkers[1][i] != null)
					done += tryHarvest(gc.senseUnitAtLocation(initialWorkers[1][i]).id())?1:0;
				if (initialWorkers[2][i] != null)
					done += tryHarvest(gc.senseUnitAtLocation(initialWorkers[2][i]).id())?1:0;
				if (done > 0)
				{
					System.out.println("Round "+roundNum+": harvest x"+done);
					continue;
				}
				/*for (Direction d: directions)
				{
					ArrayList<Unit> things = new ArrayList<Unit>();
					things.add(gc.senseUnitAtLocation(initialWorkers[0][i]));
					if (initialWorkers[1][i] != null)
						things.add(gc.senseUnitAtLocation(initialWorkers[1][i]));
					if (initialWorkers[2][i] != null)
						things.add(gc.senseUnitAtLocation(initialWorkers[2][i]));
					ArrayList<Boolean> didMove = tryMovePackOfRobots(things, d);
					for (int j = 0;j < didMove.size();j++) if (didMove.get(j))
					{
						initialWorkers[j][i] = initialWorkers[j][i].add(d);
						done++;
					}
					if (done > 0)
						break;
				}*/
				if (done > 0)
				{
					System.out.println("Round "+roundNum+": move around x"+done);
					continue;
				}
			}
		}
		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Factory && units.get(i).structureIsBuilt() > 0) {
				tryToUnload(units.get(i).id());
				tryProduceRobot(units.get(i).id());
			}
		}
		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Rocket && units.get(i).structureIsBuilt() > 0) {
				tryToLoadRocket(units.get(i).id());
				if (units.get(i).structureGarrison().size() > 0) {
					MapLocation where;
					do
						where = new MapLocation(Planet.Mars, (int)(Math.abs(rand.nextInt())%MarsMap.getWidth()), (int)(Math.abs(rand.nextInt())%MarsMap.getHeight()));
					while (MarsMap.isPassableTerrainAt(where) == 0);
					gc.launchRocket(units.get(i).id(), where);
				}
			}
		}
		units = gc.myUnits();
		numunits = (int)units.size();
		boolean notYetReplicated = true;
		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Ranger) {
				if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace())
				{
					tryAttack(units.get(i).id());
					// calculate number of nearby friendly rangers
					int numNearbyFriendlyRangers = 0;
					VecUnit nearbyRangers = gc.senseNearbyUnitsByType(units.get(i).location().mapLocation(), units.get(i).attackRange(), UnitType.Ranger);
					for (long z = 0;z < nearbyRangers.size();z++) if (nearbyRangers.get(z).team() == myTeam)
						numNearbyFriendlyRangers++;
					if (numNearbyFriendlyRangers*8 >= EarthMap.getWidth()+EarthMap.getHeight())
					{
						tryMoveAttacker(units.get(i).id());
					} else
					{
						//tryMoveForFood(units.get(i).id());
					}
				}
			} else if (roundNum > 30 && units.get(i).unitType() == UnitType.Worker) {
				if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace())
				{
					//tryRepairBlueprint(units.get(i).id());
					//if (numWorkers*100 < EarthMap.getWidth()*EarthMap.getHeight()) tryReplicate(units.get(i).id());
					if (roundNum%150 == 0 && notYetReplicated)
					{
						MapLocation result = tryReplicate(units.get(i).id());
						if (result != null) notYetReplicated = false;
					}
					if (roundNum%150 == 0)
					{
						tryToBlueprint(units.get(i).id());
					}
					if (roundNum%3 <= 1)
					{
						tryBuildBlueprint(units.get(i).id(), true);
						tryHarvest(units.get(i).id());
					}
					if (roundNum%3 == 2)
					{
						tryHarvest(units.get(i).id());
						tryBuildBlueprint(units.get(i).id(), true);
					}
					tryMoveForFood(units.get(i).id());
				}
			}
		}
	}

	static boolean tryHarvest (int unitId) throws Exception {
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Worker) {
			throw new Exception("Non-worker tryHarvest() call");
		}

		long bestKarbonite = 0;
		Direction bestFarm = null;
		for (Direction d: directions)
		{
			if (gc.canHarvest(unitId, d))
			{
				long altKarbonite = gc.karboniteAt(curLoc.add(d));
				if (altKarbonite > bestKarbonite)
				{
					bestKarbonite = altKarbonite;
					bestFarm = d;
				}
			}
		}

		if (bestKarbonite > 0) {
			//System.out.printf("Unit ID %d harvested %d\n", unitId, bestKarbonite);
			//System.out.printf("Before: %d\n", gc.karbonite());
			gc.harvest(unitId, bestFarm);
			//System.out.printf("After: %d\n", gc.karbonite());
			return true;
		} else {
			return false;
		}
	}

	static MapLocation tryReplicate(int unitId) throws Exception
	{
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Worker) {
			throw new Exception("Non-worker tryReplicate() call");
		}
		Direction where = null;
		for (Direction d: directions)
		{
			if (gc.canReplicate(unitId, d))
			{
				where = d;
				break;
			}
		}
		if (where != null)
		{
			gc.replicate(unitId, where);
			numWorkers++;
			return curLoc.add(where);
		}
		return null;
	}

	static boolean tryRepairBlueprint(int unitId) throws Exception
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
			if (potentialStructure.unitType() == UnitType.Factory)
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
				gc.moveRobot(unitId, whereIsIt);
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
			if (toBlueprint == UnitType.Factory)
			{
				numFactoryBlueprints++;
			} else
			{
				numRocketBlueprints++;
			}
			return true;
		}
		return false;
	}

	static boolean tryToUnload(int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Factory) {
			throw new Exception("Non-factory tryToUnload() call");
		}

		if (unit.structureGarrison().size() == 0)
		{
			return false;
		}

		Direction where = null;
		for (Direction d: directions)
		{
			if (gc.canUnload(unitId, d))
			{
				where = d;
				break;
			}
		}
		if (where != null)
		{
			gc.unload(unitId, where);
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
		if (gc.canProduceRobot(unitId, toProduce))
		{
			gc.produceRobot(unitId, toProduce);
			return true;
		}
		return false;
	}

	static boolean tryAttack(int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Knight && unit.unitType() != UnitType.Ranger && unit.unitType() != UnitType.Mage) {
			throw new Exception("Non-attacker tryAttack() call");
		}

		Unit bestAttack = null;
		VecUnit options = gc.senseNearbyUnitsByTeam(curLoc, 50, enemyTeam);
		long numOptions = options.size();
		for (long i = 0;i < numOptions;i++)
		{
			Unit whatOnSquare = options.get(i);
			if (bestAttack == null)
			{
				bestAttack = whatOnSquare;
			} else if (curLoc.distanceSquaredTo(whatOnSquare.location().mapLocation()) <= unit.attackRange() && curLoc.distanceSquaredTo(bestAttack.location().mapLocation()) <= unit.attackRange())
			{
				if (attackPriority(bestAttack.unitType()) < attackPriority(whatOnSquare.unitType()))
					bestAttack = whatOnSquare;
			} else if (curLoc.distanceSquaredTo(whatOnSquare.location().mapLocation()) < curLoc.distanceSquaredTo(bestAttack.location().mapLocation()))
			{
				bestAttack = whatOnSquare;
			}
		}
		if (bestAttack == null)
		{
			return false;
		}
		if (gc.isAttackReady(unitId) && gc.canAttack(unitId, bestAttack.id()))
		{
			gc.attack(unitId, bestAttack.id());
			return true;
		}
		/*Direction whereIsIt = curLoc.directionTo(bestAttack.location().mapLocation());
		if (gc.isMoveReady(unitId) && gc.canMove(unitId, whereIsIt))
		{
			gc.moveRobot(unitId, whereIsIt);
			return true;
		}*/
		return false;
	}

	public static boolean tryMoveForFood (int unitId) throws Exception {
		if (!gc.isMoveReady(unitId)) {
			return false;
		}

		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();


		long bestKarbonite = 0;
		Direction bestMove = null;

		for (Direction d: directions) {
			if (gc.canMove(unitId, d)) {
				long altKarbonite = 0;

				for (Direction e : directions) {
					MapLocation tmpLook = curLoc.add(d).add(e);

					if (EarthMap.onMap(tmpLook)) {
						altKarbonite += gc.karboniteAt(tmpLook);
					}
				}

				if (altKarbonite > bestKarbonite) {
					bestKarbonite = altKarbonite;
					bestMove = d;
				}
			}
		}

		if (bestMove == null) {
			return false;
		}

		gc.moveRobot(unitId, bestMove);
		return true;
	}

	static void tryMoveAttacker(int unitId) throws Exception
	{
		if (gc.senseNearbyUnitsByTeam(gc.unit(unitId).location().mapLocation(), gc.unit(unitId).attackRange(), enemyTeam).size() > 0) return;
		if (gc.isMoveReady(unitId))
		{
			Direction prefer = gc.unit(unitId).location().mapLocation().directionTo(lastSeenEnemy);
			for (int i = 0;i < 4;i++)
			{
				Direction d = Direction.swigToEnum((prefer.swigValue()+i)%8);
				if (gc.canMove(unitId, d))
				{
					gc.moveRobot(unitId, d);
					break;
				}
				d = Direction.swigToEnum((prefer.swigValue()+8-i)%8);
				if (gc.canMove(unitId, d))
				{
					gc.moveRobot(unitId, d);
					break;
				}
			}
		}
	}

	static void tryToLoadRocket(int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Rocket) {
			throw new Exception("Non-rocket tryToLoadRocket() call");
		}

		VecUnit loadableUnits = gc.senseNearbyUnitsByTeam(gc.unit(unitId).location().mapLocation(), gc.unit(unitId).visionRange(), myTeam);
		for (long i = 0;i < loadableUnits.size();i++) if (loadableUnits.get(i).unitType() != UnitType.Rocket && loadableUnits.get(i).unitType() != UnitType.Worker && loadableUnits.get(i).unitType() != UnitType.Factory) {
			if (gc.canLoad(unitId, loadableUnits.get(i).id())) {
				gc.load(unitId, loadableUnits.get(i).id());
			}
		}
	}

	static int attackPriority(UnitType u)
	{
		if (u == UnitType.Ranger) return 10;
		if (u == UnitType.Mage) return 8;
		if (u == UnitType.Factory) return 7;
		if (u == UnitType.Knight) return 6;
		if (u == UnitType.Rocket) return 5;
		if (u == UnitType.Worker) return 3;
		if (u == UnitType.Healer) return 0;
		return -1;
	}
}
