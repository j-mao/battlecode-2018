#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <assert.h>
#include <random>
#include <functional>

#include <utility>
#include <set>
#include <map>
#include <vector>
#include <queue>
#include <algorithm>
#include <iostream>
#include <iomanip>
#include <chrono>

#include "bc.hpp"

#define fo(i,a,b) for (int i=(a);i<(b);i++)
#define SZ(a) ((int) a.size())

#ifdef NDEBUG
 #define DEBUG_OUTPUT(x...)
#else
 #define DEBUG_OUTPUT(x...) printf(x)
#endif

#ifdef assert
 #undef assert
#endif
void failure_occurred(const char *assertion, const char *file, const unsigned line) {
	printf("[ASSERTION FAILED] %s:%d    %s\n", file, line, assertion);
	fflush(stdout);
	exit(1);
}
#define assert(expr) \
 ((expr) \
  ?  (void) (0) \
  : failure_occurred(#expr, __FILE__, __LINE__))

using namespace bc;
using namespace std;
using namespace std::chrono;

static int roundNum;

static bool raceToMars = false;

static int maxConcurrentRocketsReady = 2;

static int connectedComponent[55][55]; // connected component id of each cell
static int whichConnectedComponent[65536]; // connected component id of each unit

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
static bool hasEnemyUnit[55][55];
static int enemyUnitHealth[55][55];
static int enemyUnitId[55][55];
static int availableOverchargeId[55][55];

static int friendlyKnightCount[55][55];

// temporary seen array
static bool bfs_seen[55][55];

// which direction you should move to reach each square, according to the bfs
static int bfsDirectionIndexTo[55][55];
static MapLocation bfsClosestKarbonite;
static MapLocation bfsClosestEnemy;
static MapLocation bfsClosestFreeGoodPosition;

// battalion assignments
static map<int, int> battalionId;
static set<int> battalionLeaders;
static int battalionSizes[65536];
static MapLocation battalionDestinations[65536];

// to random shuffle the directions
static int randDirOrder[55];

// factories that are currently blueprints
static vector<Unit> factoriesBeingBuilt;
static vector<Unit> rocketsBeingBuilt;
static vector<Unit> rockets_to_fill;

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
// These variables are for making the worker replicate towards the center on maps where there are lots of resources in the "centre"
// The "centre" of the map is defined as the area where abs(distToEnemyStartingLocs - distToMyStartingLocs) is <= centreSize
// centreSize will be different on each map, depending on the distance between starting locs
// (Note: this heuristic could be pretty wrong, e.g. on disconnected component maps.)
static int shortestDistToEnemyStartLoc;
static int centreSize;
static int distToTheCentre[55][55];
// centreKarbonite is the total amount of karbonite on "centre" squares
static int centreKarbonite;
// when a worker decides to replicate towards the centre, it makes it less worth it for other workers to replicate towards as well
// So we keep track of "how much centre karbonite is going to be left" after a chuck is assumed to be taken
// by the first replicating worker
static int centreKarboniteLeft;
// whether we've reached the centre yet. Once we've reached the centre, let the rest of the old worker logic take over
static bool reachedCentre;
// whether each square is a "centre square"
static bool isCentreSquare[55][55];
// this is the dist array (from multisourceBfs) to the closest karbonite square that is a "centre" square
static int distToCentreKarbonite[55][55];

static int time_since_damaged_unit[55][55];
static bool is_good_healer_position[55][55];
static bool is_good_healer_position_taken[55][55];
static vector<pair<int, int>> good_healer_positions;

static int distToDamagedFriendlyHealableUnit[55][55];

static bool is_good_mage_position[55][55];
static bool is_good_mage_position_taken[55][55];
static vector<pair<int, int>> good_mage_positions;

static int mageAttackPriorities[55][55];

static int bfsTowardsBlueprintDist[55][55];

static int numEnemiesThatCanAttackSquare[55][55];

static int distToMyStartingLocs[55][55];
static int distToEnemyStartingLocs[55][55];

static int distToNearestEnemyFighter[55][55];
static int distToNearestFriendlyFighter[55][55];

static int numIdleRangers;
static int numIdleMages;

static bool is_attack_round;

// an array 0..8
// the array index is the degree of the location: how many units you can unload simultaneously
// we prioritise the best, of course
static vector<MapLocation> goodRocketLandingLocations[9];

// whether it's "very early game"
// during the very early game we want our replicating workers to explore as fast as possible
// currently defined as true until you see an enemy fighting unit, or you reach a square where
// distance to enemy starting locs <= distance to friendly starting locs
static bool is_very_early_game;

// TODO: generalise this to all research types. Like... current_x_research_level or something idk.
static bool has_overcharge_researched;
static bool has_blink_researched;

// we are ready to build mages once we have overcharge, or once we are "close" to getting overcharge
static const unsigned CLOSE_TO_RESEARCH_DONE = 40; // 40 turns

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

static const int MageAttackRange = 30;
// distance you have to be to be a blink and a move away from mage attack range
static const int MageReadyToBlinkRange = 80;
// distance you have to be to be a single move away from mage attack range
static const int OneMoveFromMageAttackRange = 45;
// distance you have to be to be two moves away from mage attack range
static const int TwoMovesFromMageAttackRange = 63;

// Distance around a worker to search for whether there is enough karbonite to be worth replicating
static int IsEnoughResourcesNearbySearchDist = 8;

static int getClosestFreeGoodPositionCandidates[50 * 50 + 5];

// rocket summoning: mapping unit id to target rocket
static map<int, pair<int, int>> unit_summon;

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
int get_unit_order_priority (const Unit& unit);
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
static void runMage (Unit& unit);

static void computeBattalionSizes();
static void runBattalions(vector<Unit>& myUnits);

static void calculateManhattanDistancesToClosestEnemies(vector<Unit>& allUnits);
static void doMoveRobot (Unit& unit, Direction dir, bool force=false);
static void doBlinkRobot (Unit& unit, Direction dir);
static bool doOvercharge (const std::pair<int, int> &healer_loc, Unit &unit_to_overcharge);
static void shuffleDirOrder();
static bool tryToMoveToRocket (Unit& unit );
static int getTendency(Unit& unit);
static void updateTendency(int id, int changeChance);
static int moveDistance(MapLocation a, MapLocation b);
static int getSpaceAround(MapLocation loc);
static bool isFriendlyStructure(Unit unit);
static int dirToAdjacentBuildingBlueprint(MapLocation loc);
static void moveButStayNextToLoc(Unit &unit, const MapLocation &loc);
static bool moveToAttackLocs (Unit& unit);
static void get_available_overcharges_in_range(Unit& unit, std::vector<std::pair<int, int>> &result_vector);

static MapLocation getRandomMapLocation(Planet p, const MapLocation &giveUp);
static void moveToTendency(Unit& unit);
static int getRangerAttackPriority(const Unit& unit);
static int getMageAttackPriority(Unit& unit);
static int getKnightAttackPriority(Unit& unit);
static bool rangerTryToAttack(Unit& unit);
static bool mageTryToAttack(Unit& unit);
static void mageTryToBomb(Unit& unit);
static void tryToHeal(Unit& unit);
static void multisourceBfsAvoidingUnitsAndDanger (vector<SimpleState>& startingLocs, int resultArr[55][55]);
static void multisourceBfsAvoidingNothing (vector<SimpleState>& startingLocs, int resultArr[55][55]);
static bool doReplicate(Unit& unit);
static void checkForEnemyUnits (vector<Unit>& allUnits);

template<typename T>
static void do_flood_fill(vector<SimpleState>& startingLocs, T resultArr[55][55], bool passableArr[55][55], T label);
static int can_reach_enemy_on_earth();

static bool bfsTowardsBlueprint(Unit& unit);
static bool doBuild(Unit& unit);
static bool doBlueprint(Unit& unit, UnitType toBlueprint);
static bool doRepair(Unit& unit);
static bool isEnoughResourcesNearby(Unit& unit);
static std::tuple<int,int,int> factoryGetNearbyStuff(Unit& unit);
static bool tryMoveToLoc (Unit& unit, int distArray[55][55]);
static void tryToLoadRocket (Unit& unit);
static pair<int,int> get_closest_good_position (int y, int x, bool taken_array[55][55], vector<pair<int, int>>& good_list);
static int get_dir_index (Direction dir);
static bool will_blueprint_create_blockage(MapLocation loc);
static int shortRangeBfsToBestKarbonite(Unit &unit);
static bool knight_bfs_to_enemy_unit(Unit &unit);

static void doBlueprintMovement(Unit &unit, bool &doneMove);
static void doKarboniteMovement(Unit &unit, bool &doneMove);

static void match_units_to_rockets (vector<Unit>& input_units);
static bool try_summon_move (Unit& unit);

static int howMuchCentreKarbToBeWorthIt(int distToCentre);

static inline int distance_squared(int dy, int dx) {
	return dy * dy + dx * dx;
}

// some units should move before others to avoid getting in the way (for Mars Battalions)
class compareUnitMovementOrdering {
	public:
		static int referenceY, referenceX;
		bool operator() (const Unit &a, const Unit &b) {
			return dis[referenceY][referenceX][a.get_map_location().get_y()][a.get_map_location().get_x()] > dis[referenceY][referenceX][b.get_map_location().get_y()][b.get_map_location().get_x()];
		}
};
// initialise so that the compiler doesn't vomit out a wall of text
int compareUnitMovementOrdering::referenceY = 0;
int compareUnitMovementOrdering::referenceX = 0;

// Store this list of all our units ourselves, so that we can add to it when we create units and use those new units
// immediately.
static vector<Unit> allMyUnitsVector;
// Priority queue of all my units. Each pair contains the weight which is negative get_unit_order_priority(unit), and
// the index in allMyUnitsVector of the unit.
static priority_queue<std::pair<int, int>> allMyUnitsPriorityQueue;

static void add_unit_to_all_my_units (const Unit &unit) {
	allMyUnitsVector.push_back(unit);
	allMyUnitsPriorityQueue.emplace(-get_unit_order_priority(unit), int(allMyUnitsVector.size() - 1));
}

static int get_dir_index (Direction dir) {
	fo(i, 0, 8) if (directions[i] == dir) {
		return i;
	}
	DEBUG_OUTPUT("ERROR: Couldn't find dir index for %d\n", dir);
	return 0;
}

static int get_microseconds(clock_t start, clock_t end) {
	return (int)((long long)(end - start) * 1000000 / CLOCKS_PER_SEC);
}

struct TimeStats {
	std::string name;
	std::vector<int> times;
	TimeStats(std::string name) : name(name) {}
	void add(int time) { times.push_back(time); }
	void clear() { times.clear(); }
	void print() {
		std::sort(times.begin(), times.end());
		int n = int(times.size());
		DEBUG_OUTPUT("\n");
		DEBUG_OUTPUT("Stats for '%s'\n", name.c_str());
		DEBUG_OUTPUT("%d data points\n", n);
		if (n > 0) {
			DEBUG_OUTPUT("min, 25%%, median, 75%%, max = %5d, %5d, %5d, %5d, %5d\n", times[0], times[n/4], times[n/2], times[n * 3 / 4], times[n-1]);
			int sum = 0;
			for (int time : times) {
				sum += time;
			}
			DEBUG_OUTPUT("average, sum = %5d, %6d\n", sum / n, sum);
		}
	}
};

// Stats section 1: declarations (some of them)
TimeStats mage_attack_stats("Mage Attacks");
TimeStats ranger_attack_stats("Ranger Attacks");
TimeStats mage_attack_sense_stats("Mage Attack sense_nearby_units()");
TimeStats ranger_attack_sense_stats("Ranger Attack sense_nearby_units()");
TimeStats gc_get_unit_stats("Calls to gc.get_unit(unit_id)");
TimeStats queue_pop_stats("Getting elements from allMyUnits priority queue");

int main() {
	DEBUG_OUTPUT("Player C++ bot starting\n");
	DEBUG_OUTPUT("Connecting to manager...\n");

	std::default_random_engine generator;
	std::uniform_int_distribution<int> distribution (0,8);
	auto dice = std::bind ( distribution , generator );

	srand(420);

	if (bc_has_err()) {
		// If there was an error creating gc, just die.
		DEBUG_OUTPUT("Failed, dying.\n");
		exit(1);
	}
	DEBUG_OUTPUT("Connected!\n");
	fflush(stdout);

	init_global_variables();
	DEBUG_OUTPUT("Finished init_global_variables()\n");

	reach_enemy_result = can_reach_enemy_on_earth();
	DEBUG_OUTPUT("reach_enemy_result: %d\n",reach_enemy_result);

	if (myPlanet == Mars) {
		while (true) {
			// DEBUG_OUTPUT("Starting round %d\n", roundNum);

			vector<Unit> units = gc.get_my_units();
			init_turn(units);

			// if there are good ranger positions or if there are attackLocs
			// we should go into battle
			// otherwise, there isnt much for us to do
			if (good_ranger_positions.empty() && attackLocs.empty()) {
				// we are alone on this barren land
				// patrol in battalions
				runBattalions(units);
			}

			while (!allMyUnitsPriorityQueue.empty()) {
				Unit unit = allMyUnitsVector[allMyUnitsPriorityQueue.top().second];
				allMyUnitsPriorityQueue.pop();

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
						runMage(unit);
						break;
				}

			}

			fflush(stdout);
			gc.next_turn();
			roundNum++;
		}
	} else {
		/*gc.queue_research(Worker);
		gc.queue_research(Ranger);
		gc.queue_research(Healer);
		gc.queue_research(Healer);
		gc.queue_research(Healer);
		gc.queue_research(Mage);
		gc.queue_research(Mage);
		gc.queue_research(Rocket);
		gc.queue_research(Ranger);
		gc.queue_research(Worker);
		gc.queue_research(Worker);
		gc.queue_research(Worker);
		gc.queue_research(Rocket);
		gc.queue_research(Rocket);*/
		gc.queue_research(Healer);
		gc.queue_research(Healer);
		gc.queue_research(Healer);
		gc.queue_research(Mage);
		gc.queue_research(Mage);
		gc.queue_research(Mage);
		gc.queue_research(Mage);

		int total_time = 0;
		int prev_time_left_ms = gc.get_time_left_ms();
		clock_t prev_time = clock();
		high_resolution_clock::time_point prev_point = high_resolution_clock::now();

		while (true) {
			DEBUG_OUTPUT("\n======================\nStarting round %d\n", roundNum);
			if (gc.get_time_left_ms() < 300) {
				DEBUG_OUTPUT("Warning: Running out of time! Skipping turn! (Less than 300 ms left.)\n");
			} else {
				/*int cur_time_left_ms = gc.get_time_left_ms();
				clock_t cur_time = clock();
				high_resolution_clock::time_point cur_point = high_resolution_clock::now();
				static const int extra_time_per_round_ms = 25;
				DEBUG_OUTPUT("time left (milliseconds): %d\n", cur_time_left_ms);
				DEBUG_OUTPUT(" gc time             (milliseconds) used last round (with correction) = %6d\n", int(prev_time_left_ms - cur_time_left_ms) + extra_time_per_round_ms);
				DEBUG_OUTPUT("  c time  (cpu time) (MICROseconds) used last round                   = %6d\n", get_microseconds(prev_time, cur_time));
				std::cout << "c++ time (real time) (MICROseconds) used last round                   = " << std::setw(6) << (int)duration_cast<microseconds>(cur_point - prev_point).count() << std::endl;
				DEBUG_OUTPUT("\n");
				total_time += (int)duration_cast<milliseconds>(cur_point - prev_point).count();
				DEBUG_OUTPUT("total time for the game so far (milliseconds) = %d\n", total_time);
				prev_time_left_ms = cur_time_left_ms;
				prev_time = cur_time;
				prev_point = cur_point;*/

				TimeStats get_unit_stats("Getting units from allMyUnits");
				TimeStats ranger_stats("Ranger");
				TimeStats worker_stats("Worker");
				TimeStats healer_stats("Healer");
				TimeStats factory_stats("Factory");
				TimeStats rocket_stats("Rocket");
				TimeStats mage_stats("Mage");
				// Stats section 2: clearing 
				ranger_attack_stats.clear();
				ranger_attack_sense_stats.clear();
				gc_get_unit_stats.clear();
				queue_pop_stats.clear();

				clock_t before_init_turn = clock();
				vector<Unit> units = gc.get_my_units();
				init_turn(units);
				clock_t after_init_turn = clock();
				// DEBUG_OUTPUT("  pre-turn setup took %6d microseconds\n", get_microseconds(before_init_turn, after_init_turn));

				clock_t before_matching_to_rockets = clock();
				match_units_to_rockets(units);
				clock_t after_matching_to_rockets = clock();
				DEBUG_OUTPUT("  matching units to rockets took %6d microseconds\n", get_microseconds(before_init_turn, after_init_turn));

				if (roundNum >= 400) {
					start_racing_to_mars();
				}


				clock_t before_units = clock();

				while (!allMyUnitsPriorityQueue.empty()) {
					clock_t before_getting_unit = clock();

					clock_t before_queue_pop = clock();
					Unit unit = allMyUnitsVector[allMyUnitsPriorityQueue.top().second];
					allMyUnitsPriorityQueue.pop();
					clock_t after_queue_pop = clock();
					queue_pop_stats.add(get_microseconds(before_queue_pop, after_queue_pop));

					if (!gc.can_sense_unit(unit.get_id())) {
						continue;
					}

					if (!unit.is_on_map()) {
						continue;
					}

					clock_t after_getting_unit = clock();
					get_unit_stats.add(get_microseconds(before_getting_unit, after_getting_unit));

					switch (unit.get_unit_type()) {
						case Worker:
							{
								clock_t before = clock();
								runEarthWorker(unit);
								clock_t after = clock();
								worker_stats.add(get_microseconds(before, after));
							}
							break;
						case Factory:
							{
								clock_t before = clock();
								runFactory(unit);
								clock_t after = clock();
								factory_stats.add(get_microseconds(before, after));
							}
							break;
						case Ranger:
							{
								clock_t before = clock();
								runRanger(unit);
								clock_t after = clock();
								ranger_stats.add(get_microseconds(before, after));
							}
							break;
						case Knight:
							runKnight(unit);
							break;
						case Healer:
							{
								clock_t before = clock();
								runHealer(unit);
								clock_t after = clock();
								healer_stats.add(get_microseconds(before, after));
							}
							break;
						case Rocket:
							{
								clock_t before = clock();
								runEarthRocket(unit);
								clock_t after = clock();
								rocket_stats.add(get_microseconds(before, after));
							}
							break;
						case Mage:
							{
								clock_t before = clock();
								runMage(unit);
								clock_t after = clock();
								mage_stats.add(get_microseconds(before, after));
							}
							break;
					}

				}

				clock_t after_units = clock();
				//DEBUG_OUTPUT("processing units took %6d microseconds\n", get_microseconds(before_units, after_units));

				// Stats section 3: printing
				/*
				get_unit_stats.print();
				queue_pop_stats.print();
				*/
				/*ranger_stats.print();
				healer_stats.print();
				worker_stats.print();
				factory_stats.print();
				rocket_stats.print();
				ranger_attack_stats.print();
				ranger_attack_sense_stats.print();*/
				//gc_get_unit_stats.print();

				//DEBUG_OUTPUT("Number of idle rangers is %3d / %3d\n", numIdleRangers, numRangers);
			}

			fflush(stdout);
			gc.next_turn();
			roundNum++;
		}
	}
}

static void init_turn (vector<Unit>& myUnits) {
	unit_summon.clear();

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
		if (unit.is_on_map()) {
			whichConnectedComponent[unit.get_id()] = connectedComponent[unit.get_map_location().get_y()][unit.get_map_location().get_x()];
		}
	}

	for (int y = 0; y < height; y++) {
		for (int x = 0; x < width; x++) {
			hasFriendlyUnit[y][x] = false;
			hasFriendlyStructure[y][x] = false;
			hasEnemyUnit[y][x] = false;
			enemyUnitHealth[y][x] = 0;
			attackDistanceToEnemy[y][x] = 9999;
			is_good_ranger_position[y][x] = false;
			is_good_ranger_position_taken[y][x] = false;
			isSquareDangerous[y][x] = false;
			numEnemiesThatCanAttackSquare[y][x] = 0;
			mageAttackPriorities[y][x] = 0;
			friendlyKnightCount[y][x] = 0;
			availableOverchargeId[y][x] = -1;

			time_since_damaged_unit[y][x]++;
			is_good_healer_position[y][x] = false;
			is_good_healer_position_taken[y][x] = false;

			is_good_mage_position[y][x] = false;
			is_good_mage_position_taken[y][x] = false;

			MapLocation loc(myPlanet, x, y);
			if (gc.can_sense_location(loc)) {
				lastKnownKarboniteAmount[y][x] = (int) gc.get_karbonite_at(loc);
			}
		}
	}

	// Is attack round
	// In these rounds, rangers move forward and attack, and healers will overcharge
	// TODO: change this (gp)
	// TODO: take into account current ranger upgrade level
	// TODO: make it not as deterministic. Make it every 6/7 rounds without upgrades and 5/6 rounds with upgrades or something
	is_attack_round = false;
	if (roundNum % 5 == 0) {
		is_attack_round = true;
	}

	// reset the amount of centre karbonite "left" over
	centreKarboniteLeft = centreKarbonite;

	// Research
	ResearchInfo research_info = gc.get_research_info();
	if (!has_overcharge_researched) {
		has_overcharge_researched = research_info.get_level(Healer) >= 3;
	}
	if (!has_blink_researched) {
		has_blink_researched = research_info.get_level(Mage) >= 4;
	}
	//reset unit counts
	numWorkers = 0; numKnights = 0; numRangers = 0; numMages = 0; numHealers = 0; numFactories = 0; numRockets = 0;
	numFactoryBlueprints = 0; numRocketBlueprints = 0;

	numIdleRangers = 0;
	numIdleMages = 0;

	rockets_to_fill.clear();

	vector<SimpleState> damagedFriendlyHealableUnits;
	vector<SimpleState> enemyFighters;
	vector<SimpleState> friendlyFighters;
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
					//DEBUG_OUTPUT("damaged dude at %d %d\n", loc.get_y(), loc.get_x());

					int locY = loc.get_y(), locX = loc.get_x();

					time_since_damaged_unit[locY][locX] = 0;

					damagedFriendlyHealableUnits.push_back(SimpleState(locY, locX));
				}

				if (is_fighter_unit_type(unit.get_unit_type())) {
					friendlyFighters.push_back(SimpleState(loc.get_y(), loc.get_x()));
				}

				// worker cache position
				if (unit.get_unit_type() == Worker) {
					hasFriendlyWorker[loc.get_y()][loc.get_x()] = true;
				}

				if (unit.get_unit_type() == Knight) {
 					friendlyKnightCount[loc.get_y()][loc.get_x()]++;
				}

				if (has_overcharge_researched && unit.get_unit_type() == Healer && unit.get_ability_heat() < 10) {
					availableOverchargeId[loc.get_y()][loc.get_x()] = unit.get_id();
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
						rockets_to_fill.push_back(unit);
					}

					numRockets++;
				} else numRocketBlueprints++;

			} else {
				add_type_to_friendly_unit_count(unit.get_unit_type());
			}
		} else {
			// enemy team

			// calculate closest distances to enemy fighter units
			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();
				int locY = loc.get_y(), locX = loc.get_x();

				hasEnemyUnit[locY][locX] = true;
				enemyUnitHealth[locY][locX] = unit.get_health();
				enemyUnitId[locY][locX] = unit.get_id();
				mageAttackPriorities[locY][locX] = getMageAttackPriority(unit);

				if (is_fighter_unit_type(unit.get_unit_type())) {
					// we've seen an enemy, mark as not the very early game anymore
					is_very_early_game = false;

					enemyFighters.push_back(SimpleState(locY, locX));

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
	}

	// calculate dangerous squares
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		if (attackDistanceToEnemy[y][x] <= DangerDistanceThreshold) {
			isSquareDangerous[y][x] = true;
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
	//DEBUG_OUTPUT("%d good healer positions\n", SZ(good_healer_positions));

	// calculate good positions

	good_mage_positions.clear();
	fo(y, 0, height) fo(x, 0, width) {
		if (RangerAttackRange < attackDistanceToEnemy[y][x] && attackDistanceToEnemy[y][x] <= MageReadyToBlinkRange) {
			is_good_mage_position[y][x] = true;
			good_mage_positions.push_back(make_pair(y, x));
		}
	}
	//DEBUG_OUTPUT("%d good mage positions\n", SZ(good_mage_positions));

	while (!allMyUnitsPriorityQueue.empty()) {
		allMyUnitsPriorityQueue.pop();
	}
	allMyUnitsVector.clear();
	for (int i = 0; i < myUnits.size(); i++) {
		// TODO: make this faster?
		// TODO: just set allMyUnitsVector = myUnits ?
		// TODO: use std::move() or something? If only I knew how std::move actually worked.
		add_unit_to_all_my_units(myUnits[i]);
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

	// calculate distances to nearest friendly and enemy fighter units
	multisourceBfsAvoidingNothing(enemyFighters, distToNearestEnemyFighter);
	multisourceBfsAvoidingNothing(friendlyFighters, distToNearestFriendlyFighter);

	// Calculate good ranger positions
	// Don't include:
	// 1) impassable terrain
	// 2) "good ranger positions" that are closer to enemy units than to ours
	//    (This gets rid of those "unreachable", misleading "good positions" that are on the other side of their army)
	good_ranger_positions.clear();
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		if (RangerAttackRange < attackDistanceToEnemy[y][x] && attackDistanceToEnemy[y][x] <= OneMoveFromRangerAttackRange &&
			distToNearestEnemyFighter[y][x] >= distToNearestFriendlyFighter[y][x] &&
			isPassable[y][x]) {
			is_good_ranger_position[y][x] = true;
			good_ranger_positions.push_back(make_pair(y, x));
			//System.out.println("good position at " + y + " " + x);
		}
	}
	//DEBUG_OUTPUT("Number of good ranger positions: %d\n", int(good_ranger_positions.size()));

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

	fo(i, 0, 9) directions.push_back((Direction) i);

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

	// calculating "centre" karbonite details must be done *after* calculating
	// - distToMyStartingLocs and
	// - distToEnemyStartingLocs
	// - lastKnownKarboniteAmount
	reachedCentre = false;
	shortestDistToEnemyStartLoc = MultisourceBfsUnreachableMax;
	for (int i = 0; i < (int)units.size(); i++) {
		if (units[i].get_team() == myTeam) {
			MapLocation loc = units[i].get_map_location();
			shortestDistToEnemyStartLoc = std::min(shortestDistToEnemyStartLoc, distToEnemyStartingLocs[loc.get_y()][loc.get_x()]);
		}
	}
	// centreSize is the maximum value of abs(distToEnemyStartingLocs - distToMyStartingLocs)
	// that a square can have to be considered "in the centre".
	centreSize = shortestDistToEnemyStartLoc / 3;
	std::vector<SimpleState> allCentreKarboniteSquares;
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		// ignore unreachable squares at first, because if you calculate
		// abs(distToEnemy - distToMy) on an unreachable square, you'll get 0 and you'll think it's in the centre
		if (distToMyStartingLocs[y][x] == MultisourceBfsUnreachableMax) {
			distToTheCentre[y][x] = MultisourceBfsUnreachableMax;
		} else {
			distToTheCentre[y][x] = std::abs(distToMyStartingLocs[y][x] - distToEnemyStartingLocs[y][x]);
		}
	}
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		// now for the unreachable squares, if they're next to a reachable square (e.g. they could be impassable but next
		//     to a reachable square)
		// then just set their distToTheCentre to be the minimum distToTheCentre from an adjacent reachable square + 1
		// MAKE SURE IT'S A *REACHABLE* ADJACENT SQUARE
		// otherwise you might set the wrong value for a whole connected component of unreachable squares
		// And why +1? Idk 4Head. Seemed like a good idea at the time.
		if (distToMyStartingLocs[y][x] == MultisourceBfsUnreachableMax) {
			int min_value_for_adj_reachable_square = MultisourceBfsUnreachableMax;
			for (int d = 0; d < 8; d++) {
				int ny = y + dy[d];
				int nx = x + dx[d];
				if (0 <= ny && ny < height && 0 <= nx && nx < width &&
						distToMyStartingLocs[ny][nx] != MultisourceBfsUnreachableMax) {
					min_value_for_adj_reachable_square = std::min(min_value_for_adj_reachable_square,
						distToTheCentre[ny][nx]);
				}
			}
			distToTheCentre[y][x] = min_value_for_adj_reachable_square + 1;
		}
	}
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		if (lastKnownKarboniteAmount[y][x] > 0) {
			if (distToTheCentre[y][x] <= centreSize) {
				isCentreSquare[y][x] = true;
				centreKarbonite += lastKnownKarboniteAmount[y][x];
				allCentreKarboniteSquares.push_back(SimpleState(y, x));
			}
		}
	}
	multisourceBfsAvoidingUnitsAndDanger(allCentreKarboniteSquares, distToCentreKarbonite);

	fo(i, 0, 8) randDirOrder[i] = i;

	all_pairs_shortest_path();

	is_very_early_game = true;

	int num_connected_components = 0;
	fo(y, 0, height) fo(x, 0, width) if (isPassable[y][x] && connectedComponent[y][x] == 0) {
		vector<SimpleState> myLoc;
		myLoc.push_back(SimpleState(y, x));
		num_connected_components++;
		do_flood_fill(myLoc, connectedComponent, isPassable, num_connected_components);
	}

	for (int y = 0; y < MarsMap.get_height(); y++) for (int x = 0; x < MarsMap.get_width(); x++) {
		MapLocation loc(Mars, x, y);
		if (MarsMap.is_passable_terrain_at(loc)) {
			int degree = 0;
			for (int i = 0; i < 8; i++) {
				MapLocation neighbour(loc.add(directions[i]));
				if (MarsMap.is_on_map(neighbour)) {
					if (MarsMap.is_passable_terrain_at(neighbour)) {
						degree++;
					}
				}
			}
			//goodRocketLandingLocations[degree].push_back(loc);
			goodRocketLandingLocations[8].push_back(loc);
		}
	}
	for (int i = 0;i <= 8;i++) {
		random_shuffle(goodRocketLandingLocations[i].begin(), goodRocketLandingLocations[i].end());
	}
}

