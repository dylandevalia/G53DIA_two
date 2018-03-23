package uk.ac.nott.cs.g53dia.multidemo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import javafx.util.Pair;
import uk.ac.nott.cs.g53dia.multidemo.utility.Position;
import uk.ac.nott.cs.g53dia.multilibrary.Action;
import uk.ac.nott.cs.g53dia.multilibrary.Cell;
import uk.ac.nott.cs.g53dia.multilibrary.DisposeWasteAction;
import uk.ac.nott.cs.g53dia.multilibrary.EmptyCell;
import uk.ac.nott.cs.g53dia.multilibrary.FuelPump;
import uk.ac.nott.cs.g53dia.multilibrary.LoadWasteAction;
import uk.ac.nott.cs.g53dia.multilibrary.RefuelAction;
import uk.ac.nott.cs.g53dia.multilibrary.Station;
import uk.ac.nott.cs.g53dia.multilibrary.Tanker;
import uk.ac.nott.cs.g53dia.multilibrary.Task;
import uk.ac.nott.cs.g53dia.multilibrary.Well;

/**
 * Custom {@link Tanker} with single agent AI
 */
public class MyTanker extends Tanker {
	
	private static final double FUEL_BUFFER_PERCENT = 0.1;
	private static final double FUEL_BUFFER = Tanker.MAX_FUEL * FUEL_BUFFER_PERCENT;
	
	private MyFleet fleet;
	private int fleetIndex;
	
	private Position pos = new Position(0, 0), lastPos = pos.copy();
	
	private Action lastAction = null;
	private boolean triedToMove = false;
	
	private int di = 1, dj = 1;
	private int spiralLen = Tanker.VIEW_RANGE;
	private int segmentsPassed = 0;
	
	MyTanker(MyFleet fleet, int fleetIndex, Random r) {
		this.fleet = fleet;
		this.fleetIndex = fleetIndex;
		this.r = r;
		
		switch (fleetIndex % 4) {
			case 0:
				di = 1;
				dj = 1;
				break;
			case 1:
				di = 1;
				dj = -1;
				break;
			case 2:
				di = -1;
				dj = -1;
				break;
			case 3:
				di = -1;
				dj = 1;
				break;
		}
	}
	
	/**
	 * Evaluates its surroundings and returns the {@link Action} that the tank should execute
	 *
	 * @param view     the cells the Tanker can currently see
	 * @param timeStep The current time-step
	 * @return The {@link Action} the tanker should execute
	 *
	 * @see uk.ac.nott.cs.g53dia.multilibrary.FallibleAction
	 * @see uk.ac.nott.cs.g53dia.multilibrary.MoveAction
	 * @see uk.ac.nott.cs.g53dia.multilibrary.MoveTowardsAction
	 * @see uk.ac.nott.cs.g53dia.multilibrary.DisposeWasteAction
	 * @see uk.ac.nott.cs.g53dia.multilibrary.LoadWasteAction
	 * @see uk.ac.nott.cs.g53dia.multilibrary.RefuelAction
	 */
	@Override
	public Action senseAndAct(Cell[][] view, long timeStep) {
		// if (fleetIndex == 0) {
		// 	for (MyStation job : fleet.jobs) {
		// 		job = null;
		// 	}
		// }
		
		if (actionFailed && triedToMove) {
			// If action fail, revert expected position
			pos = lastPos.copy();
		} else {
			// Set last position before changing pos
			lastPos = pos.copy();
		}
		
		// Look through view
		sense(view);
		
		ArrayList<Map.Entry<FuelPump, Position>> sortedFuelPumps = sortFuelPumps(pos);
		ArrayList<Map.Entry<MyStation, Position>> sortedStations = sortStations(pos);
		ArrayList<Map.Entry<Well, Position>> sortedWells = sortWells(pos);
		
		Action action = act(sortedFuelPumps, sortedStations, sortedWells);
		
		triedToMove = false;
		if (action instanceof MyMoveAction) {
			triedToMove = true;
			pos.move(((MyMoveAction) action).getDirection());
		}
		lastAction = action;
		return action;
	}
	
