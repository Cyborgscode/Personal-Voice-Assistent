
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;

public class LoadTask extends Plugin {

	private String getFilter = ":silence:";
	private String setFilter = ":silence:";
	private TwoKeyHash validValues = new TwoKeyHash(); // this way we get multiply options for an argument 
	
	public LoadTask() {
		System.out.println("Class LoadTask Constructor called");
	}
	
	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread","yes"); // Tells the loader to run run() 
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled actioncodes
		info.put("name","LoadTask"); // be nice, create a unique name
		vars.put("silence","no");
		validValues.put("silence","yes","ok");
		validValues.put("silence","no","ok");
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
			return true;
		}
		return false;
	}
	
	// getActionCodes() should return an empty String[], if we do not handle Actions

	public String[] getActionCodes() {  return "HEALTHREPORT:SILENCELOADWARNING:UNSILENCELOADWARNING".split(":"); };
	public boolean execute(String actioncode, String rawtext) { 
		try {
			if ( actioncode.equals("HEALTHREPORT") ) {
				
				// selfstatusreport
				
				Float f = Float.parseFloat( dos.readPipe("cat /proc/loadavg").split(" ")[0].trim() );
				long  c = Long.parseLong( dos.readPipe("grep -c processor /proc/cpuinfo").trim() );
					
				if ( f < 1 ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSENOTHINGTODO") );
				} else if ( f > c ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSEHELPHELP") );
				} else if ( f > ( c/2 ) ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSESOLALA") );
				} else {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSENORMAL") );
				}	
			
			} else if ( actioncode.equals("SILENCELOADWARNING") ) {
				log("schalte warnung aus");
				setVar("silence","yes");
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSETURNEDOFF") );
	
			} else if ( actioncode.equals("UNSILENCELOADWARNING") ) {
	
				log("schalte warnung ein");
				setVar("silence","no");
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSETURNEDON") );
	
			} else return false; // if we did not handle this code, tell the app, as it can try another plugin.
	
			return true; // ok, we handled it and the app can stop searching for plugins to handle it.
		}  catch (Exception localException) {
			// there is nothing we can do, if say() can't execute something, but we should report it:
			localException.printStackTrace();
			return true; // we can't fail if we do not handle the request and say() is the only reason to fail here.
		}

		/* EXAMPLE:
		
		HINT: if we handle codes, that are supported by multiply plugins i.e. a generalized silence cmd, the handling code returns "false";
		      if we ever need a more complex reporting strategie, we need to refactor this execute() system.
	
		if ( actioncode.equals("GENERALSILENCE") ) {

			setVar("silence","yes");
			return false;
			
		} else ...
		
		*/
	};

	Float lastState = Float.parseFloat("0");
	
	public void run() {
		try {
			String load = "";
			int time = 0;
			boolean inform = false;

			while ( true ) {
				if (isInterrupted()) {
					return;
				}

				Float f = Float.parseFloat( dos.readPipe("cat /proc/loadavg").split(" ")[0].trim() );
				long  c = Long.parseLong( dos.readPipe("grep -c processor /proc/cpuinfo").trim() );
				
//				log("load="+f+" laststate="+lastState+" time="+time);
				
				if ( f > c && ( lastState < c || time > 60 ) ) {
						if ( vars.get("silence").equals("no") ) pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSEHELPHELP") );
						time = 0;
						inform = true;
					
				} else if ( f > ( c*80/100) && ( lastState < ( c*80/100) || time > 60 ) ) {
						if ( vars.get("silence").equals("no") ) pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSE80P") );
						time = 0;
						inform = true;
				} else if ( f < ( c/2 ) && lastState > (c/2) && inform ) {
						if ( vars.get("silence").equals("no") ) pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSEOK") );
						inform = false;
				}	

				lastState = f;
				time++;
				sleep(1000L);

			}
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