int get_unit_order_priority (const Unit& unit) {
	switch(unit.get_unit_type()) {
		case Mage:
			// Run mages before others so that they get overcharge
			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();
				// give priority to units that are closer to enemies
				// NOTE: the default value for manhattanDistanceToNearestEnemy (ie the value when there are no nearby
				//   enemies) should be less than 998 so that priorities don't get mixed up!
				// NOTE: since we're giving priority to units that are closer to enemies, the frontline rangers should
				//   run before healers, so that healers can overcharge them. Idk hopefully it works out xd.
				return 999 + manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()];
			} else {
				return 1998;
			}

		// Actually not sure whether fighting units or workers should go first... so just use the same priority...
		// Edit: whatever, let fighting units go first
		case Ranger:
		case Knight:
		case Healer:
			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();
				// give priority to units that are closer to enemies
				// NOTE: the default value for manhattanDistanceToNearestEnemy (ie the value when there are no nearby
				//   enemies) should be less than 998 so that priorities don't get mixed up!
				// NOTE: since we're giving priority to units that are closer to enemies, the frontline rangers should
				//   run before healers, so that healers can overcharge them. Idk hopefully it works out xd.
				return 1999 + manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()];
			} else {
				return 2998;
			}

			// Worker before factory so that workers can finish a currently building factory before the factory runs
		case Worker:
			if (unit.get_location().is_on_map()) {
				MapLocation loc = unit.get_map_location();

				// If the round is early, give priority to the workers closest to the enemy starting locs
				// so that the worker that is currently replicating towards the center karbonite
				if (roundNum <= 80) {
					return 1999 + distToEnemyStartingLocs[loc.get_y()][loc.get_x()];
				} else {
					// give priority to units that are closer to enemies
					// NOTE: the default value for manhattanDistanceToNearestEnemy (ie the value when there are no nearby
					//   enemies) should be less than 998 so that priorities don't get mixed up!
					// NOTE: since we're giving priority to units that are closer to enemies, the frontline rangers should
					//   run before healers, so that healers can overcharge them. Idk hopefully it works out xd.
					return 1999 + manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()];
				}
			} else {
				return 2998;
			}

			// Factory before rocket so that factory can make a unit, then unit can get in rocket before the rocket runs
			// Edit: not actually sure if you can actually do that lol... whatever.
		case Factory:
			return 4999;
		case Rocket:
			return 5999;
	}
	DEBUG_OUTPUT("ERROR: getUnitOrderPriority() does not recognise this unit type!");
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

