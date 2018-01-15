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

    public static int getUnitOrderPriority(Unit unit) {
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
    public static int factoryCount;
    public static ArrayList<Unit> factoriesBeingBuilt = new ArrayList<Unit>();
    public static int workerCount;
    public static int rangerCount;
    public static int healerCount;
    // Store this list of all our units ourselves, so that we can add to it when we create units and use those new units
    // immediately.
    public static Comparator<Unit> unitOrderComparator = new UnitOrderComparator();
    public static PriorityQueue<Unit> allMyUnits = new PriorityQueue<Unit>(5, unitOrderComparator);

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

    public static final int RangerAttackRange = 50;
    // distance you have to be to be one move away from ranger attack range
    // unfortunately it's not actually the ranger's vision range which is 70, it's actually 72...
    public static final int OneMoveFromRangerAttackRange = 72;

    public static void main(String[] args) {

        if (gc.planet() == Planet.Mars) {
            // Skipping Mars for now
            // LUL
            while (true) {
                gc.nextTurn();
            }
        }

        // MapLocation is a data structure you'll use a lot.
        MapLocation loc = new MapLocation(Planet.Earth, 10, 20);
        //System.out.println("loc: " + loc + ", one step to the Northwest: " + loc.add(Direction.Northwest));
        //System.out.println("loc x: " + loc.getX());

        initialize();

        /*for (int i = 0; i < 9; i++) {
            System.out.println("dir " + i + " is " + directions[i].toString());
        }*/

        // One slightly weird thing: some methods are currently static methods on a static class called bc.
        // This will eventually be fixed :/
        //System.out.println("Opposite of " + Direction.North + ": " + bc.bcDirectionOpposite(Direction.North));

        gc.queueResearch(UnitType.Worker);
        gc.queueResearch(UnitType.Ranger);
        gc.queueResearch(UnitType.Healer);
        gc.queueResearch(UnitType.Ranger);
        gc.queueResearch(UnitType.Healer);
        gc.queueResearch(UnitType.Rocket);
        gc.queueResearch(UnitType.Rocket);
        gc.queueResearch(UnitType.Rocket);

        while (true) {
            System.out.println("Current round: " + gc.round());

            // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            VecUnit units = gc.myUnits();
            initTurn(units);

            while (!allMyUnits.isEmpty()) {
                Unit unit = allMyUnits.remove();
                //System.out.println("running " + unit.unitType().toString());

                switch (unit.unitType()) {
                    case Worker:
                        runWorker(unit);
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
                }
            }
            // Submit the actions we've done, and wait for our next turn.
            gc.nextTurn();
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

    public static void initialize() {
        PlanetMap earthMap = gc.startingMap(Planet.Earth);
        width = (int)earthMap.getWidth();
        height = (int)earthMap.getHeight();
        isPassable = new boolean[height][width];
        hasFriendlyUnit = new boolean[height][width];
        bfsSeen = new boolean[height][width];
        bfsDirectionIndexTo = new int[height][width];
        attackDistanceToEnemy = new int[height][width];
        isGoodPosition = new boolean[height][width];
        isGoodPositionTaken = new boolean[height][width];
        manhattanDistanceToNearestEnemy = new int[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            //System.out.println("checking if there's passable terrain at " + new MapLocation(gc.planet(), x, y).toString());
            //System.out.println("is passable at y = " + y + ", x = " + x + " is " + earthMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)));
            isPassable[y][x] = earthMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)) != 0;
        }
        VecUnit units = earthMap.getInitial_units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.team() != gc.team()) {
                //System.out.println("Adding attack location " + unit.location().mapLocation().toString());
                attackLocs.add(unit.location().mapLocation());
            }
        }
        randDirOrder = new int[8];
        for (int i = 0; i < 8; i++) {
            randDirOrder[i] = i;
        }
    }

    public static void initTurn(VecUnit myUnits) {
        factoryCount = 0;
        factoriesBeingBuilt.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            Unit unit = myUnits.get(i);
            if (unit.unitType() == UnitType.Factory) {
                factoryCount++;
                if (unit.structureIsBuilt() == 0) {
                    factoriesBeingBuilt.add(unit);
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hasFriendlyUnit[y][x] = false;
                attackDistanceToEnemy[y][x] = 9999;
                isGoodPosition[y][x] = false;
                isGoodPositionTaken[y][x] = false;
            }
        }
        workerCount = 0;
        rangerCount = 0;
        healerCount = 0;
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            // Note: This loops through friendly AND enemy units!
            Unit unit = units.get(i);
            if (unit.team() == gc.team()) {
                // my team

                if (unit.location().isOnMap()) {
                    MapLocation loc = unit.location().mapLocation();
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                }

                // friendly unit counts
                addToFriendlyUnitCount(unit.unitType());
                if (unit.unitType() == UnitType.Factory && unit.isFactoryProducing() != 0) {
                    addToFriendlyUnitCount(unit.factoryUnitType());
                }
            } else {
                // enemy team

                // calculate closest distances to enemy units
                if (unit.location().isOnMap()) {
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
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (RangerAttackRange < attackDistanceToEnemy[y][x] && attackDistanceToEnemy[y][x] <= OneMoveFromRangerAttackRange) {
                isGoodPosition[y][x] = true;
            }
        }

        allMyUnits.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            allMyUnits.add(myUnits.get(i));
        }

        calculateManhattanDistancesToClosestEnemies(units);
    }

    public static void doMoveRobot(Unit unit, Direction dir) {
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

    public static void shuffleDirOrder() {
        for (int i = 7; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            int tmp = randDirOrder[j];
            randDirOrder[j] = randDirOrder[i];
            randDirOrder[i] = tmp;
        }
    }

    // bfs to all squares
    // store how close you want to get to your destination in howClose (distance = #moves distance, not Euclidean distance)
    // stores bfsClosestKarbonite: closest location which gets you within howClose of karbonite
    // stores bfsClosestEnemy: closest location which is within howClose of an enemy
    // stores bfsClosestFreeGoodPosition: closest good position that hasn't been taken by another unit
    // stores the direction you should move to be within howClose of each square
    public static void bfs(MapLocation from, int howClose) {
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
                    MapLocation loc = new MapLocation(gc.planet(), x, y);
                    if (gc.canSenseLocation(loc)) {
                        if (bfsClosestKarbonite == null && gc.karboniteAt(loc) > 0) {
                            bfsClosestKarbonite = loc;
                        }
                        if (bfsClosestEnemy == null &&
                                loc.distanceSquaredTo(new MapLocation(gc.planet(), cur.x, cur.y)) <= howClose &&
                                gc.hasUnitAtLocation(loc) &&
                                gc.senseUnitAtLocation(loc).team() != gc.team()) {
                            bfsClosestEnemy = loc;
                        }
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

    public static void runWorker(Unit unit) {
        boolean doneAction = false;
        boolean doneMovement = false;

        // try to replicate
        // TODO: limit to 8 workers
        // TODO: only replicate if there is enough money
        // TODO: improve metric of "enough money"
        //System.out.println("ability cooldown = " + unit.abilityCooldown() + ", heat = " + unit.abilityHeat());
        // check we don't have too many workers, and that we have enough money for a factory
        // TODO: make this logic better. e.g. we might need money for a factory in the future
        // TODO: Find out where cost constants are and replay this "100 + 15" with those xd.
        if (workerCount < 7 && gc.karbonite() >= 100 + 15) {
            // try to replicate next to currently building factory
            shuffleDirOrder();
            Direction replicateDir = Direction.Center;
            for (int i = 0; i < 8; i++) {
                Direction dir = directions[randDirOrder[i]];
                if (gc.canReplicate(unit.id(), dir) &&
                        isNextToBuildingFactory(unit.location().mapLocation().add(dir))) {
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
                doneAction = true;
            }
        }

        // try to build an adjacent factory blueprint
        if (!doneAction) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.unitType() == UnitType.Factory && gc.canBuild(unit.id(), other.id())) {
                    gc.build(unit.id(), other.id());
                    // If it's complete, remove it from the factoriesBeingBuilt list
                    // need to re-sense the unit to get the updated structureIsBuilt() value
                    if (gc.senseUnitAtLocation(other.location().mapLocation()).structureIsBuilt() != 0) {
                        //System.out.println("Finished a factory!");
                        boolean foundIt = false;
                        for (int j = 0; j < factoriesBeingBuilt.size(); j++) {
                            if (factoriesBeingBuilt.get(j).location().mapLocation().equals(other.location().mapLocation())) {
                                factoriesBeingBuilt.remove(j);
                                foundIt = true;
                                break;
                            }
                        }
                        if (!foundIt) {
                            System.out.println("ERROR: Factory was expected in factoriesBeingBuilt, but was missing!");
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
                // Find blueprint direction such that the factory will have the most free space around it
                for (int i = 0; i < 8; i++) {
                    if (gc.canBlueprint(unit.id(), UnitType.Factory, directions[randDirOrder[i]])) {
                        int space = getSpaceAround(unit.location().mapLocation().add(directions[randDirOrder[i]]));
                        if (space > bestSpace) {
                            bestSpace = space;
                            best = i;
                        }
                    }
                }
                if (best != -1) {
                    Direction dir = directions[randDirOrder[best]];
                    //System.out.println("blueprinting in direction " + dir.toString() + " with space " + bestSpace);
                    gc.blueprint(unit.id(), UnitType.Factory, dir);
                    MapLocation loc = unit.location().mapLocation().add(dir);
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                    doneAction = true;
                    doneMovement = true;
                    Unit other = gc.senseUnitAtLocation(loc);
                    //System.out.println("The factory I just built is here: " + other.toString());
                    // TODO: continue this
                    // TODO: store ArrayList of current factory blueprints and implement workers moving towards them
                    // TODO: implement worker replication
                    factoriesBeingBuilt.add(gc.senseUnitAtLocation(loc));
                    // TODO: add this factory to allMyUnits list?
                    // TODO: or maybe not because it's not going to do anything anyway? (unless it does some calculation or something)
                }
            }
        }

        // if you're next to a factory don't move
        if (!doneMovement) {
            if (isNextToBuildingFactory(unit.location().mapLocation())) {
                doneMovement = true;
            }
        }

        // if there is a factory blueprint somewhere, and you have less than 3 complete factories, try to move towards it to help build it
        if (!doneMovement && !factoriesBeingBuilt.isEmpty() && factoryCount - factoriesBeingBuilt.size() < 3) {
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
        }

        // otherwise, if you have 3 complete factories, try to move towards minerals
        // TODO: make this >= 3 thing better
        // TODO: ie make it not horrible in special case maps LuL
        if (!doneMovement && factoryCount - factoriesBeingBuilt.size() >= 3) {
            // TODO: unify bfs logic so that you don't unnecessarily call it multiple times
            bfs(unit.location().mapLocation(), 1);

            if (bfsClosestKarbonite != null) {
                Direction dir = directions[bfsDirectionIndexTo[bfsClosestKarbonite.getY()][bfsClosestKarbonite.getX()]];

                //System.out.println("worker moving towards minerals!");
                if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                    doMoveRobot(unit, dir);
                }
            }
        }

        // otherwise move randomly
        if (!doneMovement) {
            Direction dir = directions[rand.nextInt(8)];

            // Most methods on gc take unit IDs, instead of the unit objects themselves.
            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                doMoveRobot(unit, dir);
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
    }

    public static void runFactory(Unit unit) {
        UnitType unitTypeToBuild = UnitType.Ranger;

        if (rangerCount >= 4 * healerCount + 4) {
            unitTypeToBuild = UnitType.Healer;
        }

        if (gc.canProduceRobot(unit.id(), unitTypeToBuild)) {
            //System.out.println("PRODUCING ROBOT!!!");
            gc.produceRobot(unit.id(), unitTypeToBuild);
            // make sure to immediately update unit counts
            addToFriendlyUnitCount(unitTypeToBuild);
        }

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

    public static void runRanger(Unit unit) {
        // try to attack before and after moving
        boolean doneAttack = rangerTryToAttack(unit);

        // remove completed objectives
        removeCompletedAttackLocs(unit);

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
                        // do some move
                        // currently set to move toward attackLoc
                        // TODO: change this. Like... seriously... change this...
                        bfs(unit.location().mapLocation(), 1);
                        if (moveToAttackLocsYouMustCallBfsWithHowCloseSetTo1BeforeCallingThis(unit)) {
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
                if (!doneMove && isGoodPosition[myY][myX]) {
                    doneMove = true;

                    // if there's an closer (or equally close) adjacent good position
                    // and the next attack wave isn't too soon, then move to it
                    boolean foundMove = false;
                    if (gc.round() % 5 < 3) {
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
                bfs(unit.location().mapLocation(), 0);
                if (!doneMove && bfsClosestFreeGoodPosition != null) {
                    int y = bfsClosestFreeGoodPosition.getY();
                    int x = bfsClosestFreeGoodPosition.getX();
                    isGoodPositionTaken[y][x] = true;
                    Direction dir = directions[bfsDirectionIndexTo[y][x]];
                    if (gc.canMove(unit.id(), dir)) {
                        doMoveRobot(unit, dir);
                    }
                    doneMove = true;
                }

                // otherwise move to attack loc
                bfs(unit.location().mapLocation(), 1);
                if (!doneMove && moveToAttackLocsYouMustCallBfsWithHowCloseSetTo1BeforeCallingThis(unit)) {
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

        removeCompletedAttackLocs(unit);

        if (!doneMove && unit.location().isOnMap() && gc.isMoveReady(unit.id())) {
            bfs(unit.location().mapLocation(), 1);

            if (!doneMove) {
                // try to move towards closest enemy unit
                // If the closest enemy is within some arbitrary distance, attack them
                // Currently set to ranger attack range
                // Warning: Constant used that might change!
                if (bfsClosestEnemy != null && unit.location().mapLocation().distanceSquaredTo(bfsClosestEnemy) <= RangerAttackRange) {
                    //System.out.println("My location = " + unit.location().mapLocation().toString() + ", closest enemy loc = " + bfsClosestEnemy.toString());
                    doneMove = true;
                    Direction dir = directions[bfsDirectionIndexTo[bfsClosestEnemy.getY()][bfsClosestEnemy.getX()]];
                    if (gc.canMove(unit.id(), dir)) {
                        doMoveRobot(unit, dir);
                    }
                }
            }

            if (!doneMove) {
                // try to move to attack location
                if (moveToAttackLocsYouMustCallBfsWithHowCloseSetTo1BeforeCallingThis(unit)) {
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

        // move to friendly unit
        if (!healDone) {
            // Kappa. Just move to tendency for now.
            moveToTendency(unit);
        }

        if (!healDone) {
            tryToHeal(unit);
        }
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
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
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
    public static boolean isNextToBuildingFactory(MapLocation loc) {
        for (int i = 0; i < 8; i++) {
            MapLocation other = loc.add(directions[i]);
            if (gc.hasUnitAtLocation(other)) {
                Unit unit = gc.senseUnitAtLocation(other);
                if (unit.team() == gc.team() && unit.unitType() == UnitType.Factory && unit.structureIsBuilt() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void removeCompletedAttackLocs(Unit unit) {
        if (unit.location().isOnMap()) {
            for (int i = attackLocs.size()-1; i >= 0; i--) {
                if (moveDistance(attackLocs.get(i), unit.location().mapLocation()) <= 1) {
                    attackLocs.remove(i);
                }
            }
        }
    }

    public static boolean moveToAttackLocsYouMustCallBfsWithHowCloseSetTo1BeforeCallingThis(Unit unit) {
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
        //System.out.println("doing bfs");
        Direction moveDir = directions[bfsDirectionIndexTo[bestLoc.getY()][bestLoc.getX()]];
        //System.out.println("got, from dfs, direction " + moveDir.toString());
        if (moveDir != Direction.Center && gc.canMove(unit.id(), moveDir)) {
            doMoveRobot(unit, moveDir);
            return true;
        }
        return false;
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

    // returns whether the unit attacked
    public static boolean rangerTryToAttack(Unit unit) {
        if (unit.location().isOnMap() && gc.isAttackReady(unit.id())) {
            // this radius needs to be at least 1 square larger than the ranger's attack range
            // because the ranger might move, but the ranger's unit.location().mapLocation() won't update unless we
            // call again. So just query a larger area around the ranger's starting location.
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 99);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.team() != unit.team() && gc.canAttack(unit.id(), other.id())) {
                    gc.attack(unit.id(), other.id());
                    return true;
                }
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
                System.out.println("Healer says: 'I'm being useful!'");
                gc.heal(unit.id(), units.get(whichToHeal).id());
                return true;
            }
        }
        return false;
    }

    public static void calculateManhattanDistancesToClosestEnemies(VecUnit allUnits) {
        // multi-source bfs from all enemies

        Queue<BfsState> queue = new LinkedList<>();

        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            // see note about what this default value should be in getUnitOrderPriority()
            manhattanDistanceToNearestEnemy[y][x] = ManhattanDistanceNotSeen;
        }

        for (int i = 0; i < allUnits.size(); i++) {
            Unit unit = allUnits.get(i);
            if (unit.team() != gc.team() && unit.location().isOnMap()) {
                MapLocation loc = unit.location().mapLocation();
                manhattanDistanceToNearestEnemy[loc.getY()][loc.getX()] = 0;
                // not using startingDirection so just pass -420
                queue.add(new BfsState(loc.getY(), loc.getX(), -420));
            }
        }

        while (!queue.isEmpty()) {
            BfsState cur = queue.peek();
            queue.remove();

            // use d += 2 because we're doing manhattan distance
            for (int d = 0; d < 8; d += 2) {
                int ny = cur.y + dy[d];
                int nx = cur.x + dx[d];
                if (0 <= ny && ny < height && 0 <= nx && nx < width &&
                        manhattanDistanceToNearestEnemy[ny][nx] == ManhattanDistanceNotSeen) {
                    manhattanDistanceToNearestEnemy[ny][nx] = manhattanDistanceToNearestEnemy[cur.y][cur.x] + 1;
                    queue.add(new BfsState(ny, nx, -420));
                }
            }
        }
    }

    public static void addToFriendlyUnitCount(UnitType unitType) {
        switch (unitType) {
            case Worker:
                workerCount++;
                break;
            case Ranger:
                rangerCount++;
                break;
            case Healer:
                healerCount++;
                break;
        }
    }
}