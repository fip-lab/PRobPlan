package rddl.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeMap;
import rddl.EvalException;
import rddl.RDDL.LCONST;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.State;
import rddl.viz.StateViz;

/**
 * Created by HuGuodong on 2018/11/28.
 */

public class PowerSetAction {

  static final String state_viz_class_name = "rddl.viz.GenericScreenDisplay";
  public static StateViz stateViz;
  static {
    try {
      stateViz = (StateViz)Class.forName(state_viz_class_name).newInstance();
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  public static TreeMap<String,ArrayList<PVAR_INST_DEF>> getLegalBoolActionMap(State s)
      throws EvalException {

    ArrayList<PVAR_INST_DEF> actions = new ArrayList<PVAR_INST_DEF>();

    // Build a map from propositional action names to actions
    // that can be returned from this policy.
    TreeMap<String,ArrayList<PVAR_INST_DEF>> action_map = new TreeMap<String,ArrayList<PVAR_INST_DEF>>();

    //if (ALLOW_NOOP) {
    action_map.put("noop", (ArrayList<PVAR_INST_DEF>)actions.clone());
    //}

    HashSet<HashSet<PVAR_INST_DEF>> action_set = new HashSet<HashSet<PVAR_INST_DEF>>();
    return action_map;
  }


  public static ArrayList<ArrayList<PVAR_INST_DEF>> getActions(State s){

    ArrayList<PVAR_INST_DEF> actionSet = new ArrayList<>();

    for (PVAR_NAME p : s._alActionNames) {

      // Get term instantiations for that action and select *one*
      ArrayList<ArrayList<LCONST>> inst = null;
      try {
        inst = s.generateAtoms(p);
      } catch (EvalException e) {
        System.out.println("ERROR: could not generate atoms for " + p + "\n" + e);
        e.printStackTrace();
        System.exit(1);
      }

      for (int i = 0; i < inst.size(); i++) {
        ArrayList<LCONST> terms = inst.get(i);
        PVAR_INST_DEF cur_action = new PVAR_INST_DEF(p._sPVarName, new Boolean(true), terms);
//        System.out.println(cur_action);
        actionSet.add(cur_action);
      }
    }
    stateViz.display(s, 0);
    ArrayList<ArrayList<PVAR_INST_DEF>> sets = powerSet_2(actionSet, s);
    return sets;



  }

  public static  ArrayList<ArrayList<PVAR_INST_DEF>> powerSet(ArrayList<PVAR_INST_DEF> input, State s) {
    ArrayList<ArrayList<PVAR_INST_DEF>> sets = new ArrayList<>();
    for (PVAR_INST_DEF element : input) {
      for (ListIterator<ArrayList<PVAR_INST_DEF>> setsIterator = sets.listIterator(); setsIterator.hasNext(); ) {
        ArrayList<PVAR_INST_DEF> newSet = new ArrayList<>(setsIterator.next());
        newSet.add(element);
        if(s.stateActionConstraints(newSet)){
          setsIterator.add(newSet);
        }
      }
      ArrayList<PVAR_INST_DEF> newList = new ArrayList<>(Arrays.asList(element));
//      if(s.stateActionConstraints(newList)){
        sets.add(newList);
//      }
    }
    if(s.stateActionConstraints(new ArrayList<>())){

      sets.add(new ArrayList<>());
    }
    return sets;
  }


  public static ArrayList<ArrayList<PVAR_INST_DEF>> powerSet_2(ArrayList<PVAR_INST_DEF> input, State s){
    int inputSize = input.size();
    long powerSetSize = (long)Math.pow(2, 8);
    ArrayList<ArrayList<PVAR_INST_DEF>> sets = new ArrayList<>();
    for (int counter = 0; counter < powerSetSize; counter++) {
      ArrayList<PVAR_INST_DEF> newSet = new ArrayList<>();

      for (int j = 0; j < inputSize; j++) {
        if((counter & (1 << j)) > 0){
          newSet.add(input.get(j));
        }
      }
      if(s.stateActionConstraints(newSet)){
        sets.add(newSet);
      }
    }
    return sets;
  }

}
