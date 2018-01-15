package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

// use separate files for separate controllers to preserve code sanity
// only global functions are written here
import bc18bot.EarthController;
import bc18bot.MarsController;

public class UniverseController
{
	// the controller through which you do everything
	static GameController gc;

	// a random number generator
	static Random rand;

	// the current round number
	static int roundNum;

	// a list of all possible directions
	// there are 9 in total: 8 + center
	static final Direction[] directions = Direction.values();

	// initial maps
	static PlanetMap EarthMap;
	static PlanetMap MarsMap;

	// team defs
	static Team myTeam;
	static Team enemyTeam;

	// stats about quantities of friendly units
	static int numWorkers;
	static int numKnights;
	static int numRangers;
	static int numMages;
	static int numHealers;
	static int numFactoryBlueprints;
	static int numFactories;
	static int numRocketBlueprints;
	static int numRockets;

	// for debugging purposes
	private static final int scheduledDeath = 1000;

	public static void playGame()
	{
		// initialise
		gc = new GameController();
		rand = new Random();
		Planet myPlanet = gc.planet();
		roundNum = 1;
		EarthMap = gc.startingMap(Planet.Earth);
		MarsMap = gc.startingMap(Planet.Mars);
		myTeam = gc.team();
		enemyTeam = (myTeam == Team.Red) ? Team.Blue : Team.Red;

		// run game
		while (true)
		{
			System.out.printf("Karbonite: %d\n", gc.karbonite());
			try
			{
				// stuff to do at start of turn
				obtainUnitStats(true);

				// for debugging purposes, we shorten the game
				if (roundNum == scheduledDeath)
				{
					VecUnit myUnits = gc.myUnits();
					for (long i = 0;i < myUnits.size();i++)
					{
						gc.disintegrateUnit(myUnits.get(i).id());
					}
				}

				// call the appropriate controller to run the turn
				if (myPlanet == Planet.Earth)
				{
					EarthController.runTurn();
				} else if (myPlanet == Planet.Mars)
				{
					MarsController.runTurn();
				} else
				{
					throw new Exception("Are we on Mercury?");
				}
			} catch (Exception e)
			{
				// There used to be GameActionExceptions for whenever you tried to do something retarded
				// But that doesn't seem to exist this year
				System.out.println("========================================");
				System.out.println("An exception was thrown [Planet "+myPlanet.swigValue()+", round "+roundNum+"]");
				e.printStackTrace();
				System.out.println("========================================");
				System.out.println();
			} catch (UnknownError e)
			{
				// There used to be GameActionExceptions for whenever you tried to do something retarded
				// But that doesn't seem to exist this year
				System.out.println("========================================");
				System.out.println("An exception was thrown [Planet "+myPlanet.swigValue()+", round "+roundNum+"]");
				e.printStackTrace();
				System.out.println("========================================");
				System.out.println();
			} finally
			{
				// stuff to do at end of turn
				gc.nextTurn();
				roundNum += 1;
				System.out.flush();
			}
		}
	}

	private static void obtainUnitStats(boolean print) throws Exception
	{
		if (print)
		{
			System.out.print("Round "+roundNum+": karbonite = "+gc.karbonite()+" | units: ");
		}
		numWorkers = 0; numKnights = 0; numRangers = 0; numMages = 0; numHealers = 0; numFactories = 0; numRockets = 0;
		numFactoryBlueprints = 0; numRocketBlueprints = 0;
		VecUnit myUnits = gc.myUnits();
		for (long i = 0;i < myUnits.size();i++)
		{
			if (myUnits.get(i).unitType() == UnitType.Worker)
			{
				numWorkers++;
			} else if (myUnits.get(i).unitType() == UnitType.Knight)
			{
				numKnights++;
			} else if (myUnits.get(i).unitType() == UnitType.Ranger)
			{
				numRangers++;
			} else if (myUnits.get(i).unitType() == UnitType.Mage)
			{
				numMages++;
			} else if (myUnits.get(i).unitType() == UnitType.Healer)
			{
				numHealers++;
			} else if (myUnits.get(i).unitType() == UnitType.Factory)
			{
				if (myUnits.get(i).structureIsBuilt() == 0)
				{
					numFactories++;
				} else
				{
					numFactoryBlueprints++;
				}
			} else if (myUnits.get(i).unitType() == UnitType.Rocket)
			{
				if (myUnits.get(i).structureIsBuilt() == 0)
				{
					numRockets++;
				} else
				{
					numRocketBlueprints++;
				}
			} else
			{
				throw new Exception("What unit is this?");
			}
		}
		if (print)
		{
			System.out.print("Workers="+numWorkers+"; ");
			System.out.print("Knights="+numKnights+"; ");
			System.out.print("Rangers="+numRangers+"; ");
			System.out.print("Mages="+numMages+"; ");
			System.out.print("Healers="+numHealers+"; ");
			System.out.print("Factories="+numFactories+"built+"+numFactoryBlueprints+"unbuilt; ");
			System.out.print("Rockets="+numRockets+"built+"+numRocketBlueprints+"unbuilt; ");
			System.out.println();
		}
	}
}
