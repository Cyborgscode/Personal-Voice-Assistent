package plugins.files;

import plugins.Plugin;
import server.PVA;
import io.HTTP;
import hash.StringHash;
import data.Command;
import utils.Tools;
import java.util.Date;

public class MatrixPlugin extends Plugin {

	private String name = "MatrixPlugin";
	private StringHash vars = new StringHash();
	private String session;
	
	private String hs   = "";
	private String user = "";
	private String pass = "";
	private String deviceID = "";
	private String joinroom = "";
	private String adminid = "";
	private String sinceToken = null; // Token for incremental sync
	private long lastSave = 0;
	
	
	public void init(PVA pva) {
		this.pva = pva;
		connect();
	}

	public String getVar(String name) { 
		return vars.get(name); 
	}

	public boolean setVar(String name, String value) { 
		vars.put(name, value); 
		return true; 
	}

	public StringHash getPluginInfo() {
		StringHash info = new StringHash();
		info.put("hasThread", "yes");
		info.put("hasCodes", "yes");
		info.put("name", name);
		return info;
	}

	public String[] getActionCodes() {
		return new String[]{"MATRIX_LISTROOMS", "MATRIX_RECEIVE_LLM_ANSWERE","MATRIX_SEARCH_ROOMLIST","MATRIX_SENDMSG","MATRIX_SENDROOM","MATRIX_JOIN","MATRIX_LEAVE"};
	}

	public boolean execute(Command cf, String value) {

		String cmd = cf.command;
		String result = "";

		// 1. Informationsgewinnung mit Intent-Rückkanal
		if (cmd.equals("MATRIX_LISTROOMS")) {
			if ( cf.negative.contains("|") ) {
				String[] x = cf.negative.split("\\|");
				result = listJoinedRooms(x[0]);
			} else  result = listJoinedRooms(""); // wir wissen es nicht, oder das kam gar nicht via Matrix
			
			if (!cf.filter.isEmpty() ) {
				pva.AsyncSendIntent(name, cf.filter, result, "", cf.negative );
			} else {
				log(result);
			}
			return true;
		}

		if (cmd.equals("MATRIX_SEARCH_ROOMLIST")) {
			result = searchPublicRooms(value);
			if (!cf.filter.isEmpty() ) {
				pva.AsyncSendIntent(name, cf.filter, result, "", cf.negative );
			} else {
				log(result);
			}
			return true;
		}

		// 2. Raum-Management
		if (cmd.equals("MATRIX_JOIN")) {
			joinRoom(value);
			if (!cf.filter.isEmpty() ) {
				// Signatur: sender, intent, data
				pva.AsyncSendIntent(name, cf.filter, result, "", cf.negative );
			} 
			return true;
		}

		if (cmd.equals("MATRIX_LEAVE")) {
			leaveRoom(value);
			if (!cf.filter.isEmpty() ) {
				// Signatur: sender, intent, data
				pva.AsyncSendIntent(name, cf.filter, result, "", cf.negative );
			} 
			return true;
		}

		// 3. Messaging (Multi-Target & DM)
		if (cmd.equals("MATRIX_SENDROOM")) {
			if (value.contains("|")) {
				String[] parts = value.split("\\|", 2);
				sendMessage(parts[0], parts[1]);
			} else {
				log(name+":MATRIX_SENDROOM: ups.. no delimiter in payload: "+ value);
			}
			return true;
		}

		if (cmd.equals("MATRIX_SENDMSG")) {
			if (value.contains("|")) {
				String[] parts = value.split("\\|", 2);
				sendMessage(parts[0], parts[1]);
				return true;
			} else log(name+":MATRIX_SENDMSG: ups.. no delimiter in payload: "+ value);
		}

		if (cmd.equals("MATRIX_RECEIVE_LLM_ANSWERE")) {
			
			if ( cf.negative.equals("DEFAULT") ) {
				cf.negative = joinroom+"|";
			}
			
			if ( cf.negative.contains("|")) {
				String[] parts = cf.negative.split("\\|", 2);
				sendMessage(parts[0], "->"+ parts[1]+" "+ value);
				return true;
			} else log(name+":MATRIX_RECEIVE_LLM_ANSWERE: ups.. no delimiter in IntentExtra: "+cf.negative+" => "+ value);
		}

		return false;
	}
			
