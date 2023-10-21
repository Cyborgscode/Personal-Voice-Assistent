
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import java.util.Date;
import data.Command;

public class SpeechGuard extends Plugin {

	private String getFilter = ":cantalk:";
	private String setFilter = ":cantalk:";
	private TwoKeyHash validValues = new TwoKeyHash(); 
	
	public SpeechGuard() {
		System.out.println("Class SpeechGuard Constructor called");
	}
	
	public void init(PVA pva) {
		this.pva = pva;
		
		// init and allow speechresponse
		pva.config.put("conf","cantalk","yes");

		// init Plugin
		
		info.put("hasThread","yes"); 
		info.put("hasCodes","yes");  
		info.put("name","SpeechGuard"); 
		vars.put("cantalk","yes");
		validValues.put("cantalk","yes","ok");
		validValues.put("cantalk","no","ok");
	}
	
	public StringHash getPluginInfo() {
		return this.info;		
	}
	public String  getVar(String name) {
		if ( getFilter.contains(":"+name+":") )
			return vars.get(name);
		return "";
	} 
	public boolean setVar(String name,String value) {
		if ( getFilter.contains(":"+name+":") && validValues.get(name,value).equals("ok")  )  {
			vars.put(name,value);
			if ( name.equals("cantalk") ) {
				if ( value.equals("yes") ) {
					time = 0;
					pva.config.put("conf","cantalk","yes");
					vars.put("cantalk","yes");
				
				} else {
					time = 	(new Date()).getTime()/1000 + shutUpFor;
					pva.config.put("conf","cantalk","no");
					vars.put("cantalk","no");
				}
			}
			return true;
		}
		return false;
	}

	private final long shutUpFor = 600;
	private	long time = 0;

	public String[] getActionCodes() {  return "SHUTUP:TALKTOME".split(":"); };
	public boolean execute(Command cf, String rawtext) { 
		try {
			if ( cf.command.equals("SHUTUP") ) {
				log("schalte sprache aus");
				pva.config.put("conf","cantalk","no");
				vars.put("cantalk","no");
				time = (new Date()).getTime()/1000 + shutUpFor;
			} else	if ( cf.command.equals("TALKTOME") ) {
				log("schalte sprache ein");
				pva.config.put("conf","cantalk","yes");
				vars.put("cantalk","yes");
				time = 0;
			} else return false;

	
			return true; 
		}  catch (Exception localException) {
			localException.printStackTrace();
			return true; 
		}

	};

	public void run() {
	
		try {
			boolean inform = false;
			Long now = (new Date()).getTime()/1000;
			
			TwoKeyHash warned = new TwoKeyHash();

			while ( true ) {
				if (isInterrupted()) {
					return;
				}
				
				if ( now > time && time != 0) {
					time = 0;
					pva.config.put("conf","cantalk","yes");
					vars.put("cantalk","yes");
				}
				sleep(1000L);
				now += 1;
			}
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
