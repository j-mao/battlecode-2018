// Any non-private member within UniverseController.java can be accessed directly

package bc18bot;

import java.io.*;
import java.math.*;
import java.util.*;
import bc.*;

import bc18bot.UniverseController;

public class MarsController extends UniverseController
{
	// batallion numbers
	// the id of the batallion is the id of its commander
	static Map<Integer, Integer> battalion = new HashMap<Integer, Integer>();
	static Set<Integer> battalionLeaders = new TreeSet<Integer>();
	static Map<Integer, Integer> battalionSizes;

	static void computeBattalionSizes()
	{
		battalionSizes = new HashMap<Integer, Integer>();
		for (int i = 0;i < numunits;i++)
		{
			if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace()) {
				if (units.get(i).unitType() == UnitType.Ranger) {
					if (battalion.containsKey(units.get(i).id()) && battalionLeaders.contains(battalion.get(units.get(i).id())))
					{
						if (battalionSizes.containsKey(battalion.get(units.get(i).id())))
						{
							battalionSizes.put(battalion.get(units.get(i).id()), battalionSizes.get(battalion.get(units.get(i).id()))+1);
						} else
						{
							battalionSizes.put(battalion.get(units.get(i).id()), 1);
						}
					}
				}
			}
		}
	}

	public static void runTurn() throws Exception
	{
		getUnitSets();
		checkForEnemyUnits();

		if (roundNum <= 2)
		{
			attackLocs.clear(); // they only apply to Earth
		}

		for (int i = 0;i < numunits;i++)
		{
			if (units.get(i).unitType() == UnitType.Rocket) {
				tryToUnload(units.get(i).id());
			}
		}

		// do stuff with workers
		// rangers attack
		boolean notYetReplicated = true;
		for (int i = 0;i < numunits;i++)
		{
			if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace())
			{
				if (units.get(i).unitType() == UnitType.Worker) {
					if (roundNum%150 == 0 && notYetReplicated)
					{
						boolean result = tryReplicate(units.get(i).id());
						if (result) notYetReplicated = false;
					}
					tryHarvest(units.get(i).id());
					tryMoveForFood(units.get(i).id());
				} else if (units.get(i).unitType() == UnitType.Ranger) {
					tryAttack(units.get(i).id());
				}
			}
		}

		// check if battalion leaders are alive
		Set<Integer> newLeaders = new HashSet<Integer>();
		for (Integer v: battalionLeaders)
		{
			if (gc.canSenseUnit(v))
			{
				newLeaders.add(v);
			}
		}
		battalionLeaders.clear();
		battalionLeaders.addAll(newLeaders);

		// remove robots that have been killed
		Map<Integer, Integer> newBattalion = new HashMap<Integer, Integer>();
		for (Map.Entry<Integer, Integer> i: battalion.entrySet())
		{
			if (gc.canSenseUnit(i.getKey()))
			{
				newBattalion.put(i.getKey(), i.getValue());
			} // else { rest in peace; }
		}
		battalion.clear();
		battalion.putAll(newBattalion);

		computeBattalionSizes();

		// remove battalions that are too small
		int minBattalionSize = 5; // feel free to change this
		for (Map.Entry<Integer, Integer> i: battalionSizes.entrySet())
		{
			if (i.getValue() < minBattalionSize)
			{
				battalionLeaders.remove(i.getKey());
			}
		}

		// determine all free-agents: units that arent in a battalion
		ArrayList<Unit> freeAgents = new ArrayList<Unit>();
		for (int i = 0;i < numunits;i++)
		{
			if (!units.get(i).location().isInGarrison() && !units.get(i).location().isInSpace()) {
				if (units.get(i).unitType() == UnitType.Ranger) {
					if (!battalion.containsKey(units.get(i).id())) // unassigned
					{
						freeAgents.add(units.get(i));
					} else if (!battalionLeaders.contains(battalion.get(units.get(i).id()))) // leader doesnt exist
					{
						freeAgents.add(units.get(i));
					}
				}
			}
		}

		computeBattalionSizes();

		// start a new battalion if there is none
		if (freeAgents.size() > 0 && battalionLeaders.size() == 0)
		{
			Integer me = ((Unit)freeAgents.toArray()[0]).id();
			battalionLeaders.add(me);
			battalion.put(me, me);
			battalionSizes.put(me, 1);
			freeAgents.remove(me);
		}

		// assign freeagents to battalions
		for (Unit i: freeAgents)
		{
			int best = -1, sz = 100000;
			for (Map.Entry<Integer, Integer> option: battalionSizes.entrySet())
			{
				if (option.getValue() < sz)
				{
					best = option.getKey();
					sz = option.getValue();
				}
			}
			if (best == -1)
			{
				System.out.println("rip");
			} else
			{
				battalion.put(i.id(), best);
			}
		}

		computeBattalionSizes();

		/*
		// report battalion list
		System.out.print("Round "+roundNum+":");
		for (Map.Entry<Integer, Integer> option: battalionSizes.entrySet())
		{
			System.out.printf(" [leader %d size %d]", option.getKey(), option.getValue());
		}
		System.out.println();
		*/

		// split battalions that are too big
		int optimalBattalionQty = Math.max(1, Math.min(6, numunits/8-1)); // feel free to change this
		if (battalionLeaders.size() > 0) while (battalionLeaders.size() < optimalBattalionQty)
		{
			// take the largest battalion
			Integer which = null, magnitude = null;
			for (Map.Entry<Integer, Integer> option: battalionSizes.entrySet())
			{
				if (which == null)
				{
					which = option.getKey();
					magnitude = option.getValue();
				} else if (option.getValue().intValue() > magnitude.intValue())
				{
					which = option.getKey();
					magnitude = option.getValue();
				}
			}
			if (which == null)
			{
				// shouldnt happen but dont want exceptions
				System.out.println("wtf couldnt split battalion");
				break;
			}
			// get members
			ArrayList<Integer> members = new ArrayList<Integer>();
			for (Map.Entry<Integer, Integer> option: battalion.entrySet())
			{
				if (option.getValue().equals(which))
				{
					members.add(option.getKey());
				}
			}
			// split in half
			int portion1 = members.size() / 2;
			int portion2 = members.size() - portion1;

			battalionLeaders.remove(which);
			battalionSizes.remove(which);

			battalionLeaders.add(members.get(0));
			battalionSizes.put(members.get(0), portion1);

			battalionLeaders.add(members.get(portion1));
			battalionSizes.put(members.get(portion1), portion2);

			for (int i = 0;i < portion1;i++) battalion.put(members.get(i), members.get(0));
			for (int i = portion1;i < portion1+portion2;i++) battalion.put(members.get(i), members.get(portion1));
		}

		// do stuff with battalions
		for (Integer battalionLeader: battalionLeaders)
		{
			// get list of members
			// TODO precompute a lot of this to save time
			// better data structures exist
			ArrayList<Integer> members = new ArrayList<Integer>();
			for (Map.Entry<Integer, Integer> option: battalion.entrySet())
			{
				if (option.getValue().equals(battalionLeader))
				{
					members.add(option.getKey());
				}
			}

			// move the "pack"
			// Option 1: go to attack location if possible
			boolean doneMove = false;
			if (!attackLocs.isEmpty()) {
				// find closest out of the potential attackable locations
				// TODO: change this to bfs distance so it's less horrible xd
				// TODO: once changed to bfs, make sure you don't do too many bfs's and time out
				// TODO: actually you can just do one bfs LuL
				// TODO: make the bfs result function more smooth over time
				Unit unit = gc.unit(battalionLeader);
				MapLocation bestLoc = attackLocs.get(0);
				for (int i = 1; i < attackLocs.size(); i++) {
					if (moveDistance(unit.location().mapLocation(), attackLocs.get(i)) <
							moveDistance(unit.location().mapLocation(), bestLoc)) {
						bestLoc = attackLocs.get(i);
					}
				}
				//System.out.println("doing bfs");
				// do a backwards bfs from the destination
				bfs(bestLoc, 0);
				ArrayList<Boolean> result = tryMovePackOfRobots(members);
				for (Boolean i: result) if (i)
					doneMove = true;
			}
			// Option 2: go towards the leader if possible
			if (!doneMove) {
				bfs(gc.unit(battalionLeader).location().mapLocation(), 0);
				ArrayList<Boolean> result = tryMovePackOfRobots(members);
				for (Boolean i: result) if (i)
					doneMove = true;
			}
			// Option 3: move according to the leader's tendency
			if (!doneMove) {
				Direction moveDir = directions[getTendency(gc.unit(battalionLeader))];
				ArrayList<Boolean> result = tryMovePackOfRobots(members, moveDir);
				for (Boolean i: result) if (i)
					doneMove = true;
				if (doneMove) {
					updateTendency(battalionLeader, 6);
				} else {
					updateTendency(battalionLeader, 100);
				}
			}
		}
	}
}
