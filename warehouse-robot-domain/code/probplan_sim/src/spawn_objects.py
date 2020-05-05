#! /usr/bin/env python

from gazebo_msgs.srv import *
import rospy
import os
import roslib
from geometry_msgs.msg import Pose
import rosparam

class SpawnGazeboModel():

    def __init__(self):
        rospy.loginfo("Waiting for gazebo services...")
        # rospy.init_node("spawn_objects")
        
        

    def spawn_new_model(self, task_model_name):
        rospy.loginfo('waiting for spawn model service...')
        rospy.wait_for_service("gazebo/spawn_sdf_model")
        rospy.loginfo('waiting for spawn model service success.')
        srv_spawn_model = rospy.ServiceProxy("gazebo/spawn_sdf_model", SpawnModel)

        model_name = rosparam.get_param(task_model_name+'/name')
        file_location = '/home/gdhu/tiago_public_ws/src/tiago_simulation/tiago_gazebo/models/'+model_name+'/'+model_name+'.sdf'
        f = open(file_location)
        xml_string =  f.read()

        model_name_in_gazebo = rosparam.get_param(task_model_name+'/model_name')

        req = SpawnModelRequest()
        req.model_name = model_name_in_gazebo
        req.model_xml = xml_string
        req.initial_pose = self.get_pose(task_model_name)

        res = srv_spawn_model(req)
        if res.success == True:
            rospy.loginfo(res.status_message + ' ' + model_name)
        else:
            rospy.logerr('Error: ' + res.status_message + ' ' + model_name)
    
    def delete_old_model(self, model_name):
        rospy.loginfo('waiting for delete model service...')
        rospy.wait_for_service("gazebo/delete_model")
        rospy.loginfo('waiting for delete model service success.')
        srv_delete_model = rospy.ServiceProxy("gazebo/delete_model", DeleteModel)

        req = DeleteModelRequest()
        req.model_name = model_name 
        exist = True
        try:
            res = srv_delete_model(req)
            rospy.loginfo(res)
        except rospy.ServiceException, e:
            exist = False
            rospy.logwarn("Model %s does not exist in gazebo.", model_name)
        
        if exist:
            rospy.logwarn("Model %s has been deleted from gazebo.", model_name)

    def get_pose(self, task_model_name):
        pos = Pose()
        pos.position.x = rosparam.get_param(task_model_name+'/position/x')
        pos.position.y = rosparam.get_param(task_model_name+'/position/y')
        pos.position.z = rosparam.get_param(task_model_name+'/position/z')
        pos.orientation.x = rosparam.get_param(task_model_name+'/orientation/x')
        pos.orientation.y = rosparam.get_param(task_model_name+'/orientation/y')
        pos.orientation.z = rosparam.get_param(task_model_name+'/orientation/z')
        pos.orientation.w = rosparam.get_param(task_model_name+'/orientation/w')
        return pos

    def load_params(self):
        os.chdir("/home/gdhu/tiago_public_ws/src/tiago_simulation/mult_protap_sim/params/")
        paramlist = rosparam.load_file("spots.yaml", default_namespace=None)
        for params, ns in paramlist:
            rosparam.upload_params(ns, params)



if __name__ == "__main__":
    
    sp = SpawnGazeboModel()
    sp.load_params()

    # coke_can_slim bifrutas_tropical_can
    # sp.spawn_new_model('task3_model')
    sp.spawn_new_model('task5_model')