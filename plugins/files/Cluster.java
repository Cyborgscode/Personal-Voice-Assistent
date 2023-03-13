
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import java.util.Enumeration;

public class Cluster extends Plugin {

	private String getFilter = "::";
	private String setFilter = "::";
	private TwoKeyHash validValues = new TwoKeyHash(); // this way we get multiply options for an argument 
	private TwoKeyHash cluster = new TwoKeyHash(); 
	private StringHash cmds = new StringHash();
	
	private String amid = "";
	private String asid = "";

	private boolean donotdisturb = false;
	
	public Cluster() {
		System.out.println("Class Cluster Constructor called");
	}
	
	public void init(PVA pva) {
	
		String r = "";
	
		this.pva = pva;
		info.put("hasThread","yes"); // Tells the loader to run run() 
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled actioncodes
		info.put("name","Cluster"); // be nice, create a unique name

		cmds.put("unload_micro",      "pactl unload-module <mid>");
		cmds.put("unload_speaker",    "pactl unload-module <sid>");
		cmds.put("detect_tunnel",     "pactl list short modules");
		cmds.put("remote_speaker",    "pactl load-module module-native-protocol-tcp port=4656 listen=<ip>" );
		cmds.put("remote_micro",      "pactl load-module module-native-protocol-tcp port=4657 listen=<ip>" );
		cmds.put("tunnel_speaker",    "pactl load-module module-tunnel-sink server=tcp:<ip>:4656 sink_name=<name>");
		cmds.put("tunnel_micro",      "pactl load-module module-tunnel-source server=tcp:<ip>:4657 source_name=<name>");
		cmds.put("create_all_sink",   "pactl load-module module-null-sink sink_name=ALLTUNNEL");
		cmds.put("create_all_source", "pactl load-module module-null-sink sink_name=ALLMICS");
		cmds.put("list_sinks",        "pactl list short sinks");
		cmds.put("list_sources",      "pactl list short sources");

		cmds.put("link_speaker_left",  "pw-link ALLTUNNEL:monitor_FL \"<name>:playback_FL\"");
		cmds.put("link_speaker_right", "pw-link ALLTUNNEL:monitor_FR \"<name>:playback_FR\"");
		cmds.put("link_micro_left",    "pw-link <name>:capture_FL ALLMICS:playback_FL");
		cmds.put("link_micro_right",   "pw-link <name>:capture_FR ALLMICS:playback_FR");
		cmds.put("link_pva_left",      "pw-link ALLMICS:monitor_FL \"<id>:input_FL\"");
		cmds.put("link_pva_right",     "pw-link ALLMICS:monitor_FR \"<id>:input_FR\"");


		// create the nodes, but only if they are not active

		String nodes = dos.readPipe( cmds.get("list_sinks") );
		if ( !nodes.contains("ALLTUNNEL") ) {
			asid = dos.readPipe( cmds.get("create_all_sink") ).trim();
		} else asid = "ALLTUNNEL";

		nodes = dos.readPipe( cmds.get("list_sources") );
		if ( !nodes.contains("ALLMICS") ) {
			amid = dos.readPipe(cmds.get("create_all_source") ).trim();
		} else amid = "ALLMICS";

		// Transform config key/value syntax into HashMap
		// makes it easier to use it ;)
		
		StringHash config = pva.config.get("cluster");
		Enumeration en = config.keys();
		while ( en.hasMoreElements() ) {
			String key = (String)en.nextElement();
			if ( !key.startsWith("internal_") ) {
				String[] args = config.get( key ).split(";");
				for(String x : args) {
					String[] opts = x.split("=");
					if ( opts.length == 2 ) {
						cluster.put( key, opts[0], opts[1] );
					} else  System.out.println( "Cluster:Error: invalid options in "+ key );
				}
				
				// You may wonder why we save the IDs of created nodes, but use the names to identify them
				// the IDs are needed for unloading the module, which can happen on voice command i.e. if the audio is crackling
				
				cluster.put( key, "mid", "0");	// mid = microphoneID
				cluster.put( key, "sid", "0");  // sid = speakerID
				cluster.put( key, "tmid", "0"); // tmid = tunnel_microphoneID
				cluster.put( key, "tsid", "0"); // tsid = tunnel_speakerID
				cluster.put( key, "asid", asid);
				cluster.put( key, "amid", amid);

				// check if client host is up
				
				r = dos.readPipe("ping -c 1 -W 1 -n "+ cluster.get(key,"ip") );
				if ( !r.contains( " 0% packet loss" ) ) {
					// we could not ping it, so we don't have SSH access.. ( it's LAN, not the internet ;) )
					continue;
				}
				
				log("Cluster:init: client host "+ key +" detected");
				
				// get list of modules loaded and check if tunnel ports 4656/4657 are in use.
				
				String rsid = "";
				String rmid = "";
				r = remote( cluster.get(key), "detect_tunnel");
				
				if ( r.contains("libpipewire") ) {

					// Ok, we could reach it.
				
					if ( r.contains( "port=4656 ") ) {
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("port=4656 ") ) {
								String[] opts = line.split("[\\s\t]");
								rsid = opts[0];
							}
						}
					} else rsid = remote( cluster.get(key), "remote_speaker");  // load tunnel module on remoteside
					
					if ( r.contains( "port=4657 ") ) {
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains( "port=4657 ") ) {
								String[] opts = line.split("[\\s\t]");
								rmid = opts[0];
							}
						}
					} else rmid = remote( cluster.get(key), "remote_micro");
					
