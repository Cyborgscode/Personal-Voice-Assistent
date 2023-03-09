
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import java.util.Date;

public class Netflix extends Plugin {

	private String getFilter = ":fullscreen:playing:paused:";
	private String setFilter = "";
	private TwoKeyHash validValues = new TwoKeyHash(); 
	
	public Netflix() {
		System.out.println("Class Netflix Constructor called");
	}
	
	Position pos;
	
	public void init(PVA pva) {
		this.pva = pva;
		
		// init Plugin
		pos = new Position(vars,pva);
		pos.offset = Long.parseLong( pva.config.get("netflix","offset") );
		
		info.put("hasThread","no"); 
		info.put("hasCodes","yes");  
		info.put("name","Netflix"); 
		vars.put("fullscreen","no");
		vars.put("playing","no");
		vars.put("paused","no");
		
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
		log("Netflix dummy setvar("+name+","+value+")");
		return true;
	}
	

	final boolean wait = true;
	
	public String[] getActionCodes() {  return "NETFLIXHOME:NETFLIXMYLIST:NETFLIXSEARCH:NETFLIXRETURN:NETFLIXPAUSE:NETFLIXFORWARD:NETFLIXBACKWARDS:NETFLIXFULLSCREEN".split(":"); };
	public boolean execute(String actioncode, String rawtext) { 
		try {
			if ( actioncode.equals("NETFLIXFULLSCREEN") ) {
				log("Netflix: toogle fullscreen");
				if ( vars.get("playing").equals("yes") ) {

					// FS mode can be only valid if we are in playing mode.

					// activate Netflix browser window
					pva.exec( pva.config.get("netflix","windowactivate").split(pva.config.get("conf","splitter")), wait);
					
					
					if ( vars.get("fullscreen").equals("no") ) {
						pos.parse( "pos_fullscreen_on");
						vars.put("fullscreen","yes");
					} else {
						pos.parse( "pos_fullscreen_off");
						vars.put("fullscreen","no");
					}				
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

				}
			
			} else if ( actioncode.equals("NETFLIXMYLIST") ) {
				log("Netflix: activate MY LIST");
				
				if ( vars.get("playing").equals("no") ) {
				
					// activate Netflix browser window and be sure to be on top
					pva.exec( pva.config.get("netflix","windowactivate").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					
					// now execute the real sequence
				
					pos.parse( "pos_mylist");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

					log( "DEBUG: "+ pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).replaceAll(pva.config.get("conf","splitter")," ") );
					log( "DEBUG: "+ pva.config.get("netflix","clickLMB").replaceAll(pva.config.get("conf","splitter")," ") );
				}										

			} else if ( actioncode.equals("NETFLIXHOME") ) {
				log("Netflix: activate HOME");
				
				if ( vars.get("playing").equals("no") ) {
				
					// activate Netflix browser window and be sure to be on top
					pva.exec( pva.config.get("netflix","windowactivate").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
					
					// now execute the real sequence
				
					pos.parse( "pos_start");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

					log( "DEBUG: "+ pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).replaceAll(pva.config.get("conf","splitter")," ") );
					log( "DEBUG: "+ pva.config.get("netflix","clickLMB").replaceAll(pva.config.get("conf","splitter")," ") );
				}										

			} else return false;

	
			return true; 
		}  catch (Exception localException) {
			localException.printStackTrace();
			return true; 
		}

	};

	public void run() {
		log("Netflix Dummy run()");
	}
}

class Position {

	long x = 0;
	long y = 0;
	long offset = 160; // Offset between FULLSCREEN position and WINDOWED position of buttons in the Netflix UI
	StringHash vars;
	PVA pva;

	Position(StringHash vars,PVA pva) {
		this.vars  = vars;
		this.pva = pva;
	}
	
	public void parse(String button) {
	
		String[] args = pva.config.get("netflix",button).split(",");
		if ( args.length != 2 ) {
			System.out.println("position "+button+" invalid!");
			return;
		}
	
		x = Long.parseLong(args[0].trim());
		y = Long.parseLong(args[1].trim());
		if ( vars.get("fullscreen").equals("yes") ) y = y - offset; // If in FS mode, all positions are nearer to 0 because the window frame and panels are missing
	
	}
		
}


