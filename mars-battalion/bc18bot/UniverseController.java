package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

// use separate files for separate controllers to preserve code sanity
// only global functions are written here
import bc18bot.EarthController;
import bc18bot.MarsController;

public class UniverseController extends MovementModule {

	// the current round number
	static int roundNum;

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
	private static final int scheduledDeath = 2500;

	// last location where an enemy was seen
	static MapLocation lastSeenEnemy;

	// units that are currently in vision
	static VecUnit units;
	static VecUnit allUnits;
	static int numunits;
	static int numallunits;

	// try to move in a direction
	static ArrayList<Boolean> tryMovePackOfRobots(ArrayList<Integer> pack, Direction where)
	{
		int sizeOfPack = pack.size();
		ArrayList<Boolean> result = new ArrayList<Boolean>();
		for (int i = 0;i < sizeOfPack;i++) result.add(false);
		while (true)
		{
			boolean didSomething = false;
			for (int i = 0;i < sizeOfPack;i++)
			{
				int myId = pack.get(i);
				if (gc.isMoveReady(myId) && gc.canMove(myId, where))
				{
					doMoveRobot(gc.unit(myId), where);
					result.set(i, true);
					didSomething = true;
				}
			}
			if (!didSomething)
				break;
		}
		return result;
	}

	// try to move by bfs instruction
	static ArrayList<Boolean> tryMovePackOfRobots(ArrayList<Integer> pack)
	{
		int sizeOfPack = pack.size();
		ArrayList<Boolean> result = new ArrayList<Boolean>();
		for (int i = 0;i < sizeOfPack;i++) result.add(false);
		while (true)
		{
			boolean didSomething = false;
			for (int i = 0;i < sizeOfPack;i++)
			{
				int myId = pack.get(i);
				MapLocation where = gc.unit(pack.get(i)).location().mapLocation();
				if (gc.isMoveReady(myId))
				{
					shuffleDirOrder();
					for (int d = 0;d < 8;d++)
					{
						int ny = where.getY() + dy[randDirOrder[d]];
						int nx = where.getX() + dx[randDirOrder[d]];
						if (0 <= ny && ny < height && 0 <= nx && nx < width && bfsDist[ny][nx] < bfsDist[where.getY()][where.getX()] && gc.canMove(myId, directions[randDirOrder[d]]))
						{
							doMoveRobot(gc.unit(myId), directions[randDirOrder[d]]);
							result.set(i, true);
							didSomething = true;
							break;
						}
					}
				}
			}
			if (!didSomething)
				break;
		}
		return result;
	}

