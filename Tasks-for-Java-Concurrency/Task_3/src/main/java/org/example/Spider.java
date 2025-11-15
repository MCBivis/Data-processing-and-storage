package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Spider {
    // таймаут на весь обход
    private static final long GLOBAL_TIMEOUT_SECONDS = 120L;

    // простой парсинг JSON-формата
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SUCCESSORS_ARRAY_PATTERN = Pattern.compile("\"successors\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL);
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("\"([^\"]*)\"");

    private final HttpClient httpClient;
    private final String host;
    private final int port;

    // потоки: виртуальные потоки через новый виртуальный-поток-исполнитель
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // отслеживание посещённых путей и накопление сообщений в сортируемом множестве
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final Set<String> messages = new ConcurrentSkipListSet<>();

    // счётчик ожидающих задач
    private final AtomicInteger pending = new AtomicInteger(0);
    private final CompletableFuture<Void> doneSignal = new CompletableFuture<>();

    public Spider(String host, int port) {
        this.host = host;
        this.port = port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void start(){
        submitPath("/");

        // ждём завершения либо таймаут
        try {
            doneSignal.orTimeout(GLOBAL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS).join();
        } catch (Exception e) {
            // таймаут или прерывание
        } finally {
            executor.shutdownNow();
        }

        // выводим все собранные сообщения в лексикографическом порядке
        for (String m : messages) {
            System.out.println(m);
        }
    }

    private void submitPath(String rawPath) {
        String normalized = normalizePath(rawPath);
        // если уже посещали — не добавляем
        if (!visited.add(normalized)) return;

        pending.incrementAndGet();
        // создаём виртуальный поток через executor
        executor.submit(() -> {
            try {
                fetchAndProcess(normalized);
            } finally {
                if (pending.decrementAndGet() == 0) {
                    doneSignal.complete(null);
                }
            }
        });
    }

    private void fetchAndProcess(String path) {
        URI uri = buildUri(path);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            // выполняем синхронный вызов в виртуальном потоке — это позволяет большой параллелизм
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                String body = resp.body();
                String message = parseMessage(body);
                if (message != null) {
                    messages.add(message);
                }
                List<String> succ = parseSuccessors(body);
                for (String s : succ) {
                    submitPath(s);
                }
            }
            // иначе игнорируем - не 200
        } catch (IOException | InterruptedException e) {
            // ошибки игнорируем
        }
    }

    private URI buildUri(String path) {
        String s = String.format("http://%s:%d%s", host, port, path);
        return URI.create(s);
    }

    private String normalizePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        return p.startsWith("/") ? p : "/" + p;
    }

    private String parseMessage(String json) {
        Matcher m = MESSAGE_PATTERN.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private List<String> parseSuccessors(String json) {
        List<String> out = new ArrayList<>();
        Matcher arrayMatcher = SUCCESSORS_ARRAY_PATTERN.matcher(json);
        if (arrayMatcher.find()) {
            String inside = arrayMatcher.group(1);
            Matcher q = QUOTED_STRING_PATTERN.matcher(inside);
            while (q.find()) {
                out.add(q.group(1));
            }
        }
        return out;
    }

    public static void main(String[] args){
        String host = "localhost";
        int port = 8080;
        if (args.length > 0) host = args[0];
        if (args.length > 1) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
        }

        Spider spider = new Spider(host, port);
        spider.start();
    }
}
