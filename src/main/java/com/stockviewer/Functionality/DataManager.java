package com.stockviewer.Functionality;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.stockviewer.StockViewer;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DataManager {
    private static final String URL_FORMAT_STRING = "https://www.alphavantage.co/query?apikey=%s&datatype=json&symbol=%s%s";
    private static final String ORDER_PATH = "Data/orders.json";
    private static final String DATA_PATH = "Data/data.json";
    private static String API_KEY = "";
    private static double initial = 10000;
    private static List<com.stockviewer.Functionality.Order> orders = new ArrayList<>();
    private static Map<String, String> cache = new HashMap<>();
    private static final ScheduledExecutorService ses = Executors.newScheduledThreadPool(3);
    private static final List<Runnable> queue = new ArrayList<>();
    private static long rateLimitedUntil = 0;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private static final Type listType = new TypeToken<ArrayList<com.stockviewer.Functionality.Order>>() {
    }.getType();
    private static final DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart()
            .appendPattern(" HH:mm:ss")
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter();

    static {
        loadJson();
        ses.scheduleWithFixedDelay(() -> {
            if (!queue.isEmpty()) ses.submit(queue.remove(0));
        }, 5, 12, TimeUnit.SECONDS);
        ses.scheduleWithFixedDelay(DataManager::saveJson, 5, 5, TimeUnit.MINUTES);
        ses.scheduleWithFixedDelay(DataManager::cleanCache, 1, 1, TimeUnit.MINUTES);
    }

    public static double getInitial() {
        return initial;
    }


    public static void setAPIKey(String apiKey) {
        API_KEY = apiKey;
        saveJson();
    }

    public static boolean hasApiKey() {
        return !Objects.equals(API_KEY, "");

    }

    public static void clear() {
        orders = new ArrayList<>();
        saveJson();
        loadJson();
    }

    public static void cleanCache() {
        cache = cache.keySet().stream().limit(50).collect(Collectors.toMap(Function.identity(), i -> cache.get(i)));
    }

    public static void stop() {
        saveJson();
        ses.shutdown();
    }

    public static List<com.stockviewer.Functionality.Order> getOrders() {
        return List.copyOf(orders);
    }

    public static DateTimeFormatter getDateTimeFormatter() {
        return dateTimeFormatter;
    }

    public static void saveJson() {
        try (FileWriter fw = new FileWriter(StockViewer.class.getResource(DATA_PATH).getPath())){
            Map<String, Object> data = Map.ofEntries(Map.entry("initial", initial), Map.entry("API_KEY", API_KEY));
            gson.toJson(data, mapType, fw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileWriter fw = new FileWriter(StockViewer.class.getResource(ORDER_PATH).getPath())){
            gson.toJson(orders, listType, fw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadJson() {
        try (InputStream in = StockViewer.class.getResourceAsStream(DATA_PATH);
             Reader reader = new BufferedReader(new InputStreamReader(in))) {
            Map<String, Object> data = gson.fromJson(reader, mapType);
            if (data != null) {
                if (data.get("initial") != null)
                    initial = ((Double) data.getOrDefault("initial", 10_000)).doubleValue();
                if (data.get("API_KEY") != null && data.get("API_KEY") != "")
                    API_KEY = (String) data.getOrDefault("API_KEY", "");
                else
                    StockViewer.forceApiKey();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (InputStream in = StockViewer.class.getResourceAsStream(ORDER_PATH);
             Reader reader = new BufferedReader(new InputStreamReader(in))) {
            List<com.stockviewer.Functionality.Order> fromJson = gson.fromJson(reader, listType);
            if (fromJson != null)
                orders = fromJson;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void importFile(Path path) throws IOException {
        if (path.endsWith(".json")) {
            byte[] current = Files.readAllBytes(Path.of(StockViewer.class.getResource(ORDER_PATH).getPath()));
            Reader reader = Files.newBufferedReader(path);
            List<com.stockviewer.Functionality.Order> importedData = gson.fromJson(reader, listType);
            if (importedData != null) {
                orders = importedData;
                saveJson();
            } else
                Files.write(path, current);
        } else {
            throw new IOException("Incorrect File Type : JSON Expected");
        }
    }

    private static String getSync(String url) throws ExecutionException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get().body();
    }

    public static CompletableFuture<String> getStockData(String symbol, com.stockviewer.Functionality.Interval interval) {
        CompletableFuture<String> result = new CompletableFuture<>();
        String url = String.format(URL_FORMAT_STRING, API_KEY, symbol, interval.getApiValue());
        if (cache.containsKey(url))
            result.completeAsync(() -> cache.get(url));
        else {
            Runnable task = () -> {
                try {
                    String raw = getSync(url);
                    cache.put(url, raw);
                    result.complete(raw);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    System.out.println("Api timed out with url : " + url);
                    e.printStackTrace();
                }
            };
            long current = System.nanoTime();
            if (rateLimitedUntil - current > 0)
                ses.submit(task);
            else
                ses.schedule(task, rateLimitedUntil - current, TimeUnit.NANOSECONDS);
            rateLimitedUntil = (long) (current + 1.2E9);
        }
        return result;
    }

    public static double calculateCurrent() {
        return initial + orders.stream().mapToDouble(com.stockviewer.Functionality.Order::getSignedValue).sum();
    }

    public static int getOwned(String symbol) {
        int owned = 0;
        for (com.stockviewer.Functionality.Order order : orders)
            if (order.getSymbol().equals(symbol))
                owned += (order.isSold() ? -1 : 1) * order.getAmount();
        return owned;
    }

    public static boolean buy(int amount, double buyPrice, String symbol) {
        if (amount * buyPrice <= calculateCurrent())
            return orders.add(new com.stockviewer.Functionality.Order(amount, buyPrice, symbol));
        return false;
    }

    public static boolean sell(int amount, double buyPrice, String symbol) {
        if (amount <= getOwned(symbol))
            return orders.add(new com.stockviewer.Functionality.Order(amount, buyPrice, symbol, true));
        return false;
    }

    public static String formatByInterval(LocalDateTime time, com.stockviewer.Functionality.Interval interval) {
        return switch (interval) {
            case ONE_DAY -> time.format(DateTimeFormatter.ofPattern("HH:mm"));
            case YTD -> time.format(DateTimeFormatter.ofPattern("MMM/dd/yyyy"));
            default -> time.format(DateTimeFormatter.ofPattern("MMM/dd HH:mm"));
        };
    }
}