// default argument for force is false
// if force is true, the move will be forced to occur, even if caches are corrupted
static void doMoveRobot (Unit& unit, Direction dir, bool force) {
	if (gc.can_move(unit.get_id(), dir)) {
		MapLocation loc = unit.get_map_location();
		if (!hasFriendlyUnit[loc.get_y()][loc.get_x()]) {
			DEBUG_OUTPUT("Error: hasFriendlyUnit[][] is incorrect!\n");
			if (!force) {
				return;
			}
		}
		MapLocation next_loc = loc.add(dir);
		/*hasFriendlyUnit[loc.get_y()][loc.get_x()] = false;
		hasFriendlyUnit[next_loc.get_y()][next_loc.get_x()] = true;*/

		// update locations of healers with overcharge off cooldown
		if (unit.get_unit_type() == Healer && availableOverchargeId[loc.get_y()][loc.get_x()] != -1) {
			if (availableOverchargeId[loc.get_y()][loc.get_x()] != unit.get_id()) {
				DEBUG_OUTPUT("Error: availableOverchargeId[][] is incorrect! FIX THIS!!\n");
			}
			availableOverchargeId[loc.get_y()][loc.get_x()] = -1;
			availableOverchargeId[next_loc.get_y()][next_loc.get_x()] = unit.get_id();
		}
		
		gc.move_robot(unit.get_id(), dir);
	}
}

static void doBlinkRobot (Unit& unit, MapLocation where) {
	if (gc.can_begin_blink(unit.get_id(), where)) {
		MapLocation loc = unit.get_map_location();
		if (!hasFriendlyUnit[loc.get_y()][loc.get_x()]) {
			DEBUG_OUTPUT("Error: hasFriendlyUnit[][] is incorrect!");
			return;
		}
		hasFriendlyUnit[loc.get_y()][loc.get_x()] = false;
		hasFriendlyUnit[where.get_y()][where.get_x()] = true;
		gc.blink(unit.get_id(), where);
	}
}

