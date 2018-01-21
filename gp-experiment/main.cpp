#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <assert.h>
#include <random>
#include <functional>

#include <utility>
#include <map>
#include <vector>
#include <queue>
#include <algorithm>

#include "bc.hpp"

#define fo(i,a,b) for (int i=(a);i<(b);i++)
#define SZ(a) ((int) a.size())

using namespace bc;
using namespace std;

static int roundNum;

static bool raceToMars = false;

static int maxConcurrentRocketsReady = 2;

static int dis[55][55][55][55];
static bool hasFriendlyWorker[55][55];

static int dy[] = {1,1,0,-1,-1,-1,0,1,0};
static int dx[] = {0,1,1,1,0,-1,-1,-1,0};

static int DirCenterIndex = 8; // index 8 corresponds to Center

static MapLocation nullMapLocation(Earth, -1, -1);

struct BfsState {
	int y, x, startingDir;

	BfsState(int yy, int xx, int sd) {
		y = yy;
		x = xx;
		startingDir = sd;
	}
};

struct SimpleState {
	int y, x;

	SimpleState (int yy, int xx) {
		y = yy;
		x = xx;
	}
};

// used to give priority to units that are closer to enemies
static int manhattanDistanceToNearestEnemy[55][55];
// Warning: constant used, would need to change this if the maps got bigger... but they probably won't get bigger so whatever.
static const int ManhattanDistanceNotSeen = 499;

GameController gc;

static vector<Direction> directions;
static map<int, int> tendency; // TODO : test unordered map
static vector<MapLocation> attackLocs;

static int width, height;
static bool isPassable[55][55];
static bool hasFriendlyUnit[55][55];
static bool hasFriendlyStructure[55][55];

// temporary seen array
static bool bfs_seen[55][55];

// which direction you should move to reach each square, according to the bfs
static int bfsDirectionIndexTo[55][55];
static MapLocation bfsClosestKarbonite;
static MapLocation bfsClosestEnemy;
static MapLocation bfsClosestFreeGoodPosition;

// to random shuffle the directions
static int randDirOrder[55];

// factories that are currently blueprints
static vector<Unit> factoriesBeingBuilt;
static vector<Unit> rocketsBeingBuilt;
static vector<Unit> rocketsReady;

// team defs
static Team myTeam;
static Team enemyTeam;

// Map type results
static const int CANT_REACH_ENEMY = 0;
static const int PARTIALLY_REACH_ENEMY = 1;
static const int FULLY_REACH_ENEMY = 2;
static int reach_enemy_result = -1;

// (Currently not used! Just ignore this xd)
// Map of how long ago an enemy ranger was seen at some square
// Stores -1 if a ranger was not seen at that square the last time you were able to sense it (or if you can currently
//   sense it and there's not a ranger there.
// Otherwise stores number of turns since last sighting, 0 if you can see one this turn.
// TODO: do we need this?
//static int enemyRangerSeenWhen[][];

// Map of euclidean distance of each square to closest enemy
// Only guaranteed to be calculated up to a maximum distance of 100, otherwise the distance could be correct, or
// could be set to some large value (e.g. 9999)
// TODO: Warning: Constants
// TODO: change this if constants change
static int attackDistanceToEnemy[55][55];
// good positions are squares which are some distance from the enemy.
// e.g. distance [51, 72] from the closest enemy (this might be changed later...)
// Warning: random constants being used. Need to be changed if constants change!
// if units take up "good positions" then in theory they should form a nice concave. "In theory" LUL
static bool is_good_ranger_position[55][55];
// whether a unit has already taken a potential good position
static bool is_good_ranger_position_taken[55][55];
static vector<pair<int, int>> good_ranger_positions;

static int lastKnownKarboniteAmount[55][55];
static int distToKarbonite[55][55];

static int time_since_damaged_unit[55][55];
static bool is_good_healer_position[55][55];
static bool is_good_healer_position_taken[55][55];
static vector<pair<int, int>> good_healer_positions;

static int distToDamagedFriendlyHealableUnit[55][55];

static int bfsTowardsBlueprintDist[55][55];

static int numEnemiesThatCanAttackSquare[55][55];

static int distToMyStartingLocs[55][55];
static int distToEnemyStartingLocs[55][55];

static int numIdleRangers;

// whether it's "very early game"
// during the very early game we want our replicating workers to explore as fast as possible
// currently defined as true until you see an enemy fighting unit, or you reach a square where
// distance to enemy starting locs <= distance to friendly starting locs
static bool is_very_early_game;

static const int MultisourceBfsUnreachableMax = 499;
static bool can_reach_from_spawn[55][55];

// whether the square is "near" enemy units
// currently defined as being within distance 72 of an enemy
static bool isSquareDangerous[55][55];
static int DangerDistanceThreshold = 72;

static const int RangerAttackRange = 50;
// distance you have to be to be one move away from ranger attack range
// unfortunately it's not actually the ranger's vision range which is 70, it's actually 72...
static const int OneMoveFromRangerAttackRange = 72;

// Distance around a worker to search for whether there is enough karbonite to be worth replicating
static int IsEnoughResourcesNearbySearchDist = 8;

static int getClosestFreeGoodPositionCandidates[50 * 50 + 5];

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

static Planet myPlanet;
static PlanetMap EarthMap;
static PlanetMap MarsMap;

static void init_global_variables ();
static void all_pairs_shortest_path();
int get_unit_order_priority (Unit& unit);
static void start_racing_to_mars ();
static void init_turn (vector<Unit>& myUnits);
static bool is_healable_unit_type(UnitType unitType);
static void remove_type_from_friendly_unit_count (UnitType unitType);
static void add_type_to_friendly_unit_count (UnitType unitType);
static bool is_fighter_unit_type (UnitType unitType);

static void runEarthWorker (Unit& unit);
static void runEarthRocket (Unit& unit);
static void runMarsWorker (Unit& unit);
static void runMarsRocket (Unit& unit);
static void runHealer (Unit& unit);
static void runRanger (Unit& unit);
static void runKnight(Unit& unit);
static void runFactory (Unit& unit);

static void calculateManhattanDistancesToClosestEnemies(vector<Unit>& allUnits);
static void doMoveRobot (Unit& unit, Direction dir);
static void shuffleDirOrder();
static bool tryToMoveToRocket (Unit& unit );
static int getTendency(Unit& unit);
static void updateTendency(int id, int changeChance);
static int moveDistance(MapLocation a, MapLocation b);
static int getSpaceAround(MapLocation loc);
static bool isFriendlyStructure(Unit unit);
static bool isNextToBuildingBlueprint(MapLocation loc);
static bool moveToAttackLocs (Unit& unit);

static void moveToTendency(Unit& unit);
static int getRangerAttackPriority(Unit& unit);
static bool rangerTryToAttack(Unit& unit);
static bool tryToHeal(Unit& unit);
static void multisourceBfsAvoidingUnitsAndDanger (vector<SimpleState>& startingLocs, int resultArr[55][55]);
static bool doReplicate(Unit& unit);
static void checkForEnemyUnits (vector<Unit>& allUnits);

static void do_flood_fill(vector<SimpleState>& startingLocs, int resultArr[55][55], bool passableArr[55][55], int label);
static int can_reach_enemy_on_earth();

static bool bfsTowardsBlueprint(Unit& unit);
static bool doBuild(Unit& unit);
static bool doBlueprint(Unit& unit, UnitType toBlueprint);
static bool doRepair(Unit& unit);
static bool isEnoughResourcesNearby(Unit& unit);
static bool tryMoveToLoc (Unit& unit, int distArray[55][55]);
static void tryToLoadRocket (Unit& unit);
static pair<int,int> get_closest_good_position (int y, int x, bool taken_array[55][55], vector<pair<int, int>>& good_list);
static int get_dir_index (Direction dir);
static bool will_blueprint_create_blockage(MapLocation loc);
static int shortRangeBfsToBestKarbonite(Unit &unit);

static void doBlueprintMovement(Unit &unit, bool &doneMovement);
static void doKarboniteMovement(Unit &unit, bool &doneMovement);

class compareUnits {
	public:
		bool operator() (Unit a, Unit b) {
			return get_unit_order_priority(a) > get_unit_order_priority(b);
		}
};

// Store this list of all our units ourselves, so that we can add to it when we create units and use those new units
// immediately.
static priority_queue<Unit, vector<Unit>, compareUnits> allMyUnits;

static int get_dir_index (Direction dir) {
	fo(i, 0, 8) if (directions[i] == dir) {
		return i;
	}
	printf("ERROR: Couldn't find dir index for %d\n", dir);
	return 0;
}

