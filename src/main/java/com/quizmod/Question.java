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

import java.util.List;

public record Question(String text, List<String> options, int correctIndex) {
    public String getCorrectAnswer() {
        return options.get(correctIndex);
    }
}
