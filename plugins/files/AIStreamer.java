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

	public static class AIJob {
	    public final String text;
	    public final String returnIntent;
	    public final String extra;
	    public AIJob(String text, String returnIntent, String extra) {
	        this.text = text;
	        this.extra = extra;
	        this.returnIntent = returnIntent;
	    }
	}

	
	private final LinkedBlockingQueue<AIJob> jobQueue = new LinkedBlockingQueue<>(50);
//	private final LinkedBlockingQueue<String> jobQueue = new LinkedBlockingQueue<>(50);
	private HttpURLConnection currentConn = null;
	private volatile boolean abortFlag = false;
	static AIMessages aimsgs = new AIMessages();

	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread", "yes");
		info.put("hasCodes", "yes");
		info.put("name", "AIStreamer");
		vars.put("status", "idle");
		aimsgs.addMessage(new AIMessage("system", pva.config.get("ai","model"), pva.config.get("ai","systemprompt") ));
	}

	public StringHash getPluginInfo() { return info; }
	public String getVar(String name) { return vars.get(name); }
	public boolean setVar(String name, String value) { 
		if(name.equals("stop") && value.equals("yes")) triggerStop();
		vars.put(name, value); 
		return true; 
	}

	public String[] getActionCodes() { return new String[]{"AI_RETURN_DATA","AI_SAY", "AI_STOP","AI_ANSWERE","AI_VISION_ANALYZE","AICLEARHISTORY","AI_SUMMARIZE"}; }

	public boolean execute(Command cf, String rawtext) {
	
		if ( cf.negative.matches("^[A-Za-z0-9+/]+={0,2}$") && cf.negative.length() % 4 == 0 ) {
			log("AISTreamer:"+ cf.command +" "+ cf.filter +" => "+ rawtext);
		} else	log("AISTreamer:"+ cf.toString() +" => "+ rawtext);

		if (cf.command.equals("AI_VISION_ANALYZE")) {
			String aiResponse = streamFromOllama( new AIJob(rawtext, cf.filter,cf.negative), false); 
			log("AI_STREAMER: VISION_ANALYZE response="+aiResponse);
			pva.AsyncSendIntent(new Command("AISTREAMER", cf.filter, "", cf.negative), aiResponse);
			return true;
		}
	
		if (cf.command.equals("AI_RETURN_DATA")) {
			pva.AsyncSendIntent(new Command("AISTREAMER", "MOOD_IMPULS", "", ""), "1");
			String aiResponse = streamFromOllama( new AIJob(rawtext, cf.filter,cf.negative), false,"data","PVA"); 
			log(aiResponse);
			return true;
		}
		if (cf.command.equals("AI_STOP")) {
			triggerStop();
			// Intent:      SENDERID, CMD, unused,unused, optionaltext 
			pva.AsyncSendIntent(new Command("AISTREAMER", "STOPSPEECH", cf.filter, cf.negative), "");
			pva.AsyncSendIntent(new Command("AISTREAMER", "MOOD_IMPULS", cf.filter, cf.negative), "-10");
			return true;
		}
		if (cf.command.equals("AI_SAY")) {
			pva.AsyncSendIntent(new Command("AISTREAMER", "MOOD_IMPULS", "", ""), "5");
			jobQueue.offer(new AIJob(rawtext,cf.filter,cf.negative));
			return true;
		}
		if (cf.command.equals("AI_ANSWERE")) {
			pva.AsyncSendIntent(new Command("AISTREAMER", "MOOD_IMPULS", "", ""), "5");
			if ( rawtext.contains("|") ) {
				String[] x = rawtext.split("\\|");
				String aiResponse = streamFromOllama( new AIJob(x[1], cf.filter,cf.negative), false); 
				log("aiResponse="+aiResponse);
				pva.AsyncSendIntent(new Command("AISTREAMER", cf.filter, "", ""), x[0]+"|"+aiResponse);
				return true;
			}
			if ( cf.negative.contains("|") ) {
				String[] x = cf.negative.split("\\|");
				String aiResponse = streamFromOllama( new AIJob( rawtext, cf.filter,cf.negative), false); 
				log("aiResponse="+aiResponse);
				pva.AsyncSendIntent(new Command("AISTREAMER", cf.filter, "", cf.negative), aiResponse);
				return true;
			}
			return false;
		}
		if (cf.command.equals("AI_SUMMARIZE")) {
			// Hole den harten System-Prompt aus der Config
			String systemPrompt = getT("ai_summary_system_prompt");
	
			// Request an lokales LLM via io.Dos oder io.HTTP
			// Wir erzwingen die Kürze durch das Prompt-Design
			String aiResponse = streamFromOllama(new AIJob( systemPrompt +"\\n"+ rawtext, cf.filter,cf.negative) , false) ; 
	
			// Loopback zum WikiPlugin (Goal c)
			pva.AsyncSendIntent(new Command("AISTREAMER", cf.filter, "", cf.negative ), aiResponse);
			return true;
		}

		if ( cf.command.equals("AICLEARHISTORY")) {
			aimsgs.clear();
			say( getT( "AIHISTORYCLEARED" ) , cf.filter,cf.negative);
			return true;
		}
		return false;
	}

	private void triggerStop() {
		this.abortFlag = true;
		this.jobQueue.clear();
		if (currentConn != null) currentConn.disconnect(); // Kappe Ollama-Stream
		log(getT("AIS_EMERG_STOP")); 
		vars.put("status", "idle");
	}

	public void run() {
		// Speaker-Thread starten (Consumer)
		
		while (!isInterrupted()) {
			try {
				AIJob job = jobQueue.take();
			   	log("AISTreamer:run():"+ job.text +" > "+ job.returnIntent +" > "+ job.extra );
				this.abortFlag = false;
				streamFromOllama( job ); 
			} catch (InterruptedException e) { break; }
		}
	}

	private String streamFromOllama(AIJob prompt) {
		return streamFromOllama( prompt, true , "user", pva.config.get("ai","model") );
	}
	private String streamFromOllama(AIJob prompt, boolean sayit) {
		return streamFromOllama( prompt, sayit , "user", pva.config.get("ai","model") );
	}
	private String streamFromOllama(AIJob job, boolean sayit, String role,String model) {

		String prompt = job.text;
		
		vars.put("status", "streaming");
		StringBuilder sentenceBuf = new StringBuilder();
		StringBuilder entireBuf = new StringBuilder();
		
		try {
		
			aimsgs.addMessage(new AIMessage(role, model, Tools.filterAIThinking( prompt ) ));
		
			URL url = new URL("http://" + pva.config.get("ai", "host") + ":" + pva.config.get("ai", "port") + "/api/chat");
			currentConn = (HttpURLConnection) url.openConnection();
			currentConn.setRequestMethod("POST");
			currentConn.setDoOutput(true);
			currentConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			currentConn.setRequestProperty("Connection", "keep-alive");
			currentConn.setRequestProperty("User-Agent","PVA/latest");

			String payload = "{}";
			
			if ( job.extra.matches("^[A-Za-z0-9+/]+={0,2}$") && job.extra.length() % 4 == 0 ) {
				// Könnte Base64 sein
				payload = "{\"model\":\""+ model +"\",\"stream\": true,\"messages\":"+ 
					  "[{\"role\": \"user\",\"content\":\""+ prompt +"\",\"images\": [ \""+ job.extra +"\"]}]}";
							
			} else  payload = "{\"model\":\""+ pva.config.get("ai", "model") +"\",\"stream\":true,\"messages\":"+ aimsgs.toJSON() +"}";
	
//			log("AISTreamer:streamFromOllama():"+ payload );
	
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
		
					// log("AISTreamer:streamFromOllama(): token = "+ token );
			
					if (token != null) {
						sentenceBuf.append(token);
//						log("AISTreamer:streamFromOllama(): sentenceBuf = "+ sentenceBuf.toString() );
						if (isEndOfSentence(token,sentenceBuf)) {
							// log("AISTreamer:streamFromOllama(): sentenceBuf = "+ new String( sentenceBuf.toString() ) );
							String teilstring =  sentenceBuf.toString().trim();
							json.append( teilstring +"\\n" );
							if ( sayit && !teilstring.startsWith("#") ) say( teilstring,job.returnIntent,job.extra);
							entireBuf.append( sentenceBuf.toString().trim() );
							sentenceBuf.setLength(0);
						}
					}
				}
			}
			
			aimsgs.addMessage(new AIMessage("assistant", pva.config.get("ai","model"), Tools.filterAIThinking( json.toString() ).trim() ));
			
