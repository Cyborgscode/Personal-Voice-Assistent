package plugins.files;

import plugins.Plugin;
import server.PVA;
import hash.StringHash;
import data.Command;
import java.util.concurrent.LinkedBlockingDeque;

public class Speech extends Plugin {

	PVA pva;
	
	private final LinkedBlockingDeque<String> speechQueue = new LinkedBlockingDeque<>();
	private String name = "SpeechPlugin";
	private volatile boolean abortFlag = false;
	private StringHash info = new StringHash();

	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread", "yes"); // WICHTIG: Der Vorlese-Thread
		info.put("hasCodes", "yes");
		info.put("name", name);
		vars.put("status","idle");
	}

	public StringHash getPluginInfo() { return info; }
	public String getVar(String name) { return vars.get(name); }
	public boolean setVar(String name, String value) { 
		vars.put(name, value); 
		return true; 
	}

	public String[] getActionCodes() {
		return new String[]{"ASYNCSPEAK","SPEAK","STOPSPEECH","ASYNCSPEAK_PRIO"};
	}

	public boolean execute(Command cf, String rawtext) {

		if (cf.command.equals("ASYNCSPEAK")) {
			speechQueue.offer( rawtext );
			return true;
		}
		
		if (cf.command.equals("ASYNCSPEAK_PRIO")) {
			// Schiebt den Text sofort ganz nach vorne an Position 0
			// This solves the ASYNC output issue, if someone uses say() the old fashion way or SPEAK to prio it's content.
			speechQueue.addFirst( rawtext ); 
			return true;
		}
		
		if (cf.command.equals("SPEAK")) {
			try{pva.say( rawtext );}catch(Exception e){log("Speechplugin: say() failed on: "+ rawtext);};
			return true;
		}
		if (cf.command.equals("STOPSPEECH")) {
			this.speechQueue.clear();
			dos.readPipe("killall -9 play"); // EMERGC stop
			log(getT("SPEECHPlugin: EMERG_STOP")); 
			vars.put("status", "idle");
			return true;
		}
		return false;
	}

	public void run() {
		while ( !isInterrupted() ) {
			try {
				String s = speechQueue.take(); // Blockiert bis Text da ist
				s = s.replaceAll("^\\* ", ""); // Listen-Sternchen am Anfang weg
				log("Speechplugin:speakerLoop(): "+ s );
				if (!abortFlag) {
					vars.put("status", "speaking");
					pva.say(s, true); 
					vars.put("status", "idle");
				}
			} catch (Exception e) {
				vars.put("status", "idle");
			}
		}
	}
}
