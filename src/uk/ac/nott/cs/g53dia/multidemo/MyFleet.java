package uk.ac.nott.cs.g53dia.multidemo;

import java.util.LinkedHashMap;
import java.util.Random;
import uk.ac.nott.cs.g53dia.multidemo.utility.Position;
import uk.ac.nott.cs.g53dia.multilibrary.Fleet;
import uk.ac.nott.cs.g53dia.multilibrary.FuelPump;
import uk.ac.nott.cs.g53dia.multilibrary.Well;

public class MyFleet extends Fleet {
	
	private static final int FLEET_SIZE = 4;
	
	LinkedHashMap<FuelPump, Position> fuelPumps = new LinkedHashMap<>();
	LinkedHashMap<MyStation, Position> stations = new LinkedHashMap<>();
	LinkedHashMap<Well, Position> wells = new LinkedHashMap<>();
	
	MyStation[] jobs = new MyStation[FLEET_SIZE];
	
	public MyFleet(Random r) {
		for (int i = 0; i < FLEET_SIZE; i++) {
			add(new MyTanker(this, i, r));
		}
	}
	
	public MyFleet() {
		this(new Random());
	}
}
