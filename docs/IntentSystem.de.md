# PVA Developer Guide: Das Intent-System & Plugin-Architektur

Diese Dokumentation beschreibt die asynchrone Kommunikationsschicht und die Struktur von Plugins für den Personal Voice Assistent (PVA).

## A) Plugin-Grundstruktur (How to write a Plugin)

Plugins liegen als `.java` Files in `plugins/files/` und werden zur Laufzeit instanziiert. Da sie im Speicher bleiben, können sie Runtime-Infos halten. Permanente Daten müssen selbstständig im User-Home (z.B. `~/.config/pva/`) gesichert werden.

### Erforderliche Methoden (Interface):
*   `public StringHash getPluginInfo();` -> Vital-Infos für den Loader.
*   `public void init(PVA pva);` -> Initialisierung mit Core-Referenz.
*   `public void run();` -> Thread-Logik (wenn `hasThread="yes"`).
*   `public String[] getActionCodes();` -> Registrierung für Intents.
*   `public boolean execute(String actioncode, String data);` -> Logik-Einstieg.
*   `public String getVar(String name);` / `setVar(String name, String value);` -> Variablen-Interface.

---

## B) Registrierung & Routing

Damit ein Plugin am Intent-Verkehr teilnimmt, müssen zwei Bedingungen erfüllt sein:

1.  **Plugin-Info:** `info.put("hasCodes", "yes");` muss in `getPluginInfo()` gesetzt sein.
2.  **Actioncode-Filter:** In `getActionCodes()` muss der spezifische Code (z.B. `MOOD_IMPULS`) im String-Array zurückgegeben werden.

Die Actioncodes müssen mit den Keys der "command:" Anweisungen in der config identisch sein. Plugins können mehr Actioncodes haben, als in der Config angebeben wird z.B. um interne Funktionen für andere Plugins bereit zu stellen. 
Um den `MOOD_IMPULS` als Beispiel zu benutzen, dieser macht keinen Sinn in der Config, da er anderen Plugins erlaubt, die Stimmung zu beeinflußen. 

---

## C) Die execute() Methode & Flow-Control

Der Pluginmanager nutzt den Rückgabewert von `execute()`, um die Bearbeitungskette zu steuern:

*   **`return true;`**: "Ich habe den ACTIONCODE abgegriffen und bearbeitet. Manager, du brauchst nicht mehr weiter suchen, wir sind fertig." Die Kette wird **terminiert**.
*   **`return false;`**: "Dieser Actioncode wurde vielleicht gesehen/bearbeitet, aber andere Plugins könnten ebenfalls darauf reagieren wollen." Der Manager reicht den Intent an das **nächste Plugin** in der Liste weiter.

---

## D) Synchron vs. Asynchron (Intent Methoden)

### SYNC (Synchron)
*   `pva.sendIntent(Command cmd, String data)`
*   **Verhalten:** Sofortige Bearbeitung im aktuellen Thread. Nützlich bei **Prioritäts-Anfragen**.

### ASYNC (Asynchron) - Präferierte Methode
*   **Verhalten:** Der Intent wird gequeued, um die **sequenzielle Reihenfolge** von Ereignissen beizubehalten.
*   **Signaturen:**
    *   `pva.AsyncSendIntent(Command cmd, String data)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data, String answereWith)`
    *   `pva.AsyncSendIntent(String sender, String intent, String data, String answereWith, String extra)`

---

## E) Status-Signalisierung (ON/OFF Logik)

Wichtige Status-Meldungen (z.B. `AISUPPORTOFF` / `AISUPPORTON`) dienen als globale Information für alle Plugins.
*   **Broadcast:** Diese Intents werden meist mit `return false;` quittiert, damit sie durch die gesamte Kette wandern.
*   **Eigenverantwortung:** KI-basierte Plugins (z.B. `AIStreamer`, `WikiPlugin`) müssen diesen Status intern speichern. Ist der Support `OFF`, dürfen sie keine KI-Intents absetzen, sondern müssen Fehlermeldungen (via `ASYNCSPEAK`) ausgeben, da die Intents sonst ins Leere laufen.

---

## F) Praxis-Beispiele & ExtraData

### Beispiel: Mood-Impuls & Callback

Das Wikipedia Plugin setzt nach dem Auslesen der Wikipediaseite folgende Intents ab:
```
// Einfacher Mood-Impuls (5 Punkte)
pva.AsyncSendIntent(new Command("WIKIPLUGIN", "MOOD_IMPULS", "", ""), "5");
pva.AsyncSendIntent(new Command("WIKIPLUGIN", "AI_SUMMARIZE", "WIKI_SUMMARY_READY", cf.negative), extract);
```

Die Seite wird als "extract" übergeben, vom AIStreamer verarbeitet und die Antwort an "WIKI_SUMMARY_READY" zurück geschickt. Das Wikipediaplugin hat jetzt seine Zusammenfassung.
Normalerweise würde man das jetzt per "ASYNCSPEAK" ausgeben, es aber denkbar, daß wieder ein Intent an ein anderes Plugin abgesetzt wird. Informationen die das beeinflußen könnten per "cf.negative" übergeben werden.


### Nutzung von ExtraDaten (ExtraData)

Der vierte Parameter (extra / ExtraData) dient als flexibler Container für Metadaten oder sekundäre Parameter.

Funktion: Trennung von primärer Nutzlast (data) und protokollspezifischen Steuerinformationen.

Referenz MatrixPlugin: Hier ist die Trennung besonders deutlich. Da für einen Versand sowohl die Room-ID als auch der Nachrichtentext benötigt werden, nutzt das Plugin Trenner (wie |) innerhalb des Strings, um die Daten zu tunneln.

Wichtiger Hinweis: Es gibt keinen festen Standard für das Format innerhalb von ExtraData. Entwickler müssen zwingend im Sourcecode des jeweiligen Ziel-Plugins (z.B. MatrixPlugin.java) nachsehen, wie die Strings dort geparst werden.