	/**
	 * Goes through tanker's view and updates its references to cells and records new cells
	 *
	 * @param view The view of the tanker
	 */
	private void sense(Cell[][] view) {
		for (int i = 0; i < view.length; i++) {
			for (int j = 0; j < view[i].length; j++) {
				Cell cell = view[i][j];
				if ((cell instanceof EmptyCell)) {
					continue;
				}
				
				// System.out.printf("view[%3d][%3d] -- ", i - 20, 20 - j);
				Position cellPos = new Position(pos.x + (i - 20), pos.y + (20 - j));
				
				if (cell instanceof FuelPump) {
					// System.out.println("FuelPump");
					fleet.fuelPumps.putIfAbsent((FuelPump) cell, cellPos);
				} else if (cell instanceof Station) {
					// System.out.println("Station");
					boolean found = false;
					for (Map.Entry<MyStation, Position> s : fleet.stations.entrySet()) {
						if (s.getKey().equals(cell)) {
							found = true;
							s.getKey().updateStation((Station) cell);
							break;
						}
					}
					if (!found) {
						fleet.stations.put(new MyStation((Station) cell), cellPos);
					}
				} else if (cell instanceof Well) {
					// System.out.println("Well");
					fleet.wells.putIfAbsent((Well) cell, cellPos);
				}
			}
		}
		
		// Tick all stations
		for (Map.Entry<MyStation, Position> s : fleet.stations.entrySet()) {
			s.getKey().tick();
		}
		
		// System.out.println(pos);
		// System.out.println(getPosition() + "\n");
	}
	
	private Action act(
		ArrayList<Map.Entry<FuelPump, Position>> fuelPumps,
		ArrayList<Map.Entry<MyStation, Position>> stations,
		ArrayList<Map.Entry<Well, Position>> wells
	) {
		Action finalAction;
		
		finalAction = fuelCheck(fuelPumps);
		if (finalAction != null) {
			return finalAction;
		}
		
		finalAction = stationCheck(wells, stations);
		if (finalAction != null) {
			return finalAction;
		}
		
		fleet.jobs[fleetIndex] = null;
		
		finalAction = wasteCheck(wells);
		if (finalAction != null) {
			return finalAction;
		}
		
		finalAction = explore(stations);
		if (finalAction != null) {
			return finalAction;
		}
		
		return null;
	}
	
	/**
	 * Checks if tanker needs fuel and goes to refuel if it does
	 *
	 * @param fuelPumps List of fuelPumps sorted by distance from tanker
	 * @return {@link MyMoveAction} or {@link RefuelAction} if fuel is needed or {@code null} if not
	 */
	private Action fuelCheck(ArrayList<Map.Entry<FuelPump, Position>> fuelPumps) {
		double fuelLevel = getFuel();
		int distance = fuelPumps.get(0).getValue().distanceTo(pos);
		
		// If too far from fuel pump
		if (
			fuelLevel <= distance
				|| (distance < Tanker.VIEW_RANGE / 5 && fuelLevel < Tanker.MAX_FUEL / 10)
			) {
			Action action = pos.moveToward(fuelPumps.get(0).getValue());
			
			fleet.jobs[fleetIndex] = null;
			
			if (action == null) {
				// At fuel pump
				return new RefuelAction();
			} else {
				return action;
			}
		}
		
		// Don't need to refuel
		return null;
	}
	
