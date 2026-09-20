package hearsay.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** Option parsing shared by the commands. */
final class Options {

    private Options() {
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected an option, got " + args[i]);
            }
            String key = args[i].substring(2);
            if (i + 1 < args.length) {
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
}
