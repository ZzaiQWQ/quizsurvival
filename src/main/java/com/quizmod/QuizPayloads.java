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
package com.quizmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public class QuizPayloads {

    // 服务器 → 客户端: 打开答题GUI
    public record OpenQuizS2C(String question, List<String> options, int quizId, String typeName, int wrongCount, int timeLimit, float wrongDamageBase, float wrongDamageMax) implements CustomPayload {
        public static final Id<OpenQuizS2C> ID = new Id<>(Identifier.of("quizsurvival", "open_quiz"));
        public static final PacketCodec<RegistryByteBuf, OpenQuizS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, OpenQuizS2C::question,
                PacketCodecs.STRING.collect(PacketCodecs.toList()), OpenQuizS2C::options,
                PacketCodecs.INTEGER, OpenQuizS2C::quizId,
                PacketCodecs.STRING, OpenQuizS2C::typeName,
                PacketCodecs.INTEGER, OpenQuizS2C::wrongCount,
                PacketCodecs.INTEGER, OpenQuizS2C::timeLimit,
                PacketCodecs.FLOAT, OpenQuizS2C::wrongDamageBase,
                PacketCodecs.FLOAT, OpenQuizS2C::wrongDamageMax,
                OpenQuizS2C::new
        );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 客户端 → 服务器: 提交答案
    public record AnswerQuizC2S(int quizId, int answerIndex) implements CustomPayload {
        public static final Id<AnswerQuizC2S> ID = new Id<>(Identifier.of("quizsurvival", "answer_quiz"));
        public static final PacketCodec<RegistryByteBuf, AnswerQuizC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, AnswerQuizC2S::quizId,
                PacketCodecs.INTEGER, AnswerQuizC2S::answerIndex,
                AnswerQuizC2S::new
        );

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 服务器 → 客户端: 答题结果
    public record QuizResultS2C(boolean correct, String message) implements CustomPayload {
        public static final Id<QuizResultS2C> ID = new Id<>(Identifier.of("quizsurvival", "quiz_result"));
        public static final PacketCodec<RegistryByteBuf, QuizResultS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOLEAN, QuizResultS2C::correct,
                PacketCodecs.STRING, QuizResultS2C::message,
                QuizResultS2C::new
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    // 服务器 → 客户端: 同步剩余免答次数 (格式: "chop:3,ore:0,stone:5")
    public record SyncActionsS2C(String data) implements CustomPayload {
        public static final Id<SyncActionsS2C> ID = new Id<>(Identifier.of("quizsurvival", "sync_actions"));
        public static final PacketCodec<RegistryByteBuf, SyncActionsS2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, SyncActionsS2C::data,
                SyncActionsS2C::new
        );
        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
