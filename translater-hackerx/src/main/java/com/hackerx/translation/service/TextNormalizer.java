package com.hackerx.translation.service;

import java.text.Normalizer;

public class TextNormalizer {

    public static String normalizeText(String text) {
        // Normalize text to NFC form
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }
}