	public static void playGame()
	{
		// initialise
		gc = new GameController();
		rand = new Random();
		Planet myPlanet = gc.planet();
		roundNum = 1;
		EarthMap = gc.startingMap(Planet.Earth);
		MarsMap = gc.startingMap(Planet.Mars);
		myMap = gc.startingMap(gc.planet());
		myTeam = gc.team();
		enemyTeam = (myTeam == Team.Red) ? Team.Blue : Team.Red;

		initBfsFirstTurn();

		// run game
		while (true)
		{
			//System.out.printf("Karbonite [round %d]: %d\n", roundNum, gc.karbonite());
			try
			{
				// stuff to do at start of turn
				obtainUnitStats(false);

				// for debugging purposes, we shorten the game
				if (roundNum >= scheduledDeath)
				{
					VecUnit myUnits = gc.myUnits();
					for (long i = 0;i < myUnits.size();i++)
					{
						gc.disintegrateUnit(myUnits.get(i).id());
					}
				} else
				{
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
			} finally
			{
				// stuff to do at end of turn
				System.out.flush();
				gc.nextTurn();
				roundNum += 1;
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


		initBfsPerTurn(myUnits);

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

	static void getUnitSets() throws Exception
	{
		units = gc.myUnits();
		numunits = (int)units.size();
		allUnits = gc.units();
		numallunits = (int)allUnits.size();
	}

	static void checkForEnemyUnits() throws Exception
	{
		// remove things that are incorrect
		ArrayList<MapLocation> newAttackLocs = new ArrayList<MapLocation>();
		for (MapLocation i: attackLocs)
		{
			if (!gc.canSenseLocation(i))
				newAttackLocs.add(i);
			else if (gc.senseNearbyUnitsByTeam(i, 0, enemyTeam).size() > 0)
				newAttackLocs.add(i);
		}
		attackLocs.clear();
		attackLocs.addAll(newAttackLocs);
		boolean cleared = false;
		for (int i = 0;i < numallunits;i++) if (allUnits.get(i).team() == enemyTeam)
		{
			if (!allUnits.get(i).location().isInGarrison() && !allUnits.get(i).location().isInSpace())
			{
				//lastSeenEnemy = allUnits.get(i).location().mapLocation();
				if (!cleared)
				{
					attackLocs.clear();
					cleared = true;
				}
				attackLocs.add(allUnits.get(i).location().mapLocation());
				break;
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

	static boolean tryReplicate(int unitId) throws Exception
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
			return true;
		}
		return false;
	}

	static void tryToUnload (int unitId) throws Exception
	{
		//possibly slow performance
		Unit unit = gc.unit(unitId);
		MapLocation curLoc = unit.location().mapLocation();

		if (unit.unitType() != UnitType.Factory && unit.unitType() != UnitType.Rocket) {
			throw new Exception("Non-structure tryToUnload() call");
		}

		int numRemaining = (int)unit.structureGarrison().size();
		for (Direction d: directions)
		{
			if (numRemaining > 0 && gc.canUnload(unitId, d))
			{
				gc.unload(unitId, d);
				numRemaining--;
				MapLocation loc = unit.location().mapLocation().add(d);
				hasFriendlyUnit[loc.getY()][loc.getX()] = true;
			}
		}
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

		doMoveRobot(unit, bestMove);
		return true;
	}

	static void tryMoveAttacker (Unit unit) throws Exception
	{
		if (gc.senseNearbyUnitsByTeam(unit.location().mapLocation(), unit.attackRange(), enemyTeam).size() > 0) {
			return;
		}

		if (gc.isMoveReady(unit.id())) {
			Direction prefer = unit.location().mapLocation().directionTo(lastSeenEnemy);
			for (int i = 0;i < 4;i++)
			{
				Direction d = Direction.swigToEnum((prefer.swigValue()+i)%8);
				if (gc.canMove(unit.id(), d))
				{
					doMoveRobot(unit, d);
					break;
				}
				d = Direction.swigToEnum((prefer.swigValue()+8-i)%8);
				if (gc.canMove(unit.id(), d)) {
					doMoveRobot(unit, d);
					break;
				}
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

	public static void runRanger(Unit unit) {
        boolean doMove = true;

        if (unit.location().isOnMap()) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), unit.attackRange());
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.team() != unit.team() && gc.canAttack(unit.id(), other.id())) {
                    doMove = false;
                    if (gc.isAttackReady(unit.id())) {
                        gc.attack(unit.id(), other.id());
                    }
                }
            }
        }

        if (unit.location().isOnMap()) {
            for (int i = attackLocs.size()-1; i >= 0; i--) {
                if (attackLocs.get(i).equals(unit.location().mapLocation())) {
                    attackLocs.remove(i);
                }
            }
        }

        if (doMove) {
            if (unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
                boolean doneMove = false;
                Direction moveDir;
                if (!attackLocs.isEmpty()) {
                    // find closest out of the potential attackable locations
                    // TODO: change this to bfs distance so it's less horrible xd
                    // TODO: once changed to bfs, make sure you don't do too many bfs's and time out
                    // TODO: actually you can just do one bfs LuL
                    // TODO: make the bfs result function more smooth over time
                    MapLocation bestLoc = attackLocs.get(0);
                    for (int i = 1; i < attackLocs.size(); i++) {
                        if (moveDistance(unit.location().mapLocation(), attackLocs.get(i)) <
                                moveDistance(unit.location().mapLocation(), bestLoc)) {
                            bestLoc = attackLocs.get(i);
                        }
                    }
                    //System.out.println("doing bfs");
                    bfs(unit.location().mapLocation(), 0);
                    moveDir = directions[bfsDirectionIndexTo[bestLoc.getY()][bestLoc.getX()]];
                    //System.out.println("got, from dfs, direction " + moveDir.toString());
                    if (moveDir != Direction.Center && gc.canMove(unit.id(), moveDir)) {
                        doMoveRobot(unit, moveDir);
                        doneMove = true;
                    }
                }
                if (!doneMove) {
                    moveDir = directions[getTendency(unit)];
                    if (gc.canMove(unit.id(), moveDir)) {
                        doMoveRobot(unit, moveDir);
                        updateTendency(unit.id(), 6);
                    } else {
                        updateTendency(unit.id(), 100);
                    }
                }
            }
        }
    }
}
