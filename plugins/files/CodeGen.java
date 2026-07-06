package plugins.files;

import plugins.Plugin;
import io.HTTP;
import io.Dos;
import server.PVA;
import hash.StringHash;
import data.Command;
import utils.Tools;
import java.io.*;
import java.net.*;

public class CodeGen extends Plugin {

	private String model = "";
	private String host = "";
	private String port = "";
	private String editor = "gedit";

	public void init(PVA pva) {
		this.pva = pva;
		String codeModel = pva.config.get("code", "model");
		model = codeModel.isEmpty() ? pva.config.get("ai", "model") : codeModel;
		host  = pva.config.get("ai", "host");
		port  = pva.config.get("ai", "port");
		String cfgEditor = pva.config.get("app", "txt");
		if (!cfgEditor.isEmpty()) editor = cfgEditor;
		info.put("hasThread", "no");
		info.put("hasCodes", "yes");
		info.put("name", "CodeGen");
	}

	public StringHash getPluginInfo() { return info; }
	public String getVar(String name) { return vars.get(name); }
	public boolean setVar(String name, String value) {
		if (name.equals("stop") && value.equals("yes")) triggerStop();
		vars.put(name, value);
		return true;
	}

	public String[] getActionCodes() { return new String[]{"CODEGEN"}; }

	public boolean execute(Command cf, String rawtext) {
		if (!cf.command.equals("CODEGEN")) return false;

		String prompt = rawtext.trim();
		if (prompt.isEmpty()) {
			say(getT("CODEGEN_EMPTY"), cf.filter, cf.negative);
			return true;
		}

		String response = callOllama(prompt);
		if (response == null || response.isEmpty()) {
			say(getT("CODEGEN_FAIL"), cf.filter, cf.negative);
			return true;
		}

		String[] parts = extract(response);
		String explanation = parts[0];
		String code = parts[1];

		if (!explanation.isEmpty())
			say(explanation, cf.filter, cf.negative);

		if (!code.isEmpty()) {
			pasteCode(code);
			say(getT("CODEGEN_OK"), cf.filter, cf.negative);
		}
		return true;
	}

	private String callOllama(String prompt) {
		String systemMsg = "Du bist ein Programmier-Assistent. "
			+ "Erkläre kurz, was der Code tut. "
			+ "Jede einzelne Codezeile MUSS mit dem Präfix \"OUTPUT:\" beginnen. "
			+ "Nach \"OUTPUT:\" darf kein Zeilenumbruch folgen. "
			+ "Keine Markdown-Codeblöcke.";

		String userMsg = "Erzeuge folgenden Code:\n" + prompt;

		try {
			String jsonPayload = "{\"model\":\"" + model + "\",\"stream\":false,\"messages\":["
				+ "{\"role\":\"system\",\"content\":\"" + jsonEscape(systemMsg) + "\"},"
				+ "{\"role\":\"user\",\"content\":\"" + jsonEscape(userMsg) + "\"}]}";

			jsonPayload = HTTP.toUnicode(jsonPayload);

			URL url = new URL("http://" + host + ":" + port + "/api/chat");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			conn.setRequestProperty("User-Agent", "PVA/latest");

			byte[] payloadBytes = jsonPayload.getBytes("UTF-8");
			conn.setFixedLengthStreamingMode(payloadBytes.length);
			conn.getOutputStream().write(payloadBytes);
			conn.getOutputStream().flush();

			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
			br.close();

			return extractContent(response.toString());

		} catch (Exception e) {
			log("CodeGen: " + e.getMessage());
			return null;
		}
	}

	private String extractContent(String json) {
		String content = Tools.zwischen(json, "\"content\":\"", "\"");
		if (content != null) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(content);
			StringBuffer sb = new StringBuffer();
			while (m.find())
				m.appendReplacement(sb, String.valueOf((char) Integer.parseInt(m.group(1), 16)));
			m.appendTail(sb);
			content = sb.toString();
			content = content.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "").replace("\\t", " ");
			content = content.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");
		}
		return content;
	}

	private String[] extract(String text) {
		if (text == null) return new String[]{"", ""};
		StringBuilder explanation = new StringBuilder();
		StringBuilder code = new StringBuilder();
		for (String line : text.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("OUTPUT:"))
				code.append(trimmed.substring(7)).append("\n");
			else
				explanation.append(line).append("\n");
		}
		return new String[]{explanation.toString().trim(), code.toString().trim()};
	}

	private void pasteCode(String code) {
		Dos dos = new Dos();
		String tmpFile = System.getProperty("java.io.tmpdir") + "/pva_codegen_" + System.currentTimeMillis() + ".tmp";
		String editorBin = editor.split("\\s+")[0].toLowerCase();

		dos.writeFile(tmpFile, code);
		dos.readPipe("xclip -sel clip \"" + tmpFile + "\"");

		boolean running = !dos.readPipe("pgrep -x \"" + editorBin + "\"").trim().isEmpty();
		if (!running) {
			dos.readPipe(editor + " &");
			for (int w = 0; w < 20; w++) {
				try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
				running = !dos.readPipe("pgrep -x \"" + editorBin + "\"").trim().isEmpty();
				if (running) break;
			}
		}

		if (running)
			dos.readPipe("xdotool search --name \"" + editorBin + "\" windowactivate --sync && "
				+ "xdotool key --clearmodifiers ctrl+v");

		new File(tmpFile).delete();
	}

	private String jsonEscape(String s) {
		return s.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	private void triggerStop() {}

	public void run() {}
}
