package uk.ac.nott.cs.g53dia.multidemo;

import uk.ac.nott.cs.g53dia.multilibrary.Action;
import uk.ac.nott.cs.g53dia.multilibrary.ActionFailedException;
import uk.ac.nott.cs.g53dia.multilibrary.Environment;
import uk.ac.nott.cs.g53dia.multilibrary.MoveAction;
import uk.ac.nott.cs.g53dia.multilibrary.Tanker;

public class MyMoveAction implements Action {
	
	private int direction;
	
	public MyMoveAction(int dir) {
		this.direction = dir;
	}
	
	@Override
	public void execute(Environment env, Tanker tanker)
		throws ActionFailedException {
		
		new MoveAction(direction).execute(env, tanker);
	}
	
	public int getDirection() {
		return direction;
	}
}
