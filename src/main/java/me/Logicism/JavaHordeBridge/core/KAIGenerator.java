package me.Logicism.JavaHordeBridge.core;

import me.Logicism.JavaHordeBridge.HordeBridge;
import me.Logicism.JavaHordeBridge.network.BrowserClient;
import me.Logicism.JavaHordeBridge.network.BrowserData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class KAIGenerator {

    private HordeBridge bridge;
    private String kaiURL;
    private volatile String engine;
    private volatile long engineAt;

    public KAIGenerator(HordeBridge bridge, String kaiURL) {
        this.bridge = bridge;
        this.kaiURL = kaiURL;
    }

    /** Identify the backend so engine-specific params are only sent where they exist. */
    private String probe(String path) {
        try {
            BrowserData bd = BrowserClient.executeGETRequestString(new URL(kaiURL + path), new HashMap<>(), 3);
            if (bd.getResponseCode() == 200) return bd.getResponseString();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String engine() {
        // Cache a real hit forever; an "openai" fallback may just mean the
        // backend was still starting, so retry it once a minute.
        if (engine != null && (!engine.equals("openai") || System.currentTimeMillis() - engineAt < 60000)) return engine;
        String found = "openai";
        if (probe("/api/extra/version") != null) found = "koboldcpp";
        else if (probe("/props") != null) found = "llamacpp";
        else if (probe("/get_model_info") != null) found = "sglang";
        else if (probe("/v1/model") != null) found = "tabbyapi";
        else {
            String version = probe("/version");
            if (version != null && version.contains("version")) found = "vllm";
            String models = probe("/v1/models");
            if (models != null) {
                String owner = "";
                try {
                    owner = new JSONObject(models).getJSONArray("data").getJSONObject(0)
                            .optString("owned_by", "").toLowerCase();
                } catch (JSONException ignored) {
                }
                if (owner.contains("aphrodite")) found = "aphrodite";
                else if (owner.contains("llama.cpp") || owner.contains("llamacpp")) found = "llamacpp";
                else if (owner.contains("vllm")) found = "vllm";
            } else {
                String metrics = probe("/metrics");
                if (metrics != null && metrics.contains("vllm:")) found = "vllm";
            }
        }
        engine = found;
        engineAt = System.currentTimeMillis();
        bridge.getLogger().info("Backend engine detected: " + found);
        return found;
    }

    /** Extras the detected engine understands; everything else stays off its payload. */
    private Set<String> allowedExtras() {
        switch (engine()) {
            case "vllm":
            case "aphrodite":
            case "sglang":
                return Set.of("top_k", "min_p", "top_a", "typical_p", "tfs", "repetition_penalty",
                        "dynatemp_range", "dynatemp_exponent", "truncate_prompt_tokens",
                        "logit_bias", "n", "logprobs", "top_logprobs", "include_stop_str_in_output");
            case "koboldcpp":
            case "llamacpp":
                return Set.of("top_k", "min_p", "top_a", "typical_p", "tfs", "repetition_penalty",
                        "dynatemp_range", "dynatemp_exponent", "truncate_prompt_tokens",
                        "logit_bias", "n", "logprobs", "top_logprobs", "include_stop_str_in_output",
                        "custom_token_bans", "use_default_badwordsids", "mirostat_mode",
                        "mirostat_tau", "mirostat_eta", "eta_cutoff", "eps_cutoff",
                        "dynatemp_max", "dynatemp_min");
            case "tabbyapi":
                return Set.of("top_k", "min_p", "top_a", "typical_p", "tfs", "repetition_penalty",
                        "truncate_prompt_tokens", "logit_bias", "n", "logprobs", "top_logprobs");
            default:
                return Set.of("n", "logprobs", "top_logprobs");
        }
    }

    public String startGeneration(JSONObject payload) {
        String generation = null;
        int retryCount = 0;
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Java 11 / Java Horde Bridge " + HordeBridge.BRIDGE_VERSION);
        while (retryCount < 5) {
            try {
                BrowserData generationData = BrowserClient.executePOSTRequestString(new URL(kaiURL + "/api/latest/generate"), payload.toString(), headers, 900);

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
                Object seed = null;
                try (InputStream in = generationData.getResponse()) {
                    if (generationData.getResponseCode() != 200) {
                        String text = new String(in.readNBytes(300), StandardCharsets.UTF_8);
                        bridge.getLogger().error("backend HTTP " + generationData.getResponseCode() + ": " + text);
                        return null;
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
                            if (chunk.has("seed")) seed = chunk.opt("seed");

                            JSONArray choices = chunk.optJSONArray("choices");
                            if (choices == null) continue;
                            for (int i = 0; i < choices.length(); i++) {
                                JSONObject choice = choices.getJSONObject(i);
                                JSONObject delta = choice.optJSONObject("delta");
                                if (delta == null) continue;

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
                    if (seed != null) submitObject.put("seed", seed);
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
                if (reasoningContent.isEmpty()) reasoningContent = choice.optString("reasoning", "");

                JSONObject submitObject = new JSONObject().put("content", message.optString("content", "")).put("finish_reason", choice.opt("finish_reason")).put("usage", response.opt("usage"));
                if (response.has("seed")) submitObject.put("seed", response.opt("seed"));
                if (!reasoningContent.isEmpty()) submitObject.put("reasoning_content", reasoningContent);
                if (message.has("tool_calls")) submitObject.put("tool_calls", message.getJSONArray("tool_calls"));

                return submitObject;
            }
        } catch (IOException | URISyntaxException e) {
            bridge.getLogger().debug("Client is unavailable, canceling request! (" + e + ")");
        } catch (JSONException e) {
            bridge.getLogger().error("Engine returned an unparseable body: " + e.getMessage());
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

    public JSONObject validateOpenAIClient(String kaiName, String[] priorityUsernames, boolean enableChatFeatures, boolean enablePromptJobs, String model, int maxTokens, int maxTokenLength, String[] chatFeatures, String[] imageTransports, int threads) throws IOException, URISyntaxException, InterruptedException {
        engine(); // detect before the first job so the probe cost is not in it
        JSONObject clientData = null;
        while (clientData == null) {
            if (model.isEmpty()) {
                BrowserData bd = BrowserClient.executeGETRequestString(new URL(kaiURL + "/v1/models"), new HashMap<>());
                JSONObject modelObject = new JSONObject(bd.getResponseString());
                model = modelObject.getJSONArray("data").getJSONObject(0).getString("id");
            }

            clientData = new JSONObject().put("name", kaiName).put("models", new JSONArray().put(model)).put("max_length", maxTokens).put("max_context_length", maxTokenLength).put("bridge_version", HordeBridge.BRIDGE_VERSION).put("bridge_agent", HordeBridge.BRIDGE_AGENT).put("prompt_jobs", enablePromptJobs).put("model_id", model).put("chat_jobs", enableChatFeatures).put("threads", threads);

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
        bridge.getLogger().debug("Payload keys: " + payload.keySet() + " / " + params.keySet());

        JSONObject convertedPayload = new JSONObject();
        convertedPayload.put("model", model);
        convertedPayload.put("quiet", true);
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
            if (rawStops instanceof JSONArray stopArray) {
                JSONArray stops = new JSONArray();
                for (int i = 0; i < stopArray.length() && stops.length() < 4; i++) {
                    String stop = stopArray.optString(i, "");
                    if (!stop.isEmpty()) stops.put(stop);
                }
                if (!stops.isEmpty()) convertedPayload.put("stop", stops);
            } else if (rawStops instanceof String stop && !stop.isEmpty()) {
                convertedPayload.put("stop", stop);
            }
        }
        for (String key : List.of("reasoning_effort", "tools", "tool_choice",
                "parallel_tool_calls", "response_format")) {
            // Prompt jobs nest these under params; chat jobs keep them at the top.
            if (params.has(key)) convertedPayload.put(key, params.opt(key));
            else if (payload.has(key)) convertedPayload.put(key, payload.opt(key)); // pass through unchanged
        }
        Set<String> allowed = allowedExtras();
        // Seed: the chat payload uses "seed", the native one "sampler_seed".
        if (params.has("seed") || payload.has("seed")) {
            convertedPayload.put("seed", params.has("seed") ? params.opt("seed") : payload.opt("seed"));
        } else if (params.has("sampler_seed") || payload.has("sampler_seed")) {
            convertedPayload.put("seed", params.has("sampler_seed") ? params.opt("sampler_seed") : payload.opt("sampler_seed"));
        }
        // Samplers the OpenAI schema does not define but some engines accept as
        // extras; only the detected engine's own set is sent.
        for (String key : List.of("top_k", "min_p", "top_a", "tfs", "logit_bias",
                "n", "logprobs", "top_logprobs", "eta_cutoff", "eps_cutoff",
                "dynatemp_range", "dynatemp_exponent", "use_default_badwordsids",
                "include_stop_str_in_output")) {
            if (!allowed.contains(key)) continue;
            if (params.has(key)) convertedPayload.put(key, params.opt(key));
            else if (payload.has(key)) convertedPayload.put(key, payload.opt(key));
        }
        if (convertedPayload.has("top_k") && convertedPayload.optInt("top_k", 0) == 0) {
            convertedPayload.put("top_k", -1); // 0 means "disabled" on Horde; -1 on the engines
        }
        // Renamed fields, straight from the official bridge.
        String[][] renamed = {
                {"typical", "typical_p"},
                {"badwordsids", "custom_token_bans"},
                {"mirostat", "mirostat_mode"},
                {"mirostat_tau", "mirostat_tau"},
                {"mirostat_eta", "mirostat_eta"},
                {"dynatemp_max", "dynatemp_max"},
                {"dynatemp_min", "dynatemp_min"},
        };
        for (String[] pair : renamed) {
            if (!allowed.contains(pair[1])) continue;
            if (params.has(pair[0])) convertedPayload.put(pair[1], params.opt(pair[0]));
            else if (payload.has(pair[0])) convertedPayload.put(pair[1], payload.opt(pair[0]));
        }
        // rep_pen has no OpenAI equivalent; repetition_penalty is the closest.
        if (allowed.contains("repetition_penalty") && (params.has("rep_pen") || payload.has("rep_pen"))) {
            double repPen = params.optDouble("rep_pen", payload.optDouble("rep_pen", 1.0));
            convertedPayload.put("repetition_penalty", Math.max(0.0, Math.min(2.0, repPen)));
        }
        // Leave room for the answer inside the context the client asked for.
        if (allowed.contains("truncate_prompt_tokens")
                && (params.has("max_context_length") || payload.has("max_context_length"))) {
            int ctx = params.optInt("max_context_length", payload.optInt("max_context_length", 2048));
            int answer = convertedPayload.optInt("max_tokens", 80);
            convertedPayload.put("truncate_prompt_tokens", Math.max(1, ctx - answer));
        }

        bridge.getLogger().debug("Converted keys: " + convertedPayload.keySet());
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