int main() {
	printf("Player C++ bot starting\n");
	printf("Connecting to manager...\n");

	std::default_random_engine generator;
	std::uniform_int_distribution<int> distribution (0,8);
	auto dice = std::bind ( distribution , generator );

	srand(420);

	if (bc_has_err()) {
		// If there was an error creating gc, just die.
		printf("Failed, dying.\n");
		exit(1);
	}
	printf("Connected!\n");
	fflush(stdout);

	init_global_variables();
	printf("Finished init_global_variables()\n");

	reach_enemy_result = can_reach_enemy_on_earth();
	printf("reach_enemy_result: %d\n",reach_enemy_result);

	if (myPlanet == Mars) {
		while (true) {
			// printf("Starting round %d\n", roundNum);

			vector<Unit> units = gc.get_my_units();
			init_turn(units);

			while (!allMyUnits.empty()) {
				Unit unit = allMyUnits.top();
				allMyUnits.pop();

				if (!gc.can_sense_unit(unit.get_id())) {
					continue;
				}

				if (!unit.is_on_map()) {
					continue;
				}

				switch (unit.get_unit_type()) {
					case Worker:
						runMarsWorker(unit);
						break;
					case Factory:
						break;
					case Ranger:
						runRanger(unit);
						break;
					case Knight:
						runKnight(unit);
						break;
					case Healer:
						runHealer(unit);
						break;
					case Rocket:
						runMarsRocket(unit);
						break;
					case Mage:
						break;
				}

			}

			fflush(stdout);
			gc.next_turn();
			roundNum++;
		}
	} else {
		gc.queue_research(Worker);
		gc.queue_research(Ranger);
		gc.queue_research(Healer);
		gc.queue_research(Ranger);
		gc.queue_research(Healer);
		gc.queue_research(Rocket);
		gc.queue_research(Rocket);
		gc.queue_research(Rocket);

		while (true) {
			printf("Starting round %d\n", roundNum);

			vector<Unit> units = gc.get_my_units();
			init_turn(units);

			if (roundNum >= 400) {
				start_racing_to_mars();
			}

			while (!allMyUnits.empty()) {
				Unit unit = allMyUnits.top();
				allMyUnits.pop();

				if (!gc.can_sense_unit(unit.get_id())) {
					continue;
				}

				if (!unit.is_on_map()) {
					continue;
				}

				switch (unit.get_unit_type()) {
					case Worker:
						runEarthWorker(unit);
						break;
					case Factory:
						runFactory(unit);
						break;
					case Ranger:
						runRanger(unit);
						break;
					case Knight:
						runKnight(unit);
						break;
					case Healer:
						runHealer(unit);
						break;
					case Rocket:
						runEarthRocket(unit);
						break;
					case Mage:
						break;
				}

			}

			//printf("Number of idle rangers is %3d / %3d\n", numIdleRangers, numRangers);

			fflush(stdout);
			gc.next_turn();
			roundNum++;
		}
	}
}

static void init_turn (vector<Unit>& myUnits) {

	for (int i = 0; i < height; i++) {
		for (int j = 0; j < width; j++) {
			hasFriendlyWorker[i][j] = false;
		}
	}

	factoriesBeingBuilt.clear();
	rocketsBeingBuilt.clear();

	for (int i = 0; i < myUnits.size(); i++) {
		Unit& unit = myUnits[i];
		if (unit.get_unit_type() == Factory) {
			if (unit.structure_is_built() == 0) {
				factoriesBeingBuilt.push_back(unit);
			}
		}
		if (unit.get_unit_type() == Rocket) {
			if (unit.structure_is_built() == 0) {
				rocketsBeingBuilt.push_back(unit);
			}
		}
	}

	for (int y = 0; y < height; y++) {
		for (int x = 0; x < width; x++) {
			hasFriendlyUnit[y][x] = false;
			hasFriendlyStructure[y][x] = false;
			attackDistanceToEnemy[y][x] = 9999;
			is_good_ranger_position[y][x] = false;
			is_good_ranger_position_taken[y][x] = false;
			isSquareDangerous[y][x] = false;
			numEnemiesThatCanAttackSquare[y][x] = 0;

			time_since_damaged_unit[y][x]++;
			is_good_healer_position[y][x] = false;
			is_good_healer_position_taken[y][x] = false;

			MapLocation loc(myPlanet, x, y);
			if (gc.can_sense_location(loc)) {
				lastKnownKarboniteAmount[y][x] = (int) gc.get_karbonite_at(loc);
			}
		}
	}

	//reset unit counts
	numWorkers = 0; numKnights = 0; numRangers = 0; numMages = 0; numHealers = 0; numFactories = 0; numRockets = 0;
	numFactoryBlueprints = 0; numRocketBlueprints = 0;

	numIdleRangers = 0;

	rocketsReady.clear();

	vector<SimpleState> damagedFriendlyHealableUnits;
	vector<Unit> units = gc.get_units();

	fo(i, 0, SZ(units)) {
		Unit& unit = units[i];

		// Note: This loops through friendly AND enemy units!
		if (unit.get_team() == myTeam) {
			// my team

			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();
				hasFriendlyUnit[loc.get_y()][loc.get_x()] = true;

				if (is_structure(unit.get_unit_type())) {
					hasFriendlyStructure[loc.get_y()][loc.get_x()] = true;
				}

				if (is_healable_unit_type(unit.get_unit_type()) && unit.get_health() < unit.get_max_health()) {
					//printf("damaged dude at %d %d\n", loc.get_y(), loc.get_x());

					int locY = loc.get_y(), locX = loc.get_x();

					time_since_damaged_unit[locY][locX] = 0;

					damagedFriendlyHealableUnits.push_back(SimpleState(locY, locX));
				}

				// worker cache position
				if (unit.get_unit_type() == Worker) {
					hasFriendlyWorker[loc.get_y()][loc.get_x()] = true;
				}
			}

			// friendly unit counts
			if (unit.get_unit_type() == Factory) {

				if (unit.structure_is_built() != 0) numFactories++;
				else numFactoryBlueprints++;

				if (unit.is_factory_producing() != 0) {
					add_type_to_friendly_unit_count(unit.get_factory_unit_type());
				}

			} else if (unit.get_unit_type() == Rocket) {

				if (unit.structure_is_built() != 0) {

					if (unit.get_structure_garrison().size() < unit.get_structure_max_capacity()) {
						rocketsReady.push_back(unit);
					}

					numRockets++;
				} else numRocketBlueprints++;

			} else {
				add_type_to_friendly_unit_count(unit.get_unit_type());
			}
		} else {
			// enemy team

			// calculate closest distances to enemy fighter units
			if (unit.get_location().is_on_map() && is_fighter_unit_type(unit.get_unit_type())) {
				// we've seen an enemy, mark as not the very early game anymore
				is_very_early_game = false;

				MapLocation loc = unit.get_map_location();
				int locY = loc.get_y(), locX = loc.get_x();
				for (int y = locY - 10; y <= locY + 10; y++) {
					if (y < 0 || height <= y) continue;
					for (int x = locX - 10; x <= locX + 10; x++) {
						if (x < 0 || width <= x) continue;
						int myDist = (y-locY) * (y-locY) + (x-locX) * (x-locX);
						attackDistanceToEnemy[y][x] = min(attackDistanceToEnemy[y][x], myDist);
						if (myDist <= unit.get_attack_range()) {
							numEnemiesThatCanAttackSquare[y][x]++;
						}
					}
				}
			}
		}
	}

	fo(ty, 0, height) fo(tx, 0, width) {
		if (time_since_damaged_unit[ty][tx] <= 5) {
			for (int y = ty - 6; y <= ty + 6; y++) {
				if (y < 0 || height <= y) continue;
				for (int x = tx - 6; x <= tx + 6; x++) {
					if (x < 0 || width <= x) continue;
					int myDist = (y-ty) * (y-ty) + (x-tx) * (x-tx);
					if (10 <= myDist && myDist <= 20) {
						is_good_healer_position[y][x] = true;
					}
				}
			}
		}
	}

	good_healer_positions.clear();
	fo(y, 0, height) fo(x, 0, width) {
		if (attackDistanceToEnemy[y][x] <= 75) {
			is_good_healer_position[y][x] = false;
		}
		if (is_good_healer_position[y][x]) {
			good_healer_positions.push_back(make_pair(y, x));
		}
	}
	printf("%d good healer positions\n", SZ(good_healer_positions));

	// calculate good positions
	good_ranger_positions.clear();
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		if (RangerAttackRange < attackDistanceToEnemy[y][x] && attackDistanceToEnemy[y][x] <= OneMoveFromRangerAttackRange) {
			is_good_ranger_position[y][x] = true;
			good_ranger_positions.push_back(make_pair(y, x));
			//System.out.println("good position at " + y + " " + x);
		}

		// also calculate dangerous squares
		if (attackDistanceToEnemy[y][x] <= DangerDistanceThreshold) {
			isSquareDangerous[y][x] = true;
		}
	}
	//printf("Number of good ranger positions: %d\n", int(good_ranger_positions.size()));

	while (!allMyUnits.empty()) {
		allMyUnits.pop();
	}
	for (int i = 0; i < myUnits.size(); i++) {
		allMyUnits.push(myUnits[i]);
	}

	calculateManhattanDistancesToClosestEnemies(units);

	// calculate distances from all workers to closest karbonite
	vector<SimpleState> karboniteLocs;
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		if (!isSquareDangerous[y][x] && lastKnownKarboniteAmount[y][x] > 0) {
			karboniteLocs.push_back(SimpleState(y, x));
		}
	}
	multisourceBfsAvoidingUnitsAndDanger(karboniteLocs, distToKarbonite);

	// calculate distances to damaged healable friendly units
	multisourceBfsAvoidingUnitsAndDanger(damagedFriendlyHealableUnits, distToDamagedFriendlyHealableUnit);

	// attackLoc update
	checkForEnemyUnits(units);

	/*

	// debug
	if (false) {
	System.out.print("Workers="+numWorkers+"; ");
	System.out.print("Knights="+numKnights+"; ");
	System.out.print("Rangers="+numRangers+"; ");
	System.out.print("Mages="+numMages+"; ");
	System.out.print("Healers="+numHealers+"; ");
	System.out.print("Factories="+numFactories+"built+"+numFactoryBlueprints+"unbuilt; ");
	System.out.print("Rockets="+numRockets+"built+"+numRocketBlueprints+"unbuilt; ");
	System.out.println();
	}

	 */
}

static void start_racing_to_mars () {
	if (raceToMars || myPlanet == Mars) {
		return;
	}

	raceToMars = true;
	maxConcurrentRocketsReady = 5;
}

