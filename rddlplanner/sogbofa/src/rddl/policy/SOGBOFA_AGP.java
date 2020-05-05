package rddl.policy;

/**
 * Created by HuGuodong on 2019/1/22.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.math3.random.RandomDataGenerator;
import rddl.EvalException;
import rddl.Global;
import rddl.RDDL;
import rddl.RDDL.BOOL_EXPR;
import rddl.RDDL.INSTANCE;
import rddl.RDDL.LCONST;
import rddl.RDDL.LVAR;
import rddl.RDDL.OBJECT_TYPE_DEF;
import rddl.RDDL.PVARIABLE_DEF;
import rddl.RDDL.PVAR_INST_DEF;
import rddl.RDDL.PVAR_NAME;
import rddl.RDDL.TYPE_NAME;
import rddl.State;
import rddl.TEState;
import rddl.TreeExp;
import rddl.competition.Records;

public class SOGBOFA_AGP extends Policy {

  public SOGBOFA_AGP() {
    super();
  }

  public SOGBOFA_AGP(String instance_name) {
    Global.ifLift = true;
    searchDepth = -1;
    expectMaxVarDepth = 50;
    ifConformant = true;
  }

  //
  boolean ifConverge = false;
  // 是一个常数
  final double convergeNorm = 0.01;

  long t0 = 0;

  // if time out
  boolean stopAlgorithm = false;

  // base number of dived the alpha legal region
  int AlphaTime = 10;

  // if record the tree
  final boolean ifRecord = false;

  // max time of iteratively shrink alpha
  final int MaxShrink = 5;

  final boolean ifPrint = false;
  final boolean ifPrintEst = false;
  final boolean ifPrintBit = false;
  boolean ifPrintInitial = false;
  final boolean ifPrintProb = false;
  //print out the starting and ending points of each random restart
  final boolean ifPrintGrid = false;
  final boolean ifDefaultNoop = true;

  //print trajectory actions step by step in the end
  final boolean ifPrintTraje = true; // false

  //print the routine updates
  final boolean ifPrintUpdateInRoutine = true;

  //if use forward accumulation or reverse accumulation
  final boolean ifReverseAccu = true;
  final double fixAlpha = -1;
  final boolean ifRecordRoutine = true;
  final boolean ifTopoSort = true;

  int maxDepth = 0;

  //stats
  double roundRandom = 0;
  double roundUpdates = 0;
  double roundSeen = 0;

  ArrayList<Double> bestNumAct = new ArrayList<Double>();
  double highestScore = -Double.MAX_VALUE;
  HashMap<ArrayList<Double>, Double> routine = new HashMap<ArrayList<Double>, Double>();
  HashMap<ArrayList<Double>, Double> initActRoutine = new HashMap<ArrayList<Double>, Double>();
  HashMap<Integer, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, TreeExp>>> trans2Tree = new HashMap<Integer, HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, TreeExp>>>();
  ArrayList<PVAR_NAME> int2PVAR = new ArrayList<>();
  ArrayList<ArrayList<TYPE_NAME>> int2TYPE_NAME = new ArrayList<>();

  @Override
  public ArrayList<PVAR_INST_DEF> getActions(State s) throws EvalException {

    // stats
    roundRandom = 0;
    roundUpdates = 0;
    roundSeen = 0;

    // update action probs
    highestScore = -Double.MAX_VALUE;
    routine = new HashMap<>();
    initActRoutine = new HashMap<>();

    //every time get to this point, meaning we have one more time of record of how many random restart have been tried
    if (currentRound < 5) {
      _visCounter.randomTime++;
      _visCounter.updateTime++;
      _visCounter.SeenTime++;
      _visCounter.depthTime++;
      _visCounter.sizeTime++;
    }

    t0 = System.currentTimeMillis();

    // declare a action list
    ArrayList<PVAR_INST_DEF> actions = new ArrayList<>();

    // recalculate the root init
    //because we assume there is no constraint so simple use concurrency divided by number of action bits
    // 统计动作的个数
    if (countActBits == 0) {
      for (PVAR_NAME p : s._alActionNames) { // 理解成一共有多少个动作0010101
        countActBits += s.generateAtoms(p).size();
      }
    }

    // 问题实例
    INSTANCE instance = _rddl._tmInstanceNodes.get(_sInstanceName);
    // 最大深度
    if (searchDepth == -1) {
      maxDepth = instance._nHorizon - currentRound + 1;
    } else {
      maxDepth = (instance._nHorizon - currentRound + 1) > searchDepth ? searchDepth
          : (instance._nHorizon - currentRound + 1);
    }

    stopAlgorithm = false;
    //beacuase maxVarDpeth could be too small when
    maxVarDepth = expectMaxVarDepth;
    //decide using dynamic depth
    searchDepth = -1;

    //decide using conformant 使用一致规划，我的理解是选择动作概率最大的那个动作
    if (theRatio == -1 && maxVarDepth >= 1) {
      //use fixed conformant depth
      System.out.println("Use conformant depth: " + maxVarDepth);
    } else {
      //use searchdepth * theRatio as conformant depth
      if (theRatio != -1 && maxVarDepth == -1) {
        System.out.println("Use conformant depth as ratio: " + theRatio);
      } else {
        try {
          throw new Exception(
              "theRatio or maxVarDepth set incorrectly. Please check conformant set up!");
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    TreeExp.counter = 0;

    //clear the state history
    //ready for building new history record
    stateHistory = null;
    stateHistory = new ArrayList<>();
    //initialize action variables in graph or not 构建表达式图的时候判断动作是否在图中
    ifInGraph = new Boolean[countActBits * maxVarDepth];
    ifForcednotChoose = new Boolean[countActBits * maxVarDepth];
    for (int i = 0; i < countActBits * maxVarDepth; i++) {
      ifInGraph[i] = false;
      ifForcednotChoose[i] = false;
    }


    // 构建Qfunction
    // Qf <- BuildQf(S, timeAllowed)
    TreeExp F = BuildF(s);

    Long t1 = System.currentTimeMillis();

    //total number of updates used in this step
    updatesIntotal = 0;

    // 生成随机动作，计算q值，然后根据梯度上升，不断更新q值，直至收敛或者超时
    //initialize action prob
    ArrayList<Double> actionProb = null; // 没有用上
    actionProb = UpdateAllwtProj(s, F);
    numberNodesUpdates += TreeExp.counter * updatesIntotal;
    // routine 不是很明白什么意思。
    System.out.println("Routine records:");
    System.out.println(bestNumAct); // 最好的动作
    System.out.println(highestScore); // 最高的Q值

    //state history is not useful any more
    //free the memory
    stateHistory.clear();
    stateHistory = null;

    //fnd conformant actions for each level。 找到每层对应的一致性动作
    ArrayList<ArrayList<PVAR_INST_DEF>> conformantActs = new ArrayList<>();
    SampleAction(s, conformantActs, bestNumAct);

    //ending work
    TreeExp.ClearHash();
    RDDL.ClearHash();

    timeUsedForCal += System.currentTimeMillis() - t1;
    if (ifPrintTraje) {
      System.out.println("********* trajectory actions **********");
      PrintTraje(conformantActs);
    }
    F = null;
    routine.clear();
    bestNumAct.clear();
    trans2Tree.clear();
    int2PVAR.clear();
    int2TYPE_NAME.clear();
    act2Int.clear();
    minimalProb.clear();
    routine = null;
    bestNumAct = null;
    trans2Tree = null;
    int2PVAR = null;
    int2TYPE_NAME = null;
    act2Int = null;
    minimalProb = null;
    routine = new HashMap<>();
    bestNumAct = new ArrayList<>();
    trans2Tree = new HashMap<>();
    int2PVAR = new ArrayList<>();
    int2TYPE_NAME = new ArrayList<>();
    act2Int = new HashMap<>();
    minimalProb = new ArrayList<>();

    // conformantActs 返回第0层的动作
    return conformantActs.get(0);
  }

  TreeExp BuildF(State s) throws EvalException {
    INSTANCE instance = _rddl._tmInstanceNodes.get(_sInstanceName);
    double cur_discount = 1;
    // Q function
    TreeExp F = new TreeExp(0.0, null);

    // initialize
    int actionCounter = 0;
    for (int h = 0; h < maxVarDepth; h++) {
      trans2Tree.put(h, new HashMap<>());
      for (PVAR_NAME p : s._alActionNames) {
        trans2Tree.get(h).put(p, new HashMap<>());
        // 实例名称
        ArrayList<ArrayList<LCONST>> terms = s.generateAtoms(p);
        // find list of types of the p_var_name
        // 动作变量的定义
        PVARIABLE_DEF pvariable_def = s._hmPVariables.get(p);
        // list of type names of the pvar do(action, ability)
        ArrayList<TYPE_NAME> theTypeNames = pvariable_def._alParamTypes;
        for (ArrayList<LCONST> t : terms) {
          trans2Tree.get(h).get(p).put(t, new TreeExp(actionCounter, null));
          int2PVAR.add(p); // 动作名
          int2TYPE_NAME.add(theTypeNames); // 动作中的参数
          minimalProb.add(-Double.MAX_VALUE);

          if (h == 0) {
            if (!act2Int.containsKey(p)) {
              act2Int.put(p, new HashMap<>());
            }
            act2Int.get(p).put(t, actionCounter);
          }
          actionCounter++; // 动作计数器
        }
      }
    } // 这个阶段建立了一个N层的，包含动作名和实例的层状结构

    //initialize and copy states for trajcotry
    TEState as = new TEState();
    State cs = new State();
    cs = (State) DeepCloneUtil.deepClone(s);
    as.init(cs);

    //calculate concurrency
    if (ifConstructConstraints) {
      System.out.println("Rebuild constraints system!!!!");
      sumVars = new ArrayList<>();
      sumLimits = new ArrayList<>();
      sumCoeffecients = new ArrayList<>();
      // 因子动作例如01001累计的位数的总数
      ifInSumConstraints = new Boolean[countActBits];
      Arrays.fill(ifInSumConstraints, false);
      try {
        as.AddExtraActionEff(trans2Tree.get(0), sumVars, sumLimits, sumCoeffecients);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        System.exit(0);
      }
      GetRandomTrajAct(as);
      ifConstructConstraints = false;
    }

    //deal with hard constraints
    int count = 0;
    // 在Mult ROS 领域中没有
    for (PVAR_NAME p : s._alActionNames) {
      if (Policy._extraEffects.containsKey(p)) {
        //addVars.addAll(c)
        ArrayList<TYPE_NAME> typenames = s._hmPVariables.get(p)._alParamTypes;
        HashMap<ArrayList<TYPE_NAME>, ArrayList<BOOL_EXPR>> possibleMaches = Policy._extraEffects
            .get(p);
        Iterator it = possibleMaches.entrySet().iterator();
        //first figure out which are the variables used in for this PVAR_NAME

        while (it.hasNext()) {
          Map.Entry pair = (Map.Entry) it.next();
          //first decide if the type of each parameter is a subclass of the type of parameters in the preconditions
          ArrayList<TYPE_NAME> constraintsTypeName = (ArrayList<RDDL.TYPE_NAME>) pair.getKey();

          if (TEState.IfSuperClassList(typenames, constraintsTypeName)) {
            for (ArrayList<LCONST> t : s.generateAtoms(p)) {

              //times each additional effects to the action variables
              for (BOOL_EXPR theAddEff : (ArrayList<BOOL_EXPR>) pair.getValue()) {
                RandomDataGenerator newR = new RandomDataGenerator();
                //laod the substituion of lvars into newsub
                //pass new sub to the sampling of the constraints
                HashMap<LVAR, LCONST> newSubs = new HashMap<>();
                //deal with each parameter appeared in the precondition
                for (int i = 0;
                    i < Policy._extraEffectsLVARS.get(p).get(constraintsTypeName).size(); i++) {
                  //important:
                  // we assume that there is no repetition of types in both the preconditions and action variable subs
                  LVAR theVar = (LVAR) Policy._extraEffectsLVARS.get(p).get(constraintsTypeName)
                      .get(i);
                  newSubs.put(theVar, t.get(i));
                }

                TreeExp theT = TEState.toTExp(theAddEff.sample(newSubs, s, newR), null);
                if (theT.Is0()) {
                  Policy.ifForcednotChoose[count] = true;
                  break;
                }
              }
              count++;
            }
          }
        }
      }
    }

    //build the super-child relations between all types in this instances
    //only do it once in each instances
    if (superClass.isEmpty()) {
      System.out.println("Rebuild super classes!!!!");
      OrderTypes(as);
    }

    //start calclulating trajectories
    int h = 0;

    routine.clear();

    double updatePerNode = 0.00001;

    if (!ifFirstStep) {
      updatePerNode = updatesIntotal == 0 ? 0.00001 : timeUsedForCal / numberNodesUpdates;
      System.out.println(
          "Dealt with number of nodes times updates: " + numberNodesUpdates + " using time "
              + timeUsedForCal);
      System.out.println("Estimate calculation time per node per update: " + updatePerNode);
    }

    for (; h < maxDepth; h++) {
      Policy.stateHistory.add(as.QuickCopy());
      ArrayList<PVAR_INST_DEF> trajeActs = new ArrayList<>();

      if (h < maxVarDepth) { // h 小于最大变量深度
        for (PVAR_NAME p : as._alActionNames) {
          for (ArrayList<LCONST> t : as.generateAtoms(p)) {
            // 从数中取第h层
            HashMap<PVAR_NAME, HashMap<ArrayList<LCONST>, TreeExp>> in1 = trans2Tree.get(h);
            HashMap<ArrayList<LCONST>, TreeExp> in2 = in1.get(p);
            TreeExp tr = in2.get(t);
            PVAR_INST_DEF theAct = new PVAR_INST_DEF(p._sPVarName, (Object) tr, t);
            trajeActs.add(theAct);
          }
        }
      } else { // h 大于最大变量深度
        int index = 0;
        for (PVAR_NAME p : as._alActionNames) {
          for (ArrayList<LCONST> t : as.generateAtoms(p)) {
            int realIndex = -1;
            for (int c = 0; c < sumVars.size(); c++) {
              if (sumVars.get(c).contains(index)) {
                realIndex = c;
              }
            }
            if (realIndex == -1) {
              trajeActs.add(new PVAR_INST_DEF(p._sPVarName, (Object) new TreeExp(0.5, null), t));
            } else {  // 合法动作
              trajeActs.add(new PVAR_INST_DEF(p._sPVarName,
                  (Object) new TreeExp((Double) randomAction.get(realIndex), null), t));
            }
            index++;

          }
        }
      }
      as.SetActNoCompute(trajeActs);
      TreeExp reward = null;

      // 记录输出到文件
      if (ifRecord) {
        Records r = new Records();
        r.fileAppend("BUIlding2", "-state-\n");
        for (PVAR_NAME p : as._alStateNames) {
          for (ArrayList<LCONST> t : as.generateAtoms(p)) {
            r.fileAppend("BUIlding2",
                p._sPVarName + t.toString() + as.getPVariableAssign(p, t).toString() + "\n");
          }
        }
//        r.fileAppend("BUIlding2", "-reward-\n");
//        r.fileAppend("BUIlding2", reward.toString() + "\n");
        r.fileAppend("BUIlding2", "-F-\n");
        r.fileAppend("BUIlding2", F.toString() + "\n");
      }

      cur_discount *= instance._dDiscount;
      // output the current achieved depth
      if (ifPrint) {
        System.out.println("finish: " + (h + 1) + " steps.");
      }

      // 估算计算时间
      if (searchDepth == -1) {
        int numOfNodes = TreeExp.counter;
        long timeLeft = _timeAllowed - (System.currentTimeMillis() - t0);
        if (!ifFirstStep) { // 如果不是第一步
          //possibly maxvardepth is bigger than the searchdepth being used
          int currentRealConformant = maxVarDepth > h ? h : maxVarDepth;
          if (numOfNodes * updatePerNode * 500 * Math.pow(1.5, currentRealConformant)
              > timeLeft * 0.9) {
            h++;
            break;
          }
        } else { // 第一步
          if (timeLeft < _timeAllowed * 0.3) {
            h++;
            System.out.println("Estimating update time built to depth = " + h);
            break;
          }
        }
      }

      if (h != maxDepth - 1) {
        as.computeNextState(_random); // 计算到下一个状态
        reward = TEState.toTExp(as._reward.sample(new HashMap<LVAR, LCONST>(), as, _random), null);
        // 一层一层构建出F的表达式
        // 这里面也有一个问题：就是状态是如何处理的？
        F = (TreeExp) RDDL
            .ComputeArithmeticResult(F, RDDL.ComputeArithmeticResult(reward, cur_discount, "*"),
                "+");

        as.advanceNextState(); // 真正演化到下一个状态
      }

      // check time，超时停止计算
      if ((System.currentTimeMillis() - t0) > _timeAllowed) {
        h++;
        System.out.println("Forced to stop building tree!");
        break;
      }
    }

    // 当前轮数小于5
    if (currentRound < 5) { // 统计深度？
      _visCounter.depthInTotal += h; // 不是很明白什么意思
    }

    System.out.println(h);
    System.out.println("finish: " + (h + 1) + " steps.");

    //double check that maxvardepth is at most same as h
    if (maxVarDepth > h) {
      maxVarDepth = h;
    }

    // 输出统计多少层变量
    if (ifPrint) {
      System.out.println("We are finally using " + maxVarDepth + "-layer variables");
    }

    //record the maxvardepth used temporarily
    //later in update step decide if add this to statistics
    //based on if having useful updates
    tmpVarDepthChange = maxVarDepth;
    tmpSearchDepthChange = maxDepth;

    return F;
  }

  /**
   * 计算随机动作的边缘概率
   */
  public void GetRandomTrajAct(TEState s) {
    for (int c = 0; c < sumVars.size(); c++) {
      int numVarBit = sumVars.get(c).size();
      int numVar = 0;
      for (int j = 0; j < numVarBit; j++) {
        numVar += Policy.sumCoeffecients.get(c).get(j);
      }

      ArrayList<Double> comb = new ArrayList<Double>();
      Double numInTotal = 0.0;

      // caculate the number of choose k from n
      for (int k = 0; k <= sumLimits.get(c); k++) {
        int max = numVar;
        double resu = 1;
        for (int j = 1; j <= k; j++) {
          resu *= max;
          max--;
        }
        for (int j = 2; j <= k; j++) {
          resu /= j;
        }
        // now res the is number of combs (choose j from countActBits)
        comb.add(resu);
        numInTotal += resu;
      }
      // now cal the marginal for random policy
      double marRandom = 0;
      for (int k = 1; k <= sumLimits.get(c); k++) {
        marRandom += Double.valueOf(k) / numVar * comb.get(k)
            / numInTotal;
      }
      randomAction.add(marRandom);
    }
  }

  /**
   * 类型 父类 子类 Mult ROS 没有使用
   */
  public void OrderTypes(TEState s) {
    Iterator thisIterator = s._hmTypes.entrySet().iterator();
    while (thisIterator.hasNext()) {
      Map.Entry entry = (Map.Entry) thisIterator.next();
      OBJECT_TYPE_DEF val = (OBJECT_TYPE_DEF) entry.getValue();
      if (val._typeSuperclass != null) {
        TYPE_NAME theSuper = val._typeSuperclass;
        if (!superClass.containsKey(val._sName)) {
          superClass.put(val._sName, new ArrayList<>());
        }
        superClass.get(val._sName).add(theSuper);
        if (!childClass.containsKey(theSuper)) {
          childClass.put(theSuper, new ArrayList<>());
        }
        childClass.get(theSuper).add(val._sName);
      }
    }
  }

  public ArrayList<Double> UpdateAllwtProj(State s, TreeExp F) throws EvalException {
    tmpUpdatesChange = 0;

    // 动作概率，初始化
    ArrayList<Double> actionProb = new ArrayList<Double>();
    for (int i = 0; i < countActBits * maxVarDepth; i++) {
      actionProb.add(0.0);
    }

    int randomRestart = 0;
    roundRandom = 0;
    roundUpdates = 0;
    roundSeen = 0;

    // Record the best actionProb that gets the highest Q value in the F tree
    ArrayList<Double> bestActionProb = new ArrayList<>();
    for (int i = 0; i < countActBits; i++) {
      bestActionProb.add(0.0);
    }

    double bestQ = Double.NEGATIVE_INFINITY;
    ArrayList<Double> completeBest = new ArrayList<Double>();
    for (int i = 0; i < countActBits * maxVarDepth; i++) {
      completeBest.add(0.0);
    }

    //topological ordering, record two things
    //1. number of non-leaf nodes
    //2. action variables in the graph (ifIngraph[])
    ArrayList<TreeExp> que = new ArrayList<TreeExp>();
    if (ifReverseAccu) {
      que = F.TopologQueue(ifTopoSort);
      if (currentRound < 5) {
        _visCounter.sizeInTotal += F.numOfNonLeaf;
      }
      System.out.println("Number of non-leaf nodes: " + F.numOfNonLeaf);
      System.out.print("Action variables in graph: ");
      for (int i = 0; i < ifInGraph.length - 1; i++) {
        System.out.print(ifInGraph[i] + ", ");
      }
      System.out.println(ifInGraph[ifInGraph.length - 1]);
    }

    stopAlgorithm = false;
    //looping over rando restarts
    while (!stopAlgorithm) {
      //flag for convergence
      ifConverge = false;

      //updates that being done in this random restart
      //note that these updates may not be all used in statistics
      double updatedasCounter = 0;

      //initialize the action bits to 0 and 1 randomly
      for (int i = 0; i < actionProb.size(); i++) {
        // 使用随机策略
//        actionProb.set(i, _random.nextUniform(0, 1.0));
        actionProb.set(i, 1.0);
      }

      // 为什么投射了两次？
      try {
        Projection(actionProb);
      } catch (Exception e2) {
        // TODO Auto-generated catch block
        e2.printStackTrace();
      }

      //evaluate the initial action bits
      // 试着注释了一下，没有什么区别
      try {
        Projection(actionProb);
      } catch (Exception e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }

      HashMap<TreeExp, Double> valRec = new HashMap<>();
      HashMap<TreeExp, Double> gradRec = new HashMap<>();

      // 计算初始动作概率的Q
      //initialize oldQ to be realvalue calculated with initial action prob
      double oldQ = F.RealValue(actionProb, valRec);

      if (ifRecordRoutine) {
        UpdateRoutine(F, s, actionProb, true);
      }

      // this is used to judge whether Q has been changed
      double initialQ = oldQ;
      // 输出初始的动作概率和Q
      if (ifPrintInitial) {
        System.out.println(actionProb + " " + initialQ);
      }

      if (ifPrint) {
        System.out.println("Q is initlaized to: " + oldQ);
      }

      if (oldQ > bestQ) {
        bestQ = oldQ;
        // 本次选择的动作
        for (int a = 0; a < countActBits; a++) {
          bestActionProb.set(a, actionProb.get(a));
        }
        // 所有动作的概率
        for (int a = 0; a < actionProb.size(); a++) {
          completeBest.set(a, actionProb.get(a));
        }
      }

      //dead bit record
      //if during this random restart, certain bits never change, it means that Q is not related to it
      //set it to be 0
      //only for top level
      ArrayList<Boolean> ifthisBitChange = new ArrayList<Boolean>();
      for (int a = 0; a < actionProb.size(); a++) {
        ifthisBitChange.add(false);
      }

      // 输出动作的序号，对应概率
      if (ifPrintBit) {
        System.out.println();
        for (int a = 0; a < actionProb.size(); a++) {
          System.out.println("a for " + "v" + a + " " + actionProb.get(a));
        }
        System.out.println();
      }

      //initialize newQ
      double newQ = 0; // this will be recalculated later
      //one random restart
      //quit when either ifconverge = true (this is udpated in fndalpha)
      //or running out of time
      while (!ifConverge && !stopAlgorithm) {
        //calculate delta 计算delta
        ArrayList<Double> delta = new ArrayList<Double>();
        try {
          F.RevAccGradient(ifTopoSort, que, delta, actionProb);
        } catch (Exception e) {
          e.printStackTrace();
        }
        //do updates for each bit
        if (ifPrintBit) {
          for (int i = 0; i < actionProb.size(); i++) {
            System.out.println("d for " + "v" + i + " " + delta.get(i));
          }
        }

        //record if a bit is changed
        for (int i = 0; i < delta.size(); i++) {
          double d = delta.get(i);
          if (d != 0) {
            ifthisBitChange.set(i, true);
          }
        }

        updatedasCounter++;

        //this step updates prob and return the Q
        // 更新概率和返回Q值
        // TODO
        newQ = FndAlpha(s, F, actionProb, delta); // 动作的概率也修改了
        if (ifRecordRoutine) {
          UpdateRoutine(F, s, actionProb, true);
        }

        // 输出动作
        if (ifPrintBit) {
          for (int a = 0; a < actionProb.size(); a++) {
            System.out.println("a for " + "v" + a + " " + actionProb.get(a));
          }
        }

        //now alphas are changed so we need to clear the value record in the tree
        valRec.clear();

        if (ifPrint) {
          System.out.println("oldQ: " + oldQ + "\n");
          System.out.println("newQ: " + newQ + "\n");
        }
        oldQ = newQ;
        //we don't need to clear valrec again
        //because the value when calculating newQ can be reused in next iteration
        if (System.currentTimeMillis() - t0 > _timeAllowed * 0.95) {
          stopAlgorithm = true;
          break;
        }
      }

      //converged; continue to next random restart
      //record statics only if the Q value has been changed during the updates
      if (newQ != initialQ) {
        roundRandom++;
        roundUpdates += updatedasCounter;
        updatesIntotal += updatedasCounter;
        if (currentRound < 5) {
          _visCounter.updatesInTotal += updatedasCounter;
          _visCounter.randomInTotal++;
        }
        tmpUpdatesChange += updatedasCounter;
      }
      if (ifPrint) {
        if (ifConverge) {
          System.out.println("RR: " + randomRestart + "converged!");
        } else {
          System.out
              .println("RR: " + randomRestart + "forced to stop because running out of time.");
        }
      }
      //Get the Q value for this convergence
      if (newQ > bestQ) {
        if (ifPrint) {
          System.out.println("Previous best is: " + bestQ + " and now the Q is: " + newQ);
        }

        bestQ = newQ;
        for (int a = 0; a < countActBits; a++) {
          bestActionProb.set(a, actionProb.get(a));
        }
        for (int a = 0; a < actionProb.size(); a++) {
          completeBest.set(a, actionProb.get(a));
        }

        //if an action bit is not changed
        //set it to be false
        if (ifDefaultNoop) {
          for (int a = 0; a < countActBits; a++) {
            if (!ifthisBitChange.get(a)) {
              bestActionProb.set(a, 0.0);
            }
          }
        }
      } else {
        if (ifPrint) {
          System.out.println(
              "Failed to update Q. Previous best is: " + bestQ + " and now the Q is: " + newQ);
        }
      }
      if (ifDefaultNoop) {
        //System.out.println(ifthisBitChange);
        for (int a = 0; a < countActBits; a++) {
          //System.out.println(ifthisBitChange);
          if (!ifthisBitChange.get(a)) {
            bestActionProb.set(a, 0.0);
          }
        }
      }
    }
    if (ifPrintGrid) {
      System.out.println("In total: " + randomRestart);
    }
    //record how many random restart have been done
    String countingStr = new String();
    countingStr += "\n\n*************************\n"
        + "\n******** Summary ********\n"
        + "\n*************************\n";
    countingStr += "Number of Random Restart: " + roundRandom + "\n";
    countingStr += "Number of Updates: " + roundUpdates + "\n";
    countingStr += "Number of Actions Seen: " + roundSeen + "\n";
    System.out.println(countingStr);

    //printout the action probs
    if (ifPrintBit) {
      System.out.println("\nfinal action prob: ");
      for (double a : bestActionProb) {
        System.out.println(a);
      }
    }

    System.out.println("best: " + bestQ);

    if (tmpUpdatesChange != 0) {
      avgUpdates += tmpUpdatesChange;
      avgSearchDepth += tmpSearchDepthChange;
      //System.out.println("add " + tmpUpdatesChange + ", get " + avgUpdates);
      avgVarDepth += tmpVarDepthChange;
      effectiveSteps++;
      //System.out.println(tmpVarDepthChange + " " + tmpSearchDepthChange);
      //System.out.println("effective: " + effectiveSteps);
    }
    return bestActionProb;
  }


  /**
   * 找到最大的alpha？ 返回Q值
   */
  public double FndAlpha(State s, TreeExp F, ArrayList<Double> actionProb, ArrayList<Double> delta)
      throws EvalException {
    double maxAlpha = Double.MAX_VALUE;
    // 我们允许动作概率大于1
    // so first find the max prob and then acrrordingly find the space

    double maxProb = -1;
    for (double a : actionProb) {
      if (a > maxProb) {
        maxProb = a;
      }
    }
    maxProb += 1; //
    // traverse each bit to shrink maxalpha
    for (int i = 0; i < actionProb.size(); i++) {
      double possibleAlpha = -1;
      if (delta.get(i) > 0) {
        possibleAlpha = (maxProb - actionProb.get(i)) / Math.abs(delta.get(i));
      }
      if (delta.get(i) < 0) {
        possibleAlpha = (actionProb.get(i) - (-1)) / Math.abs(delta.get(i));
      }
      if (possibleAlpha != -1 && possibleAlpha < maxAlpha) {
        maxAlpha = possibleAlpha;
      }
    }
    //if we do concurrency projection then we need to again shrink the alpha by constraint the sum of prob be no bigger than
    //concurrency
    //System.out.println("max alpha is: " + maxAlpha);
    //now try alpha from 0 to maxAlpha
    double bestAlpha = 0;
    double bestQ = Double.NEGATIVE_INFINITY;
    ArrayList<Double> tempActProb = new ArrayList<Double>();
    for (int i = 0; i < actionProb.size(); i++) {
      tempActProb.add(0.0);
    }

    ArrayList<Double> bestActProb = new ArrayList<Double>();
    for (int i = 0; i < actionProb.size(); i++) {
      bestActProb.add(0.0);
    }
    // try to find the alpha with highest Q
    double realBest = -1;
    int shrinkCounter = 0;
    double realNorm = 0;
    while (true) {
      if (fixAlpha == -1) {
        bestAlpha = 0;
      } else {
        bestAlpha = fixAlpha;
        AlphaTime = 1;
      }
      for (int i = 1; i <= AlphaTime; i++) {
        if (fixAlpha == -1) {
          bestAlpha += maxAlpha / AlphaTime;
        }
        for (int j = 0; j < actionProb.size(); j++) {
          double d = delta.get(j);
          double update = bestAlpha * d;
          double now = actionProb.get(j);

          if (now + update < 0) {
            update = -now;
          }
          if (now + update > 1) {
            update = 1 - now;
          }
          //norm += update * update;
          double newVal = now + update;

          tempActProb.set(j, newVal);
        }
        try {
          Projection(tempActProb);
        } catch (Exception e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }

        // update bestQ
        HashMap<TreeExp, Double> valRec = new HashMap<TreeExp, Double>();
        try {

          double theQ = F.RealValue(tempActProb, valRec);
          if (theQ > bestQ) {
            bestQ = theQ;
            // update actionProb
            for (int j = 0; j < actionProb.size(); j++) {
              bestActProb.set(j, tempActProb.get(j));
            }
            realBest = bestAlpha;
            double norm = 0;
            for (int j = 0; j < actionProb.size(); j++) {
              double diff = tempActProb.get(j) - actionProb.get(j);
              norm += diff * diff;
            }
            norm = Math.sqrt(norm / actionProb.size());
            realNorm = norm;
          }
        } catch (EvalException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      if (fixAlpha == -1 && realBest == maxAlpha / AlphaTime) {
        maxAlpha /= AlphaTime;
        shrinkCounter++;

        if (shrinkCounter > MaxShrink) {
          break;
        }
        // System.out.println("Alpha is too large, will try alpha between 0 and "
        // + maxAlpha );
      } else {
        break;
      }
    }
    if (convergeNorm != -1 && realNorm <= convergeNorm) {
      ifConverge = true;
    }
    for (int j = 0; j < actionProb.size(); j++) {
      actionProb.set(j, bestActProb.get(j));
    }
    return bestQ;
  }


  public void Projection(ArrayList<Double> actionProb) throws Exception {

    // first masking actions that are not in the graph
    MaskingActions(actionProb);
    for (int h = 0; h < maxVarDepth; h++) {
      //set minimal prob of each var in this step to -1
      int base = h * countActBits;
      for (int i = 0; i < countActBits; i++) {
        minimalProb.set(base + i, -Double.MAX_VALUE);
      }
      //value record for each node
      //so long as this is in the same depth
      //the value record could be reused
      HashMap<LVAR, LCONST> valMap = new HashMap();
      //traverse each action var to see if any has forced condition
      //also find the highest marginal action variable in the exist force action
      int highestMarIndex = -1;
      double highestMar = -1.0;
      PVAR_NAME highestName = null;
      ArrayList<TYPE_NAME> highestTypeName = null;
      for (int i = 0; i < countActBits; i++) {
        int j = i + base;
        PVAR_NAME theP = int2PVAR.get(j);
        if (_forceActionCondForall.containsKey(theP)) {
          ArrayList<TYPE_NAME> typenames = int2TYPE_NAME.get(j);
          HashMap<ArrayList<TYPE_NAME>, ArrayList<BOOL_EXPR>> possibleMaches =
              Policy._forceActionCondForall.get(theP);
          Iterator it = possibleMaches.entrySet().iterator();
          //traverse and find each cond associated with it
          while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            //first decide if the type of each parameter is a subclass of the type of parameters in the preconditions
            ArrayList<TYPE_NAME> constraintsTypeName = (ArrayList<RDDL.TYPE_NAME>) pair.getKey();
            if (TEState.IfSuperClassList(typenames, constraintsTypeName)) {
              //traverse each cond
              for (BOOL_EXPR theforceCond : (ArrayList<BOOL_EXPR>) pair.getValue()) {
                RandomDataGenerator newR = new RandomDataGenerator();
                Object theCondVal = null;
                try {
                  theCondVal = theforceCond.sample(valMap, stateHistory.get(h), newR);
                  double theCondDouble = -1;
                  if (theCondVal instanceof Double) {
                    theCondDouble = (Double) theCondVal;
                  } else {
                    theCondDouble = ((TreeExp) theCondVal).RealValue(actionProb, new HashMap<>());
                  }

                  if (theCondDouble > 1 || theCondDouble < 0) {
                    throw new Exception(
                        "value of forcing action condition can only be within 0 ~ 1!");
                  }
                  if (theCondDouble > minimalProb.get(j)) {
                    minimalProb.set(j, theCondDouble);
                  }
                } catch (EvalException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
              }
            }
          }
        }
        //traverse each action variable to check exsits force action
        //find the one with highest marginals
        if (_forceActionCondExist.containsKey(theP)) {
          ArrayList<TYPE_NAME> typenames = int2TYPE_NAME.get(j);
          HashMap<ArrayList<TYPE_NAME>, ArrayList<Object>> possibleMaches =
              Policy._forceActionCondExist.get(theP);
          Iterator it = possibleMaches.entrySet().iterator();
          //traverse and find each cond associated with it
          while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            //first decide if the type of each parameter is a subclass of the type of parameters in the preconditions
            ArrayList<TYPE_NAME> constraintsTypeName = (ArrayList<RDDL.TYPE_NAME>) pair.getKey();

            if (TEState.IfSuperClassList(typenames, constraintsTypeName)) {
              boolean ifIgnor = false;
              if (h == 0 && Policy.ifForcednotChoose[j]) {
                ifIgnor = true;
              }
              if (!ifIgnor && actionProb.get(j) > highestMar) {
                highestMar = actionProb.get(j);
                highestMarIndex = j;
                highestName = theP;
                highestTypeName = constraintsTypeName;
              }
            }
          }
        }
      }

      //deal with the exist force action
      //only deal with the highest marginal variable
      //traverse each cond
      if (highestMarIndex != -1) {
        for (Object theforceCond : (ArrayList<Object>) _forceActionCondExist.get(highestName)
            .get(highestTypeName)) {
          RandomDataGenerator newR = new RandomDataGenerator();
          Object theCondVal = null;
          try {
            if (theforceCond instanceof BOOL_EXPR) {
              theCondVal = ((BOOL_EXPR) theforceCond).sample(valMap, stateHistory.get(h), newR);
            }
            if (theforceCond instanceof Double) {
              theCondVal = theforceCond;
            }
            double theCondDouble = -1;
            if (theCondVal instanceof Double) {
              theCondDouble = (Double) theCondVal;
            } else {
              theCondDouble = ((TreeExp) theCondVal).RealValue(actionProb, new HashMap<>());
            }

            if (theCondDouble > 1 || theCondDouble < 0) {
              throw new Exception("value of forcing action condition can only be within 0 ~ 1!");
            }
            if (theCondDouble > minimalProb.get(highestMarIndex)) {
              minimalProb.set(highestMarIndex, theCondDouble);
            }
          } catch (EvalException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      }

      //set everything
      for (int j = 0; j < countActBits; j++) {
        if (minimalProb.get(base + j) > 0) {
          actionProb.set(base + j, minimalProb.get(base + j));
        }
      }

      for (int c = 0; c < sumVars.size(); c++) {
        int concurrency = sumLimits.get(c);
        ArrayList<Integer> morethan1Counter = new ArrayList<Integer>();
        int numOfVar = sumVars.get(c).size();

        // calculate the sum of probs, max probs, more than 1 counter
        // for each variable depth
        for (int j = 0; j < maxVarDepth; j++) {
          morethan1Counter.add(0);
        }

        ArrayList<Double> sumOfProb = new ArrayList<Double>();
        for (int j = 0; j < maxVarDepth; j++) {
          sumOfProb.add(0.0);
        }

        ArrayList<Double> maxOfProb = new ArrayList<Double>();
        for (int j = 0; j < maxVarDepth; j++) {
          maxOfProb.add(Double.NEGATIVE_INFINITY);
        }
        for (int k = 0; k < numOfVar; k++) {

          int j = sumVars.get(c).get(k) + h * countActBits;

          int currentDepth = h;
          double newVal = actionProb.get(j);
          if (newVal > 1) {
            morethan1Counter.set(currentDepth, morethan1Counter.get(currentDepth) + 1);
          }
          // update sum to do projection

          sumOfProb.set(currentDepth,
              sumOfProb.get(currentDepth) + newVal * sumCoeffecients.get(c).get(k));
          if (newVal > maxOfProb.get(currentDepth)) {
            maxOfProb.set(currentDepth, newVal);
          }
        }

        // do projection for this step
        double sumP = sumOfProb.get(h);
        double adjustPar = Double.NaN;
        if (sumP > concurrency) {
          adjustPar = sumP - concurrency;
          double theRemain = adjustPar;
          int notZero = 0;
          for (int k = 0; k < numOfVar; k++) {
            int j = sumVars.get(c).get(k);
            int index = base + j;
            double theVal = actionProb.get(index);
            if (theVal > 0 && theVal != minimalProb.get(index)) {
              notZero += sumCoeffecients.get(c).get(k);
            }
          }
          while (theRemain > 0) {
            double del = theRemain / notZero;
            theRemain = 0;
            notZero = 0;
            for (int k = 0; k < numOfVar; k++) {
              int j = sumVars.get(c).get(k);
              int theCoe = sumCoeffecients.get(c).get(k);
              int index = base + j;
              double oldVal = actionProb.get(index);
              if (oldVal == 0 || oldVal == minimalProb.get(index)) {
                continue;
              }
              double newVal = oldVal - del;
              // if newVal < minimalProb
              // set back newVal to minimalProb
              // add back to remin
              if (minimalProb.get(index) > 0 && newVal < minimalProb.get(index)) {
                actionProb.set(index, minimalProb.get(index));
                theRemain += (minimalProb.get(index) - newVal) * theCoe;
              } else {
                if (newVal < 0) {
                  actionProb.set(index, 0.0);
                  theRemain += (0.0 - newVal) * theCoe;
                } else {
                  actionProb.set(index, newVal);
                }
              }
              if (actionProb.get(index) > 0) {
                notZero += theCoe;
              }
            }
          }
        }
      }
      //deal with sums
      for (int i = 0; i < sumVars.size(); i++) {
        if (ifEqual.get(i)) {
          double sumH = 0;
          int countInGraph = 0;
          for (int j = 0; j < sumVars.get(i).size(); j++) {
            int thecoe = sumCoeffecients.get(i).get(j);
            int index = sumVars.get(i).get(j) + base;
            if (ifInSumConstraints[index - base] && !ifForcednotChoose[index - base]) {
              sumH += actionProb.get(index) * thecoe;
              countInGraph += thecoe;
            }
          }
          double diff = sumLimits.get(i) - sumH;
          double addToBit = diff / countInGraph;
          for (int j = 0; j < sumVars.get(i).size(); j++) {
            int index = sumVars.get(i).get(j) + base;
            if (ifInSumConstraints[index - base] && !ifForcednotChoose[index - base]) {
              double old = actionProb.get(index);
              actionProb.set(index, old + addToBit);
            }
          }
        }
      }
    }
  }


  /**
   * 不在图中的动作概率设为0
   */
  public void MaskingActions(ArrayList<Double> actions) {
    for (int i = 0; i < actions.size(); i++) {
      if (!ifInGraph[i]) {
        actions.set(i, 0.0);
      }
    }
  }

  /**
   * 更新动作
   * 根据动作概率选择动作然后更新 bestNumAct
   */
  public void UpdateRoutine(TreeExp F, State s, ArrayList<Double> varVal, boolean ifStatistics)
      throws EvalException {
    //based on varVal, sample concrete action
    // 抽样动作
    ArrayList<Double> closestAct = SampleNumAct(varVal, s);
    ArrayList<Double> realAct = new ArrayList<Double>(); // 当前步骤需要选择的动作。
    for (int i = 0; i < countActBits; i++) {
      realAct.add(closestAct.get(i));
    }

    // evaluate the whole action including trajectories together
    if (!routine.containsKey(closestAct)) {
      double val = 0;
      HashMap<TreeExp, Double> valRec = new HashMap<>();
      val = F.RealValue(closestAct, valRec);
      routine.put(closestAct, val);
      // update highest score and the best init action
      if (val > highestScore) {
        highestScore = val;
        bestNumAct = closestAct;
        // could try to output every new update action including the trajectory
        if (ifPrintUpdateInRoutine) {
          PrintUpdateInRoutine(val, s, closestAct);
        }
      }

      // A routine is a short sequence of jokes, remarks, actions, or movements that forms part of a longer performance.
      // routine 一套动作
      if (!initActRoutine.containsKey(realAct)) {
        initActRoutine.put(realAct, val);
        if (ifStatistics) {
          roundSeen++;
          if (currentRound < 5) {
            _visCounter.SeenInTotal++;
          }
        }
      } else {
        if (val > initActRoutine.get(realAct)) {
          initActRoutine.put(realAct, val);
        }
      }
    }
  }


  //call if want to print the trajectory actions from the new num actions
  public void PrintUpdateInRoutine(double val, State s, ArrayList<Double> closetAct) {
    System.out.println("\nUpdated the val to:" + val);
    ArrayList<ArrayList<PVAR_INST_DEF>> conformantActs = new ArrayList<>();
    try {
      SampleAction(s, conformantActs, closetAct);
    } catch (EvalException e) {
      e.printStackTrace();
    }
    PrintTraje(conformantActs);
  }


  /**
   * 输出选择动作的轨迹，输出所有的conformantActs
   * @param conformantActs
   */
  public void PrintTraje(ArrayList<ArrayList<PVAR_INST_DEF>> conformantActs) {
    for (int h = 0; h < conformantActs.size(); h++) {
      System.out.println("h = " + h + ": " + conformantActs.get(h));
    }
  }


  //sample action from largest to smallest; build actions incrementally
  public void SampleAction(State s, ArrayList<ArrayList<PVAR_INST_DEF>> conformantActs,
      ArrayList<Double> useNum) throws EvalException {
    //find the best concrete action directly
    for (int h = 0; h < maxVarDepth; h++) {
      ArrayList<PVAR_INST_DEF> finalAction = new ArrayList<>();
      int counter = 0;
      for (PVAR_NAME p : s._alActionNames) {
        for (ArrayList<LCONST> t : s.generateAtoms(p)) {
          double resptNum = useNum.get(counter + h * countActBits);
          boolean ifChosen = false;
          Object actionVal = null;
          if (h == 0) {
            if (resptNum == 1.0) {
              ifChosen = true;
            }
            actionVal = true;
          } else {
            if (ifConformant) {
              if (resptNum == 1.0) {
                ifChosen = true;
              }
              actionVal = true;
            } else {
              if (resptNum > 0.0) {
                ifChosen = true;
              }
              actionVal = resptNum;
            }
          }
          if (ifChosen) {
            finalAction.add(new PVAR_INST_DEF(p._sPVarName, actionVal, t));
          }
          counter++;
        }
      }
      conformantActs.add(finalAction);
    }
  }

  /**
   * 根据varVal的概率，选择出动作 if conformant 1,0 not 1,0 else 0.23... 其中对约束也进行了处理
   */
  public ArrayList<Double> SampleNumAct(ArrayList<Double> varVal, State s) {
    int size = varVal.size();

    // find best action level wise
    // if use conformant, then do this for all trajectory level
    // otherwise only do this for first step
    int conformantDepth = ifConformant ? maxVarDepth : 1;
    boolean[] mute = new boolean[conformantDepth * countActBits];
    double[] res = new double[conformantDepth * countActBits];
    for (int h = 0; h < conformantDepth; h++) {
      ArrayList<Integer> sumForEachCons = new ArrayList<>();
      for (int c = 0; c < sumVars.size(); c++) {
        sumForEachCons.add(0);
      }
      for (int c = 0; c < sumVars.size(); c++) {
        int concurrency = sumLimits.get(c); // 并发
        int numVar = sumVars.get(c).size(); // 变量数目？
        for (int i = 0; i < concurrency; i++) {
          double max = -Double.MAX_VALUE;
          int maxIndex = -1;
          // 变量的数目
          for (int k = 0; k < numVar; k++) {
            int j = h * countActBits + sumVars.get(c).get(k);
            if (!mute[j] && varVal.get(j) > max) { // 最大的概率和index
              max = varVal.get(j);
              maxIndex = j;
            }
          }
          //the new added act bit might break some concurrency
          // need to check each of them by adding a sum to it
          int initialBit = maxIndex - h * countActBits;
          Boolean ifBreak = false;
          for (int c2 = 0; c2 < sumVars.size(); c2++) {
            if (sumVars.get(c2).contains(initialBit)) {
              int theIndex = sumVars.get(c2).indexOf(initialBit);
              int theSum = sumForEachCons.get(c2) + sumCoeffecients.get(c2).get(theIndex);
              if (theSum > sumLimits.get(c2)) {
                ifBreak = true;
                break;
              } else {
                sumForEachCons.set(c2, theSum);
              }
            }
          }
          if (ifBreak) {
            break;
          }
          if (max > randomAction.get(c)) {
            res[maxIndex] = 1;
            mute[maxIndex] = true;
          } else {
            break;
          }
        }
      }
    }

    // 概率大于0.5的选上
    for (int i = 0; i < countActBits; i++) {
      int index = i;
      if (!ifInSumConstraints[i] && varVal.get(index) > 0.5) {
        res[index] = 1;
      }
    }

    ArrayList<Double> numAct = new ArrayList<Double>();
    for (double r : res) {
      numAct.add(r);
    }

    //if we are not doing conformant
    //need to add fractional actions in the future steps
    if (!ifConformant) {
      for (int j = countActBits; j < size; j++) {
        numAct.add(varVal.get(j));
      }
    }
    return numAct;
  }

}
