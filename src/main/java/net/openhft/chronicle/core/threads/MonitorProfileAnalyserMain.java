package net.openhft.chronicle.core.threads;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MonitorProfileAnalyserMain {

    public static final String PROFILE_OF_THE_THREAD = "profile of the thread";
    public static final String THREAD_HAS_BLOCKED_FOR = "thread has blocked for";

    /**
     * Reads one or more log files and looks for thread profiles to summarise
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            System.err.println("No input file(s) provided");

        int interval = Integer.getInteger("interval", 0);
        if (interval <= 0) {
            main0(args);
        } else {
            for (; ; ) {
                main0(args);
                JitterSampler.sleepSilently(interval * 1000);
                System.out.println("\n---\n");
            }
        }
    }

    public static void main0(String[] args) throws IOException {
        System.out.println("Grouped by line");
        Map<String, Integer> stackCount = new LinkedHashMap<>();

        for (String arg : args) {
            StringBuilder sb = new StringBuilder();
            int lineCount = -1;
            try (BufferedReader br = Files.newBufferedReader(Paths.get(arg))) {
                // TODO: PrintGCApplicationStoppedTime

                for (String line; (line = br.readLine()) != null; ) {
                    if (line.contains(PROFILE_OF_THE_THREAD) || line.contains(THREAD_HAS_BLOCKED_FOR)) {
                        if (sb.length() > 0) {
                            String lines = sb.toString();
                            stackCount.compute(lines, (k, v) -> v == null ? 1 : v + 1);
                        }
                        lineCount = 0;
                        sb.setLength(0);

                    } else if (line.startsWith("\tat") && lineCount >= 0) {
                        if (++lineCount <= 8) {
                            sb.append(line).append("\n");
                        }
                    } else if (sb.length() > 0) {
                        String lines = sb.toString();
                        stackCount.compute(lines, (k, v) -> v == null ? 1 : v + 1);
                        sb.setLength(0);
                    }
                }
            }
        }
        List<Map.Entry<String, Integer>> stackSortedByCount =
                stackCount.entrySet().stream()
                        .filter(e -> e.getValue() > 2)
                        .sorted(Comparator.comparing(e -> -e.getValue())) // reversed
                        .limit(20)
                        .collect(Collectors.toList());
        stackSortedByCount
                .stream()
                .peek(e -> stackCount.remove(e.getKey()))
                .forEach(e -> System.out.println(e.getValue() + e.getKey()));

        System.out.println("Grouped by method.");
        Map<String, Integer> methodCount = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : stackCount.entrySet()) {
            String stack = entry.getKey().replaceFirst("\\(.*?\\)", "( * )");
            methodCount.compute(stack, (k, v) -> (v == null ? 0 : v) + entry.getValue());
        }

        List<Map.Entry<String, Integer>> methodSortedByCount =
                methodCount.entrySet().stream()
                        .filter(e -> e.getValue() > 2)
                        .sorted(Comparator.comparing(e -> -e.getValue())) // reversed
                        .limit(20)
                        .collect(Collectors.toList());
        methodSortedByCount
                .stream()
                .forEach(e -> System.out.println(e.getValue() + e.getKey()));
    }
}
