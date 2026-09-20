package hearsay.experiments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Argument parsing shared by the experiment runners. */
final class Cli {

    private Cli() {
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected an option, got " + args[i]);
            }
            String key = args[i].substring(2);
            if (key.equals("help")) {
                options.put(key, "");
            } else if (i + 1 < args.length) {
                options.put(key, args[++i]);
            } else {
                throw new IllegalArgumentException("Option --" + key + " needs a value");
            }
        }
        return options;
    }

    static int intOption(Map<String, String> options, String key, int fallback) {
        return options.containsKey(key) ? Integer.parseInt(options.get(key)) : fallback;
    }

    static long longOption(Map<String, String> options, String key, long fallback) {
        return options.containsKey(key) ? Long.parseLong(options.get(key)) : fallback;
    }

    static double doubleOption(Map<String, String> options, String key, double fallback) {
        return options.containsKey(key) ? Double.parseDouble(options.get(key)) : fallback;
    }

    static List<Double> doubles(String list) {
        List<Double> values = new ArrayList<>();
        for (String part : list.split(",")) {
            values.add(Double.parseDouble(part.trim()));
        }
        return values;
    }
}
