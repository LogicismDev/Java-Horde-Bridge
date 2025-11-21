package me.Logicism.JavaHordeBridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.Logicism.JavaHordeBridge.console.HordeLogger;
import me.Logicism.JavaHordeBridge.core.InterrogationGenerator;
import me.Logicism.JavaHordeBridge.runnables.InterrogationHordeRunnable;
import me.Logicism.JavaHordeBridge.runnables.TextHordeRunnable;
import me.Logicism.JavaHordeBridge.core.KAIGenerator;
import org.apache.commons.cli.*;
import org.fusesource.jansi.AnsiConsole;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HordeBridge {

    public static int BRIDGE_VERSION = 3;
    public static String BRIDGE_AGENT = "Java Horde Bridge:" + BRIDGE_VERSION + ":https://github.com/LogicismDev/Java-Horde-Bridge";

    public static HordeBridge INSTANCE;
    public static int INTERVAL = 1;

    private KAIGenerator kaiGenerator;
    private InterrogationGenerator interroGenerator;
    private HordeLogger logger;
    private HordeConfig config;

    private static String workerType = "text";
    private static String kaiURL = "http://127.0.0.1:5000";
    private static String kaiName = "Java Horde Bridge Worker #" + new Random().nextInt();
    private static String kaiAPIKey = "0000000000";
    private static String clusterURL = "https://aihorde.net";
    private static String backupClusterURL = "https://stablehorde.net";
    private static String[] priorityUsernames = new String[0];
    private static String[] forms = new String[0];

    private static ExecutorService service = Executors.newSingleThreadExecutor();

    public static void main(String[] args) {
        AnsiConsole.systemInstall();

        Options options = new Options();

        Option interval = Option.builder("i").longOpt("interval").argName("interval").hasArg().desc("The interval to check if there are new generations").numberOfArgs(1).build();
        Option config = Option.builder("c").longOpt("config").argName("config").hasArg().desc("The configuration file to grab the Horde Worker information from").numberOfArgs(1).build();
        Option worker_type = Option.builder("t").longOpt("worker_type").argName("worker_type").hasArg().desc("The Worker type to specify (text/interrogation)").numberOfArgs(1).build();
        Option kai_url = Option.builder("k").longOpt("kai_url").argName("kai_url").hasArg().desc("The KoboldAI URL to grab generations from").numberOfArgs(1).build();
        Option kai_name = Option.builder("n").longOpt("kai_name").argName("kai_name").hasArg().desc("Set the Horde Worker name").numberOfArgs(1).build();
        Option kai_apikey = Option.builder("a").longOpt("kai_apikey").argName("kai_apikey").hasArg().desc("Set the Horde Worker API Key").numberOfArgs(1).build();
        Option cluster_url = Option.builder("u").longOpt("cluster_url").argName("cluster_url").hasArg().desc("Set the cluster url to grab prompts and to send generations to").numberOfArgs(1).build();
        Option backup_cluster_url = Option.builder("b").longOpt("backup_cluster_url").argName("backup_cluster_url").hasArg().desc("Set the backup cluster url if the main cluster is down").numberOfArgs(1).build();
        Option priority_usernames = Option.builder("p").longOpt("priority_usernames").argName("priority_usernames").hasArg().valueSeparator(',').desc("The usernames to prioritize generations").numberOfArgs(1).build();
        Option oForms = Option.builder("f").longOpt("forms").argName("forms").hasArg().valueSeparator(',').desc("The interrogation forms to enable for the worker").numberOfArgs(1).build();

        options.addOption(interval);
        options.addOption(config);
        options.addOption(worker_type);
        options.addOption(kai_url);
        options.addOption(kai_name);
        options.addOption(kai_apikey);
        options.addOption(cluster_url);
        options.addOption(backup_cluster_url);
        options.addOption(priority_usernames);
        options.addOption(oForms);

        HelpFormatter helper = new HelpFormatter();
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            INSTANCE = new HordeBridge(cmd);

            INSTANCE.getLogger().info("Starting Java Horde Worker " + kaiName);

            if (workerType.equals("text")) {
                service.execute(new TextHordeRunnable(INSTANCE, kaiURL, kaiName, kaiAPIKey, clusterURL, backupClusterURL, priorityUsernames));
            } else if (workerType.equals("interrogation")) {
                service.execute(new InterrogationHordeRunnable(INSTANCE, kaiURL, kaiName, kaiAPIKey, clusterURL, backupClusterURL, priorityUsernames, forms));
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helper.printHelp("Usage", options);
            System.exit(0);
        }
    }

    public HordeBridge(CommandLine cmd) {
        logger = new HordeLogger(HordeBridge.class);

        if (cmd.hasOption("interval")) {
            INTERVAL = Integer.parseInt(cmd.getOptionValue("interval"));
        }
        File configFile = new File("config.yml");
        if (cmd.hasOption("config")) {
            configFile = new File(cmd.getOptionValue("config"));
        }

        if (!configFile.exists()) {
            if (cmd.hasOption("worker_type")) {
                if (!cmd.getOptionValue("worker_type").equals("interrogation") || !cmd.getOptionValue("worker_type").equals("text")) {
                    workerType = "text";
                } else {
                    workerType = cmd.getOptionValue("worker_type");
                }
            }
            if (cmd.hasOption("kai_url")) {
                kaiURL = cmd.getOptionValue("kai_url");
            }
            if (cmd.hasOption("kai_name")) {
                kaiName = cmd.getOptionValue("kai_name");
            }
            if (cmd.hasOption("kai_apikey")) {
                kaiAPIKey = cmd.getOptionValue("kai_apikey");
            }
            if (cmd.hasOption("kai_url")) {
                kaiURL = cmd.getOptionValue("kai_url");
            }
            if (cmd.hasOption("cluster_url")) {
                clusterURL = cmd.getOptionValue("cluster_url");
            }
            if (cmd.hasOption("cluster_url")) {
                backupClusterURL = cmd.getOptionValue("cluster_url");
            }
            if (cmd.hasOption("priority_usernames")) {
                priorityUsernames = cmd.getOptionValues("priority_usernames");
            }
            if (cmd.hasOption("forms")) {
                forms = cmd.getOptionValues("forms");
            }
        } else {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            try {
                config = mapper.readValue(configFile, HordeConfig.class);

                if (config.getWorkerType().equals("interrogation") || config.getWorkerType().equals("text")) {
                    workerType = config.getWorkerType();
                }
                kaiURL = config.getKaiURL();
                if (!kaiName.isEmpty()) {
                    kaiName = config.getWorkerName();
                }
                kaiAPIKey = config.getApiKey();
                clusterURL = config.getClusterURL();
                backupClusterURL = config.getBackupClusterURL();
                priorityUsernames = config.getPriorityUsernames().toArray(new String[0]);
                forms = config.getInterrogationForms().toArray(new String[0]);
            } catch (IOException e) {
                logger.error("Couldn't load configuration! " + e.getMessage() + " Stopping Java Horde Bridge");
                System.exit(0);
            }
        }

        kaiGenerator = new KAIGenerator(this, kaiURL);
    }

    public HordeLogger getLogger() {
        return logger;
    }

    public KAIGenerator getGenerator() {
        return kaiGenerator;
    }

    public InterrogationGenerator getInterroGenerator() {
        return interroGenerator;
    }
}