static void init_global_variables () {

	fo(i, 0, 8) directions.push_back((Direction) i);

	roundNum = 1;
	EarthMap = gc.get_earth_map();
	MarsMap = gc.get_mars_map();
	myPlanet = gc.get_planet();

	myTeam = gc.get_team();
	enemyTeam = myTeam == Red ? Blue : Red;

	PlanetMap myMap = gc.get_starting_planet(myPlanet);
	width = (int) myMap.get_width();
	height = (int) myMap.get_height();

	fo(y, 0, height) fo(x, 0, width) {
		MapLocation loc(myPlanet, x, y);
		isPassable[y][x] = myMap.is_passable_terrain_at(loc);
		lastKnownKarboniteAmount[y][x] = (int) myMap.get_initial_karbonite_at(loc);
	}

	vector<Unit> units = myMap.get_initial_units();
	vector<SimpleState> myStartingUnits;
	vector<SimpleState> enemyStartingUnits;
	fo(i, 0, SZ(units)) {
		MapLocation loc = units[i].get_map_location();
		if (units[i].get_team() == myTeam) {
			myStartingUnits.push_back(SimpleState(loc.get_y(), loc.get_x()));
		} else {
			attackLocs.push_back(units[i].get_map_location());
			enemyStartingUnits.push_back(SimpleState(loc.get_y(), loc.get_x()));
		}
	}

	multisourceBfsAvoidingUnitsAndDanger(myStartingUnits, distToMyStartingLocs);
	multisourceBfsAvoidingUnitsAndDanger(enemyStartingUnits, distToEnemyStartingLocs);

	fo(i, 0, 8) randDirOrder[i] = i;

	all_pairs_shortest_path();

	is_very_early_game = true;
}

int get_unit_order_priority (Unit& unit) {
	switch(unit.get_unit_type()) {
		// Actually not sure whether fighting units or workers should go first... so just use the same priority...
		case Ranger:
		case Knight:
		case Mage:
		case Healer:
			// Worker before factory so that workers can finish a currently building factory before the factory runs
		case Worker:
			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();
				// give priority to units that are closer to enemies
				// NOTE: the default value for manhattanDistanceToNearestEnemy (ie the value when there are no nearby
				//   enemies) should be less than 998 so that priorities don't get mixed up!
				return 999 + manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()];
			} else {
				return 1998;
			}
			// Factory before rocket so that factory can make a unit, then unit can get in rocket before the rocket runs
			// Edit: not actually sure if you can actually do that lol... whatever.
		case Factory:
			return 1999;
		case Rocket:
			return 2999;
	}
	printf("ERROR: getUnitOrderPriority() does not recognise this unit type!");
	return 9998;
}

static void remove_type_from_friendly_unit_count (UnitType unitType) {
	if (unitType == Worker) {
		numWorkers--;
	} else if (unitType == Knight) {
		numKnights--;
	} else if (unitType == Ranger) {
		numRangers--;
	} else if (unitType == Mage) {
		numMages--;
	} else if (unitType == Healer) {
		numHealers--;
	}
}

static void add_type_to_friendly_unit_count (UnitType unitType) {
	if (unitType == Worker) {
		numWorkers++;
	} else if (unitType == Knight) {
		numKnights++;
	} else if (unitType == Ranger) {
		numRangers++;
	} else if (unitType == Mage) {
		numMages++;
	} else if (unitType == Healer) {
		numHealers++;
	}
}

static bool is_fighter_unit_type (UnitType unitType) {
	return unitType == Ranger ||
		unitType == Knight ||
		unitType == Mage ||
		unitType == Healer;
}

static bool is_healable_unit_type (UnitType unitType) {
	return unitType == Ranger ||
		unitType == Knight ||
		unitType == Mage ||
		unitType == Healer ||
		unitType == Worker;
}

static void all_pairs_shortest_path () {
	for (int sy = 0; sy < height; sy++) {
		for (int sx = 0; sx < width; sx++) {

			for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
				// large number so that we don't accidentally think that unreachable squares are the closest squares
				dis[sy][sx][y][x] = 999;
			}

			queue<SimpleState> q;
			q.push(SimpleState(sy, sx));
			dis[sy][sx][sy][sx] = 0;

			while (!q.empty()) {
				SimpleState cur = q.front();
				q.pop();

				for (int d = 0; d < 8; d++) {
					int ny = cur.y + dy[d];
					int nx = cur.x + dx[d];
					if (0 <= ny && ny < height && 0 <= nx && nx < width && isPassable[ny][nx] && dis[sy][sx][ny][nx] == 999) {
						dis[sy][sx][ny][nx] = dis[sy][sx][cur.y][cur.x] + 1;
						q.push(SimpleState(ny, nx));
					}
				}
			}

		}
	}
}

static void doMoveRobot (Unit& unit, Direction dir) {
	if (gc.can_move(unit.get_id(), dir)) {
		MapLocation loc = unit.get_map_location();
		if (!hasFriendlyUnit[loc.get_y()][loc.get_x()]) {
			printf("Error: hasFriendlyUnit[][] is incorrect!");
			return;
		}
		hasFriendlyUnit[loc.get_y()][loc.get_x()] = false;
		loc.add(dir);
		hasFriendlyUnit[loc.get_y()][loc.get_x()] = true;
		gc.move_robot(unit.get_id(), dir);
	}
}

static void shuffleDirOrder() {
	for (int i = 7; i >= 0; i--) {
		int j = rand() % (i+1);
		int tmp = randDirOrder[j];
		randDirOrder[j] = randDirOrder[i];
		randDirOrder[i] = tmp;
	}
}

static void runEarthWorker (Unit& unit) {
	if (!unit.is_on_map()) {
		return;
	}

	{
		MapLocation cur_loc = unit.get_map_location();
		if (distToEnemyStartingLocs[cur_loc.get_y()][cur_loc.get_x()] <=
				distToMyStartingLocs[cur_loc.get_y()][cur_loc.get_x()]) {
			// we've reached a square that's closer to the enemy starting locs than our own
			// mark as not the very early game anymore
			is_very_early_game = false;
		}
	}

	bool doneMovement = !gc.is_move_ready(unit.get_id());

	// movement
	if (!doneMovement && false /* need to place factory and no adjacent good places and nearby good place */) {
		// move towards good place
	}
	// If it's early game and you're a currently replicating worker, prioritize karbonite over blueprints
	// Otherwise prioritise blueprints over karbonite
	// See declaration of is_very_early_game for how we define early game
	if (is_very_early_game && unit.get_ability_heat() < 20) {
		doKarboniteMovement(unit, doneMovement);
		doBlueprintMovement(unit, doneMovement);
	} else {
		doBlueprintMovement(unit, doneMovement);
		doKarboniteMovement(unit, doneMovement);
	}
	if (!doneMovement /* next to damaged structure */) {
		// done, stay next to damaged structure

		// TODO: implement this
	}
	if (!doneMovement /* damaged structure */) {
		// move towards damaged structure

		// TODO: implement this
	}

	if (!doneMovement) {
		// otherwise move randomly to a not-dangerous square
		shuffleDirOrder();
		for (int i = 0; i < 8; i++) {
			Direction dir = directions[randDirOrder[i]];
			MapLocation loc = unit.get_map_location().add(dir);
			if (0 <= loc.get_y() && loc.get_y() < height &&
					0 <= loc.get_x() && loc.get_x() < width &&
					!isSquareDangerous[loc.get_y()][loc.get_x()] &&
					gc.can_move(unit.get_id(), dir)) {
				doMoveRobot(unit, dir);
				break;
			}
		}
	}

	// update worker location after moving
	unit = gc.get_unit(unit.get_id());
	MapLocation loc = unit.get_map_location();

	// actions
	bool doneAction = false;

	bool replicateForBuilding = (numFactories + numFactoryBlueprints > 0 && numWorkers < 6);
	bool replicateForHarvesting = distToKarbonite[loc.get_y()][loc.get_x()] < IsEnoughResourcesNearbySearchDist && isEnoughResourcesNearby(unit);

	bool canRocket = (gc.get_research_info().get_level(Rocket) >= 1);
	bool lowRockets = ((int) rocketsReady.size() + numRocketBlueprints < maxConcurrentRocketsReady);
	bool needToSave = (gc.get_karbonite() < 75 + 15);

	bool shouldReplicate = (replicateForBuilding || replicateForHarvesting);

	if (canRocket && lowRockets && needToSave) {
		shouldReplicate = false;
	}

	if (!doneAction && unit.get_ability_heat() < 10 && shouldReplicate) {
		// try to replicate
		if (doReplicate(unit)) {
			doneAction = true;
		}
	}

	// hard code not building factory on round 1 so that we can replicate
	if (roundNum > 1 && !doneAction) {
		// if you can blueprint factory/rocket and want to blueprint factory/rocket...
		if (gc.get_karbonite() >= 100 &&
				(numFactories + numFactoryBlueprints < 3 ||
				 gc.get_karbonite() >= 130 + (numFactories + numFactoryBlueprints - 3) * 15)) {

			if (doBlueprint(unit, Factory)) {
				doneAction = true;
			}
		} else if (canRocket && lowRockets) {

			if (doBlueprint(unit, Rocket)) {
				doneAction = true;
			}

		}

		// TODO: try to move to a location with space if the adjacent spaces are too cramped to build a blueprint

	}

	if (doBuild(unit)) {
		// try to build adjacent structures
		doneAction = true;
	}
	if (!doneAction) {
		// if next to karbonite, mine
		for (int i = 0; i < 9; i++) {
			if (gc.can_harvest(unit.get_id(), directions[i])) {
				//System.out.println("harvesting!");
				gc.harvest(unit.get_id(), directions[i]);
				doneAction = true;
				break;
			}
		}
	}
	if (!doneAction) {
		if (doRepair(unit)){
			doneAction = true;
		}
	}

	return;
}

