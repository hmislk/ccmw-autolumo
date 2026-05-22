package org.carecode.mw.lims.mw.autolumo;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class LISCommunicator {

    private static final Logger logger = LogManager.getLogger(LISCommunicator.class);
    private static final Gson gson = new Gson();

    public static DataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
        logger.info("[PULL] Requesting test orders for sampleId={}", queryRecord.getSampleId());
        try {
            String baseUrl = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(baseUrl + "/test_orders_for_sample_requests");
            logger.info("[PULL] POST -> {}", url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            DataBundle requestBundle = new DataBundle();
            requestBundle.setMiddlewareSettings(SettingsLoader.getSettings());
            requestBundle.addQueryRecord(queryRecord);
            String requestJson = gson.toJson(requestBundle);
            logger.debug("[PULL] Request body: {}", requestJson);

            long t0 = System.currentTimeMillis();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestJson.getBytes("utf-8"));
            }

            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - t0;
            logger.info("[PULL] Response code={} elapsed={}ms", code, elapsed);

            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                logger.debug("[PULL] Response body: {}", response);
                DataBundle result = gson.fromJson(response.toString(), DataBundle.class);
                int orderCount = (result != null && result.getOrderRecords() != null)
                        ? result.getOrderRecords().size() : 0;
                if (orderCount > 0) {
                    logger.info("[PULL] Got {} order record(s)", orderCount);
                } else {
                    logger.warn("[PULL] Response parsed but orderRecords is null/empty");
                }
                ConnectionStatus.get().limsPullOk(queryRecord.getSampleId(), orderCount);
                return result;
            } else {
                logErrorBody(conn, code, elapsed, "[PULL]");
                ConnectionStatus.get().limsPullFailed(queryRecord.getSampleId(), "HTTP " + code);
            }
        } catch (java.net.SocketTimeoutException e) {
            logger.error("[PULL] Timeout waiting for LIMS: {}", e.getMessage());
            ConnectionStatus.get().limsPullFailed(queryRecord.getSampleId(), "timeout");
        } catch (Exception e) {
            logger.error("[PULL] Exception: {}", e.getMessage(), e);
            ConnectionStatus.get().limsPullFailed(queryRecord.getSampleId(), e.getMessage());
        }
        return null;
    }

    public static void pushResults(DataBundle db) {
        logger.info("[PUSH] Pushing {} result(s) to LIMS", db.getResultsRecords().size());
        for (ResultsRecord rr : db.getResultsRecords()) {
            logger.info("[PUSH]  sampleId={} testCode={} value={}",
                    rr.getSampleId(), rr.getTestCode(), rr.getResultValueString());
        }
        try {
            String endpoint = SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() + "/test_results";
            URL url = new URL(endpoint);
            logger.info("[PUSH] POST -> {}", url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            db.setMiddlewareSettings(SettingsLoader.getSettings());
            String requestJson = gson.toJson(db);
            logger.debug("[PUSH] Request body: {}", requestJson);

            long t0 = System.currentTimeMillis();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestJson.getBytes("utf-8"));
            }

            int code = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - t0;
            logger.info("[PUSH] Response code={} elapsed={}ms", code, elapsed);

            String sampleId = db.getQueryRecords() != null && !db.getQueryRecords().isEmpty()
                    ? db.getQueryRecords().get(0).getSampleId() : "?";
            if (code == HttpURLConnection.HTTP_OK) {
                ConnectionStatus.get().limsPushOk(sampleId, db.getResultsRecords().size());
            } else {
                logErrorBody(conn, code, elapsed, "[PUSH]");
                ConnectionStatus.get().limsPushFailed(sampleId, "HTTP " + code);
            }
        } catch (java.net.SocketTimeoutException e) {
            logger.error("[PUSH] Timeout waiting for LIMS: {}", e.getMessage());
            String sampleId = db.getQueryRecords() != null && !db.getQueryRecords().isEmpty()
                    ? db.getQueryRecords().get(0).getSampleId() : "?";
            ConnectionStatus.get().limsPushFailed(sampleId, "timeout");
        } catch (Exception e) {
            logger.error("[PUSH] Exception: {}", e.getMessage(), e);
            String sampleId = db.getQueryRecords() != null && !db.getQueryRecords().isEmpty()
                    ? db.getQueryRecords().get(0).getSampleId() : "?";
            ConnectionStatus.get().limsPushFailed(sampleId, e.getMessage());
        }
    }

    private static void logErrorBody(HttpURLConnection conn, int code, long elapsed, String tag) {
        try {
            BufferedReader err = new BufferedReader(new InputStreamReader(
                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), "utf-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = err.readLine()) != null) body.append(line);
            err.close();
            logger.error("{} FAILED code={} elapsed={}ms body=[{}]", tag, code, elapsed, body);
        } catch (Exception ignored) {
            logger.error("{} FAILED code={} elapsed={}ms (could not read error body)", tag, code, elapsed);
        }
    }
}
