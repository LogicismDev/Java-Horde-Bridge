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

    public static int BRIDGE_VERSION = 5;
    public static String BRIDGE_AGENT = "Java Horde Bridge:" + BRIDGE_VERSION + ":https://github.com/LogicismDev/Java-Horde-Bridge";

    public static HordeBridge INSTANCE;
    public static int INTERVAL = 1;

    private KAIGenerator kaiGenerator;
    private InterrogationGenerator interroGenerator;
    private final HordeLogger logger;
    private HordeConfig config;

    private static String workerType = "text";
    private static String kaiURL = "http://127.0.0.1:5000";
    private static String kaiName = "Java Horde Bridge Worker #" + new Random().nextInt();
    private static String kaiAPIKey = "0000000000";
    private static String clusterURL = "https://aihorde.net";
    private static String backupClusterURL = "https://stablehorde.net";
    private static String[] priorityUsernames = new String[0];
    private static String[] forms = new String[0];
    private static boolean enablePromptJobs = false;
    private static boolean enableChatCompletions = false;
    private static boolean disableKoboldClientVerifier = false;
    private static String model = "";
    private static int maxTokens = 512;
    private static int maxTokenLength = 2048;
    private static String[] chatFeatures = new String[0];
    private static String[] imageTransports = new String[0];

    private static int BRIDGE_PROCS = 1;

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
        Option enable_prompt_jobs = Option.builder("e").longOpt("enable_prompt_jobs").argName("enable_prompt_jobs").hasArg().valueSeparator(',').desc("Enable Prompt Jobs (true/false)").numberOfArgs(1).build();
        Option enable_chat_completions = Option.builder("E").longOpt("enable_chat_completions").argName("enable_chat_completions").hasArg().valueSeparator(',').desc("Enable Chat Completions (true/false)").numberOfArgs(1).build();
        Option disable_kobold_client_verifier = Option.builder("d").longOpt("disable_kobold_client_verifier").argName("disable_kobold_client_verifier").hasArg().valueSeparator(',').desc("Disable Kobold Client Verifier, only use if you're not using a KoboldAI supported endpoint program. Disables OpenAI model fetching if specified. (true/false)").numberOfArgs(1).build();
        Option oModel = Option.builder("o").longOpt("model").argName("model").hasArg().valueSeparator(',').desc("Set the model optionally if Kobold Client Verifier is disabled").numberOfArgs(1).build();
        Option max_tokens = Option.builder("m").longOpt("max_tokens").argName("max_tokens").hasArg().valueSeparator(',').desc("Set the max tokens if Kobold Client Verifier is disabled").numberOfArgs(1).build();
        Option max_token_length = Option.builder("M").longOpt("max_token_length").argName("max_token_length").hasArg().valueSeparator(',').desc("Set the max token length if Kobold Client Verifier is disabled").numberOfArgs(1).build();
        Option chat_features = Option.builder("C").longOpt("chat_features").argName("chat_features").hasArg().valueSeparator(',').desc("The chat features to enable for the text worker").numberOfArgs(1).build();
        Option image_transports = Option.builder("I").longOpt("image_transports").argName("image_transports").hasArg().valueSeparator(',').desc("The image transports to enable for the text worker (base64, url)").numberOfArgs(1).build();

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
        options.addOption(enable_prompt_jobs);
        options.addOption(enable_chat_completions);
        options.addOption(disable_kobold_client_verifier);
        options.addOption(oModel);
        options.addOption(max_tokens);
        options.addOption(max_token_length);
        options.addOption(chat_features);
        options.addOption(image_transports);

        HelpFormatter helper = new HelpFormatter();
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            INSTANCE = new HordeBridge(cmd);

            INSTANCE.getLogger().info("Starting Java Horde Worker " + kaiName);

            String procsEnv = System.getenv("BRIDGE_PROCS");
            if (procsEnv != null && !procsEnv.isEmpty()) {
                try {
                    BRIDGE_PROCS = Math.max(1, Integer.parseInt(procsEnv));
                } catch (NumberFormatException ignored) {
                }
            }
            ExecutorService service = Executors.newFixedThreadPool(BRIDGE_PROCS);
            if (workerType.equals("text")) {
                for (int i = 1; i <= BRIDGE_PROCS; i++) {
                    service.execute(new TextHordeRunnable(INSTANCE, i, kaiURL, kaiName, kaiAPIKey, clusterURL, backupClusterURL, priorityUsernames, enableChatCompletions, enablePromptJobs, disableKoboldClientVerifier, model, maxTokens, maxTokenLength, chatFeatures, imageTransports, BRIDGE_PROCS));
                }
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
                if (cmd.getOptionValue("worker_type").equals("interrogation") || cmd.getOptionValue("worker_type").equals("text")) {
                    workerType = cmd.getOptionValue("worker_type");
                } else {
                    workerType = "text";
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
            if (cmd.hasOption("backup_cluster_url")) {
                backupClusterURL = cmd.getOptionValue("backup_cluster_url");
            }
            if (cmd.hasOption("priority_usernames")) {
                priorityUsernames = cmd.getOptionValues("priority_usernames");
            }
            if (cmd.hasOption("forms")) {
                forms = cmd.getOptionValues("forms");
            }
            if (cmd.hasOption("enable_prompt_jobs")) {
                enablePromptJobs = Boolean.parseBoolean(cmd.getOptionValue("enable_prompt_jobs"));
            }
            if (cmd.hasOption("enable_chat_completions")) {
                enableChatCompletions = Boolean.parseBoolean(cmd.getOptionValue("enable_chat_completions"));
            }
            if (cmd.hasOption("disable_kobold_client_verifier")) {
                disableKoboldClientVerifier = Boolean.parseBoolean(cmd.getOptionValue("disable_kobold_client_verifier"));
            }
            if (cmd.hasOption("model")) {
                model = cmd.getOptionValue("model");
            }
            if (cmd.hasOption("max_tokens")) {
                maxTokens = Integer.parseInt(cmd.getOptionValue("max_tokens"));
            }
            if (cmd.hasOption("max_token_length")) {
                maxTokenLength = Integer.parseInt(cmd.getOptionValue("max_token_length"));
            }
            if (cmd.hasOption("chat_features")) {
                chatFeatures = cmd.getOptionValues("chat_features");
            }
            if (cmd.hasOption("image_transports")) {
                imageTransports = cmd.getOptionValues("image_transports");
            }
        } else {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            try {
                config = mapper.readValue(configFile, HordeConfig.class);

                if (config.getWorkerType().equals("interrogation") || config.getWorkerType().equals("text")) {
                    workerType = config.getWorkerType();
                }
                kaiURL = config.getKaiURL();
                if (config.getWorkerName() != null && !config.getWorkerName().isEmpty()) {
                    kaiName = config.getWorkerName();
                }
                kaiAPIKey = config.getApiKey();
                clusterURL = config.getClusterURL();
                backupClusterURL = config.getBackupClusterURL();
                priorityUsernames = config.getPriorityUsernames().toArray(new String[0]);
                forms = config.getInterrogationForms().toArray(new String[0]);
                enablePromptJobs = config.isEnablePromptJobs();
                enableChatCompletions = config.isEnableChatCompletions();
                disableKoboldClientVerifier = config.isDisableKoboldClientVerifier();
                model = config.getModel();
                maxTokens = config.getMaxTokens();
                maxTokenLength = config.getMaxTokenLength();
                chatFeatures = config.getChatFeatures().toArray(new String[0]);
                imageTransports = config.getImageTransports().toArray(new String[0]);
            } catch (IOException e) {
                logger.error("Couldn't load configuration! " + e.getMessage() + " Stopping Java Horde Bridge");
                System.exit(0);
            }
        }

        if (workerType.equals("interrogation")) {
            interroGenerator = new InterrogationGenerator(this, kaiURL);
        } else if (workerType.equals("text")) {
            if (!enablePromptJobs && !enableChatCompletions) {
                logger.error("Prompt Jobs should be enabled if Chat Completions is false, or Chat Completions should be enabled, or both!");
                System.exit(0);
            }
            kaiGenerator = new KAIGenerator(this, kaiURL);
        }
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
