#! /usr/bin/env python

import json
import yaml
import io
import os
import sys
import time
from decimal import *
# getcontext().prec = 10
from math import pow,sqrt
import rosparam
import rospy
from nav_msgs.srv import GetPlan, GetPlanRequest
from nav_msgs.msg import Odometry
from geometry_msgs.msg import Pose



def dumper(obj):
    """
    dumper object to json format
    """
    try:
        return obj.toJSON()
    except:
        return obj.__dict__

class PlanningProblem:
    """
    Generate Json File for Planning
    ROS load yaml config
    Calculate goto cost do cost etc
    """
    def __init__(self):
        self.load_params()
        self.index = 1
        self.description = "planning problem description"
        self.robots = []
        self.robot_at = []
        self.abilities = rosparam.get_param('abilities')
        self.locations = []
        self.task_tag_names = []
        self.goto_costs = []
        self.do_costs = []
        self.tasks = []
        self.orders = []
        self.robot_current_location_dict = {}
        self.generate_problem()
    
    def generate_robot(self):
        all_robots = rosparam.get_param('robots')
        robots = []
        for rbt in all_robots:
            if rbt['available']:
                self.robots.append(rbt['name'])
    
    def gen_robot_current_location_dict(self):
        for robot_name in self.robots:
            self.robot_current_location_dict[robot_name] = rosparam.get_param(robot_name+'/loaction_tag')
    
    def gen_robot_at(self):
        """
        robot may be at task location
        dynamic check locations 
        """
        self.robot_at = []
        for robot_name in self.robots:
            self.robot_at.append(self.robot_current_location_dict[robot_name])
            
    def generate_task_names(self):
        self.task_tag_names = rosparam.get_param('task_names')
    
    def generate_locations(self):
        if self.locations:
            self.locations.clear()
        for task in self.tasks:
            if not task.finished:
                self.locations.append(task.location)
        for robot_name, location in self.robot_current_location_dict.items():
            if location not in self.locations:
                self.locations.append(location)
    
    def generate_tasks(self):
        
        for t_name in self.task_tag_names:
            task = Task()
            task.tag_name = t_name
            task.task_name = rosparam.get_param(t_name+'/description')
            task.ability = rosparam.get_param(t_name+'/ability')
            task.location = rosparam.get_param(t_name+'/loaction_tag')
            task.utility = rosparam.get_param(t_name+'/utility')
            self.tasks.append(task)

    def generate_goto_cost(self):
        self.goto_costs = []
        for robot in self.robots:
            calc = CalculateGoalPathLength(robot, self.uncompleted_tasks)
            res = calc.get_uncompleted_task_plan_length()
            self.build_goto_cost(robot, res)
    
    def build_goto_cost(self, robot_name, goto_cost_dict):
        # robot-at
        status = RobotLocationStatus()
        # check if the robot has already been at the task location
        robot_at_task = status.helper_convert_pose_to_location_tag(robot_name)

        if robot_at_task == 'unsure':
            robot_at_task = rosparam.get_param(robot_name+'/loaction_tag')
        else:
            robot_at_task = rosparam.get_param(robot_at_task+'/loaction_tag')
            self.robot_current_location_dict[robot_name] = robot_at_task
        for task_name, plan_length in goto_cost_dict.items():
            goto_cost = GotoCost()
            goto_cost.start = robot_at_task
            goto_cost.dest = rosparam.get_param(task_name+'/loaction_tag')
            goto_cost.cost = plan_length
            self.goto_costs.append(goto_cost)
   
    def generate_task_order(self):
        for t_name in self.task_tag_names:
            order = TaskOrder()
            order.pre = rosparam.get_param(t_name+'/description')
            after = rosparam.get_param(t_name+'/must_be_completed_before')
            if not after == 'none':
                order.after = rosparam.get_param(after+'/description')
                self.orders.append(order)

    def update_task_order(self):
        self.orders = []
        for t_name in self.uncompleted_tasks:
            order = TaskOrder()
            order.pre = rosparam.get_param(t_name+'/description')
            after = rosparam.get_param(t_name+'/must_be_completed_before')
            if not after == 'none':
                order.after = rosparam.get_param(after+'/description')
                self.orders.append(order)    

    def generate_task_utility(self):
        for task in self.tasks:
            task.utility = 100

    def load_params(self):
        os.chdir("/home/gdhu/tiago_public_ws/src/tiago_simulation/mult_protap_sim/params/")
        paramlist = rosparam.load_file("spots.yaml", default_namespace=None)
        for params, ns in paramlist:
            rosparam.upload_params(ns, params)
    
    def dump_to_json_file(self):
        os.chdir("/home/gdhu/tiago_public_ws/src/tiago_simulation/mult_protap_sim/params/")
        with open('./ros_param_to_json.json', 'w') as f:
            json.dump(self, f, default=dumper, indent=2, sort_keys=True)
        rospy.loginfo('dump to json file successfully!')

    
    def generate_problem(self):
        self.generate_robot()
        self.gen_robot_current_location_dict()
        self.generate_task_names()
        self.gen_uncompleted_tasks()
        self.generate_tasks()
        self.generate_task_utility()
        self.generate_task_order()
        self.generate_goto_cost()
        self.generate_locations()
        self.gen_robot_at()
        self.dump_to_json_file()
        self.gen_task_location_tag_to_label_dict()
    
    def update_problem(self):
        rospy.loginfo('updating problem...')
        # goto cost, robot location
        self.generate_goto_cost()
        self.update_problem_locations()
        self.gen_robot_at()
        self.update_task_order()
        self.dump_to_json_file()
        rospy.loginfo('updating probem done.')
    
    def gen_task_location_tag_to_label_dict(self):
        self.task_location_tag_to_label_dict = {}
        for task in self.tasks:
            self.task_location_tag_to_label_dict[task.location] = task.tag_name
    
    def gen_uncompleted_tasks(self):
        self.uncompleted_tasks = self.task_tag_names
    
    def get_available_robots(self):
        self.available_robots = self.robots
    
    def update_uncompleted_tasks(self, task_name):
        
        for task_tag in self.task_tag_names:
            if task_tag == task_name:
                rospy.loginfo('update_uncompleted_tasks: removing %s.', task_tag)
                self.task_tag_names.remove(task_tag)
        rospy.loginfo('update_uncompleted_tasks: %s', self.task_tag_names)
        self.uncompleted_tasks = self.task_tag_names
        
        # update
        for task in self.tasks:
            if task.tag_name == task_name:
                self.tasks.remove(task)
    
    def update_problem_locations(self):
        
        temp_location = set()

        for task_tag_name in self.uncompleted_tasks:
            temp_location.add(rosparam.get_param(task_tag_name+'/loaction_tag'))
        
        for robot_name, location in self.robot_current_location_dict.items():
            temp_location.add(location)
        
        self.locations = sorted(temp_location)
        rospy.loginfo('update locations: %s', self.locations)
    
    # def update_robot_at(self, probem_status):
        
        



            
