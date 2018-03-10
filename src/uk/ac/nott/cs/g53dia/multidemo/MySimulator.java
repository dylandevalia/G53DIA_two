package uk.ac.nott.cs.g53dia.multidemo;

import java.util.Random;
import javax.swing.WindowConstants;
import uk.ac.nott.cs.g53dia.multilibrary.Action;
import uk.ac.nott.cs.g53dia.multilibrary.ActionFailedException;
import uk.ac.nott.cs.g53dia.multilibrary.Cell;
import uk.ac.nott.cs.g53dia.multilibrary.Environment;
import uk.ac.nott.cs.g53dia.multilibrary.Fleet;
import uk.ac.nott.cs.g53dia.multilibrary.OutOfFuelException;
import uk.ac.nott.cs.g53dia.multilibrary.Tanker;
import uk.ac.nott.cs.g53dia.multilibrary.TankerViewer;

public class MySimulator {
	
	public static void main(String[] args) {
		final int DELAY = 100;
		final int DURATION = 10000;
		
		Random rnd = new Random(System.nanoTime());
		Environment environment = new Environment(Tanker.MAX_FUEL / 2, rnd);
		Fleet fleet = new MyFleet(rnd);
		TankerViewer tankerViewer = new TankerViewer(fleet);
		tankerViewer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		
		while (environment.getTimestep() < DURATION) {
			// Update environment and draw
			environment.tick();
			
			// Update the GUI
			tankerViewer.tick(environment);
			
			for (Tanker t : fleet) {
				Cell[][] view = environment.getView(t.getPosition(), Tanker.VIEW_RANGE);
				Action a = t.senseAndAct(view, environment.getTimestep());
				
				try {
					a.execute(environment, t);
				} catch (OutOfFuelException e) {
					System.err.println(e.getMessage());
					System.exit(-1);
				} catch (ActionFailedException e) {
					System.err.println(e.getMessage());
				}
			}
			
			// Delay before next update
			try {
				Thread.sleep(DELAY);
			} catch (Exception e) {
				System.err.println("Thread failed to sleep");
			}
		}
	}
}
