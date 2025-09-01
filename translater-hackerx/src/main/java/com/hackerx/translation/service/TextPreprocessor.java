package com.hackerx.translation.service;
import java.text.Normalizer;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

public class TextPreprocessor {

    private static final Logger logger = Logger.getLogger(TextPreprocessor.class.getName());
    private static Map<String, String> urlMap = new HashMap<>();

    public static String preprocessText(String text, String key) {
       // //System.out.println("Preprocessing text for key: " + key);
       // //System.out.println("Original text: " + text);

        // Escape special characters and common escape sequences
        text = text.replace("\n", "__NEWLINE__")
                   .replace("\t", "__TAB__")
                   .replace("\\", "__BACKSLASH__")
                   .replace("'", "__SINGLEQUOTE__")
                   .replace("’", "__CURLYSINGLEQUOTE__")
                   .replace("‘", "__LSINGLEQUOTE__")
                   .replace("&", "__AMPERSAND__")
                   .replace("\"", "__DOUBLEQUOTE__")
                   .replace("“", "__LDOUBLEQUOTE__")
                   .replace("”", "__RDOUBLEQUOTE__")
                   .replace("%", "__PERCENT__")
                   .replace("!", "__EXCLAMATION__")
                   .replace("#", "__HASH__")
                   .replace("$", "__DOLLAR__")
                   .replace("(", "__LPAREN__")
                   .replace(")", "__RPAREN__")
                   .replace("*", "__ASTERISK__")
                   .replace("+", "__PLUS__")
                   .replace(",", "__COMMA__")
                  // .replace("-", "__MINUS__")
                   .replace(".", "__DOT__")
                   .replace("/", "__SLASH__")
                   .replace(":", "__COLON__")
                   .replace(";", "__SEMICOLON__")
                   .replace("=", "__EQUAL__")
                   .replace("?", "__QUESTION__")
                   .replace("@", "__AT__")
                   .replace("[", "__LBRACKET__")
                   .replace("]", "__RBRACKET__")
                   .replace("^", "__CARET__")
                   .replace("`", "__BACKTICK__")
                   .replace("{", "__LBRACE__")
                   .replace("|", "__PIPE__")
                   .replace("}", "__RBRACE__")
                   .replace("~", "__TILDE__");

//        // Handle URLs
//        if (text.contains("\nhttps://") || text.contains("\nhttp://")) {
//            urlMap.put(key, text);
//            text = text.replace("\nhttps://", "https://")
//                       .replace("\nhttp://", "http://");  // Remove newline before the URL
//        }

        // Normalize text to NFC form
        text = Normalizer.normalize(text, Normalizer.Form.NFC);

      //  //System.out.println("Text after preprocessing: " + text);
        return text.replaceAll("[\\n\\r]+", " ").trim();
    }

    public static String postprocessText(String text, String key, String service) {
       // //System.out.println("Postprocessing text for key: " + key + " using service: " + service);
        ////System.out.println("Text before postprocessing: " + text);
        text = text.trim();

        // Restore special characters and common escape sequences
        text = text.replace("__NEWLINE__", "\n").replace("__TAB__", "\t").replace("__BACKSLASH__", "\\")
                   .replace("__SINGLEQUOTE__", "'").replace("__CURLYSINGLEQUOTE__", "’").replace("__LSINGLEQUOTE__", "‘")
                   .replace("__AMPERSAND__", "&").replace("__LESSTHAN__", "<").replace("__GREATERTHAN__", ">")
                   .replace("__LDOUBLEQUOTE__", "“").replace("__RDOUBLEQUOTE__", "”").replace("__PERCENT__", "%")
                   .replace("__EXCLAMATION__", "!").replace("__HASH__", "#").replace("__DOLLAR__", "$")
                   .replace("__LPAREN__", "(").replace("__RPAREN__", ")").replace("__ASTERISK__", "*")
                   .replace("__PLUS__", "+").replace("__COMMA__", ",")/*.replace("__MINUS__", "-")*/.replace("__DOT__", ".")
                   .replace("__SLASH__", "/").replace("__COLON__", ":").replace("__SEMICOLON__", ";")
                   .replace("__EQUAL__", "=").replace("__QUESTION__", "?").replace("__AT__", "@")
                   .replace("__LBRACKET__", "[").replace("__RBRACKET__", "]").replace("__CARET__", "^")
                   .replace("__BACKTICK__", "`").replace("__LBRACE__", "{").replace("__PIPE__", "|")
                   .replace("__RBRACE__", "}").replace("__TILDE__", "~").replace("__DOUBLEQUOTE__", "\"");

        // Manually handle specific issues for ChatGPT service
        if ("ChatGPT".equalsIgnoreCase(service)) {
            text = text.replace("\\\"", "\"").replace("\\“", "“").replace("\\”", "”").replace("\"", " ");
        }

        // Specific logic to remove outer quotes from translated JSON-like strings
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }

        // Normalize text to NFC form
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
       // text = text.replaceAll("__URL__(https?://\\S+)__URL__", "$1");

      //  //System.out.println("Text after postprocessing: " + text);
        text = text.replaceAll("\\s*\\d+\\s*:", "").trim(); // Remove unwanted numbering
        return text.replaceAll(" +", " ").trim();
    }

    public static Map<String, String> getUrlMap() {
        return urlMap;
    }

    public static void clearUrlMap() {
        urlMap.clear();
    }
}

