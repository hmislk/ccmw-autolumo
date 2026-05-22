package org.carecode.mw.lims.mw.autolumo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.MiddlewareSettings;

/**
 * Optional HTTP diagnostics panel served on a local port.
 *
 * Enable in config.json:
 *   "diagnosticsEnabled": true,
 *   "diagnosticsPort": 8765
 *
 * Open http://localhost:8765/ in a browser to see live connection status.
 * Disable this server once the analyzer/LIS connection is confirmed working.
 */
public class DiagnosticsServer {

    private static final Logger logger = LogManager.getLogger(DiagnosticsServer.class);
    private HttpServer server;

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/", new StatusHandler());
            server.setExecutor(null);
            server.start();
            logger.info("Diagnostics panel running at http://localhost:{}/  (disable once connection is confirmed)", port);
        } catch (IOException e) {
            logger.error("Could not start diagnostics server on port {}: {}", port, e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private static class StatusHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            ConnectionStatus.Snapshot s = ConnectionStatus.get().snapshot();
            String body = buildHtml(s);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String buildHtml(ConnectionStatus.Snapshot s) {
            String analyzerColor = s.analyzerConnected ? "#2ecc71" : "#e74c3c";
            String analyzerLabel = s.analyzerConnected ? "CONNECTED" : "DISCONNECTED";

            String limsColor;
            String limsLabel;
            switch (s.limsState) {
                case OK:      limsColor = "#2ecc71"; limsLabel = "OK";      break;
                case ERROR:   limsColor = "#e74c3c"; limsLabel = "ERROR";   break;
                default:      limsColor = "#f39c12"; limsLabel = "UNKNOWN"; break;
            }

            StringBuilder log = new StringBuilder();
            for (String line : s.eventLog) {
                log.append("<tr><td>").append(escHtml(line)).append("</td></tr>\n");
            }

            MiddlewareSettings cfg = null;
            try { cfg = SettingsLoader.getSettings(); } catch (Exception ignored) {}

            String analyzerPort = cfg != null ? String.valueOf(cfg.getAnalyzerDetails().getAnalyzerPort()) : "?";
            String limsUrl      = cfg != null ? cfg.getLimsSettings().getLimsServerBaseUrl() : "?";

            return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta http-equiv=\"refresh\" content=\"5\">\n"
                + "  <title>AutoLumo Diagnostics</title>\n"
                + "  <style>\n"
                + "    body { font-family: monospace; background: #1a1a2e; color: #eee; margin: 0; padding: 20px; }\n"
                + "    h1 { color: #a29bfe; margin-bottom: 4px; }\n"
                + "    .subtitle { color: #888; font-size: 12px; margin-bottom: 24px; }\n"
                + "    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }\n"
                + "    .card { background: #16213e; border-radius: 8px; padding: 16px; }\n"
                + "    .card h2 { margin: 0 0 12px; font-size: 14px; color: #a29bfe; }\n"
                + "    .badge { display: inline-block; padding: 4px 12px; border-radius: 20px;\n"
                + "             font-weight: bold; font-size: 13px; color: #fff; margin-bottom: 10px; }\n"
                + "    .row { font-size: 12px; margin: 4px 0; }\n"
                + "    .label { color: #888; }\n"
                + "    .val   { color: #dfe6e9; }\n"
                + "    table { width: 100%; border-collapse: collapse; font-size: 12px; }\n"
                + "    td { padding: 3px 6px; border-bottom: 1px solid #2d3561; word-break: break-all; }\n"
                + "    tr:hover td { background: #2d3561; }\n"
                + "    .warn { color: #f39c12; font-size: 11px; margin-top: 16px; text-align: center; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h1>AutoLumo Middleware &mdash; Diagnostics</h1>\n"
                + "  <div class=\"subtitle\">Auto-refreshes every 5 s &nbsp;|&nbsp; Page generated: " + escHtml(s.timestamp) + "</div>\n"
                + "  <div class=\"grid\">\n"
                + "    <div class=\"card\">\n"
                + "      <h2>Analyzer Connection</h2>\n"
                + "      <div class=\"badge\" style=\"background:" + analyzerColor + "\">" + analyzerLabel + "</div>\n"
                + "      <div class=\"row\"><span class=\"label\">Listening port: </span><span class=\"val\">" + escHtml(analyzerPort) + "</span></div>\n"
                + "      <div class=\"row\"><span class=\"label\">Remote address: </span><span class=\"val\">" + escHtml(s.analyzerAddress) + "</span></div>\n"
                + "      <div class=\"row\"><span class=\"label\">Last event:     </span><span class=\"val\">" + escHtml(s.lastAnalyzerEvent) + "</span></div>\n"
                + "    </div>\n"
                + "    <div class=\"card\">\n"
                + "      <h2>LIMS Connection</h2>\n"
                + "      <div class=\"badge\" style=\"background:" + limsColor + "\">" + limsLabel + "</div>\n"
                + "      <div class=\"row\"><span class=\"label\">URL:       </span><span class=\"val\">" + escHtml(limsUrl) + "</span></div>\n"
                + "      <div class=\"row\"><span class=\"label\">Last pull: </span><span class=\"val\">" + escHtml(s.lastLimsPull) + "</span></div>\n"
                + "      <div class=\"row\"><span class=\"label\">Last push: </span><span class=\"val\">" + escHtml(s.lastLimsPush) + "</span></div>\n"
                + "    </div>\n"
                + "  </div>\n"
                + "  <div class=\"card\">\n"
                + "    <h2>Recent Events (newest first)</h2>\n"
                + "    <table><tbody>\n"
                + log
                + "    </tbody></table>\n"
                + "  </div>\n"
                + "  <p class=\"warn\">&#9888; Diagnostics panel is ENABLED. Disable it in config.json once the connection is confirmed.</p>\n"
                + "</body>\n"
                + "</html>\n";
        }

        private String escHtml(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}
