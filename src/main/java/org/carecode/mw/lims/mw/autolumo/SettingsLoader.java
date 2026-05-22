package org.carecode.mw.lims.mw.autolumo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.MiddlewareSettings;

public class SettingsLoader {

    private static final Logger logger = LogManager.getLogger(SettingsLoader.class);
    private static MiddlewareSettings middlewareSettings;
    private static boolean diagnosticsEnabled = false;
    private static int     diagnosticsPort    = 8765;

    private static final String CONFIG_PATH = "D:\\ccmw\\settings\\autolumo\\config.json";

    public static void loadSettings() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(CONFIG_PATH)) {
            // Parse once into a JsonObject so we can read both the
            // MiddlewareSettings sub-tree and the diagnostics keys.
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            middlewareSettings = gson.fromJson(root, MiddlewareSettings.class);

            if (root.has("diagnosticsEnabled")) {
                diagnosticsEnabled = root.get("diagnosticsEnabled").getAsBoolean();
            }
            if (root.has("diagnosticsPort")) {
                diagnosticsPort = root.get("diagnosticsPort").getAsInt();
            }

            if (middlewareSettings.getAnalyzerDetails() != null) {
                logger.info("Analyzer: {} port={}",
                        middlewareSettings.getAnalyzerDetails().getAnalyzerName(),
                        middlewareSettings.getAnalyzerDetails().getAnalyzerPort());
            }
            if (middlewareSettings.getLimsSettings() != null) {
                String url = middlewareSettings.getLimsSettings().getLimsServerBaseUrl();
                logger.info("LIMS URL: {}", url);
                ConnectionStatus.get().limsConfigured(url);
            }
            logger.info("Diagnostics panel: {} (port {})",
                    diagnosticsEnabled ? "ENABLED" : "disabled", diagnosticsPort);

        } catch (IOException e) {
            logger.error("Failed to load settings from {}", CONFIG_PATH, e);
            throw new RuntimeException("Cannot load settings", e);
        }
    }

    public static MiddlewareSettings getSettings() {
        if (middlewareSettings == null) loadSettings();
        return middlewareSettings;
    }

    public static boolean isDiagnosticsEnabled() { return diagnosticsEnabled; }
    public static int     getDiagnosticsPort()   { return diagnosticsPort;    }
}
