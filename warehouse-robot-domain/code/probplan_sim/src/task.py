#! /usr/bin/env python
import rospy

class TaskDispatch():
    """
    read result from planner result file
    map results to ROS
    send command to robots
    """
    def get_goto_task_dispatch_dict(self):
        goto_list = self.helper_get_action_list('goto')
        goto_in_num = self.check_action_in_line_num('goto')
        do_in_num = self.check_action_in_line_num('do')
        goto_task_dispatch = {}
        if goto_in_num == 0:
            return goto_task_dispatch
        if goto_in_num > do_in_num and do_in_num != 0:
            return goto_task_dispatch
        
        if goto_list[0]:
            for act in goto_list[0]:
                goto_task_dispatch[act[5:11]] = act[12:17]
        rospy.loginfo('goto_task_dispatch: %s', goto_task_dispatch)
        return goto_task_dispatch
    
    def get_do_task_dispatch_dict(self):
        do_list = self.helper_get_action_list('do')
        do_in_num = self.check_action_in_line_num('do')
        goto_in_num = self.check_action_in_line_num('goto')
        do_task_dispatch = {}
        if do_in_num == 0:
            return do_task_dispatch
        if do_in_num > goto_in_num and goto_in_num != 0:
            return do_task_dispatch
        if do_list[0]:
            for act in do_list[0]:
                do_task_dispatch[act[3:9]] = act[10:-1]

        rospy.loginfo('do_task_dispatch: %s', do_task_dispatch)
        return do_task_dispatch
    
    def helper_get_action_list(self, action_name):
        action_list = []
        with open('/home/gdhu/projects/test/files/plan.txt', 'r') as f:
            for line in f:
                if line.find(action_name)!=-1:
                    line = line.replace('[','').replace(']','').replace(';,','|').replace(';','').replace(' ','').rstrip('\n')
                    actions = line.split('|')
                    temp_list = []
                    for act in actions:
                        if act.find(action_name)!=-1:
                            temp_list.append(act)
                    action_list.append(temp_list)
        return action_list
    
    def check_action_in_line_num(self, action_name):
        result = 0
        with open('/home/gdhu/projects/test/files/plan.txt', 'r') as f:
            for num, line in enumerate(f, 1) :
                if line.find(action_name)!=-1:
                    result = num
                    break
        return result

if __name__ == "__main__":
    task = TaskDispatch()
    print task.get_goto_task_dispatch_dict()
    print task.get_do_task_dispatch_dict()