static void runMarsWorker (Unit& unit) {

	bool doneMovement = !gc.is_move_ready(unit.get_id());

	if (!doneMovement) {
		// move towards karbonite
		MapLocation loc = unit.get_map_location();
		if (distToKarbonite[loc.get_y()][loc.get_x()] != MultisourceBfsUnreachableMax) {
			tryMoveToLoc(unit, distToKarbonite);
			doneMovement = true;
		}
	}

	if (!doneMovement) {
		// otherwise move randomly to a not-dangerous square
		shuffleDirOrder();
		for (int i = 0; i < 8; i++) {
			Direction dir = directions[randDirOrder[i]];
			MapLocation loc = unit.get_map_location().add(dir);
			if (0 <= loc.get_y() && loc.get_y() < height &&
					0 <= loc.get_x() && loc.get_x() < width &&
					!isSquareDangerous[loc.get_y()][loc.get_x()] &&
					gc.can_move(unit.get_id(), dir)) {
				doMoveRobot(unit, dir);
				break;
			}
		}
	}

	// update worker location after moving
	unit = gc.get_unit(unit.get_id());
	MapLocation loc = unit.get_map_location();

	bool doneAction = false;

	// late game or can afford rocket + units + replicate
	bool shouldReplicate = (roundNum >= 751 || gc.get_karbonite() > 75 + 20*2 + 30);
	shouldReplicate |= isEnoughResourcesNearby(unit);

	if (!doneAction && unit.get_ability_heat() < 10 && shouldReplicate) {
		if (doReplicate(unit)) {
			doneAction = true;
		}
	}

	if (!doneAction) {
		// try to harvest
		for (int i = 0; i < 9; i++) {
			if (gc.can_harvest(unit.get_id(), directions[i])) {
				//System.out.println("harvesting!");
				gc.harvest(unit.get_id(), directions[i]);
			}
		}
	}
}

static void tryToUnload (Unit& unit) {
	for (int j = 0; j < 8; j++) {
		Direction unloadDir = directions[j];
		if (gc.can_unload(unit.get_id(), unloadDir)) {
			gc.unload(unit.get_id(), unloadDir);
			MapLocation loc = unit.get_map_location().add(unloadDir);
			hasFriendlyUnit[loc.get_y()][loc.get_x()] = true;
			allMyUnits.push(gc.sense_unit_at_location(loc));
			// TODO: check everywhere else to make sure hasFriendlyUnits[][] is being correctly maintained.
		}
	}
}

static void runEarthRocket (Unit& unit) {

	if (!unit.structure_is_built()) {
		return;
	}

	tryToLoadRocket(unit);

	bool fullRocket = (unit.get_structure_garrison().size() == unit.get_structure_max_capacity());
	bool aboutToFlood = (roundNum >= 749);
	bool notWorthWaiting = (roundNum >= 649 || gc.get_orbit_pattern().duration(roundNum)+roundNum <= gc.get_orbit_pattern().duration(roundNum+1)+roundNum+1);
	bool dangerOfDestruction = (unit.get_health() <= 70);

	// System.out.printf("I am a rocket and I think it isn't worth waiting: %d, full? %d\n", notWorthWaiting ? 1 : 0, fullRocket ? 1 : 0);

	if (dangerOfDestruction || aboutToFlood || (fullRocket && notWorthWaiting)) {
		MapLocation where;
		do {
			int landX = (int)(rand() % MarsMap.get_width());
			int landY = (int)(rand() % MarsMap.get_height());
			where = MapLocation(Mars, landX, landY);
		} while (MarsMap.is_passable_terrain_at(where) == 0);

		gc.launch_rocket(unit.get_id(), where);
	}
}


static void runMarsRocket (Unit& unit) {
	tryToUnload(unit);
}

static void runFactory (Unit& unit) {
	UnitType unitTypeToBuild = Ranger;

	MapLocation loc = unit.get_map_location();

	// TODO: change proportion based on current research levels
	if (isSquareDangerous[loc.get_y()][loc.get_x()]) {
		// dangerous square. Just build rangers, healers will just get instantly killed.
		unitTypeToBuild = Ranger;
	} else if (numWorkers == 0) {
		unitTypeToBuild = Worker;
	} else if (numRangers >= 2 * numHealers + 4) {
		unitTypeToBuild = Healer;
	}

	bool canRocket = (gc.get_research_info().get_level(Rocket) >= 1);
	bool lowRockets = ((int) rocketsReady.size() + numRocketBlueprints < maxConcurrentRocketsReady);
	int unitCost = (unitTypeToBuild == Worker ? 25 : 20);
	bool needToSave = (gc.get_karbonite() < 75 + unitCost);

	if (canRocket && lowRockets && needToSave) {
		return;
	}

	if (gc.can_produce_robot(unit.get_id(), unitTypeToBuild)) {
		//System.out.println("PRODUCING ROBOT!!!");
		gc.produce_robot(unit.get_id(), unitTypeToBuild);
		// make sure to immediately update unit counts
		add_type_to_friendly_unit_count(unitTypeToBuild);
	}

	tryToUnload(unit);
}

static void runRanger (Unit& unit) {
	// try to attack before and after moving
	bool doneAttack = rangerTryToAttack(unit);

	// decide movement
	if (unit.is_on_map() && gc.is_move_ready(unit.get_id())) {
		bool doneMove = false;
		int myY = unit.get_map_location().get_y();
		int myX = unit.get_map_location().get_x();

		// Warning: Constant being used. Change this constant
		if (!doneMove && attackDistanceToEnemy[myY][myX] <= OneMoveFromRangerAttackRange) {
			// if near enemies, do fighting movement

			// somehow decide whether to move forward or backwards
			if (attackDistanceToEnemy[myY][myX] <= RangerAttackRange) {
				// currently in range of enemy

				// find which square would be best to kite to (even if your movement is currently on cd)
				int best = -1, bestNumEnemies = 999, bestAttackDist = -1;
				for (int i = 0; i < 8; i++) {
					Direction dir = directions[i];
					MapLocation loc = unit.get_map_location().add(dir);
					if (0 <= loc.get_y() && loc.get_y() < height &&
							0 <= loc.get_x() && loc.get_x() < width) {
						int numEnemies = numEnemiesThatCanAttackSquare[loc.get_y()][loc.get_x()];
						int attackDist = attackDistanceToEnemy[loc.get_y()][loc.get_x()];
						if (numEnemies < bestNumEnemies ||
								(numEnemies == bestNumEnemies && attackDist > bestAttackDist)) {
							best = i;
							bestNumEnemies = numEnemies;
							bestAttackDist = attackDist;
						}
					}
				}

				if (doneAttack) {
					// just completed an attack, move backwards now to kite if you can

					if (best != -1 && bestNumEnemies < numEnemiesThatCanAttackSquare[myY][myX]) {
						// System.out.println("kiting backwards!");
						doMoveRobot(unit, directions[best]);

						// if current square is a good position, mark it
						MapLocation after_loc = unit.get_map_location().add(directions[best]);
						if (is_good_ranger_position[after_loc.get_y()][after_loc.get_x()])
						{
							is_good_ranger_position_taken[after_loc.get_y()][after_loc.get_x()] = true;
						}
					}

					// if you didn't find a good square, don't waste your move cd, just wait for next turn
					doneMove = true;

				} else {
					// wait for your next attack before kiting back, so don't move yet
					doneMove = true;

					// if the square you want to kite to is a good position, mark it as taken
					// (same code as the if() clause, but just don't move)
					if (best != -1 && bestNumEnemies < numEnemiesThatCanAttackSquare[myY][myX]) {
						MapLocation after_loc = unit.get_map_location().add(directions[best]);
						if (is_good_ranger_position[after_loc.get_y()][after_loc.get_x()])
						{
							is_good_ranger_position_taken[after_loc.get_y()][after_loc.get_x()] = true;
						}
					}	
				}
			} else {
				// currently 1 move from being in range of enemy
				if (!doneAttack && gc.is_attack_ready(unit.get_id()) && roundNum % 5 == 0) {
					// move into a position where you can attack
					int best = -1, bestNumEnemies = 999;
					shuffleDirOrder();
					for (int i = 0; i < 8; i++) {
						Direction dir = directions[randDirOrder[i]];
						MapLocation loc = unit.get_map_location().add(dir);
						if (0 <= loc.get_y() && loc.get_y() < height &&
								0 <= loc.get_x() && loc.get_x() < width &&
								attackDistanceToEnemy[loc.get_y()][loc.get_x()] <= RangerAttackRange &&
								gc.can_move(unit.get_id(), dir)) {
							int numEnemies = numEnemiesThatCanAttackSquare[loc.get_y()][loc.get_x()];
							if (numEnemies < bestNumEnemies) {
								best = i;
								bestNumEnemies = numEnemies;
							}
						}
					}

					if (best != -1) {
						Direction dir = directions[randDirOrder[best]];

						// mark your current position as yours so other rangers don't try to move there while you're gone
						if (is_good_ranger_position[myY][myX]) {
							is_good_ranger_position_taken[myY][myX] = true;
						} else {
							printf("ERROR: expected current position to be a good position but it wasn't...");
						}
						doMoveRobot(unit, dir);
						doneMove = true;
					}
				}
			}

			if (doneMove) {
				// if you ended up on a good position, claim it so no-one else tries to move to it
				// TODO: add this
				// TODO: THIS IS A REALLY SIMPLE CHANGE
				// TODO: SERIOUSLY JUST ADD THIS
			}
		}

		if (!doneMove) {
			// otherwise, search for a good destination to move towards

			if (raceToMars && !rocketsReady.empty()) {
				doneMove = true;

				tryToMoveToRocket(unit);
			}

			// check if current location is good position
			if (!doneMove && is_good_ranger_position[myY][myX]) {
				doneMove = true;

				// if there's an closer (or equally close) adjacent good position
				// and the next attack wave isn't too soon, then move to it
				bool foundMove = false;
				if (roundNum % 5 < 3) {
					shuffleDirOrder();
					for (int i = 0; i < 8; i++) {
						Direction dir = directions[randDirOrder[i]];
						MapLocation loc = unit.get_map_location().add(dir);
						if (0 <= loc.get_y() && loc.get_y() < height &&
								0 <= loc.get_x() && loc.get_x() < width &&
								is_good_ranger_position[loc.get_y()][loc.get_x()] &&
								manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()] <= manhattanDistanceToNearestEnemy[myY][myX] &&
								gc.can_move(unit.get_id(), dir)) {
							//System.out.println("moving to other good location!!");
							foundMove = true;
							doMoveRobot(unit, dir);
							is_good_ranger_position_taken[loc.get_y()][loc.get_x()] = true;
							break;
						}
					}
				}

				if (!foundMove) {
					// otherwise, stay on your current position
					if (!is_good_ranger_position_taken[myY][myX]) {
						is_good_ranger_position_taken[myY][myX] = true;
					}
				}
			}

			// otherwise, try to move to good position
			pair<int,int> best_good = get_closest_good_position(myY, myX, is_good_ranger_position_taken, good_ranger_positions);
			//printf("I'm at %d %d and I'm moving to good location %d %d\n", unit.get_map_location().get_x(),
			//	unit.get_map_location().get_y(), best_good.second, best_good.first);
			if (!doneMove && best_good.first != -1) {
				int y = best_good.first;
				int x = best_good.second;
				is_good_ranger_position_taken[y][x] = true;
				tryMoveToLoc(unit, dis[y][x]);
				doneMove = true;
			}

			// otherwise, if the ranger can't find a free good position, mark it as idle
			if (!doneMove) {
				numIdleRangers++;
			}

			// otherwise move to attack loc
			if (!doneMove && moveToAttackLocs(unit)) {
				doneMove = true;
			}
			if (!doneMove) {
				moveToTendency(unit);
				doneMove = true;
			}
		}
	}

	// try to attack before and after moving
	if (!doneAttack) {
		rangerTryToAttack(unit);
		doneAttack = true;
	}
}