	/**
	 * Checks if stations have tasks and goes to them if they do. Sees if station is within range,
	 * has a task and has enough fuel. If the tanker has waste, it reduces the search range to
	 * 1/10th of the fuel range
	 *
	 * @param wells    List of wells sorted by distance from player
	 * @param stations List of stations sorted by if they have a task then by distance
	 * @return {@link MyMoveAction} or {@link LoadWasteAction} if picking up waste or {@code null}
	 * if not
	 */
	private Action stationCheck(
		ArrayList<Map.Entry<Well, Position>> wells,
		ArrayList<Map.Entry<MyStation, Position>> stations
	) {
		if (
			stations.size() == 0
				|| stations.get(0).getKey().getTask() == null
				|| getWasteLevel() == Tanker.MAX_WASTE
				|| getFuel() < stations.get(0).getValue().distanceTo(pos)
			) {
			// If no stations have a task
			// or waste level is max
			// or nearest station with task is out of reach
			return null;
		}
		
		// If on a station
		if (pos.distanceTo(stations.get(0).getValue()) == 0) {
			return new LoadWasteAction(stations.get(0).getKey().getTask());
		}
		
		// Pick station to go to
		for (Entry<MyStation, Position> station : stations) {
			MyStation s = station.getKey();
			Position p = station.getValue();
			
			// If no tasks, exit
			if (s.getTask() == null) {
				return null;
			}
			
			// If tanker has waste, limit range
			double range = (getWasteLevel() > 0 && wells.size() > 0)
				? wells.get(0).getValue().distanceTo(pos) //Tanker.MAX_FUEL * FUEL_BUFFER_PERCENT
				: getFuel();
			
			// If stations is too far away, ignore
			int distToStation = p.distanceTo(pos);
			if (distToStation > range) {
				continue;
			}
			// vvv Is within fuel range and has a task vvv
			
			// Check if another agent is going to the station
			boolean stationTaken = false;
			for (int i = 0; i < fleet.jobs.length; i++) {
				if (i == fleetIndex) {
					continue;
				}
				if (fleet.jobs[i] == s) {
					stationTaken = true;
					break;
				}
			}
			if (stationTaken) {
				continue;
			}
			
			// Calculate dist pos->station->fuel pump
			ArrayList<Map.Entry<FuelPump, Position>> pumps = sortFuelPumps(p);
			int distStationToPump = p.distanceTo(pumps.get(0).getValue());
			if (distToStation + distStationToPump > getFuel()) {
				continue;
			}
			
			fleet.jobs[fleetIndex] = s;
			
			// Found nearest pump that can go to station with enough fuel
			// to get to pump afterwards. Move towards it
			return pos.moveToward(p);
		}
		
		return null;
	}
	
	
	private Pair<MyStation, Position> a(
		Pair<MyStation, Position> currentStation,
		ArrayList<Pair<MyStation, Position>> visitedStations,
		int fuelLevel,
		int wasteLevel
	) {
		// If closest station is out of fuel range
		ArrayList<Entry<MyStation, Position>> nearbyStations = sortStations(
			currentStation.getValue()
		);
		if (
			nearbyStations.get(0).getKey().getTask() == null ||
			currentStation.getValue().distanceTo(nearbyStations.get(0).getValue()) > fuelLevel
		) {
			return null;
		}
		
		// Check if enough fuel to get from station to fuel pump
		ArrayList<Entry<FuelPump, Position>> pumps = sortFuelPumps(currentStation.getValue());
		int distToPump = currentStation.getValue().distanceTo(pumps.get(0).getValue());
		if (distToPump > fuelLevel) {
			return new Pair<>(null, null);
		}
		
		ArrayList<Pair<MyStation, Position>> possibleStations = new ArrayList<>();
		ArrayList<Pair<MyStation, Position>> _visitedStations = new ArrayList<>(visitedStations);
		_visitedStations.add(currentStation);
		for (Entry<MyStation, Position> s : nearbyStations) {
			int distToStation = s.getValue().distanceTo(currentStation.getValue());
			
			Pair<MyStation, Position> r =
				a(new Pair<>(
						s.getKey(), s.getValue()),
					_visitedStations,
					fuelLevel - distToStation,
					wasteLevel + s.getKey().getTask().getWasteRemaining()
				);
			
			if (r == null) {
				// Nearest station has no task or is out of fuel range
				break;
			} else if (r.getKey() == null) {
				// Station didn't have enough fuel to get back to a pump
				continue;
			}
			
			// Add
			possibleStations.add(r);
		}
		
		return null;
	}
	
	
	/**
	 * Checks if the tanker is on a station with a task and gets waste. If it has waste, it moves
	 * towards the nearest well and dumps waste
	 *
	 * @param wells The list of wells sorted by distance from tanker
	 * @return {@link LoadWasteAction} if on a station and can get waste, {@link DisposeWasteAction}
	 * if on a well, {@code null} else
	 */
	private Action wasteCheck(
		ArrayList<Map.Entry<Well, Position>> wells
	) {
		// If found no wells or carrying no waste or nearest well to too far to get to
		if (wells.size() == 0 || getWasteLevel() == 0
			|| wells.get(0).getValue().distanceTo(pos) > getFuel()
			) {
			return null;
		}
		
		// If on a well
		if (wells.get(0).getValue().distanceTo(pos) == 0) {
			return new DisposeWasteAction();
		} else if (wells.get(0).getValue().distanceTo(pos) > getFuel()) {
			return null;
		}
		
		// Move towards well
		return pos.moveToward(wells.get(0).getValue());
	}
	
