#! /usr/bin/env python
import rospy
import time
from multiprocessing.pool import ThreadPool

import os
import actionlib
from geometry_msgs.msg import Pose
from move_base_msgs.msg import MoveBaseActionGoal, MoveBaseAction, MoveBaseResult, MoveBaseGoal

import rosparam
from planner import RDDLPlanner
from ros_param_to_planning import PlanningProblem, RobotLocationStatus
from task import TaskDispatch
from spawn_objects import SpawnGazeboModel

class Plan2ROS():

    def __init__(self, robot_names):
        self.gen_robot_move_base_client_dict(robot_names)


    def feedback_callback(self, feedback):
        # rospy.loginfo('[Feedback] Going to Goal Pose...')
        pass
    
    # navigation goal
    def gen_goal(self, robot_name, task_name):
        goal = MoveBaseGoal()
        goal.target_pose.pose = self.get_goal_pose(task_name)
        goal.target_pose.header.frame_id = 'map'
        return goal

    # get goal pose from ros param server
    def get_goal_pose(self, task_name):
        goal_pose = Pose()
        goal_pose.position.x = rosparam.get_param(task_name+'/position/x')
        goal_pose.position.y = rosparam.get_param(task_name+'/position/y')
        goal_pose.position.z = rosparam.get_param(task_name+'/position/z')
        goal_pose.orientation.x = rosparam.get_param(task_name+'/orientation/x')
        goal_pose.orientation.y = rosparam.get_param(task_name+'/orientation/y')
        goal_pose.orientation.z = rosparam.get_param(task_name+'/orientation/z')
        goal_pose.orientation.w = rosparam.get_param(task_name+'/orientation/w')
        return goal_pose

    # create the connection to the action server
    def gen_robot_move_base_client_dict(self, robot_names):
        self.move_base_client_dict = {}
        for robot in robot_names:
            client = actionlib.SimpleActionClient('/'+robot+'/move_base', MoveBaseAction)
            client.wait_for_server()
            self.move_base_client_dict[robot] = client
        rospy.loginfo('gen_robot_move_base_client_dict done!')

    def wait_result(self, client):
        time.sleep(20)
        client.cancel_goal()
        print('[Result] State: %d'%(client.get_state()))
    
    # check task executed status
    def check_tast_status(self, task_dispatch_dic, robot_name_mapping_dict, clients_dict):
        for k in goto_tasks_dispatch:
            print(k,goto_tasks_dispatch[k])
            clt = robot_move_base_dict[planner_to_ros_robot_name_mapping[k]]
            if clt.get_state() != 3:
                return False
        return True
    
    def execute_do_action(self, robot_name, ability, task_name):
        rospy.loginfo('%s uses %s executing do action', robot_name, ability)
        # hard code for specifc tasks
        
        if task_name == 'task3' or task_name == 'task4':
            model = task_name + '_model'
            spawn_gazebo_model = SpawnGazeboModel()
            spawn_gazebo_model.spawn_new_model(model)
            rospy.loginfo('%s completed! add new ojbects to gazebo!', task_name)
        
        if task_name == 'task5' or task_name == 'task6':
            model = task_name + '_model'
            spawn_gazebo_model = SpawnGazeboModel()
            spawn_gazebo_model.delete_old_model(model)
            spawn_gazebo_model.spawn_new_model(model)
            rospy.loginfo('%s completed! add new ojbects to gazebo!', task_name)

        time.sleep(3)
        rospy.loginfo('executed success!')
        return 0
    