static void runKnight(Unit& unit) {
	bool doneMove = false;

	if (unit.is_on_map()) {
		vector<Unit> units = gc.sense_nearby_units(unit.get_map_location(), unit.get_attack_range());
		for (int i = 0; i < units.size(); i++) {
			Unit other = units[i];
			if (other.get_team() != unit.get_team() && gc.can_attack(unit.get_id(), other.get_id())) {
				doneMove = true;
				if (gc.is_attack_ready(unit.get_id())) {
					gc.attack(unit.get_id(), other.get_id());
				}
			}
		}
	}

	if (!doneMove && unit.is_on_map() && gc.is_move_ready(unit.get_id())) {
		// bfs(unit.get_map_location(), 1);

		if (!doneMove) {
			// try to move towards closest enemy unit
			// If the closest enemy is within some arbitrary distance, attack them
			// Currently set to ranger attack range
			// Warning: Constant used that might change!
			/*
			   FOR PERFORMANCE REASONS BFS CLOSEST ENEMY DOES NOT WORK
			   if (bfsClosestEnemy != NULL && unit.get_map_location().distanceSquaredTo(bfsClosestEnemy) <= RangerAttackRange) {
			//System.out.println("My location = " + unit.get_map_location().toString() + ", closest enemy loc = " + bfsClosestEnemy.toString());
			doneMove = true;
			Direction dir = directions[bfsDirectionIndexTo[bfsClosestEnemy.get_y()][bfsClosestEnemy.get_x()]];
			if (gc.can_move(unit.get_id(), dir)) {
			doMoveRobot(unit, dir);
			}
			}
			 */
		}

		if (!doneMove) {
			// try to move to attack location
			if (moveToAttackLocs(unit)) {
				doneMove = true;
			}
		}

		if (!doneMove) {
			// move to tendency
			moveToTendency(unit);
		}
	}
}

static void runHealer (Unit& unit) {
	bool doneHeal = tryToHeal(unit);
	bool doneMove = false;

	if (raceToMars && !doneMove) {
		doneMove = tryToMoveToRocket(unit);
	}

	MapLocation loc = unit.get_map_location();
	int myY = loc.get_y(), myX = loc.get_x();

	if (!doneMove && is_good_healer_position[myY][myX]) {
		doneMove = true;
		is_good_healer_position_taken[myY][myX] = true;
	}

	if (!doneMove) {
		pair<int,int> best_good = get_closest_good_position(myY, myX, is_good_healer_position_taken, good_healer_positions);
		if (best_good.first != -1) {
			int y = best_good.first;
			int x = best_good.second;
			is_good_healer_position_taken[y][x] = true;
			//System.out.println("I'm at " + unit.get_map_location().toString() + " and I'm moving to good location " + closestFreeGoodPosition.toString());
			tryMoveToLoc(unit, dis[y][x]);
			doneMove = true;
		}
	}

	if (!doneMove) {
		if (tryMoveToLoc(unit, distToDamagedFriendlyHealableUnit)) {
			doneMove = true;
		}
	}

	if (!doneHeal) {
		tryToHeal(unit);
	}
}

static bool tryToMoveToRocket (Unit& unit ) {
	// try to get to a fucking rocket as soon as possible

	if (rocketsReady.empty()) {
		return false;
	}

	int fromY = unit.get_map_location().get_y();
	int fromX = unit.get_map_location().get_x();

	int minDist = -1;
	int bestToY = -1;
	int bestToX = -1;

	for (Unit u : rocketsReady) {
		int toY = u.get_map_location().get_y();
		int toX = u.get_map_location().get_x();

		if (minDist == -1 || dis[toY][toX][fromY][fromX] < minDist) {
			minDist = dis[toY][toX][fromY][fromX];
			bestToY = toY;
			bestToX = toX;
		}
	}

	return tryMoveToLoc(unit, dis[bestToY][bestToX]);
}

static int getTendency(Unit& unit) {
	if (!tendency.count(unit.get_id())) {
		if (attackLocs.empty()) {
			tendency[unit.get_id()] = rand() % 8;
		} else if (unit.is_on_map()) {
			tendency[unit.get_id()] = get_dir_index(unit.get_map_location().direction_to(attackLocs.back()));
			//System.out.println("Trying to attack enemy starting location!");
		} else {
			// in garrison or space or something
			// don't set tendency yet and just return something random
			return rand() % 8;
		}
	}
	return tendency[unit.get_id()];
}

static void updateTendency(int id, int changeChance) {
	if (!tendency.count(id)) {
		return;
	}
	// assert tendency has id as key
	int k = tendency[id];
	int change = rand() % 100;
	if (change < changeChance / 2) {
		k = (k + 1) % 8;
	} else if (change < changeChance) {
		k = (k + 7) % 8;
	}
	tendency[id] = k;
}

static int moveDistance(MapLocation a, MapLocation b) {
	return dis[a.get_y()][a.get_x()][b.get_y()][b.get_x()];
}

static bool tryMoveToLoc (Unit& unit, int distArray[55][55]) {

	if (!unit.is_on_map() || !gc.is_move_ready(unit.get_id())) {
		return false;
	}

	int myY = unit.get_map_location().get_y();
	int myX = unit.get_map_location().get_x();
	int bestDist = -1;
	int bestDir = -1;

	shuffleDirOrder();
	for (int i = 0; i < 8; i++) {
		if (gc.can_move(unit.get_id(), directions[randDirOrder[i]])) {
			int tmpDist = distArray[myY + dy[randDirOrder[i]]][myX + dx[randDirOrder[i]]];
			if (bestDist == -1 || tmpDist < bestDist) {
				bestDist = tmpDist;
				bestDir = randDirOrder[i];
			}
		}
	}

	if (bestDir == -1) {
		return false;
	}

	int myDis = distArray[myY][myX];
	if (bestDist < myDis) {
		doMoveRobot(unit, directions[bestDir]);
	} else if (bestDist == myDis) {
		if (rand() % 100 < 50) {
			doMoveRobot(unit, directions[bestDir]);
		}
	} else {
		if (rand() % 100 < 20) {
			doMoveRobot(unit, directions[bestDir]);
		}
	}
	return true;
}

// Required: you must be able to sense every square around loc
static int getSpaceAround(MapLocation loc) {
	int space = 0;
	for (int i = 0; i < 8; i++) {
		MapLocation other = loc.add(directions[i]);
		// TODO: change has_unit_at_location && unit.team == my team to be less dangerous
		// TODO: because hasFriendlyUnit[][] might be incorrect T_T
		if (0 <= other.get_y() && other.get_y() < height && 0 <= other.get_x() && other.get_x() < width &&
				isPassable[other.get_y()][other.get_x()] &&
				!(gc.has_unit_at_location(other) && isFriendlyStructure(gc.sense_unit_at_location(other)))) {
			space++;
		}
	}
	return space;
}

static bool isFriendlyStructure(Unit unit) {
	return unit.get_team() == gc.get_team() && (unit.get_unit_type() == Factory || unit.get_unit_type() == Rocket);
}

// Required: loc must be a valid location (ie not out of bounds)
static bool isNextToBuildingBlueprint(MapLocation loc) {
	for (int i = 0; i < 8; i++) {
		MapLocation other = loc.add(directions[i]);
		if (gc.has_unit_at_location(other)) {
			Unit unit = gc.sense_unit_at_location(other);
			if (unit.get_team() == gc.get_team() &&
					(unit.get_unit_type() == Factory || unit.get_unit_type() == Rocket)
					&& unit.structure_is_built() == 0) {
				return true;
			}
		}
	}
	return false;
}

static bool moveToAttackLocs (Unit& unit) {
	if (attackLocs.empty()) {
		return false;
	}
	// find closest out of the potential attackable locations
	// TODO: change this to bfs distance so it's less horrible xd
	// TODO: once changed to bfs, make sure you don't do too many bfs's and time out
	// TODO: actually you can just do one bfs LuL
	// TODO: make the bfs result function more smooth over time
	MapLocation bestLoc = attackLocs[0];
	for (int i = 1; i < attackLocs.size(); i++) {
		if (moveDistance(unit.get_map_location(), attackLocs[i]) <
				moveDistance(unit.get_map_location(), bestLoc)) {
			bestLoc = attackLocs[i];
		}
	}
	return tryMoveToLoc(unit, dis[bestLoc.get_y()][bestLoc.get_x()]);
}

