package org.example;

import org.json.JSONWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> abbreviations = new HashSet<>();
    private static final Set<String> fullMean = new HashSet<>();
    private static final Map<String, Map<String, Double>> outputMap = new HashMap<>();
    private static List<String> textLines;

    public static void main(String[] args) {

        // Input
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter path to input file");
        while (true) {
            try {
                textLines = Files.readAllLines(Path.of(sc.nextLine()), StandardCharsets.UTF_8);
                break;
            } catch (IOException | InvalidPathException e) {
                System.out.println("Input file not found, please try again");
            }
        }

        // Main logic
        for (String line : textLines) {
            Matcher matcher = Pattern.compile("[А-Я]{2,}").matcher(line);
            while (matcher.find()) {
                String abb = matcher.group();
                abbreviations.add(abb);
                String pattern = "(?<=\\W)(?i)" + abb.charAt(0) + "(?-i)([А-Яа-я]+\\s*?){1,"+ abb.length() +"}(?=\\s*\\([^(]*" + abb + "[^)]*\\))";
                Matcher m = Pattern.compile(pattern , Pattern.UNICODE_CASE).matcher(line);
                if (m.find()) {
                    fullMean.add(m.group());
                }
            }

            matcher = Pattern.compile("([А-Я][а-я]+ )+([А-Я][а-я]+)").matcher(line);
            while (matcher.find()) {
                fullMean.add(matcher.group());
            }
//            for (String abb : abbreviations) {
//                String lowAbb = abb.toLowerCase();
//                //"\\b(\\p{Lower}\\p{Upper}*\\s*)+" + abbreviation.toLowerCase() + "\\b"
//                //lowAbb.charAt(0) + "\\w+ " + "\\[(" + lowAbb.substring(1) + "]\\w)+"
//                String pattern = "(?<=\\()([^)]+)\\s*" + lowAbb + "\\)";
//                matcher = Pattern.compile(pattern).matcher(line);
//                while (matcher.find()) {
//                    fullMean.add(matcher.group());
//                }
//            }
        }
        abbreviations.stream().parallel().forEach(abb -> outputMap.put(abb, fullMean.stream().filter(full -> isMatch(abb, full)).collect(Collectors.toMap(full -> full, full -> getProbability(abb, full)))));

        System.out.println("Found abbreviations:\n" + abbreviations);
        System.out.println("Found meanings:\n" + fullMean);
        System.out.println("Result:\n" + outputMap);

        // Output
        System.out.println("Enter path to output file");
        while (true) {
            try (FileWriter writer = new FileWriter(sc.nextLine())) {
                JSONWriter json = new JSONWriter(writer);
                json.object().key("result").array();
                outputMap.forEach((key, m) -> {
                    json.object().key(key).array();
                    m.forEach((k, p) -> json.object()
                            .key("text").value(k)
                            .key("probability").value(p)
                            .endObject());
                    json.endArray().endObject();
                });
                json.endArray().endObject();
                writer.flush();
                break;
            } catch (IOException e) {
                System.out.println("Output file cannot be created, please enter different location");;
            }
        }
        sc.close();
        System.out.println("Complete!");
        try {Thread.sleep(1500);} catch (InterruptedException ignored) {}
    }

    private static boolean isMatch(String abbreviation, String full) {
        if (abbreviation.isBlank() || full.isBlank()) return false;
        String abb = abbreviation.toLowerCase();
        String lowFull = full.toLowerCase();
        String[] words = lowFull.split(" ");
        int len = Math.min(abb.length(), words.length);
        int counter = 0;
        for (int i = 0; i < len; ++i) {
            if (words[i].charAt(0) == abb.charAt(i)) ++counter;
        }
        return (double) counter /len >= 0.5 && Arrays.stream(abb.split("")).allMatch(lowFull::contains);
    }

    private static double getProbability(String abbreviation, String fullMeaning) {
        String lowText = String.join(" ", textLines).toLowerCase();
        String lowAbb = abbreviation.toLowerCase();
        String lowFull = fullMeaning.toLowerCase();
        List<String> wordsText = Arrays.stream(lowText.split(" ")).toList();

        // +1 to avoid infinity
        AtomicReference<Double> probability = new AtomicReference<>(1.0 / (Math.abs(wordsText.indexOf(lowAbb) - wordsText.indexOf(lowFull)) + 1));
        Stream.iterate(0, i -> i < wordsText.size(), i -> ++i).parallel().forEach(i -> {
            if (Arrays.stream(lowFull.split(" ")).parallel().anyMatch(w -> similarity(w, wordsText.get(i)))) {
                probability.updateAndGet(v -> v + 1.0 / (Math.abs(wordsText.indexOf(lowAbb) - i) + 1));
            }
        });
        return Math.round(probability.get()*100.0)/100.0;
    }

    private static boolean similarity(String w1, String w2) {
        if (w1.length()<3 || w2.length()<3) return false;
        if (w1.equals(w2) || w1.contains(w2) || w2.contains(w1)) return true;

        int s = Math.max(0, w2.length()/2-2);
        int e = Math.min(w2.length()/2+2, w2.length());
        if (!w1.contains(w2.substring(s, e))) return false;

        return recursive(w1, w2, w1.length()-1, w2.length()-1) <= 4;
    }

    private static int recursive(String w1, String w2, int i, int j) {
        if (i == 0 && j == 0) return 0;
        if (j == 0) return i;
        if (i == 0) return j;
        return Math.min(
                Math.min(recursive(w1, w2, i, j-1)+1, recursive(w1, w2, i-1, j)+1),
                recursive(w1, w2, i-1, j-1) + (w1.charAt(i) == w2.charAt(j) ? 0:1)
        );
    }
}