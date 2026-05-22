package org.carecode.mw.lims.mw.autolumo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Shared singleton that records live connection state for the diagnostics page.
 */
public class ConnectionStatus {

    public enum State { UNKNOWN, OK, ERROR }

    private static final int MAX_LOG = 50;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ConnectionStatus INSTANCE = new ConnectionStatus();

    private volatile boolean analyzerConnected = false;
    private volatile String  analyzerAddress   = "-";
    private volatile String  lastAnalyzerEvent = "-";

    private volatile State  limsState       = State.UNKNOWN;
    private volatile String lastLimsPull    = "-";
    private volatile String lastLimsPush    = "-";
    private volatile String limsUrl         = "-";

    private final Deque<String> eventLog = new ArrayDeque<>();

    private ConnectionStatus() {}

    public static ConnectionStatus get() { return INSTANCE; }

    // ---- analyzer ----

    public synchronized void analyzerConnected(String address) {
        analyzerConnected = true;
        analyzerAddress   = address;
        lastAnalyzerEvent = now();
        addLog("Analyzer connected from " + address);
    }

    public synchronized void analyzerDisconnected() {
        analyzerConnected = false;
        lastAnalyzerEvent = now();
        addLog("Analyzer disconnected from " + analyzerAddress);
    }

    public synchronized void analyzerMessage(String direction, String msg) {
        lastAnalyzerEvent = now();
        String truncated = msg.length() > 120 ? msg.substring(0, 120) + "…" : msg;
        addLog("[" + direction + "] " + truncated);
    }

    // ---- LIMS ----

    public synchronized void limsConfigured(String url) {
        limsUrl = url;
    }

    public synchronized void limsPullOk(String sampleId, int orderCount) {
        limsState   = State.OK;
        lastLimsPull = now() + " – sample=" + sampleId + " orders=" + orderCount;
        addLog("[PULL OK] sample=" + sampleId + " orders=" + orderCount);
    }

    public synchronized void limsPullFailed(String sampleId, String reason) {
        limsState   = State.ERROR;
        lastLimsPull = now() + " – sample=" + sampleId + " ERROR: " + reason;
        addLog("[PULL FAIL] sample=" + sampleId + " " + reason);
    }

    public synchronized void limsPushOk(String sampleId, int resultCount) {
        limsState    = State.OK;
        lastLimsPush = now() + " – sample=" + sampleId + " results=" + resultCount;
        addLog("[PUSH OK] sample=" + sampleId + " results=" + resultCount);
    }

    public synchronized void limsPushFailed(String sampleId, String reason) {
        limsState    = State.ERROR;
        lastLimsPush = now() + " – sample=" + sampleId + " ERROR: " + reason;
        addLog("[PUSH FAIL] sample=" + sampleId + " " + reason);
    }

    // ---- snapshot for rendering ----

    public synchronized Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.analyzerConnected = analyzerConnected;
        s.analyzerAddress   = analyzerAddress;
        s.lastAnalyzerEvent = lastAnalyzerEvent;
        s.limsState         = limsState;
        s.lastLimsPull      = lastLimsPull;
        s.lastLimsPush      = lastLimsPush;
        s.limsUrl           = limsUrl;
        s.eventLog          = new ArrayList<>(eventLog);
        s.timestamp         = now();
        return s;
    }

    // ---- internal ----

    private void addLog(String msg) {
        eventLog.addFirst(now() + "  " + msg);
        while (eventLog.size() > MAX_LOG) eventLog.removeLast();
    }

    private static String now() {
        return LocalDateTime.now().format(FMT);
    }

    public static class Snapshot {
        public boolean analyzerConnected;
        public String  analyzerAddress;
        public String  lastAnalyzerEvent;
        public State   limsState;
        public String  lastLimsPull;
        public String  lastLimsPush;
        public String  limsUrl;
        public List<String> eventLog;
        public String  timestamp;
    }
}
