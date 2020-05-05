package rddl;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by HuGuodong on 2019/1/29.
 */

public class TreeExp_AGP {

  class Term {

    double coefficient;
    int var;

    public Term(double c) {
      coefficient = c;
      var = -1;
    }

    public Term(double c, int v) {
      coefficient = c;
      var = v;
    }

    public Term(Term t) {
      var = t.var;
      coefficient = t.coefficient;
    }
  }

  // node type has "SUM"/"MIN"/"PRO"/"DIV"/"LEA"/EX
  public String expType;

  // if a node is leaf, then there is a term assigned to it
  public Term term = null;

  // if a node is not a leaf, then it has children
  public ArrayList<TreeExp_AGP> subExp = null;

  // pointer to the father
  public ArrayList<TreeExp_AGP> father = null;

  public static int counter = 0;

  public long numOfNonLeaf = 0;

  boolean ifInQ = false;

  int printCounter = 0;

  static final double ACTIVE_THRE = 500;

  public TreeExp_AGP() {
    counter++;
  }

  static HashMap<Double, TreeExp_AGP> nodesToNumber = new HashMap<>();

  public static void ClearHash() {
    nodesToNumber.clear();
    nodesToNumber = null;
    nodesToNumber = new HashMap<>();
  }

  /**
   * 从缓存中拿结点
   */
  public static TreeExp_AGP BuildNewTreeExp(double constant, TreeExp_AGP f) {
    if (Global.ifLift && nodesToNumber.containsKey(constant)) {
      TreeExp_AGP theNode = nodesToNumber.get(constant);
      return theNode;
    } else {
      TreeExp_AGP node = new TreeExp_AGP(constant, f);
      if (Global.ifRecordLift) {
        nodesToNumber.put(constant, node);
      }
      return node;
    }
  }

  /**
   * 判断结点是否是0
   */
  public boolean Is0() {
    if (term != null && term.coefficient == 0 && term.var == -1) {
      return true;
    } else {
      return false;
    }
  }

  public TreeExp_AGP(String type, TreeExp_AGP f) {
    if (father == null) {
      father = new ArrayList<>();
    }
    if (f != null) {
      father.add(f);
    }
    expType = new String(type);
    term = null;
    subExp = new ArrayList<>();
    counter++;
  }

  public TreeExp_AGP(double constant, TreeExp_AGP f) {
    if (father == null) {
      father = new ArrayList<>();
    }
    if (f != null) {
      father.add(f);
    }
    expType = "LEA";
    term = new Term(constant, -1);
    subExp = null;
    counter++;
  }

  // constructor for a variable and father
  // v 计数用？
  public TreeExp_AGP(int v, TreeExp_AGP f) {
    if (father == null) {
      father = new ArrayList<TreeExp_AGP>();
    }
    if (f != null)
      father.add(f);
    expType = "LEA";
    term = new Term(1, v);
    subExp = null;
    counter++;
  }

  // constructor for a variable and father
  // only used for THRE node
  public TreeExp_AGP(String type, double c, int v, TreeExp_AGP f) {
    if (father == null) {
      father = new ArrayList<>();
    }
    if (f != null)
      father.add(f);
    expType = new String(type);
    term = new Term(c, v);
    subExp = new ArrayList<>();
    counter++;
  }

  // take gradient of oriExp wrt var
  // fill in the content for gradient exp
  private double GetGradientForTerm(int v) {
    if (v == term.var) {
      return term.coefficient;
    } else {
      return 0;
    }
  }

  boolean IsNumber() {
    return term != null && term.var == -1;
  }

  public int GetVar() {
    return term.var;
  }

  /**
   * 计算树的值
   *
   * @param valRec 树
   */
  public double RealValue(ArrayList<Double> varVal, HashMap<TreeExp_AGP, Double> valRec)
      throws EvalException {
    if (valRec.containsKey(this)) {
      return valRec.get(this);
    } else {
      double r = 0;
      double rt = 0;
      if (expType.equals("LEA")) {
        if (term.var == -1) {
          r = term.coefficient;
        } else {
          r = varVal.get(term.var) * term.coefficient;
        }
      } else if (expType.equals("SUM")) {
        r = 0;
        for (TreeExp_AGP subT : subExp) {
          double v = subT.RealValue(varVal, valRec);
          r += v;
        }
      } else if (expType.equals("MIN")) { // minus
        r = subExp.get(0).RealValue(varVal, valRec);
        for (int i = 1; i < subExp.size(); i++) {
          r -= subExp.get(i).RealValue(varVal, valRec);
        }
      } else if (expType.equals("PRO")) { // 累乘
        r = 1;
        for (TreeExp_AGP subT : subExp) {
          double v = subT.RealValue(varVal, valRec);
          r *= v;
          if (r == 0) {
            break;
          }
        }
      } else if (expType.equals("DIV")) {
        if (subExp.size() != 2) {
          throw new EvalException(
              "DIVISION has to have two childean!");
        }
        double a = subExp.get(0).RealValue(varVal,
            valRec);
        double b = subExp.get(1).RealValue(varVal,
            valRec);
        r = a / b;
      } else if (expType.equals("EXP")) {
        if (subExp.size() != 1) {
          throw new EvalException(
              "realValue: EXP node can only have one child");
        }
        rt = subExp.get(0)
            .RealValue(varVal, valRec);
        r = Math.exp(rt);
      } else if (expType.equals("SIGMOID")) { // S型函数
        double a = subExp.get(0).RealValue(varVal,
            valRec);
        if (a > ACTIVE_THRE) {
          r = 1;
        } else {
          if (a < -ACTIVE_THRE) {
            r = 0;
          } else {
            r = Sigmoid(a);
          }
        }
      } else if (expType.equals("THR")) {
        double a = subExp.get(0).RealValue(
            varVal, valRec);
        if (a > term.coefficient) {
          r = term.coefficient;
        } else {
          if (a < term.var) {
            r = term.var;
          } else {
            r = a;
          }
        }
      } else if (expType.equals("POW")) {
        double a = subExp.get(0).RealValue(
            varVal, valRec);
        double b = subExp.get(1).RealValue(
            varVal, valRec);
        r = Math.pow(a, b);
      } else {
        throw new EvalException(
            "realValue: the operation"
                + expType
                + "is not supported");
      }
      valRec.put(this, r);
      return r;
    }
  }

