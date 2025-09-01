package com.hackerx.translation.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.HarmCategory;
import com.google.cloud.aiplatform.v1.SafetySetting.HarmBlockThreshold;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.hackerx.translation.config.CustomSafetySetting;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class TranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationService.class);

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${google.cloud.credentials.file-path}")
    private String googleCredentialsFilePath;

    private Translate translate;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String targetLanguage;
    private List<String> outputDataList = new ArrayList<>();

    public TranslationService() {
    this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.SECONDS)
            .writeTimeout(500, TimeUnit.SECONDS)
            .readTimeout(500, TimeUnit.SECONDS)
            .build();
    // Initialize objectMapper only once
    this.objectMapper = new ObjectMapper();
}

    @PostConstruct
    public void initialize() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(googleCredentialsFilePath))
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
        this.translate = TranslateOptions.newBuilder().setCredentials(credentials).build().getService();
    }

    public String translateTextWithGemini(String text, String targetLanguage) throws IOException {
        if (text.isEmpty()) {
            return text;
        }
        text = TextPreprocessor.preprocessText(text, "Gemini");

        String requestBody = String.format(
                "{\"contents\":[{\"parts\":[{\"text\":\"JUST GIVE TRANSLATED TEXT IN OUTPUT,NOTHING ELSE ,DONT ADD ANY EXTRA CHARACTERS OR SPECIAL CHARACTERS(keep weblinks as they are)You are a highly skilled translation model specializing in translating technical educational content. "
                        + "The following text contains educational material for an online learning platform called HackerX. "
                        + "HackerX helps users learn ethical hacking through real-life examples, Python code, and bits of code. "
                        + "Currently, the tutorials are only available in English, and we need to translate them into various languages "
                        + "to make the content accessible to a broader audience. "
                        + "Your task is to translate the following text into the specified language, ensuring the following guidelines are strictly followed:\\n"
                        + "1. Maintain the original context and meaning of the text.\\n"
                        + "2. Preserve any placeholders in the format '__XXXX__' and ensure they are appropriately placed in the translated text.\\n"
                        + "3. Handle special characters, escape sequences, and formatting marks correctly to avoid any loss of information or formatting issues.\\n"
                        + "4. Translate technical terms accurately and consistently, referring to common terminology in the target language.\\n"
                        + "5. Ensure the translated text is clear, natural, and grammatically correct in the target language.\\n"
                        + "6. Do not include any gibberish or nonsensical content in the translation.\\n"
                        + "7. If any text seems out of context or unclear, translate it to the best of your ability while preserving the original intent,placeholders are supposed to be postprocessed by the backend,so please dont try to translate or do anything with placeholders which are surrounded by two underscores for example (__DOT__ , __COMMA__)Dont try to translate these.\\n"
                        + "Translate the following text to %s: %s\"}]}],\"safetySettings\": %s}",
                targetLanguage, text, objectMapper.writeValueAsString(getSafetySettings()));

        HttpUrl.Builder urlBuilder = HttpUrl.parse(geminiApiUrl).newBuilder();
        urlBuilder.addQueryParameter("key", geminiApiKey);

        Request request = new Request.Builder().url(urlBuilder.build())
                .post(RequestBody.create(requestBody, MediaType.parse("application/json"))).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBodyStr = response.body().string();

            Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                if (content != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        String translatedText = (String) parts.get(0).get("text");
                        if (translatedText != null) {
                            return TextPreprocessor.postprocessText(translatedText.strip(), "Gemini", "Gemini");
                        }
                    }
                }
                String finishReason = (String) candidate.get("finishReason");
                if ("SAFETY".equals(finishReason)) {
                    return "Translation blocked due to safety filters.";
                }
            }
            throw new IOException("No translated text found in the response");
        }
    }

    private List<CustomSafetySetting> getSafetySettings() {
        return List.of(
                new CustomSafetySetting(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT.name(),
                        HarmBlockThreshold.BLOCK_NONE.name()),
                new CustomSafetySetting(HarmCategory.HARM_CATEGORY_HATE_SPEECH.name(),
                        HarmBlockThreshold.BLOCK_NONE.name()),
                new CustomSafetySetting(HarmCategory.HARM_CATEGORY_HARASSMENT.name(),
                        HarmBlockThreshold.BLOCK_NONE.name()),
                new CustomSafetySetting(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT.name(),
                        HarmBlockThreshold.BLOCK_NONE.name()));
    }

    public String translateTextWithChatGPT(String text, String targetLanguage) throws IOException {
    if (text.isEmpty()) {
        return text;
    }
    System.out.println("Received in translateTextWithChatGPT");

    String systemPrompt = SystemPrompts.getChatGPTTranslationPrompt(targetLanguage, text);

    String requestBody = objectMapper.writeValueAsString(Map.of("model", "gpt-4o-mini-2024-07-18", "messages",
            List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", text))));

    Request request = new Request.Builder().url("https://api.openai.com/v1/chat/completions")
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .addHeader("Authorization", "Bearer " + openaiApiKey).build();

    try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        String responseBodyStr = response.body().string();
        Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String translatedText = (String) message.get("content");
        System.out.println("Received Translated Text");
        return translatedText;
    }
}

    public List<Map<String, Object>> translateNDJson(String content, String language, String service) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        		System.out.println("Received  in translateNDJSON");

        if (content == null || content.trim().isEmpty()) {
            return results;
        }
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            
            try {
                // Parse single JSON line using LinkedHashMap
                Map<String, Object> json = objectMapper.readValue(line, LinkedHashMap.class);
                
                // Translate the JSON object using existing method
                Map<String, Object> translatedJson = translateJson(json, language, service);
                
                // Add to results
                results.add(translatedJson);
                
            } catch (Exception e) {
                LOGGER.error("Error processing JSON line: " + line, e);
                Map<String, Object> errorJson = new LinkedHashMap<>();
                errorJson.put("error", "Failed to process line: " + e.getMessage());
                errorJson.put("original_line", line);
                results.add(errorJson);
            }
        }
        
        return results;
    }

    public Map<String, Object> translateJson(Map<String, Object> json, String targetLanguage, String service)
        throws IOException {
        this.targetLanguage = targetLanguage;
        System.out.println("Received  in translatejson");


        Map<String, Object> originalJson = new LinkedHashMap<>(json);
        outputDataList.clear();
        saveOutputData(originalJson);  // Use originalJson instead of json

        List<String> textList = new ArrayList<>();
        List<String> keysList = new ArrayList<>();

        collectTextToTranslate(originalJson, textList, keysList, false);

        List<String> translatedTextList = translateTextList(textList, targetLanguage, service);
        Iterator<String> translatedTextIterator = translatedTextList.iterator();

        putTranslatedTextBack(originalJson, keysList, translatedTextIterator);
        restoreOutputData(originalJson);

        // Process highlights after translation
        processHighlights(originalJson);

        return originalJson;  // Return originalJson instead of json
    }


    private void processHighlights(Map<String, Object> json) throws IOException {
        if (json.containsKey("data") && json.containsKey("highlight")) {
            String translatedData = (String) json.get("data");
            List<Map<String, Object>> highlights = (List<Map<String, Object>>) json.get("highlight");

            for (Map<String, Object> highlight : highlights) {
                String originalKeyTitle = (String) highlight.get("key_title");
                String translatedKeyTitle = findCorrespondingText(translatedData, originalKeyTitle);
                highlight.put("key_title", translatedKeyTitle);
            }
        }

        // Recursively process nested structures
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if (entry.getValue() instanceof Map) {
                processHighlights((Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                for (Object item : (List<Object>) entry.getValue()) {
                    if (item instanceof Map) {
                        processHighlights((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    private String findCorrespondingText(String translatedData, String originalKeyTitle) throws IOException {
        if (translatedData == null || originalKeyTitle == null) {
            LOGGER.warn("Null data received in findCorrespondingText. TranslatedData: {}, OriginalKeyTitle: {}", 
                translatedData, originalKeyTitle);
            return originalKeyTitle; // Return original if either is null
        }
        
        String prompt = String.format(
            "Original highlighted text: \"%s\"\n" +
            "Translated text: \"%s\"\n\n" +
            "Find the exact phrase in the translated text that corresponds to the original highlighted text. " +
            "Return only the found phrase, nothing else. If there's no exact match, return the closest matching phrase. " +
            "Do not include quotation marks in your response.",
            originalKeyTitle,
            translatedData
        );

        try {
            String response = callChatGPTAPI(prompt);
            return response.replaceAll("^[\"']|[\"']$", "").trim();
        } catch (IOException e) {
            LOGGER.error("Error finding corresponding text. Returning original key title.", e);
            return originalKeyTitle;
        }
    }

    private String callChatGPTAPI(String prompt) throws IOException {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "model", "gpt-4o-mini-2024-07-18",
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.7
        ));

        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .addHeader("Authorization", "Bearer " + openaiApiKey)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBodyStr = response.body().string();
            Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return ((String) message.get("content")).trim();
        }
    }

    private List<String> translateTextList(List<String> textList, String targetLanguage, String service)
            throws IOException {
        List<String> translatedTextList = new ArrayList<>();
        for (String text : textList) {
            String translatedText;
            if ("ChatGPT".equalsIgnoreCase(service)) {
                translatedText = translateTextWithChatGPT(text, targetLanguage);
            } else if ("Gemini".equalsIgnoreCase(service)) {
                translatedText = translateTextWithGemini(text, targetLanguage);
            } else {
                throw new IllegalArgumentException("Unsupported translation service: " + service);
            }
            translatedTextList.add(translatedText);
        }
        return translatedTextList;
    }

    public void collectTextToTranslate(Map<String, Object> json, List<String> textList, List<String> keysList,
            boolean skipTranslation) {
        boolean isOutputType = false;
        boolean shouldTranslateNext = true;

        List<Map.Entry<String, Object>> entries = new ArrayList<>(json.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("type".equals(key) && "OUTPUT".equals(value)) {
                isOutputType = true;
                continue;
            }

            if (isOutputType && "data".equals(key)) {
                continue;
            }

            if ("translate_content".equals(key)) {
                shouldTranslateNext = !Boolean.FALSE.toString().equalsIgnoreCase(value.toString());
                continue;
            }

            if (!skipTranslation && shouldTranslate(key)) {
                if (shouldTranslateNext) {
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        if (!stringValue.isEmpty()) {
                            textList.add(stringValue);
                            keysList.add(key);
                        }
                    } else if (value instanceof List) {
                        List<Object> list = (List<Object>) value;
                        for (int j = 0; j < list.size(); j++) {
                            Object item = list.get(j);
                            if (item instanceof String) {
                                String stringItem = (String) item;
                                if (!stringItem.isEmpty()) {
                                    textList.add(stringItem);
                                    keysList.add(key + "_" + j);
                                }
                            } else if (item instanceof Map) {
                                collectTextToTranslate((Map<String, Object>) item, textList, keysList, skipTranslation);
                            }
                        }
                    }
                }
                shouldTranslateNext = true; // Reset for the next key
            }

            if (value instanceof Map) {
                collectTextToTranslate((Map<String, Object>) value, textList, keysList, skipTranslation);
            } else if (value instanceof List && !"option".equals(key)) {
                for (Object item : (List<Object>) value) {
                    if (item instanceof Map) {
                        collectTextToTranslate((Map<String, Object>) item, textList, keysList, skipTranslation);
                    }
                }
            }
        }
    }

    private void putTranslatedTextBack(Map<String, Object> json, List<String> keysList, Iterator<String> iterator) {
        boolean isOutputType = false;
        boolean shouldTranslateNext = true;

        List<Map.Entry<String, Object>> entries = new ArrayList<>(json.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Object> entry = entries.get(i);
            String key = entry.getKey();
            Object value = entry.getValue();

            if ("type".equals(key) && "OUTPUT".equals(value)) {
                isOutputType = true;
                continue;
            }

            if (isOutputType && "data".equals(key)) {
                continue;
            }

            if ("translate_content".equals(key)) {
                shouldTranslateNext = !Boolean.FALSE.toString().equalsIgnoreCase(value.toString());
                continue;
            }

            if (shouldTranslate(key)) {
                if (shouldTranslateNext) {
                    if (value instanceof String) {
                        if (iterator.hasNext() && !((String) value).isEmpty()) {
                            String newValue = iterator.next();
                            entry.setValue(TextPreprocessor.postprocessText(newValue, key, "service"));
                        }
                    } else if (value instanceof List) {
                        List<Object> list = (List<Object>) value;
                        for (int j = 0; j < list.size(); j++) {
                            Object item = list.get(j);
                            if (item instanceof String) {
                                if (iterator.hasNext() && !((String) item).isEmpty()) {
                                    String newValue = iterator.next();
                                    list.set(j, TextPreprocessor.postprocessText(newValue, key + "_" + j, "service"));
                                }
                            } else if (item instanceof Map) {
                                putTranslatedTextBack((Map<String, Object>) item, keysList, iterator);
                            }
                        }
                    }
                }
                shouldTranslateNext = true; // Reset for the next key
            }

            if (value instanceof Map) {
                putTranslatedTextBack((Map<String, Object>) value, keysList, iterator);
            } else if (value instanceof List && !"option".equals(key)) {
                for (Object item : (List<Object>) value) {
                    if (item instanceof Map) {
                        putTranslatedTextBack((Map<String, Object>) item, keysList, iterator);
                    }
                }
            }
        }
    }

    private void saveOutputData(Map<String, Object> json) {
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if ("type".equals(entry.getKey()) && "OUTPUT".equals(entry.getValue())) {
                if (json.containsKey("data")) {
                    outputDataList.add((String) json.get("data"));
                    json.put("data", ""); // Clear the data field
                }
            } else if (entry.getValue() instanceof Map) {
                saveOutputData((Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                for (Object item : (List<Object>) entry.getValue()) {
                    if (item instanceof Map) {
                        saveOutputData((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    private void restoreOutputData(Map<String, Object> json) {
        for (Map.Entry<String, Object> entry : json.entrySet()) {
            if ("type".equals(entry.getKey()) && "OUTPUT".equals(entry.getValue())) {
                if (json.containsKey("data") && !outputDataList.isEmpty()) {
                    json.put("data", outputDataList.remove(0));
                }
            } else if (entry.getValue() instanceof Map) {
                restoreOutputData((Map<String, Object>) entry.getValue());
            } else if (entry.getValue() instanceof List) {
                for (Object item : (List<Object>) entry.getValue()) {
                    if (item instanceof Map) {
                        restoreOutputData((Map<String, Object>) item);
                    }
                }
            }
        }
    }

    // Add these methods to your TranslationService class

/**
 * Translates all values in a flat App JSON structure
 * 
 * @param json The app JSON with keys and values to translate
 * @param targetLanguage The target language code (e.g., "es", "fr")
 * @param service The translation service to use ("Google", "ChatGPT", "Gemini")
 * @return The JSON with translated values
 * @throws IOException If translation fails
 */
public Map<String, String> translateAppJson(Map<String, String> json, String targetLanguage, String service) 
        throws IOException {
    // Skip translation if empty
    if (json == null || json.isEmpty()) {
        return new LinkedHashMap<>();
    }
    
    LOGGER.info("Translating App JSON with {} keys to {}", json.size(), targetLanguage);
    
    // Extract all values that need translation
    List<String> valuesToTranslate = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    
    for (Map.Entry<String, String> entry : json.entrySet()) {
        String value = entry.getValue();
        if (value != null && !value.trim().isEmpty()) {
            valuesToTranslate.add(value);
            keys.add(entry.getKey());
        }
    }
    
    // Translate all values
    List<String> translatedValues = translateTextList(valuesToTranslate, targetLanguage, service);
    
    // Create new JSON with translated values
    Map<String, String> translatedJson = new LinkedHashMap<>();
    for (int i = 0; i < keys.size(); i++) {
        translatedJson.put(keys.get(i), translatedValues.get(i));
    }
    
    // Copy any untranslated entries (should be rare)
    for (Map.Entry<String, String> entry : json.entrySet()) {
        if (!translatedJson.containsKey(entry.getKey())) {
            translatedJson.put(entry.getKey(), entry.getValue());
        }
    }
    
    return translatedJson;
}



    private boolean shouldTranslate(String key) {
        return key.equals("subject") || key.equals("topic_name") || key.equals("data") || key.equals("question_text")
               || key.equals("correct_explanation") || key.equals("incorrect_explanation") || key.equals("option")
                || key.equals("info_text") || key.equals("tap_option") || key.equals("rhs") || key.equals("lhs")
                || key.equals("key_title") || key.equals("hint") || key.equals("content") || key.equals("description") || key.equals("title") || key.equals("name") || key.equals("subtopic_name");
    }

/**
 * Translates a batch of NDJSON lines received from the frontend (typically 50 lines)
 * by extracting all translatable content, sending it as a single batch to the translation
 * service, and reconstructing the translated JSON objects.
 * 
 * @param content The NDJSON content as a string (multiple JSON objects, one per line)
 * @param language Target language code (e.g., "es", "fr")
 * @param service Translation service to use ("ChatGPT", "Gemini")
 * @return List of translated JSON objects
 * @throws IOException If translation fails
 */
public List<Map<String, Object>> translateNDJsonBatch(String content, String language, String service) 
        throws IOException {
    List<Map<String, Object>> results = new ArrayList<>();
    LOGGER.info("Starting batch translation of NDJSON to {}", language);
    
    // Handle empty content
    if (content == null || content.trim().isEmpty()) {
        return results;
    }
    
    // Step 1: Parse each NDJSON line into a separate JSON object
    List<Object[]> documentData = new ArrayList<>(); // Will hold: [originalJson, textToTranslate, keys, outputData]
    
    String[] lines = content.split("\n");
    for (String line : lines) {
        if (line.trim().isEmpty()) {
            continue;
        }
        
        try {
            // Parse single JSON line
            Map<String, Object> json = objectMapper.readValue(line, LinkedHashMap.class);
            
            // Create a working copy
            Map<String, Object> originalJson = new LinkedHashMap<>(json);
            
            // Create collections to store data for this document
            List<String> outputDataList = new ArrayList<>();
            List<String> textList = new ArrayList<>();
            List<String> keysList = new ArrayList<>();
            
            // Save OUTPUT data
            saveNDJsonOutputData(originalJson, outputDataList);
            
            // Collect text to translate
            collectNDJsonTextToTranslate(originalJson, textList, keysList, false);
            
            // Store everything related to this document
            documentData.add(new Object[] {
                originalJson,
                textList,
                keysList,
                outputDataList
            });
            
        } catch (Exception e) {
            LOGGER.error("Error processing JSON line: " + line, e);
            
            // Add error information to results
            Map<String, Object> errorJson = new LinkedHashMap<>();
            errorJson.put("error", "Failed to process line: " + e.getMessage());
            errorJson.put("original_line", line);
            results.add(errorJson);
        }
    }
    
    // Step 2: Combine all text to translate into a single batch
    List<String> allTextsToTranslate = new ArrayList<>();
    List<Integer> documentIndices = new ArrayList<>();
    List<Integer> textIndices = new ArrayList<>();
    
    for (int docIndex = 0; docIndex < documentData.size(); docIndex++) {
        Object[] docData = documentData.get(docIndex);
        @SuppressWarnings("unchecked")
        List<String> docTexts = (List<String>) docData[1];
        
        for (int textIndex = 0; textIndex < docTexts.size(); textIndex++) {
            allTextsToTranslate.add(docTexts.get(textIndex));
            documentIndices.add(docIndex);
            textIndices.add(textIndex);
        }
    }
    
    // Step 3: Translate all text in batch
    LOGGER.info("Translating batch of {} text segments from {} NDJSON objects", 
            allTextsToTranslate.size(), documentData.size());
    
    List<String> allTranslatedTexts;
    
    if (!allTextsToTranslate.isEmpty()) {
        if ("ChatGPT".equalsIgnoreCase(service)) {
            allTranslatedTexts = translateBatchWithChatGPT(allTextsToTranslate, language);
        } else if ("Gemini".equalsIgnoreCase(service)) {
            allTranslatedTexts = translateBatchWithGemini(allTextsToTranslate, language);
        } else {
            throw new IllegalArgumentException("Unsupported translation service: " + service);
        }
    } else {
        allTranslatedTexts = new ArrayList<>();
    }
    
    // Step 4: Distribute translated text back to their source documents
    if (allTranslatedTexts.size() == allTextsToTranslate.size()) {
        for (int i = 0; i < allTranslatedTexts.size(); i++) {
            int docIndex = documentIndices.get(i);
            int textIndex = textIndices.get(i);
            
            Object[] docData = documentData.get(docIndex);
            @SuppressWarnings("unchecked")
            List<String> docTexts = (List<String>) docData[1];
            
            docTexts.set(textIndex, allTranslatedTexts.get(i));
        }
    } else {
        LOGGER.error("Translation count mismatch: expected {}, got {}. Falling back to individual translation.", 
                allTextsToTranslate.size(), allTranslatedTexts.size());
        
        // If there's a count mismatch, fall back to individual translation
        for (Object[] docData : documentData) {
            @SuppressWarnings("unchecked")
            List<String> docTexts = (List<String>) docData[1];
            
            for (int i = 0; i < docTexts.size(); i++) {
                String text = docTexts.get(i);
                String translatedText;
                
                if ("ChatGPT".equalsIgnoreCase(service)) {
                    translatedText = translateTextWithChatGPT(text, language);
                } else if ("Gemini".equalsIgnoreCase(service)) {
                    translatedText = translateTextWithGemini(text, language);
                } else {
                    throw new IllegalArgumentException("Unsupported translation service: " + service);
                }
                
                docTexts.set(i, translatedText);
            }
        }
    }
    
    // Step 5: Reconstruct all translated documents
    for (Object[] docData : documentData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> originalJson = (Map<String, Object>) docData[0];
        
        @SuppressWarnings("unchecked")
        List<String> textList = (List<String>) docData[1];
        
        @SuppressWarnings("unchecked")
        List<String> keysList = (List<String>) docData[2];
        
        @SuppressWarnings("unchecked")
        List<String> outputDataList = (List<String>) docData[3];
        
        // Put translated text back in the document
        Iterator<String> translatedTextIterator = textList.iterator();
        putNDJsonTranslatedTextBack(originalJson, keysList, translatedTextIterator, service);
        
        // Restore OUTPUT data
        restoreNDJsonOutputData(originalJson, outputDataList);
        
        // Process highlights if needed
        try {
            processNDJsonHighlights(originalJson);
        } catch (Exception e) {
            LOGGER.error("Error processing highlights: {}", e.getMessage());
        }
        
        // Add the fully translated document to results
        results.add(originalJson);
    }
    
    return results;
}



/**
 * Saves OUTPUT type data before translation (for a single NDJSON object)
 */
private void saveNDJsonOutputData(Map<String, Object> json, List<String> outputDataList) {
    for (Map.Entry<String, Object> entry : json.entrySet()) {
        if ("type".equals(entry.getKey()) && "OUTPUT".equals(entry.getValue())) {
            if (json.containsKey("data")) {
                outputDataList.add((String) json.get("data"));
                json.put("data", ""); // Clear the data field
            }
        } else if (entry.getValue() instanceof Map) {
            saveNDJsonOutputData((Map<String, Object>) entry.getValue(), outputDataList);
        } else if (entry.getValue() instanceof List) {
            for (Object item : (List<Object>) entry.getValue()) {
                if (item instanceof Map) {
                    saveNDJsonOutputData((Map<String, Object>) item, outputDataList);
                }
            }
        }
    }
}

/**
 * Restores OUTPUT type data after translation (for a single NDJSON object)
 */
private void restoreNDJsonOutputData(Map<String, Object> json, List<String> outputDataList) {
    if (outputDataList.isEmpty()) {
        return;
    }
    
    for (Map.Entry<String, Object> entry : json.entrySet()) {
        if ("type".equals(entry.getKey()) && "OUTPUT".equals(entry.getValue())) {
            if (json.containsKey("data") && !outputDataList.isEmpty()) {
                json.put("data", outputDataList.remove(0));
            }
        } else if (entry.getValue() instanceof Map) {
            restoreNDJsonOutputData((Map<String, Object>) entry.getValue(), outputDataList);
        } else if (entry.getValue() instanceof List) {
            for (Object item : (List<Object>) entry.getValue()) {
                if (item instanceof Map) {
                    restoreNDJsonOutputData((Map<String, Object>) item, outputDataList);
                }
            }
        }
    }
}

/**
 * Collects text that needs to be translated from a JSON object
 */
private void collectNDJsonTextToTranslate(Map<String, Object> json, List<String> textList, 
        List<String> keysList, boolean skipTranslation) {
    boolean isOutputType = false;
    boolean shouldTranslateNext = true;

    List<Map.Entry<String, Object>> entries = new ArrayList<>(json.entrySet());
    for (int i = 0; i < entries.size(); i++) {
        Map.Entry<String, Object> entry = entries.get(i);
        String key = entry.getKey();
        Object value = entry.getValue();

        if ("type".equals(key) && "OUTPUT".equals(value)) {
            isOutputType = true;
            continue;
        }

        if (isOutputType && "data".equals(key)) {
            continue;
        }

        if ("translate_content".equals(key)) {
            shouldTranslateNext = !Boolean.FALSE.toString().equalsIgnoreCase(value.toString());
            continue;
        }

        if (!skipTranslation && shouldTranslate(key)) {
            if (shouldTranslateNext) {
                if (value instanceof String) {
                    String stringValue = (String) value;
                    if (!stringValue.isEmpty()) {
                        // Add text directly without preprocessing
                        textList.add(stringValue);
                        keysList.add(key);
                    }
                } else if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    for (int j = 0; j < list.size(); j++) {
                        Object item = list.get(j);
                        if (item instanceof String) {
                            String stringItem = (String) item;
                            if (!stringItem.isEmpty()) {
                                // Add text directly without preprocessing
                                textList.add(stringItem);
                                keysList.add(key + "_" + j);
                            }
                        } else if (item instanceof Map) {
                            collectNDJsonTextToTranslate((Map<String, Object>) item, textList, keysList, skipTranslation);
                        }
                    }
                }
            }
            shouldTranslateNext = true;
        }

        if (value instanceof Map) {
            collectNDJsonTextToTranslate((Map<String, Object>) value, textList, keysList, skipTranslation);
        } else if (value instanceof List && !"option".equals(key)) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    collectNDJsonTextToTranslate((Map<String, Object>) item, textList, keysList, skipTranslation);
                }
            }
        }
    }
}

/**
 * Puts translated text back into the JSON object
 */
private void putNDJsonTranslatedTextBack(Map<String, Object> json, List<String> keysList, 
        Iterator<String> iterator, String serviceType) {
    boolean isOutputType = false;
    boolean shouldTranslateNext = true;

    List<Map.Entry<String, Object>> entries = new ArrayList<>(json.entrySet());
    for (int i = 0; i < entries.size(); i++) {
        Map.Entry<String, Object> entry = entries.get(i);
        String key = entry.getKey();
        Object value = entry.getValue();

        if ("type".equals(key) && "OUTPUT".equals(value)) {
            isOutputType = true;
            continue;
        }

        if (isOutputType && "data".equals(key)) {
            continue;
        }

        if ("translate_content".equals(key)) {
            shouldTranslateNext = !Boolean.FALSE.toString().equalsIgnoreCase(value.toString());
            continue;
        }

        if (shouldTranslate(key)) {
            if (shouldTranslateNext) {
                if (value instanceof String) {
                    if (iterator.hasNext() && !((String) value).isEmpty()) {
                        String newValue = iterator.next();
                        // Fix double question marks directly here
                        newValue = newValue.replaceAll("\\?{2,}", "?");
                        // Set value directly without postprocessing
                        entry.setValue(newValue);
                    }
                } else if (value instanceof List) {
                    List<Object> list = (List<Object>) value;
                    for (int j = 0; j < list.size(); j++) {
                        Object item = list.get(j);
                        if (item instanceof String) {
                            if (iterator.hasNext() && !((String) item).isEmpty()) {
                                String newValue = iterator.next();
                                // Fix double question marks directly here
                                newValue = newValue.replaceAll("\\?{2,}", "?");
                                // Set value directly without postprocessing
                                list.set(j, newValue);
                            }
                        } else if (item instanceof Map) {
                            putNDJsonTranslatedTextBack((Map<String, Object>) item, keysList, iterator, serviceType);
                        }
                    }
                }
            }
            shouldTranslateNext = true;
        }

        if (value instanceof Map) {
            putNDJsonTranslatedTextBack((Map<String, Object>) value, keysList, iterator, serviceType);
        } else if (value instanceof List && !"option".equals(key)) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    putNDJsonTranslatedTextBack((Map<String, Object>) item, keysList, iterator, serviceType);
                }
            }
        }
    }
}

/**
 * Translates a batch of text segments with ChatGPT in a single API call
 */
private List<String> translateBatchWithChatGPT(List<String> batch, String targetLanguage) throws IOException {
    if (batch.isEmpty()) {
        return new ArrayList<>();
    }
    
    LOGGER.info("Sending batch of {} texts to ChatGPT", batch.size());
    
    // If there's only one item, use the existing method
    if (batch.size() == 1) {
        return List.of(translateTextWithChatGPT(batch.get(0), targetLanguage));
    }
    
    // Create a unique separator with UUID
    String separator = "---TRANSLATION_SEPARATOR_" + UUID.randomUUID().toString() + "---";
    
    // Join all texts with the separator
    StringBuilder combinedText = new StringBuilder();
    for (int i = 0; i < batch.size(); i++) {
        combinedText.append("Text ").append(i + 1).append(":\n");
        combinedText.append(batch.get(i));
        if (i < batch.size() - 1) {
            combinedText.append("\n").append(separator).append("\n");
        }
    }
    
    // System prompt that emphasizes just returning translations
    String systemPrompt = String.format(
        "You are a translation engine that translates text from English to %s. " +
        "ONLY RETURN THE TRANSLATED TEXT. DO NOT ADD ANY COMMENTS, EXPLANATIONS, OR NOTES. " +
        "Multiple texts will be provided, separated by '%s'. " +
        "Each text is preceded by 'Text N:' where N is a number. " +
        "Translate each text separately, preserving these 'Text N:' markers and separators in your response. " +
        "Maintain the exact format of the input in your output, just with translated content.",
        targetLanguage, separator);
    
    String requestBody = objectMapper.writeValueAsString(Map.of(
        "model", "gpt-4o-mini-2024-07-18", 
        "messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", combinedText.toString())
        ),
        "temperature", 0.3 // Lower temperature for more consistent translations
    ));
    
    Request request = new Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
        .addHeader("Authorization", "Bearer " + openaiApiKey)
        .build();
    
    try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        
        String responseBodyStr = response.body().string();
        Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String translatedBatch = (String) message.get("content");
        
        // Split the response by the separator to get individual translations
        List<String> translatedTexts = new ArrayList<>();
        for (String part : translatedBatch.split(Pattern.quote(separator))) {
            // Clean up any "Text N:" prefixes and trim whitespace
            String cleaned = part.replaceFirst("(?i)^\\s*Text\\s+\\d+:\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                translatedTexts.add(cleaned);
            }
        }
        
        // Ensure we have the right number of translations
        if (translatedTexts.size() != batch.size()) {
            LOGGER.warn("Expected {} translations but got {}. Falling back to individual translation.",
                    batch.size(), translatedTexts.size());
            
            // Fall back to translating individually if the count doesn't match
            translatedTexts.clear();
            for (String text : batch) {
                translatedTexts.add(translateTextWithChatGPT(text, targetLanguage));
            }
        }
        
        return translatedTexts;
    }
}

/**
 * Translates a batch of text segments with Gemini in a single API call
 */
private List<String> translateBatchWithGemini(List<String> batch, String targetLanguage) throws IOException {
    if (batch.isEmpty()) {
        return new ArrayList<>();
    }
    
    LOGGER.info("Sending batch of {} texts to Gemini", batch.size());
    
    // If there's only one item, use the existing method
    if (batch.size() == 1) {
        return List.of(translateTextWithGemini(batch.get(0), targetLanguage));
    }
    
    // Create a unique separator with UUID
    String separator = "---TRANSLATION_SEPARATOR_" + UUID.randomUUID().toString() + "---";
    
    // Join all texts with the separator
    StringBuilder combinedText = new StringBuilder();
    for (int i = 0; i < batch.size(); i++) {
        combinedText.append("Text ").append(i + 1).append(":\n");
        combinedText.append(batch.get(i));
        if (i < batch.size() - 1) {
            combinedText.append("\n").append(separator).append("\n");
        }
    }
    
    // Simple prompt focused just on translation
    String prompt = "TRANSLATE THE FOLLOWING TEXTS TO " + targetLanguage + ". " +
                    "ONLY RETURN THE TRANSLATED TEXT. DO NOT ADD ANY COMMENTS OR EXPLANATIONS. " +
                    "Multiple texts are provided below, separated by '" + separator + "'. " +
                    "Each text starts with 'Text N:' where N is a number. " +
                    "Translate each text separately and maintain the exact same format in your response.\n\n" +
                    combinedText.toString();
    
    String requestBody = String.format(
            "{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}],\"safetySettings\": %s}",
            prompt.replace("\"", "\\\""), 
            objectMapper.writeValueAsString(getSafetySettings()));
    
    HttpUrl.Builder urlBuilder = HttpUrl.parse(geminiApiUrl).newBuilder();
    urlBuilder.addQueryParameter("key", geminiApiKey);
    
    Request request = new Request.Builder().url(urlBuilder.build())
            .post(RequestBody.create(requestBody, MediaType.parse("application/json"))).build();
    
    try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }
        
        String responseBodyStr = response.body().string();
        Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
        
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> candidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            
            if (content != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                
                if (parts != null && !parts.isEmpty()) {
                    String translatedBatch = (String) parts.get(0).get("text");
                    
                    // Split the response by the separator to get individual translations
                    List<String> translatedTexts = new ArrayList<>();
                    for (String part : translatedBatch.split(Pattern.quote(separator))) {
                        // Clean up any "Text N:" prefixes and trim whitespace
                        String cleaned = part.replaceFirst("(?i)^\\s*Text\\s+\\d+:\\s*", "").trim();
                        if (!cleaned.isEmpty()) {
                            translatedTexts.add(cleaned);
                        }
                    }
                    
                    // Ensure we have the right number of translations
                    if (translatedTexts.size() != batch.size()) {
                        LOGGER.warn("Expected {} translations but got {}. Falling back to individual translation.",
                                batch.size(), translatedTexts.size());
                        
                        // Fall back to translating individually
                        translatedTexts.clear();
                        for (String text : batch) {
                            translatedTexts.add(translateTextWithGemini(text, targetLanguage));
                        }
                    }
                    
                    return translatedTexts;
                }
            }
            
            String finishReason = (String) candidate.get("finishReason");
            if ("SAFETY".equals(finishReason)) {
                LOGGER.warn("Translation blocked due to safety filters. Falling back to individual translation.");
            }
        }
        
        // Fall back to translating individually
        List<String> fallbackResults = new ArrayList<>();
        for (String text : batch) {
            fallbackResults.add(translateTextWithGemini(text, targetLanguage));
        }
        return fallbackResults;
    }
}


