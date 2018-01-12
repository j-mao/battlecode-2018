// import the API.
// See xxx for the javadocs.
import bc.*;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;

public class Player {

    private static int dy[] = {1,1,0,-1,-1,-1,0,1,0};
    private static int dx[] = {0,1,1,1,0,-1,-1,-1,0};

    private static int DirCenterIndex = 8; // index 8 corresponds to Direction.Center

    static class BfsState {
        public int y, x, startingDir;

        public BfsState(int yy, int xx, int sd) {
            y = yy;
            x = xx;
            startingDir = sd;
        }
    }

    private static GameController gc = new GameController();
    private static Random rand = new Random();
    // Direction is a normal java enum.
    private static Direction[] directions = Direction.values();
    private static Map<Integer, Integer> tendency = new HashMap<Integer, Integer>();
    private static ArrayList<MapLocation> attackLocs = new ArrayList<MapLocation>();
    private static int width, height;
    private static boolean isPassable[][];
    private static boolean hasFriendlyUnit[][];
    // temporary seen array
    private static boolean bfsSeen[][];
    // which direction you should move to reach each square, according to the bfs
    private static int bfsDirectionIndexTo[][];
    // to random shuffle the directions
    private static int randDirOrder[];
    // factories that are currently blueprints
    private static ArrayList<Unit> factoriesBeingBuilt = new ArrayList<Unit>();
    private static int workerCount;

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
        System.out.println("loc: " + loc + ", one step to the Northwest: " + loc.add(Direction.Northwest));
        System.out.println("loc x: " + loc.getX());

        initialize();

        for (int i = 0; i < 9; i++) {
            System.out.println("dir " + i + " is " + directions[i].toString());
        }

        // One slightly weird thing: some methods are currently static methods on a static class called bc.
        // This will eventually be fixed :/
        System.out.println("Opposite of " + Direction.North + ": " + bc.bcDirectionOpposite(Direction.North));

