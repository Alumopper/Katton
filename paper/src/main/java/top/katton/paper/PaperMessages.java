package top.katton.paper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PaperMessages {
    private static final String DEFAULT_LOCALE = "en_us";
    private static final Map<String, Map<String, String>> TRANSLATIONS = Map.of(
        "en_us", load("en_us"),
        "zh_cn", load("zh_cn"),
        "zh_tw", load("zh_tw")
    );

    private PaperMessages() {
    }

    static Component tr(CommandSender sender, String key, Object... args) {
        return Component.text(format(resolveLocale(sender), key, args));
    }

    private static String format(String locale, String key, Object... args) {
        String pattern = TRANSLATIONS.getOrDefault(locale, TRANSLATIONS.get(DEFAULT_LOCALE)).get(key);
        if (pattern == null) {
            pattern = TRANSLATIONS.get(DEFAULT_LOCALE).getOrDefault(key, key);
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalArgumentException ignored) {
            return pattern;
        }
    }

    private static String resolveLocale(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return DEFAULT_LOCALE;
        }

        Object locale = invokeNoArg(player, "locale");
        if (locale == null) {
            locale = invokeNoArg(player, "getLocale");
        }
        return selectLocale(locale);
    }

    private static Object invokeNoArg(Player player, String method) {
        try {
            return player.getClass().getMethod(method).invoke(player);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String selectLocale(Object locale) {
        String normalized;
        if (locale instanceof Locale javaLocale) {
            normalized = javaLocale.toLanguageTag();
        } else if (locale instanceof String text) {
            normalized = text;
        } else {
            return DEFAULT_LOCALE;
        }

        normalized = normalized.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("zh_tw") || normalized.startsWith("zh_hk")
            || normalized.startsWith("zh_mo") || normalized.contains("hant")) {
            return "zh_tw";
        }
        if (normalized.startsWith("zh")) {
            return "zh_cn";
        }
        return DEFAULT_LOCALE;
    }

    private static Map<String, String> load(String locale) {
        String path = "assets/katton/lang/" + locale + ".json";
        try (InputStream stream = PaperMessages.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Map.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    values.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
