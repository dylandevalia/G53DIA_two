package uk.ac.nott.cs.g53dia.multidemo;

import java.util.Random;
import uk.ac.nott.cs.g53dia.multilibrary.Fleet;

public class MyFleet extends Fleet {
	
	private static int FLEET_SIZE = 3;
	
	public MyFleet(Random r) {
		for (int i = 0; i < FLEET_SIZE; i++) {
			this.add(new MyTanker(r));
		}
	}
	
}
