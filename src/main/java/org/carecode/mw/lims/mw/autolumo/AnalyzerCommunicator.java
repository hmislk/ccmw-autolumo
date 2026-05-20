package org.carecode.mw.lims.mw.autolumo;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.OrderRecord;
import org.carecode.lims.libraries.PatientRecord;

/**
 * Builds Autobio proprietary protocol response strings sent back to the analyzer.
 *
 * Message format: {CommandWord,Param1,Param2,...}
 *   Integer:       plain digits, e.g. 0
 *   Long integer:  digits followed by L, e.g. 12345L
 *   Float:         digits with decimal followed by F, e.g. 45.67000F
 *   String:        [S] prefix, commas replaced with semicolons, e.g. [S]Sample001
 */
public class AnalyzerCommunicator {

    private static final Logger logger = LogManager.getLogger(AnalyzerCommunicator.class);

    /**
     * Cmd 2 – respond to Cmd 1 (test order query by sample No).
     * Sent when the analyzer asks for pending test orders for a given sample.
     */
    public static String buildCmd2Response(String sampleNo, PatientRecord patient, OrderRecord order) {
        if (order == null) {
            logger.warn("[CMD2] No order found for sampleNo={}, responding with error", sampleNo);
            return "{2,1,[S]" + escape(sampleNo) + "}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{2,0,");
        sb.append("[S]").append(escape(sampleNo)).append(",");
        sb.append("1,");   // dilution factor – default 1x
        sb.append("0,");   // ordinary sample (not emergency)

        if (patient != null) {
            sb.append("[S]").append(escape(nvl(patient.getPatientId()))).append(",");
            sb.append("[S]").append(escape(nvl(patient.getPatientName()))).append(",");
            sb.append("[S]").append(escape(nvl(patient.getPatientSecondName()))).append(",");
            sb.append(genderCode(patient.getPatientSex())).append(",");
            sb.append("0,");   // age – not in PatientRecord
            sb.append("[S],"); // department
            sb.append("[S],"); // inpatient area
            sb.append("[S],"); // bed no
            sb.append("[S]").append(escape(nvl(patient.getAttendingDoctor()))).append(",");
            sb.append("[S],"); // submitting date
            sb.append("[S],"); // inspector
            sb.append("[S],"); // notes
        } else {
            sb.append("[S],[S],[S],0,0,[S],[S],[S],[S],[S],[S],[S],");
        }

        appendTestItems(sb, order.getTestNames());
        sb.append("}");
        logger.debug("[CMD2] response={}", sb);
        return sb.toString();
    }

    /**
     * Cmd 10 – respond to Cmd 9 (quick test order query by sample No).
     * Carries sample type and patient No in addition to test items.
     */
    public static String buildCmd10Response(String sampleNo, PatientRecord patient, OrderRecord order) {
        if (order == null) {
            logger.warn("[CMD10] No order found for sampleNo={}, responding with error", sampleNo);
            return "{10,1,[S]" + escape(sampleNo) + "}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{10,0,");
        sb.append("[S]").append(escape(sampleNo)).append(",");
        sb.append("1,");  // dilution factor
        sb.append("0,");  // ordinary sample
        sb.append("0,");  // sample type: 0=Serum (default)

        if (patient != null) {
            sb.append("[S]").append(escape(nvl(patient.getPatientId()))).append(",");
        } else {
            sb.append("[S],");
        }

        appendTestItems(sb, order.getTestNames());
        sb.append("}");
        logger.debug("[CMD10] response={}", sb);
        return sb.toString();
    }

    /**
     * Cmd 6 – acknowledge a result sent by test (Cmd 5).
     */
    public static String buildCmd6Ack(int errorCode, int testReqId, String sampleNo, String patientNo, String itemNo) {
        return "{6," + errorCode + "," + testReqId
                + ",[S]" + escape(sampleNo)
                + ",[S]" + escape(patientNo)
                + ",[S]" + escape(itemNo) + "}";
    }

    /**
     * Cmd 8 – acknowledge a result sent by sample (Cmd 7).
     */
    public static String buildCmd8Ack(int errorCode, int sampleId, String sampleNo, String patientNo) {
        return "{8," + errorCode + "," + sampleId
                + ",[S]" + escape(sampleNo)
                + ",[S]" + escape(patientNo) + "}";
    }

    /**
     * Cmd 12 – respond to Cmd 11 (version request).
     */
    public static String buildCmd12VersionResponse(String version) {
        return "{12,0,[S]" + escape(version) + "}";
    }

    // ---- helpers ----

    private static void appendTestItems(StringBuilder sb, List<String> testNames) {
        sb.append(testNames.size());
        for (String testName : testNames) {
            sb.append(",[S]").append(escape(testName));
            sb.append(",1");  // default dilution ratio = 1
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace(",", ";");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static int genderCode(String sex) {
        return "male".equalsIgnoreCase(sex) ? 1 : 0;
    }
}
