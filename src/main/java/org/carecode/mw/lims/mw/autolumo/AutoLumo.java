package org.carecode.mw.lims.mw.autolumo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class AutoLumo {

    public static final Logger logger = LogManager.getLogger(AutoLumo.class);

    // Flip either flag to true for quick smoke-testing without a real analyzer
    static boolean testingPullingTestOrders  = false;
    static boolean testingPushingTestResults = false;

    public static void main(String[] args) {
        if (testingPullingTestOrders) {
            SettingsLoader.loadSettings();
            QueryRecord qr = new QueryRecord(0, "S0001", null, null);
            DataBundle db = LISCommunicator.pullTestOrdersForSampleRequests(qr);
            logger.info("Pull result: {}", db);
            System.exit(0);
        }

        if (testingPushingTestResults) {
            SettingsLoader.loadSettings();
            DataBundle db = new DataBundle();
            PatientRecord pr = new PatientRecord(0, "P001", "P001", "Test", "Patient",
                    "Male", "", "19800101", "", "", "Doctor");
            db.setPatientRecord(pr);
            db.addResultsRecord(new ResultsRecord(1, "ANTI-HCV", "45.67",
                    0, 0, "N", "", "", "202408172147", "AutoLumo", "S0001"));
            db.addQueryRecord(new QueryRecord(0, "S0001", "P001", ""));
            LISCommunicator.pushResults(db);
            System.exit(0);
        }

        logger.info("Starting AutoLumo middleware...");
        try {
            SettingsLoader.loadSettings();
        } catch (Exception e) {
            logger.error("Failed to load settings – cannot start.", e);
            return;
        }

        if (SettingsLoader.isDiagnosticsEnabled()) {
            new DiagnosticsServer().start(SettingsLoader.getDiagnosticsPort());
        }

        int port = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();
        new AutoLumoServer().start(port);
    }
}
