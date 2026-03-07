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
		
		// 1. Matrix-Benachrichtigung
		String targetRoom = pva.config.get("matrix", "joinroom");
		pva.AsyncSendIntent(name, "MATRIX_SENDROOM", targetRoom + "|" + msg);
		
		String alertRoom = pva.config.get("matrix", "alertroom");
		if (!alertRoom.isEmpty()) {
			pva.AsyncSendIntent(name, "MATRIX_SENDROOM", alertRoom + "|" + msg);
		}

		// 2. E-Mail an Admin (Nutzt ServerWatch-Config für Empfänger)
		String adminMail = pva.config.get("serverwatch", "adminmail");
		if (adminMail != null && !adminMail.isEmpty()) {
			sendEmail(adminMail, "ServerWatch Alarm: " + host, msg);
		}
	}

	private void sendEmail(String to, String subject, String body) {
		if (to == null || to.isEmpty() || pva.mailboxes == null) return;

		new Thread(() -> {
			try {
				String mID = pva.config.get("serverwatch", "mailbox");
				if (mID == null || mID.isEmpty()) return;

				// 1. Direktzugriff: Wir suchen die EINE Box
				data.MailboxData md = pva.mailboxes.stream()
					.filter(m -> m.commonname.equals(mID))
					.findFirst()
					.orElse(null);

				// 2. Wenn der Admin gepennt hat: Raus hier
				if (md == null || md.servername == null || md.servername.isEmpty()) return;

				java.util.Properties props = new java.util.Properties();
				int smtpPort = (md.port == 143 || md.port <= 0) ? 587 : md.port;
				
				props.put("mail.smtp.host", md.servername);
				props.put("mail.smtp.port", String.valueOf(smtpPort));
				props.put("mail.smtp.auth", "true");
				props.put("mail.smtp.starttls.enable", md.secure ? "true" : "false");
				if (smtpPort == 465) props.put("mail.smtp.ssl.enable", "true");

				final data.MailboxData fMd = md;
				javax.mail.Session session = javax.mail.Session.getInstance(props, new javax.mail.Authenticator() {
					protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
						return new javax.mail.PasswordAuthentication(fMd.username, fMd.password);
					}
				});

				javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
				String fromAddr = pva.config.get("serverwatch", "sendalarmfrom");
				String sender = (fromAddr != null && !fromAddr.isEmpty()) ? fromAddr : md.username;
				
				msg.setFrom(new javax.mail.internet.InternetAddress(sender, "PVA ServerWatch"));
				msg.setRecipient(javax.mail.Message.RecipientType.TO, new javax.mail.internet.InternetAddress(to));
				msg.setSubject(subject);
				msg.setText(body, "utf-8");

				javax.mail.Transport.send(msg);
				log(name + ": Alarm-Mail an " + to + " verschickt.");
			} catch (Exception e) {
				log(name + ": SMTP-Abbruch -> " + e.getMessage());
			}
		}).start();
	}
}

