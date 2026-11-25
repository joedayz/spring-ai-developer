package com.joedayz.springai.m03;

import jakarta.validation.constraints.NotBlank;

public record ProductMention(@NotBlank String product, Sentiment sentiment) {

    public enum Sentiment { POSITIVE, NEUTRAL, NEGATIVE }
}
