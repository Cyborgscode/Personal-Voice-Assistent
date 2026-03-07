package plugins.files;

import plugins.Plugin;
import server.PVA;
import hash.StringHash;
import data.Command;
import java.io.*;
import java.util.Base64;
import utils.Tools;

// Verifizierte OpenCV Imports
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

public class WatchdogPlugin extends Plugin {

	private String name = "WatchdogPlugin";
	private StringHash vars = new StringHash();
	private boolean active = false;
	private VideoCapture camera = null;
	private int brightnessThreshold = 45;
	private String prompt = "Analysiere Bild. Antworte NUR mit genau einem Keyword: 'RESULT_OWNER' (Besitzer erkannt), 'RESULT_EMPTY' (Niemand da) oder 'RESULT_ALARM' (Jede andere Person/Unbekannter). Keine Sätze, kein Punkt.";
	boolean alarmRunning = false;
	private String alarmsample = "/usr/share/evolution/sounds/default_alarm.wav";
	private String mailboxID = "";
	private String sendalarmto = "";
	private String sendalarmfrom = "";
	private String videopath = "";
	private long lastMailSent = 0;
	private volatile long lastAiAnalyze = 0; // Neu hinzufügen
	private org.opencv.videoio.VideoWriter videoWriter = null;
	private long lastAlarmDetected = 0;
	private long retainDays = 21;
		
	public void init(PVA pva) {
		this.pva = pva;
		if ( !pva.config.get("watchdog","aiprompt").isEmpty() ) this.prompt = pva.config.get("watchdog","aiprompt");
		if ( !pva.config.get("watchdog","alarmsample").isEmpty() ) this.alarmsample = pva.config.get("watchdog","alarmsample");
		if (  pva.config.get("watchdog","start").toLowerCase().equals("true") ) this.active = openCamera();
		if ( !pva.config.get("watchdog","mailbox").isEmpty() ) this.mailboxID = pva.config.get("watchdog","mailbox");
		if ( !pva.config.get("watchdog","sendalarmto").isEmpty() ) this.sendalarmto = pva.config.get("watchdog","sendalarmto");
		if ( !pva.config.get("watchdog","sendalarmfrom").isEmpty() ) this.sendalarmfrom = pva.config.get("watchdog","sendalarmfrom");
		if ( !pva.config.get("watchdog","retainDays").isEmpty() && Tools.isNumber( pva.config.get("watchdog","retainDays") ) ) this.retainDays = Long.parseLong(pva.config.get("watchdog","retainDays"));
		if ( !pva.config.get("watchdog","videopath").isEmpty() ) {
			this.videopath = pva.config.get("watchdog","videopath");
		} else  this.videopath = pva.getHome()+"/Videos"; // default to Freedesktop
	}

	public StringHash getPluginInfo() {
		StringHash info = new StringHash();
		info.put("hasThread", "yes");
		info.put("hasCodes", "yes");
		info.put("name", name);
		return info;
	}

	public boolean setVar(String name, String value) { 
		vars.put(name, value); 
		return true; 
	}

	public String getVar(String name) { 
		return vars.get(name); 
	}
	
	public String[] getActionCodes() {
		return new String[]{"WATCHDOG_START", "WATCHDOG_STOP", "WATCHDOG_AI_RESPONSE"};
	}

	public boolean execute(Command cf, String value) {
		if (cf.command.equals("WATCHDOG_START")) {
			if (camera == null || !camera.isOpened()) {
				active = openCamera();
			}
			return true;
		}
		
		if (cf.command.equals("WATCHDOG_STOP")) {
			active = false;
			alarmRunning = false;
			if (camera != null) {
				camera.release();
				camera = null;
			}
			return true;
		}

		if (cf.command.equals("WATCHDOG_AI_RESPONSE")) {
			handleAIResponse(value, cf);
			return true;
		}
		return false;
	}

	private boolean openCamera() {
		for (int i = 0; i < 6; i++) {
			log("Versuche Kamera Index: " + i);
			camera = new VideoCapture(i, org.opencv.videoio.Videoio.CAP_V4L2);
			
			if (camera.isOpened()) {
				camera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, 1920);
				camera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, 1080);
				// Korrektur: Voll qualifizierter Pfad zu VideoWriter
				camera.set(org.opencv.videoio.Videoio.CAP_PROP_FOURCC, org.opencv.videoio.VideoWriter.fourcc('M', 'J', 'P', 'G'));

				Mat testFrame = new Mat();
				// Kleiner Delay, damit der Sensor Zeit zum Einschwingen hat
				try { Thread.sleep(500); } catch (Exception e) {}
				
				if (camera.read(testFrame) && !testFrame.empty()) {
					double w = camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH);
					double h = camera.get(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT);
					log("Erfolg! Kamera " + i + " liefert Bilder: " + (int)w + "x" + (int)h);
					testFrame.release();
					return true;
				}
				
