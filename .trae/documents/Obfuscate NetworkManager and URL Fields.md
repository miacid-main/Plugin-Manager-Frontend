I will rename the `backendUrl` field and the `NetworkManager` class to generic, inconspicuous names to hide their purpose from decompilers.

### 1. Rename & Refactor for Obfuscation
*   **Rename `NetworkManager`** → `com.dailymissions.utils.UpdateChecker` (or `MetricsHandler`).
    *   This makes it look like a standard, boring utility class that most plugins have.
*   **Rename `backendUrl` field** → `cacheKey` or `serviceEndpoint`.
*   **Rename `getBackendUrl()` method** → `resolveKey()`.

### 2. Continue with Core Logic Porting
*   Once renamed, I will continue porting the `MissionManager`, `RewardManager`, and `GUIManager` logic as planned, ensuring they use this new obfuscated class for communication.

### 3. Execution
*   Apply the renaming immediately to `NetworkManager.java` and `SecureString.java`.
*   Proceed with the remaining implementation steps (Missions, GUI, ProGuard).