  public double Sigmoid(double x) {
    double a = 1.0 / (1.0 + Math.exp(0.0 - x));
    return a;
  }

  public double GetPartialGradient(boolean sortGurentee,
      HashMap<TreeExp_AGP, Double> partialDev, ArrayList<Double> varVal,
      HashMap<TreeExp_AGP, Double> valRec) throws Exception {
    double thePartDev = 0;
    ArrayList<Double> selves = new ArrayList<Double>();
    ArrayList<Double> fathers = new ArrayList<Double>();
    for (TreeExp_AGP f : father) {
      if (f == null || !f.ifInQ) {
        continue;
      }
      // the partial gradient of f to this
      double selfDev = 1;
      // the partial gradient of Q to f
      double fatherPartDev = 0;
      if (partialDev.containsKey(f)) {
        fatherPartDev = partialDev.get(f);
      } else if (sortGurentee) {
        throw new EvalException("father hasn't been calculated");
      } else {
        fatherPartDev = f.GetPartialGradient(sortGurentee,
            partialDev, varVal, valRec);
      }
      if (f.expType.equals("SUM")) {
        selfDev = 1;
      } else if (f.expType.equals("PRO")) {
        int thisIndex = f.subExp.indexOf(this);
        for (int i = 0; i < f.subExp.size(); i++) {
          TreeExp_AGP sibling = f.subExp.get(i);
          if (thisIndex == i) {
            continue;
          }
          selfDev *= sibling.RealValue(varVal, valRec);
        }
      } else if (f.expType.equals("DIV")) {
        if (f.subExp.get(0) == this) {
          selfDev /= f.subExp.get(1)
              .RealValue(varVal, valRec);
        } else {
          selfDev = (0 - f.subExp.get(0).RealValue(varVal,
              valRec))
              / Math.pow(
              f.subExp.get(1).RealValue(varVal,
                  valRec), 2);
        }
      } else if (f.expType.equals("MIN")) {
        if (f.subExp.get(0) == this) {
          selfDev = 1;
        } else {
          selfDev = -1;
        }
      } else if (f.expType.equals("EXP")) {
        selfDev = Math.exp(this.RealValue(varVal,
            valRec));
      } else if (f.expType.equals("SIGMOID")) {
        double a = this.RealValue(varVal, valRec);
        if (a > ACTIVE_THRE) {
          selfDev = Math.pow(10, -5);
        } else if (a < -ACTIVE_THRE) {
          selfDev = -Math.pow(10, -5);
        } else {
          selfDev = Sigmoid(a)
              * (1 - Sigmoid(a));
        }
      } else if (f.expType.equals("THR")) {
        selfDev = 1;
      } else if (f.expType.equals("POW")) {
        if (f.subExp.get(0) == this) {
          double b = f.subExp.get(1)
              .RealValue(varVal,
                  valRec);
          double a = f.subExp.get(0)
              .RealValue(varVal,
                  valRec);
          selfDev = b
              * Math.pow(a, b - 1);
        } else {
          double b = f.subExp.get(1)
              .RealValue(varVal,
                  valRec);
          double a = f.subExp.get(0)
              .RealValue(varVal,
                  valRec);
          selfDev = Math.pow(a, b)
              * Math.log(a);
        }
      } else {
        throw new Exception(
            "partial gradient wrong!");
      }

      thePartDev += selfDev * fatherPartDev;
      selves.add(selfDev);

      fathers.add(fatherPartDev);
    }
    if (thePartDev == 0) {
      TreeExp_AGP a = this;
    }
    partialDev.put(this, thePartDev);
    if (thePartDev == 0) {
      int a = 1;
    }
    return thePartDev;

  }
}
