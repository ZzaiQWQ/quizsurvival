package com.quizmod;

import java.util.List;

public record Question(String text, List<String> options, int correctIndex) {
    public String getCorrectAnswer() {
        return options.get(correctIndex);
    }
}
