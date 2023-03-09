
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
	
	private void activateNetflixWindow() {
	
		try {
	
			// activate Netflix browser window and be sure to be on top
			pva.exec( pva.config.get("netflix","windowactivate").split(pva.config.get("conf","splitter")), wait);

			// anyone with a better idea is welcome to share it...
		
			pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
			pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
			pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
			pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
			pva.exec( pva.config.get("netflix","pageup").split(pva.config.get("conf","splitter")), wait);
		} catch (Exception e) {
			log(e.toString());
			e.printStackTrace();
		}
	}

	
	final boolean wait = true;
	
	public String[] getActionCodes() {  return "NETFLIXHOME:NETFLIXMYLIST:NETFLIXSEARCH:NETFLIXRETURN:NETFLIXPAUSE:NETFLIXFORWARD:NETFLIXBACKWARDS:NETFLIXFULLSCREEN".split(":"); };
	public boolean execute(String actioncode, String rawtext) { 
		try {
			if ( actioncode.equals("NETFLIXFULLSCREEN") ) {
				log("Netflix: toogle fullscreen");
				if ( vars.get("playing").equals("yes") ) {

					// FS mode can be only valid if we are in playing mode.

					activateNetflixWindow();
					
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
				
					activateNetflixWindow();
					 
					// now execute the real sequence
				
					pos.parse( "pos_mylist");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

				}										

			} else if ( actioncode.equals("NETFLIXHOME") ) {
				log("Netflix: activate HOME");
				
				if ( vars.get("playing").equals("no") ) {

					activateNetflixWindow();
					
					// now execute the real sequence
				
					pos.parse( "pos_start");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
				}										

			} else if ( actioncode.equals("NETFLIXRETURN") ) {
				log("Netflix: activate RETURN");
				
				if ( vars.get("playing").equals("yes") ) {

					activateNetflixWindow();
					
					// now execute the real sequence
				
					pos.parse( "pos_back");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
					vars.put("paused","no");
					vars.put("playing","no");

				}										

			} else if ( actioncode.equals("NETFLIXPAUSE") ) {
				log("Netflix: activate PAUSE/PLAY");

				if ( vars.get("playing").equals("yes") ) {

					activateNetflixWindow();
					
					// now execute the real sequence
				
					pos.parse( "pos_play");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

					if ( vars.get("paused").equals("no") ) {
						vars.put("paused","yes");
					} else {
						vars.put("paused","no");
					}

				}										

			} else if ( actioncode.equals("NETFLIXFORWARD") ) {
				log("Netflix: activate FORWARD");
				
				if ( vars.get("playing").equals("yes") ) {

					activateNetflixWindow();
					
					// now execute the real sequence
				
					pos.parse( "pos_forward");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

				}										

			} else if ( actioncode.equals("NETFLIXBACKWARDS") ) {
				log("Netflix: activate BACKWARDS");
				
				if ( vars.get("playing").equals("yes") ) {

					activateNetflixWindow();
					
					// now execute the real sequence
				
					pos.parse( "pos_backwards");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

				}										

			} else if ( actioncode.equals("NETFLIXSEARCH") ) {
				log("Netflix: activate SEARCH");

				if ( vars.get("playing").equals("yes") ) {
					activateNetflixWindow();
					pos.parse( "pos_back");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
					Thread.sleep(2000L);
					vars.put("playing","no");
					vars.put("paused","no");
				}
				
				if ( vars.get("playing").equals("no") ) {
				
					String searchterm = rawtext.trim();

					if ( pva.config.get("conf","lang_short").equals("de") ) {
						// if we run i.e. german Y & Z need to be exchanged as xdotool does not honor $LANG :/
						searchterm = searchterm.replaceAll("y",":&:").replaceAll("z","y").replaceAll(":&:","z");
					}

					log("search "+ searchterm);

					activateNetflixWindow();

					// now execute the real sequence
					pva.exec( pva.config.get("netflix","escape").split(pva.config.get("conf","splitter")), wait);
					pos.parse( "pos_start");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
					Thread.sleep(1000L);
					pos.parse( "pos_search");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
					Thread.sleep(1000L);
					pva.exec( pva.config.get("netflix","type").replaceAll("<TERM1>", searchterm+"\n") .split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","linefeed").split(pva.config.get("conf","splitter")), wait);
					pos.parse( "pos_firstentry");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);
					Thread.sleep(2000L);
					pos.parse( "pos_miniplay");
					pva.exec( pva.config.get("netflix","mousemove").replaceAll("XXX", ""+pos.x ).replaceAll("YYY", ""+pos.y ).split(pva.config.get("conf","splitter")), wait);
					pva.exec( pva.config.get("netflix","clickLMB").split(pva.config.get("conf","splitter")), wait);

					vars.put("playing","yes");

					
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

