package plugins.files;

import plugins.Plugin;
import server.PVA;
import io.HTTP;
import hash.StringHash;
import data.Command;
import utils.Tools;

public class ServerWatchPlugin extends Plugin {

	private String name = "ServerWatchPlugin";
	private StringHash vars = new StringHash();
	private String targets = "";
	private int interval = 300000;
	private String lastAlarms = ""; // Merker gegen Spam

	public void init(PVA pva) {
		this.pva = pva;
		targets = pva.config.get("serverwatch", "hosts");
		String confInt = pva.config.get("serverwatch", "interval");
		if (confInt != null && !confInt.isEmpty()) {
			try {
				// Sicherstellen, dass es wirklich eine Zahl ist
				this.interval = Integer.parseInt(confInt.trim());
			
				// Plausibilitäts-Check: Nicht unter 10 Sek pollen (Server-Schutz)
				if (this.interval < 10000) this.interval = 10000;
				
			} catch (NumberFormatException nfe) {
				log(name + ": WARNING - Invalid interval '" + confInt + "'. Using default 5min.");
				this.interval = 300000; // 5 Minuten Fallback
			}
		} else {
			this.interval = 300000; // Default wenn leer
		}		
		// Der Core startet den Thread (run) automatisch nach der Init-Phase
		log(name + " geladen. Monitoring aktiv.");
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
		return new String[]{"SERVERWATCH_CHECK", "SERVERWATCH_STATUS"};
	}

	public boolean execute(Command cf, String value) {
		if (cf.command.equals("SERVERWATCH_STATUS")) {
			String status = "Monitoring: " + targets + " | Letzter Check: " + new java.util.Date().toString();
			pva.AsyncSendIntent(name, cf.filter, status);
			return true;
		}
		return false;
	}
	
	private boolean isReachable(String target) {
		String host = target;
		int port = 443; // Default
	
		// 1. Port extrahieren, falls vorhanden (z.B. server.de:22)
		if (target.contains(":")) {
			String[] parts = target.split(":");
			host = parts[0];
			try {
				port = Integer.parseInt(parts[1]);
			} catch (Exception e) { port = 443; }
		}
	
		// 2. TCP-Socket Check (Universal für SSH, Mail, DB, Web)
		try (java.net.Socket socket = new java.net.Socket()) {
			// Kurzer Connect-Versuch (5 Sek Timeout)
			socket.connect(new java.net.InetSocketAddress(host, port), 5000);
			return true; // Port ist offen!
		} catch (java.io.IOException e) {
			log(name + ": Port " + port + " auf " + host + " dicht! -> " + e.getMessage());
			return false; // Down oder Firewall
		}
	}

	public void run() {
		while (!isInterrupted()) {
			if (targets.isEmpty()) {
				try { sleep(60000); } catch(Exception e) {}
				continue;
			}

			String[] hosts = targets.split(",");
			for (String host : hosts) {
				host = host.trim();
				if (host.isEmpty()) continue;

				boolean online = isReachable(host); // Die Methode von vorhin
    
				    if (!online) {
				        if (!lastAlarms.contains(host)) {
			            sendAlarm(host, "PORT CLOSED/TIMEOUT");
			            lastAlarms += host + "|";
        				}
				    } else if (lastAlarms.contains(host)) {
        				lastAlarms = lastAlarms.replace(host + "|", "");
				        sendAlarm(host, "RECOVERY: Port open");
				    }				
    			}

			try {
				sleep(interval);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void sendAlarm(String host, String reason) {
		String msg = "🖥️ ServerWatch [" + host + "]: " + (reason.contains("RECOVERY") ? "✅ Wieder erreichbar." : "⚠️ DOWN! (" + reason + ")");
		String targetRoom = pva.config.get("matrix", "joinroom");
		
		// Über Matrix rausfeuern
		pva.AsyncSendIntent(name, "MATRIX_SENDROOM", targetRoom + "|" + msg);
		
		targetRoom = pva.config.get("matrix", "alertroom");

		if ( !targetRoom.isEmpty() ) 
			pva.AsyncSendIntent(name, "MATRIX_SENDROOM", targetRoom + "|" + msg);

	}
}

