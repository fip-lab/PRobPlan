/**
 * RDDL: Implements a random policy for a domain with a single boolean action.
 *       The action selected to be true is the first ground instance.
 * 
 * @author Scott Sanner (ssanner@gmail.com)
 * @version 10/10/10
 *
 **/

package rddl.policy;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import rddl.ActionGenerator;
import rddl.EvalException;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.State;

public class RandomBoolPolicyFor2018 extends Policy {

	public Random _rand = new Random();

	public RandomBoolPolicyFor2018() {

	}

	public RandomBoolPolicyFor2018(String instance_name) {
		super(instance_name);
	}

	public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {
		

		// Return a random action selection
		ArrayList<ArrayList<PVAR_INST_DEF>> actions = PowerSetAction.getActions(s);
		ArrayList<PVAR_INST_DEF> action_taken = actions.get(_rand.nextInt(actions.size()));
		//System.out.println("\n--> Action taken: " + action_taken);
		
		return action_taken;
	}
}
