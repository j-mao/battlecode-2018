// import the API.
// See xxx for the javadocs.
import bc.*;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Comparator;

public class Player {

    public static int roundNum;

    public static boolean raceToMars = false;

    public static int maxConcurrentRocketsReady = 2;

    public static int[][][][] dis = new int[55][55][55][55];

    public static int[][] cacheKarboniteAt = new int[55][55];
    public static int[][] cacheCanSenseLocation = new int[55][55];

    public static int dy[] = {1,1,0,-1,-1,-1,0,1,0};
    public static int dx[] = {0,1,1,1,0,-1,-1,-1,0};

    public static int DirCenterIndex = 8; // index 8 corresponds to Direction.Center

    static class BfsState {
        public int y, x, startingDir;

        public BfsState(int yy, int xx, int sd) {
            y = yy;
            x = xx;
            startingDir = sd;
        }
    }

    static class SimpleState {
        public int y, x;

        public SimpleState (int yy, int xx) {
            y = yy;
            x = xx;
        }
    }

    // from https://stackoverflow.com/questions/683041/how-do-i-use-a-priorityqueue
    // LuL not knowing how to use java in 2018 LuL
    public static class UnitOrderComparator implements Comparator<Unit>
    {
        @Override
        public int compare(Unit x, Unit y)
        {
            int xp = getUnitOrderPriority(x);
            int yp = getUnitOrderPriority(y);
            return xp - yp;
        }
    }

    // used to give priority to units that are closer to enemies
    public static int manhattanDistanceToNearestEnemy[][];
    // Warning: constant used, would need to change this if the maps got bigger... but they probably won't get bigger so whatever.
    public static final int ManhattanDistanceNotSeen = 499;

    public static int getUnitOrderPriority (Unit unit) {
        switch(unit.unitType()) {
            // Actually not sure whether fighting units or workers should go first... so just use the same priority...
            case Ranger:
            case Knight:
            case Mage:
            case Healer:
            // Worker before factory so that workers can finish a currently building factory before the factory runs
            case Worker:
                if (unit.location().isOnMap()) {
                    MapLocation loc = unit.location().mapLocation();
                    // give priority to units that are closer to enemies
                    // NOTE: the default value for manhattanDistanceToNearestEnemy (ie the value when there are no nearby
                    //   enemies) should be less than 999 so that priorities don't get mixed up!
                    return 1000 + manhattanDistanceToNearestEnemy[loc.getY()][loc.getX()];
                } else {
                    return 1999;
                }
            // Factory before rocket so that factory can make a unit, then unit can get in rocket before the rocket runs
            // Edit: not actually sure if you can actually do that lol... whatever.
            case Factory:
                return 2000;
            case Rocket:
                return 3000;
        }
        System.out.println("ERROR: getUnitOrderPriority() does not recognise this unit type!");
        return 9999;
    }

    public static GameController gc = new GameController();
    public static Random rand = new Random();
    // Direction is a normal java enum.
    public static Direction[] directions = Direction.values();
    public static Map<Integer, Integer> tendency = new HashMap<Integer, Integer>();
    public static ArrayList<MapLocation> attackLocs = new ArrayList<MapLocation>();
    public static int width, height;
    public static boolean isPassable[][];
    public static boolean hasFriendlyUnit[][];
    // temporary seen array
    public static boolean bfsSeen[][];
    // which direction you should move to reach each square, according to the bfs
    public static int bfsDirectionIndexTo[][];
    public static MapLocation bfsClosestKarbonite;
    public static MapLocation bfsClosestEnemy;
    public static MapLocation bfsClosestFreeGoodPosition;
    // to random shuffle the directions
    public static int randDirOrder[];
    // factories that are currently blueprints
    public static ArrayList<Unit> factoriesBeingBuilt = new ArrayList<Unit>();
    public static ArrayList<Unit> rocketsBeingBuilt = new ArrayList<Unit>();
    public static ArrayList<Unit> rocketsReady = new ArrayList<Unit>();
    // Store this list of all our units ourselves, so that we can add to it when we create units and use those new units
    // immediately.
    public static Comparator<Unit> unitOrderComparator = new UnitOrderComparator();
    public static PriorityQueue<Unit> allMyUnits = new PriorityQueue<Unit>(5, unitOrderComparator);

    // team defs
    static Team myTeam;
    static Team enemyTeam;

    // (Currently not used! Just ignore this xd)
    // Map of how long ago an enemy ranger was seen at some square
    // Stores -1 if a ranger was not seen at that square the last time you were able to sense it (or if you can currently
    //   sense it and there's not a ranger there.
    // Otherwise stores number of turns since last sighting, 0 if you can see one this turn.
    // TODO: do we need this?
    //public static int enemyRangerSeenWhen[][];

    // Map of euclidean distance of each square to closest enemy
    // Only guaranteed to be calculated up to a maximum distance of 100, otherwise the distance could be correct, or
    // could be set to some large value (e.g. 9999)
    // TODO: Warning: Constants
    // TODO: change this if constants change
    public static int attackDistanceToEnemy[][];
    // good positions are squares which are some distance from the enemy.
    // e.g. distance [51, 72] from the closest enemy (this might be changed later...)
    // Warning: random constants being used. Need to be changed if constants change!
    // if units take up "good positions" then in theory they should form a nice concave. "In theory" LUL
    public static boolean isGoodPosition[][];
    // whether a unit has already taken a potential good position
    public static boolean isGoodPositionTaken[][];
    public static int numGoodPositions;
    public static int goodPositionsXList[];
    public static int goodPositionsYList[];

    public static int lastKnownKarboniteAmount[][] = new int[55][55];
    public static int distToKarbonite[][] = new int[55][55];

    public static int distToDamagedFriendlyHealableUnit[][] = new int[55][55];

    public static int bfsTowardsBlueprintDist[][] = new int[55][55];

    public static int numIdleRangers;

    public static final int MultisourceBfsUnreachableMax = 499;

    // whether the square is "near" enemy units
    // currently defined as being within distance 72 of an enemy
    public static boolean isSquareDangerous[][];
    public static final int DangerDistanceThreshold = 72;

    public static final int RangerAttackRange = 50;
    // distance you have to be to be one move away from ranger attack range
    // unfortunately it's not actually the ranger's vision range which is 70, it's actually 72...
    public static final int OneMoveFromRangerAttackRange = 72;

    // Distance around a worker to search for whether there is enough karbonite to be worth replicating
    public static int IsEnoughResourcesNearbySearchDist = 8;

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

    public static Planet myPlanet;
    public static PlanetMap EarthMap;
    public static PlanetMap MarsMap;

    public static void main(String[] args) {

        roundNum = 1;
        EarthMap = gc.startingMap(Planet.Earth);
        MarsMap = gc.startingMap(Planet.Mars);
        myPlanet = gc.planet();

        myTeam = gc.team();
        enemyTeam = (myTeam == Team.Red) ? Team.Blue : Team.Red;

        initializeBFS();

        if (myPlanet == Planet.Mars) {
            
            while (true) {
                //System.out.println("Current round: " + roundNum);

                VecUnit units = gc.myUnits();
                initTurn(units);

                while (!allMyUnits.isEmpty()) {
                    Unit unit = allMyUnits.remove();
                    //System.out.println("running " + unit.unitType().toString());

                    if (unit.location().isInSpace() || unit.location().isInGarrison()) {
                        continue;
                    }

                    switch (unit.unitType()) {
                        case Worker:
                            runMarsWorker(unit);
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
                    }
                }
                gc.nextTurn();
                roundNum++;
            }
        } else {

            gc.queueResearch(UnitType.Worker);
            gc.queueResearch(UnitType.Ranger);
            gc.queueResearch(UnitType.Healer);
            gc.queueResearch(UnitType.Ranger);
            gc.queueResearch(UnitType.Healer);
            gc.queueResearch(UnitType.Rocket);
            gc.queueResearch(UnitType.Rocket);
            gc.queueResearch(UnitType.Rocket);

            while (true) {
                System.out.println("Current round: " + roundNum);

                VecUnit units = gc.myUnits();
                initTurn(units);

                if (roundNum >= 400) {
                    startRacingToMars();
                }

                while (!allMyUnits.isEmpty()) {
                    Unit unit = allMyUnits.remove();
                    //System.out.println("running " + unit.unitType().toString());

                    if (unit.location().isInSpace() || unit.location().isInGarrison()) {
                        continue;
                    }

                    switch (unit.unitType()) {
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
                    }
                }

                finishTurn();

                gc.nextTurn();
                roundNum++;
            }

        }
    }

