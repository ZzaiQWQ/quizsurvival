/*
 * Quiz Survival Mod
 * Copyright (c) 2026 oaoi
 * https://github.com/ZzaiQWQ/quizsurvival
 *
 * This software is licensed under a custom non-commercial license.
 * You may NOT sell or commercially distribute this software.
 * You may NOT remove or alter this copyright notice.
 * See LICENSE file for full terms.
 */
package com.quizmod.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.LinkedHashMap;
import java.util.Map;

public class QuizHud implements HudRenderCallback {
    // 客户端缓存的剩余次数
    public static final Map<String, Integer> remainingActions = new LinkedHashMap<>();

    private static final Map<String, String> TYPE_NAMES_ZH = Map.of(
            "chop", "§a砍伐",
            "ore", "§b矿物",
            "stone", "§7石头",
            "dirt", "§6泥土",
            "interact", "§d交互",
            "combat", "§c战斗",
            "jump", "§3跳跃",
            "other", "§f方块"
    );
    private static final Map<String, String> TYPE_NAMES_EN = Map.of(
            "chop", "§aChop",
            "ore", "§bOre",
            "stone", "§7Stone",
            "dirt", "§6Dirt",
            "interact", "§dInteract",
            "combat", "§cCombat",
            "jump", "§3Jump",
            "other", "§fBlock"
    );

    private static boolean isEnglish() {
        MinecraftClient client = MinecraftClient.getInstance();
        String lang = client.options.language;
        return lang != null && lang.startsWith("en");
    }

    public static void parseAndUpdate(String data) {
        remainingActions.clear();
        if (data == null || data.isEmpty()) return;
        for (String pair : data.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    remainingActions.put(kv[0], Integer.parseInt(kv[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;
        if (remainingActions.isEmpty()) return;

        // 只显示有剩余次数的分类
        boolean hasAny = remainingActions.values().stream().anyMatch(v -> v > 0);
        if (!hasAny) return;

        int x = 4;
        int y = 4;
        var tr = client.textRenderer;

        // 标题背景
        context.fill(x - 2, y - 2, x + 82, y + 10, 0x88000000);
        String title = isEnglish() ? "§e§l[Quiz Survival]" : "§e§l[答题生存]";
        context.drawTextWithShadow(tr, title, x, y, 0xFFFFAA00);
        y += 12;

        for (var entry : remainingActions.entrySet()) {
            int count = entry.getValue();
            if (count <= 0) continue;
            String name = (isEnglish() ? TYPE_NAMES_EN : TYPE_NAMES_ZH).getOrDefault(entry.getKey(), entry.getKey());
            String text = name + "§r: §f" + count;
            context.fill(x - 2, y - 1, x + 82, y + 9, 0x66000000);
            context.drawTextWithShadow(tr, text, x, y, 0xFFFFFFFF);
            y += 11;
        }
    }
}
