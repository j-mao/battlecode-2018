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

			if (unit.unitType() == UnitType.Worker) {
				// can I farm karbonite?
				if (tryHarvest(unitId)) {
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
				// can I lay a blueprint?
				if (tryToBlueprint(unitId)) {
					continue;
				}
			}

			// move randomly if possible
			if (gc.isMoveReady(unitId))
			{
				for (Direction d: directions)
				{
					if (gc.canMove(unitId, d))
					{
						gc.moveRobot(unitId, d);
						break;
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
			gc.harvest(unitId, bestFarm);
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
		/*Direction where = null;
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
		}*/
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
			if (potentialStructure.unitType() == UnitType.Factory && potentialStructure.structureIsBuilt() == 1)
				// TODO figure out why structureIsBuilt() is returning a short and not a boolean
			{
				if (blueprintId == -1)
				{
					blueprintId = potentialStructure.id();
					whereIsIt = curLoc.directionTo(potentialStructure.location().mapLocation());
				} else if (curLoc.distanceSquaredTo(potentialStructure.location().mapLocation()) < curLoc.distanceSquaredTo(units.get(blueprintId).location().mapLocation()))
				{
					blueprintId = potentialStructure.id();
					whereIsIt = curLoc.directionTo(potentialStructure.location().mapLocation());
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
			return true;
		}
		return false;
	}
}
