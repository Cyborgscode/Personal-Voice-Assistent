package plugins.files;

import plugins.Plugin;
import io.HTTP;
import io.Dos;
import utils.Tools;
import server.PVA;
import hash.StringHash;
import data.Command;
import data.AIMessage;
import data.AIMessages;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.*;
import java.net.*;

/*
	If comments arn't in english, just translate them.. the idea was simple, but the logic is complex, so pls use a good translator ;)

*/

public class AIStreamer extends Plugin {

	private final LinkedBlockingQueue<String> jobQueue = new LinkedBlockingQueue<>(5);
	private final LinkedBlockingQueue<String> speechQueue = new LinkedBlockingQueue<>(50);
	private volatile boolean abortFlag = false;
	private HttpURLConnection currentConn = null;
	static AIMessages aimsgs = new AIMessages();

	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread", "yes");
		info.put("hasCodes", "yes");
		info.put("name", "AIStreamer");
		vars.put("status", "idle");
		aimsgs.addMessage(new AIMessage("user", "User", "Hallo" ));
	}

	public StringHash getPluginInfo() { return info; }
	public String getVar(String name) { return vars.get(name); }
	public boolean setVar(String name, String value) { 
		if(name.equals("stop") && value.equals("yes")) triggerStop();
		vars.put(name, value); 
		return true; 
	}

	public String[] getActionCodes() { return new String[]{"AI_SAY", "AI_STOP","AICLEARHISTORY"}; }

	public boolean execute(Command cf, String rawtext) {
	
		log("AISTreamer:"+ cf.command +" => "+ rawtext);
	
		if (cf.command.equals("AI_STOP")) {
			triggerStop();
			return true;
		}
		if (cf.command.equals("AI_SAY")) {
			jobQueue.offer(rawtext);
			return true;
		}
		
		if ( cf.command.equals("AICLEARHISTORY")) {
			aimsgs.clear();
			
			try {
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "AIHISTORYCLEARED" ) );
			} catch (Exception e) {
				// not much we can do exept 
				log("AIStreamer:AICLEARHISTORY: pva.say failed for AIHISTORYCLEARED");
			}

			return true;
		}
	
		return false;
	}

	// Hilfsmethode für die Übersetzung
	private String getT(String key) {
		return pva.texte.get(pva.config.get("conf", "lang_short"), key);
	}
	
	private void triggerStop() {
		this.abortFlag = true;
		this.jobQueue.clear();
		this.speechQueue.clear();
		if (currentConn != null) currentConn.disconnect(); // Kappe Ollama-Stream
		dos.readPipe("killall -9 play"); // Der "Star-Trek" Reset
		log(getT("AIS_EMERG_STOP")); 
		vars.put("status", "idle");
	}

	public void run() {
		// Speaker-Thread starten (Consumer)

		new Thread(this::speakerLoop).start();

		while (!isInterrupted()) {
			try {
				String job = jobQueue.take();
			   	log("AISTreamer:run()"+ job );
				this.abortFlag = false;
				streamFromOllama( job ); 
			} catch (InterruptedException e) { break; }
		}
	}

	private void streamFromOllama(String prompt) {
		vars.put("status", "streaming");
		StringBuilder sentenceBuf = new StringBuilder();
		
		try {
		
			aimsgs.addMessage(new AIMessage("user", "User", prompt ));
		
			URL url = new URL("http://" + pva.config.get("ai", "host") + ":" + pva.config.get("ai", "port") + "/api/chat");
			currentConn = (HttpURLConnection) url.openConnection();
			currentConn.setRequestMethod("POST");
			currentConn.setDoOutput(true);
			currentConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			currentConn.setRequestProperty("Connection", "keep-alive");
			currentConn.setRequestProperty("User-Agent","PVA/latest");

			String payload = "{\"model\":\"" + pva.config.get("ai", "model") + "\",\"stream\":true,\"messages\":"+ aimsgs.toJSON() +"}";
	
			log("AISTreamer:streamFromOllama():"+ payload );
	
			payload = HTTP.toUnicode( payload );
	
			byte[] payloadBytes = payload.getBytes("UTF-8");
			currentConn.setFixedLengthStreamingMode(payloadBytes.length); // Sagt Java: Nicht puffern, direkt raus damit!
			currentConn.setRequestProperty("Content-Length", Integer.toString(payloadBytes.length));	
	
			currentConn.getOutputStream().write(payloadBytes);
			currentConn.getOutputStream().flush(); 
	
//			log("AISTreamer:streamFromOllama(): sent");

			InputStream in = currentConn.getInputStream();
	  		InputStreamReader isr = new InputStreamReader( in , "UTF-8");

			int c;
			StringBuilder rawJson = new StringBuilder();
			StringBuilder json	= new StringBuilder();
	
			// MIKROMANAGEMENT: Byte-für-Byte Parser für NDJSON
		
			while ((c = isr.read()) != -1 && !abortFlag) {
				char ch = (char) c;
				rawJson.append(ch);
		
				// log("AISTreamer:streamFromOllama(): "+ rawJson.toString() );
		
				if (ch == '\n') {
		
					String token = extractResponse(rawJson.toString());
					rawJson.setLength(0);
			
					log("AISTreamer:streamFromOllama(): token = "+ token );
			
					if (token != null) {
						sentenceBuf.append(token);
		
//						log("AISTreamer:streamFromOllama(): sentenceBuf = "+ sentenceBuf.toString() );
			   
						if (isEndOfSentence(token,sentenceBuf)) {
		
							// log("AISTreamer:streamFromOllama(): sentenceBuf = "+ new String( sentenceBuf.toString() ) );
							json.append( sentenceBuf.toString().trim()+"\n" );
							speechQueue.put(sentenceBuf.toString().trim());
							sentenceBuf.setLength(0);
						}
					}
				}
			}
	
			aimsgs.addMessage(new AIMessage("assistant", pva.config.get("ai","model"), Tools.filterAIThinking( json.toString() ).trim() ));
	
		} catch (Exception e) {
			log(getT("AIS_STREAM_ERR") + e.getMessage());
		} finally {
			if (sentenceBuf.length() > 0) speechQueue.offer(sentenceBuf.toString());
			vars.put("status", "idle");
		}
	}

	private void speakerLoop() {
		while (!isInterrupted()) {
			try {
				String s = speechQueue.take();
		
				s = s.replaceAll("^\\* ", ""); // Listen-Sternchen am Anfang weg
				
				log("AISTreamer:speakerLoop(): "+ s );
		
				if (!abortFlag) {
					vars.put("status", "speaking");
					pva.say(s, true); 
					vars.put("status", "idle");
				}
			} catch (Exception e) { 
				log(getT("AIS_SAY_FAIL"));
				vars.put("status", "idle");
			}
		}
	}

	private String extractResponse(String body) {
		// Den Inhalt des "response"-Feldes extrahieren
		// Ollama Format: ...,"response":"HIER DER TEXT","done":...
		
		String content = Tools.zwischen(body, "\"response\":\"", "\"");
		if ( content == null ) {
			content = Tools.zwischen(body, "\"content\":\"", "\"");
		}
		
		if (content != null) {

			// 1. Alle Unicode-Escapes auf einen Schlag wandeln
			// Nutzt Regex, um das Muster zu finden und die Hex-Zahl zu parsen
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(content);
			StringBuilder sb = new StringBuilder();
			while ( matcher.find() ) 
				matcher.appendReplacement(sb, String.valueOf((char) Integer.parseInt(matcher.group(1), 16)));
			matcher.appendTail(sb);
			content = sb.toString();
			
			// EMOTICON Ersetzungen
		
			content = content.replace("😀", " hahaha ")
				 .replace("😂", " , das ist ja zum Schießen, ")
				 .replace("🤔", " hmmm, lass mich mal überlegen, ")
				 .replace("😉", " , zwinker zwinker, ")
				 .replace("🙄", " , na toll, schon wieder, ")
				 .replace("😡", " , ich koche vor Wut, ")
				 .replace("😎", " , ich bin ja so cool, ");
			 
			// Den Rest der Unicodeparade in die Hölle senden:

			content = content.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]", "");
		  
			return content.replace("\\\"", "\"")
				  .replace("\\n", "\n")
				  .replaceAll("(?m)^\\s*\\*\\s+", "")
				  .replaceAll("\\*\\*", "")
			   	  .replaceAll("^\\* ", "")
				  .replace("\\r", "")
				  .replace("\\t", " ");
		}
		return null;
	 }
	  
	private boolean isEndOfSentence(String t, StringBuilder currentBuf) {
	
		// YES, This is complex shit and it's just the beginning.
	
		// 1. Virtuelle Vorschau: Wie würde der Satz aussehen, wenn wir t hinzufügen?
		String vorschau = (currentBuf.toString() + t).trim().toLowerCase();

		// 2. DER ULTIMATIVE SCHUTZ: 
		// Wenn die Vorschau auf eine Zahl endet (mit oder ohne Punkt), 
		// ist es NIEMALS ein Satzende.
		// Deckt ab: "1", " 1.", "1.010", "1.010.408"
 
		//	log(" t = |"+t+"| vorschau=|"+ vorschau +"|");

		// 1. DER ZAHLEN-SCHUTZ (Bleibt heilig!)
		// Wenn die Vorschau auf einer Zahl (mit/ohne Punkt) endet -> WEITERMACHEN.
		if (vorschau.matches(".*\\d+$") || vorschau.matches(".*\\d+\\.$")) {
		if (t.contains("\n")) return true; 
			return false; 
		}

		// 2. LISTEN-TRENNER (Neu!)
		// Wenn wir ein Newline (\n) sehen und davor steht Text (keine Zahl) -> TRENNEN!
		if (t.contains("\n") && vorschau.length() > 5) {
			return true; 
		}

		// 3. HARTE SATZENDE-ZEICHEN
		if (t.contains("!") || t.contains("?")) return true;

		// 4. DER PUNKT-CHECK (Präzise)
		if (t.contains(".")) {
	
			// 5. RÖMISCHE ZAHLEN
			// Erkennt: I. II. III. IV. V. VI. VII. VIII. IX. X. usw.
			// Der Regex prüft, ob vor dem Punkt nur römische Ziffern stehen
			if (vorschau.matches(".*\\s[ivxldcm]+\\.$")) {
				return false; // Warten! Es ist ein Name oder eine Dynastie.
			}

			// 6. KLASSISCHE ABKÜRZUNGS-BREMSE (ca., v. Chr., Dr.)
		
			String[] checkboxAlpha = new String[]{" ca.","(ca."," v."," dr."," chr."," bzw."," aso."};
			
			for(String c : checkboxAlpha) 
				if ( vorschau.endsWith( c ) || vorschau.endsWith( c+".") ) 
				return false;
		
			// Tausendertrenner-Schutz
			if (vorschau.matches(".*\\d{1,3}\\.$")) return false;
			if (vorschau.matches(".*\\d{1,3}\\.\\.$")) return false;
				// Abkürzungsschutz (ca., Dr., usw.)
			String[] parts = vorschau.split("\\s+");
			if (parts.length > 0) {
				String last = parts[parts.length - 1].toLowerCase();
				if (last.length() <= 3 && last.contains(".")) return false;
			}
			return true;
		}
		return false;
	}
}