static void moveToTendency(Unit& unit) {
	Direction moveDir = directions[getTendency(unit)];
	if (gc.is_move_ready(unit.get_id()) && gc.can_move(unit.get_id(), moveDir)) {
		doMoveRobot(unit, moveDir);
		updateTendency(unit.get_id(), 6);
	} else {
		updateTendency(unit.get_id(), 100);
	}
}

// returns the priority of the unit for a range to attack
static int getRangerAttackPriority(Unit& unit){
	if (unit.get_unit_type()==Rocket || unit.get_unit_type()==Factory){
		return 0;
	}
	if (unit.get_unit_type()==Worker){
		return 1;
	}
	return 2;
}

// returns whether the unit attacked
static bool rangerTryToAttack(Unit& unit) {
	if (unit.is_on_map() && gc.is_attack_ready(unit.get_id())) {
		// this radius needs to be at least 1 square larger than the ranger's attack range
		// because the ranger might move, but the ranger's unit.get_map_location() won't update unless we
		// call again. So just query a larger area around the ranger's starting location.
		vector<Unit> units = gc.sense_nearby_units(unit.get_map_location(), 99);
		int whichToAttack = -1, whichToAttackHealth = 999, whichToAttackPriority = -1;
		for (int i = 0; i < units.size(); i++) {
			Unit other = units[i];
			if (other.get_team() != unit.get_team() && gc.can_attack(unit.get_id(), other.get_id())) {
				int health = (int)other.get_health();
				int attackPriority = (int) getRangerAttackPriority(other);

				if (whichToAttack == -1 || (attackPriority>whichToAttackPriority) || (attackPriority==whichToAttackPriority && health < whichToAttackHealth)) {
					whichToAttack = i;
					whichToAttackPriority = attackPriority;
					whichToAttackHealth = health;
				}
			}
		}
		if (whichToAttack != -1) {
			gc.attack(unit.get_id(), units[whichToAttack].get_id());
			return true;
		}
	}
	return false;
}

// returns whether the healer successfully healed
static bool tryToHeal(Unit& unit) {
	if (unit.is_on_map() && gc.is_heal_ready(unit.get_id())) {
		// this radius needs to be larger than the healer's heal range in case the healer moves
		// (because the Unit object is cached and the location() won't update)
		vector<Unit> units = gc.sense_nearby_units_by_team(unit.get_map_location(), 80, gc.get_team());
		int whichToHeal = -1, whichToHealHealthMissing = -1;
		for (int i = 0; i < units.size(); i++) {
			Unit other = units[i];
			if (gc.can_heal(unit.get_id(), other.get_id())) {
				int healthMissing = (int)(other.get_max_health() - other.get_health());
				if (whichToHeal == -1 || healthMissing > whichToHealHealthMissing) {
					whichToHeal = i;
					whichToHealHealthMissing = healthMissing;
				}
			}
		}
		if (whichToHeal != -1 && whichToHealHealthMissing > 0) {
			//System.out.println("Healer says: 'I'm being useful!'");
			gc.heal(unit.get_id(), units[whichToHeal].get_id());
			return true;
		}
	}
	return false;
}

static void checkForEnemyUnits (vector<Unit>& allUnits) {
	// remove things that are incorrect
	vector<MapLocation> newAttackLocs;
	for (MapLocation i: attackLocs) {
		if (!gc.can_sense_location(i))
			newAttackLocs.push_back(i);
		else if (gc.sense_nearby_units_by_team(i, 0, enemyTeam).size() > 0)
			newAttackLocs.push_back(i);
	}
	attackLocs.clear();

	for (MapLocation m : newAttackLocs) {
		attackLocs.push_back(m);
	}

	bool cleared = false;
	for (int i = 0;i < (int) allUnits.size();i++) if (allUnits[i].get_team() == enemyTeam)
	{
		if (!allUnits[i].get_location().is_in_garrison() && !allUnits[i].get_location().is_in_space()) {
			//lastSeenEnemy = allUnits[i].get_map_location();
			if (!cleared)
			{
				attackLocs.clear();
				cleared = true;
			}
			attackLocs.push_back(allUnits[i].get_map_location());
			break;
		}
	}
}

static void calculateManhattanDistancesToClosestEnemies(vector<Unit>& allUnits) {
	// multi-source bfs from all enemies

	queue<SimpleState> q;

	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		// see note about what this default value should be in getUnitOrderPriority()
		manhattanDistanceToNearestEnemy[y][x] = ManhattanDistanceNotSeen;
	}

	for (int i = 0; i < allUnits.size(); i++) {
		Unit& unit = allUnits[i];
		if (unit.get_team() != gc.get_team() && unit.is_on_map()) {
			MapLocation loc = unit.get_map_location();
			manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()] = 0;
			q.push(SimpleState(loc.get_y(), loc.get_x()));
		}
	}

	while (!q.empty()) {
		SimpleState cur = q.front();
		q.pop();

		// use d += 2 because we're doing manhattan distance
		for (int d = 0; d < 8; d += 2) {
			int ny = cur.y + dy[d];
			int nx = cur.x + dx[d];
			if (0 <= ny && ny < height && 0 <= nx && nx < width &&
					manhattanDistanceToNearestEnemy[ny][nx] == ManhattanDistanceNotSeen) {
				manhattanDistanceToNearestEnemy[ny][nx] = manhattanDistanceToNearestEnemy[cur.y][cur.x] + 1;
				q.push(SimpleState(ny, nx));
			}
		}
	}
}

static void tryToLoadRocket (Unit& unit) {

	//possibly slow performance
	MapLocation curLoc = unit.get_map_location();

	vector<Unit> loadableUnits = gc.sense_nearby_units_by_team(unit.get_map_location(), unit.get_vision_range(), myTeam);

	// bring along anything
	fo(i, 0, SZ(loadableUnits)) {
		if (gc.can_load(unit.get_id(), loadableUnits[i].get_id())) {

			if (loadableUnits[i].get_unit_type() == Worker && numWorkers <= 2) 
				continue;

			remove_type_from_friendly_unit_count(loadableUnits[i].get_unit_type());
			gc.load(unit.get_id(), loadableUnits[i].get_id());

			// @JERRY : I don't know what this does
			// loadedUnits.add(unit.get_id());
		}
	}
}

static void do_flood_fill (vector<SimpleState>& startingLocs, bool resultArr[55][55], bool passableArr[55][55]) {

	fo(y, 0, height) fo(x, 0, width) {
		resultArr[y][x] = false;
	}

	queue<SimpleState> q;
	for (SimpleState loc: startingLocs){
		resultArr[loc.y][loc.x] = true;
		q.push(loc);
	}

	while (!q.empty()){
		SimpleState cur = q.front(); q.pop();

		fo(d, 0, 8) {
			int ny = cur.y + dy[d];
			int nx = cur.x + dx[d];
			if (0 <= ny && ny < height && 0 <= nx && nx < width && passableArr[ny][nx] && !resultArr[ny][nx]) {
				resultArr[ny][nx] = true;

				q.push(SimpleState(ny,nx));
			}
		}
	}
}

static int can_reach_enemy_on_earth () {

	//returns either CANT_REACH_ENEMY, PARTIALLY_REACH_ENEMY or FULLY_REACH_ENEMY

	vector<SimpleState> spawn_locs;
	vector<Unit> initial_units = EarthMap.get_initial_units();

	fo(i, 0, SZ(initial_units)) {
		Unit& unit = initial_units[i];
		if (unit.get_team() == myTeam) {
			MapLocation loc = unit.get_map_location();
			spawn_locs.push_back(SimpleState(loc.get_y(),loc.get_x()));
		}
	}

	do_flood_fill(spawn_locs, can_reach_from_spawn, isPassable);

	bool doReachSomeEnemy = false;
	bool doMissSomeEnemy = false;

	fo(i, 0, SZ(initial_units)) {
		Unit& unit = initial_units[i];
		if (unit.get_team() == enemyTeam){
			MapLocation loc = unit.get_map_location();
			if (can_reach_from_spawn[loc.get_y()][loc.get_x()]) {
				doReachSomeEnemy = true;
			} else{
				doMissSomeEnemy = true;
			}
		}
	}

	if (doReachSomeEnemy && !doMissSomeEnemy) return FULLY_REACH_ENEMY;
	if (doReachSomeEnemy) return PARTIALLY_REACH_ENEMY;
	return CANT_REACH_ENEMY;
}

// finds the shortest distance to all squares from an array of starting locations
// unreachable squares get distance MultisourceBfsUnreachableMax
// dangerous squares get MultisourceBfsUnreachableMax + 300 - sq distance to enemy
// NOTE: DOES NOT allow movement through friendly units
// NOTE: DOES NOT allow movement through dangerous squares (as defined by isSquareDangerous[][])
static void multisourceBfsAvoidingUnitsAndDanger (vector<SimpleState>& startingLocs, int resultArr[55][55]) {
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		resultArr[y][x] = MultisourceBfsUnreachableMax;
		if (isSquareDangerous[y][x]) {
			resultArr[y][x] = MultisourceBfsUnreachableMax + 300 - attackDistanceToEnemy[y][x];
		}
	}
	queue<SimpleState> q;

	for (int i = 0; i < startingLocs.size(); i++) {
		SimpleState loc = startingLocs[i];
		resultArr[loc.y][loc.x] = 0;
		q.push(SimpleState(loc.y, loc.x));
	}

	while (!q.empty()) {
		SimpleState cur = q.front();
		q.pop();

		for (int d = 0; d < 8; d++) {
			int ny = cur.y + dy[d];
			int nx = cur.x + dx[d];
			if (0 <= ny && ny < height && 0 <= nx && nx < width &&
					isPassable[ny][nx] && !isSquareDangerous[ny][nx] && resultArr[ny][nx] == MultisourceBfsUnreachableMax) {
				resultArr[ny][nx] = resultArr[cur.y][cur.x] + 1;

				// if the next square has a friendly unit, mark down the distance to that square but don't continue the bfs
				// through that square
				//if (!hasFriendlyUnit[ny][nx])

				q.push(SimpleState(ny, nx));
			}
		}
	}
}

