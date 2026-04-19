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

import com.quizmod.QuizPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class QuizScreen extends Screen {
    private final String question;
    private final List<String> options;
    private final int quizId;
    private final String typeName;
    private final int wrongCount;
    private final int timeLimit; // 秒
    private final float wrongDamageBase;
    private final float wrongDamageMax;
    private final long openTime; // 打开时间
    private boolean autoSubmitted = false; // 防止重复自动提交

    // 按钮颜色标签
    private static final String[] LABELS = {"A", "B", "C", "D"};

    public QuizScreen(String question, List<String> options, int quizId, String typeName, int wrongCount, int timeLimit, float wrongDamageBase, float wrongDamageMax) {
        super(Text.literal("Quiz"));
        this.question = question;
        this.options = options;
        this.quizId = quizId;
        this.typeName = typeName;
        this.wrongCount = wrongCount;
        this.timeLimit = timeLimit;
        this.wrongDamageBase = wrongDamageBase;
        this.wrongDamageMax = wrongDamageMax;
        this.openTime = System.currentTimeMillis();
    }

    private boolean isEnglish() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        String lang = client.options.language;
        return lang != null && lang.startsWith("en");
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 10;
        int btnWidth = 240;
        int btnHeight = 24;
        int gap = 28;

        for (int i = 0; i < options.size() && i < 4; i++) {
            final int answerIndex = i;
            String label = "§l[" + LABELS[i] + "] §r" + options.get(i);
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), button -> {
                        // 发送答案到服务器
                        ClientPlayNetworking.send(new QuizPayloads.AnswerQuizC2S(quizId, answerIndex));
                        // 禁用所有按钮
                        for (var child : this.children()) {
                            if (child instanceof ButtonWidget b) b.active = false;
                        }
                    })
                    .dimensions(centerX - btnWidth / 2, startY + i * gap, btnWidth, btnHeight)
                    .build();
            this.addDrawableChild(btn);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 半透明黑色背景
        context.fill(0, 0, this.width, this.height, 0xAA000000);

        int centerX = this.width / 2;
        int boxTop = this.height / 2 - 90;
        int boxWidth = 280;

        // 绘制标题背景框
        int bx = centerX - boxWidth/2 - 5;
        int by = boxTop - 5;
        int bw = boxWidth + 10;
        int bh = 70;
        context.fill(bx, by, bx + bw, by + bh, 0xCC333333);
        // 金色边框
        context.fill(bx, by, bx + bw, by + 1, 0xFFFFAA00);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFFFFAA00);
        context.fill(bx, by, bx + 1, by + bh, 0xFFFFAA00);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFFFFAA00);

        // 类型标签
        boolean en = isEnglish();
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§e§l" + typeName + " §r§7- " + (en ? "Quiz Challenge" : "答题挑战"), centerX, boxTop, 0xFFFFAA00);

        // 题目标题
        context.drawCenteredTextWithShadow(this.textRenderer,
                en ? "§6§l⚡ Quiz Time ⚡" : "§6§l⚡ 答题时间 ⚡", centerX, boxTop + 14, 0xFFFFAA00);

        // 题目内容
        context.drawCenteredTextWithShadow(this.textRenderer,
                "§f" + question, centerX, boxTop + 30, 0xFFFFFFFF);

        // 扣血警告信息
        if (wrongCount > 0) {
            int nextDamage = (int) Math.min(wrongDamageMax, Math.pow(wrongDamageBase, wrongCount));
            String warning = en
                    ? "§c§l⚠ " + wrongCount + " wrong in a row! Next: -" + nextDamage + " HP!"
                    : "§c§l⚠ 已连续答错 " + wrongCount + " 次！下次扣 " + nextDamage + " 点血！";
            context.drawCenteredTextWithShadow(this.textRenderer, warning, centerX, boxTop + 46, 0xFFFF5555);
        } else {
            int firstDamage = (int) Math.min(wrongDamageMax, Math.pow(wrongDamageBase, 0));
            String hint = en
                    ? "§aFirst attempt, wrong = -" + firstDamage + " HP"
                    : "§a首次答题，答错扣 " + firstDamage + " 点血";
            context.drawCenteredTextWithShadow(this.textRenderer, hint, centerX, boxTop + 46, 0xFF55FF55);
        }

        // 渲染按钮
        super.render(context, mouseX, mouseY, delta);

        // 倒计时显示
        if (timeLimit > 0) {
            long elapsed = (System.currentTimeMillis() - openTime) / 1000;
            int remaining = Math.max(0, timeLimit - (int) elapsed);
            // 进度条
            int barX = centerX - 120;
            int barY = this.height / 2 + 108;
            int barW = 240;
            int barH = 6;
            float progress = (float) remaining / timeLimit;
            int color = remaining <= 5 ? 0xFFFF3333 : remaining <= 10 ? 0xFFFFAA00 : 0xFF55FF55;
            context.fill(barX, barY, barX + barW, barY + barH, 0x88000000);
            context.fill(barX, barY, barX + (int)(barW * progress), barY + barH, color);
            // 文字
            String timeText = remaining <= 5 ? "§c§l⏰ " + remaining + "秒" : remaining <= 10 ? "§e⏰ " + remaining + "秒" : "§a⏰ " + remaining + "秒";
            context.drawCenteredTextWithShadow(this.textRenderer, timeText, centerX, barY + barH + 3, 0xFFFFFFFF);

            // 倒计时到0自动提交错误答案
            if (remaining <= 0 && !autoSubmitted) {
                autoSubmitted = true;
                // 发送-1保证答错，正确答案只会是0-3
                ClientPlayNetworking.send(new QuizPayloads.AnswerQuizC2S(quizId, -1));
                for (var child : this.children()) {
                    if (child instanceof ButtonWidget b) b.active = false;
                }
            }
        } else {
            // 无倒计时时显示原来的提示
            context.drawCenteredTextWithShadow(this.textRenderer,
                    isEnglish() ? "§7Answer correctly to continue!" : "§7答对即可继续" + typeName + "！", centerX, this.height / 2 + 110, 0xFF999999);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 不能按ESC关闭
    }

    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏
    }
}
