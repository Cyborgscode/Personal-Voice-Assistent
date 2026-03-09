# PVA Developer Guide: Intent System & Plugin Architecture

This documentation describes the asynchronous communication layer and the plugin structure for the Personal Voice Assistant (PVA).

## A) Basic Plugin Structure (How to write a Plugin)

Plugins are stored as `.java` files in `plugins/files/` and are instantiated at runtime. Since they remain in memory, they can maintain runtime information. Persistent data must be saved manually in the user's home directory (e.g., `~/.config/pva/`).

### Required Methods (Interface):
*   `public StringHash getPluginInfo();` -> Vital info for the loader.
*   `public void init(PVA pva);` -> Initialization with Core reference.
*   `public void run();` -> Thread logic (if `hasThread="yes"`).
*   `public String[] getActionCodes();` -> Registration for intents.
*   `public boolean execute(String actioncode, String data);` -> Main logic entry point.
*   `public String getVar(String name);` / `setVar(String name, String value);` -> Variable interface.

---

## B) Registration & Routing

For a plugin to participate in intent traffic, two conditions must be met:

1.  **Plugin-Info:** `info.put("hasCodes", "yes");` must be set in `getPluginInfo()`.
2.  **Actioncode Filter:** The specific code (e.g., `MOOD_IMPULS`) must be returned in the string array of `getActionCodes()`.

Actioncodes must be identical to the keys of the "command:" directives in the config. Plugins can have more Actioncodes than specified in the config, for example, to provide internal functions for other plugins. 
Using `MOOD_IMPULS` as an example: this code makes no sense in the config, as it exists to allow other plugins to influence the mood.

---

## C) The execute() Method & Flow Control

The plugin manager uses the return value of `execute()` to control the processing chain:

*   **`return true;`**: "I intercepted and processed the ACTIONCODE. Manager, no need to look further, we are done." The chain is **terminated**.
*   **`return false;`**: "This Actioncode may have been seen/processed, but other plugins might want to react to it as well." The manager passes the intent to the **next plugin** in the list.

---

## D) Synchronous vs. Asynchronous (Intent Methods)

### SYNC (Synchronous)
*   `pva.sendIntent(Command cmd, String data)`
*   **Behavior:** Immediate processing in the current thread. Useful for **priority requests**.

### ASYNC (Asynchronous) - Preferred Method
*   **Behavior:** The intent is queued to maintain the **sequential order** of events.
*   **Signatures:**
    *   `pva.AsyncSendIntent(Command cmd, String data)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data, String answereWith)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data, String answereWith, String extra)`

---

## E) Status Signaling (ON/OFF Logic)

Important status messages (e.g., `AISUPPORTOFF` / `AISUPPORTON`) serve as global information for all plugins.
*   **Broadcast:** These intents are usually acknowledged with `return false;` so they propagate through the entire chain.
*   **Self-Responsibility:** AI-based plugins (e.g., `AIStreamer`, `WikiPlugin`) must store this status internally. If support is `OFF`, they must not dispatch AI intents and instead output error messages (via `ASYNCSPEAK`), otherwise the intents will vanish into thin air.

---

## F) Practical Examples & ExtraData

### Example: Mood Impulse & Callback

After reading a Wikipedia page, the Wikipedia plugin dispatches the following intents:
```
// Simple Mood Impulse (5 points)
pva.AsyncSendIntent(new Command("WIKIPLUGIN", "MOOD_IMPULS", "", ""), "5");
pva.AsyncSendIntent(new Command("WIKIPLUGIN", "AI_SUMMARIZE", "WIKI_SUMMARY_READY", cf.negative), extract);
```
The page is passed as "extract", processed by the AIStreamer, and the response is sent back to WIKI_SUMMARY_READY. The Wikipedia plugin now has its summary.
Normally, this would be output via ASYNCSPEAK, but it is conceivable that another intent is dispatched to yet another plugin. Information influencing this can be passed via the cf.negative (extra) parameter.

### Using Extra Data (ExtraData)

The fourth parameter (extra / ExtraData) serves as a flexible container for metadata or secondary parameters.

Function: Separation of primary payload (data) and protocol-specific control information.

MatrixPlugin Reference: The separation is particularly clear here. Since sending requires both the Room ID and the message text, the plugin uses delimiters (like |) within the string to tunnel the data.

Important Note: There is no fixed standard for the format within ExtraData. Developers must check the source code of the respective target plugin (e.g., MatrixPlugin.java) to see how strings are parsed there.
