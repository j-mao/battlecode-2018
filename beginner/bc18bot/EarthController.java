// Any non-private member within UniverseController.java can be accessed directly

package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

import bc18bot.UniverseController;

public class EarthController extends UniverseController
{
	static VecUnit units;
	static long numunits;

	public static void runTurn() throws Exception
	{
		units = gc.myUnits();
		numunits = units.size();
		for (long i = 0;i < numunits;i++)
		{
			// get unit info
			Unit unit = units.get(i);
			int unitId = unit.id();
			if (!unit.location().isOnPlanet(Planet.Earth))
			{
				continue; // NOTE: this may not be necessary, but who knows what gc.myUnits will return
			}

			MapLocation curLoc = unit.location().mapLocation();

			//if (unit.unitType() == UnitType.Factory && unit.structureIsBuilt() == 1)
			if (unit.unitType() == UnitType.Factory)
			{
				if (tryToUnload(unitId)) {
					continue;
				}
				if (tryProduceRobot(unitId)) {
					continue;
				}
			}

			if (unit.unitType() == UnitType.Worker) {
				// can I farm karbonite?
				if (tryHarvest(unitId)) {
					continue;
				}
				// can I repair an existing blueprint?
				if (tryRepairBlueprint(unitId)) {
					continue;
				}
				// can I build an existing blueprint? [or walk towards one]
				if (tryBuildBlueprint(unitId)) {
					continue;
				}
				// can I replicate?
				if (tryReplicate(unitId)) {
					continue;
				}
				// should && can I lay a blueprint?
				if ((numFactories+numFactoryBlueprints)*8 <= numRangers)
				{
					if (tryToBlueprint(unitId)) {
						continue;
					}
				}
			}

			if (unit.unitType() == UnitType.Knight || unit.unitType() == UnitType.Ranger || unit.unitType() == UnitType.Mage)
			{
				// attack
				if (gc.isAttackReady(unitId)) {
					if (tryAttack(unitId)) {
						continue;
					}
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
			System.out.printf("Unit ID %d harvested %d\n", unitId, bestKarbonite);
			System.out.printf("Before: %d\n", gc.karbonite());
			gc.harvest(unitId, bestFarm);
			System.out.printf("After: %d\n", gc.karbonite());
			return true;
		} else {
			return false;
		}
	}

	static boolean tryReplicate(int unitId) throws Exception
	{
		// returning false for the time being because replay files will explode to >100MB otherwise
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
			return true;
		}
		return false;
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
		for (long j = 0;j < numunits;j++)
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

	static boolean tryBuildBlueprint(int unitId) throws Exception
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
		for (long j = 0;j < numunits;j++)
		{
			Unit potentialStructure = units.get(j);
			if (potentialStructure.unitType() == UnitType.Factory)
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
			} else if (gc.isMoveReady(unitId) && gc.canMove(unitId, whereIsIt))
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
			} else if (curLoc.distanceSquaredTo(whatOnSquare.location().mapLocation()) < curLoc.distanceSquaredTo(bestAttack.location().mapLocation()))
			{
				bestAttack = whatOnSquare;
			}
		}
		if (bestAttack == null)
		{
			return false;
		}
		if (gc.canAttack(unitId, bestAttack.id()))
		{
			gc.attack(unitId, bestAttack.id());
			return true;
		}
		Direction whereIsIt = curLoc.directionTo(bestAttack.location().mapLocation());
		if (gc.isMoveReady(unitId) && gc.canMove(unitId, whereIsIt))
		{
			gc.moveRobot(unitId, whereIsIt);
			return true;
		}
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
}
