// Any non-private member within UniverseController.java can be accessed directly

package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

import bc18bot.UniverseController;

public class MarsController extends UniverseController
{
	public static void runTurn() throws Exception
	{
		getUnitSets();
		checkForEnemyUnits();

		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Rocket) {
				tryToUnload(units.get(i).id());
			}
		}
		getUnitSets();

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
						tryMoveAttacker(units.get(i));
					} else
					{
						//tryMoveForFood(units.get(i).id());
					}
				}
			} else if (roundNum > 30 && units.get(i).unitType() == UnitType.Worker) {
				if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace())
				{
					if (roundNum%150 == 0 && notYetReplicated)
					{
						MapLocation result = tryReplicate(units.get(i).id());
						if (result != null) notYetReplicated = false;
					}
					tryHarvest(units.get(i).id());
					tryMoveForFood(units.get(i).id());
				}
			}
		}
	}
}
