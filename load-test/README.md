# Load Testing

All load-testing scripts for RewaBank live here.

| Script | Purpose |
|---|---|
| `run-load-test.ps1` | Full automation — starts cluster if needed, waits for pods, gets a token, runs the load test. **Start here.** |
| `load-test.ps1` | The actual load generator (PowerShell runspace pool + .NET HttpClient). Fires requests at all services via the gateway and prints a WORKING / BREAKING / RISK report. |
| `get-token.ps1` | Fetches a Keycloak JWT. Called by `run-load-test.ps1`; also usable standalone. |

## Usage

Run from anywhere (paths resolve via `$PSScriptRoot`):

```powershell
# Full automation (defaults: 50 users, 60s)
.\load-test\run-load-test.ps1

# All params
.\load-test\run-load-test.ps1 -User "newuser@rewabank.com" -Password "Test@1234" -Users 100 -DurationSeconds 120
```

Drive the generator directly with an existing token:

```powershell
$TOKEN = .\load-test\get-token.ps1 -User "newuser@rewabank.com" -Password "Test@1234"
.\load-test\load-test.ps1 -Token $TOKEN -Users 50 -DurationSeconds 60
```