					r = execute( cluster.get(key), "detect_tunnel" );
					
					if ( rsid.trim().matches("^[0-9]+$") ) {
				
						cluster.put( key, "sid", rsid );
					
						// check if our module list contains  the destination clients tunnel
					
						if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":4656 sink_name="+ cluster.get( key, "name") ) ) {

							// it does not, so we create it.
					
							cluster.put( key, "tsid", execute( cluster.get(key), "tunnel_speaker").trim() );
						} else {
						
							// It does, so we get the id
					
							String[] lines = r.split("\n");
							for(String line : lines ) {
								if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":4656 sink_name="+ cluster.get( key, "name") ) ) {
									String[] opts = line.split("[\\s\t]");
									cluster.put( key, "tsid", opts[0] );
								}
							}
						}

						// Now we LINK the tunnel with the ALLTUNNEL node
					
						execute( cluster.get(key), "link_speaker_left" );
						execute( cluster.get(key), "link_speaker_right" );
					}
				
					if (rmid.trim().matches("^[0-9]+$") ) {
						cluster.put( key, "mid", rmid );
						
						// check if our module list contains  the destination clients tunnel
						
						if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":4657 source_name="+ cluster.get( key, "name") ) ) {
	
							// it does not, so we create it.
							cluster.put( key, "tmid", execute( cluster.get(key), "tunnel_micro").trim() );
						} else {
						
							// It does, so we get the id
						
							String[] lines = r.split("\n");
							for(String line : lines ) {
								if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":4657 source_name="+ cluster.get( key, "name") ) ) {
									String[] opts = line.split("[\\s\t]");
									cluster.put( key, "tmid", opts[0] );
								}
							}
						}
						
						// Now we LINK the tunnel with the ALLMICS node
											
						execute( cluster.get(key), "link_micro_left" );
						execute( cluster.get(key), "link_micro_right" );
					}
				} else {
					log("Cluster:init: unable to connect to client "+ key +" / unable to detect pipewire environment");
				}
			} 
		}
	
		log("Cluster:init: waiting for vosk to be ready");
		
		r = dos.readPipe( "pactl list source-outputs" );
		while ( !r.contains( config.get("internal_pvasink") ) ) {
			// wait for it...
			try {
				Thread.sleep(1000L);
			} catch (Exception e) {
				log("Cluster:init: abnormal termination caused by Thread.sleep()");
				return;
			}
			r = dos.readPipe( "pactl list source-outputs" );
		}
		
		dos.readPipe("pw-link ALLMICS:monitor_FL \""+ config.get("internal_pvasink") +":input_FL\"");
		dos.readPipe("pw-link ALLMICS:monitor_FR \""+ config.get("internal_pvasink") +":input_FR\"");
		
		dos.readPipe("pw-link ALLTUNNEL:monitor_FL \""+ config.get("internal_speaker") +":playback_FL\"");
		dos.readPipe("pw-link ALLTUNNEL:monitor_FR \""+ config.get("internal_speaker") +":playback_FR\"");

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

	private String replacePlaceHolders(StringHash infos, String cmd) {
	
		Enumeration en = infos.keys();
		while ( en.hasMoreElements() ) {
			String key = (String)en.nextElement();
			cmd = cmd.replaceAll("<"+ key +">", infos.get(key) );
		}
		return cmd;
	}

	private String remote(StringHash infos, String cmd) {
		if ( infos != null ) {
			String ssh = "";
			if ( infos.get("key").equals("default") ) {
				ssh = "ssh <user>@<ip> \"<CMD>\"";
			} else {
				ssh = "ssh -i <key> <user>@<ip> \"<CMD>\"";
			} 		
			// System.out.println( replacePlaceHolders( infos, ssh.replaceAll("<CMD>", cmds.get( cmd ) ) ) );
			String r = dos.readPipe( replacePlaceHolders( infos, ssh.replaceAll("<CMD>", cmds.get( cmd ) ) ) );
			// System.out.println( "returns "+r );
			return r;
		}
		return "";
	}
	
	private String execute(StringHash infos, String cmd) {
		if ( infos != null ) {
		
			// System.out.println( replacePlaceHolders( infos, cmds.get( cmd ) ) );
			
			String r = dos.readPipe( replacePlaceHolders( infos, cmds.get( cmd ) ) );
			
			// System.out.println( "returns "+r );
			
			return r;
		}
		return "";
	}
	
	// getActionCodes() should return an empty String[], if we do not handle Actions

	public String[] getActionCodes() {  return "CLUSTERRESTARTCLIENT".split(":"); };
	public boolean execute(String actioncode, String rawtext) { 
		try {
			if ( actioncode.equals("CLUSTERRESTARTCLIENT") ) {

				donotdisturb = true;
				
				String text = rawtext.trim();
				
				StringHash infos = cluster.get(text);
				if ( infos != null ) {
					
					log("client unload "+ text);
					
					execute(infos,"unload_micro");
					execute(infos,"unload_speaker");
	
					String r = execute( infos, "detect_tunnel" );
					
					if ( !r.contains("server=tcp:"+ infos.get( "ip") +":4656 sink_name="+ infos.get("name") ) ) {

						// it does not, so we create it.
					
						infos.put("tsid", execute( infos, "tunnel_speaker").trim() );
					} else {
						
						// It does, so we get the id
					
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("server=tcp:"+ infos.get("ip") +":4656 sink_name="+ infos.get("name") ) ) {
								String[] opts = line.split("[\\s\t]");
								infos.put( "tsid", opts[0] );
							}
						}
					}

					// Now we LINK the tunnel with the ALLTUNNEL node
					
					execute( infos, "link_speaker_left" );
					execute( infos, "link_speaker_right" );
				
					if ( !r.contains("server=tcp:"+ infos.get("ip") +":4657 source_name="+ infos.get("name") ) ) {
	
						// it does not, so we create it.
						infos.put("tmid", execute( infos, "tunnel_micro").trim() );
					} else {
						
						// It does, so we get the id
						
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("server=tcp:"+ infos.get("ip") +":4657 source_name="+ infos.get("name") ) ) {
								String[] opts = line.split("[\\s\t]");
								infos.put("tmid", opts[0] );
							}
						}
					}
						
					// Now we LINK the tunnel with the ALLMICS node
										
					execute( infos, "link_micro_left" );
					execute( infos, "link_micro_right" );
					
					if ( !infos.get("tsid").matches("^[0-9]+$") || !infos.get("tmid").matches("^[0-9]+$") ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", text ) );
					}
				} else pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", text ) );
			
				donotdisturb = true;
			
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
		try {
			String load = "";
			int time = 0;
			boolean inform = false;

			while ( true ) {
				if (isInterrupted()) {
					return;
				}
				
				// 10 Seconds Cycle.. 
				sleep(10000L);

				if ( donotdisturb ) continue;
				
				StringHash config = pva.config.get("cluster");
				Enumeration en = config.keys();
				while ( en.hasMoreElements() ) {
					String key = (String)en.nextElement();
					
					if ( !key.startsWith("internal_") ) {
				
						// check if available without hassle, means: no need to code that in java ourselfs!
						
						String r = dos.readPipe("ping -c 1 -W 1 -n "+ cluster.get(key,"ip") );
						if ( !r.contains( " 0% packet loss" ) ) {
							// we could not ping it, so we don't have SSH access..
							// remove local tunnel modules in case it was already connected. This is vital if the client return later
							if ( !cluster.get(key,"sid").equals("0") || !cluster.get(key,"mid").equals("0") ) {
								log("Cluster:run: host down for client "+key+" detected ... removing connection");
								// we need to remove the tunnel module
								execute( cluster.get(key), "unload_micro");
								execute( cluster.get(key), "unload_speaker");
								cluster.put(key, "sid","0");
								cluster.put(key, "mid","0");
							}							
							continue;
						}
						
						// If a client host is up AND we have a sid&mic from it, everything is fine
						// BUT, if a sid/mic is 0, we need to setup him.
				
						if ( cluster.get(key,"sid").equals("0") || cluster.get(key,"mid").equals("0") ) {
				
							log("Cluster:run: client "+key+" detected and not integrated...trying to setup connection");
				
							// we do the same as in init, except loading the config for a client, so no more comments here to compactify the source
					
							String rsid = "";
							String rmid = "";
							r = remote( cluster.get(key), "detect_tunnel");
							if ( r.contains("libpipewire") ) {
								if ( r.contains( "port=4656 ") ) {
									String[] lines = r.split("\n");
									for(String line : lines ) {
										if ( line.contains( "port=4656 ") ) {
											String[] opts = line.split("[\\s\t]");
											rsid = opts[0];
										}
									}
								} else rsid = remote( cluster.get(key), "remote_speaker");  // load tunnel module on remoteside
						
								if ( r.contains("port=4657 ") ) {
									String[] lines = r.split("\n");
									for(String line : lines ) {
										if ( line.contains( "port=4657 ") ) {
											String[] opts = line.split("[\\s\t]");
											rmid = opts[0];
										}
									}
								} else rmid = remote( cluster.get(key), "remote_micro");
								
								r = execute( cluster.get(key), "detect_tunnel" );
								if ( rsid.trim().matches("^[0-9]+$") ) {
									cluster.put( key, "sid", rsid );
									if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":4656 sink_name="+ cluster.get( key, "name") ) ) {
										cluster.put( key, "tsid", execute( cluster.get(key), "tunnel_speaker").trim() );
									} else {
										String[] lines = r.split("\n");
										for(String line : lines ) {
											if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":4656 sink_name="+ cluster.get( key, "name") ) ) {
												String[] opts = line.split("[\\s\t]");
												cluster.put( key, "tsid", opts[0] );
											}
										}
									}
									execute( cluster.get(key), "link_speaker_left" );
									execute( cluster.get(key), "link_speaker_right" );
								}
							
								if (rmid.trim().matches("^[0-9]+$") ) {
									cluster.put( key, "mid", rmid );
									if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":4657 source_name="+ cluster.get( key, "name") ) ) {
										cluster.put( key, "tmid", execute( cluster.get(key), "tunnel_micro").trim() );
									} else {
										String[] lines = r.split("\n");
										for(String line : lines ) {
											if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":4657 source_name="+ cluster.get( key, "name") ) ) {
												String[] opts = line.split("[\\s\t]");
												cluster.put( key, "tmid", opts[0] );
											}
										}
									}
									execute( cluster.get(key), "link_micro_left" );
									execute( cluster.get(key), "link_micro_right" );
								}
							} else {
								log("Cluster:init: unable to connect to client "+ key +" / unable to detect pipewire environment");
							}
						}
					}
			
				}
			}
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
