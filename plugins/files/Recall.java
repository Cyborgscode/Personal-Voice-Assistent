package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;
import java.io.File;

public class Recall extends Plugin {

	/*
		 THIS PLUGIN NEEDS:
		 
		 https://github.com/Cyborgscode/Linux-Recall-in-bash
		 
		 Globally installed for all users or for a single user only, does not matter.
		 
	*/
	

	private String getFilter = ":";
	private String setFilter = ":";
	private TwoKeyHash validValues = new TwoKeyHash(); // this way we get multiply options for an argument 
	
	public Recall() {
		System.out.println("Class Recall Constructor called");
	}
	
	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread","no"); // Tells the loader to run run() 
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled cf.commands
		info.put("name","Recall"); // be nice, create a unique name
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

	public String[] getActionCodes() {  return "RECALLSEARCH".split(":"); };
	public boolean execute(Command cf, String rawtext) { 
		try {
			if ( cf.command.equals("RECALLSEARCH") ) {

				String subtext = rawtext.trim();
				
				// System.out.println("Recall Subtext="+subtext);
				
				if ( subtext.isEmpty() ) {
					// System.out.println("Recall parseerror");
				
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "RECALLPARSEERROR") );
					return true;
				}
		
				if ( ! dos.fileExists( pva.getHome()+"/.config/recall/path" ) ) {
					// System.out.println("Recall not installed: "+ pva.getHome()+"/.config/recall/path");
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "RECALLNOTINSTALLED") );
					return true;
				}

				String pathname = dos.readFile( pva.getHome()+"/.config/recall/path" ).trim();
				if ( !pathname.endsWith("/") ) {
					pathname += "/";
				}

				File path = new File( pathname );
				
				// System.out.println("Recall Path="+ pathname ) );
				
		                File[] entries = path.listFiles();
		                
       				// System.out.println("Recall entries="+ entries.length );
		       	        if ( entries != null && entries.length > 2 ) {
					// System.out.println("Recall Call recall-pva "+subtext);
					dos.readPipe("recall-pva '"+ subtext +"'");
				
				} else {
					// System.out.println("Recall NODATA");
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "RECALLNODATA") );
					return true;
				}
			
			} else return false; // if we did not handle this code, tell the app, as it can try another plugin.
	
			return true; // ok, we handled it and the app can stop searching for plugins to handle it.
		}  catch (Exception localException) {
			// there is nothing we can do, if say() can't execute something, but we should report it:
			localException.printStackTrace();
			return true; // we can't fail if we do not handle the request and say() is the only reason to fail here.
		}

	};

	Float lastState = Float.parseFloat("0");
	
	public void run() {
		// Not needed in this plugin.
	}
}
