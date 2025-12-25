I will implement the `ChatCleaner` plugin using **strictly** the existing backend endpoints found in `backend/src/index.ts`.

### **1. Backend Communication (`ServiceLink.java`)**
I will implement exactly these calls:

*   **`GET /health`**: Connectivity check.
*   **`POST /plugin/register`**: Startup registration.
*   **`POST /plugin/heartbeat`**: Periodic keep-alive + Command receiving.
*   **`POST /plugin/players`**: Player join/quit updates.
*   **`POST /plugin/console`**: Streaming logs.
*   **`POST /plugin/status`**: Status updates.
*   **`POST /plugin/event`**: Reporting filter violations.

### **2. Core Features**
*   **Chat Filtering**: Replaces banned words (configurable).
*   **Spam Prevention**: Caps limit, repetition check, delay.
*   **Slow Mode**: Per-player cooldowns.
*   **Logging**: To local file AND to backend via `/plugin/console` and `/plugin/event`.

### **3. Execution Flow**
1.  **Scaffold**: Create `ChatCleaner` directory structure.
2.  **Code**: Write `ChatCleaner.java`, `ConfigManager.java`, `ChatListener.java`, `CommandManager.java`.
3.  **Network**: Write `ServiceLink.java` with the strict endpoint list above.
4.  **Logging**: Write a custom Log4j Appender to feed `/plugin/console`.
5.  **Build**: Create `compile_chatcleaner.ps1` to handle dependencies and building.

I will start by creating the project structure.