	private void connect() {
		// Lokale Variablen für bessere Übersicht im PVA-Stil
		hs   = pva.config.get("matrix", "homeserver");
		user = pva.config.get("matrix", "user");
		pass = pva.config.get("matrix", "pass");
		deviceID = pva.config.get("matrix", "device");
		joinroom = pva.config.get("matrix","joinroom");
		adminid = pva.config.get("matrix","adminid");
						
		sinceToken = pva.config.get("config","matrix_since");
		if ( sinceToken.isEmpty() ) sinceToken = null;

		// Nur fortfahren, wenn die Sektion "matrix" valide Daten liefert
		if (hs == null || user == null || pass == null) {
			log("MatrixPlugin: Configuration incomplete. Check 60-matrix.conf.");
			return;
		}

		HTTP.timeout = 31000;

		session = HTTP.postSSL( hs ,"/_matrix/client/v3/login", "{\"identifier\":{\"type\": \"m.id.user\",\"user\": \""+user+"\"},\"initial_device_display_name\":\""+ deviceID +"\",\"password\":\""+ pass +"\",\"type\": \"m.login.password\"}", "Content-Type: application/json; charset=utf-8" ); 

		if ( session.contains( "access_token" ) ) {
		
			session = Tools.zwischen( session, "access_token\":\"","\",\"");

			// {"user_id":"@carola:linuxphones.de","access_token":"#####","home_server":"linuxphones.de","device_id":"DMLTRNLNGL","well_known":{"m.homeserver":{"base_url":"https://linuxphones.de/"}}}
			
			log("Matrix session="+session);

			if ( session == null || session.isEmpty() ) {
				log("MatrixPlugin: login failed - "+ session );
				return;
			}
			

			// --- DATABASE ROW CHECK & REPAIR ---
			String myFullId = "@" + user + ":" + hs;
			String encodedId = myFullId.replace("@", "%40").replace(":", "%3A");
			String profilePath = "/_matrix/client/v3/profile/" + encodedId + "/displayname";

			// 1. Check if profile row exists
			String checkProfile = HTTP.getPageSSL(hs, profilePath, "Authorization: Bearer " + session);
			if (checkProfile.contains("M_UNKNOWN") || checkProfile.contains("M_NOT_FOUND") || checkProfile.contains("No row found") ) {
		
				log("MatrixPlugin: Profile row MISSING for " + myFullId + ". Triggering repair...");
		
				// 2. Repair by forcing a PUT request to create the missing database row
				String repairRes = HTTP.putSSL(hs, profilePath, "{\"displayname\":\"Carola\"}", 
					"Authorization: Bearer " + session + "\r\nContent-Type: application/json; charset=utf-8");
		
				log("MatrixPlugin: Repair-Result: " + repairRes);
		
			} else {
				log("MatrixPlugin: Profile row for " + myFullId + " is OK.");
			}

			
			String result = HTTP.postSSL( hs ,"/_matrix/client/v3/rooms/"+ joinroom.replace("#", "%23").replace("!", "%21").replace(":", "%3A") +"/join","{}", "Authorization: Bearer "+ session +"\r\nContent-Type: application/json; charset=utf-8");
			// log( result );
			if (result.contains("room_id") && result.contains("!")) {
			
				log("Room "+ joinroom +" joined");
			
			} else  log("Room "+ joinroom +" NOT joined:"+ result);
	
		} else {
			log("MatrixPlugin: Connection failed - " + Tools.zwischen( session, "error\":\"","\"") );
		} 
	}
	
	/**
	 * Erstellt einen direkten Chat (DM) mit einem User oder nutzt einen bestehenden.
	 * @param userId Die Matrix-ID des Zielusers (@user:server.tld).
	 * @param text Die zu sendende Nachricht.
	 */
	public String firstSendDirectMessage(String userId, String text) {
	
		log("sendDirectMessage: "+ userId +" "+ text );
	
		if (this.session == null || !userId.startsWith("@") || !userId.contains(":")) {
			log("MatrixPlugin: Invalid User-ID for DM: " + userId);
			return "";
		}
		
		// JSON händisch zusammenbauen
		String jsonBody = "{\"invite\":[\"" + userId + "\"],\"is_direct\":true,\"preset\":\"trusted_private_chat\"}";
		
		// Request an den Homeserver
		String response = HTTP.postSSL(hs, "/_matrix/client/v3/createRoom", jsonBody, 
				"Authorization: Bearer " + session + "\r\nContent-Type: application/json");

		// Room-ID extrahieren (deine effiziente Methode)
		return Tools.zwischen(response,"\"room_id\":\"","\""); // Liefert z.B. !abc:phones.de
	}

