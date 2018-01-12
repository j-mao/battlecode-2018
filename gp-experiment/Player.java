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
    // to random shuffle the directions
    private static int randDirOrder[];

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

            initTurn();

            // VecUnit is a class that you can think of as similar to ArrayList<Unit>, but immutable.
            VecUnit units = gc.myUnits();
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

    private static void initTurn() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hasFriendlyUnit[y][x] = false;
            }
        }
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
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

    private static Direction bfs(MapLocation from, MapLocation to) {
        int fromY = from.getY(), fromX = from.getX();
        int toY = to.getY(), toX = to.getX();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) bfsSeen[y][x] = false;
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

            if (cur.y == toY && cur.x == toX) {
                return directions[cur.startingDir];
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

        return Direction.Center;
    }

    private static void runWorker(Unit unit) {
        boolean done = false;

        if (!done) {
            VecUnit units = gc.senseNearbyUnits(unit.location().mapLocation(), 2);
            for (int i = 0; i < units.size(); i++) {
                Unit other = units.get(i);
                if (other.unitType() == UnitType.Factory && gc.canBuild(unit.id(), other.id())) {
                    gc.build(unit.id(), other.id());
                    done = true;
                    break;
                }
            }
        }

        // looks like hasUnitAtLocation is bugged?
        if (false && !done) {
            // try build blueprint
            for (int i = 0; i < 8; i++) {
                MapLocation loc = unit.location().mapLocation().add(directions[i]);
                if (false && gc.canSenseLocation(loc) && gc.hasUnitAtLocation(loc)) {
                    Unit other = gc.senseUnitAtLocation(loc);
                    if (gc.canBuild(unit.id(), other.id())) {
                        gc.build(unit.id(), other.id());
                        done = true;
                        break;
                    }
                }
            }
        }

        if (!done) {
            Direction dir = directions[rand.nextInt(8)];

            // Most methods on gc take unit IDs, instead of the unit objects themselves.
            if (gc.isMoveReady(unit.id()) && gc.canMove(unit.id(), dir)) {
                doMoveRobot(unit, dir);
            }

            if (gc.canBlueprint(unit.id(), UnitType.Factory, dir)) {
                gc.blueprint(unit.id(), UnitType.Factory, dir);
            }
        }
    }

    private static void runFactory(Unit unit) {
        if (gc.canProduceRobot(unit.id(), UnitType.Ranger)) {
            gc.produceRobot(unit.id(), UnitType.Ranger);
        }

        for (int j = 0; j < 8; j++) {
            Direction unloadDir = directions[j];
            if (gc.canUnload(unit.id(), unloadDir)) {
                gc.unload(unit.id(), unloadDir);
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
                    MapLocation bestLoc = attackLocs.get(0);
                    for (int i = 1; i < attackLocs.size(); i++) {
                        if (moveDistance(unit.location().mapLocation(), attackLocs.get(i)) <
                                moveDistance(unit.location().mapLocation(), bestLoc)) {
                            bestLoc = attackLocs.get(i);
                        }
                    }
                    System.out.println("doing bfs");
                    moveDir = bfs(unit.location().mapLocation(), bestLoc);
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