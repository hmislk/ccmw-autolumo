package org.carecode.mw.lims.mw.autolumo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

/**
 * TCP server that accepts connections from the AutoLumo analyzer (analyzer acts as client).
 *
 * Implements the Autobio proprietary protocol:
 *   Each message is framed as  {CommandWord,Param1,Param2,...}
 *   Field types:
 *     Integer      → plain digits
 *     Long integer → digits + L suffix  (e.g. 12345L)
 *     Float        → digits + F suffix  (e.g. 45.670F)
 *     String       → [S] prefix, internal commas replaced with semicolons by sender
 */
public class AutoLumoServer {

    private static final Logger logger = LogManager.getLogger(AutoLumoServer.class);
    private ServerSocket serverSocket;

    public void start(int port) {
        try {
            serverSocket = new ServerSocket(port);
            logger.info("AutoLumo middleware listening on port {}", port);
            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    Thread t = new Thread(() -> handleClient(client));
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    logger.error("Error accepting connection", e);
                }
            }
        } catch (IOException e) {
            logger.error("Could not start server on port {}", port, e);
        } finally {
            stop();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
    }

    // ---- per-connection handler ----

    private void handleClient(Socket client) {
        String address = client.getInetAddress().getHostAddress();
        ConnectionStatus.get().analyzerConnected(address);
        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            StringBuilder buf = new StringBuilder();
            boolean inside = false;
            int b;

            while ((b = in.read()) != -1) {
                char c = (char) b;

                if (c == '{') {
                    inside = true;
                    buf.setLength(0);
                    buf.append(c);
                } else if (c == '}' && inside) {
                    buf.append(c);
                    String raw = buf.toString();
                    logger.info("[RX] {}", raw);
                    ConnectionStatus.get().analyzerMessage("RX", raw);
                    String response = dispatch(raw);
                    if (response != null) {
                        logger.info("[TX] {}", response);
                        ConnectionStatus.get().analyzerMessage("TX", response);
                        out.write(response.getBytes("UTF-8"));
                        out.flush();
                    }
                    inside = false;
                    buf.setLength(0);
                } else if (inside) {
                    buf.append(c);
                }
            }
        } catch (IOException e) {
            logger.error("Connection error", e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
            logger.info("Analyzer disconnected");
            ConnectionStatus.get().analyzerDisconnected();
        }
    }

    // ---- command dispatcher ----

    private String dispatch(String raw) {
        String body = raw.substring(1, raw.length() - 1);
        String[] f = body.split(",", -1);
        if (f.length == 0) return null;

        int cmd;
        try {
            cmd = Integer.parseInt(f[0].trim());
        } catch (NumberFormatException e) {
            logger.warn("Unreadable command word in: {}", raw);
            return null;
        }

        switch (cmd) {
            case 1:  return onCmd1QueryTestRequest(f);
            case 5:  return onCmd5ResultByTest(f);
            case 7:  return onCmd7ResultBySample(f);
            case 9:  return onCmd9QuickQueryTestRequest(f);
            case 11: return onCmd11VersionRequest(f);
            case 19: return null;  // sample info notification – no response needed
            default:
                logger.warn("Unhandled command {}", cmd);
                return null;
        }
    }

    // ---- command handlers ----

    /**
     * Cmd 1: analyzer requests test order for a sample.
     * Fields: [0]=1  [1]=sysId  [2]=sampleNo
     */
    private String onCmd1QueryTestRequest(String[] f) {
        if (f.length < 3) { logger.warn("Cmd1: too few fields"); return null; }
        String sampleNo = parseStr(f[2]);
        logger.info("[CMD1] Query test order sampleNo={}", sampleNo);

        DataBundle db = fetchOrders(sampleNo);
        if (db == null || db.getOrderRecords().isEmpty()) {
            return "{2, 1, [S]" + sampleNo + "}";
        }
        return AnalyzerCommunicator.buildCmd2Response(sampleNo,
                db.getPatientRecord(), db.getOrderRecords().get(0));
    }

    /**
     * Cmd 9: analyzer quick-requests test order (adds rack position fields).
     * Fields: [0]=9  [1]=sysId  [2]=sampleNo  [3]=sampleNo2  [4]=position
     */
    private String onCmd9QuickQueryTestRequest(String[] f) {
        if (f.length < 3) { logger.warn("Cmd9: too few fields"); return null; }
        String sampleNo = parseStr(f[2]);
        logger.info("[CMD9] Quick query test order sampleNo={}", sampleNo);

        DataBundle db = fetchOrders(sampleNo);
        if (db == null || db.getOrderRecords().isEmpty()) {
            return "{10, 1, [S]" + sampleNo + "}";
        }
        return AnalyzerCommunicator.buildCmd10Response(sampleNo,
                db.getPatientRecord(), db.getOrderRecords().get(0));
    }

    /**
     * Cmd 5: analyzer pushes a single test result.
     * Fields: [0]=5  [1]=sysId  [2]=sampleNo  [3]=itemNo  [4]=testReqId
     *         [5]=rluL  [6]=concF_or_[S]  [7+]=configurable optional fields
     * Optional field order (when enabled in analyzer config):
     *   [7]=completionTime [8]=patientNo [9]=flag [10]=kitName
     *   [11]=reagentItemNo [12]=lotNo [13]=interferonResult
     *   [14]=kitValidity [15]=rackNo [16]=position(int)
     */
    private String onCmd5ResultByTest(String[] f) {
        if (f.length < 7) { logger.warn("Cmd5: too few fields"); return null; }

        String sampleNo  = parseStr(f[2]);
        String itemNo    = parseStr(f[3]);
        int    testReqId = parseInt(f[4]);
        long   rlu       = parseLong(f[5]);
        String conc      = stripSuffix(f[6]);

        logger.info("[CMD5] Result sampleNo={} itemNo={} rlu={} conc={}", sampleNo, itemNo, rlu, conc);

        // Optional configurable fields – capture what is present
        String completionTime = f.length > 7  ? parseStr(f[7])  : "";
        String patientNo      = f.length > 8  ? parseStr(f[8])  : "";
        String flag           = f.length > 9  ? parseStr(f[9])  : "";
        String kitName        = f.length > 10 ? parseStr(f[10]) : "";

        ResultsRecord rr = new ResultsRecord(testReqId, itemNo, conc,
                0, 0, flag, "", "", completionTime, "AutoLumo", sampleNo);

        DataBundle db = new DataBundle();
        db.setMiddlewareSettings(SettingsLoader.getSettings());
        db.addResultsRecord(rr);
        if (!patientNo.isEmpty()) {
            db.addQueryRecord(new QueryRecord(0, sampleNo, patientNo, ""));
        }
        if (db.getPatientRecord() == null && !patientNo.isEmpty()) {
            PatientRecord pr = new PatientRecord(0, patientNo, "", "", "", "", "", "", "", "", "");
            db.setPatientRecord(pr);
        }

        LISCommunicator.pushResults(db);

        return AnalyzerCommunicator.buildCmd6Ack(0, testReqId, sampleNo, patientNo, itemNo);
    }

    /**
     * Cmd 7: analyzer pushes all results for a sample at once.
     * Fields: [0]=7  [1]=sysId  [2]=sampleId(int)  [3]=sampleNo  [4]=testCount
     *         then per test (3 fields each): itemCode, rluL, concF
     *         trailing optional: rackNo, position
     */
    private String onCmd7ResultBySample(String[] f) {
        if (f.length < 5) { logger.warn("Cmd7: too few fields"); return null; }

        int    sampleId  = parseInt(f[2]);
        String sampleNo  = parseStr(f[3]);
        int    testCount = parseInt(f[4]);

        logger.info("[CMD7] Results by sample sampleNo={} sampleId={} testCount={}", sampleNo, sampleId, testCount);

        DataBundle db = new DataBundle();
        db.setMiddlewareSettings(SettingsLoader.getSettings());

        for (int i = 0; i < testCount; i++) {
            int base = 5 + 3 * i;
            if (base + 2 >= f.length) break;
            String itemCode = parseStr(f[base]);
            long   rlu      = parseLong(f[base + 1]);
            String conc     = stripSuffix(f[base + 2]);
            logger.info("[CMD7]   test={} itemCode={} rlu={} conc={}", i + 1, itemCode, rlu, conc);
            db.addResultsRecord(new ResultsRecord(i + 1, itemCode, conc,
                    "", "", "AutoLumo", sampleNo));
        }

        LISCommunicator.pushResults(db);

        return AnalyzerCommunicator.buildCmd8Ack(0, sampleId, sampleNo, "");
    }

    /**
     * Cmd 11: analyzer requests the LIS version string.
     */
    private String onCmd11VersionRequest(String[] f) {
        logger.info("[CMD11] Version request");
        return AnalyzerCommunicator.buildCmd12VersionResponse("1.0");
    }

    // ---- LIMS query helper ----

    private DataBundle fetchOrders(String sampleNo) {
        QueryRecord qr = new QueryRecord(0, sampleNo, null, null);
        return LISCommunicator.pullTestOrdersForSampleRequests(qr);
    }

    // ---- field-type parsers ----

    /** Strip [S] prefix from a string field. */
    private String parseStr(String f) {
        if (f == null) return "";
        f = f.trim();
        return f.startsWith("[S]") ? f.substring(3) : f;
    }

    /** Parse a plain integer field. */
    private int parseInt(String f) {
        if (f == null) return 0;
        try { return Integer.parseInt(f.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Parse a long integer field (optional trailing L). */
    private long parseLong(String f) {
        if (f == null) return 0L;
        f = f.trim();
        if (f.endsWith("L") || f.endsWith("l")) f = f.substring(0, f.length() - 1);
        try { return Long.parseLong(f); }
        catch (NumberFormatException e) { return 0L; }
    }

    /**
     * Strip type-suffix from any field:
     *   [S]text  → text
     *   12.3F    → 12.3
     *   12345L   → 12345
     *   plain    → as-is
     */
    private String stripSuffix(String f) {
        if (f == null) return "";
        f = f.trim();
        if (f.startsWith("[S]")) return f.substring(3);
        if (f.endsWith("F") || f.endsWith("f")) return f.substring(0, f.length() - 1);
        if (f.endsWith("L") || f.endsWith("l")) return f.substring(0, f.length() - 1);
        return f;
    }
}
