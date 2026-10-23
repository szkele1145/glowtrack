package com.glowtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.MissingResourceException;

/**
 * 客户端文案统一出口：全部走语言文件（en_us.json / zh_cn.json），
 * 代码里不再出现硬编码中文。键缺失时兜底返回键名，不会崩。
 */
public final class Text {
    private Text() {}

    /** 取翻译。lang 里缺键时返回键名本身（开发期可见）。 */
    public static Component tr(String key, Object... args) {
        try {
            // 1.18.2 还没有 Component.translatable 静态工厂（1.19 才加）
            return new TranslatableComponent(key, args);
        } catch (Throwable t) {
            return new TextComponent(key);
        }
    }

    /** 取翻译并转纯文本（drawString 用）。 */
    public static String s(String key, Object... args) {
        return tr(key, args).getString();
    }
}
