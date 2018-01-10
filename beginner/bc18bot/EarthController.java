// Any non-private member within UniverseController.java can be accessed directly

package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

import bc18bot.UniverseController;

public class EarthController extends UniverseController
{

	public static void runTurn() throws Exception
	{
		VecUnit units = gc.myUnits();
		final long numunits = units.size();
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

			boolean harvestSuccessful = false;
			// can I farm karbonite?
			if (unit.unitType() == UnitType.Worker) {
				harvestSuccessful = tryHarvest(unitId);

				if (!harvestSuccessful) {
					tryMoveForFood(unitId);
				}
			}
		}
	}

	public static boolean tryHarvest (int unitId) throws Exception {
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

					if (earthMap.onMap(tmpLook)) {
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
