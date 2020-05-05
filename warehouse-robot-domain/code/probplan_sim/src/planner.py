#! /usr/bin/env python

import subprocess
import os
import shlex
import threading
import time
from multiprocessing.pool import ThreadPool
import rospy

class RDDLPlanner():

    def __init__(self, thread_pool):
        self.pool = thread_pool
    
    # start rddl server, write plan to file
    def launch_planner_client(self):
        rospy.logdebug('planner client starts!')
        os.chdir("/home/gdhu/projects/planner/copyb/planner")
        cmd = '''./run_ros_planner_clt.sh rddl.competition.Team3 multi_ros_robot_inst_mdp__1 2323'''
        result = os.popen(cmd)
        # print(result.read())
        rospy.logdebug('planner client finished!')
        return True
    
    # launch rddl planner client
    def launch_planner_server(self):
        rospy.logdebug('planner server starts!')
        os.chdir("/home/gdhu/projects/server/rddlsim")
        rospy.logdebug(os.getcwd())
        cmd = '''./run_ros_planner_srv.sh rddl.competition.Server /home/gdhu/projects/test/files | grep "** Actions received:" | cut -c 22- > /home/gdhu/projects/test/files/plan.txt'''
        result = os.popen(cmd)
        # print(result.read())
        rospy.logdebug('planner server finished!')
        return True
    
    def launch_planner_gen_problem(self):
        rospy.logdebug('planner_gen_problem starts!')
        os.chdir("/home/gdhu/projects/test")
        rospy.logdebug(os.getcwd())
        cmd = '''./run_planner_gen_problem.sh rosgen.fromros.ParamToPlanningProblem'''
        result = os.popen(cmd)
        rospy.logdebug(result.read())
        rospy.logdebug('generate planning problem file successfully!')
        return True
    
    def generate_plan(self):

        # generate planning problem
        self.launch_planner_gen_problem()

        # start planner server
        async_server_result = self.pool.apply_async(self.launch_planner_server)
        # wait 5 secs
        time.sleep(5)
        # start planner client
        async_client_result = self.pool.apply_async(self.launch_planner_client)

        rospy.logdebug('planner server result %s', async_server_result.get())
        rospy.logdebug('planner client result %s', async_client_result.get())
        rospy.logdebug('plan resulf file has been generated!')

if __name__ == "__main__":
    print('init node')
    pool = ThreadPool(processes=2)
    planner = RDDLPlanner(pool)
    # planner.generate_plan()
    # planner.launch_planner_gen_problem()
    planner.generate_plan()