        while (true) {
            System.out.println("Current round: " + gc.round());

            VecUnit units = gc.myUnits();
            initTurn(units);

            // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);

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
                }
            }
            // Submit the actions we've done, and wait for our next turn.
            gc.nextTurn();
        }
    }

    private static int getDirIndex(Direction dir) {
        // TODO: make this faster?
        for (int i = 0; i < 8; i++) {
            if (directions[i] == dir) {
                return i;
            }
        }
        System.out.println("ERROR: Couldn't find dir index for: " + dir.toString());
        return 0;
    }

    private static void initialize() {
        PlanetMap earthMap = gc.startingMap(Planet.Earth);
        width = (int)earthMap.getWidth();
        height = (int)earthMap.getHeight();
        isPassable = new boolean[height][width];
        hasFriendlyUnit = new boolean[height][width];
        bfsSeen = new boolean[height][width];
        bfsDirectionIndexTo = new int[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            System.out.println("checking if there's passable terrain at " + new MapLocation(gc.planet(), x, y).toString());
            //System.out.println("is passable at y = " + y + ", x = " + x + " is " + earthMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)));
            isPassable[y][x] = earthMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)) != 0;
        }
        VecUnit units = earthMap.getInitial_units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.team() != gc.team()) {
                System.out.println("Adding attack location " + unit.location().mapLocation().toString());
                attackLocs.add(unit.location().mapLocation());
            }
        }
        randDirOrder = new int[8];
        for (int i = 0; i < 8; i++) {
            randDirOrder[i] = i;
        }
    }

    private static void initTurn(VecUnit myUnits) {
        factoriesBeingBuilt.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            Unit unit = myUnits.get(i);
            if (unit.unitType() == UnitType.Factory && unit.structureIsBuilt() == 0) {
                factoriesBeingBuilt.add(unit);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hasFriendlyUnit[y][x] = false;
            }
        }
        workerCount = 0;
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            // Note: This loops through friendly AND enemy units!
            Unit unit = units.get(i);
            if (unit.team() == gc.team() && unit.unitType() == UnitType.Worker) {
                workerCount++;
            }
            if (unit.team() == gc.team() && unit.location().isOnMap()) {
                MapLocation loc = unit.location().mapLocation();
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
            }
        }
    }

    private static void doMoveRobot(Unit unit, Direction dir) {
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

    private static void shuffleDirOrder() {
        for (int i = 7; i >= 0; i--) {
            int j = rand.nextInt(i+1);
            int tmp = randDirOrder[j];
            randDirOrder[j] = randDirOrder[i];
            randDirOrder[i] = tmp;
        }
    }

    // bfs to all squares
    // store how close you want to get to your destination in howClose (distance = #moves distance, not Euclidean distance)
    // stores the direction you should move to be within howClose of each square
    private static void bfs(MapLocation from, int howClose) {
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

    private static void runWorker(Unit unit) {
        boolean doneAction = false;
        boolean doneMovement = false;

        // try to replicate
        // TODO: limit to 8 workers
        // TODO: only replicate if there is enough money
        // TODO: improve metric of "enough money"
        System.out.println("ability cooldown = " + unit.abilityCooldown() + ", heat = " + unit.abilityHeat());
        // check we don't have too many workers, and that we have enough money for a factory
        // TODO: make this logic better. e.g. we might need money for a factory in the future
        // TODO: Find out where cost constants are and replay this "100 + 15" with those xd.
        if (workerCount < 8 && gc.karbonite() > 100 + 15) {
            shuffleDirOrder();
            for (int i = 0; i < 8; i++) {
                if (gc.canReplicate(unit.id(), directions[randDirOrder[i]])) {
                    gc.replicate(unit.id(), directions[randDirOrder[i]]);
                    // TODO: add created units to units list so they can do something immediately on the turn they're created
                    MapLocation loc = unit.location().mapLocation().add(directions[randDirOrder[i]]);
                    hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                    // TODO: test on a rectangular map to make sure I didn't mess up any y, x orders anywhere
                    doneAction = true;
                    break;
                }
            }
        }

        // try to build an adjacent factory blueprint
        if (!doneAction) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.unitType() == UnitType.Factory && gc.canBuild(unit.id(), other.id())) {
                    gc.build(unit.id(), other.id());
                    doneAction = true;
                    doneMovement = true;
                    break;
                }
            }
        }

        // try to blueprint
        if (!doneAction) {
            Direction dir = directions[rand.nextInt(8)];
            if (factoriesBeingBuilt.isEmpty() && gc.canBlueprint(unit.id(), UnitType.Factory, dir)) {
                gc.blueprint(unit.id(), UnitType.Factory, dir);
                MapLocation loc = unit.location().mapLocation().add(dir);
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                doneAction = true;
                doneMovement = true;
                Unit other = gc.senseUnitAtLocation(loc);
                System.out.println("The factory I just built is here: " + other.toString());
                // TODO: continue this
                // TODO: store ArrayList of current factory blueprints and implement workers moving towards them
                // TODO: implement worker replication
                factoriesBeingBuilt.add(gc.senseUnitAtLocation(loc));
            }
        }

        // if there is a factory blueprint somewhere, try to move towards it to help build it
        if (!doneMovement && !factoriesBeingBuilt.isEmpty()) {
            // set done to true so we don't move randomly
            doneMovement = true;

            // bfs to within distance 1 of every square because we want to move within distance 1 of a factory
            bfs(unit.location().mapLocation(), 1);

            // TODO: change this to closest factory
            Unit factory = factoriesBeingBuilt.get(0);
            MapLocation loc = factory.location().mapLocation();
            Direction dir = directions[bfsDirectionIndexTo[loc.getY()][loc.getX()]];
            System.out.println("worker moving to factory in dir " + dir.toString());

            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                doMoveRobot(unit, dir);
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
                    System.out.println("harvesting!");
                    gc.harvest(unit.id(), directions[i]);
                }
            }
        }
    }

    private static void runFactory(Unit unit) {
        if (gc.canProduceRobot(unit.id(), UnitType.Ranger)) {
            System.out.println("PRODUCING ROBOT!!!");
            gc.produceRobot(unit.id(), UnitType.Ranger);
        }

        for (int j = 0; j < 8; j++) {
            Direction unloadDir = directions[j];
            if (gc.canUnload(unit.id(), unloadDir)) {
                gc.unload(unit.id(), unloadDir);
                MapLocation loc = unit.location().mapLocation().add(unloadDir);
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
                // TODO: check everywhere else to make sure hasFriendlyUnits[][] is being correctly maintained.
            }
        }
    }

    private static void runRanger(Unit unit) {
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
                    System.out.println("doing bfs");
                    bfs(unit.location().mapLocation(), 0);
                    moveDir = directions[bfsDirectionIndexTo[bestLoc.getY()][bestLoc.getX()]];
                    System.out.println("got, from dfs, direction " + moveDir.toString());
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

    private static int getTendency(Unit unit) {
        if (!tendency.containsKey(unit.id())) {
            if (attackLocs.isEmpty()) {
                tendency.put(unit.id(), rand.nextInt(8));
            } else if (unit.location().isOnMap()) {
                tendency.put(unit.id(), getDirIndex(unit.location().mapLocation().directionTo(attackLocs.get(attackLocs.size()-1))));
                System.out.println("Trying to attack enemy starting location!");
            } else {
                // in garrison or space or something
                // don't set tendency yet and just return something random
                return rand.nextInt(8);
            }
        }
        return tendency.get(unit.id());
    }

    private static void updateTendency(int id, int changeChance) {
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

    private static int moveDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }
}