static bool doReplicate(Unit& unit) {
	if (unit.get_ability_heat() >= 10) {
		// on cooldown
		return false;
	}

	Direction replicateDir = Center;
	MapLocation cur_loc = unit.get_map_location();

	// If you are closer to your own base than your opponents, then:
	// Try to replicate towards best karbonite (ie karbonite close to enemy and far from your own base)
	// But don't go too far or you'll leave your own karbonite unfarmed
	// (Example map to see this is testmap. If you get too aggressive with stealing your opponents karbonite,
	// then you just switch places with the enemy team and get holed up in their corner...)
	// shortRangeBfsToBestKarbonite will return Center if none is found.
	if (replicateDir == Center && distToEnemyStartingLocs[cur_loc.get_y()][cur_loc.get_x()] >
			distToMyStartingLocs[cur_loc.get_y()][cur_loc.get_x()]) {
		// get direction as an int
		int karbonite_dir = shortRangeBfsToBestKarbonite(unit);

		if (karbonite_dir != DirCenterIndex) {
			// now find the direction closest to karbonite_dir that you can replicate in
			int cur_dir = karbonite_dir;
			for (int i = 0; i < 8; i++) {
				if (i & 1) cur_dir = (cur_dir + i) % 8;
				else cur_dir = (cur_dir + 8 - i) % 8;
				if (gc.can_replicate(unit.get_id(), directions[cur_dir])) {
					replicateDir = directions[cur_dir];
					break;
				}
			}
		}
	}

	// otherwise try to replicate next to currently building factory
	if (replicateDir == Center) {
		shuffleDirOrder();
		for (int i = 0; i < 8; i++) {
			Direction dir = directions[randDirOrder[i]];
			if (gc.can_replicate(unit.get_id(), dir) &&
					isNextToBuildingBlueprint(unit.get_map_location().add(dir))) {
				replicateDir = dir;
				break;
			}
		}
	}

	// otherwise, replicate wherever
	if (replicateDir == Center) {
		shuffleDirOrder();
		for (int i = 0; i < 8; i++) {
			Direction dir = directions[randDirOrder[i]];
			if (gc.can_replicate(unit.get_id(), dir)) {
				replicateDir = dir;
				break;
			}
		}
	}

	if (replicateDir != Center) {
		gc.replicate(unit.get_id(), replicateDir);
		MapLocation loc = unit.get_map_location().add(replicateDir);
		allMyUnits.push(gc.sense_unit_at_location(loc));
		hasFriendlyUnit[loc.get_y()][loc.get_x()] = true;
		numWorkers++;
		return true;
	}
	return false;
}

static bool doBlueprint (Unit& unit, UnitType toBlueprint) {
	shuffleDirOrder();
	int best = -1, bestAttackDistance = -1, bestSpace = -1;

	if (toBlueprint == Factory) {
		int wouldBeBest = best;

		// Find blueprint direction such that the factory will have the most free space around it
		// Don't blueprint in a location that's close to enemies
		for (int i = 0; i < 8; i++) {
			MapLocation loc = unit.get_map_location().add(directions[randDirOrder[i]]);
			if (0 <= loc.get_y() && loc.get_y() < height &&
					0 <= loc.get_x() && loc.get_x() < width) {
				// don't worry about attack distance more than 99
				int attackDistance = min(99, attackDistanceToEnemy[loc.get_y()][loc.get_x()]);
				// we want at least 5 squares around the factory
				int space = min(getSpaceAround(loc), 5);
				if ((space > bestSpace ||
							(space == bestSpace && attackDistance > bestAttackDistance)) &&
						gc.can_blueprint(unit.get_id(), toBlueprint, directions[randDirOrder[i]]) &&
						!will_blueprint_create_blockage(loc)) {
					bestAttackDistance = attackDistance;
					bestSpace = space;
					best = i;
				}
			}
		}

		// System.out.println("worker at " + unit.get_map_location() + " before decision " + wouldBeBest + " after decision + " + best);
	} else {
		// TODO: decide if we want Rockets to have minimal space like factories (see above)
		// currently this runs the same code as for Factory
		for (int i = 0; i < 8; i++) {
			if (gc.can_blueprint(unit.get_id(), toBlueprint, directions[randDirOrder[i]])) {
				int space = getSpaceAround(unit.get_map_location().add(directions[randDirOrder[i]]));
				if (space > bestSpace) {
					bestSpace = space;
					best = i;
				}
			}
		}
	}

	if (best != -1) {

		Direction dir = directions[randDirOrder[best]];

		// System.out.println("blueprinting in direction " + dir.toString() + " with space " + bestSpace);

		gc.blueprint(unit.get_id(), toBlueprint, dir);
		MapLocation loc = unit.get_map_location().add(dir);
		hasFriendlyUnit[loc.get_y()][loc.get_x()] = true;
		hasFriendlyStructure[loc.get_y()][loc.get_x()] = true;
		Unit other = gc.sense_unit_at_location(loc);

		if (toBlueprint == Factory) {
			factoriesBeingBuilt.push_back(other);
			numFactoryBlueprints++;
		} else {
			rocketsBeingBuilt.push_back(other);
			numRocketBlueprints++;
		}

		return true;
	}

	return false;
}

static bool doBuild(Unit& unit) {
	vector<Unit> units = gc.sense_nearby_units(unit.get_map_location(), 2);
	fo(i, 0, SZ(units)) {
		Unit& other = units[i];

		// only look at factories and rockets
		if (other.get_unit_type() != Factory && other.get_unit_type() != Rocket) {
			continue;
		}

		if (gc.can_build(unit.get_id(), other.get_id())) {
			gc.build(unit.get_id(), other.get_id());

			// If it's complete, remove it from the factoriesBeingBuilt list
			// need to re-sense the unit to get the updated structure_is_built() value
			if (gc.sense_unit_at_location(other.get_map_location()).structure_is_built() != 0) {
				//System.out.println("Finished a factory!");
				bool foundIt = false;

				if (other.get_unit_type() == Factory) {
					for (int j = 0; j < factoriesBeingBuilt.size(); j++) {
						if (factoriesBeingBuilt[j].get_map_location() == other.get_map_location()) {
							factoriesBeingBuilt.erase(factoriesBeingBuilt.begin()+j);
							foundIt = true;
							break;
						}
					}
				} else {
					for (int j = 0; j < rocketsBeingBuilt.size(); j++) {
						if (rocketsBeingBuilt[j].get_map_location() == other.get_map_location()) {
							rocketsReady.push_back(other);
							rocketsBeingBuilt.erase(rocketsBeingBuilt.begin()+j);
							foundIt = true;
							break;
						}
					}
				}
				if (!foundIt) {
					printf("ERROR: Structure was expected in ___BeingBuilt, but was missing!");
				}
			}
			return true;
		}
	}
	return false;
}

static bool doRepair(Unit& unit) {
	vector<Unit> units = gc.sense_nearby_units(unit.get_location().get_map_location(), 2);
	int bestId = -1;
	int maxHealthGap = -1;
	//greatest gap from max not lowest health as we don't want to be stuck healing a rocket on 200 HP with a factory on 230
	//possible optimization: prioritisation based of type / % health
	for (int i = 0; i < units.size(); i++) {
		Unit other = units[i];

		// only look at factories and rockets
		if (other.get_unit_type() != Factory && other.get_unit_type() != Rocket) {
			continue;
		}

		if (gc.can_repair(unit.get_id(), other.get_id())) {
			int healthGap = other.get_max_health()-other.get_health();
			if (healthGap>0 && (bestId==-1 || healthGap>maxHealthGap)){
				bestId = other.get_id();
				maxHealthGap = healthGap;
			}
		}
	}
	if (bestId!=-1){
		gc.repair(unit.get_id(),bestId);
		return true;
	}
	return false;
}

static bool isEnoughResourcesNearby(Unit& unit) {
	if (!unit.is_on_map()) {
		return false;
	}

	MapLocation loc = unit.get_map_location();
	int locY = loc.get_y(), locX = loc.get_x();

	int nearbyKarbonite = 0;
	// nearby workers includes yourself
	int nearbyWorkers = 0;

	for (int y = max(locY - IsEnoughResourcesNearbySearchDist, 0); y <= min(locY + IsEnoughResourcesNearbySearchDist, height - 1); y++) {
		for (int x = max(locX - IsEnoughResourcesNearbySearchDist, 0); x <= min(locX + IsEnoughResourcesNearbySearchDist, width - 1); x++) {
			if (dis[locY][locX][y][x] <= IsEnoughResourcesNearbySearchDist) {
				nearbyKarbonite += lastKnownKarboniteAmount[y][x];
			}
			if (hasFriendlyWorker[y][x]) {
				nearbyWorkers++;
			}
		}
	}

	bool worthReplicating = false;
	if (myPlanet == Earth) {
		//System.out.println("worker at " + loc.toString() + " considering replicating with " + nearbyKarbonite + " karbonite nearby and " + nearbyWorkers + " nearby workers");
		worthReplicating = nearbyKarbonite > 25 * (nearbyWorkers + 1);

		// if still setting up, we can afford more leniency
		if (numFactories + numFactoryBlueprints < 3 && nearbyKarbonite > 19 * (nearbyWorkers + 1) && nearbyWorkers < 7) {
			worthReplicating = true;
		}

	} else {
		worthReplicating = nearbyKarbonite > 31 * (nearbyWorkers + 1);
	}

	return worthReplicating;
}