	private String escapeJSON(String text) {
		if (text == null) return "";
		return text.replace("\\", "\\\\")  // Backslashes zuerst!
	           .replace("\"", "\\\"")  // Anführungszeichen
	           .replace("\n", "\\n")   // Echte Zeilenumbrüche
	           .replace("\r", "");     // Carriage Return entfernen
	}
	public boolean sendMessage(String roomId, String text) {

		// 1. Eindeutige ID für diesen Sende-Versuch (Beschleunigt die Verarbeitung)
		String txnId = "reply_" + System.currentTimeMillis();
		
		// 2. Body bauen (JSON-String-Fu)
		String jsonBody = "{\"msgtype\":\"m.text\",\"body\":\"" + escapeJSON( text ) + "\"}";
		
		// 3. URL vorbereiten (ID encoden!)
		String encodedRoomId = roomId.replace("!", "%21").replace(":", "%3A");
		String path = "/_matrix/client/v3/rooms/" + encodedRoomId + "/send/m.room.message/" + txnId;

		// 4. Ab die Post
		String res = HTTP.putSSL(hs, path, jsonBody, "Authorization: Bearer " + session + "\r\nContent-Type: application/json");
		// log("Antwort gesendet: " + res);

		return true;	
	}

	private String encode(String id) {
		return id.replace("!", "%21").replace(":", "%3A").replace("#", "%23").replace("@", "%40");
	}

	/**
	 * Listet alle Räume auf, in denen der Bot aktuell Mitglied ist.
	 * Gibt die Liste als formatierten String an das LLM zurück.
	 */

	public String listJoinedRooms(String currentID) {
		if (session == null) return "";
	
		String res = HTTP.getPageSSL(hs, "/_matrix/client/v3/joined_rooms", "Authorization: Bearer " + session);
		String roomsRaw = Tools.zwischen(res, "\"joined_rooms\":[", "]");
	
		if (roomsRaw == null || roomsRaw.isEmpty()) return "Keine Räume gefunden.";
	
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		String[] ids = roomsRaw.replace("\"", "").split(",");
	
		for (String id : ids) {
			String cleanId = id.trim();
			if (!cleanId.isEmpty()) {
				String named = getRoomName(cleanId);
				if ( named.equals(cleanId) && cleanId.equals(currentID) ) {
					sb.append("Dieser Raum hier").append(" (").append(cleanId).append(")\n");
				} else 	sb.append(named).append(" (").append(cleanId).append(")\n");
			}
		}
		return sb.toString();
	}

	public String getRoomName(String roomId) {
		if (session == null) return roomId;
	
		// API: /rooms/{roomId}/state/m.room.name
		String path = "/_matrix/client/v3/rooms/" + encode(roomId) + "/state/m.room.name";
		String res = HTTP.getPageSSL(hs, path, "Authorization: Bearer " + session);
	
		// 1. Versuch: Expliziter Name
		String name = Tools.zwischen(res, "\"name\":\"", "\"");
		if (name != null && !name.isEmpty()) return name;
		
		// 2. Fallback: Canonical Alias (#name:server.tld)
		path = "/_matrix/client/v3/rooms/" + encode(roomId) + "/state/m.room.canonical_alias";
		res = HTTP.getPageSSL(hs, path, "Authorization: Bearer " + session);
		String alias = Tools.zwischen(res, "\"alias\":\"", "\"");
	
		return (alias != null && !alias.isEmpty()) ? alias : roomId;
	}

	public String searchPublicRooms(String query) {
		if (session == null) return "";
	
		// Matrix nutzt hierfür meist POST an /publicRooms
		String payload = "{\"filter\":{\"generic_search_term\":\"" + query + "\"},\"limit\":10}";
		String res = HTTP.postSSL(hs, "/_matrix/client/v3/publicRooms", payload, 
			"Authorization: Bearer " + session + "\r\nContent-Type: application/json");
	
		if (res.contains("chunk")) {
		// Liefert die Liste der gefundenen Räume als Roh-JSON-Chunk
			return Tools.zwischen(res, "\"chunk\":[", "]");
		}
		return ""; // Fix: "" statt null, um NPEs zu vermeiden
	}
	
