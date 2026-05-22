# AutoLumo Analyzer — LIS Connection Settings Guide

This document explains every parameter in the analyzer's **LIS connection settings** screen, what each one means in the context of the AutoLumo middleware, what value to set, and why.

---

## Architecture overview

```
┌──────────────┐   TCP (Autobio protocol)   ┌────────────────────┐   HTTP/JSON   ┌──────┐
│  AutoLumo    │ ─────────────────────────► │  AutoLumo          │ ────────────► │ LIMS │
│  Analyzer    │   analyzer = TCP client    │  Middleware (Java) │               │      │
└──────────────┘                            └────────────────────┘               └──────┘
```

- **The analyzer acts as a TCP client.** It connects *outward* to the middleware.
- **The middleware acts as a TCP server.** It listens for incoming connections from the analyzer.
- The middleware then talks to the LIMS over HTTP.
- "Network port address" is the **IP of the PC running the middleware**, not the analyzer itself.
- "Network port" is the **port the middleware is listening on** (configured in `config.json` → `analyzerPort`).

---

## Settings reference

### Section 1 — Interface & serial port (legacy / unused when TCP is active)

| Parameter | Default shown | Meaning | Recommended setting |
|-----------|--------------|---------|---------------------|
| **LIS interface switch** | ✓ (enabled) | Master on/off toggle for the entire LIS interface. Must be ON for any communication to work. | **ON (✓)** |
| **Send results** | ✓ (enabled) | Whether the analyzer sends test results to the LIS/middleware. Must be ON. | **ON (✓)** |
| **LIS result sending mode** | By sample | Controls whether results are sent test-by-test (triggers Cmd 5) or all at once per sample (triggers Cmd 7). "By sample" means the middleware receives one batch per sample via Cmd 7. "By test" sends each result individually via Cmd 5. Both are handled by the middleware. | **By sample** (simpler; choose "By test" only if your LIMS needs results as they finish) |
| **COM port settings** | COM1 | Serial port used for RS-232 mode. **Not used when TCP/IP mode is active.** | Leave as COM1 (irrelevant in TCP mode) |
| **Parity check settings** | None | RS-232 parity. Not used in TCP mode. | None |
| **Baud rate** | 9600 | RS-232 baud rate. Not used in TCP mode. | 9600 (irrelevant) |
| **Flow control** | None | RS-232 flow control. Not used in TCP mode. | None |
| **Data bit** | 8 | RS-232 data bits. Not used in TCP mode. | 8 |
| **Stop bit** | 1.5 | RS-232 stop bits. Not used in TCP mode. | 1.5 |
| **Number of LIS communication timeouts allowed** | 5 (min 1, max 5) | How many consecutive TCP timeouts the analyzer tolerates before showing an error. | **3** — gives the middleware a couple of retries before alarming without waiting too long |
| **LIS gripper protocol switch** | × (disabled) | Enables a gripper/robot automation sub-protocol. Not needed for standard middleware use. | **OFF (×)** |
| **Send test results by string** | × (disabled) | If ON, the concentration field in Cmd 5/7 is sent as a plain string instead of a float with `F` suffix. The middleware's `stripSuffix()` handles both, but OFF (standard float format) is safer. | **OFF (×)** |

---

### Section 2 — Protocol & network