class RobotLocationStatus:

    def __init__(self):
        self.task_names = rosparam.get_param('task_names')
        self.task_poses_dict = {}
        self.load()
    
    
    def load(self):
        for task_name in self.task_names:
            self.task_poses_dict[task_name] = self.get_pose(task_name)

    def where_is_the_robot(self, robot_name):
        """
        check where is the robot
        child_frame_id: "base_footprint"
        pose: 
            pose: 
                position: 
                x: 2.36745281628
                y: -6.46416688754
        """
        odom_sub_name = rosparam.get_param(robot_name+'/namespace/odom')
        msg = rospy.wait_for_message(odom_sub_name, Odometry)
        return msg.pose.pose
    
    def helper_convert_pose_to_location_tag(self, robot_name):
        res = 'unsure'
        current_robot_pose = self.where_is_the_robot(robot_name)
        x0 = current_robot_pose.position.x
        y0 = current_robot_pose.position.y
        for task in self.task_names:
            x1 = self.task_poses_dict[task].position.x
            y1 = self.task_poses_dict[task].position.y
            dist = sqrt(pow(x1-x0,2) + pow(y1-y0,2))
            if dist < 0.2:
                res = task
                break
        return res

    def get_pose(self, label):
        pose = Pose()
        pose.position.x = rosparam.get_param(label+'/position/x')
        pose.position.y = rosparam.get_param(label+'/position/y')
        pose.position.z = rosparam.get_param(label+'/position/z')
        pose.orientation.x = rosparam.get_param(label+'/orientation/x')
        pose.orientation.y = rosparam.get_param(label+'/orientation/y')
        pose.orientation.z = rosparam.get_param(label+'/orientation/z')
        pose.orientation.w = rosparam.get_param(label+'/orientation/w')
        return pose