	/**
	 * Checks stations that haven't been updated in a while or walks in a diagonal spiral
	 */
	private Action explore(ArrayList<Map.Entry<MyStation, Position>> stations) {
		// Update stations that haven't been visited/updated in a while
		for (Map.Entry<MyStation, Position> s : stations) {
			if (s.getValue().distanceTo(pos) > getFuel() + Tanker.VIEW_RANGE) {
				break;
			}
			
			if (s.getKey().shouldRecheck()) {
				return pos.moveToward(s.getValue());
			}
		}
		
		// Spiral pattern
		Position tpos = new Position(pos.x + di, pos.y + dj);
		++segmentsPassed;
		if (segmentsPassed == spiralLen) {
			segmentsPassed = 0;
			
			// rotate
			int buffer = dj;
			dj = -di;
			di = buffer;
			
			if (dj == 1 && spiralLen == Tanker.VIEW_RANGE) {
				// if ((spiralLen += Tanker.VIEW_RANGE) > Tanker.MAX_FUEL / 2) {
				// 	spiralLen = 1;
				// }
				spiralLen *= 2;
			}
		}
		
		return pos.moveToward(tpos);
	}
	
	
	/**
	 * Sorts {@link #fleet}.stations map by if it has waste then by distance
	 *
	 * @return List where the first element is the closest station with waste
	 */
	private ArrayList<Map.Entry<MyStation, Position>> sortStations(Position distFrom) {
		ArrayList<Entry<MyStation, Position>> sortedStations = new ArrayList<>(
			fleet.stations.entrySet());
		sortedStations.sort((a, b) -> {
			// To return:
			//   positive if a is better
			//   zero     if equal
			//   negative if b is better
			
			Task aTask = a.getKey().getTask();
			Task bTask = b.getKey().getTask();
			int aWaste = (aTask == null) ? 0 : aTask.getWasteRemaining();
			int bWaste = (bTask == null) ? 0 : bTask.getWasteRemaining();
			
			// If one has doesn't have waste
			if (aWaste == 0 || bWaste == 0) {
				// Return one with waste or equal
				return bWaste - aWaste;
			}
			
			// TODO: Improve calculation weighing up amount at station vs travel costs
			
			int aDist = a.getValue().distanceTo(distFrom);
			int bDist = b.getValue().distanceTo(distFrom);
			
			return aDist - bDist;
		});
		
		return sortedStations;
	}
	
	/**
	 * Sorts the {@link #fleet}.fuelPumps map by distance from player
	 *
	 * @return List where the first element is closest fuel pump
	 */
	private ArrayList<Map.Entry<FuelPump, Position>> sortFuelPumps(Position distFrom) {
		ArrayList<Entry<FuelPump, Position>> sortedPumps = new ArrayList<>(
			fleet.fuelPumps.entrySet());
		sortedPumps.sort(Comparator.comparingInt(a -> a.getValue().distanceTo(distFrom)));
		return sortedPumps;
	}
	
	/**
	 * Sorts the {@link #fleet}.wells map by distance from player
	 *
	 * @return List where the first element is the closet well
	 */
	private ArrayList<Map.Entry<Well, Position>> sortWells(Position distFrom) {
		ArrayList<Entry<Well, Position>> sortedPumps = new ArrayList<>(fleet.wells.entrySet());
		sortedPumps.sort(Comparator.comparingInt(a -> a.getValue().distanceTo(distFrom)));
		return sortedPumps;
	}
	
	private double getFuel() {
		return super.getFuelLevel() - FUEL_BUFFER;
	}
}
