package org.carecode.mw.lims.mw.autolumo;

import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.MiddlewareSettings;

public class SettingsLoader {

    private static final Logger logger = LogManager.getLogger(SettingsLoader.class);
    private static MiddlewareSettings middlewareSettings;
    private static final String CONFIG_PATH = "D:\\ccmw\\settings\\autolumo\\config.json";

    public static void loadSettings() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(CONFIG_PATH)) {
            middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
            if (middlewareSettings.getAnalyzerDetails() != null) {
                logger.info("Analyzer: {}", middlewareSettings.getAnalyzerDetails().getAnalyzerName());
            }
            if (middlewareSettings.getLimsSettings() != null) {
                logger.info("LIMS URL: {}", middlewareSettings.getLimsSettings().getLimsServerBaseUrl());
            }
        } catch (IOException e) {
            logger.error("Failed to load settings from {}", CONFIG_PATH, e);
            throw new RuntimeException("Cannot load settings", e);
        }
    }

    public static MiddlewareSettings getSettings() {
        if (middlewareSettings == null) {
            loadSettings();
        }
        return middlewareSettings;
    }
}