| Parameter | Default shown | Meaning | Recommended setting |
|-----------|--------------|---------|---------------------|
| **Medical record number sending switch** | × (disabled) | If ON, the analyzer includes the patient medical record number in result messages (Cmd 5 field [8]). The middleware can capture this, but the LIMS must be able to accept it. | **OFF (×)** unless your LIMS needs it |
| **Completion time sending switch** | × (disabled) | If ON, the analyzer includes a timestamp of when the test completed in Cmd 5 field [7]. Useful for audit trails. The middleware stores it in `ResultsRecord.completionTime`. | **ON (✓)** recommended — adds useful traceability |
| **LIS testing and local testing priority** | 0 (min 0, max 2) | Determines whether LIS-provided test orders (0 = LIS priority) or locally entered orders take precedence. 0 = always ask LIS first; 2 = local only. | **0** — middleware must be queried for orders |
| **Whether to display LIS communication failure** | 1 | If 1, the analyzer shows a warning on screen when it cannot reach the middleware. Recommended for quick fault detection. | **1** |
| **LIS communication type** | 0 (min 0, max 1) | 0 = TCP/IP network; 1 = serial (RS-232). Must be 0 for the middleware. | **0 (TCP/IP)** |
| **LIS protocol type** | Autobio protocol | The message framing/encoding standard. Must match what the middleware implements. The middleware speaks Autobio proprietary protocol (`{cmd,field,...}` framing). | **Autobio protocol** |
| **Network port type** | As client | Whether the analyzer acts as TCP client or server. The middleware is the server (`AutoLumoServer` binds a `ServerSocket`), so the analyzer must be the client. | **As client** |
| **Network port address** | 192.168.122.129 | **IP address of the PC running the AutoLumo middleware** (not the analyzer). The analyzer connects to this address. Must match the actual IP of the middleware host. | **IP of the middleware PC** — update whenever the middleware PC's IP changes |
| **Network port** | 11119 | **TCP port the middleware is listening on.** Must match `analyzerPort` in `D:\ccmw\settings\autolumo\config.json`. | **11119** (or whatever port is set in `config.json`) |
| **Test results are stored by day** | × (disabled) | Whether the analyzer groups stored results by calendar day. Internal analyzer storage behaviour; does not affect middleware. | Leave default |
| **Whether to send sample position** | × (disabled) | If ON, the analyzer appends rack/position info (rack number and position integer) as trailing fields in Cmd 5 and Cmd 7. The middleware reads these if present. | **OFF (×)** unless rack tracking is required by the LIMS |

---

### Section 3 — Result content options

| Parameter | Default shown | Meaning | Recommended setting |
|-----------|--------------|---------|---------------------|
| **How to send interferon results** | 0,189,289,389,489,589,689 | Comma-separated list of interferon result codes and how to encode them. Leave at analyzer default unless your LIMS requires a different mapping. | Leave as default |
| **Whether to swap the position of the test item** | × | Swaps column order for test items in the result message. Do not change unless explicitly required by your LIMS. | **OFF (×)** |
| **Whether to send data type character S** | ✓ | If ON, string fields are prefixed with `[S]` in the message (e.g. `[S]SampleNo`). The middleware's `parseStr()` method strips this prefix. **Must be ON** for correct parsing. | **ON (✓)** |
| **Whether to send data type character F** | ✓ | If ON, float fields have an `F` suffix (e.g. `45.67F`). The middleware's `stripSuffix()` handles this. Recommended ON for unambiguous field typing. | **ON (✓)** |
| **Whether to parse the medical record number** | ✓ | Tells the analyzer to parse/extract the medical record number from the sample barcode if present. Harmless when ON even if records have no MRN. | **ON (✓)** |
| **Interferon result field position** | 1 (min 0, max 2) | Column position of the interferon result within the result fields. Leave at analyzer default. | Default (1) |
| **LIS timeout period** | 9 (min 0, max 59) | Seconds the analyzer waits for a response from the middleware before declaring a timeout. The middleware's LIS HTTP calls have an 8 s connect + 15 s read timeout. Set this higher than the longest expected LIMS response time. | **15** — covers the 8+15 s middleware-to-LIMS window with margin |
| **Encoding method for sending data to LIS** | UTF-8 | Character encoding of the TCP byte stream. The middleware decodes with `"UTF-8"` and encodes responses with `"UTF-8"`. Must match. | **UTF-8** |
| **Whether to send detailed interferon positive** | × | Sends extended interferon subtype breakdown. Only enable if the LIMS supports and requires it. | **OFF (×)** |
| **Whether to open the surface antigen dilution** | 0,1113 | Dilution factor handling for HBsAg surface antigen. Leave at analyzer default unless the lab has a specific dilution protocol. | Default |
| **Whether to send test flags to LIS** | × | If ON, appends QC/result flags (e.g. `H`, `L`, `*`) to result messages (Cmd 5 field [9]). The middleware stores the flag in `ResultsRecord`. Enable if the LIMS acts on flags. | **ON (✓)** recommended |