	public String searchUsers(String query) {
		if (session == null || query.isEmpty()) return "";

		String path = "/_matrix/client/v3/joined_rooms";
		
		// Annahme: Dein Wrapper hat eine getSSL Methode
		String result = HTTP.getPageSSL(hs, path, "Authorization: Bearer " + session);
		
		if (result.contains(" 200 ")) {
				log("Beigetretene Räume: " + result);
				
				// Simples Extrakt-Verfahren für alle IDs in der Liste
				String roomsRaw = Tools.zwischen(result, "\"joined_rooms\":[", "]");
				return roomsRaw;
		}

		return "";
	}
	private void processSyncResponse(String response) {
		if ( response == null || response.isEmpty() ) return;

		String keyword = pva.config.get("conf", "keyword");
		// PVA-Standard: Leerer String statt NULL
		if (keyword.isEmpty()) return;

		String inviteSection = Tools.zwischen(response, "\"invite\":{", "}}"); 

		if (inviteSection != null && !inviteSection.isEmpty()) {
			int pos = 0;
			while ((pos = inviteSection.indexOf("\"!", pos)) != -1) {
				int endId = inviteSection.indexOf("\":{", pos);
				String inviteRoomId = inviteSection.substring(pos + 1, endId);

				// 1. Bereich für diesen spezifischen Invite isolieren
				int nextInvite = inviteSection.indexOf("\"!", endId);
				String inviteBlock = (nextInvite != -1) ? inviteSection.substring(endId, nextInvite) : inviteSection.substring(endId);
	
				// 2. Sender aus dem invite_state extrahieren
				// Matrix-Format: "sender":"@user:server.tld"
				String inviter = Tools.zwischen(inviteBlock, "\"sender\":\"", "\"");
	
				log("MatrixPlugin: Invite for " + inviteRoomId + " from " + inviter);

				// 3. Sofortiger Auto-Join
				if ( inviter.equals(adminid) || adminid.equals("%") ) {
					if ( isValidRoomId(inviteRoomId)) {
						if (joinRoom(inviteRoomId)) {
							sendMessage(inviteRoomId, "PVA Matrix-Node online. Hallo " + inviter);
						}
					}
				} else {
					log("MatrixPlugin: SECURITY VIOLATION - Inviter is not Admin");
				}	
				// Pointer für nächsten Invite setzen
				pos = (nextInvite != -1) ? nextInvite : inviteSection.length();
			}
		}

		String joinSection = Tools.zwischen(response, "\"join\":{", "}}"); 

		if (joinSection != null) {
			// 1. Die Room-ID finden (beginnt mit !)
			int pos = 0;
			while ((pos = joinSection.indexOf("\"!", pos)) != -1) {
				int endId = joinSection.indexOf("\":{", pos);
				// Das ist deine !RoomID:server.de
				String currentRoomId = joinSection.substring(pos + 1, endId); 
				
				// 2. Den Bereich für DIESEN Raum isolieren (bis zum nächsten Raum oder Ende)
				int nextRoom = joinSection.indexOf("\"!", endId);
				String roomBlock = (nextRoom != -1) ? joinSection.substring(endId, nextRoom) : joinSection.substring(endId);

				if (roomBlock.contains("\"m.room.encrypted\"")) {
					// E2EE DETECTED: Synapse sends encrypted blobs, but we lack the OLM-Library
					log("MatrixPlugin: E2EE ALERT in [" + currentRoomId + "] - cannot decrypt message.");
				}

				// 3. Nur in DIESEM Raum-Block nach Nachrichten suchen
				if (roomBlock.contains("\"m.room.message\"")) {
					String msg = Tools.zwischen(roomBlock, "\"body\":\"", "\"");
					String sender = Tools.zwischen(roomBlock, "\"sender\":\"", "\"");
						
					log("Raum [" + currentRoomId + "] | " + sender + ": " + msg);
						
					// Jetzt kannst du gezielt in GENAU DIESEM RAUM antworten
					if ( !sender.contains("@"+user+":"+hs) ) {
					
						msg = HTTP.fromUnicode(msg).toLowerCase();
					
						if ( ( sender.equals(adminid) && msg.contains(keyword) ) || !msg.contains(keyword) ) {
						
							try {
							
								pva.handleInput( msg, "MATRIX_RECEIVE_LLM_ANSWERE", currentRoomId+"|"+sender );
							} catch(Exception e) {
								// we can't tell it the user .. 
								sendMessage( currentRoomId, "\"Housten, we have a problem!\" "+sender+" :(");
								e.printStackTrace();
							}
						}							
						//	pva.AsyncSendIntent(new Command("MATRIX","AI_ANSWERE", "MATRIX_RECEIVE_LLM_ANSWERE", ""), currentRoomId+"|viaMATRIX-FROM:"+ sender +":msg:"+ msg );
					}
				}
				pos = (nextRoom != -1) ? nextRoom : joinSection.length();
			}		
		}
	}
	
	/**
	 * Prüft die Room-ID gegen die offizielle Matrix-Grammatik (!opaque_id:domain).
	 */
	private boolean isValidRoomId(String roomID) {
		// RegEx: Startet mit !, gefolgt von Nicht-Doppelpunkten, Doppelpunkt, Nicht-Doppelpunkten
		// Mindestlänge durch + Quantifier (mind. 1 Zeichen pro Part) gewährleistet.
		String matrixRoomPattern = "^![^:]+:[^:]+$";
		return roomID.matches(matrixRoomPattern);
	}

