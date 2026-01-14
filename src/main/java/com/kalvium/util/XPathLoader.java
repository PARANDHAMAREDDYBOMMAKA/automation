package com.kalvium.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XPathLoader {

    private static final Logger logger = LoggerFactory.getLogger(XPathLoader.class);
    private static final Properties xpaths = new Properties();
    private static boolean loaded = false;

    static {
        loadXPaths();
    }

    private static void loadXPaths() {
        if (loaded) {
            return;
        }

        try (InputStream input = XPathLoader.class.getClassLoader()
                .getResourceAsStream("xpaths.properties")) {

            if (input == null) {
                logger.error("Unable to find xpaths.properties file");
                throw new RuntimeException("xpaths.properties file not found in classpath");
            }

            xpaths.load(input);
            loaded = true;
            logger.info("Loaded {} XPath locators from xpaths.properties", xpaths.size());

        } catch (IOException ex) {
            logger.error("Error loading xpaths.properties", ex);
            throw new RuntimeException("Failed to load XPath properties", ex);
        }
    }

    /**
     * Get XPath by key
     * @param key The property key
     * @return The XPath string
     * @throws IllegalArgumentException if key not found
     */
    public static String get(String key) {
        String xpath = xpaths.getProperty(key);
        if (xpath == null || xpath.trim().isEmpty()) {
            throw new IllegalArgumentException("XPath not found for key: " + key);
        }
        return xpath.trim();
    }

    /**
     * Get XPath by key with default value
     * @param key The property key
     * @param defaultValue Default value if key not found
     * @return The XPath string or default value
     */
    public static String get(String key, String defaultValue) {
        String xpath = xpaths.getProperty(key, defaultValue);
        return xpath != null ? xpath.trim() : defaultValue;
    }

    /**
     * Check if XPath key exists
     * @param key The property key
     * @return true if key exists
     */
    public static boolean has(String key) {
        return xpaths.containsKey(key);
    }

    public static void reload() {
        loaded = false;
        xpaths.clear();
        loadXPaths();
    }
}