---

### Section 4 — Extended result metadata

| Parameter | Default shown | Meaning | Recommended setting |
|-----------|--------------|---------|---------------------|
| **Whether to send the name of the kit used** | × | Appends kit/reagent name (Cmd 5 field [10]) to results. The middleware captures `kitName` but does not currently forward it. | **OFF (×)** unless LIMS needs it |
| **Whether to send the item ID of the reagent used** | × | Appends reagent item ID. Not forwarded by middleware. | **OFF (×)** |
| **Whether to send the validity period of the kit** | × | Appends kit expiry date. Not forwarded by middleware. | **OFF (×)** |
| **Get from LIS whether the test sends the sample** | × | Asks the middleware/LIMS whether this sample should be run. Corresponds to Cmd 1 / Cmd 9 query flow. Enable if the lab uses LIS-driven worklists. | **ON (✓)** if the lab downloads worklists from LIMS; **OFF** for walk-up mode |
| **Whether to send the remaining number of reagents** | × | Appends reagent remaining count. Not forwarded by middleware. | **OFF (×)** |
| **Whether to use +/- to indicate the detailed result** | × | Uses `+`/`-` symbols for qualitative results instead of numeric thresholds. Enable only if LIMS expects symbolic results. | **OFF (×)** |
| **Flags that do not send results** | 0 | Bitmask of result flag codes that should be suppressed and not sent to the LIS. `0` means send everything. | **0** |
| **Whether to send reagent lot number** | × | Appends lot number to results. Not forwarded by current middleware version. | **OFF (×)** |
| **Whether to send sample status information** | × | Appends sample status (e.g. haemolysis, lipaemia). Not forwarded by middleware. | **OFF (×)** |
| **Whether to send test results with error flag** | × | Includes results that carry an instrument error flag rather than suppressing them. Useful for audit. | **ON (✓)** recommended — better to receive flagged results than lose them |
| **Whether to output interferon suffix name** | × | Appends text suffix to interferon result values. LIMS-specific. | **OFF (×)** |
| **Whether to replace sample information when…** | × | Overwrites patient/sample fields on re-run. Leave OFF to preserve original data. | **OFF (×)** |
| **LIS sample position** | 47\|48 | Internal rack/position mapping used by the analyzer. Leave at analyzer default. | Default |
| **LIS duplex is enabled for duplicate sample ID** | × | Allows two samples with the same ID to coexist (duplex mode). Leave OFF unless the lab explicitly uses duplicate-ID workflows. | **OFF (×)** |
| **Whether to send the positive and negative result** | × | Sends qualitative positive/negative interpretation alongside the numeric value. Enable if LIMS needs the interpretation. | **OFF (×)** |

---

## Quick-reference checklist

Before going live, verify these settings on the analyzer:

- [ ] **LIS interface switch** = ON
- [ ] **Send results** = ON
- [ ] **LIS communication type** = 0 (TCP/IP)
- [ ] **LIS protocol type** = Autobio protocol
- [ ] **Network port type** = As client
- [ ] **Network port address** = IP of the PC running the AutoLumo middleware
- [ ] **Network port** = matches `analyzerPort` in `D:\ccmw\settings\autolumo\config.json` (default **11119**)
- [ ] **Whether to send data type character S** = ON
- [ ] **Whether to send data type character F** = ON
- [ ] **Encoding method** = UTF-8
- [ ] **LIS timeout period** ≥ 15 seconds
- [ ] **LIS testing and local testing priority** = 0 (LIS priority)

---

## config.json reference (middleware side)

The middleware reads `D:\ccmw\settings\autolumo\config.json`. The key fields that must align with the analyzer settings are:

| config.json field | Must match analyzer setting |
|-------------------|-----------------------------|
| `analyzerDetails.analyzerPort` | **Network port** |
| `limsSettings.limsServerBaseUrl` | LIMS server URL (no analyzer setting; this is the middleware-to-LIMS address) |

The middleware PC's IP address itself must match what is entered in **Network port address** on the analyzer. If the middleware PC's IP changes, update that field on the analyzer.