static bool bfsTowardsBlueprint(Unit& unit) {

	if (SZ(rocketsBeingBuilt) + SZ(factoriesBeingBuilt) == 0) {
		return false;
	}

	int dist = 4;

	MapLocation loc = unit.get_map_location();
	int locY = loc.get_y();
	int locX = loc.get_x();

	for (int y = max(locY - dist, 0); y <= min(locY + dist, height - 1); y++) {
		for (int x = max(locX - dist, 0); x <= min(locX + dist, width - 1); x++) {
			bfsTowardsBlueprintDist[y][x] = -1;
		}
	}

	queue<BfsState> q;

	q.push(BfsState(locY, locX, DirCenterIndex));
	bfsTowardsBlueprintDist[locY][locX] = 0;

	Direction dirToMove = Center;

	while (!q.empty()) {
		BfsState cur = q.front();
		q.pop();


		if (bfsTowardsBlueprintDist[cur.y][cur.x] < dist) {
			for (int d = 0; d < 8; d++) {
				int ny = cur.y + dy[d];
				int nx = cur.x + dx[d];
				if (0 <= ny && ny < height && 0 <= nx && nx < width &&
						isPassable[ny][nx] && !isSquareDangerous[ny][nx] &&
						bfsTowardsBlueprintDist[ny][nx] == -1) {
					bfsTowardsBlueprintDist[ny][nx] = bfsTowardsBlueprintDist[cur.y][cur.x] + 1;

					if (bfsTowardsBlueprintDist[cur.y][cur.x] == 0) {
						// first move so set starting direction
						cur.startingDir = d;
					}

					// if the next square has a friendly unit, mark down the distance to that square but don't continue the bfs
					// through that square
					if (!hasFriendlyUnit[ny][nx]) {
						q.push(BfsState(ny, nx, cur.startingDir));
					} else {
						// check if the square is a blueprint
						MapLocation nextLoc(myPlanet, nx, ny);
						if (gc.has_unit_at_location(nextLoc)) {
							Unit other = gc.sense_unit_at_location(nextLoc);
							if (other.get_team() == myTeam &&
									(other.get_unit_type() == Factory || other.get_unit_type() == Rocket)
									&& other.structure_is_built() == 0) {
								dirToMove = directions[cur.startingDir];
								break;
							}
						}
					}
				}
			}
		}

		if (dirToMove != Center) {
			// found blueprint
			break;
		}
	}

	if (dirToMove != Center) {
		doMoveRobot(unit, dirToMove);
		//System.out.println("robot at " + unit.get_map_location() + " found nearby blueprint");
		return true;
	}

	//System.out.println("robot at " + unit.get_map_location() + " DIDN'T find nearby blueprint");
	return false;
}

static pair<int,int> get_closest_good_position (int y, int x, bool taken_array[55][55], vector<pair<int, int>>& good_list) {
	int bestDis = 999;
	int candidates = 0;
	fo(i, 0, SZ(good_list)) {
		int curY = good_list[i].first;
		int curX = good_list[i].second;
		if (!taken_array[curY][curX] && !hasFriendlyUnit[curY][curX]) {
			if (candidates == 0 || dis[y][x][curY][curX] < bestDis) {
				getClosestFreeGoodPositionCandidates[0] = i;
				candidates = 1;
				bestDis = dis[y][x][curY][curX];
			} else if (dis[y][x][curY][curX] == bestDis) {
				getClosestFreeGoodPositionCandidates[candidates] = i;
				candidates++;
			}
		}
	}

	if (candidates == 0) {
		return make_pair(-1, -1);
	} else {
		int best = getClosestFreeGoodPositionCandidates[rand() % candidates];
		return good_list[best];
	}
}

// checks whether making a blueprint at loc will block off the surrounding squares from each other
// does a bfs from one of the adjacent squares for distance search_dist and checks whether it can
// reach all the other adjacent squares
static bool will_blueprint_create_blockage(MapLocation loc) {
	static const int search_dist = 6;
	int loc_y = loc.get_y();
	int loc_x = loc.get_x();
	// Note: need to add/subtract 1 for each of the directions that we reset the seen array
	// because we start from a square adjacent to loc and then travel search_dist so it's actually search_dist + 1 distance
	for (int y = std::max(0, loc_y - search_dist - 1); y <= std::min(loc_y + search_dist + 1, height - 1); y++) {
		for (int x = std::max(0, loc_x - search_dist - 1); x <= std::min(loc_x + search_dist + 1, width - 1); x++) {
			bfs_seen[y][x] = false;
		}
	}

	int start_y = -1, start_x = -1;
	for (int i = 0; i < 8; i++) {
		int ny = loc_y + dy[i];
		int nx = loc_x + dx[i];
		if (0 <= ny && ny < height && 0 <= nx && nx < width &&
				isPassable[ny][nx] &&
				!hasFriendlyStructure[ny][nx]) {
			start_y = ny;
			start_x = nx;
			break;
		}
	}

	if (start_y == -1 || start_x == -1) {
		// no adjacent squares (which is impossible...) but theoretically if there were no adjacent squares
		// this wouldn't create a blockage
		printf("ERROR: Impossible situation in blueprint_wont_create_blockage()");
		return false;
	}

	std::queue<std::tuple<int,int,int>> q;
	q.emplace(start_y, start_x, 0);
	bfs_seen[start_y][start_x] = true;
	while (!q.empty()) {
		int cur_y, cur_x, cur_dist;
		std::tie(cur_y, cur_x, cur_dist) = q.front();
		q.pop();

		if (cur_dist >= search_dist) {
			continue;
		}

		for (int i = 0; i < 8; i++) {
			int ny = cur_y + dy[i];
			int nx = cur_x + dx[i];
			if (0 <= ny && ny < height && 0 <= nx && nx < width &&
					!bfs_seen[ny][nx] &&
					isPassable[ny][nx] &&
					!hasFriendlyStructure[ny][nx] &&
					!(ny == loc_y && nx == loc_x)) {
				bfs_seen[ny][nx] = true;
				q.emplace(ny, nx, cur_dist + 1);
			} 
		}
	}

	// check if any of the adjacent squares were not reached
	for (int i = 0; i < 8; i++) {
		int ny = loc_y + dy[i];
		int nx = loc_x + dx[i];
		if (0 <= ny && ny < height && 0 <= nx && nx < width &&
				isPassable[ny][nx] &&
				!hasFriendlyStructure[ny][nx]) {
			if (!bfs_seen[ny][nx]) {
				return true;
			}
		}
	}

	return false;
}

// bfs towards karbonite, and if you're already on karbonite, move towards squares that are further from your base
// and closer to their base
// In particular, this square searches for the square within distance search_dist that has
// the minimum (distance to enemy starting locs - distance to my starting locs)
// Edit: We don't want to go too far towards the enemy, so we cap the distance metric so it can't go below 0.
// Returns Center if no other karbonite is found
static int shortRangeBfsToBestKarbonite(Unit &unit) {
	static const int search_dist = 4;

	MapLocation loc = unit.get_map_location();
	int start_y = loc.get_y();
	int start_x = loc.get_x();
	for (int y = std::max(start_y - search_dist, 0); y <= std::min(start_y + search_dist, height - 1); y++) {
		for (int x = std::max(start_x - search_dist, 0); x <= std::min(start_x + search_dist, width - 1); x++) {
			bfs_seen[y][x] = false;
		}
	}

	std::queue<std::tuple<int,int,int,int>> q;
	q.emplace(start_y, start_x, 0, DirCenterIndex);
	bfs_seen[start_y][start_x] = true;

	// distance metric described in comment above
	int best_dist_metric = 99999999, best_dir = DirCenterIndex;
	while (!q.empty()) {
		int cur_y, cur_x, cur_dist, cur_starting_dir;
		std::tie(cur_y, cur_x, cur_dist, cur_starting_dir) = q.front();
		q.pop();

		int cur_dist_metric =
			std::max(0, distToEnemyStartingLocs[cur_y][cur_x] - distToMyStartingLocs[cur_y][cur_x]);
		if (lastKnownKarboniteAmount[cur_y][cur_x] > 0 && cur_dist_metric < best_dist_metric) {
			best_dist_metric = cur_dist_metric;
			best_dir = cur_starting_dir;
		}

		if (cur_dist >= search_dist) {
			continue;
		}

		shuffleDirOrder();
		for (int i = 0; i < 8; i++) {
			int ny = cur_y + dy[randDirOrder[i]];
			int nx = cur_x + dx[randDirOrder[i]];
			if (0 <= ny && ny < height &&
					0 <= nx && nx < width &&
					isPassable[ny][nx] &&
					!hasFriendlyUnit[ny][nx] &&
					!bfs_seen[ny][nx]) {
				if (cur_dist == 0) {
					// first move, set the starting direction
					cur_starting_dir = randDirOrder[i];
				}
				bfs_seen[ny][nx] = true;
				q.emplace(ny, nx, cur_dist + 1, cur_starting_dir);
			}
		}
	}

	return best_dir;
}

static void doBlueprintMovement(Unit &unit, bool &doneMovement) {
	if (!doneMovement && isNextToBuildingBlueprint(unit.get_map_location())) {
		// if next to blueprint, stay next to blueprint
		doneMovement = true;
	}
	if (!doneMovement) {
		// if very near a blueprint, move towards it

		if (bfsTowardsBlueprint(unit)) {
			doneMovement = true;
		}
	}
}

static void doKarboniteMovement(Unit &unit, bool &doneMovement) {
	if (!doneMovement) {
		//  move towards karbonite

		// try to move towards nearby karbonite squares that are nearer the enemy base and further from your own base
		if (!doneMovement) {
			int dir_index = shortRangeBfsToBestKarbonite(unit);
			if (dir_index != DirCenterIndex) {
				Direction dir = directions[dir_index];
				doMoveRobot(unit, dir);
				doneMovement = true;
			}
		}

		// otherwise move to the closest karbonite
		if (!doneMovement) {
			MapLocation loc = unit.get_map_location();
			if (distToKarbonite[loc.get_y()][loc.get_x()] != MultisourceBfsUnreachableMax) {
				tryMoveToLoc(unit, distToKarbonite);
				doneMovement = true;
			}
		}
	}
}
