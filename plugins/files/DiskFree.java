
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;

/*

	This plugin does not handle floppy size disks, we have 2022 
	
*/

public class DiskFree extends Plugin {

	private String getFilter = ":silence:";
	private String setFilter = ":silence:";
	private TwoKeyHash validValues = new TwoKeyHash(); 
	
	public DiskFree() {
		System.out.println("Class DiskFree Constructor called");
	}
	
	public void init(PVA pva) {
		this.pva = pva;
		info.put("hasThread","yes"); 
		info.put("hasCodes","yes");  
		info.put("name","DiskFree"); 
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

	public String[] getActionCodes() {  return "SILENCEDISKWARNING:UNSILENCEDISKWARNING".split(":"); };
	public boolean execute(String actioncode, String rawtext) { 
		try {
			if ( actioncode.equals("SILENCEDISKWARNING") ) {
				log("schalte warnung aus");
				setVar("silence","yes");
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "DISKRESPONSETURNEDOFF") );
	
			} else if ( actioncode.equals("UNSILENCEDISKWARNING") ) {
	
				log("schalte warnung ein");
				setVar("silence","no");
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "DISKRESPONSETURNEDON") );
	
			} else return false;
	
			return true; 
		}  catch (Exception localException) {
			localException.printStackTrace();
			return true; 
		}

	};

	public void run() {
	
		try {
			String[] disks;
			int time = 0;
			boolean inform = false;
			
			TwoKeyHash warned = new TwoKeyHash();

			while ( true ) {
				if (isInterrupted()) {
					return;
				}

				disks = dos.readPipe("/usr/bin/df -h --print-type").split("\n");
							
				int i = 0;
				for(String disk : disks ) {
				
					if ( i >0 ) {
									
						if ( ! disk.contains("tmpfs") ) {
					
							// removes all <spaces> until we have just one <space> so we can split them
														
							while ( disk.contains("  ") ) { disk = disk.replaceAll("  "," "); };
							
							String[] spalten = disk.replaceAll("  "," ").split(" ");
												
							if ( spalten[5].equals("100%") ) {
								long free = 0;
								String unit = "";
								if ( spalten[4].length() > 1 ) {
									free = Long.parseLong( spalten[4].substring(0, spalten[4].length()-1 ) );
									unit = spalten[4].substring( spalten[4].length()-1, spalten[4].length() );
								}
								if ( unit.equals("") && free == 0 ) {
									if ( vars.get("silence").equals("no") && ! warned.get(spalten[6],"full").equals("1") ) {
										pva.say( 
											pva.texte.get( 
												pva.config.get("conf","lang_short"),
												"DISKWARNINGFULL"
											).replaceAll("<TERM1>", ""+free)
											 .replaceAll("<TERM2>", "Megabyte")
											 .replaceAll("<TERM3>", spalten[6])
										);
										warned.put( spalten[6], "full", "1");
									}
								} else if ( unit.equals("M") && free < 500 ) {
									if ( vars.get("silence").equals("no") && ! warned.get(spalten[6],"warn").equals("1") ) {
										pva.say( 
											pva.texte.get( 
												pva.config.get("conf","lang_short"),
												"DISKWARNINGNEARLY"
											).replaceAll("<TERM1>", ""+free)
											 .replaceAll("<TERM2>", "Megabyte")
											 .replaceAll("<TERM3>", spalten[6] )
										);
										warned.put( spalten[6], "warn", "1");
									}
								} else if ( unit.equals("G") && free < 1 ) {
									if ( vars.get("silence").equals("no") && ! warned.get(spalten[6],"soon").equals("1") ) { 
										pva.say( 
											pva.texte.get( 
												pva.config.get("conf","lang_short"),
												"DISKWARNINGSOON"
											).replaceAll("<TERM1>", ""+free)
											 .replaceAll("<TERM2>", "Gigabyte")
											 .replaceAll("<TERM3>", spalten[6] )
										);
										warned.put( spalten[6], "soon", "1");
									}
								}
							} else {
							
								StringHash content = warned.get( spalten[6] );
								if ( content != null 
								     && ( 
									      content.get("full").equals("1") 
									   || content.get("warn").equals("1")
									   || content.get("soon").equals("1")  
								     )
								) {
									content.put("full","0");
									content.put("warn","0");
									content.put("soon","0");
									if ( vars.get("silence").equals("no") ) 
										pva.say( 
											pva.texte.get( 
												pva.config.get("conf","lang_short"),
												"DISKWARNINGCLEAR"
											).replaceAll("<TERM1>", spalten[6] )
										);
								}
							}
						}
					}
					i++;
				}
				
				time++;
				sleep(1000L);

			}
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
