package rosgen.fromros;



import com.alibaba.fastjson.JSON;
import rosgen.rosgenjson.FileUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Random;

public class ParamToPlanningProblem {
    public String gen(ProblemParam p) {
        Random r = new Random();

        StringBuilder sb = new StringBuilder();

        List<String> robots = p.getRobots();
        List<String> robotAt = p.getRobotAt();
        List<String> abilities = p.getAbilities();
        List<String> taskNames = p.getTaskNames();
        List<String> locations = p.getLocations();
        List<ProblemParam.PreTask> preTasks = p.getOrders();
        List<ProblemParam.GotoCost> gotoCosts = p.getGotoCosts();
        List<ProblemParam.Task> tasks = p.getTasks();

        sb.append("instance multi_ros_robot_inst_mdp__" + p.index + " {\n\n");
        sb.append("\tdomain = multi_ros_robot_mdp;\n\n");
        sb.append("\tobjects {\n");

        sb.append("\t\trobot : {\n");
        for (int i = 0; i < robots.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            String robot = "\t\t\t" + p.getRobots().get(i);
            sb.append(robot);
        }
        sb.append(" };\n");

        sb.append("\t\tability : {\n");
        for (int i = 0; i < abilities.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            String ability = "\t\t\t" + abilities.get(i);
            sb.append(ability);
        }
        sb.append(" };\n");

        sb.append("\t\tlocation : { ");
        for (int i = 0; i < locations.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String location = locations.get(i);
            sb.append(location);
        }

        sb.append(" };\n");

        sb.append("\t\ttask : {\n");
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) {
                sb.append(",\n");
            }
            String task = "\t\t\t" + tasks.get(i).getTaskName();
            sb.append(task);
        }
        sb.append(" };\n");

        sb.append("  };\n\n");

        // NON-FLUENTS
        sb.append("\tnon-fluents {\n");
        for (int i = 0; i < robots.size(); i++) {
            sb.append("\t\tMOVE_PROB(" + robots.get(i) + ")" + " = ").append(p.moveProb)
                    .append(";\n");
        }
        for (int i = 0; i < robots.size(); i++) {
            sb.append("\t\tDO_PROB(" + robots.get(i) + ")" + " = ").append(p.doProb).append(";\n");
        }

        for (int i = 0; i < gotoCosts.size(); i++) {
            ProblemParam.GotoCost cost = gotoCosts.get(i);
            sb.append("\t\tGOTO_COST(" + cost.getStart() + ", " + cost.getDest() + ") = ").
                        append(cost.getCost()).append(";\n");
        }

        for (int i = 0; i < tasks.size(); i++) {
            ProblemParam.Task t = tasks.get(i);
          sb.append(
              "\t\tTASK(" + t.getTaskName() + ", " + t.getAbility() + ", "
                  + t.getLocation())
              .append(");\n");
        }

        for (int i = 0; i < tasks.size(); i++) {
            sb.append("\t\tTASK_UTILITY(" + tasks.get(i).getTaskName() + ") = ")
                    .append(tasks.get(i).getUtility())
                    .append(";\n");
        }

        sb.append("\t};\n\n");

        sb.append("\tinit-state {\n");
        for (int i = 0; i < robots.size(); i++) {
            sb.append("\t\trobot-at(" + robots.get(i) + ", " + robotAt.get(i) + ");\n");

        }
        for (int i = 0; i < preTasks.size(); i++) {
            String s = String.format("\t\tpre-task-finished(%s, %s) = false;\n",
                    preTasks.get(i).getPre(),
                    preTasks.get(i).getAfter());
            sb.append(s);
        }
        sb.append("\t};\n\n");
        sb.append("\thorizon = 10;\n\n");
        sb.append("\tdiscount = 0.8;\n\n");
        sb.append("}\n");
        return sb.toString();

    }

    public void writefile(String path, ProblemParam problem) throws Exception {
        String content = gen(problem);
        BufferedWriter out = null;
        try {
            File file = new File(path + "multi_ros_robot_inst_mdp__" + problem.index + ".rddl");
            out = new BufferedWriter(new FileWriter(file));
            out.write(content);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        // load json file output by ros node
        String jsonString = FileUtil.readFile("/home/gdhu/tiago_public_ws/src/tiago_simulation/mult_protap_sim/params/ros_param_to_json.json");
        ProblemParam problemJson = JSON.parseObject(jsonString, ProblemParam.class);

        // generate planning problem file
        ParamToPlanningProblem p = new ParamToPlanningProblem();
        final String path = "/home/gdhu/projects/test/files/";
        System.out.println("Start generate problems...");
        p.writefile(path, problemJson);
        System.out.println("Success!");


        }
    }
