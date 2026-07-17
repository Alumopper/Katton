package top.katton.compat;

import net.minecraft.ChatFormatting;

#if MC_VERSION == "26.2"
import net.minecraft.network.chat.TextColor;
#endif

public final class TextApiCompat {
    private TextApiCompat() {
    }

    public static Integer chatFormattingColor(ChatFormatting formatting) {
#if MC_VERSION == "26.2"
        TextColor color = TextColor.fromLegacyFormat(formatting);
        return color == null ? null : color.getValue();
#else
        return formatting.getColor();
#endif
    }
}