class CalculateGoalPathLength():
    
    def __init__(self, robot_name, uncompleted_tasks):
        self.robot_name = robot_name
        self.make_plan_service_name = rosparam.get_param(robot_name+'/namespace/make_plan')
        self.odom_sub_name = rosparam.get_param(robot_name+'/namespace/odom')
        self.tasks = uncompleted_tasks
        self.task_poses = []
        self.plan_req = GetPlanRequest()
        self.robot_status = RobotLocationStatus()
        
        # service
        rospy.wait_for_service(self.make_plan_service_name)
        self.make_plan_service = rospy.ServiceProxy(self.make_plan_service_name, GetPlan)
        self.get_task_poses()
        print('init success -->> %s'%(self.robot_name))
    
    def get_task_poses(self):
        self.task_poses_dict = {}
        for task in self.tasks:
            self.task_poses_dict[task] = self.get_pose(task)


    def get_pose(self, label):
        pose = Pose()
        pose.position.x = rosparam.get_param(label+'/position/x')
        pose.position.y = rosparam.get_param(label+'/position/y')
        pose.position.z = rosparam.get_param(label+'/position/z')
        pose.orientation.x = rosparam.get_param(label+'/orientation/x')
        pose.orientation.y = rosparam.get_param(label+'/orientation/y')
        pose.orientation.z = rosparam.get_param(label+'/orientation/z')
        pose.orientation.w = rosparam.get_param(label+'/orientation/w')
        return pose

    def wait_make_single_plan(self):
        self.raw_plan = self.make_plan_service(self.plan_req)   
   

    def get_uncompleted_task_plan_length(self):
        """
        calculate goto cost
        """
        msg = rospy.wait_for_message(self.odom_sub_name, Odometry)
        self.odom_msg = msg
        self.gen_plan_request_msg_header()
        
        goto_cost_dict = {}

        # check where is the robot
        robot_at_task = self.robot_status.helper_convert_pose_to_location_tag(self.robot_name)

        for task_name, task_pose in self.task_poses_dict.items():

            if task_name is robot_at_task: 
                rospy.loginfo('%s is at %s location!', self.robot_name, robot_at_task)
                continue

            self.plan_req.goal.pose = task_pose
            self.raw_plan = self.make_plan_service(self.plan_req)
            path_length = self.get_plan_path_length()

            if path_length < 0.2 : # set to defualt cost if failed to get a plan
                path_length = 10.0
                rospy.loginfo('%s navigates to %s path_length is too small, set to default length %s.', self.robot_name, task_name, path_length)
            goto_cost_dict[task_name] = path_length
            rospy.loginfo('%s navigates to %s is %s', self.robot_name, task_name, path_length)
        return goto_cost_dict

    def gen_plan_request_msg_header(self):
        self.plan_req.start.header.frame_id = self.robot_name+'/map'
        self.plan_req.start.pose = self.odom_msg.pose.pose
        self.plan_req.goal.header.frame_id = self.robot_name+'/map'

    def get_plan_path_length(self):
        result = 0
        poses = self.raw_plan.plan.poses
        for point in zip(poses[:-1], poses[1:]):
            x1 = point[1].pose.position.x
            x0 = point[0].pose.position.x
            y1 = point[1].pose.position.y
            y0 = point[0].pose.position.y
            # print (x1,x0,y1,y0)
            result += sqrt(pow(x1-x0,2) + pow(y1-y0,2))
        return result

class Task:

    def __init__(self):
        self.task_name = ''
        self.ability = ''
        self.location = ''
        self.utility = 0
        self.finished = False

class GotoCost:
    
    def __init__(self):
        self.start = ''
        self.dest = ''
        self.cost = 0

class TaskOrder:

    def __init__(self):
        self.pre = ''
        self.after = ''

def write_to_json(res):
    with open('./ros_param_to_json.json', 'w') as f:
        json.dump(res, f, default=dumper, indent=2, sort_keys=True)


# rospy.init_node('make_plan_client')

# p = PlanningProblem()

# print json.dumps(p, default=dumper, indent=2, sort_keys=True)

# write_to_json(p)

# load_params()
# robots = rosparam.get_param('robots')
# print robots
# pstatus = RobotLocationStatus()
# # p.where_is_the_robot('tiago1')
# res = pstatus.helper_convert_post_to_location_tag('tiago1')
# print res