    public static int getDirIndex(Direction dir) {
        // TODO: make this faster?
        for (int i = 0; i < 8; i++) {
            if (directions[i] == dir) {
                return i;
            }
        }
        System.out.println("ERROR: Couldn't find dir index for: " + dir.toString());
        return 0;
    }

    public static void initializeBFS () {
        PlanetMap myMap = gc.startingMap(gc.planet());
        width = (int)myMap.getWidth();
        height = (int)myMap.getHeight();
        isPassable = new boolean[height][width];
        hasFriendlyUnit = new boolean[height][width];
        bfsSeen = new boolean[height][width];
        bfsDirectionIndexTo = new int[height][width];
        attackDistanceToEnemy = new int[height][width];
        isGoodPosition = new boolean[height][width];
        isGoodPositionTaken = new boolean[height][width];
        goodPositionsXList = new int[height * width];
        goodPositionsYList = new int[height * width];
        manhattanDistanceToNearestEnemy = new int[height][width];
        isSquareDangerous = new boolean[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            MapLocation loc = new MapLocation(myPlanet, x, y);
            //System.out.println("checking if there's passable terrain at " + new MapLocation(gc.planet(), x, y).toString());
            //System.out.println("is passable at y = " + y + ", x = " + x + " is " + myMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)));
            isPassable[y][x] = myMap.isPassableTerrainAt(loc) != 0;
            lastKnownKarboniteAmount[y][x] = (int)myMap.initialKarboniteAt(loc);
        }
        VecUnit units = myMap.getInitial_units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.team() != myTeam) {
                //System.out.println("Adding attack location " + unit.location().mapLocation().toString());
                attackLocs.add(unit.location().mapLocation());
            }
        }
        randDirOrder = new int[8];
        for (int i = 0; i < 8; i++) {
            randDirOrder[i] = i;
        }
        allPairsShortestPath();
    }

    public static void initTurn (VecUnit myUnits) {

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                cacheKarboniteAt[i][j] = -1;
                cacheCanSenseLocation[i][j] = -1;
            }
        }

        factoriesBeingBuilt.clear();
        rocketsBeingBuilt.clear();

        for (int i = 0; i < myUnits.size(); i++) {
            Unit unit = myUnits.get(i);
            if (unit.unitType() == UnitType.Factory) {
                if (unit.structureIsBuilt() == 0) {
                    factoriesBeingBuilt.add(unit);
                }
            }
            if (unit.unitType() == UnitType.Rocket) {
                if (unit.structureIsBuilt() == 0) {
                    rocketsBeingBuilt.add(unit);
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hasFriendlyUnit[y][x] = false;
                attackDistanceToEnemy[y][x] = 9999;
                isGoodPosition[y][x] = false;
                isGoodPositionTaken[y][x] = false;
                isSquareDangerous[y][x] = false;

                MapLocation loc = new MapLocation(gc.planet(), x, y);
                if (gc.canSenseLocation(loc)) {
                    lastKnownKarboniteAmount[y][x] = (int) gc.karboniteAt(loc);
                }
            }
        }

        //reset unit counts
        numWorkers = 0; numKnights = 0; numRangers = 0; numMages = 0; numHealers = 0; numFactories = 0; numRockets = 0;
        numFactoryBlueprints = 0; numRocketBlueprints = 0;

        numIdleRangers = 0;

        rocketsReady.clear();

        ArrayList<SimpleState> damagedFriendlyHealableUnits = new ArrayList<SimpleState>();
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            // Note: This loops through friendly AND enemy units!
            Unit unit = units.get(i);
            if (unit.team() == myTeam) {
                // my team

                if (unit.location().isOnMap()) {
                    MapLocation loc = unit.location().mapLocation();
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;

                    if (isHealableUnitType(unit.unitType()) && unit.health() < unit.maxHealth()) {
                        MapLocation damagedUnitLoc = unit.location().mapLocation();
                        damagedFriendlyHealableUnits.add(new SimpleState(damagedUnitLoc.getY(), damagedUnitLoc.getX()));
                    }
                }

                // friendly unit counts
                if (unit.unitType() == UnitType.Factory) {

                    if (unit.structureIsBuilt() != 0) numFactories++;
                    else numFactoryBlueprints++;

                    if (unit.isFactoryProducing() != 0) {
                        addTypeToFriendlyUnitCount(unit.factoryUnitType());
                    }

                } else if (unit.unitType() == UnitType.Rocket) {

                    if (unit.structureIsBuilt() == 0) {

                        if (unit.structureGarrison().size() < unit.structureMaxCapacity()) {
                            rocketsReady.add(unit);
                        }

                        numRockets++;
                    } else numRocketBlueprints++;

                } else {
                    addTypeToFriendlyUnitCount(unit.unitType());
                }
            } else {
                // enemy team

                // calculate closest distances to enemy fighter units
                if (unit.location().isOnMap() && isFighterUnitType(unit.unitType())) {
                    MapLocation loc = unit.location().mapLocation();
                    int locY = loc.getY(), locX = loc.getX();
                    for (int y = locY - 10; y <= locY + 10; y++) {
                        if (y < 0 || height <= y) continue;
                        for (int x = locX - 10; x <= locX + 10; x++) {
                            if (x < 0 || width <= x) continue;
                            attackDistanceToEnemy[y][x] = Math.min(attackDistanceToEnemy[y][x],
                                    (y - locY) * (y - locY) + (x - locX) * (x - locX));
                        }
                    }
                }
            }
        }

        // calculate good positions
        numGoodPositions = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (RangerAttackRange < attackDistanceToEnemy[y][x] && attackDistanceToEnemy[y][x] <= OneMoveFromRangerAttackRange) {
                isGoodPosition[y][x] = true;
                goodPositionsYList[numGoodPositions] = y;
                goodPositionsXList[numGoodPositions] = x;
                //System.out.println("good position at " + y + " " + x);
                numGoodPositions++;
            }

            // also calculate dangerous squares
            if (attackDistanceToEnemy[y][x] <= DangerDistanceThreshold) {
                isSquareDangerous[y][x] = true;
            }
        }

        allMyUnits.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            allMyUnits.add(myUnits.get(i));
        }

        calculateManhattanDistancesToClosestEnemies(units);

        // calculate distances from all workers to closest karbonite
        ArrayList<SimpleState> karboniteLocs = new ArrayList<SimpleState>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!isSquareDangerous[y][x] && lastKnownKarboniteAmount[y][x] > 0) {
                karboniteLocs.add(new SimpleState(y, x));
            }
        }
        multisourceBfsAvoidingUnitsAndDanger(karboniteLocs, distToKarbonite);

        // calculate distances to damaged healable friendly units
        multisourceBfsAvoidingUnitsAndDanger(damagedFriendlyHealableUnits, distToDamagedFriendlyHealableUnit);

        // attackLoc update
        checkForEnemyUnits(units);

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
    }

    public static void finishTurn() {
        System.out.println("" + numIdleRangers + " idle rangers");
    }

    public static void startRacingToMars () {
        if (raceToMars || myPlanet == Planet.Mars) {
            return;
        }

        raceToMars = true;
        maxConcurrentRocketsReady = 5;
    }

    public static void doMoveRobot(Unit unit, Direction dir) {
        if (gc.canMove(unit.id(), dir)) {
            MapLocation loc = unit.location().mapLocation();
            if (!hasFriendlyUnit[loc.getY()][loc.getX()]) {
                System.out.println("Error: hasFriendlyUnit[][] is incorrect!");
                return;
            }
            hasFriendlyUnit[loc.getY()][loc.getX()] = false;
            loc.add(dir);
            hasFriendlyUnit[loc.getY()][loc.getX()] = true;
            gc.moveRobot(unit.id(), dir);
        }
    }

    public static void shuffleDirOrder() {
        for (int i = 7; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            int tmp = randDirOrder[j];
            randDirOrder[j] = randDirOrder[i];
            randDirOrder[i] = tmp;
        }
    }

    public static boolean getCanSenseLocation (MapLocation where) {
        int ty = where.getY();
        int tx = where.getX();
        if (cacheCanSenseLocation[ty][tx] == -1) {
            cacheCanSenseLocation[ty][tx] = gc.canSenseLocation(where) ? 1 : 0;
        }
        return cacheCanSenseLocation[ty][tx] == 1;
    }

    public static int getKarboniteAt (MapLocation where) {
        int ty = where.getY();
        int tx = where.getX();
        if (cacheKarboniteAt[ty][tx] == -1) {
            cacheKarboniteAt[ty][tx] = (int) gc.karboniteAt(where);
        }
        return cacheKarboniteAt[ty][tx];
    }

    // bfs to all squares
    // store how close you want to get to your destination in howClose (distance = #moves distance, not Euclidean distance)
    // stores bfsClosestKarbonite: closest location which gets you within howClose of karbonite
    // stores bfsClosestEnemy: closest location which is within howClose of an enemy
    // stores bfsClosestFreeGoodPosition: closest good position that hasn't been taken by another unit
    // stores the direction you should move to be within howClose of each square
    public static void bfs (MapLocation from, int howClose) {
        bfsClosestKarbonite = null;
        bfsClosestEnemy = null;
        bfsClosestFreeGoodPosition = null;

        int fromY = from.getY(), fromX = from.getX();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            bfsDirectionIndexTo[y][x] = DirCenterIndex;
            bfsSeen[y][x] = false;
        }
        bfsSeen[fromY][fromX] = true;
        Queue<BfsState> queue = new LinkedList<>();

        shuffleDirOrder();
        for (int d = 0; d < 8; d++) {
            int ny = fromY + dy[randDirOrder[d]];
            int nx = fromX + dx[randDirOrder[d]];
            if (0 <= ny && ny < height && 0 <= nx && nx < width && !hasFriendlyUnit[ny][nx] && isPassable[ny][nx]) {
                bfsSeen[ny][nx] = true;
                queue.add(new BfsState(ny, nx, randDirOrder[d]));
            }
        }

        while (!queue.isEmpty()) {
            BfsState cur = queue.peek();
            queue.remove();

            for (int y = cur.y - howClose; y <= cur.y + howClose; y++) for (int x = cur.x - howClose; x <= cur.x + howClose; x++) {
                if (0 <= y && y < height && 0 <= x && x < width && bfsDirectionIndexTo[y][x] == DirCenterIndex) {
                    bfsDirectionIndexTo[y][x] = cur.startingDir;
                    MapLocation loc = new MapLocation(myPlanet, x, y);
                    if (getCanSenseLocation(loc)) {
                        if (bfsClosestKarbonite == null && getKarboniteAt(loc) > 0) {
                            bfsClosestKarbonite = loc;
                        }
                        /*
                        FOR PERFORMANCE REASONS, BFSCLOSESTENEMY IS NOT WORKING
                        if (bfsClosestEnemy == null &&
                                loc.distanceSquaredTo(new MapLocation(myPlanet, cur.x, cur.y)) <= howClose &&
                                gc.hasUnitAtLocation(loc) &&
                                gc.senseUnitAtLocation(loc).team() != myTeam) {
                            bfsClosestEnemy = loc;
                        }
                        */
                        if (bfsClosestFreeGoodPosition == null && isGoodPosition[y][x] && !isGoodPositionTaken[y][x]) {
                            bfsClosestFreeGoodPosition = loc;
                        }
                    }
                }
            }

            shuffleDirOrder();
            for (int d = 0; d < 8; d++) {
                int ny = cur.y + dy[randDirOrder[d]];
                int nx = cur.x + dx[randDirOrder[d]];
                if (0 <= ny && ny < height && 0 <= nx && nx < width && !hasFriendlyUnit[ny][nx] && isPassable[ny][nx] && !bfsSeen[ny][nx]) {
                    bfsSeen[ny][nx] = true;
                    queue.add(new BfsState(ny, nx, cur.startingDir));
                }
            }
        }
    }

    public static void runEarthWorker (Unit unit) {
        if (!unit.location().isOnMap()) {
            return;
        }

        boolean doneMovement = !gc.isMoveReady(unit.id());

        // movement
        if (!doneMovement && false /* need to place factory and no adjacent good places and nearby good place */) {
            // move towards good place
        }
        if (!doneMovement && isNextToBuildingBlueprint(unit.location().mapLocation())) {
            // if next to blueprint, stay next to blueprint
            doneMovement = true;
        }
        if (!doneMovement) {
            // if very near a blueprint, move towards it

            if (bfsTowardsBlueprint(unit)) {
                doneMovement = true;
            }
        }
        if (!doneMovement) {
            //  move towards karbonite
            MapLocation loc = unit.location().mapLocation();
            if (distToKarbonite[loc.getY()][loc.getX()] != MultisourceBfsUnreachableMax) {
                tryMoveToLoc(unit, distToKarbonite);
                doneMovement = true;
            }
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
                MapLocation loc = unit.location().mapLocation().add(dir);
                if (0 <= loc.getY() && loc.getY() < height &&
                        0 <= loc.getX() && loc.getX() < width &&
                        !isSquareDangerous[loc.getY()][loc.getX()] &&
                        gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                    break;
                }
            }
        }

        // update worker location after moving
        unit = gc.unit(unit.id());
        MapLocation loc = unit.location().mapLocation();

        // actions
        boolean doneAction = false;

        boolean replicateForBuilding = (numFactories + numFactoryBlueprints > 0 && numWorkers < 6);
        boolean replicateForHarvesting = distToKarbonite[loc.getY()][loc.getX()] < IsEnoughResourcesNearbySearchDist && isEnoughResourcesNearby(unit);

        boolean canRocket = (gc.researchInfo().getLevel(UnitType.Rocket) >= 1);
        boolean lowRockets = ((int) rocketsReady.size() + numRocketBlueprints < maxConcurrentRocketsReady);
        boolean needToSave = (gc.karbonite() < 75 + 15);

        boolean shouldReplicate = (replicateForBuilding || replicateForHarvesting);

        if (canRocket && lowRockets && needToSave) {
            shouldReplicate = false;
        }

        if (!doneAction && unit.abilityHeat() < 10 && shouldReplicate) {
            // try to replicate
            if (doReplicate(unit)) {
                doneAction = true;
            }
        }

        if (!doneAction) {
            if (numFactories + numFactoryBlueprints < 3) {

                if (doBlueprint(unit, UnitType.Factory)) {
                    doneAction = true;
                }
            } else if (canRocket && lowRockets) {

                if (doBlueprint(unit, UnitType.Rocket)) {
                    doneAction = true;
                }

            }

            // can blueprint factory/rocket and want to blueprint factory/rocket...

            // TODO: try to move to a location with space if the adjacent spaces are too cramped to build a blueprint

        }

        if (doBuild(unit)) {
            // try to build adjacent structures
            doneAction = true;
        }
        if (!doneAction) {
            // if next to karbonite, mine
            for (int i = 0; i < 9; i++) {
                if (gc.canHarvest(unit.id(), directions[i])) {
                    //System.out.println("harvesting!");
                    gc.harvest(unit.id(), directions[i]);
                    doneAction = true;
                    break;
                }
            }
        }
        if (!doneAction) {
            // if next to damaged structure, repair
            // TODO: implement this
        }

        return;

        /*
        // try to replicate
        // TODO: limit to 8 workers
        // TODO: only replicate if there is enough money
        // TODO: improve metric of "enough money"
        //System.out.println("ability cooldown = " + unit.abilityCooldown() + ", heat = " + unit.abilityHeat());
        // check we don't have too many workers, and that we have enough money for a factory
        // TODO: make this logic better. e.g. we might need money for a factory in the future
        // TODO: Find out where cost constants are and replay this "100 + 15" with those xd.

        int earthWorkerLimit = raceToMars ? 20 : 7;

        boolean shouldReplicate = false;

        if (!raceToMars) {
            if (numWorkers<earthWorkerLimit && gc.karbonite() >= 100+15) {
                shouldReplicate = true;
            }
        } else {
            if (gc.karbonite() >= 75+15) {
                shouldReplicate = true;
            }
        }

        if (shouldReplicate) {
            // try to replicate next to currently building factory
            shuffleDirOrder();
            Direction replicateDir = Direction.Center;
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[randDirOrder[i]];
                if (gc.canReplicate(unit.id(), dir) &&
                        isNextToBuildingBlueprint(unit.location().mapLocation().add(dir))) {
                    replicateDir = dir;
                    break;
                }
            }
            // otherwise, replicate wherever
            if (replicateDir == Direction.Center) {
                for (int i = 0; i < 8; i++) {
                    Direction dir = directions[randDirOrder[i]];
                    if (gc.canReplicate(unit.id(), dir)) {
                        replicateDir = dir;
                        break;
                    }
                }
            }
            if (replicateDir != Direction.Center) {
                gc.replicate(unit.id(), replicateDir);
                // TODO: add created units to units list so they can do something immediately on the turn they're created
                MapLocation loc = unit.location().mapLocation().add(replicateDir);
                allMyUnits.add(gc.senseUnitAtLocation(loc));
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                // TODO: test on a rectangular map to make sure I didn't mess up any y, x orders anywhere
                numWorkers++;
                doneAction = true;
            }
        }

        // try to build an adjacent factory blueprint
        if (!doneAction) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);

                // only look at factories and rockets
                if (other.unitType() != UnitType.Factory && other.unitType() != UnitType.Rocket) {
                    continue;
                }

                if (gc.canBuild(unit.id(), other.id())) {
                    gc.build(unit.id(), other.id());

                    // If it's complete, remove it from the factoriesBeingBuilt list
                    // need to re-sense the unit to get the updated structureIsBuilt() value
                    if (gc.senseUnitAtLocation(other.location().mapLocation()).structureIsBuilt() != 0) {
                        //System.out.println("Finished a factory!");
                        boolean foundIt = false;

                        if (other.unitType() == UnitType.Factory) {
                            for (int j = 0; j < factoriesBeingBuilt.size(); j++) {
                                if (factoriesBeingBuilt.get(j).location().mapLocation().equals(other.location().mapLocation())) {
                                    factoriesBeingBuilt.remove(j);
                                    foundIt = true;
                                    break;
                                }
                            }
                        } else {
                            for (int j = 0; j < rocketsBeingBuilt.size(); j++) {
                                if (rocketsBeingBuilt.get(j).location().mapLocation().equals(other.location().mapLocation())) {
                                    rocketsReady.add(other);
                                    rocketsBeingBuilt.remove(j);
                                    foundIt = true;
                                    break;
                                }
                            }
                        }
                        if (!foundIt) {
                            System.out.println("ERROR: Structure was expected in ___BeingBuilt, but was missing!");
                        }
                    }
                    doneAction = true;
                    doneMovement = true;
                    break;
                }
            }
        }

        // try to blueprint
        if (!doneAction) {
            // TODO: make this all directions
            // TODO: make this choose a square with more space
            if (factoriesBeingBuilt.isEmpty()) {
                shuffleDirOrder();
                int best = -1, bestSpace = -1;

                UnitType toBlueprint = UnitType.Factory;
                if (gc.researchInfo().getLevel(UnitType.Rocket) >= 1) {
                    // System.out.println("I CAN NOW TRY TO BUILD A ROCKET");
                    toBlueprint = UnitType.Rocket;
                }

                if (toBlueprint == UnitType.Factory) {
                    // Find blueprint direction such that the factory will have the most free space around it
                    for (int i = 0; i < 8; i++) {
                        if (gc.canBlueprint(unit.id(), toBlueprint, directions[randDirOrder[i]])) {
                            int space = getSpaceAround(unit.location().mapLocation().add(directions[randDirOrder[i]]));
                            if (space > bestSpace) {
                                bestSpace = space;
                                best = i;
                            }
                        }
                    }
                } else {
                    // TODO: decide if we want Rockets to have minimal space like factories (see above)
                    // currently this runs the same code as for Factory
                    for (int i = 0; i < 8; i++) {
                        if (gc.canBlueprint(unit.id(), toBlueprint, directions[randDirOrder[i]])) {
                            int space = getSpaceAround(unit.location().mapLocation().add(directions[randDirOrder[i]]));
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
                    
                    gc.blueprint(unit.id(), toBlueprint, dir);
                    MapLocation loc = unit.location().mapLocation().add(dir);
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                    doneAction = true;
                    doneMovement = true;
                    Unit other = gc.senseUnitAtLocation(loc);
                    //System.out.println("The factory I just built is here: " + other.toString());
                    // TODO: continue this
                    // TODO: store ArrayList of current factory blueprints and implement workers moving towards them
                    // TODO: implement worker replication

                    if (toBlueprint == UnitType.Factory) {
                        factoriesBeingBuilt.add(other);
                        numFactoryBlueprints++;
                    } else {
                        rocketsBeingBuilt.add(other);
                        numRocketBlueprints++;
                    }
                    // TODO: add this factory to allMyUnits list?
                    // TODO: or maybe not because it's not going to do anything anyway? (unless it does some calculation or something)
                }
            }
        }

        // if you're next to a factory don't move
        if (!doneMovement) {
            if (isNextToBuildingBlueprint(unit.location().mapLocation())) {
                doneMovement = true;
            }
        }

        // if there is a blueprint somewhere, try to move towards it to help build it
        if (!doneMovement) {
            if (!factoriesBeingBuilt.isEmpty() && numFactories < 3) {
                // set done to true so we don't move randomly
                doneMovement = true;

                // bfs to within distance 1 of every square because we want to move within distance 1 of a factory
                bfs(unit.location().mapLocation(), 1);

                // TODO: change this to closest factory
                Unit factory = factoriesBeingBuilt.get(0);
                MapLocation loc = factory.location().mapLocation();
                Direction dir = directions[bfsDirectionIndexTo[loc.getY()][loc.getX()]];
                //System.out.println("worker moving to factory in dir " + dir.toString());

                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                }
            } else if (!rocketsBeingBuilt.isEmpty() && (int) rocketsReady.size() < maxConcurrentRocketsReady) {
                // set done to true so we don't move randomly
                doneMovement = true;

                // bfs to within distance 1 of every square because we want to move within distance 1 of a rocket
                bfs(unit.location().mapLocation(), 1);

                // TODO: change this to closest rocket
                Unit rocket = rocketsBeingBuilt.get(0);
                MapLocation loc = rocket.location().mapLocation();
                Direction dir = directions[bfsDirectionIndexTo[loc.getY()][loc.getX()]];
                //System.out.println("worker moving to rocket in dir " + dir.toString());

                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                }
            }
        }



        // otherwise, if you have 3 complete factories, try to move towards minerals
        // TODO: make this >= 3 thing better
        // TODO: ie make it not horrible in special case maps LuL
        if (!doneMovement && unit.location().isOnMap() && numFactories >= 3) {
            MapLocation loc = unit.location().mapLocation();
            if (distToKarbonite[loc.getY()][loc.getX()] != MultisourceBfsUnreachableMax) {
                tryMoveToLoc(unit, distToKarbonite);
                doneMovement = true;
            }
        }

        // otherwise move randomly to a not-dangerous square
        if (!doneMovement && unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
            shuffleDirOrder();
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[randDirOrder[i]];
                MapLocation loc = unit.location().mapLocation().add(dir);
                if (0 <= loc.getY() && loc.getY() < height &&
                        0 <= loc.getX() && loc.getX() < width &&
                        !isSquareDangerous[loc.getY()][loc.getX()] &&
                        gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                    break;
                }
            }
        }

        if (!doneAction) {
            for (int i = 0; i < 9; i++) {
                if (gc.canHarvest(unit.id(), directions[i])) {
                    //System.out.println("harvesting!");
                    gc.harvest(unit.id(), directions[i]);
                }
            }
        }
        */
    }

    public static void runMarsWorker (Unit unit) {

        boolean doneAction = false;

        if (roundNum % 150 == 0 || roundNum >= 750) {
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[randDirOrder[i]];
                if (gc.canReplicate(unit.id(), dir)) {
                    gc.replicate(unit.id(), dir);
                    // TODO: add created units to units list so they can do something immediately on the turn they're created
                    MapLocation loc = unit.location().mapLocation().add(dir);
                    allMyUnits.add(gc.senseUnitAtLocation(loc));
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                    // TODO: test on a rectangular map to make sure I didn't mess up any y, x orders anywhere
                    numWorkers++;
                    doneAction = true;
                    break;
                }
            }
        }

        if (!doneAction) {
            // try to harvest
            for (int i = 0; i < 9; i++) {
                if (gc.canHarvest(unit.id(), directions[i])) {
                    //System.out.println("harvesting!");
                    gc.harvest(unit.id(), directions[i]);
                }
            }
        }

        // try move randomly
        // TODO: MOVE SMARTLY TOWARDS FOOD
        Direction dir = directions[rand.nextInt(8)];

        // Most methods on gc take unit IDs, instead of the unit objects themselves.
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
            doMoveRobot(unit, dir);
        }
    }

    static void tryToUnload (Unit unit) {
        for (int j = 0; j < 8; j++) {
            Direction unloadDir = directions[j];
            if (gc.canUnload(unit.id(), unloadDir)) {
                gc.unload(unit.id(), unloadDir);
                MapLocation loc = unit.location().mapLocation().add(unloadDir);
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                allMyUnits.add(gc.senseUnitAtLocation(loc));
                // TODO: check everywhere else to make sure hasFriendlyUnits[][] is being correctly maintained.
            }
        }
    }

    public static void runEarthRocket (Unit unit) {

        if (unit.structureIsBuilt() == 0) {
            return;
        }

        tryToLoadRocket(unit);

        boolean fullRocket = (unit.structureGarrison().size() == unit.structureMaxCapacity());
        boolean aboutToFlood = (roundNum >= 749);
        boolean notWorthWaiting = (roundNum >= 649 && gc.orbitPattern().duration(roundNum)+roundNum <= gc.orbitPattern().duration(roundNum+1)+roundNum+1);
        boolean dangerOfDestruction = (unit.health() <= 70);

        if (dangerOfDestruction || aboutToFlood || (fullRocket && notWorthWaiting)) {
            MapLocation where = null;
            do {
                int landX = (int)(Math.abs(rand.nextInt())%MarsMap.getWidth());
                int landY = (int)(Math.abs(rand.nextInt())%MarsMap.getHeight());
                where = new MapLocation(Planet.Mars, landX, landY);
            } while (MarsMap.isPassableTerrainAt(where) == 0);

            gc.launchRocket(unit.id(), where);
        }
    }

    public static void runMarsRocket (Unit unit) {
        tryToUnload(unit);
    }

    public static void runFactory (Unit unit) {
        UnitType unitTypeToBuild = UnitType.Ranger;

        // TODO: change proportion based on current research levels
        if (numRangers >= 4 * numHealers + 4) {
            unitTypeToBuild = UnitType.Healer;
        }

        if (numWorkers == 0) {
            unitTypeToBuild = UnitType.Worker;
        }

        boolean canRocket = (gc.researchInfo().getLevel(UnitType.Rocket) >= 1);
        boolean lowRockets = ((int) rocketsReady.size() + numRocketBlueprints < maxConcurrentRocketsReady);
        int unitCost = (unitTypeToBuild == UnitType.Worker ? 25 : 20);
        boolean needToSave = (gc.karbonite() < 75 + unitCost);

        if (canRocket && lowRockets && needToSave) {
            return;
        }

        if (gc.canProduceRobot(unit.id(), unitTypeToBuild)) {
            //System.out.println("PRODUCING ROBOT!!!");
            gc.produceRobot(unit.id(), unitTypeToBuild);
            // make sure to immediately update unit counts
            addTypeToFriendlyUnitCount(unitTypeToBuild);
        }

        tryToUnload(unit);
    }

    public static void runRanger (Unit unit) {
        // try to attack before and after moving
        boolean doneAttack = rangerTryToAttack(unit);

        // decide movement
        if (unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
            boolean doneMove = false;
            int myY = unit.location().mapLocation().getY();
            int myX = unit.location().mapLocation().getX();

            // Warning: Constant being used. Change this constant
            if (!doneMove && attackDistanceToEnemy[myY][myX] <= OneMoveFromRangerAttackRange) {
                // if near enemies, do fighting movement

                // somehow decide whether to move forward or backwards
                if (attackDistanceToEnemy[myY][myX] <= RangerAttackRange) {
                    // currently in range of enemy

                    if (doneAttack) {
                        // just completed an attack, move backwards now to kite
                        // to move backwards, we just don't set doneMove to true
                        // and then the code will fall back to the "move to good position" code

                    } else {
                        // wait for your next attack before kiting back, so don't move yet
                        doneMove = true;
                    }
                } else {
                    // currently 1 move from being in range of enemy
                    if (!doneAttack && gc.isAttackReady(unit.id()) && gc.round() % 5 == 0) {
                        // move into a position where you can attack
                        shuffleDirOrder();
                        for (int i = 0; i < 8; i++) {
                            Direction dir = directions[randDirOrder[i]];
                            MapLocation loc = unit.location().mapLocation().add(dir);
                            if (0 <= loc.getY() && loc.getY() < height &&
                                    0 <= loc.getX() && loc.getX() < width &&
                                    attackDistanceToEnemy[loc.getY()][loc.getX()] <= RangerAttackRange &&
                                    gc.canMove(unit.id(), dir)) {
                                // mark your current position as yours so other rangers don't try to move there while you're gone
                                if (isGoodPosition[myY][myX]) {
                                    isGoodPositionTaken[myY][myX] = true;
                                } else {
                                    System.out.println("ERROR: expected current position to be a good position but it wasn't...");
                                }
                                doMoveRobot(unit, dir);
                                doneMove = true;
                                break;
                            }
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

                if (raceToMars && !rocketsReady.isEmpty()) {
                    doneMove = true;

                    tryToMoveToRocket(unit);
                }

                // check if current location is good position
                if (!doneMove && isGoodPosition[myY][myX]) {
                    doneMove = true;

                    // if there's an closer (or equally close) adjacent good position
                    // and the next attack wave isn't too soon, then move to it
                    boolean foundMove = false;
                    if (roundNum % 5 < 3) {
                        shuffleDirOrder();
                        for (int i = 0; i < 8; i++) {
                            Direction dir = directions[randDirOrder[i]];
                            MapLocation loc = unit.location().mapLocation().add(dir);
                            if (0 <= loc.getY() && loc.getY() < height &&
                                    0 <= loc.getX() && loc.getX() < width &&
                                    isGoodPosition[loc.getY()][loc.getX()] &&
                                    manhattanDistanceToNearestEnemy[loc.getY()][loc.getX()] <= manhattanDistanceToNearestEnemy[myY][myX] &&
                                    gc.canMove(unit.id(), dir)) {
                                //System.out.println("moving to other good location!!");
                                foundMove = true;
                                doMoveRobot(unit, dir);
                                isGoodPositionTaken[loc.getY()][loc.getX()] = true;
                                break;
                            }
                        }
                    }

                    if (!foundMove) {
                        // otherwise, stay on your current position
                        if (!isGoodPositionTaken[myY][myX]) {
                            isGoodPositionTaken[myY][myX] = true;
                        }
                    }
                }

                // otherwise, try to move to good position
                MapLocation closestFreeGoodPosition = getClosestFreeGoodPosition(myY, myX);
                if (!doneMove && closestFreeGoodPosition != null) {
                    int y = closestFreeGoodPosition.getY();
                    int x = closestFreeGoodPosition.getX();
                    isGoodPositionTaken[y][x] = true;
                    //System.out.println("I'm at " + unit.location().mapLocation().toString() + " and I'm moving to good location " + closestFreeGoodPosition.toString());
                    tryMoveToLoc(unit, dis[closestFreeGoodPosition.getY()][closestFreeGoodPosition.getX()]);
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

    public static void runKnight(Unit unit) {
        boolean doneMove = false;

        if (unit.location().isOnMap()) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), unit.attackRange());
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.team() != unit.team() && gc.canAttack(unit.id(), other.id())) {
                    doneMove = true;
                    if (gc.isAttackReady(unit.id())) {
                        gc.attack(unit.id(), other.id());
                    }
                }
            }
        }

        if (!doneMove && unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
            bfs(unit.location().mapLocation(), 1);

            if (!doneMove) {
                // try to move towards closest enemy unit
                // If the closest enemy is within some arbitrary distance, attack them
                // Currently set to ranger attack range
                // Warning: Constant used that might change!
                /*
                FOR PERFORMANCE REASONS BFS CLOSEST ENEMY DOES NOT WORK
                if (bfsClosestEnemy != null && unit.location().mapLocation().distanceSquaredTo(bfsClosestEnemy) <= RangerAttackRange) {
                    //System.out.println("My location = " + unit.location().mapLocation().toString() + ", closest enemy loc = " + bfsClosestEnemy.toString());
                    doneMove = true;
                    Direction dir = directions[bfsDirectionIndexTo[bfsClosestEnemy.getY()][bfsClosestEnemy.getX()]];
                    if (gc.canMove(unit.id(), dir)) {
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

    public static void runHealer(Unit unit) {
        boolean healDone = tryToHeal(unit);
        boolean moveDone = false;

        // move to damaged friendly healable unit
        if (!healDone) {
            moveDone = tryMoveToLoc(unit, distToDamagedFriendlyHealableUnit);
        } else {
            // if you're too close, try to move back
            if (unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
                MapLocation loc = unit.location().mapLocation();
                if (attackDistanceToEnemy[loc.getY()][loc.getX()] <= 100) {
                    int best = -1, bestDist = -1;
                    for (int i = 0; i < 8; i++) {
                        if (gc.canMove(unit.id(), directions[i])) {
                            MapLocation other = loc.add(directions[i]);
                            int attackDist = attackDistanceToEnemy[other.getY()][other.getX()];
                            if (best == -1 || attackDist > bestDist) {
                                bestDist = attackDist;
                                best = i;
                            }
                        }
                    }
                    if (best != -1) {
                        //System.out.println("healer moving back!");
                        moveDone = true;
                        doMoveRobot(unit, directions[best]);
                    }
                }
            }
        }

        if (raceToMars && !moveDone) {
            moveDone = tryToMoveToRocket(unit);
        }

        if (!healDone) {
            tryToHeal(unit);
        }
    }

    public static boolean tryToMoveToRocket (Unit unit ) {
        // try to get to a fucking rocket as soon as possible

        if (rocketsReady.isEmpty()) {
            return false;
        }

        int fromY = unit.location().mapLocation().getY();
        int fromX = unit.location().mapLocation().getX();

        int minDist = -1;
        int bestToY = -1;
        int bestToX = -1;

        for (Unit u : rocketsReady) {
            int toY = u.location().mapLocation().getY();
            int toX = u.location().mapLocation().getX();

            if (minDist == -1 || dis[toY][toX][fromY][fromX] < minDist) {
                minDist = dis[toY][toX][fromY][fromX];
                bestToY = toY;
                bestToX = toX;
            }
        }

        return tryMoveToLoc(unit, dis[bestToY][bestToX]);
    }

    public static int getTendency(Unit unit) {
        if (!tendency.containsKey(unit.id())) {
            if (attackLocs.isEmpty()) {
                tendency.put(unit.id(), rand.nextInt(8));
            } else if (unit.location().isOnMap()) {
                tendency.put(unit.id(), getDirIndex(unit.location().mapLocation().directionTo(attackLocs.get(attackLocs.size()-1))));
                //System.out.println("Trying to attack enemy starting location!");
            } else {
                // in garrison or space or something
                // don't set tendency yet and just return something random
                return rand.nextInt(8);
            }
        }
        return tendency.get(unit.id());
    }

    public static void updateTendency(int id, int changeChance) {
        if (!tendency.containsKey(id)) {
            return;
        }
        // assert tendency has id as key
        int k = tendency.get(id);
        int change = rand.nextInt(100);
        if (change < changeChance / 2) {
            k = (k + 1) % 8;
        } else if (change < changeChance) {
            k = (k + 7) % 8;
        }
        tendency.put(id, k);
    }

    public static int moveDistance(MapLocation a, MapLocation b) {
        return dis[a.getY()][a.getX()][b.getY()][b.getX()];
    }

    private static void allPairsShortestPath () {
        for (int sy = 0; sy < height; sy++) {
            for (int sx = 0; sx < width; sx++) {

                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    // large number so that we don't accidentally think that unreachable squares are the closest squares
                    dis[sy][sx][y][x] = 999;
                }

                Queue<SimpleState> queue = new LinkedList<>();
                queue.add(new SimpleState(sy, sx));
                dis[sy][sx][sy][sx] = 0;

                while (!queue.isEmpty()) {
                    SimpleState cur = queue.peek();
                    queue.remove();

                    for (int d = 0; d < 8; d++) {
                        int ny = cur.y + dy[d];
                        int nx = cur.x + dx[d];
                        if (0 <= ny && ny < height && 0 <= nx && nx < width && isPassable[ny][nx] && dis[sy][sx][ny][nx] == 999) {
                            dis[sy][sx][ny][nx] = dis[sy][sx][cur.y][cur.x] + 1;
                            queue.add(new SimpleState(ny, nx));
                        }
                    }
                }

            }
        }
    }

    private static boolean tryMoveToLoc (Unit unit, int distArray[][]) {

        if (!unit.location().isOnMap() || !gc.isMoveReady(unit.id())) {
            return false;
        }

        int myY = unit.location().mapLocation().getY();
        int myX = unit.location().mapLocation().getX();
        int bestDist = -1;
        int bestDir = -1;

        for (int i = 0; i < 8; i++) {
            if (gc.canMove(unit.id(), directions[i])) {
                int tmpDist = distArray[myY + dy[i]][myX + dx[i]];
                if (bestDist == -1 || tmpDist < bestDist) {
                    bestDist = tmpDist;
                    bestDir = i;
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
            if (rand.nextInt(100) < 50) {
                doMoveRobot(unit, directions[bestDir]);
            }
        } else {
            if (rand.nextInt(100) < 20) {
                doMoveRobot(unit, directions[bestDir]);
            }
        }
        return true;
    }

    // Required: you must be able to sense every square around loc
    public static int getSpaceAround(MapLocation loc) {
        int space = 0;
        for (int i = 0; i < 8; i++) {
            MapLocation other = loc.add(directions[i]);
            // TODO: change hasUnitAtLocation && unit.team == my team to be less dangerous
            // TODO: because hasFriendlyUnit[][] might be incorrect T_T
            if (0 <= other.getY() && other.getY() < height && 0 <= other.getX() && other.getX() < width &&
                    isPassable[other.getY()][other.getX()] &&
                    !(gc.hasUnitAtLocation(other) && isFriendlyStructure(gc.senseUnitAtLocation(other)))) {
                space++;
            }
        }
        return space;
    }

    public static boolean isFriendlyStructure(Unit unit) {
        return unit.team() == gc.team() && (unit.unitType() == UnitType.Factory || unit.unitType() == UnitType.Rocket);
    }

    // Required: loc must be a valid location (ie not out of bounds)
    public static boolean isNextToBuildingBlueprint(MapLocation loc) {
        for (int i = 0; i < 8; i++) {
            MapLocation other = loc.add(directions[i]);
            if (gc.hasUnitAtLocation(other)) {
                Unit unit = gc.senseUnitAtLocation(other);
                if (unit.team() == gc.team() &&
                        (unit.unitType() == UnitType.Factory || unit.unitType() == UnitType.Rocket)
                        && unit.structureIsBuilt() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean moveToAttackLocs (Unit unit) {
        if (attackLocs.isEmpty()) {
            return false;
        }
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
        return tryMoveToLoc(unit, dis[bestLoc.getY()][bestLoc.getX()]);
    }

    public static void moveToTendency(Unit unit) {
        Direction moveDir = directions[getTendency(unit)];
        if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), moveDir)) {
            doMoveRobot(unit, moveDir);
            updateTendency(unit.id(), 6);
        } else {
            updateTendency(unit.id(), 100);
        }
    }

    // returns the priority of the unit for a range to attack
    public static int getRangerAttackPriority(Unit unit){
        if (unit.unitType()==UnitType.Rocket || unit.unitType()==UnitType.Factory){
            return 0;
        }
        if (unit.unitType()==UnitType.Worker){
            return 1;
        }
        return 2;
    }

    // returns whether the unit attacked
    public static boolean rangerTryToAttack(Unit unit) {
        if (unit.location().isOnMap() && gc.isAttackReady(unit.id())) {
            // this radius needs to be at least 1 square larger than the ranger's attack range
            // because the ranger might move, but the ranger's unit.location().mapLocation() won't update unless we
            // call again. So just query a larger area around the ranger's starting location.
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 99);
            int whichToAttack = -1, whichToAttackHealth = 999, whichToAttackPriority = -1;
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.team() != unit.team() && gc.canAttack(unit.id(), other.id())) {
                    int health = (int)other.health();
                    int attackPriority = (int) getRangerAttackPriority(other);

                    if (whichToAttack == -1 || (attackPriority>whichToAttackPriority) || (attackPriority==whichToAttackPriority && health < whichToAttackHealth)) {
                        whichToAttack = i;
                        whichToAttackPriority = attackPriority;
                        whichToAttackHealth = health;
                    }
                }
            }
            if (whichToAttack != -1) {
                gc.attack(unit.id(), units.get(whichToAttack).id());
                return true;
            }
        }
        return false;
    }

    // returns whether the healer successfully healed
    public static boolean tryToHeal(Unit unit) {
        if (unit.location().isOnMap() && gc.isHealReady(unit.id())) {
            // this radius needs to be larger than the healer's heal range in case the healer moves
            // (because the Unit object is cached and the location() won't update)
            VecUnit units = gc.senseNearbyUnitsByTeam(unit.location().mapLocation(), 80, gc.team());
            int whichToHeal = -1, whichToHealHealthMissing = -1;
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (gc.canHeal(unit.id(), other.id())) {
                    int healthMissing = (int)(other.maxHealth() - other.health());
                    if (whichToHeal == -1 || healthMissing > whichToHealHealthMissing) {
                        whichToHeal = i;
                        whichToHealHealthMissing = healthMissing;
                    }
                }
            }
            if (whichToHeal != -1 && whichToHealHealthMissing > 0) {
                //System.out.println("Healer says: 'I'm being useful!'");
                gc.heal(unit.id(), units.get(whichToHeal).id());
                return true;
            }
        }
        return false;
    }

    static void checkForEnemyUnits (VecUnit allUnits) {
        // remove things that are incorrect
        ArrayList<MapLocation> newAttackLocs = new ArrayList<MapLocation>();
        for (MapLocation i: attackLocs) {
            if (!gc.canSenseLocation(i))
                newAttackLocs.add(i);
            else if (gc.senseNearbyUnitsByTeam(i, 0, enemyTeam).size() > 0)
                newAttackLocs.add(i);
        }
        attackLocs.clear();
        attackLocs.addAll(newAttackLocs);
        boolean cleared = false;
        for (int i = 0;i < (int) allUnits.size();i++) if (allUnits.get(i).team() == enemyTeam)
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

    public static void calculateManhattanDistancesToClosestEnemies(VecUnit allUnits) {
        // multi-source bfs from all enemies

        Queue<SimpleState> queue = new LinkedList<>();

        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            // see note about what this default value should be in getUnitOrderPriority()
            manhattanDistanceToNearestEnemy[y][x] = ManhattanDistanceNotSeen;
        }

        for (int i = 0; i < allUnits.size(); i++) {
            Unit unit = allUnits.get(i);
            if (unit.team() != gc.team() && unit.location().isOnMap()) {
                MapLocation loc = unit.location().mapLocation();
                manhattanDistanceToNearestEnemy[loc.getY()][loc.getX()] = 0;
                queue.add(new SimpleState(loc.getY(), loc.getX()));
            }
        }

        while (!queue.isEmpty()) {
            SimpleState cur = queue.peek();
            queue.remove();

            // use d += 2 because we're doing manhattan distance
            for (int d = 0; d < 8; d += 2) {
                int ny = cur.y + dy[d];
                int nx = cur.x + dx[d];
                if (0 <= ny && ny < height && 0 <= nx && nx < width &&
                        manhattanDistanceToNearestEnemy[ny][nx] == ManhattanDistanceNotSeen) {
                    manhattanDistanceToNearestEnemy[ny][nx] = manhattanDistanceToNearestEnemy[cur.y][cur.x] + 1;
                    queue.add(new SimpleState(ny, nx));
                }
            }
        }
    }

    public static void removeTypeToFriendlyUnitCount (UnitType unitType) {
        if (unitType == UnitType.Worker) {
            numWorkers--;
        } else if (unitType == UnitType.Knight) {
            numKnights--;
        } else if (unitType == UnitType.Ranger) {
            numRangers--;
        } else if (unitType == UnitType.Mage) {
            numMages--;
        } else if (unitType == UnitType.Healer) {
            numHealers--;
        }
    }

    public static void addTypeToFriendlyUnitCount (UnitType unitType) {
        if (unitType == UnitType.Worker) {
            numWorkers++;
        } else if (unitType == UnitType.Knight) {
            numKnights++;
        } else if (unitType == UnitType.Ranger) {
            numRangers++;
        } else if (unitType == UnitType.Mage) {
            numMages++;
        } else if (unitType == UnitType.Healer) {
            numHealers++;
        }
    }

    public static int getClosestFreeGoodPositionCandidates[] = new int[50*50 + 5];
    public static MapLocation getClosestFreeGoodPosition(int y, int x) {
        int bestDis = 999;
        int candidates = 0;
        for (int i = 0; i < numGoodPositions; i++) {
            int curY = goodPositionsYList[i];
            int curX = goodPositionsXList[i];
            if (!isGoodPositionTaken[curY][curX] && !hasFriendlyUnit[curY][curX]) {
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
            return null;
        } else {
            int best = getClosestFreeGoodPositionCandidates[rand.nextInt(candidates)];
            return new MapLocation(myPlanet, goodPositionsXList[best], goodPositionsYList[best]);
        }
    }

    static void tryToLoadRocket (Unit unit) {

        //possibly slow performance
        MapLocation curLoc = unit.location().mapLocation();

        VecUnit loadableUnits = gc.senseNearbyUnitsByTeam(unit.location().mapLocation(), unit.visionRange(), myTeam);

        // bring along anything
        for (long i = 0;i < loadableUnits.size();i++) {
            if (gc.canLoad(unit.id(), loadableUnits.get(i).id())) {

                if (loadableUnits.get(i).unitType() == UnitType.Worker && numWorkers <= 2) {
                    continue;
                }

                removeTypeToFriendlyUnitCount(loadableUnits.get(i).unitType());
                gc.load(unit.id(), loadableUnits.get(i).id());

                // @JERRY : I don't know what this does
                // loadedUnits.add(unit.id());
            }
        }
    }

    // finds the shortest distance to all squares from an array of starting locations
    // unreachable squares get distance MultisourceBfsUnreachableMax
    // dangerous squares get MultisourceBfsUnreachableMax + 300 - sq distance to enemy
    // NOTE: DOES NOT allow movement through friendly units
    // NOTE: DOES NOT allow movement through dangerous squares (as defined by isSquareDangerous[][])
    public static void multisourceBfsAvoidingUnitsAndDanger (ArrayList<SimpleState> startingLocs, int resultArr[][]) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            resultArr[y][x] = MultisourceBfsUnreachableMax;
            if (isSquareDangerous[y][x]) {
                resultArr[y][x] = MultisourceBfsUnreachableMax + 300 - attackDistanceToEnemy[y][x];
            }
        }
        Queue<SimpleState> queue = new LinkedList<>();

        for (int i = 0; i < startingLocs.size(); i++) {
            SimpleState loc = startingLocs.get(i);
            resultArr[loc.y][loc.x] = 0;
            queue.add(new SimpleState(loc.y, loc.x));
        }

        while (!queue.isEmpty()) {
            SimpleState cur = queue.remove();

            for (int d = 0; d < 8; d++) {
                int ny = cur.y + dy[d];
                int nx = cur.x + dx[d];
                if (0 <= ny && ny < height && 0 <= nx && nx < width &&
                        isPassable[ny][nx] && !isSquareDangerous[ny][nx] && resultArr[ny][nx] == MultisourceBfsUnreachableMax) {
                    resultArr[ny][nx] = resultArr[cur.y][cur.x] + 1;

                    // if the next square has a friendly unit, mark down the distance to that square but don't continue the bfs
                    // through that square
                    if (!hasFriendlyUnit[ny][nx]) {
                        queue.add(new SimpleState(ny, nx));
                    }
                }
            }
        }
    }

    public static boolean isFighterUnitType(UnitType unitType) {
        return unitType == UnitType.Ranger ||
                unitType == UnitType.Knight ||
                unitType == UnitType.Mage ||
                unitType == UnitType.Healer;
    }

    public static boolean isHealableUnitType(UnitType unitType) {
        return unitType == UnitType.Ranger ||
                unitType == UnitType.Knight ||
                unitType == UnitType.Mage ||
                unitType == UnitType.Healer ||
                unitType == UnitType.Worker;
    }

    public static boolean doReplicate(Unit unit) {
        // try to replicate next to currently building factory
        shuffleDirOrder();
        Direction replicateDir = Direction.Center;
        for (int i = 0; i < 8; i++) {
            Direction dir = directions[randDirOrder[i]];
            if (gc.canReplicate(unit.id(), dir) &&
                    isNextToBuildingBlueprint(unit.location().mapLocation().add(dir))) {
                replicateDir = dir;
                break;
            }
        }
        // otherwise, replicate wherever
        if (replicateDir == Direction.Center) {
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[randDirOrder[i]];
                if (gc.canReplicate(unit.id(), dir)) {
                    replicateDir = dir;
                    break;
                }
            }
        }
        if (replicateDir != Direction.Center) {
            gc.replicate(unit.id(), replicateDir);
            MapLocation loc = unit.location().mapLocation().add(replicateDir);
            allMyUnits.add(gc.senseUnitAtLocation(loc));
            hasFriendlyUnit[loc.getY()][loc.getX()] = true;
            numWorkers++;
            return true;
        }
        return false;
    }

    public static boolean doBlueprint(Unit unit, UnitType toBlueprint) {
        shuffleDirOrder();
        int best = -1, bestSpace = -1;

        if (toBlueprint == UnitType.Factory) {
            // Find blueprint direction such that the factory will have the most free space around it
            for (int i = 0; i < 8; i++) {
                if (gc.canBlueprint(unit.id(), toBlueprint, directions[randDirOrder[i]])) {
                    int space = getSpaceAround(unit.location().mapLocation().add(directions[randDirOrder[i]]));
                    if (space > bestSpace) {
                        bestSpace = space;
                        best = i;
                    }
                }
            }
        } else {
            // TODO: decide if we want Rockets to have minimal space like factories (see above)
            // currently this runs the same code as for Factory
            for (int i = 0; i < 8; i++) {
                if (gc.canBlueprint(unit.id(), toBlueprint, directions[randDirOrder[i]])) {
                    int space = getSpaceAround(unit.location().mapLocation().add(directions[randDirOrder[i]]));
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

            gc.blueprint(unit.id(), toBlueprint, dir);
            MapLocation loc = unit.location().mapLocation().add(dir);
            hasFriendlyUnit[loc.getY()][loc.getX()] = true;
            Unit other = gc.senseUnitAtLocation(loc);

            if (toBlueprint == UnitType.Factory) {
                factoriesBeingBuilt.add(other);
                numFactoryBlueprints++;
            } else {
                rocketsBeingBuilt.add(other);
                numRocketBlueprints++;
            }

            return true;
        }

        return false;
    }

    public static boolean doBuild(Unit unit) {
        VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
        for (int i = 0; i < units.size(); i++) {
            Unit other = units.get(i);

            // only look at factories and rockets
            if (other.unitType() != UnitType.Factory && other.unitType() != UnitType.Rocket) {
                continue;
            }

            if (gc.canBuild(unit.id(), other.id())) {
                gc.build(unit.id(), other.id());

                // If it's complete, remove it from the factoriesBeingBuilt list
                // need to re-sense the unit to get the updated structureIsBuilt() value
                if (gc.senseUnitAtLocation(other.location().mapLocation()).structureIsBuilt() != 0) {
                    //System.out.println("Finished a factory!");
                    boolean foundIt = false;

                    if (other.unitType() == UnitType.Factory) {
                        for (int j = 0; j < factoriesBeingBuilt.size(); j++) {
                            if (factoriesBeingBuilt.get(j).location().mapLocation().equals(other.location().mapLocation())) {
                                factoriesBeingBuilt.remove(j);
                                foundIt = true;
                                break;
                            }
                        }
                    } else {
                        for (int j = 0; j < rocketsBeingBuilt.size(); j++) {
                            if (rocketsBeingBuilt.get(j).location().mapLocation().equals(other.location().mapLocation())) {
                                rocketsReady.add(other);
                                rocketsBeingBuilt.remove(j);
                                foundIt = true;
                                break;
                            }
                        }
                    }
                    if (!foundIt) {
                        System.out.println("ERROR: Structure was expected in ___BeingBuilt, but was missing!");
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isEnoughResourcesNearby(Unit unit) {
        if (!unit.location().isOnMap()) {
            return false;
        }

        MapLocation loc = unit.location().mapLocation();
        int locY = loc.getY(), locX = loc.getX();

        int nearbyKarbonite = 0;
        // nearby workers includes yourself
        int nearbyWorkers = 0;

        for (int y = Math.max(locY - IsEnoughResourcesNearbySearchDist, 0); y <= Math.min(locY + IsEnoughResourcesNearbySearchDist, height - 1); y++) {
            for (int x = Math.max(locX - IsEnoughResourcesNearbySearchDist, 0); x <= Math.min(locX + IsEnoughResourcesNearbySearchDist, width - 1); x++) {
                if (dis[locY][locX][y][x] <= IsEnoughResourcesNearbySearchDist) {
                    nearbyKarbonite += lastKnownKarboniteAmount[y][x];
                }
                MapLocation otherLoc = new MapLocation(gc.planet(), x, y);
                if (gc.hasUnitAtLocation(otherLoc)) {
                    Unit otherUnit = gc.senseUnitAtLocation(otherLoc);
                    if (otherUnit.team() == gc.team() && otherUnit.unitType() == UnitType.Worker) {
                        nearbyWorkers++;
                    }
                }
            }
        }

        //System.out.println("worker at " + loc.toString() + " considering replicating with " + nearbyKarbonite + " karbonite nearby and " + nearbyWorkers + " nearby workers");
        return nearbyKarbonite > 25 * (nearbyWorkers + 1);
    }

    public static boolean bfsTowardsBlueprint(Unit unit) {
        int dist = 4;

        MapLocation loc = unit.location().mapLocation();
        int locY = loc.getY();
        int locX = loc.getX();

        for (int y = Math.max(locY - dist, 0); y <= Math.min(locY + dist, height - 1); y++) {
            for (int x = Math.max(locX - dist, 0); x <= Math.min(locX + dist, width - 1); x++) {
                bfsTowardsBlueprintDist[y][x] = -1;
            }
        }

        Queue<BfsState> queue = new LinkedList<>();

        queue.add(new BfsState(locY, locX, DirCenterIndex));
        bfsTowardsBlueprintDist[locY][locX] = 0;

        Direction dirToMove = Direction.Center;

        while (!queue.isEmpty()) {
            BfsState cur = queue.remove();


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
                            queue.add(new BfsState(ny, nx, cur.startingDir));
                        } else {
                            // check if the square is a blueprint
                            MapLocation nextLoc = new MapLocation(gc.planet(), nx, ny);
                            if (gc.hasUnitAtLocation(nextLoc)) {
                                Unit other = gc.senseUnitAtLocation(nextLoc);
                                if (other.team() == gc.team() &&
                                        (other.unitType() == UnitType.Factory || other.unitType() == UnitType.Rocket)
                                        && other.structureIsBuilt() == 0) {
                                    dirToMove = directions[cur.startingDir];
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (dirToMove != Direction.Center) {
                // found blueprint
                break;
            }
        }

        if (dirToMove != Direction.Center) {
            doMoveRobot(unit, dirToMove);
            //System.out.println("robot at " + unit.location().mapLocation() + " found nearby blueprint");
            return true;
        }

        //System.out.println("robot at " + unit.location().mapLocation() + " DIDN'T find nearby blueprint");
        return false;
    }
}
