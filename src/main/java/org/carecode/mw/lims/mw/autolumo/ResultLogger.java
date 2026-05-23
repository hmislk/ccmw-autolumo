package org.carecode.mw.lims.mw.autolumo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResultLogger {

    private static final Logger logger = LogManager.getLogger(ResultLogger.class);

    private static final String LOG_DIR = "middlewere_logs/autolumo_log/result_log";
    private static final DateTimeFormatter DATE_TITLE   = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Inner width of each column (chars between the | pipes = dashes in separator row).
    // Format per cell: " " + content left-padded to (innerWidth - 1) chars.
    private static final int W_RESULT_AT      = 23;
    private static final int W_SAMPLE_ID      = 18;
    private static final int W_TEST_CODE      = 12;
    private static final int W_RESULT         = 13;
    private static final int W_SAMPLE_SEND_AT = 23;
    private static final int W_STATUS         = 17;

    private static final String SEP;
    private static final String HDR_ROW;

    static {
        SEP = "+"
            + repeat('-', W_RESULT_AT)      + "+"
            + repeat('-', W_SAMPLE_ID)      + "+"
            + repeat('-', W_TEST_CODE)      + "+"
            + repeat('-', W_RESULT)         + "+"
            + repeat('-', W_SAMPLE_SEND_AT) + "+";

        HDR_ROW = "|"
            + cell("Result_At",        W_RESULT_AT)      + "|"
            + cell("Sample ID",        W_SAMPLE_ID)      + "|"
            + cell("Test Code",        W_TEST_CODE)      + "|"
            + cell("Result",           W_RESULT)         + "|"
            + cell("Sample Send At",   W_SAMPLE_SEND_AT) + "|";
    }

    /**
     * Appends one result row to today's log file.
     *
     * @param sampleSendAt  completion timestamp from the analyzer (may be empty — falls back to now)
     * @param sampleId      sample number
     * @param testCode      test / item code
     * @param result        numeric result string
     * @param unit          unit string (empty when not available from the protocol)
     */
    public static void log(String sampleSendAt, String sampleId,
                           String testCode, String result, boolean pushSuccess) {
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String today   = LocalDate.now().format(DATE_TITLE);
            File   logFile = new File(dir, "autolumo_" + today + ".txt");
            boolean isNew  = !logFile.exists();

            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                if (isNew) {
                    writeFileHeader(pw);
                }

                String resultAt    = (sampleSendAt != null && !sampleSendAt.isEmpty())
                        ? sampleSendAt
                        : LocalDateTime.now().format(DATETIME_FMT);
                String sampleSendTime = LocalDateTime.now().format(DATETIME_FMT);

                String dataRow = "|"
                    + cell(resultAt,               W_RESULT_AT)      + "|"
                    + cell(nvl(sampleId),          W_SAMPLE_ID)      + "|"
                    + cell(nvl(testCode),          W_TEST_CODE)      + "|"
                    + cell(nvl(result),            W_RESULT)         + "|"
                    + cell(sampleSendTime,         W_SAMPLE_SEND_AT) + "|";

                pw.println(dataRow);
                pw.println(SEP);
            }
        } catch (IOException e) {
            logger.error("Failed to write result log: {}", e.getMessage());
        }
    }

    private static void writeFileHeader(PrintWriter pw) {
        // Full-width title box spanning the same width as the data table
        int    innerW      = SEP.length() - 2;   // chars between the two | pipes
        String titleBorder = "+" + repeat('-', innerW) + "+";
        String title       = "[Result - " + LocalDate.now().format(DATE_TITLE) + "]";
        String titleRow    = "|" + center(title, innerW) + "|";

        pw.println(titleBorder);
        pw.println(titleRow);
        pw.println(titleBorder);
        pw.println(SEP);
        pw.println(HDR_ROW);
        pw.println(SEP);
    }

    /** Centers {@code s} within a field of {@code width} chars using space padding. */
    private static String center(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int left  = (width - s.length()) / 2;
        int right = width - s.length() - left;
        return repeat(' ', left) + s + repeat(' ', right);
    }

    /** Returns a padded cell body (leading space + content left-aligned to innerWidth - 1). */
    private static String cell(String content, int innerWidth) {
        if (content == null) content = "";
        int contentWidth = innerWidth - 1;
        if (content.length() > contentWidth) {
            content = content.substring(0, contentWidth);
        }
        return String.format(" %-" + contentWidth + "s", content);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
