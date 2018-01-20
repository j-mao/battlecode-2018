#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <assert.h>
#include <random>
#include <functional>

#include <map>
#include <vector>
#include <queue>

#include "bc.hpp"

#define fo(i,a,b) for (int i=(a);i<(b);i++)

using namespace bc;
using namespace std;

static int roundNum;

static bool raceToMars = false;

static int maxConcurrentRocketsReady = 2;

static int dis[55][55][55][55];
static bool hasFriendlyWorker[55][55];

static int dy[] = {1,1,0,-1,-1,-1,0,1,0};
static int dx[] = {0,1,1,1,0,-1,-1,-1,0};

static int DirCenterIndex = 8; // index 8 corresponds to Direction.Center

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

// temporary seen array
static bool bfsSeen[55][55];

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
static bool isGoodPosition[55][55];
// whether a unit has already taken a potential good position
static bool isGoodPositionTaken[55][55];
static int numGoodPositions;
static int goodPositionsXList[55 * 55];
static int goodPositionsYList[55 * 55];

static int lastKnownKarboniteAmount[55][55];
static int distToKarbonite[55][55];

static int distToDamagedFriendlyHealableUnit[55][55];

static int bfsTowardsBlueprintDist[55][55];

static int numEnemiesThatCanAttackSquare[55][55];

static int numIdleRangers;

static const int MultisourceBfsUnreachableMax = 499;

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

void initGlobalVariables () {

	fo(i, 0, 8) directions.push_back((Direction) i);

}

int getUnitOrderPriority (Unit unit) {
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

class compareUnits {
	public:
		bool operator() (Unit a, Unit b) {
			return getUnitOrderPriority(a) > getUnitOrderPriority(b);
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

	initGlobalVariables();

	// Most methods return pointers; methods returning integers or enums are the only exception.
	gc.queue_research(Knight);
	gc.queue_research(Knight);
	gc.queue_research(Knight);

	if (bc_has_err()) {
		// If there was an error creating gc, just die.
		printf("Failed, dying.\n");
		exit(1);
	}
	printf("Connected!\n");

	// loop through the whole game.
	while (true) {
		unsigned round = gc.get_round();
		printf("Round: %d\n", round);


		auto units = gc.get_my_units();
		for (const auto unit : units) {
			const unsigned id = unit.get_id();

			if (unit.get_unit_type() == Factory) {
				auto garrison = unit.get_structure_garrison();

				if (garrison.size() > 0){
					Direction dir = (Direction) dice();
					if (gc.can_unload(id, dir)){
						gc.unload(id, dir);
						continue;
					}
				} else if (gc.can_produce_robot(id, Knight)){
					gc.produce_robot(id, Knight);
					continue;
				}
			}

			if (unit.get_location().is_on_map()){
				// Calls on the controller take unit IDs for ownership reasons.
				const auto locus = unit.get_location().get_map_location();
				const auto nearby = gc.sense_nearby_units(locus, 2);
				for ( auto place : nearby ){
					//Building 'em blueprints
					if(gc.can_build(id, place.get_id()) && unit.get_unit_type() == Worker){
						gc.build(id, place.get_id());
						continue;
					}
					//Attacking 'em enemies
					if( place.get_team() != unit.get_team() and
							gc.is_attack_ready(id) and
							gc.can_attack(id, place.get_id()) ){
						gc.attack(id, place.get_id());
						continue;
					}
				}
			}

			Direction d = (Direction) dice();
			// Placing 'em blueprints
			if(gc.can_blueprint(id, Factory, d) and gc.get_karbonite() > unit_type_get_blueprint_cost(Factory)){
				gc.blueprint(id, Factory, d);
			} else if (gc.is_move_ready(id) && gc.can_move(id,d)){ // Moving otherwise (if possible)
				gc.move_robot(id,d);
			}
		}
		// this line helps the output logs make more sense by forcing output to be sent
		// to the manager.
		// it's not strictly necessary, but it helps.
		// pause and wait for the next turn.
		fflush(stdout);
		gc.next_turn();
	}
	// I'm convinced C++ is the better option :)
}