/**
 * Process highlights after translation for NDJSON objects
 */
private void processNDJsonHighlights(Map<String, Object> json) throws IOException {
    if (json.containsKey("data") && json.containsKey("highlight")) {
        String translatedData = (String) json.get("data");
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) json.get("highlight");

        for (Map<String, Object> highlight : highlights) {
            String originalKeyTitle = (String) highlight.get("key_title");
            String translatedKeyTitle = findNDJsonCorrespondingText(translatedData, originalKeyTitle);
            highlight.put("key_title", translatedKeyTitle);
        }
    }

    // Recursively process nested structures
    for (Map.Entry<String, Object> entry : json.entrySet()) {
        if (entry.getValue() instanceof Map) {
            processNDJsonHighlights((Map<String, Object>) entry.getValue());
        } else if (entry.getValue() instanceof List) {
            for (Object item : (List<Object>) entry.getValue()) {
                if (item instanceof Map) {
                    processNDJsonHighlights((Map<String, Object>) item);
                }
            }
        }
    }
}

/**
 * Find corresponding text in translated content for highlights
 */
private String findNDJsonCorrespondingText(String translatedData, String originalKeyTitle) throws IOException {
    if (translatedData == null || originalKeyTitle == null) {
        LOGGER.warn("Null data received in findNDJsonCorrespondingText");
        return originalKeyTitle;
    }
    
    String prompt = String.format(
        "Original highlighted text: \"%s\"\n" +
        "Translated text: \"%s\"\n\n" +
        "Find the exact phrase in the translated text that corresponds to the original highlighted text. " +
        "Return ONLY the found phrase, nothing else.",
        originalKeyTitle,
        translatedData
    );

    try {
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "model", "gpt-4o-mini-2024-07-18",
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.3
        ));

        Request request = new Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .addHeader("Authorization", "Bearer " + openaiApiKey)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBodyStr = response.body().string();
            Map<String, Object> responseBody = objectMapper.readValue(responseBodyStr, Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return ((String) message.get("content")).trim();
        }
    } catch (IOException e) {
        LOGGER.error("Error finding corresponding text: {}", e.getMessage());
        return originalKeyTitle;
    }
}




}