				log("Kamera " + i + " liefert keine Bilder. Nächster Index...");
				if (testFrame != null) testFrame.release();
				camera.release();
			}
		}
		return false;
	}

	private void cleanupRecords(int days) {
		java.io.File folder = new java.io.File(videopath);
		if (!folder.exists() || !folder.isDirectory()) return;

		long purgeTime = System.currentTimeMillis() - ((long)days * 24 * 60 * 60 * 1000);
		java.io.File[] files = folder.listFiles();

		if (files != null) {
			for (java.io.File f : files) {
				String n = f.getName();
				// NUR Dateien löschen, die wir selbst erzeugt haben!
				if (n.startsWith("alarm_video_") && n.endsWith(".avi")) {
					if (f.lastModified() < purgeTime) {
						if (f.delete()) {
							log("Watchdog Cleanup: " + n + " entfernt (älter als " + days + " Tage).");
						}
					}
				}
			}
		}
	}

	@Override
	public void run() {
		Mat frame = null;
		try {
			frame = new Mat();
		} catch (UnsatisfiedLinkError e) {
			log("KRITISCH: Mat konnte nicht initialisiert werden. JNI-Fehler.");
			return;
		}
				
		while (!isInterrupted()) {
			// 1. BEZIEHUNGS-RETTER: Sound nur alle 60 Sek, nicht alle 50ms!
			if (alarmRunning) {
				dos.readPipe("play \"" + alarmsample + "\"");
			}

			if (active && camera != null && camera.isOpened()) {
				if (camera.read(frame) && !frame.empty()) {
					// 2. Video-Recording (Echtzeit)
					if (alarmRunning) {
						if (videoWriter == null) {
							startVideoRecording(frame);
						}
						if (videoWriter != null && videoWriter.isOpened()) {
							videoWriter.write(frame);
						}
					}

/*
					FaceGeometry geo = new FaceGeometry(eyesArray, noseArray, mouthArray, crop);

					if (geo.valid) {
						log("Geometrie-Check: Ratio = " + geo.ratio + " | Winkel = " + geo.angle);
		
						// Hier dein Schwellenwert-Vergleich (z.B. Besitzer-Ratio ist 1.45)
						if (Math.abs(geo.ratio - 1.45) < 0.05) {
							log("Vektor-Match! Besitzer erkannt (Lokal).");
							// Optional: Trotzdem Gemma 3 fragen für "Lebender-Mensch-Check"
						}
					}
*/

					// 3. KI-Analyse getaktet auslagern
					long currentTime = System.currentTimeMillis();
					if (currentTime - lastAiAnalyze > 4000) {
						lastAiAnalyze = currentTime;
						final Mat aiFrame = frame.clone();

						new Thread(() -> {
							try {
								// Helligkeit über alle Kanäle (B,G,R) mitteln
								Scalar mean = Core.mean(aiFrame);
								double avgB = (mean.val[0] + mean.val[1] + mean.val[2]) / 3.0;

								if (avgB < brightnessThreshold) {
									log("Watchdog: Zu dunkel (" + (int)avgB + ") - Skip.");
								} else {
									Mat resizedFrame = new Mat();
									double scale = 640.0 / aiFrame.cols();
									Imgproc.resize(aiFrame, resizedFrame, new org.opencv.core.Size(640, (int)(aiFrame.rows() * scale)));

									MatOfByte buf = new MatOfByte();
									Imgcodecs.imencode(".jpg", resizedFrame, buf);
									String base64 = Base64.getEncoder().encodeToString(buf.toArray());
									
									// JSON-Safe Senden (escaped Anführungszeichen)
									pva.AsyncSendIntent(new Command("WATCHDOGPLUGIN", "AI_VISION_ANALYZE", "WATCHDOG_AI_RESPONSE", base64), prompt.replace("\"", "\\\""));

									resizedFrame.release();
									buf.release();
								}
							} catch (Exception e) {
								log("AI Thread Error: " + e.getMessage());
							} finally {
								aiFrame.release();
							}
						}).start();
					}
				}
				try { Thread.sleep(50); } catch (Exception e) {}
			} else {
				try { Thread.sleep(2000); } catch (Exception e) {}
			}
		}
		if (frame != null) frame.release();
	}


	
	private void handleAIResponse(String aiResult, Command cf) {
		String res = aiResult.toUpperCase();
		
		if (!alarmRunning && res.contains("RESULT_ALARM")) {
			// ... (Sounds & Stimmung wie bisher) ...

			say(getT("WATCHDOG_ALARM_REPORT").replace("<REASON>", aiResult), cf.filter, "1");

			// Video-Aufnahme initialisieren
			alarmRunning = true;
			lastAlarmDetected = System.currentTimeMillis();

			// Mailbox-Objekt über commonname suchen
			if (!mailboxID.isEmpty() && !sendalarmto.isEmpty()) {
				data.MailboxData targetMd = null;

				// Deine gewünschte Schleifen-Logik
				if (pva.mailboxes.size() > 0) {
					for (int i = 0; i < pva.mailboxes.size(); i++) {
						data.MailboxData m = pva.mailboxes.get(i);
						// Abgleich: Entspricht der commonname unserer mailboxID?
						if (m.commonname.equals(mailboxID)) {
							targetMd = m;
							break;
						}
					}
				}

				if (targetMd != null) {
					sendAlarmMail(cf.negative, targetMd);
				} else {
					log("Watchdog: Mailbox mit commonname '" + mailboxID + "' nicht in pva.mailboxes gefunden.");
				}
			}
				
		}
		
		// STOP-Logik mit Nachlauf: Nur stoppen, wenn seit 10 Sek. kein Alarm mehr kam
		if (alarmRunning && res.contains("RESULT_EMPTY")) {
			if (System.currentTimeMillis() - lastAlarmDetected > 60000) { 
				alarmRunning = false;
				stopVideoRecording();
				log("Watchdog: Alarm beendet, Video gespeichert.");
			} else {
				log("Watchdog: Warte auf Nachlaufzeit (Buffer)...");
			}
		}		
	}
	
	private void startVideoRecording(Mat referenceFrame) {
		if (videoWriter != null) return;
		
		java.io.File dir = new java.io.File("records");
		if (!dir.exists()) dir.mkdirs();

		String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
		String fileName = videopath+"/alarm_video_" + timestamp + ".avi";
		
		// WICHTIG: Nicht die Kamera fragen, sondern den tatsächlichen Frame!
		int w = referenceFrame.cols();
		int h = referenceFrame.rows();
		
		if (w <= 0 || h <= 0) { w = 1920; h = 1080; } // Hard-Fallback
		
		org.opencv.core.Size frameSize = new org.opencv.core.Size(w, h);
		int fourcc = org.opencv.videoio.VideoWriter.fourcc('M', 'J', 'P', 'G');
		
		// CAP_FFMPEG erzwingen
		videoWriter = new org.opencv.videoio.VideoWriter(fileName, 
			org.opencv.videoio.Videoio.CAP_FFMPEG, fourcc, 15.0, frameSize, true);
		
		if (videoWriter.isOpened()) {
			log("Watchdog: Video gestartet (" + w + "x" + h + ") -> " + fileName);
		} else {
			log("KRITISCH: VideoWriter konnte nicht öffnen!");
			videoWriter = null;
		}
	}

	private void stopVideoRecording() {
		if (videoWriter != null) {
			videoWriter.release();
			videoWriter = null;
			log("Watchdog: Videoaufnahme beendet.");
		}
	}
		
	private void sendAlarmMail(String base64, data.MailboxData md) {
		// Cooldown 5 Min
		if (System.currentTimeMillis() - lastMailSent < 300000) return;
		lastMailSent = System.currentTimeMillis();

		new Thread(() -> {
			try {
				log("Watchdog: SMTP-Versand via " + md.servername + " [" + md.commonname + "]");
				java.util.Properties props = new java.util.Properties();
				
				int smtpPort = (md.port == 143) ? 25 : md.port; 
				props.put("mail.smtp.host", md.servername);
				props.put("mail.smtp.port", String.valueOf(smtpPort));
				props.put("mail.smtp.auth", "true");
				props.put("mail.smtp.starttls.enable", md.secure ? "true" : "false");
				if (smtpPort == 465) props.put("mail.smtp.ssl.enable", "true");

				javax.mail.Session session = javax.mail.Session.getInstance(props, new javax.mail.Authenticator() {
					protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
						return new javax.mail.PasswordAuthentication(md.username, md.password);
					}
				});

				javax.mail.internet.MimeMessage msg = new javax.mail.internet.MimeMessage(session);
				
				// Nutzung von sendalarmfrom als Absender
				String fromAddr = (sendalarmfrom != null && !sendalarmfrom.isEmpty()) ? sendalarmfrom : md.username;
				msg.setFrom(new javax.mail.internet.InternetAddress(fromAddr, md.commonname));
				
				msg.setRecipient(javax.mail.Message.RecipientType.TO, new javax.mail.internet.InternetAddress(sendalarmto));
				msg.setSubject("Haus-KI: Wachhund Alarm!");

				String html = "<h2>Bewegung erkannt!</h2><p>Zeit: " + new java.util.Date() + "</p>" +
					      "<img src=\"data:image/jpeg;base64," + base64 + "\" width=\"640\" />";
				msg.setContent(html, "text/html; charset=utf-8");

				javax.mail.Transport.send(msg);
				log("Watchdog: E-Mail von " + fromAddr + " an " + sendalarmto + " verschickt.");
			} catch (Exception e) {
				log("Watchdog: Mail-Fehler -> " + e.getMessage());
			}
		}).start();
	}
	
}