	/**
	 * Verlässt einen Raum dauerhaft.
	 * @param roomID Die Matrix-ID des zu verlassenden Raums (!abc:server.tld).
	 */
	public boolean leaveRoom(String roomID) {
		if (!isValidRoomId(roomID)) {
			log("MatrixPlugin: Cannot leave malformed Room-ID: " + roomID);
			return false;
		}

		// roomID encoden (! -> %21, : -> %3A)
		String encodedRoomId = roomID.replace("#", "%23").replace("!", "%21").replace(":", "%3A");
		String path = "/_matrix/client/v3/rooms/" + encodedRoomId + "/leave";
	
		String result = HTTP.postSSL(hs, path, "{}", 
			"Authorization: Bearer " + session + "\r\nContent-Type: application/json");
	
		if (result.contains(" 200 ")) {
				log("Erfolgreich aus " + roomID + " ausgetreten.");
				return true;
			} else {
				log("Fehler beim Verlassen von " + roomID + ": " + result);
		}
		return false;
	}
	
	 /*
	 * Tritt einem Raum bei. Der Server verwaltet die Mitgliedschaft.
	 * Neue Nachrichten erscheinen automatisch im nächsten regulären Sync-Batch.
	 */
	public boolean joinRoom(String roomID) {
		if (!isValidRoomId(roomID) ) {
			log("MatrixPlugin: joinroom failed -> " + roomID + " is invalid");
			return true;
		}

		String encodedRoomId = roomID.replace("!", "%21")
						 .replace(":", "%3A")
						 .replace("#", "%23");
	
		String path = "/_matrix/client/v3/join/" + encodedRoomId;

		// Matrix spec: Join via room ID or alias
		String result = HTTP.postSSL(hs, path, "{}", 
			"Authorization: Bearer " + session + "\r\nContent-Type: application/json");
	
		if (result.contains("room_id")) {
			log("MatrixPlugin: Successfully joined " + roomID);
			return true;
		} else {
		log("MatrixPlugin: Failed to join " + roomID + " | Error: " + result);
			return false;
		}
	}	

	public void run() {
		lastSave = System.currentTimeMillis();

		int timeout = 0;
		// String filter = "{\"room\":{\"ephemeral\":{\"types\":[]}},\"presence\":{\"types\":[]}}";
		// Filter-Definition: Nur Nachrichten und Status-Änderungen (Invites/Joins)
		// String filter = "{\"room\":{\"timeline\":{\"types\":[\"m.room.message\"]},\"state\":{\"types\":[\"m.room.member\"]}}}";
		String filter = "{}";

		while (!isInterrupted()) {
		
			if ( session == null || session.isEmpty() ) break;

		
			try {
				String url = "/_matrix/client/v3/sync?timeout="+timeout+"&filter=" + filter.replace("\"", "%22");
				if ( sinceToken != null && !sinceToken.isEmpty()) {
					url += "&since=" + sinceToken;
					timeout = 30000;
				}

				// log( (new Date()).toString() +": polling "+ url );

				// GET-Request (HTTP.getSSL oder ähnlich nutzen)
				String response = HTTP.getPageSSL(hs, url, "Authorization: Bearer " + session +"\r\nAccept: application/json");
				
				// log( (new Date()).toString() +": polling ende:"+ response );
				
				if (response != null && response.contains("next_batch")) {
				
					// 1. next_batch Token für den nächsten Aufruf extrahieren
					sinceToken = extractValue(response, "next_batch");
					if (response.contains("\"m.room.message\"") || response.contains("\"invite\":{")) {
						log("Neue Nachricht empfangen!");
			 			processSyncResponse(response);
			 		}
				}

			} catch (Exception e) {
				// Abbruch bei Interrupt (Shutdown), sonst Backoff bei Netzwerkfehlern
				if ( isInterrupted()) break;
				log("MatrixPlugin: Sync-Error (Host/Timeout), retrying in 10s...");
				try{sleep(10000);}catch(Exception f){};
				timeout=0;
			}
		}
		log("MatrixPlugin: Poller-Thread stopped.");
	}
	
	public static String extractValue(String json, String key) {
		// Wir suchen nach dem Muster: "key":"
		String searchPattern = "\"" + key + "\":\"";
		int start = json.indexOf(searchPattern);
	
		if (start == -1) return null; // Key nicht gefunden
	
		start += searchPattern.length();
		int end = json.indexOf("\"", start);
	
		if (end == -1) return null; // Schließendes Anführungszeichen fehlt
	
		return json.substring(start, end);
	}
}

