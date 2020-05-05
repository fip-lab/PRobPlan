//
// Created by gdhu on 18-12-27.
//


#include <stdio.h>
#include <map>
#include <unistd.h>
#include <iterator>
#include <vector>
#include <algorithm>
#include <fstream>
#include <cmath>
typedef std::vector<std::tuple<int, int, int> > init_tuple_list;
typedef std::vector<std::tuple<int, int, int> > goal_tuple_list;


#define xRange 5
#define yRange 5
#define numberOfRobot 2
#define robotNamePrefix "robot"
#define numberOfMoveitActions 2
#define moveitActionNamePrefix "MoveitAction"
#define numberOfGoals 3
#define OtherActionName "OtherAction"
#define numberOfOtherAction 1
#define numberOfAbility 3 // This should be <= (numberOfRobot*numberOfMoveitActions)
#define probGotoDefault 0.9
#define gotoCostDefault 1
#define probDoDefault 1
#define goalUtilityDefault 100
#define rddlsimPath "/mnt/d/projects/ecl-workspace/rddlsim-master"
#define problemName "multi_ros_robot_inst_mdp__1"
#define tempFilePath "/mnt/d/projects/r2files"
#define descriptionFile "/home/gdhu/project/cplusprimer/AMyExp/ros_demo/multi_ros_robot_mdp.rddl"
#define problemFile "/home/gdhu/project/cplusprimer/AMyExp/ros_demo/multi_ros_robot_inst_mdp__1.rddl"

std::tuple<int, int, int> get_init_tuple(int id) {
  return std::make_tuple(id, rand() % xRange, rand() % yRange);
}
//prepared for none-robot Env, actions tobe done at random position.
std::tuple<int, int, int> get_goal_tuple(int id) {
  int randomActionNumber = rand() % (numberOfMoveitActions +
      numberOfOtherAction);
  return std::make_tuple(randomActionNumber, rand() % xRange, rand() %
      yRange);
}

int generate_problem(const init_tuple_list &itl, const goal_tuple_list & gtl) {
  using std::pow;
  std::ofstream out;
  out.open(problemFile, std::ios::trunc);
  int i, j, iGoto, jGoto;
  out << "non-fluents nf_multi_ros_robot_inst_mdp__1 {" << "\n";
  out << " domain = multi_ros_robot_mdp;" << "\n";
  out << " objects {" << "\n";
  out << " robot : { ";
  for (i = 1; i <= numberOfRobot; i++) {
    if (i > 1) out << ",";
    out << "r" << i;
  };
  out << "};\n";
  out << " action : { ";
  for (i = 1; i <= (numberOfMoveitActions + numberOfOtherAction); i++) {
    if (i > 1) out << ",";
    out << "a" << i;
  };
  out << "};\n";
  out << " xPos : { ";
  for (i = 1; i <= xRange; i++) {
    if (i > 1) out << ",";
    out << "x" << i;
  };
  out << "};\n";
  out << " yPos : { ";
  for (i = 1; i <= yRange; i++) {
    if (i > 1) out << ",";
    out << "y" << i;
  };
  out << "};\n";
  out << " goal : { ";
  for (i = 1; i <= numberOfGoals; i++) {
    if (i > 1) out << ",";
    out << "g" << i;
  };
  out << "};\n";
  out << " };" << "\n";
  out << "non-fluents {" << "\n";
  for (i = 1; i <= numberOfRobot; i++)
    out << " PROB_GOTO(r" << i << ") = " << probGotoDefault << ";\n";
  for (i = 1; i <= numberOfRobot; i++)
    for (j = 1; j <= (numberOfMoveitActions + numberOfOtherAction); j++)
      out << " DO_COST(r" << i << ",a" << j << ") = " << probDoDefault << ";\n";
  for (i = 1; i <= xRange; i++)
    for (j = 1; j <= yRange; j++)
      for (iGoto = 1; iGoto <= xRange; iGoto++)
        for (jGoto = 1; jGoto <= yRange; jGoto++)
          out << " GOTO_COST(x" << i << ", y" << j
//              << ", xGoto" << iGoto << ", yGoto" << jGoto
              << ", x" << iGoto << ", y" << jGoto
              << ") = " << round(100 * sqrt(pow(i - iGoto, 2) + pow(j - jGoto, 2))) / 100.0 << ";\n";
  for (i = 1; i <= numberOfRobot; i++)
    out << " GOAL_UTILITY(g" << i << ") = " << goalUtilityDefault << ";\n";
  i = 1;
  for (goal_tuple_list::const_iterator it = gtl.begin(); it != gtl.end(); ++it)
    out << " GOAL(g" << i++ << ",a" << std::get<0>(*it) << ", x" <<
        std::get<1>(*it) << ", y" << std::get<2>(*it) << ");\n";
  out << " };\n";
  out << "}\n";
  out << "instance multi_ros_robot_inst_mdp__1 {\n";
  out << " domain = multi_ros_robot_mdp;\n";
  out << " non-fluents = nf_multi_ros_robot_inst_mdp__1;\n";
  out << " init-state {\n";
  for (init_tuple_list::const_iterator it = itl.begin(); it != itl.end(); ++it)
    out << " robotAt(r" << std::get<0>(*it) << ", x" << std::get<1>(*it) << ",y " << std::get<2>(*it) <<");\n ";
  out << " };\n";
  out << " max-nondef-actions = " << floor(2 * numberOfRobot) << ";\n";
  out << " horizon = 10;\n";
  out << " discount = 1.0;\n";
  out << "}\n";
  out.close();
  return 0;
}

int main(){
  srand(time(NULL));

  // seting <x, y robot> tuples
  init_tuple_list itl;
  for (int i = 0; i < numberOfRobot; i++)
    itl.push_back(get_init_tuple(i));

  goal_tuple_list gtl;
  for (int i = 0; i < numberOfGoals; i++)
    gtl.push_back(get_goal_tuple(i));

  generate_problem(itl, gtl);

  return 0;
}