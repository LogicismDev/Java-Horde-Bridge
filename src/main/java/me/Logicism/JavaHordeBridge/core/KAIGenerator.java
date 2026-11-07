package me.Logicism.JavaHordeBridge.core;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

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
                BrowserData generationData = BrowserClient.executePOSTRequestString(new URL(kaiURL + "/api/latest/generate"), payload.toString(), headers);

                if (generationData.getResponseCode() == 200) {
                    JSONObject generationObject = new JSONObject(generationData.getResponseString());

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
            } catch (IOException | InterruptedException | URISyntaxException e) {
                bridge.getLogger().debug("Client is unavailable (attempt " + retryCount++ + "), retrying generation in 5 seconds...");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        return generation;
    }

    public JSONObject startChatGeneration(JSONObject rawPayload, String clusterURL, String model, String apiKey, String currentId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);

        try {
            BrowserData generationData;

            JSONObject payload = convertPayload(model, rawPayload, resolveMessages(rawPayload));
            if (payload.optBoolean("stream", false)) {
                headers.put("Content-Type", "text/event-stream");
                generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/v1/chat/completions"), payload.toString(), headers, 900);
                if (generationData.getResponseCode() == 415) {
                    headers.replace("Content-Type", "application/json");
                    generationData = BrowserClient.executePOSTRequest(new URL(kaiURL + "/v1/chat/completions"), payload.toString(), headers, 900);
                }

                StringBuilder content = new StringBuilder();
                StringBuilder reasoningContent = new StringBuilder();
                TreeMap<Integer, JSONObject> calls = new TreeMap<>();
                String finishReason = "";
                int seq = 1;
                boolean cancelled = false;

                boolean contentStarted = false;
                boolean reasoningStarted = false;
                long lastProgressMs = 0;

                JSONObject usage = null;
                try (InputStream in = generationData.getResponse()) {
                    if (generationData.getResponseCode() != 200) {
                        String text = new String(in.readNBytes(300), StandardCharsets.UTF_8);
                        System.out.println("backend HTTP " + generationData.getResponseCode() + ": " + text);
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    input:
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String data = line.substring(5).strip();

                            if (data.equals("[DONE]")) break;

                            JSONObject chunk;
                            try {
                                Object parsed = new JSONTokener(data).nextValue();
                                if (!(parsed instanceof JSONObject)) continue;
                                chunk = (JSONObject) parsed;
                            } catch (JSONException e) {
                                continue;
                            }

                            JSONObject u = chunk.optJSONObject("usage", new JSONObject());
                            if (!u.isEmpty()) usage = u;

                            for (int i = 0; i < chunk.optJSONArray("choices").length(); i++) {
                                JSONObject choice = chunk.optJSONArray("choices").getJSONObject(i);
                                JSONObject delta = choice.optJSONObject("delta");

                                String contentDelta = delta.optString("content", "");
                                String reasoningContentDelta = delta.optString("reasoning_content", "");

                                if (reasoningContentDelta.isEmpty())
                                    reasoningContentDelta = delta.optString("reasoning", "");

                                if (!contentStarted) {
                                    if (contentDelta.strip().isEmpty()) {
                                        contentDelta = "";
                                    } else {
                                        contentStarted = true;
                                    }
                                }
                                if (!reasoningStarted) {
                                    if (reasoningContentDelta.strip().isEmpty()) {
                                        reasoningContentDelta = "";
                                    } else {
                                        reasoningStarted = true;
                                    }
                                }

                                content.append(contentDelta);
                                reasoningContent.append(reasoningContentDelta);

                                for (int i1 = 0; i1 < delta.optJSONArray("tool_calls", new JSONArray()).length(); i1++) {
                                    JSONObject toolCall = delta.optJSONArray("tool_calls", new JSONArray()).getJSONObject(i1);

                                    int index = toolCall.optInt("index", -1);
                                    String id = toolCall.optString("id", "");

                                    JSONObject toolSlot = calls.computeIfAbsent(index == -1 ? 0 : index, k -> new JSONObject());
                                    if (!id.isEmpty()) toolSlot.put("id", id);

                                    JSONObject function = toolCall.optJSONObject("function", new JSONObject());
                                    String name = function.optString("name", "");

                                    if (!name.isEmpty()) toolSlot.put("name", name);
                                    String arguments = function.optString("arguments", "");
                                    toolSlot.put("arguments", arguments);
                                }

                                String finishReasonChoice = choice.optString("finish_reason", "");
                                if (!finishReasonChoice.isEmpty()) finishReason = finishReasonChoice;

                                if (!contentDelta.isEmpty() || !reasoningContentDelta.isEmpty()) {
                                    // Horde rate-limits progress updates; one per token gets a
                                    // 429 and the old code aborted the whole generation on it.
                                    // Send at most every 5 s and never let a failure cancel.
                                    if (System.currentTimeMillis() - lastProgressMs >= 5000) {
                                        lastProgressMs = System.currentTimeMillis();
                                        try {
                                            JSONObject bodyDelta = new JSONObject().put("content", contentDelta).put("reasoning_content", reasoningContentDelta);
                                            JSONObject body = new JSONObject().put("id", currentId).put("seq", seq++).put("delta", bodyDelta);

                                            Map<String, String> progressHeaders = new HashMap<>();
                                            progressHeaders.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
                                            progressHeaders.put("Content-Type", "application/json");
                                            progressHeaders.put("apikey", apiKey);
                                            BrowserData bd = BrowserClient.executePOSTRequestString(new URL(clusterURL + "/api/v2/generate/text/progress"), body.toString(), progressHeaders);

                                            JSONObject response = new JSONObject(bd.getResponseString());
                                            if (response.optBoolean("cancelled", false)) {
                                                cancelled = true;
                                                break input;
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!cancelled) {
                    JSONArray toolCalls = new JSONArray();
                    for (JSONObject slot : calls.values()) {
                        toolCalls.put(slot);
                    }

                    JSONObject submitObject = new JSONObject().put("content", content.toString()).put("finish_reason", finishReason).put("usage", usage);
                    if (!reasoningContent.toString().isEmpty()) {
                        submitObject.put("reasoning_content", reasoningContent.toString());
                    }
                    if (!toolCalls.isEmpty()) {
                        submitObject.put("tool_calls", toolCalls);
                    }

                    return submitObject;
                }
            } else {
                headers.put("Content-Type", "application/json");
                generationData = BrowserClient.executePOSTRequestString(new URL(kaiURL + "/v1/chat/completions"), payload.toString(), headers, 900);

                if (generationData.getResponseCode() != 200) {
                    bridge.getLogger().error("Engine returned " + generationData.getResponseCode() + ": " + generationData.getResponseString());
                }
                JSONObject response = new JSONObject(generationData.getResponseString());
                JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String reasoningContent = choice.optString("reasoning_content", "");
                if (!reasoningContent.isEmpty()) reasoningContent = choice.optString("reasoning", "");

                JSONObject submitObject = new JSONObject().put("content", message.optString("content", "")).put("finish_reason", choice.opt("finish_reason")).put("usage", response.opt("usage"));
                if (!reasoningContent.isEmpty()) submitObject.put("reasoning_content", reasoningContent);

                return submitObject;
            }
        } catch (IOException | URISyntaxException e) {
            bridge.getLogger().debug("Client is unavailable, canceling request! (" + e + ")");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public JSONObject validateClient(String kaiName, String[] priorityUsernames, boolean enableChatFeatures, boolean enablePromptJobs, String[] chatFeatures, String[] imageTransports) throws IOException, URISyntaxException, InterruptedException {
        JSONObject clientData = null;
        while (clientData == null) {
            BrowserData bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/api/latest/model/"), new HashMap<>());
            JSONObject modelObject = new JSONObject(bd.getResponseString());

            bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/api/latest/config/max_context_length/"), new HashMap<>());
            JSONObject maxContextLengthObject = new JSONObject(bd.getResponseString());

            bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/api/latest/config/max_length/"), new HashMap<>());
            JSONObject maxLengthObject = new JSONObject(bd.getResponseString());

            String model = modelObject.getString("result");
            if (!model.contains("/")) {
                model = model.replace("_", "/");
            }

            clientData = new JSONObject().put("name", kaiName).put("models", new JSONArray().put(model)).put("max_length", maxLengthObject.getInt("value")).put("max_context_length", maxContextLengthObject.getInt("value")).put("bridge_version", HordeBridge.BRIDGE_VERSION).put("bridge_agent", HordeBridge.BRIDGE_AGENT).put("prompt_jobs", enablePromptJobs).put("model_id", model).put("chat_jobs", enableChatFeatures);

            if (priorityUsernames.length != 0) {
                JSONArray priorityUsernameArrays = new JSONArray();

                for (String username : priorityUsernames) {
                    priorityUsernameArrays.put(username);
                }

                clientData = clientData.put("priority_usernames", priorityUsernameArrays);
            }

            bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/api/latest/config/soft_prompts_list/"), new HashMap<>());
            JSONObject softPromptsListObject = new JSONObject(bd.getResponseString());

            clientData = clientData.put("softprompts", softPromptsListObject.has(modelObject.getString("result")) ? softPromptsListObject.getJSONArray(modelObject.getString("result")) : new JSONArray());

            if (enableChatFeatures) {
                JSONArray chatFeaturesArray = new JSONArray();
                JSONArray imageTransportsArray = new JSONArray();

                for (String feature : chatFeatures) {
                    chatFeaturesArray = chatFeaturesArray.put(feature);

                    if (feature.equals("vision")) {
                        for (String transport : imageTransports) {
                            imageTransportsArray = imageTransportsArray.put(transport);
                        }
                    }
                }

                clientData = clientData.put("chat_features", chatFeaturesArray).put("image_transports", imageTransportsArray).put("chat_limits", new JSONObject().put("max_length", maxLengthObject.getInt("value")).put("max_context_length", maxContextLengthObject.getInt("value")).put("tools_max_temperature", 1));
            }
        }

        return clientData;
    }

    // The 5.0 jar's TextHordeRunnable calls the 8-arg form; keep it and pull the
    // slot count from the environment so the worker still advertises all slots.
    public JSONObject validateOpenAIClient(String kaiName, String[] priorityUsernames, boolean enableChatFeatures, String model, int maxTokens, int maxTokenLength, String[] chatFeatures, String[] imageTransports) throws IOException, URISyntaxException, InterruptedException {
        int slots = 1;
        try {
            slots = Integer.parseInt(System.getenv().getOrDefault("BRIDGE_PROCS", "1"));
        } catch (NumberFormatException ignored) {
        }
        return validateOpenAIClient(kaiName, priorityUsernames, enableChatFeatures, model, maxTokens, maxTokenLength, chatFeatures, imageTransports, slots);
    }

    public JSONObject validateOpenAIClient(String kaiName, String[] priorityUsernames, boolean enableChatFeatures, String model, int maxTokens, int maxTokenLength, String[] chatFeatures, String[] imageTransports, int threads) throws IOException, URISyntaxException, InterruptedException {
        JSONObject clientData = null;
        while (clientData == null) {
            if (model.isEmpty()) {
                BrowserData bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/v1/models"), new HashMap<>());
                JSONObject modelObject = new JSONObject(bd.getResponseString());
                model = modelObject.getJSONArray("data").getJSONObject(0).getString("id");
            }

            clientData = new JSONObject().put("name", kaiName).put("models", new JSONArray().put(model)).put("max_length", maxTokens).put("max_context_length", maxTokenLength).put("bridge_version", HordeBridge.BRIDGE_VERSION).put("bridge_agent", HordeBridge.BRIDGE_AGENT).put("prompt_jobs", true).put("model_id", model).put("chat_jobs", enableChatFeatures).put("threads", threads);

            if (priorityUsernames.length != 0) {
                JSONArray priorityUsernameArrays = new JSONArray();

                for (String username : priorityUsernames) {
                    priorityUsernameArrays.put(username);
                }

                clientData = clientData.put("priority_usernames", priorityUsernameArrays);
            }

            if (enableChatFeatures) {
                JSONArray chatFeaturesArray = new JSONArray();
                JSONArray imageTransportsArray = new JSONArray();

                for (String feature : chatFeatures) {
                    chatFeaturesArray = chatFeaturesArray.put(feature);

                    if (feature.equals("vision")) {
                        for (String transport : imageTransports) {
                            imageTransportsArray = imageTransportsArray.put(transport);
                        }
                    }
                }

                clientData = clientData.put("chat_features", chatFeaturesArray).put("image_transports", imageTransportsArray).put("chat_limits", new JSONObject().put("max_length", maxTokens).put("max_context_length", maxTokenLength).put("tools_max_temperature", 1));
            }
        }

        return clientData;
    }

    private JSONObject convertPayload(String model, JSONObject payload, JSONArray messages) {
        // Prompt jobs carry their sampler settings in a nested "params" object and
        // omit several of them; chat jobs put them at the top level. Accept both.
        JSONObject params = payload.optJSONObject("params");
        if (params == null) params = payload;

        JSONObject convertedPayload = new JSONObject();
        convertedPayload.put("model", model);
        convertedPayload.put("messages", messages);
        convertedPayload.put("max_tokens", payload.optInt("max_length", params.optInt("max_length", 80)));
        // Horde allows wider ranges than vLLM's OpenAI schema; clamp so a valid
        // Horde job cannot produce a 400.
        double temperature = params.optDouble("temperature", payload.optDouble("temperature", 0.7));
        convertedPayload.put("temperature", Math.max(0.0, Math.min(2.0, temperature)));
        convertedPayload.put("top_p", Math.max(0.0, Math.min(1.0, params.optDouble("top_p", payload.optDouble("top_p", 0.95)))));
        double frequencyPenalty = params.optDouble("frequency_penalty", payload.optDouble("frequency_penalty", 0.0));
        convertedPayload.put("frequency_penalty", Math.max(-2.0, Math.min(2.0, frequencyPenalty)));
        double presencePenalty = params.optDouble("presence_penalty", payload.optDouble("presence_penalty", 0.0));
        convertedPayload.put("presence_penalty", Math.max(-2.0, Math.min(2.0, presencePenalty)));

        if (payload.has("stream_progress")) convertedPayload.put("stream", payload.optBoolean("stream_progress", false)).put("stream_options", new JSONObject().put("include_usage", true)).remove("stream_progress");
        // vLLM's OpenAI endpoint accepts at most 4 stop sequences; Horde prompt
        // jobs routinely send 8-12. Keep the first four non-empty ones.
        if (params.has("stop_sequence")) {
            Object rawStops = params.opt("stop_sequence");
            if (rawStops instanceof JSONArray) {
                JSONArray stops = new JSONArray();
                for (int i = 0; i < ((JSONArray) rawStops).length() && stops.length() < 4; i++) {
                    String stop = ((JSONArray) rawStops).optString(i, "");
                    if (!stop.isEmpty()) stops.put(stop);
                }
                if (!stops.isEmpty()) convertedPayload.put("stop", stops);
            } else if (rawStops instanceof String) {
                if (!((String) rawStops).isEmpty()) convertedPayload.put("stop", rawStops);
            }
        }
        for (String key : List.of("seed", "reasoning_effort", "tools", "tool_choice",
                "parallel_tool_calls", "response_format")) {
            if (payload.has(key)) payload.put(key, payload.opt(key));      // pass through unchanged
        }

        return convertedPayload;
    }

    private JSONArray resolveMessages(JSONObject payload) {
        if (payload.has("messages")) {
            JSONArray existing = payload.optJSONArray("messages", new JSONArray());
            if (!existing.isEmpty()) return existing;
        }

        // Text/prompt jobs carry "prompt", not "messages". Wrap it as a single
        // user turn so the chat endpoint does not reject an empty conversation.
        String plainPrompt = payload.optString("prompt", "");
        if (!plainPrompt.isEmpty()) {
            return new JSONArray().put(new JSONObject().put("role", "user").put("content", plainPrompt));
        }

        String url = payload.optString("messages_url", "");
        if (!url.isEmpty()) {
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
                BrowserData bd = BrowserClient.executeGETRequest(new URL(url), headers);

                try (InputStream raw = bd.getResponse()) {
                    if (bd.getResponseCode() != 200) throw new IOException("messages_url did not return 200");

                    boolean gzip = bd.getHeaders().get("Content-Encoding").get(0).equalsIgnoreCase("gzip");

                    try (InputStream in = gzip ? new GZIPInputStream(raw) : raw) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();

                        byte[] chunk = new byte[65536];
                        int n;
                        while ((n = in.read(chunk)) != -1) {
                            baos.write(chunk, 0, n);
                            if (baos.size() > 36 * 1024 * 1024) {       // counts DECODED bytes, so a gzip bomb cannot slip past
                                throw new IOException("offloaded messages exceed the 36 MiB cap");
                            }
                        }

                        Object parsed = new JSONTokener(baos.toString(StandardCharsets.UTF_8)).nextValue();
                        if (!(parsed instanceof JSONArray)) throw new IOException("offloaded messages are not a JSON array");
                        return (JSONArray) parsed;
                    }
                }
            } catch (IOException | URISyntaxException | InterruptedException e) {
                return new JSONArray();
            }
        } else {
            return new JSONArray();
        }
    }
}