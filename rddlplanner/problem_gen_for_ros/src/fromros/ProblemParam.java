package rosgen.fromros;

import com.alibaba.fastjson.JSON;
import rosgen.rosgenjson.FileUtil;

import java.util.List;

public class ProblemParam {
    int index;
    List<String>  robots;
    List<String>  robotAt;
    List<String> taskNames;
    List<String> abilities;
    List<String> locations;
    List<PreTask> orders;
    List<GotoCost> gotoCosts;
    List<Task> tasks;
    double moveProb = 0.9;
    double doProb = 0.9;

    class Task{
        String taskName;
        String ability;
        String location;
        int utility = 1;

        int priority;
        boolean finished;

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
        }

        public String getAbility() {
            return ability;
        }

        public void setAbility(String ability) {
            this.ability = ability;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getUtility() {
            return utility;
        }

        public void setUtility(int utility) {
            this.utility = utility;
        }

        @Override
        public String toString() {
            return "Task{" +
                    "taskName='" + taskName + '\'' +
                    ", ability='" + ability + '\'' +
                    ", location='" + location + '\'' +
                    ", finished=" + finished +
                    ", priority=" + priority +
                    '}';
        }
    }

    class PreTask{
        String pre;
        String after;

        public String getPre() {
            return pre;
        }

        public void setPre(String pre) {
            this.pre = pre;
        }

        public String getAfter() {
            return after;
        }

        public void setAfter(String after) {
            this.after = after;
        }

        @Override
        public String toString() {
            return "PreTask{" +
                    "pre='" + pre + '\'' +
                    ", after='" + after + '\'' +
                    '}';
        }
    }

    class GotoCost{
        String start;
        String dest;
        double cost;

        public String getStart() {
            return start;
        }

        public void setStart(String start) {
            this.start = start;
        }

        public String getDest() {
            return dest;
        }

        public void setDest(String dest) {
            this.dest = dest;
        }

        public double getCost() {
            return cost;
        }

        public void setCost(double cost) {
            this.cost = cost;
        }

        @Override
        public String toString() {
            return "GotoCost{" +
                    "start='" + start + '\'' +
                    ", dest='" + dest + '\'' +
                    ", cost=" + cost +
                    '}';
        }
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public List<String> getRobots() {
        return robots;
    }

    public void setRobots(List<String> robots) {
        this.robots = robots;
    }

    public List<String> getTaskNames() {
        return taskNames;
    }

    public void setTaskNames(List<String> taskNames) {
        this.taskNames = taskNames;
    }

    public List<String> getAbilities() {
        return abilities;
    }

    public void setAbilities(List<String> abilities) {
        this.abilities = abilities;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<PreTask> getOrders() {
        return orders;
    }

    public void setOrders(List<PreTask> orders) {
        this.orders = orders;
    }

    public List<GotoCost> getGotoCosts() {
        return gotoCosts;
    }

    public void setGotoCosts(List<GotoCost> gotoCosts) {
        this.gotoCosts = gotoCosts;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public List<String> getRobotAt() {
        return robotAt;
    }

    public void setRobotAt(List<String> robotAt) {
        this.robotAt = robotAt;
    }

    public double getMoveProb() {
        return moveProb;
    }

    public void setMoveProb(double moveProb) {
        this.moveProb = moveProb;
    }

    public double getDoProb() {
        return doProb;
    }

    public void setDoProb(double doProb) {
        this.doProb = doProb;
    }

    @Override
    public String toString() {
        return "ProblemParam{" +
                "index=" + index +
                ", robots=" + robots +
                ", robotAt=" + robotAt +
                ", taskNames=" + taskNames +
                ", abilities=" + abilities +
                ", locations=" + locations +
                ", orders=" + orders +
                ", gotoCosts=" + gotoCosts +
                ", tasks=" + tasks +
                ", moveProb=" + moveProb +
                ", doProb=" + doProb +
                '}';
    }

    public static void main(String[] args) {
        String jsonString = FileUtil.readFile("/home/gdhu/tiago_public_ws/src/tiago_simulation/mult_protap_sim/params/ros_param_to_json.json");
        System.out.println(jsonString);
        ProblemParam problemJson = JSON.parseObject(jsonString, ProblemParam.class);
        System.out.println(problemJson);
    }
}
