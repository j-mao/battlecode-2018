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

			// can I farm karbonite?
			if (unit.unitType() == UnitType.Worker)
			{
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
				if (bestKarbonite > 0)
				{
					gc.harvest(unitId, bestFarm);
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
}