static bool doOvercharge (const std::pair<int, int> &healer_loc, Unit &unit_to_overcharge) {
	int loc_y = healer_loc.first;
	int loc_x = healer_loc.second;
	int healer_id = availableOverchargeId[loc_y][loc_x];

	if (!gc.can_overcharge(healer_id, unit_to_overcharge.get_id())) {
		return false;
	}

	// rip can_overcharge doesn't exist...
	// I guess we'll just always return true and hope that it doesn't crash
	printf("healer at %d %d overcharging unit at %d %d\n", loc_x, loc_y, unit_to_overcharge.get_map_location().get_x(),
		unit_to_overcharge.get_map_location().get_y());
	gc.overcharge(healer_id, unit_to_overcharge.get_id());

	// remove available overcharge
	availableOverchargeId[loc_y][loc_x] = -1;

	return true;
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
		if (isCentreSquare[cur_loc.get_y()][cur_loc.get_x()]) {
			reachedCentre = true;
		}
	}

	bool doneMove = !gc.is_move_ready(unit.get_id());

	if (!doneMove && try_summon_move(unit)) {
		doneMove = true;
	}

	// movement
	if (!doneMove && false /* need to place factory and no adjacent good places and nearby good place */) {
		// move towards good place
	}
	// If it's early game and you're a currently replicating worker, prioritize karbonite over blueprints
	// Otherwise prioritise blueprints over karbonite
	// See declaration of is_very_early_game for how we define early game
	// Edit: added max round 50, because sometimes replicate would actually come off cooldown before the "very early game"
	// actually finished, and then your building bots would bug out. Example map: secretgarden
	// Warning: constant used (replicate cooldown in #rounds)
	if (is_very_early_game && roundNum < 50 && unit.get_ability_heat() < 20) {
		doKarboniteMovement(unit, doneMove);
		doBlueprintMovement(unit, doneMove);
	} else {
		doBlueprintMovement(unit, doneMove);
		doKarboniteMovement(unit, doneMove);
	}
	if (!doneMove /* next to damaged structure */) {
		// done, stay next to damaged structure

		// TODO: implement this
	}
	if (!doneMove /* damaged structure */) {
		// move towards damaged structure

		// TODO: implement this
	}

	if (!doneMove) {
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

	bool replicateForBuilding = (numFactories + numFactoryBlueprints > 0 && numWorkers < 4);
	bool replicateForHarvesting = distToKarbonite[loc.get_y()][loc.get_x()] < IsEnoughResourcesNearbySearchDist && isEnoughResourcesNearby(unit);
	bool replicateForCentreKarbonite = false;

	if (is_very_early_game && roundNum <= 50 && !reachedCentre) {
		// see doKarboniteMovement for more info on this if statement which checks whether it's worth it
		// to replicate towards centre karbonite
		if (centreKarboniteLeft > howMuchCentreKarbToBeWorthIt(distToCentreKarbonite[loc.get_y()][loc.get_x()])) {
			replicateForCentreKarbonite = true;
		}
	}

	bool canRocket = (gc.get_research_info().get_level(Rocket) >= 1);
	bool lowRockets = ((int) rockets_to_fill.size() + numRocketBlueprints < maxConcurrentRocketsReady);
	bool needToSave = (gc.get_karbonite() < 150 + 60);

	bool shouldReplicate = (replicateForBuilding || replicateForHarvesting || replicateForCentreKarbonite);

	if (canRocket && lowRockets && needToSave) {
		shouldReplicate = false;
	}

	if (!doneAction && unit.get_ability_heat() < 10 && shouldReplicate) {
		// try to replicate
		if (doReplicate(unit)) {
			doneAction = true;
		}
	}

	// Build before blueprinting so that we don't lay down a bunch of blueprints while we haven't even finished one factory
	if (doBuild(unit)) {
		// try to build adjacent structures
		doneAction = true;
	}

	// hard code not building factory on round 1 so that we can replicate
	if (roundNum > 1 && !doneAction) {
		// if you can blueprint factory/rocket and want to blueprint factory/rocket...
		if (gc.get_karbonite() >= 200 &&
				(numFactories + numFactoryBlueprints < 4 ||
				 gc.get_karbonite() >= 280 + (numFactories + numFactoryBlueprints - 4) * 40)) {

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

	if (!doneAction) {
		// if next to karbonite, mine
		int best = -1, best_karbonite_amount = -1;
		for (int i = 0; i < 9; i++) {
			// This gc call has to be in the outer if statement because we don't check for out of bounds locations in the
			// inner if statement. (This is, we rely on can_harvest() to do the oob check for us.)
			if (gc.can_harvest(unit.get_id(), directions[i])) {
				MapLocation loc = unit.get_map_location().add(directions[i]);
				if (best == -1 || lastKnownKarboniteAmount[loc.get_y()][loc.get_x()] > best_karbonite_amount) {
					best = i;
					best_karbonite_amount = lastKnownKarboniteAmount[loc.get_y()][loc.get_x()];
				}
			}
		}
		if (best != -1) {
			//printf("harvesting!\n");
			gc.harvest(unit.get_id(), directions[best]);
			MapLocation loc = unit.get_map_location().add(directions[best]);
			lastKnownKarboniteAmount[loc.get_y()][loc.get_x()] = gc.get_karbonite_at(loc);
			doneAction = true;
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

	bool doneMove = !gc.is_move_ready(unit.get_id());

	if (!doneMove) {
		// move towards karbonite
		MapLocation loc = unit.get_map_location();
		if (distToKarbonite[loc.get_y()][loc.get_x()] != MultisourceBfsUnreachableMax) {
			tryMoveToLoc(unit, distToKarbonite);
			doneMove = true;
		}
	}

	if (!doneMove) {
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
	bool shouldReplicate = (roundNum >= 751 || gc.get_karbonite() > 150 + 40*2 + 60);
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
			add_unit_to_all_my_units(gc.sense_unit_at_location(loc));
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

	// DEBUG_OUTPUT("I am a rocket and I think it isn't worth waiting: %d, full? %d\n", notWorthWaiting ? 1 : 0, fullRocket ? 1 : 0);

	if (dangerOfDestruction || aboutToFlood || (fullRocket && notWorthWaiting)) {
		MapLocation where = nullMapLocation;
		for (int i = 8; i > 0; i--) {
			if (goodRocketLandingLocations[i].size() > 0) {
				where = goodRocketLandingLocations[i].back();
				goodRocketLandingLocations[i].pop_back();
				break;
			}
		}
		if (where == nullMapLocation) {
			do {
				int landX = (int)(rand() % MarsMap.get_width());
				int landY = (int)(rand() % MarsMap.get_height());
				where = MapLocation(Mars, landX, landY);
			} while (MarsMap.is_passable_terrain_at(where) == 0);
		}

		gc.launch_rocket(unit.get_id(), where);
	}
}


static void runMarsRocket (Unit& unit) {
	tryToUnload(unit);
}

static void runFactory (Unit& unit) {
	// Don't check whether the factory is built because it might be built this turn and the unit object would not be updated
	// (i.e. if the factory is completed this turn then we might incorrectly skip this factory by checking structure_is_built()
	//   so just lazily don't check it and it's fine xd)

	UnitType unitTypeToBuild = Ranger;

	MapLocation loc = unit.get_map_location();

	// TODO: change proportion based on current research levels
	// TODO: maybe make knights past round 150, but maybe not. Who knows what the meta is now.
	// Don't check for knights past round 150 to save time or something
 	bool done_choice = false;
 	if (roundNum <= 150) {
 		// danger units = fighting units or factories
 		int enemyFactories, friendlyK, enemyK;
 		std::tie(enemyFactories, friendlyK, enemyK) = factoryGetNearbyStuff(unit);
 		if (friendlyK < enemyK || (enemyFactories > 0 && friendlyK <= enemyK)) {
 			unitTypeToBuild = Knight;
 			done_choice = true;
 		}
 	}
 	if (!done_choice) {
 		if (isSquareDangerous[loc.get_y()][loc.get_x()]) {
 			// dangerous square. Just build rangers, healers will just get instantly killed.
 			unitTypeToBuild = Ranger;
 		} else if (numWorkers == 0) {
 			unitTypeToBuild = Worker;
 		} else if (!has_overcharge_researched) {
			// early game priorities
			if (numRangers >= 2 * numHealers + 4) {
				unitTypeToBuild = Healer;
			}
		} else if (has_overcharge_researched) {
			// reduce rangers so that we can get healers and mages out there
			if (numRangers*2 < good_ranger_positions.size()*3) {
				unitTypeToBuild = Ranger;
			} else if (numHealers*2 > 3*numMages) {
				unitTypeToBuild = Mage;
			} else {
				unitTypeToBuild = Healer;
			}
		}
 	}

	bool canRocket = (gc.get_research_info().get_level(Rocket) >= 1);
	bool lowRockets = ((int) rockets_to_fill.size() + numRocketBlueprints < maxConcurrentRocketsReady);
	int unitCost = (unitTypeToBuild == Worker ? 50 : 40);
	bool needToSave = (gc.get_karbonite() < 150 + unitCost);

	if (canRocket && lowRockets && needToSave) {
		return;
	}

	if (gc.can_produce_robot(unit.get_id(), unitTypeToBuild)) {
		//DEBUG_OUTPUT("PRODUCING ROBOT!!!");
		gc.produce_robot(unit.get_id(), unitTypeToBuild);
		// make sure to immediately update unit counts
		add_type_to_friendly_unit_count(unitTypeToBuild);
	}

	tryToUnload(unit);
}

static void runRanger (Unit& unit) {
	// try to attack before and after moving
	clock_t before_attack = clock();
	bool doneAttack = rangerTryToAttack(unit);
	clock_t after_attack = clock();
	ranger_attack_stats.add(get_microseconds(before_attack, after_attack));

	// decide movement
	if (unit.is_on_map() && gc.is_move_ready(unit.get_id())) {
		bool doneMove = false;

		if (!doneMove && try_summon_move(unit)) {
			doneMove = true;
		}

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

				if (doneAttack || attackDistanceToEnemy[myY][myX] <= 36) {
					// just completed an attack or we're too close to the enemy
					// move backwards now to kite if you can

					// if the chosen square is attacked by less enemies, or if we're too close and the best square is further away
					// then move
					if (best != -1 &&
							(bestNumEnemies < numEnemiesThatCanAttackSquare[myY][myX] ||
							 (attackDistanceToEnemy[myY][myX] <= 36 && bestAttackDist > attackDistanceToEnemy[myY][myX]))) {
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
				if (!doneAttack && gc.is_attack_ready(unit.get_id()) && is_attack_round) {
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
							DEBUG_OUTPUT("ERROR: expected current position to be a good position but it wasn't...");
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
			//DEBUG_OUTPUT("I'm at %d %d and I'm moving to good location %d %d\n", unit.get_map_location().get_x(),
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

	// update unit location
	clock_t before_gc_get_unit = clock();
	unit = gc.get_unit(unit.get_id());
	clock_t after_gc_get_unit = clock();
	gc_get_unit_stats.add(get_microseconds(before_gc_get_unit, after_gc_get_unit));

	// try to attack before and after moving
	if (!doneAttack) {
		clock_t before_attack = clock();
		rangerTryToAttack(unit);
		clock_t after_attack = clock();
		ranger_attack_stats.add(get_microseconds(before_attack, after_attack));
		doneAttack = true;
	}
}

// note: we only call this function when overcharge+blink is unavailable
// this means that the mage would suicide bomb if possible
// on mars, the mage would just hang around
static void runMage (Unit& unit) {
	// try to attack before and after moving
	clock_t before_attack = clock();
	bool doneAttack = mageTryToAttack(unit);
	clock_t after_attack = clock();
	mage_attack_stats.add(get_microseconds(before_attack, after_attack));

	// decide movement
	if (unit.is_on_map() && gc.is_move_ready(unit.get_id())) {
		bool doneMove = false;

		if (!doneMove && try_summon_move(unit)) {
			doneMove = true;
		}

		int myY = unit.get_map_location().get_y();
		int myX = unit.get_map_location().get_x();

		// Warning: Constant being used. Change this constant
		if (!doneMove &&
			// we can blink so we can be far
			((attackDistanceToEnemy[myY][myX] <= MageReadyToBlinkRange && has_blink_researched) ||
			// do not want to be too far if we cant blink
			 attackDistanceToEnemy[myY][myX] <= TwoMovesFromMageAttackRange))
		{
			// if near enemies, do fighting movement

			// somehow decide whether to move forward or backwards
			if (attackDistanceToEnemy[myY][myX] <= MageAttackRange) {
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
						if (is_good_mage_position[after_loc.get_y()][after_loc.get_x()])
						{
							is_good_mage_position_taken[after_loc.get_y()][after_loc.get_x()] = true;
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
						if (is_good_mage_position[after_loc.get_y()][after_loc.get_x()])
						{
							is_good_mage_position_taken[after_loc.get_y()][after_loc.get_x()] = true;
						}
					}	
				}
			} else if (!doneAttack && gc.is_attack_ready(unit.get_id())) {
				// currently within a move and a blink of enemy
				// lets bomb them with a mage
				if (attackDistanceToEnemy[myY][myX] > OneMoveFromMageAttackRange && gc.is_blink_ready(unit.get_id())) {
					// we are too far away and need to blink
					// warning: constants
					MapLocation curLoc = unit.get_map_location();
					MapLocation newLoc = nullMapLocation;
					for (int y = std::max(0, curLoc.get_y() - 2); y <= std::min(height - 1, curLoc.get_y() + 2); y++) {
						for (int x = std::max(0, curLoc.get_x() - 2); x <= std::min(width - 1, curLoc.get_x() + 2); x++) {
							if (attackDistanceToEnemy[y][x] <= OneMoveFromMageAttackRange) {
								MapLocation alt(curLoc.get_planet(), x, y);
								if (gc.can_begin_blink(unit.get_id(), alt)) {
									newLoc = alt;
								}
							}
						}
					}

					if (newLoc != nullMapLocation) {

						doBlinkRobot(unit, newLoc);

						// update unit location
						clock_t before_gc_get_unit = clock();
						unit = gc.get_unit(unit.get_id());
						clock_t after_gc_get_unit = clock();
						gc_get_unit_stats.add(get_microseconds(before_gc_get_unit, after_gc_get_unit));
						myY = unit.get_map_location().get_y();
						myX = unit.get_map_location().get_x();

					}
				}
				if (attackDistanceToEnemy[myY][myX] > MageAttackRange) {
					// use overcharge to get closer
					std::vector<std::pair<int, int>> available_overcharge_list;
					get_available_overcharges_in_range(unit, available_overcharge_list);
					MapLocation resultantLocation(unit.get_map_location());
					vector<Direction> moveDirs;
					vector<size_t> whoIsOvercharging;
					for (size_t i = 0; i < available_overcharge_list.size(); i++) {
						if (attackDistanceToEnemy[resultantLocation.get_y()][resultantLocation.get_x()] <= MageAttackRange) {
							break;
						}
						shuffleDirOrder();
						int best = -1, bestDist = -1;
						for (int j = 0; j < 8; j++) {
							MapLocation newLocation(resultantLocation.add(directions[randDirOrder[j]]));
							if (EarthMap.is_on_map(newLocation)) {
								// check distance first to save on gc calls
								int alt = attackDistanceToEnemy[newLocation.get_y()][newLocation.get_x()];
								if (best == -1 || alt < bestDist) {
									// warning: constant used (healer overcharge radius)
									if (distance_squared(available_overcharge_list[i].first-newLocation.get_y(), available_overcharge_list[i].second-newLocation.get_x()) <= 30) {
										if (gc.can_sense_location(newLocation)) {
											if (gc.is_occupiable(newLocation)) {
												best = j;
												bestDist = alt;
											}
										}
									}
								}
							}
						}
						if (best == -1) {
							break;
						}
						moveDirs.push_back(directions[randDirOrder[best]]);
						whoIsOvercharging.push_back(i);
						resultantLocation = resultantLocation.add(directions[randDirOrder[best]]);
					}
					if (attackDistanceToEnemy[resultantLocation.get_y()][resultantLocation.get_x()] <= MageAttackRange) {
						// a path was found; lets do this
						for (size_t i = 0; i < moveDirs.size(); i++) {
							// the final argument forces the move to occur, whether or not caches were corrupted
							// multiple moves screw up caches badly so we must force
							doMoveRobot(unit, moveDirs[i], true);
							// update location
							unit = gc.get_unit(unit.get_id());
							myY = unit.get_map_location().get_y();
							myX = unit.get_map_location().get_x();
							mageTryToAttack(unit); // attack for free
							if (doOvercharge(available_overcharge_list[whoIsOvercharging[i]], unit)) {
							}
						}
					}
				}
			}
			if (attackDistanceToEnemy[myY][myX] <= MageAttackRange) {
				mageTryToBomb(unit);
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

			// check if current location is good position
			if (!doneMove && is_good_mage_position[myY][myX]) {
				doneMove = true;

				// if there's an closer (or equally close) adjacent good position
				bool foundMove = false;
				shuffleDirOrder();
				for (int i = 0; i < 8; i++) {
					Direction dir = directions[randDirOrder[i]];
					MapLocation loc = unit.get_map_location().add(dir);
					if (0 <= loc.get_y() && loc.get_y() < height &&
							0 <= loc.get_x() && loc.get_x() < width &&
							is_good_mage_position[loc.get_y()][loc.get_x()] &&
							manhattanDistanceToNearestEnemy[loc.get_y()][loc.get_x()] <= manhattanDistanceToNearestEnemy[myY][myX] &&
							gc.can_move(unit.get_id(), dir)) {
						//System.out.println("moving to other good location!!");
						foundMove = true;
						doMoveRobot(unit, dir);
						is_good_mage_position_taken[loc.get_y()][loc.get_x()] = true;
						break;
					}
				}

				if (!foundMove) {
					// otherwise, stay on your current position
					if (!is_good_mage_position_taken[myY][myX]) {
						is_good_mage_position_taken[myY][myX] = true;
					}
				}
			}

			// otherwise, try to move to good position
			pair<int,int> best_good = get_closest_good_position(myY, myX, is_good_mage_position_taken, good_mage_positions);
			//DEBUG_OUTPUT("I'm at %d %d and I'm moving to good location %d %d\n", unit.get_map_location().get_x(),
			//	unit.get_map_location().get_y(), best_good.second, best_good.first);
			if (!doneMove && best_good.first != -1) {
				int y = best_good.first;
				int x = best_good.second;
				is_good_mage_position_taken[y][x] = true;
				tryMoveToLoc(unit, dis[y][x]);
				doneMove = true;
			}

			// otherwise, if the mage can't find a free good position, mark it as idle
			if (!doneMove) {
				numIdleMages++;
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

	// update unit location
	clock_t before_gc_get_unit = clock();
	unit = gc.get_unit(unit.get_id());
	clock_t after_gc_get_unit = clock();
	gc_get_unit_stats.add(get_microseconds(before_gc_get_unit, after_gc_get_unit));

	// try to attack before and after moving
	if (!doneAttack) {
		clock_t before_attack = clock();
		mageTryToAttack(unit);
		clock_t after_attack = clock();
		mage_attack_stats.add(get_microseconds(before_attack, after_attack));
		doneAttack = true;
	}
}

static void runKnight (Unit& unit) {
	bool doneMove = false;

	if (!doneMove && try_summon_move(unit)) {
		doneMove = true;
	}

	if (unit.is_on_map() && gc.is_attack_ready(unit.get_id())) {
		vector<Unit> units = gc.sense_nearby_units(unit.get_map_location(), unit.get_attack_range());
		int whichToAttack = -1;
		int whichToAttackPriority = -1;
		int whichToAttackHealth = 9999;
		for (int i = 0; i < units.size(); i++) {
			Unit &other = units[i];
			if (other.get_team() != unit.get_team() && gc.can_attack(unit.get_id(), other.get_id())) {
				int health = (int)other.get_health();
				int attackPriority = (int) getKnightAttackPriority(other);

				if (whichToAttack == -1 || (attackPriority>whichToAttackPriority) || (attackPriority==whichToAttackPriority && health < whichToAttackHealth)) {
					whichToAttack = i;
					whichToAttackPriority = attackPriority;
					whichToAttackHealth = health;
				}
			}
		}
		if (whichToAttack != -1) {
			gc.attack(unit.get_id(), units[whichToAttack].get_id());
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
			if (knight_bfs_to_enemy_unit(unit)) {
 				doneMove = true;
 			}
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
	tryToHeal(unit);
	bool doneMove = false;

	if (!doneMove && try_summon_move(unit)) {
		doneMove = true;
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

	// update unit location and cooldowns
	unit = gc.get_unit(unit.get_id());

	tryToHeal(unit);
}

static bool tryToMoveToRocket (Unit& unit ) {
	// try to get to a fucking rocket as soon as possible

	if (rockets_to_fill.empty()) {
		return false;
	}

	int fromY = unit.get_map_location().get_y();
	int fromX = unit.get_map_location().get_x();

	int minDist = -1;
	int bestToY = -1;
	int bestToX = -1;

	for (Unit u : rockets_to_fill) {
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
static int dirToAdjacentBuildingBlueprint(MapLocation loc) {
	for (int i = 0; i < 8; i++) {
		MapLocation other = loc.add(directions[i]);
		if (gc.has_unit_at_location(other)) {
			Unit unit = gc.sense_unit_at_location(other);
			if (unit.get_team() == gc.get_team() &&
					(unit.get_unit_type() == Factory || unit.get_unit_type() == Rocket)
					&& unit.structure_is_built() == 0) {
				return i;
			}
		}
	}
	return -1;
}

static void moveButStayNextToLoc(Unit &unit, const MapLocation &loc) {
	shuffleDirOrder();
	for (int i = 0; i < 8; i++) {
		Direction dir = directions[randDirOrder[i]];
		MapLocation next = unit.get_map_location().add(dir);
		if (gc.can_move(unit.get_id(), dir) && distance_squared(next.get_y() - loc.get_y(), next.get_x() - loc.get_x()) <= 2) {
			doMoveRobot(unit, dir, false);
			break;
		}
	}
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

static void get_available_overcharges_in_range(Unit& unit, std::vector<std::pair<int, int>> &result_vector) {
	MapLocation loc = unit.get_map_location();
	int loc_y = loc.get_y();
	int loc_x = loc.get_x();

	// Warning: constant being used (healer overcharge range)
	for (int y = std::max(0, loc_y - 5); y <= std::min(height - 1, loc_y + 5); y++) {
		for (int x = std::max(0, loc_x - 5); x <= std::min(width - 1, loc_x + 5); x++) {
			if (distance_squared(loc_y - y, loc_x - x) <= 30 && availableOverchargeId[y][x] != -1) {
				result_vector.emplace_back(y, x);
			}
		}
	}
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

// returns the priority of the unit for a ranger to attack
static int getRangerAttackPriority(const Unit& unit){
	if (unit.get_unit_type()==Rocket || unit.get_unit_type()==Factory){
		return 0;
	}
	if (unit.get_unit_type()==Worker){
		return 1;
	}
	if (unit.get_unit_type()==Mage){
		return 3;
	}
	return 2;
}

// returns the priority of the unit for a mage to attack
static int getMageAttackPriority(Unit& unit){
	if (unit.get_unit_type()==Rocket || unit.get_unit_type()==Factory){
		return 0;
	}
	if (unit.get_unit_type()==Worker){
		return 1;
	}
	if (unit.get_unit_type()==Ranger){
		return 3;
	}
	return 2;
}

// returns the priority of the unit for a knight to attack
static int getKnightAttackPriority(Unit& unit){
	if (unit.get_unit_type()==Rocket || unit.get_unit_type()==Factory){
		return 0;
	}
	if (unit.get_unit_type()==Worker){
		return 1;
	}
	if (unit.get_unit_type()==Knight){
		return 3;
	}
	return 2;
}

// returns whether the unit attacked
// Requriements: Make sure the Unit object is up to date!
// I.e. you must call gc.get_unit() again if the ranger moves!
static bool rangerTryToAttack(Unit& unit) {
	if (unit.is_on_map() && gc.is_attack_ready(unit.get_id())) {
		// this radius needs to be at least 1 square larger than the ranger's attack range
		// because the ranger might move, but the ranger's unit.get_map_location() won't update unless we
		// call again. So just query a larger area around the ranger's starting location.
		MapLocation loc = unit.get_map_location();
		clock_t before_sense = clock();
		//vector<Unit> units = gc.sense_nearby_units(unit.get_map_location(), 50);
		vector<Unit> units;
		// Warning: Constants being used. Need to change constants if the game spec changes
		for (int y = std::max(0, loc.get_y() - 7); y <= std::min(height - 1, loc.get_y() + 7); y++) {
			for (int x = std::max(0, loc.get_x() - 7); x <= std::min(width - 1, loc.get_x() + 7); x++) {
				int dist = distance_squared(y - loc.get_y(), x - loc.get_x());
				// Warning: More constants being used (minimum and maximum ranger attack range)
				if (10 < dist && dist <= 50) {
					if (hasEnemyUnit[y][x] && gc.can_sense_unit(enemyUnitId[y][x])) {
						units.push_back(gc.get_unit(enemyUnitId[y][x]));
					}
				}
			}
		}
		clock_t after_sense = clock();
		ranger_attack_sense_stats.add(get_microseconds(before_sense, after_sense));

		// units to attack. Stored as tuple of
		// -- negative ranger attack priority (negative for sorting purposes)
		// -- health
		// -- index in units[]
		vector<std::tuple<int, int, int>> to_attack;

		for (int i = 0; i < units.size(); i++) {
			const Unit &other = units[i];
			if (other.get_team() != unit.get_team() && gc.can_attack(unit.get_id(), other.get_id())) {
				int health = (int)other.get_health();
				int attackPriority = (int) getRangerAttackPriority(other);

				to_attack.emplace_back(-attackPriority, health, i);
			}
		}

		// sort highest priority units to the front
		std::sort(to_attack.begin(), to_attack.end());

		// do your first attack which is for free.
		int to_attack_index = 0;
		bool done_attack = false;
		if (!to_attack.empty()) {
			int enemy_index = std::get<2>(to_attack[0]);
			gc.attack(unit.get_id(), units[enemy_index].get_id());
			if (gc.can_sense_unit(units[enemy_index].get_id())) {
				// enemy still alive, update unit health
				units[enemy_index] = gc.get_unit(units[enemy_index].get_id());
			} else {
				// enemy killed
				to_attack_index = 1;
			}
			done_attack = true;
		}

		// get all healers that have overcharge available that are in range
		std::vector<std::pair<int, int>> available_overcharge_list;
		get_available_overcharges_in_range(unit, available_overcharge_list);

		// now go through all the enemies and if you can one shot them with overcharge, do it
		bool overcharge_done = false;
		int current_overcharge_index = 0;
		int total_overcharges = int(available_overcharge_list.size());
		for ( ; to_attack_index < int(to_attack.size()); to_attack_index++) {
			const auto &tuple = to_attack[to_attack_index];
			// check if we're out of overcharge
			if (current_overcharge_index >= total_overcharges) {
				break;
			}

			int enemy_index = std::get<2>(tuple);

			// skip knights, they're not worth wasting overcharge on.
			// NOTE: IF YOU DECIDE TO CHANGE THIS, THEN YOU MUST TAKE INTO ACCOUNT THE ENEMY
			// KNIGHT UPGRADE LEVEL !!!!
			// BECAUSE THIS WILL CHANGE HOW MANY ATTACKS IT TAKES TO ONE SHOT THEM!!!
			// DON'T IGNORE THIS!!! ^^
			if (units[enemy_index].get_unit_type() == Knight) {
				continue;
			}

			// Warning: Constant being used (ranger attack damage)
			int attacks_needed = (units[enemy_index].get_health() + 29) / 30;
			if (attacks_needed <= total_overcharges - current_overcharge_index) {
				for (int i = 0; i < attacks_needed; i++) {
					if (doOvercharge(available_overcharge_list[current_overcharge_index], unit)) {
						overcharge_done = true;
					} else {
						DEBUG_OUTPUT("Error: Bot thought that it could overcharge, but it couldn't!\n");
						// overcharge failed, can't attack, just break rip
						break;
					}
					current_overcharge_index++;

					if (gc.can_attack(unit.get_id(), units[enemy_index].get_id())) {
						gc.attack(unit.get_id(), units[enemy_index].get_id());
					} else {
						DEBUG_OUTPUT("Error: Ranger being overcharged thinks it can attack unit, but it can't!\n");
						// Note: If this happens, it may be because the enemy unit object in units[enemy_index] was
						// out of date before reaching this for loop. Make sure it's updated whenever it's attacked
						// before reaching this for loop.

						// rip, we somehow miscalculated the required number of attacks
						// break so we don't waste any more overcharge on attacking this unit
						break;
					}
				}

				// check that we actually killed it
				if (gc.can_sense_unit(units[enemy_index].get_id())) {
					DEBUG_OUTPUT("Error: Ranger thought it one shot a unit with a bunch of overcharge, but it didnt!\n");
				} else {
					printf("Yay, one shot worked on unit at %d %d!\n", units[enemy_index].get_map_location().get_x(),
						units[enemy_index].get_map_location().get_y());
				}
			}
		}

		if (overcharge_done) {
			// if overcharge was done, put unit back into queue so they can do movement again
			add_unit_to_all_my_units(gc.get_unit(unit.get_id()));
		}

		return done_attack;
	} else {
		return false;
	}
}

// returns whether the unit attacked
// Requriements: Make sure the Unit object is up to date!
// I.e. you must call gc.get_unit() again if the mage moves!
static bool mageTryToAttack(Unit& unit) {
	if (unit.is_on_map() && gc.is_attack_ready(unit.get_id())) {
		MapLocation loc = unit.get_map_location();
		clock_t before_sense = clock();
		vector<Unit> units;
		// Warning: Constants being used. Need to change constants if the game spec changes
		for (int y = std::max(0, loc.get_y() - 5); y <= std::min(height - 1, loc.get_y() + 5); y++) {
			for (int x = std::max(0, loc.get_x() - 5); x <= std::min(width - 1, loc.get_x() + 5); x++) {
				int dist = distance_squared(y - loc.get_y(), x - loc.get_x());
				// Warning: More constants being used (minimum and maximum ranger attack range)
				if (dist <= MageAttackRange) {
					if (hasEnemyUnit[y][x]) {
						if (gc.can_sense_unit(enemyUnitId[y][x])) {
							units.push_back(gc.get_unit(enemyUnitId[y][x]));
							enemyUnitHealth[y][x] = units.back().get_health();
						} else {
							// that unit is dead for goodness sake
							// why would you attack a dead unit??
							hasEnemyUnit[y][x] = false;
						}
					}
				}
			}
		}
		clock_t after_sense = clock();
		mage_attack_sense_stats.add(get_microseconds(before_sense, after_sense));
		// unlike rangers, which prioritise low-health units, mages will prioritise shots that provide maximum kills
		int whichToAttack = -1, whichToAttackKills = -1, whichToAttackPriority = -1;
		for (int i = 0; i < units.size(); i++) {
			Unit other = units[i];
			if (other.get_team() != unit.get_team() && gc.can_attack(unit.get_id(), other.get_id())) {
				int kills = 0;
				int attackPriority = 0;
				// sum over area of effect
				MapLocation theirLoc(unit.get_map_location());
				for (int y = std::max(0, theirLoc.get_y() - 1); y <= std::min(height - 1, theirLoc.get_y() + 1); y++) {
					for (int x = std::max(0, theirLoc.get_x() - 1); x <= std::min(width - 1, theirLoc.get_x() + 1); x++) {
						if (hasEnemyUnit[y][x]) {
							if (enemyUnitHealth[y][x] <= unit.get_damage()) {
								kills++;
							}
							attackPriority += mageAttackPriorities[y][x];
						}
						if (y == loc.get_y() && x == loc.get_x()) {
							// dont shoot yourself
							kills = -420420;
							attackPriority = -420420;
						}
					}
				}

				if (whichToAttack == -1 || (kills>whichToAttackKills) || (kills==whichToAttackKills && attackPriority > whichToAttackPriority)) {
					whichToAttack = i;
					whichToAttackKills = kills;
					whichToAttackPriority = attackPriority;
				}
			}
		}
		if (whichToAttack != -1) {
			gc.attack(unit.get_id(), units[whichToAttack].get_id());
			vector<pair<int, int>> available_overcharge_list;
			get_available_overcharges_in_range(unit, available_overcharge_list);
			MapLocation theirLoc(units[whichToAttack].get_map_location());
			while (!available_overcharge_list.empty() && gc.can_sense_unit(units[whichToAttack].get_id())) {
				// is it worth requesting overcharge to go again?
				bool worthAnOvercharge = false;
				int hits = 0;
				for (int y = std::max(0, theirLoc.get_y() - 1); y <= std::min(height - 1, theirLoc.get_y() + 1); y++) {
					for (int x = std::max(0, theirLoc.get_x() - 1); x <= std::min(width - 1, theirLoc.get_x() + 1); x++) {
						// take into account the damage just dealt
						if (hasEnemyUnit[y][x]) {
							enemyUnitHealth[y][x] -= unit.get_damage();
							if (enemyUnitHealth[y][x] <= 0) {
								hasEnemyUnit[y][x] = false;
							}
						}
						if (hasEnemyUnit[y][x]) {
							hits++;
						}
					}
				}
				if (hits >= 3) {
					worthAnOvercharge = true;
				}
				if (worthAnOvercharge) {
					doOvercharge(available_overcharge_list.back(), unit);
					available_overcharge_list.pop_back();
					gc.attack(unit.get_id(), units[whichToAttack].get_id());
				} else {
					break;
				}
			}
			return true;
		}
	}
	return false;
}

// calculates the value of a mage-bomb coming here
// Requirement: the hasEnemyUnit cache is up to date
int calcNumMageHitsFrom(int y, int x) {
	int result = 0;
	// warning: constants
	for (int hy = std::max(0, y - 5); hy <= std::min(height - 1, y + 5); hy++) {
		for (int hx = std::max(0, x - 5); hx <= std::min(width - 1, x + 5); hx++) {
			if (hasEnemyUnit[y][x]) { // try hitting here
				int dist = distance_squared(hy - y, hx - x);
				if (dist <= MageAttackRange) {
					int alt = 0;
					for (int sy = max(0, hy - 1); sy <= min(height - 1, hy + 1); sy++) {
						for (int sx = max(0, hx - 1); sx <= min(height - 1, hx + 1); sx++) {
							if (hasEnemyUnit[sy][sx]) { // splash
								alt++;
							}
						}
					}
					result = max(result, alt);
				}
			}
		}
	}
	return result;
}

// does a mage bomb
// it attacks, finds somewhere to overcharge to, and tries to attack again. repeat.
// Requirement: the unit object is up to date
// Requirement: the unit has just been overcharged and thus has zero heat
void mageTryToBomb(Unit &unit) {
	mageTryToAttack(unit);
	int absDist = 1;
	if (gc.is_blink_ready(unit.get_id())) {
		absDist = 2;
	}
	vector<pair<int, int> > available_overcharge_list;
	get_available_overcharges_in_range(unit, available_overcharge_list);
	if (available_overcharge_list.size() == 0) {
		return;
	}
	MapLocation loc(unit.get_map_location());
	MapLocation best(nullMapLocation);
	int bestValue = -1;
	for (int y = std::max(0, loc.get_y() - absDist); y <= std::min(height - 1, loc.get_y() + absDist); y++) {
		for (int x = std::max(0, loc.get_x() - absDist); x <= std::min(width - 1, loc.get_x() + absDist); x++) {
			int value = calcNumMageHitsFrom(y, x);
			if (value < bestValue) {
				best = MapLocation(myPlanet, x, y);
				bestValue = value;
			}
		}
	}
	if (bestValue >= 4) {
		if (absDist == 1) {
			doMoveRobot(unit, loc.direction_to(best));
		} else {
			doBlinkRobot(unit, best);
		}
		unit = gc.get_unit(unit.get_id());
		doOvercharge(available_overcharge_list.back(), unit);
		mageTryToBomb(unit);
	}
}

// returns whether the healer successfully healed
// Requirements: The Unit object must have an up-to-date location and cooldowns.
// Otherwise the healer won't heal even when it is able to
// And the healer might incorrectly try to heal/overcharge, or waste time limit trying to when it can't
static void tryToHeal(Unit& unit) {
	bool is_heal_ready = gc.is_heal_ready(unit.get_id());
	// Only overcharges if the current round is an attack round
	if (unit.is_on_map() && is_heal_ready) {
		// Warning: Constant being used (healer heal range)
		vector<Unit> units = gc.sense_nearby_units_by_team(unit.get_map_location(), 30, gc.get_team());
		int whichToHeal = -1, whichToHealHealthMissing = -1;
		for (int i = 0; i < units.size(); i++) {
			const Unit &other = units[i];
			// heal the unit with most health missing
			int healthMissing = (int)(other.get_max_health() - other.get_health());
			if (is_heal_ready && (whichToHeal == -1 || healthMissing > whichToHealHealthMissing) && gc.can_heal(unit.get_id(), other.get_id())) {
				whichToHeal = i;
				whichToHealHealthMissing = healthMissing;
			}
		}
		if (whichToHeal != -1 && whichToHealHealthMissing > 0) {
			//System.out.println("Healer says: 'I'm being useful!'");
			gc.heal(unit.get_id(), units[whichToHeal].get_id());
		}
	}
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
		if (loadableUnits[i].get_unit_type() == Mage) {
			// almost useless on mars since no ranger front line
			continue;
		}
		if (gc.can_load(unit.get_id(), loadableUnits[i].get_id())) {

			if (loadableUnits[i].get_unit_type() == Worker && numWorkers <= 2) 
				continue;

			remove_type_from_friendly_unit_count(loadableUnits[i].get_unit_type());
			gc.load(unit.get_id(), loadableUnits[i].get_id());
		}
	}
}

template<typename T>
static void do_flood_fill (vector<SimpleState>& startingLocs, T resultArr[55][55], bool passableArr[55][55], T label) {

	queue<SimpleState> q;
	for (SimpleState loc: startingLocs){
		resultArr[loc.y][loc.x] = label;
		q.push(loc);
	}

	while (!q.empty()){
		SimpleState cur = q.front(); q.pop();

		fo(d, 0, 8) {
			int ny = cur.y + dy[d];
			int nx = cur.x + dx[d];
			if (0 <= ny && ny < height && 0 <= nx && nx < width && passableArr[ny][nx] && !resultArr[ny][nx]) {
				resultArr[ny][nx] = label;

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

	fo(y, 0, height) fo(x, 0, width) {
		can_reach_from_spawn[y][x] = false;
	}
	do_flood_fill(spawn_locs, can_reach_from_spawn, isPassable, true);

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

// Copied from multisourceBfsAvoidingUnitsAndDanger,
// except that we pass through everything *including* passing through impassable terrain
static void multisourceBfsAvoidingNothing (vector<SimpleState>& startingLocs, int resultArr[55][55]) {
	for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
		resultArr[y][x] = MultisourceBfsUnreachableMax;
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
			if (0 <= ny && ny < height && 0 <= nx && nx < width && resultArr[ny][nx] == MultisourceBfsUnreachableMax) {
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
	if (unit.get_ability_heat() >= 10 || gc.get_karbonite() < 60) {
		// on cooldown or not enough karbonite
		return false;
	}

	Direction replicateDir = Center;
	MapLocation cur_loc = unit.get_map_location();

	// if it's very early game and you're the currently replicating worker,
	// consider replicating towards the karbonite in the centre of the map
	if (replicateDir == Center && is_very_early_game && roundNum <= 50 && !reachedCentre) {

		// see doKarboniteMovement for more info on this if statement which checks whether it's worth it
		// to replicate towards centre karbonite
		if (centreKarboniteLeft > howMuchCentreKarbToBeWorthIt(distToCentreKarbonite[cur_loc.get_y()][cur_loc.get_x()])) {
			// let's do it

			// take our chunk from centreKarboniteLeft, because it's now less worth it for other workers to also
			// replicate towards the centre
			centreKarboniteLeft -= howMuchCentreKarbToBeWorthIt(distToCentreKarbonite[cur_loc.get_y()][cur_loc.get_x()]);
			// also permanently decrease the centreKarbonite to account for spending money on this replicate
			centreKarbonite -= 60;

			// now let's do the actual replication
			shuffleDirOrder();
			int best = -1, bestDist = MultisourceBfsUnreachableMax;
			for (int i = 0; i < 8; i++) {
				Direction dir = directions[randDirOrder[i]];
				MapLocation next_loc = cur_loc.add(dir);
				if (gc.can_replicate(unit.get_id(), dir)) {
					int dist = distToCentreKarbonite[next_loc.get_y()][next_loc.get_x()];
					if (best == -1 || dist < bestDist) {
						best = randDirOrder[i];
						bestDist = dist;
					}
				}
			}

			if (best != -1) {
				replicateDir = directions[best];
			}
		}
	}

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
					dirToAdjacentBuildingBlueprint(unit.get_map_location().add(dir)) != -1) {
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
		add_unit_to_all_my_units(gc.sense_unit_at_location(loc));
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
	int bestIndex = -1, bestHealthLeft = 99999;
	fo(i, 0, SZ(units)) {
		Unit& other = units[i];

		// only look at factories and rockets
		if (other.get_unit_type() != Factory && other.get_unit_type() != Rocket) {
			continue;
		}

		int healthLeft = other.get_max_health() - other.get_health();
		if (bestIndex == -1 || healthLeft < bestHealthLeft) {
			if (gc.can_build(unit.get_id(), other.get_id())) {
				bestIndex = i;
				bestHealthLeft = healthLeft;
			}
		}
	}
	if (bestIndex != -1) {
		Unit &other = units[bestIndex];

		gc.build(unit.get_id(), other.get_id());

		// If it's complete, remove it from the factoriesBeingBuilt list
		// need to re-sense the unit to get the updated structure_is_built() value

		// Don't need to add new object to allMyUnits, because we can just use the existing object
		// (even though it will be out of date now that the factory is complete, it'll still work if we just
		// don't check whether structure_is_built() in runFactory())
		if (gc.sense_unit_at_location(other.get_map_location()).structure_is_built() != 0) {
			//DEBUG_OUTPUT("Finished a factory!");
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
						rockets_to_fill.push_back(other);
						rocketsBeingBuilt.erase(rocketsBeingBuilt.begin()+j);
						foundIt = true;
						break;
					}
				}
			}
			if (!foundIt) {
				DEBUG_OUTPUT("ERROR: Structure was expected in ___BeingBuilt, but was missing!");
			}
		}
		return true;
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
				if (hasFriendlyWorker[y][x]) {
					nearbyWorkers++;
				}
			}
		}
	}

	bool worthReplicating = false;
	if (myPlanet == Earth) {
		//System.out.println("worker at " + loc.toString() + " considering replicating with " + nearbyKarbonite + " karbonite nearby and " + nearbyWorkers + " nearby workers");
		worthReplicating = nearbyKarbonite > 50 * (nearbyWorkers + 1);

		// if still setting up, we can afford more leniency
		if (numFactories + numFactoryBlueprints < 4 && nearbyKarbonite > 40 * (nearbyWorkers + 1) && nearbyWorkers < 5) {
			worthReplicating = true;
		}

	} else {
		worthReplicating = nearbyKarbonite > 62 * (nearbyWorkers + 1);
	}

	return worthReplicating;
}

static std::tuple<int,int,int> factoryGetNearbyStuff(Unit& unit) {
	if (!unit.is_on_map()) {
		return make_tuple(0, 0, 0);
	}

	static const int search_dist = 5;

	MapLocation loc = unit.get_map_location();
	int locY = loc.get_y(), locX = loc.get_x();

	int nearbyEnemyFactories = 0;
	int nearbyFriendlyKnights = 0;
	int nearbyEnemyKnights = 0;

	for (int y = max(locY - search_dist, 0); y <= min(locY + search_dist, height - 1); y++) {
		for (int x = max(locX - search_dist, 0); x <= min(locX + search_dist, width - 1); x++) {
			if (dis[locY][locX][y][x] <= search_dist) {
				if (hasEnemyUnit[y][x] && gc.can_sense_unit(enemyUnitId[y][x])) {
					Unit other = gc.get_unit(enemyUnitId[y][x]);
					if (other.get_team() != myTeam) {
						if (other.get_unit_type() == Knight) {
							nearbyEnemyKnights++;
						}
						if (is_fighter_unit_type(other.get_unit_type()) || other.get_unit_type() == Factory) {
							nearbyEnemyFactories++;
						}
					}
				}
				nearbyFriendlyKnights += friendlyKnightCount[y][x];
			}
		}
	}

	return make_tuple(nearbyEnemyFactories, nearbyFriendlyKnights, nearbyEnemyKnights);
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
		DEBUG_OUTPUT("ERROR: Impossible situation in blueprint_wont_create_blockage()");
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

static bool knight_bfs_to_enemy_unit(Unit &unit) {
	// run towards enemy like an idiot
	static const int search_dist = 7;

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

	while (!q.empty()) {
		int cur_y, cur_x, cur_dist, cur_starting_dir;
		std::tie(cur_y, cur_x, cur_dist, cur_starting_dir) = q.front();
		q.pop();

		if (hasEnemyUnit[cur_y][cur_x]) {
			if (cur_starting_dir == DirCenterIndex) {
				printf("Error in knight bfs!");
			} else {
				doMoveRobot(unit, directions[cur_starting_dir]);
			}
			return true;
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

	return false;
}

static void doBlueprintMovement(Unit &unit, bool &doneMove) {
	if (!doneMove) {
		int blueprint_dir = dirToAdjacentBuildingBlueprint(unit.get_map_location());
		if (blueprint_dir != -1) {
			// if next to blueprint, stay next to blueprint
			moveButStayNextToLoc(unit, unit.get_map_location().add(directions[blueprint_dir]));
			doneMove = true;
		}
	}
	if (!doneMove) {
		// if very near a blueprint, move towards it

		if (bfsTowardsBlueprint(unit)) {
			doneMove = true;
		}
	}
}

static void doKarboniteMovement(Unit &unit, bool &doneMove) {
	if (!doneMove) {
		//  move towards karbonite
		MapLocation loc = unit.get_map_location();

		// if it's the very early game and you're the replicating worker, consider moving towards the centre karbonite
		if (!doneMove && is_very_early_game && roundNum < 50 && !reachedCentre && unit.get_ability_heat() < 20) {
			// you need to replicate dist / 2 times to race to the centre karbonite
			// because each turn you make 1 move and 1 replicate.
			// Warning: constant used (replicate cost)
			// We'll say it's worth it if the centreKarboniteLeft is greater than the cost of replicating all those workers
			if (centreKarboniteLeft > howMuchCentreKarbToBeWorthIt(distToCentreKarbonite[loc.get_y()][loc.get_x()])) {
				// don't modify the centreKarboniteLeft yet
				// we'll do that in doReplicate
				tryMoveToLoc(unit, distToCentreKarbonite);
				doneMove = true;
			}
		}

		// try to move towards nearby karbonite squares that are nearer the enemy base and further from your own base
		if (!doneMove) {
			int dir_index = shortRangeBfsToBestKarbonite(unit);
			if (dir_index != DirCenterIndex) {
				Direction dir = directions[dir_index];
				doMoveRobot(unit, dir);
				doneMove = true;
			}
		}

		// otherwise move to the closest karbonite
		if (!doneMove) {
			if (distToKarbonite[loc.get_y()][loc.get_x()] != MultisourceBfsUnreachableMax) {
				tryMoveToLoc(unit, distToKarbonite);
				doneMove = true;
			}
		}
	}
}

static void computeBattalionSizes() {
	// reset relevant data
	for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end(); it++) {
		battalionSizes[*it] = 0;
	}
	// check id of each unit
	for (map<int, int>::iterator it = battalionId.begin(); it != battalionId.end(); it++) {
		battalionSizes[it->second]++;
	}
}

static void runBattalions(vector<Unit> &myUnits) {
	// check if battalion leaders are alive
	for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end();) {
		if (!gc.can_sense_unit(*it)) {
			it = battalionLeaders.erase(it);
		} else {
			it++;
		}
	}
	// remove entries corresponding to dead robots
	for (map<int, int>::iterator it = battalionId.begin(); it != battalionId.end();) {
		if (!gc.can_sense_unit(it->first)) {
			it = battalionId.erase(it);
		} else {
			it++;
		}
	}
	// remove battalions that are too small
	computeBattalionSizes();
	// TODO experiment with the minimum size
	const int minBattalionSize = 5;
	for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end();) {
		if (battalionSizes[*it] < minBattalionSize) {
			it = battalionLeaders.erase(it);
		} else {
			it++;
		}
	}
	// determine free-agents: units that arent in a battalion
	vector<Unit> freeAgents;
	int numFighters = 0;
	for (size_t i = 0; i < myUnits.size(); i++) {
		if (myUnits[i].is_on_map()) {
			if (is_fighter_unit_type(myUnits[i].get_unit_type())) {
				int unitId = myUnits[i].get_id();
				map<int, int>::iterator it = battalionId.lower_bound(unitId);
				if (it == battalionId.end() || it->first != unitId) {
					// this unit is unassigned
					freeAgents.push_back(myUnits[i]);
				} else if (battalionLeaders.find(it->second) == battalionLeaders.end()) {
					// this battalion was dissolved
					freeAgents.push_back(myUnits[i]);
				}
				numFighters++;
			}
		}
	}
	// assign free-agents to battalions
	computeBattalionSizes();
	for (size_t i = 0; i < freeAgents.size(); i++) {
		int bestBattalionId = -1, bestBattalionSize = 100000;
		for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end(); it++) {
			// condition: must be in the same connected component
			// otherwise the battalion would be separated.. forever *cri*
			// given a choice, take the one with the smallest size to give it more power
			if (whichConnectedComponent[*it] == whichConnectedComponent[freeAgents[i].get_id()] && battalionSizes[*it] < bestBattalionSize) {
				bestBattalionId = *it;
				bestBattalionSize = battalionSizes[*it];
			}
		}
		int unitId = freeAgents[i].get_id();
		if (bestBattalionId == -1) {
			// gg, got no option but to create a new one
			battalionLeaders.insert(unitId);
			battalionId[unitId] = unitId;
			battalionSizes[unitId] = 1;
			battalionDestinations[unitId] = getRandomMapLocation(Mars, freeAgents[i].get_map_location());
		} else {
			// assign to that one
			battalionId[unitId] = bestBattalionId;
			battalionSizes[bestBattalionId]++;
		}
	}
	// split battalions that are too big if we have too few
	// TODO experiment with this value
	const int optimalBattalionQty = max(1, min(6, numFighters/8-1));
	if (!battalionLeaders.empty()) {
		while (battalionLeaders.size() < optimalBattalionQty) {
			// take the largest one and split it in halves
			int which = *battalionLeaders.begin();
			for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end(); it++) {
				if (battalionSizes[*it] > battalionSizes[which]) {
					which = *it;
				}
			}
			// get a list of the members of this battalion
			vector<int> members;
			for (map<int, int>::iterator it = battalionId.begin(); it != battalionId.end(); it++) {
				if (it->second == which) {
					members.push_back(it->first);
				}
			}
			// calculate sizes of split portions
			int portion1 = members.size() / 2;
			int portion2 = members.size() - portion1;
			// update stats
			battalionLeaders.erase(which);
			battalionLeaders.insert(members[0]);
			battalionSizes[members[0]] = portion1;
			// TODO remove this gc call TODO TODO TODO TODO TODO
			battalionDestinations[members[0]] = getRandomMapLocation(Mars, gc.get_unit(members[0]).get_map_location());
			battalionLeaders.insert(members[portion1]);
			battalionSizes[members[portion1]] = portion2;
			// TODO remove this gc call TODO TODO TODO TODO TODO
			battalionDestinations[members[portion1]] = getRandomMapLocation(Mars, gc.get_unit(members[portion1]).get_map_location());
			// assign
			for (int i = 0; i < portion1; i++) {
				battalionId[members[i]] = members[0];
			}
			for (int i = portion1; i < portion1+portion2; i++) {
				battalionId[members[i]] = members[portion1];
			}
		}
	}
	// move the battalions around
	for (set<int>::iterator it = battalionLeaders.begin(); it != battalionLeaders.end(); it++) {
		int battalionLeader = *it;
		// TODO: remove this gc call TODO TODO TODO TODO TODO
		MapLocation locOfLeader = gc.get_unit(battalionLeader).get_map_location();
		vector<int> members;
		for (map<int, int>::iterator it2 = battalionId.begin(); it2 != battalionId.end(); it2++) {
			if (it2->second == battalionLeader) {
				members.push_back(it2->first);
			}
		}
		// some robots may be scattered
		// move towards leader if possible
		// use pq to ensure units dont walk into each other
		compareUnitMovementOrdering::referenceY = locOfLeader.get_y();
		compareUnitMovementOrdering::referenceX = locOfLeader.get_x();
		priority_queue<Unit, vector<Unit>, compareUnitMovementOrdering> movementPq;
		for (size_t i = 0; i < members.size(); i++) {
			// TODO: remove this gc call TODO TODO TODO TODO TODO
			movementPq.push(gc.get_unit(members[i]));
		}
		while (!movementPq.empty()) {
			Unit nextUnit = movementPq.top();
			movementPq.pop();
			tryMoveToLoc(nextUnit, dis[compareUnitMovementOrdering::referenceY][compareUnitMovementOrdering::referenceX]);
		}
		// now move together towards somewhere as a pack
		// check our destination: if we are already there, get a new target
		if (hasFriendlyUnit[battalionDestinations[battalionLeader].get_y()][battalionDestinations[battalionLeader].get_x()] ||
				hasFriendlyStructure[battalionDestinations[battalionLeader].get_x()][battalionDestinations[battalionLeader].get_x()]) {
			battalionDestinations[battalionLeader] = getRandomMapLocation(Mars, locOfLeader);
		}
		compareUnitMovementOrdering::referenceY = battalionDestinations[battalionLeader].get_y();
		compareUnitMovementOrdering::referenceX = battalionDestinations[battalionLeader].get_x();
		for (size_t i = 0; i < members.size(); i++) {
			// TODO: remove this gc call TODO TODO TODO TODO TODO
			movementPq.push(gc.get_unit(members[i]));
		}
		while (!movementPq.empty()) {
			Unit nextUnit = movementPq.top();
			movementPq.pop();
			tryMoveToLoc(nextUnit, dis[compareUnitMovementOrdering::referenceY][compareUnitMovementOrdering::referenceX]);
		}
	}
}

static MapLocation getRandomMapLocation(Planet p, const MapLocation &giveUp) {
	MapLocation result(p, rand()%width, rand()%height);
	int num_retries = 10;
	while (hasFriendlyUnit[result.get_y()][result.get_x()] || hasFriendlyStructure[result.get_y()][result.get_x()] || !isPassable[result.get_y()][result.get_x()]) {
		result = MapLocation(p, rand()%width, rand()%height);
		if (num_retries-- == 0) {
			return giveUp;
		}
	}
	return result;
}

static void match_units_to_rockets (vector<Unit>& input_units) {
	if (rockets_to_fill.empty()) {
		return;
	}

	vector<Unit> loadable_units;
	fo(i, 0, SZ(input_units)) {
		Unit& unit = input_units[i];
		if (unit.is_on_map() && is_healable_unit_type(unit.get_unit_type())) {
			loadable_units.push_back(input_units[i]);
		}
	}

	fo(z, 0, SZ(rockets_to_fill)) {
		Unit& rocket = rockets_to_fill[z];

		int ry = rocket.get_map_location().get_y();
		int rx = rocket.get_map_location().get_x();

		bool need_healer = true;
		bool need_worker = true;

		vector<unsigned> tmp_garrison = rocket.get_structure_garrison();
		for (unsigned i : tmp_garrison)  {
			UnitType tmp = gc.get_unit(i).get_unit_type();
			if (tmp == Healer) need_healer = false;
			if (tmp == Worker) need_worker = false;
		}

		int num_needed = int(rocket.get_structure_max_capacity() - tmp_garrison.size());

		if (num_needed == 0) {
			continue;
		}

		// sort all available units by distance from rocket

		vector<tuple<int, int, UnitType>> sorted_by_dist;

		fo(i, 0, SZ(loadable_units)) {
			Unit& unit = loadable_units[i];

			if (unit_summon.count(unit.get_id())) {
				// already matched
				continue;
			}

			int ty = unit.get_map_location().get_y();
			int tx = unit.get_map_location().get_x();

			sorted_by_dist.emplace_back(dis[ry][rx][ty][tx], unit.get_id(), unit.get_unit_type());
		}

		sort(sorted_by_dist.begin(), sorted_by_dist.end());

		int c_healer = -1;
		int c_worker = -1;

		fo(i, 0, SZ(sorted_by_dist)) {
			if (!need_healer && !need_worker) {
				break;
			}

			int id;
			UnitType type;
			tie(ignore, id, type) = sorted_by_dist[i];

			if (need_healer && type == Healer) {
				need_healer = false;
				num_needed--;
				c_healer = id;
				unit_summon[id] = make_pair(ry, rx);
			}

			if (need_worker && type == Worker) {
				need_worker = false;
				num_needed--;
				c_worker = id;
				unit_summon[id] = make_pair(ry, rx);
			}
		}

		fo(i, 0, SZ(sorted_by_dist)) {
			if (num_needed == 0) {
				break;
			}

			int id;
			UnitType type;
			tie(ignore, id, type) = sorted_by_dist[i];

			if (id == c_healer || id == c_worker) {
				// don't double count on yourself
				continue;
			}

			num_needed--;
			unit_summon[id] = make_pair(ry, rx);
		}
	}
}

static bool try_summon_move (Unit& unit) {
	if (unit_summon.count(unit.get_id())) {
		// summoned to rocket
		pair<int, int> dest = unit_summon[unit.get_id()];
		tryMoveToLoc(unit, dis[dest.first][dest.second]);
		return true;
	} else {
		return false;
	}
}

static int howMuchCentreKarbToBeWorthIt(int distToCentre) {
	// how much karbonite needs to be in the centre for it to be worth replicate towards the centre
	// if you replicated towards the centre you would need 1 replicate per 2 steps (because each turn you can
	// move once and then replicate once).
	// Let's require that you almost recoup the cost of the workers.
	// Warning: constant being used (replicate karbonite cost)
	return distToCentre / 2 * 56;
}
