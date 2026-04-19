package com.quizmod.client;

import com.quizmod.QuizPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

public class QuizModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 收到题目 → 打开GUI
        ClientPlayNetworking.registerGlobalReceiver(QuizPayloads.OpenQuizS2C.ID, (payload, context) -> {
            context.client().execute(() -> MinecraftClient.getInstance().setScreen(
                    new QuizScreen(payload.question(), payload.options(), payload.quizId(), payload.typeName(), payload.wrongCount(), payload.timeLimit(), payload.wrongDamageBase(), payload.wrongDamageMax())
            ));
        });

        // 收到答题结果
        ClientPlayNetworking.registerGlobalReceiver(QuizPayloads.QuizResultS2C.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                // 用消息颜色判断音效：§c开头=答错，§a开头=答对
                boolean isWrong = payload.message().startsWith("§c");
                if (client.player != null) {
                    if (isWrong) {
                        client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                    } else {
                        client.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
                // correct=true 时关闭界面
                if (payload.correct() && client.currentScreen instanceof QuizScreen) {
                    client.setScreen(null);
                }
            });
        });

        // 收到免答次数同步
        ClientPlayNetworking.registerGlobalReceiver(QuizPayloads.SyncActionsS2C.ID, (payload, context) -> {
            context.client().execute(() -> QuizHud.parseAndUpdate(payload.data()));
        });

        // 注册HUD渲染
        HudRenderCallback.EVENT.register(new QuizHud());
    }
}