if __name__ == "__main__":
    # initializes the action client node
    rospy.init_node('plan_to_ros_client')
    rospy.loginfo('init ros node successfully!')

    simulated_plan_and_execution_time = rospy.get_time()

    # initializes rddl planning, generate problem json config file
    p = PlanningProblem()
    rospy.loginfo('problem json file got!')

    # instance for dispatch tasks (goto, do actions...)
    task_dispatch = TaskDispatch()

    # helper varialbe (task name and loacation tag mapping)
    task_tag_dict = p.task_location_tag_to_label_dict
    rospy.loginfo('task_tag_dict: %s', task_tag_dict)

    # robot location tag
    robot_status = RobotLocationStatus()

    simulated_plan_time = 0.0
    while True:
        
        # generate plan from rddl planner
        simulated_plan_time_start = rospy.get_time()
        pool = ThreadPool(processes=2)
        planner = RDDLPlanner(pool)
        planner.generate_plan()
        rospy.loginfo('plan has successfully generated!')
        simulated_plan_time_end = rospy.get_time()

        rospy.loginfo('plan end time %s , start time %s', simulated_plan_time_end, simulated_plan_time_start)
        simulated_plan_time = simulated_plan_time + simulated_plan_time_end - simulated_plan_time_start

        # mapping plan to ros
        plan_2_ros = Plan2ROS(p.robots)
    
        # get all goto actions from plan result
        # i.e. goto_tasks_dispatch = {'tiago1': 'l_t01', 'tiago2': 'l_t00',}
        goto_tasks_dispatch = task_dispatch.get_goto_task_dispatch_dict()

        # goto actions
        for robot_name, task_loction_tag in goto_tasks_dispatch.items():
            clt = plan_2_ros.move_base_client_dict[robot_name]
            task_tag_name = task_tag_dict[task_loction_tag]
            goal = plan_2_ros.gen_goal(robot_name, task_tag_name)
            rospy.logdebug('goal is %s', goal)
            # send navigation goal
            clt.send_goal(goal, feedback_cb=plan_2_ros.feedback_callback)
            rospy.loginfo('%s is movting to %s (%s).', robot_name, task_loction_tag, task_tag_name)

        
        while True:
            # count the number of goto_tasks
            goto_finish_sum = len(goto_tasks_dispatch)

            for robot_name, task_loction_tag in goto_tasks_dispatch.items():
                clt = plan_2_ros.move_base_client_dict[robot_name]
                rospy.loginfo('Robot %s, [Result] State: %d'%(robot_name, clt.get_state()))
                if clt.get_state() == 3:
                    rospy.loginfo('%s has navigated to %s.', robot_name, task_loction_tag)
                    goto_finish_sum = goto_finish_sum - 1
            
            if goto_finish_sum > 0:
                time.sleep(5)
            else:
                break
        
            
        #  get do tasks
        do_tasks_dispatch = task_dispatch.get_do_task_dispatch_dict()

        # execute do action
        for robot_name, ability in do_tasks_dispatch.items():
            task_name = robot_status.helper_convert_pose_to_location_tag(robot_name)
            rospy.loginfo('robot is at %s', task_name)
            if task_name is not 'unsure':
                task_ability = rosparam.get_param(task_name+'/ability')
                if task_ability == ability:
                    exe_code = plan_2_ros.execute_do_action(robot_name, ability, task_name)
                    # complete a task
                    if exe_code is 0:
                        rospy.loginfo('%s has been completed!', task_name)
                        # update uncompleted tasks 
                        p.update_uncompleted_tasks(task_name)
                        rospy.loginfo('problem has been updated!')

        # update ros simulated world state
        rospy.loginfo('update ros simulated world state')
        if not p.uncompleted_tasks:
            rospy.loginfo('all tasks has been completed!')
            rospy.loginfo('Shut down!')
            break
        # update poroblem status
        p.update_problem()
    
    rospy.loginfo('total plan time is %s', simulated_plan_time)

    simulated_plan_and_execution_time = rospy.get_time() - simulated_plan_and_execution_time

    rospy.loginfo('total simulated_plan_and_execution_time time is %s', simulated_plan_and_execution_time)

    portion = simulated_plan_time / simulated_plan_and_execution_time
    rospy.loginfo('portion is %s', str(portion))




    
    
    




    

    
    
    
    # while True:
    #     # step 1:
    #     # check if there is any uncompleted task
    #     if not p.uncompleted_tasks:
    #         rospy.loginfo('all task haven been complete!')
    #         break

    #     # step 2:
    #     # update world status (dump world status to json file, generate new problem file)
    #     p.update_problem()

    #     # step 3: get plan
    #     pool = ThreadPool(processes=2)
    #     planner = RDDLPlanner(pool)
    #     planner.generate_plan()

    #     # step 2: allocate tasks
    #     task_dict = task.get_goto_task_dispatch_dict()
    #     rospy.loginfo('task_dispatch: %s'%task_dict) 
    #     # break
    #     # task_dict = {'r02': 'l_t02', 'r00': 'l_t00'}

    #     # step 3: execute for 20s
    #     # create the connection to the action server
    #     plan_2_ros = Plan2ROS()
    #     plan_2_ros.load_params()

    #     robot_move_base_dict = plan_2_ros.get_robots_move_base_dict(robot_names)
        
    #     planner_to_ros_robot_name_mapping = {}
    #     planner_to_ros_robot_name_mapping['r00'] = 'robot1'
    #     planner_to_ros_robot_name_mapping['r01'] = 'robot2'
    #     planner_to_ros_robot_name_mapping['r02'] = 'robot3'

    #     planner_to_ros_task_name_mapping = {}
    #     planner_to_ros_task_name_mapping['l_t00']='task1'
    #     planner_to_ros_task_name_mapping['l_t01']='task2'
    #     planner_to_ros_task_name_mapping['l_t02']='task3'


    #     goto_tasks_dispatch = task_dict

    #     for k in goto_tasks_dispatch:
    #         print(k, goto_tasks_dispatch[k])
    #         clt = robot_move_base_dict[planner_to_ros_robot_name_mapping[k]]
    #         rbt_2_goal = plan_2_ros.get_goal_points(planner_to_ros_task_name_mapping[goto_tasks_dispatch[k]])
    #         clt.send_goal(rbt_2_goal, feedback_cb=plan_2_ros.feedback_callback)
    #         # clt.send_goal_and_wait(rbt_2_goal,)
    #     # wait    
    #     time.sleep(20)
    #     for k in goto_tasks_dispatch:
    #         robot = planner_to_ros_robot_name_mapping[k]
    #         clt = robot_move_base_dict[robot]
    #         clt.cancel_goal()
    #         print('Robot %s, [Result] State: %d'%(robot, clt.get_state()))
    #         if clt.get_state() == 3:
    #             completed_task = planner_to_ros_task_name_mapping[goto_tasks_dispatch[k]]
    #             print 'completed_task: ',completed_task
    #             print task.all_tasks
    #             task.remove_completed_task(completed_task)
    #     # TODO
    #     # break
        
    #     # check task status
    #     if plan_2_ros.check_tast_status(goto_tasks_dispatch, planner_to_ros_robot_name_mapping, robot_move_base_dict):
    #         print('all tasks have been completed!')
    #         break
    #     # step 4: 
    #     #        save status in file
    #     #        cancel goals
    #     count = count + 1
    #     if count>20:
    #         break

    #     # step 5: according to status, re launch planner to get new plan