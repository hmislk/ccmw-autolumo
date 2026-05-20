# ccmw-autolumo

CareCode Middleware for the **AutoLumo** immunoassay analyzer (Autobio Diagnostics).

Implements the **Autobio Proprietary LIS Communication Protocol v1.4** over TCP/IP.  
The analyzer acts as TCP **Client**; this middleware acts as TCP **Server**.

## Supported commands

| Cmd | Direction | Description |
|-----|-----------|-------------|
| 1   | Analyzer → LIS | Query test order for sample |
| 2   | LIS → Analyzer | Respond with test order data |
| 5   | Analyzer → LIS | Send result by test item |
| 6   | LIS → Analyzer | Acknowledge result by test |
| 7   | Analyzer → LIS | Send all results by sample |
| 8   | LIS → Analyzer | Acknowledge result by sample |
| 9   | Analyzer → LIS | Quick query test order (with rack position) |
| 10  | LIS → Analyzer | Respond to quick query |
| 11  | Analyzer → LIS | Request LIS version |
| 12  | LIS → Analyzer | Respond with version |
| 19  | Analyzer → LIS | Sample info notification (no response) |

## Configuration

Create `D:\ccmw\settings\autolumo\config.json`:

```json
{
  "analyzerDetails": {
    "analyzerName": "AutoLumo",
    "analyzerPort": 5100,
    "communicationType": "tcpip",
    "socketCommunicationType": "server"
  },
  "limsSettings": {
    "limsServerBaseUrl": "http://your-hmis-server/api/middleware",
    "username": "your-username",
    "password": "your-password"
  }
}
```

## Build

```bash
mvn clean package
```

Produces a fat JAR at `target/AutoLumo-1.0.jar`.

## Run

```bash
java -jar target/AutoLumo-1.0.jar
```

## Architecture

```
AutoLumo.java            – main(), startup, smoke-test flags
AutoLumoServer.java      – TCP ServerSocket loop + Autobio protocol FSM
AnalyzerCommunicator.java – builds response strings for each command
LISCommunicator.java     – REST POST/GET to HMIS (/test_results, /test_orders_for_sample_requests)
SettingsLoader.java      – reads config.json into MiddlewareSettings
```

Depends on [`lims-middleware-libraries`](https://github.com/hmislk/lims-middleware-libraries) (via JitPack).
