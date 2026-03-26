package plugins.files;

import plugins.Plugin;
import server.PVA;
import hash.StringHash;
import data.Command;
import java.util.concurrent.LinkedBlockingDeque;

public class Sound extends Plugin {

	PVA pva;
	
	public static class SoundJob {
	    public final String text;
	    public final String returnIntent;
	    public final String extra;
	    public SoundJob(String text, String returnIntent, String extra) {
	        this.text = text;
	        this.extra = extra;
	        this.returnIntent = returnIntent;
	    }
	}
	
	private final LinkedBlockingDeque<SoundJob> SoundQueue = new LinkedBlockingDeque<>(50);
	private String name = "SoundPlugin";
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
		return new String[]{"PLAYSOUND","PLAYSOUNDASYNC","STOPSOUND","PLAYSOUNDASYNC_PRIO"};
	}

	public boolean execute(Command cf, String rawtext) {

		if (cf.command.equals("PLAYSOUNDASYNC")) {
			SoundQueue.offer( new SoundJob(rawtext,cf.filter,cf.negative) );
			return true;
		}
		
		if (cf.command.equals("PLAYSOUNDASYNC_PRIO")) {
			// Schiebt den Text sofort ganz nach vorne an Position 0
			// This solves the ASYNC output issue, if someone uses say() the old fashion way or SPEAK to prio it's content.
			SoundQueue.addFirst( new SoundJob(rawtext,cf.filter,cf.negative) ); 
			return true;
		}
		
		if (cf.command.equals("PLAYSOUND")) {
			try {
				new Thread(() -> {
					dos.readPipe("play "+rawtext);
				}).start();
			} catch (Exception e) {
				// nothing we can do except reporting it 
				log("Soundplugin: play() failed on: "+ rawtext);
			}
			return true;
		}
		if (cf.command.equals("STOPSOUND")) {
			this.SoundQueue.clear();
			dos.readPipe("killall -9 play"); // EMERGC stop
			log(getT("SoundPlugin: EMERG_STOP")); 
			vars.put("status", "idle");
			return true;
		}
		return false;
	}

	public void run() {
		while ( !isInterrupted() ) {
			try {
				SoundJob job = SoundQueue.take();// Blockiert bis Text da ist
				String s = job.text;
				s = s.replaceAll("^\\* ", ""); // Listen-Sternchen am Anfang weg
				log("Soundplugin:playerLoop(): "+ s );
				if (!abortFlag) {
					vars.put("status", "playing");
					dos.readPipe("play "+s);
					vars.put("status", "idle");
				}
			} catch (Exception e) {
				vars.put("status", "idle");
			}
		}
	}
}
