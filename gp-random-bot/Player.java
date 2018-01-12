// import the API.
// See xxx for the javadocs.
import bc.*;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public class Player {

    private static GameController gc = new GameController();
    private static Random rand = new Random();
    // Direction is a normal java enum.
    private static Direction[] directions = Direction.values();
    private static Map<Integer, Integer> tendency = new HashMap<Integer, Integer>();

    public static void main(String[] args) {
        // MapLocation is a data structure you'll use a lot.
        MapLocation loc = new MapLocation(Planet.Earth, 10, 20);
        System.out.println("loc: " + loc + ", one step to the Northwest: " + loc.add(Direction.Northwest));
        System.out.println("loc x: " + loc.getX());

        // One slightly weird thing: some methods are currently static methods on a static class called bc.
        // This will eventually be fixed :/
        System.out.println("Opposite of " + Direction.North + ": " + bc.bcDirectionOpposite(Direction.North));

        while (true) {
            System.out.println("Current round: " + gc.round());
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
                gc.moveRobot(unit.id(), dir);
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

        if (doMove) {
            if (gc.isMoveReady(unit.id())) {
                Direction moveDir = directions[getTendency(unit.id())];
                if (gc.canMove(unit.id(), moveDir)) {
                    gc.moveRobot(unit.id(), moveDir);
                    updateTendency(unit.id(), 20);
                } else {
                    updateTendency(unit.id(), 100);
                }
            }
        }
    }

    private static int getTendency(int id) {
        if (!tendency.containsKey(id)) {
            tendency.put(id, rand.nextInt(8));
        }
        return tendency.get(id);
    }

    private static void updateTendency(int id, int changeChance) {
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
}