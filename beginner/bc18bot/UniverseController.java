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

	public static void playGame()
	{
		// initialise
		gc = new GameController();
		rand = new Random();
		Planet myPlanet = gc.planet();
		roundNum = 1;

		// run game
		while (true)
		{
			try
			{
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
			}

			// stuff to do at end of turn
			gc.nextTurn();
			roundNum += 1;
		}
	}
}
