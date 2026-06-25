package me.Logicism.JavaHordeBridge.network;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class BrowserClient {

    private static HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public static BrowserData executeGETRequest(URL url, Map<String, String> headers) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).GET();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executeGETRequestString(URL url, Map<String, String> headers) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).GET();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executeGETRequestDiscard(URL url, Map<String, String> headers) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).GET();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode());
    }

    public static BrowserData executePOSTRequest(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePOSTRequestString(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePOSTRequestDiscard(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).POST(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode());
    }

    public static BrowserData executePUTRequest(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePUTRequest(URL url, byte[] data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofByteArray(data));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePUTRequestString(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePUTRequestString(URL url, byte[] data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofByteArray(data));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode(), response.body(), Integer.parseInt(response.headers().firstValue("Content-Length").orElse("-1")));
    }

    public static BrowserData executePUTRequestDiscard(URL url, String data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode());
    }

    public static BrowserData executePUTRequestDiscard(URL url, byte[] data, Map<String, String> headers) throws IOException, InterruptedException, URISyntaxException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).uri(url.toURI()).PUT(HttpRequest.BodyPublishers.ofByteArray(data));
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = requestBuilder.build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        return new BrowserData(response.uri().toURL().toString(), response.headers().map(), response.statusCode());
    }

}
