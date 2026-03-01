
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;

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
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled cf.commands
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
	public boolean execute(Command cf, String rawtext) { 
		try {
			if ( cf.command.equals("HEALTHREPORT") ) {
				
				// selfstatusreport
				
				Float f = Float.parseFloat( dos.readPipe("cat /proc/loadavg").split(" ")[0].trim() );
				long  c = Long.parseLong( dos.readPipe("grep -c processor /proc/cpuinfo").trim() );

				// Thanks that you cared for me +1 MOOD
				pva.AsyncSendIntent(new Command("LOADTASK", "MOOD_IMPULS", "", ""), "1");
					
				if ( f < 1 ) {
						say( getT( "HEALTHRESPONSENOTHINGTODO"), cf.filter,cf.negative );
				} else if ( f > c ) {
						say( getT( "HEALTHRESPONSEHELPHELP"), cf.filter,cf.negative  );
				} else if ( f > ( c/2 ) ) {
						say( getT( "HEALTHRESPONSESOLALA"), cf.filter,cf.negative  );
				} else {
						say( getT( "HEALTHRESPONSENORMAL"), cf.filter,cf.negative  );
				}	
	
			} else if ( cf.command.equals("SILENCELOADWARNING") ) {
				log("schalte warnung aus");
				setVar("silence","yes");
				// WTF.. you do not care for me? : -25 MOOD
				pva.AsyncSendIntent(new Command("LOADTASK", "MOOD_IMPULS", "", ""), "-15");
				say( getT( "HEALTHRESPONSETURNEDOFF"), cf.filter,cf.negative  );
	
			} else if ( cf.command.equals("UNSILENCELOADWARNING") ) {
	
				log("schalte warnung ein");
				setVar("silence","no");
				// Finally : -15 MOOD
				pva.AsyncSendIntent(new Command("LOADTASK", "MOOD_IMPULS", "", ""), "-15");
				say( getT( "HEALTHRESPONSETURNEDON"), cf.filter,cf.negative );
	
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
	
		if ( cf.command.equals("GENERALSILENCE") ) {

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
						// I'm meltung: -25 MOOD
						pva.AsyncSendIntent(new Command("LOADTASK", "MOOD_IMPULS", "", ""), "-25");
						// we use SPEAK - Intent here because we wanne have it now, not in 20 Seconds, when the queue cleared.
						
						if ( vars.get("silence").equals("no") ) pva.AsyncSendIntent(new Command("LOADTASK", "SPEAK", "", ""), getT("HEALTHRESPONSEHELPHELP") );
						time = 0;
						inform = true;
					
				} else if ( f > ( c*80/100) && ( lastState < ( c*80/100) || time > 60 ) ) {
						if ( vars.get("silence").equals("no") ) pva.AsyncSendIntent(new Command("LOADTASK", "SPEAK", "", ""), getT("HEALTHRESPONSE80P") );
						time = 0;
						inform = true;
				} else if ( f < ( c/2 ) && lastState > (c/2) && inform ) {
						if ( vars.get("silence").equals("no") ) pva.AsyncSendIntent(new Command("LOADTASK", "SPEAK", "", ""), getT("HEALTHRESPONSEOK") );
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
