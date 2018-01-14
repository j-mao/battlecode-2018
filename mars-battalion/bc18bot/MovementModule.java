package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

// use separate files for separate controllers to preserve code sanity
// only global functions are written here
import bc18bot.EarthController;
import bc18bot.MarsController;

public class MovementModule {
    // the controller through which you do everything
    static GameController gc;

    static Random rand = new Random();

    // a list of all possible directions
    // there are 9 in total: 8 + center
    static final Direction[] directions = Direction.values();

    // initial maps
    static PlanetMap EarthMap;
    static PlanetMap MarsMap;
    static PlanetMap myMap;

	static int dy[] = {1,1,0,-1,-1,-1,0,1,0};
    static int dx[] = {0,1,1,1,0,-1,-1,-1,0};

    private static int DirCenterIndex = 8; // index 8 corresponds to Direction.Center

    static class BfsState {
        public int y, x, startingDir;

        public BfsState(int yy, int xx, int sd) {
            y = yy;
            x = xx;
            startingDir = sd;
        }
    }

    // list of factory blueprints
    static ArrayList<Unit> factoriesBeingBuilt = new ArrayList<Unit>();

    static Map<Integer, Integer> tendency = new HashMap<Integer, Integer>();
    static ArrayList<MapLocation> attackLocs = new ArrayList<MapLocation>();
    static int width, height;
    private static boolean isPassable[][];
    static boolean hasFriendlyUnit[][];
    // temporary distance array
    static int bfsDist[][];
    // which direction you should move to reach each square, according to the bfs
    static int bfsDirectionIndexTo[][];
    static MapLocation bfsClosestKarbonite;
    // to random shuffle the directions
    static int randDirOrder[];

    public static void initBfsFirstTurn () {
        width = (int)myMap.getWidth();
        height = (int)myMap.getHeight();
        isPassable = new boolean[height][width];
        hasFriendlyUnit = new boolean[height][width];
        bfsDist = new int[height][width];
        bfsDirectionIndexTo = new int[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            //System.out.println("checking if there's passable terrain at " + new MapLocation(gc.planet(), x, y).toString());
            //System.out.println("is passable at y = " + y + ", x = " + x + " is " + myMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)));
            isPassable[y][x] = myMap.isPassableTerrainAt(new MapLocation(gc.planet(), x, y)) != 0;
        }
        VecUnit units = myMap.getInitial_units();
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

    public static void initBfsPerTurn (VecUnit myUnits) {
        factoriesBeingBuilt.clear();
        for (int i = 0; i < myUnits.size(); i++) {
            Unit unit = myUnits.get(i);
            if (unit.unitType() == UnitType.Factory) {
                if (unit.structureIsBuilt() == 0) {
                    factoriesBeingBuilt.add(unit);
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                hasFriendlyUnit[y][x] = false;
            }
        }
        VecUnit units = gc.units();
        for (int i = 0; i < units.size(); i++) {
            // Note: This loops through friendly AND enemy units!
            Unit unit = units.get(i);
            if (unit.team() == gc.team() && unit.location().isOnMap()) {
                MapLocation loc = unit.location().mapLocation();
                hasFriendlyUnit[loc.getY()][loc.getX()] = true;
            }
        }
    }

    public static void doMoveRobot (Unit unit, Direction dir) {
        MapLocation loc = unit.location().mapLocation();
        if (!hasFriendlyUnit[loc.getY()][loc.getX()]) {
			System.out.printf("Error: hasFriendlyUnit[][] is incorrect! [round=%d, x=%d, y=%d]\n", UniverseController.roundNum, loc.getX(), loc.getY());
            return;
        }
        hasFriendlyUnit[loc.getY()][loc.getX()] = false;
        loc.add(dir);
        hasFriendlyUnit[loc.getY()][loc.getX()] = true;
        gc.moveRobot(unit.id(), dir);
    }

    // bfs to all squares
    // store how close you want to get to your destination in howClose (distance = #moves distance, not Euclidean distance)
    // stores the direction you should move to be within howClose of each square
    public static void bfs(MapLocation from, int howClose) {
        bfsClosestKarbonite = null;

        int fromY = from.getY(), fromX = from.getX();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            bfsDirectionIndexTo[y][x] = DirCenterIndex;
            bfsDist[y][x] = 100000;
        }
        bfsDist[fromY][fromX] = 0;
        Queue<BfsState> queue = new LinkedList<>();

        shuffleDirOrder();
        for (int d = 0; d < 8; d++) {
            int ny = fromY + dy[randDirOrder[d]];
            int nx = fromX + dx[randDirOrder[d]];
            if (0 <= ny && ny < height && 0 <= nx && nx < width && !hasFriendlyUnit[ny][nx] && isPassable[ny][nx]) {
                bfsDist[ny][nx] = 1;
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
                    if (bfsClosestKarbonite == null && gc.canSenseLocation(loc) && gc.karboniteAt(loc) > 0) {
                        bfsClosestKarbonite = loc;
                    }
                }
            }

            shuffleDirOrder();
            for (int d = 0; d < 8; d++) {
                int ny = cur.y + dy[randDirOrder[d]];
                int nx = cur.x + dx[randDirOrder[d]];
                if (0 <= ny && ny < height && 0 <= nx && nx < width && !hasFriendlyUnit[ny][nx] && isPassable[ny][nx] && bfsDist[ny][nx] > bfsDist[cur.y][cur.x]+1) {
                    bfsDist[ny][nx] = bfsDist[cur.y][cur.x]+1;
                    queue.add(new BfsState(ny, nx, cur.startingDir));
                }
            }
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

    public static int moveDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }
}
