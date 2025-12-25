I will fix the missing commands, broken GUI protection, and backend connection issues based on your feedback and screenshots.

**1. Fix Missing Commands (`MissionsCommand.java`)**

* **Implement** **`/missions progress`** **(or just** **`/missions`** **fallback):** Matches the first screenshot. Displays the player's own active daily/weekly missions and their current progress in chat.

* **Implement** **`/missions info <player>`:** Matches the second screenshot. Displays detailed mission status for *another* player (admin/debug tool), including:

  * Status (Online/Offline)

  * List of Daily/Weekly missions with progress

  * Summary stats (completed counts, reset times)

**2. Fix GUI "Broken" (Items can be taken)**

* **Add** **`InventoryDragEvent`** **handler:** Prevents dragging items around in the GUI.

* **Strengthen** **`InventoryClickEvent`:** Ensure the title check is robust (stripping colors for comparison) to guarantee the event is cancelled.

**3. Fix Backend Communication**

* **Enable Error Logging:** Update `UpdateChecker.java` to print connection errors (404, connection refused, etc.) to the console instead of failing silently. This will help us "detect" why the backend isn't seeing it.

* **Fix Server IP:** `Bukkit.getServer().getIp()` often returns empty on local/hosted servers. I will add a fallback to `127.0.0.1` or a real IP lookup so the backend receives valid JSON.

**4. Build & Verify**

* Rebuild the plugin (obfuscated) and provide the new jar.

