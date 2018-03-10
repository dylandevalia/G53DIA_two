package uk.ac.nott.cs.g53dia.multidemo;
import uk.ac.nott.cs.g53dia.multilibrary.*;

import java.util.*;

public class DemoFleet extends Fleet {

    /** 
     * Number of tankers in the fleet
     */
    private static int FLEET_SIZE = 3;

    public DemoFleet() {
	// Create the tankers
	for (int i=0; i<FLEET_SIZE; i++) {
	    this.add(new DemoTanker());
	}
    }
}