//			log("message="+ aimsgs.toJSON());
	
		} catch (Exception e) {
			log(getT("AIS_STREAM_ERR") + e.getMessage());
		} finally {
			if (sentenceBuf.length() > 0) {
				String teilstring = sentenceBuf.toString();
				if ( sayit && !teilstring.startsWith("#") ) say( teilstring,job.returnIntent,job.extra);
				entireBuf.append( teilstring.trim() );
			}
			vars.put("status", "idle");
		}
		String ret = entireBuf.toString().trim();
		if ( ret.startsWith("#") ) {
		
			/*
				#INTENT#DATA
				#INTENT#EXTRADATA#DATA
				#INTENT#EXTRADATA#INTENT#DATA
			*/
		
			ret = (ret+" ").substring(1);
			log("ret=|"+ret+"|");
		
			String[] args = ret.split("#");
			if ( args.length == 2 ) {
				pva.AsyncSendIntent(new Command("AISTREAMER", args[0], "AI_RETURN_DATA" , ""), args[1]);
			} else if ( args.length == 3 ) {
				pva.AsyncSendIntent(new Command("AISTREAMER", args[0], "AI_RETURN_DATA" , args[1]), args[2]);
			} else if ( args.length == 4 ) {
				pva.AsyncSendIntent(new Command("AISTREAMER", args[0], args[2] , args[1]), args[3]);
			} else {
				pva.AsyncSendIntent(new Command("AISTREAMER", "AI_RETURN_DATA" ,"", ""), "#ERROR#could not parse output.");
				log("NO CLUE WHAT THE LLM WANTED TO SAY WITH THIS: "+ ret );
			}
			return "";
		}
		return entireBuf.toString().trim();
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
		
			String[] checkboxAlpha = new String[]{" ca.","(ca."," v."," dr."," chr."," bzw."," aso.","z.B.","z.b."};
			
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
