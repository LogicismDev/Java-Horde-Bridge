package me.Logicism.JavaHordeBridge.core;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KAIGenerator {

    private HordeBridge bridge;
    private String kaiURL;

    public KAIGenerator(HordeBridge bridge, String kaiURL) {
        this.bridge = bridge;
        this.kaiURL = kaiURL;
    }

    public String startGeneration(JSONObject payload) {
        String generation = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/api/latest/generate"), payload.toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    JSONObject generationObject = new JSONObject(BrowserClient.requestToString(generationData.getResponse()));

                    generation = generationObject.getJSONArray("results").getJSONObject(0).getString("text");

                    break;
                } else if (generationData.getResponseCode() == 422) {
                    generation = "payload validation error";

                    break;
                } else if (generationData.getResponseCode() == 503) {
                    bridge.getLogger().debug("Client is busy (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    bridge.getLogger().debug("Client responded with " + generationData.getResponseCode() + " (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (JSONException e) {
                bridge.getLogger().debug("Client returned unexpected response (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            } catch (IOException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        return generation;
    }

    public JSONObject validateClient(String kaiName, String[] priorityUsernames) throws IOException {
        JSONObject clientData = null;
        while (clientData == null) {
            BrowserData bd = BrowserClient.executeGETRequest(new URL(kaiURL + "/api/latest/model/"), null);
            JSONObject modelObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            bd = BrowserClient.executeGETRequest(new URL(kaiURL + "/api/latest/config/max_context_length/"), null);
            JSONObject maxContextLengthObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            bd = BrowserClient.executeGETRequest(new URL(kaiURL + "/api/latest/config/max_length/"), null);
            JSONObject maxLengthObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            String model = modelObject.getString("result");
            if (!model.contains("/")) {
                model = model.replace("_", "/");
            }

            clientData = new JSONObject().put("name", kaiName).put("models", new JSONArray().put(model)).put("max_length", maxLengthObject.getInt("value")).put("max_context_length", maxContextLengthObject.getInt("value")).put("bridge_version", HordeBridge.BRIDGE_VERSION).put("bridge_agent", HordeBridge.BRIDGE_AGENT);

            if (priorityUsernames.length != 0) {
                JSONArray priorityUsernameArrays = new JSONArray();

                for (String username : priorityUsernames) {
                    priorityUsernameArrays.put(username);
                }

                clientData.put("priority_usernames", priorityUsernameArrays);
            }

            bd = BrowserClient.executeGETRequest(new URL(kaiURL + "/api/latest/config/soft_prompts_list/"), null);
            JSONObject softPromptsListObject = new JSONObject(BrowserClient.requestToString(bd.getResponse()));

            clientData.put("softprompts", softPromptsListObject.has(modelObject.getString("result")) ? softPromptsListObject.getJSONArray(modelObject.getString("result")) : new JSONArray());

        }

        return clientData;
    }
}
