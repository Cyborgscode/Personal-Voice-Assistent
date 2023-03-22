
package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import server.streaming.*;
import data.Command;
import hash.StringHash;
import hash.TwoKeyHash;
import hash.TreeSort;
import java.util.Enumeration;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.HashMap;

public class Cluster extends Plugin {

	private String getFilter = "::";
	private String setFilter = "::";
	private TwoKeyHash validValues = new TwoKeyHash(); // this way we get multiply options for an argument 
	private TwoKeyHash cluster = new TwoKeyHash(); 
	private StringHash cmds = new StringHash();
	
	private HashMap<String,Streaming> server = new HashMap<String,Streaming>();
	
	
	private String amid = "";
	private String asid = "";

	private boolean donotdisturb = false;
	
	public Cluster() {
		System.out.println("Class Cluster Constructor called");
	}
	
	public void init(PVA pva) {
	
		String r = "";
	
		this.pva = pva;
		StringHash config = pva.config.get("cluster");

		info.put("hasThread","yes"); // Tells the loader to run run() 
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled cf.commands
		info.put("name","Cluster"); // be nice, create a unique name

		cmds.put("unload_micro",      "pactl unload-module <mid>");
		cmds.put("unload_speaker",    "pactl unload-module <sid>");
		cmds.put("detect_tunnel",     "pactl list short modules");
		cmds.put("remote_speaker",    "pactl load-module module-native-protocol-tcp port=<sport> listen=<ip>" );
		cmds.put("remote_micro",      "pactl load-module module-native-protocol-tcp port=<mport> listen=<ip>" );
		cmds.put("tunnel_speaker",    "pactl load-module module-tunnel-sink server=tcp:<ip>:<sport> sink_name=<name>");
		cmds.put("tunnel_micro",      "pactl load-module module-tunnel-source server=tcp:<ip>:<mport> source_name=<name>");
		cmds.put("create_all_sink",   "pactl load-module module-null-sink sink_name=ALLTUNNEL");
		cmds.put("create_all_source", "pactl load-module module-null-sink sink_name=ALLMICS");
		cmds.put("list_sinks",        "pactl list short sinks");
		cmds.put("list_sources",      "pactl list short sources");

		// LINK commands should be executed for 2 times, as it happens to be that links sometimes do not work on the first try

		cmds.put("link_speaker_left",      "pw-link ALLTUNNEL:monitor_FL \"<name>:playback_FL\"");
		cmds.put("link_speaker_right",     "pw-link ALLTUNNEL:monitor_FR \"<name>:playback_FR\"");
		cmds.put("link_speaker_left_new",  "pw-link ALLTUNNEL:monitor_FL \"<name>:send_FL\"");		// Thanks PIPEWIRE 0.3.67 :(
		cmds.put("link_speaker_right_new", "pw-link ALLTUNNEL:monitor_FR \"<name>:send_FR\"");
		cmds.put("link_micro_left",        "pw-link <name>:capture_FL ALLMICS:playback_FL");
		cmds.put("link_micro_right",       "pw-link <name>:capture_FR ALLMICS:playback_FR");
		cmds.put("link_pva_left",          "pw-link ALLMICS:monitor_FL \"<id>:input_FL\"");
		cmds.put("link_pva_right",         "pw-link ALLMICS:monitor_FR \"<id>:input_FR\"");

		// Streaming 
		
		cmds.put("killplayer", config.get("internal_killplayer") );
		cmds.put("killstreamserver", config.get("internal_killstreamserver") );
		cmds.put("streamplayer", config.get("internal_streamplayer") );
		cmds.put("streamserver", config.get("internal_streamserver") );
		cmds.put("desktopstream", config.get("internal_desktopstream") );
		cmds.put("camerastream", config.get("internal_camerastream") );
						
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
		
		Enumeration en = config.keys();
		while ( en.hasMoreElements() ) {
			String key = (String)en.nextElement();
			if ( !key.startsWith("internal_") ) {
			
				cluster.put(key, "sport", "4656");
				cluster.put(key, "mport", "4657");
				cluster.put(key, "streamport", "9999");
				cluster.put(key, "streamresolution", "1920x1080");
				cluster.put(key, "streamvideorate", "3000");
				cluster.put(key, "streamaudiorate", "48000");
				cluster.put(key, "desktopresolution", config.get("internal_resolution") );
				cluster.put(key, "desktopdisplay", config.get("internal_display") );
				cluster.put(key, "desktopaudio", config.get("internal_audio") );
				cluster.put(key, "videodevice", config.get("internal_videodevice") );
				cluster.put(key, "audiodevice", config.get("internal_audiodevice") );
				
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
				
					if ( r.contains( "port="+ cluster.get(key,"sport")+" ") ) {
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("port="+ cluster.get(key,"sport")+" ") ) {
								String[] opts = line.split("[\\s\t]");
								rsid = opts[0];
							}
						}
					} else rsid = remote( cluster.get(key), "remote_speaker");  // load tunnel module on remoteside
					
					if ( r.contains( "port="+ cluster.get(key,"mport")+" ") ) {
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains( "port="+ cluster.get(key,"mport")+" ") ) {
								String[] opts = line.split("[\\s\t]");
								rmid = opts[0];
							}
						}
					} else rmid = remote( cluster.get(key), "remote_micro");
					
					r = local( cluster.get(key), "detect_tunnel" );
					
					if ( rsid.trim().matches("^[0-9]+$") ) {
				
						cluster.put( key, "sid", rsid );
					
						// check if our module list contains  the destination clients tunnel
					
						if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":"+ cluster.get(key,"sport")+" sink_name="+ cluster.get( key, "name") ) ) {

							// it does not, so we create it.
					
							cluster.put( key, "tsid", local( cluster.get(key), "tunnel_speaker").trim() );
						} else {
						
							// It does, so we get the id
					
							String[] lines = r.split("\n");
							for(String line : lines ) {
								if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":"+ cluster.get(key,"sport")+" sink_name="+ cluster.get( key, "name") ) ) {
									String[] opts = line.split("[\\s\t]");
									cluster.put( key, "tsid", opts[0] );
								}
							}
						}

						// Now we LINK the tunnel with the ALLTUNNEL node
					
						local( cluster.get(key), "link_speaker_left" );
						local( cluster.get(key), "link_speaker_right" );
						local( cluster.get(key), "link_speaker_left" );
						local( cluster.get(key), "link_speaker_right" );

						// needed to compensate for change <= pw 0.3.67 to a new naming schema

						local( cluster.get(key), "link_speaker_left_new" );
						local( cluster.get(key), "link_speaker_right_new" );
						local( cluster.get(key), "link_speaker_left_new" );
						local( cluster.get(key), "link_speaker_right_new" );

					}
				
					if (rmid.trim().matches("^[0-9]+$") ) {
						cluster.put( key, "mid", rmid );
						
						// check if our module list contains  the destination clients tunnel
						
						if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":"+ cluster.get(key,"mport")+" source_name="+ cluster.get( key, "name") ) ) {
	
							// it does not, so we create it.
							cluster.put( key, "tmid", local( cluster.get(key), "tunnel_micro").trim() );
						} else {
						
							// It does, so we get the id
						
							String[] lines = r.split("\n");
							for(String line : lines ) {
								if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":"+ cluster.get(key,"mport")+" source_name="+ cluster.get( key, "name") ) ) {
									String[] opts = line.split("[\\s\t]");
									cluster.put( key, "tmid", opts[0] );
								}
							}
						}
						
						// Now we LINK the tunnel with the ALLMICS node
											
						local( cluster.get(key), "link_micro_left" );
						local( cluster.get(key), "link_micro_right" );
						local( cluster.get(key), "link_micro_left" );
						local( cluster.get(key), "link_micro_right" );

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
			return dos.readPipe( replacePlaceHolders( infos, ssh.replaceAll("<CMD>", cmds.get( cmd ) ) ) );
		}
		return "";
	}
	private String local(StringHash infos, String cmd) {
		return local(infos,cmd,0);
	}
	
	private String local(StringHash infos, String cmd,int log) {
		if ( infos != null ) {
		
			if ( log>1 ) System.out.println( replacePlaceHolders( infos, cmds.get( cmd ) ) );
			
			String r = dos.readPipe( replacePlaceHolders( infos, cmds.get( cmd ) ) );
			
			if ( log>0 ) System.out.println( "returns "+r );
			
			return r;
		}
		return "";
	}
	
	// getActionCodes() should return an empty String[], if we do not handle Actions

	public String[] getActionCodes() {  return "CLUSTERRESTARTCLIENT:CLUSTERSTREAMDESKTOP:CLUSTERSTREAMVIDEO:CLUSTERSTREAMSTOP:CLUSTERSTREAMNEXT:CLUSTERLISTCLIENTS:CLUSTERSTREAMCAMERA".split(":"); };
	public boolean execute(Command cf, String rawtext) { 

		try {
			if ( cf.command.equals("CLUSTERSTREAMVIDEO") ) {
			
				if ( cf.terms.size() < 2 ) {
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERPARSEERROR") );
					log("exit execute("+rawtext+")");
					return false;
				}

				String subtext = ((String)cf.terms.get(0)).trim();
				String client  = ((String)cf.terms.get(1)).trim();
	
				StringHash infos = cluster.get(client);
				if ( infos != null ) {

					log("Ich suche nach Videos : "+ subtext);

					String suchergebnis = pva.suche( pva.config.get("path","video"), subtext, ".mp4|.mpg|.mkv|.avi|.flv" );
					if (!suchergebnis.isEmpty() ) {	

						dos.writeFile(pva.getHome()+"/.cache/pva/search.stream.cache",suchergebnis);					

						TreeSort ts = new TreeSort();
	
						String[] files = suchergebnis.split(pva.config.get("conf","splitter"));
						for(String file : files ) {
							Path p = Paths.get( file );
							ts.add( p.getFileName().toString(), file );
						}
						String[] erg  = ts.getValues();
						suchergebnis = "";                				
				                for(int uyz=0;uyz<ts.size();uyz++) suchergebnis += erg[uyz]+pva.config.get("conf","splitter");
	
						Streaming serverinstance = (Streaming)(new VideoStreaming( pva, infos,cmds, suchergebnis.split( pva.config.get("conf","splitter") ) ) );
						server.put( client.toLowerCase(), serverinstance);
				                serverinstance.start();
	
					} else {
						log( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERSTREAMSEARCHERROR").replaceAll("<TERM1>", client ) );
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERSTREAMSEARCHERROR").replaceAll("<TERM1>", client ) );
					}
				} else {
					log( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
				}
				
				return true;
			
			} else if ( cf.command.equals("CLUSTERSTREAMDESKTOP") ) {
			
				if ( cf.terms.size() < 1 ) {
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERPARSEERROR") );
					log("exit execute("+rawtext+")");
					return false;
				}

				String client = ((String)cf.terms.get(0)).trim();
				StringHash infos = cluster.get(client);
				if ( infos != null ) {
	
					Streaming serverinstance = (Streaming)(new DesktopStreaming( pva, infos,cmds, null ));
					server.put( client.toLowerCase(), serverinstance);
			                serverinstance.start();
	
				} else {
					log( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
				}
				
				return true;
			
			} else if ( cf.command.equals("CLUSTERSTREAMCAMERA") ) {

				if ( cf.terms.size() < 1 ) {
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERPARSEERROR") );
					log("exit execute("+rawtext+")");
					return false;
				}

				String client = ((String)cf.terms.get(0)).trim();
				StringHash infos = cluster.get(client);
				if ( infos != null ) {
	
					Streaming serverinstance = (Streaming)(new LiveStreaming( pva, infos,cmds, null ));
					server.put( client.toLowerCase(), serverinstance);
			                serverinstance.start();
	
				} else {
					log( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
					pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERCLIENTERRORNOTFOUND").replaceAll("<TERM1>", client ) );
				}
				
				return true;
			
			
			} else if ( cf.command.equals("CLUSTERSTREAMNEXT") ) {

				String text = rawtext.trim();

				if ( text.isEmpty() ) {
				
					log("cf.size= "+ cf.terms.size() + "\n" + cf.terms.toString() );
				
					if ( cf.terms.size() > 0 && ! ((String)cf.terms.get(0)).trim().isEmpty() ) {
						text = ((String)cf.terms.get(0)).trim();
					}
				}
				
				StringHash infos = cluster.get(text);
				if ( infos != null ) {
					log("skip to next video for client "+ text);					
					log("server="+server.toString() );
	
					Streaming s = server.get( text );
					if ( s!=null && infos.get("streaming").equals("on") ) {
						log("skip "+ text);
						s.next();
						log("skipped "+ text);
					} else 	log("s="+s+ " and streaming is "+ infos.get("streaming") );
			
				} 
				
				return true;
					
			} else if ( cf.command.equals("CLUSTERSTREAMSTOP") ) {

				String text = rawtext.trim();
				
				if ( text.isEmpty() ) {
				
					log("cf.size= "+ cf.terms.size() + "\n" + cf.terms.toString() );
				
					if ( cf.terms.size() > 0 && ! ((String)cf.terms.get(0)).trim().isEmpty() ) {
						text = ((String)cf.terms.get(0)).trim();
					}
				}
						
				log("stoppe client "+text);
				
				StringHash infos = cluster.get(text);
				if ( infos != null ) {
					log("streaming stop for client "+ text);
					Streaming s = server.get( text );
					if ( s!=null && infos.get("streaming").equals("on") ) {
						log("stopping "+ text);
						s.exit();
						log("stopped "+ text);
						server.remove(s);
					} else  log("s="+s+ " and streaming is "+ infos.get("streaming") );
			
				} 
				
				return true;
					
			} else if ( cf.command.equals("CLUSTERLISTCLIENTS") ) {

				String text = "";
				Enumeration en = cluster.keys();
				while ( en.hasMoreElements() ) {
					text += (String)en.nextElement() +".";
				}

				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "CLUSTERLISTCLIENTS").replaceAll("<TERM1>", text ) );
				
				return true;
					
			} else if ( cf.command.equals("CLUSTERRESTARTCLIENT") ) {

				donotdisturb = true;
				
				String text = rawtext.trim();
				
				StringHash infos = cluster.get(text);
				if ( infos != null ) {
					
					log("client unload "+ text);
					
					local(infos,"unload_micro");
					local(infos,"unload_speaker");
	
					String r = remote(infos, "detect_tunnel");
					
					if ( !r.matches("port="+infos.get("sport")+".*listen="+ infos.get( "ip")+".*") ) {
						
						infos.put("rsid", remote( infos, "remote_speaker") );  // load tunnel module on remoteside
			
					}
					if ( !r.matches("port="+infos.get("mport")+".*listen="+ infos.get( "ip")+".*") ) {
						
						infos.put("rmid", remote( infos, "remote_micro") );  // load tunnel module on remoteside
					
					}
					
					r = local( infos, "detect_tunnel" );
					if ( !r.contains("server=tcp:"+ infos.get( "ip") +":"+infos.get("sport")+" sink_name="+ infos.get("name") ) ) {

						// it does not, so we create it.
					
						infos.put("tsid", local( infos, "tunnel_speaker").trim() );
						
						log("client "+text+" created tunnel "+ infos.get("tsid") );
						
					} else {
						
						// It does, so we get the id
					
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("server=tcp:"+ infos.get("ip") +":"+infos.get("sport")+" sink_name="+ infos.get("name") ) ) {
								String[] opts = line.split("[\\s\t]");
								infos.put( "tsid", opts[0] );
							}
						}
					}

					// Now we LINK the tunnel with the ALLTUNNEL node
					
					local( infos, "link_speaker_left" );
					local( infos, "link_speaker_right" );
					local( infos, "link_speaker_left" );
					local( infos, "link_speaker_right" );
				
					if ( !r.contains("server=tcp:"+ infos.get("ip") +":"+infos.get("mport")+" source_name="+ infos.get("name") ) ) {
	
						// it does not, so we create it.
						infos.put("tmid", local( infos, "tunnel_micro").trim() );
						log("client "+text+" created tunnel "+ infos.get("tmid") );
					} else {
						
						// It does, so we get the id
						
						String[] lines = r.split("\n");
						for(String line : lines ) {
							if ( line.contains("server=tcp:"+ infos.get("ip") +":"+infos.get("mport")+" source_name="+ infos.get("name") ) ) {
								String[] opts = line.split("[\\s\t]");
								infos.put("tmid", opts[0] );
							}
						}
					}
						
					// Now we LINK the tunnel with the ALLMICS node
										
					local( infos, "link_micro_left" );
					local( infos, "link_micro_right" );
					local( infos, "link_micro_left" );
					local( infos, "link_micro_right" );
					
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

				String modules = dos.readPipe("pactl list modules short");
				
				StringHash config = pva.config.get("cluster");
				Enumeration en = config.keys();
				while ( en.hasMoreElements() ) {
					String key = (String)en.nextElement();
					
					if ( !key.startsWith("internal_") ) {
				
						// check if available without hassle, means: no need to code that in java ourselfs!
						
						String r = dos.readPipe("ping -c 1 -W 1 -n "+ cluster.get(key,"ip") );
						if ( !r.contains( " 0% packet loss" ) ) {
							// we could not ping it, so we don't have SSH access..
							// remove local tunnel modules in case it was already connected. This is vital if the client returns later
							if ( !cluster.get(key,"sid").equals("0") || !cluster.get(key,"mid").equals("0") ) {
								log("Cluster:run: host down for client "+key+" detected ... removing connection");
								// we need to remove the tunnel module
								local( cluster.get(key), "unload_micro");
								local( cluster.get(key), "unload_speaker");
								cluster.put(key, "sid","0");
								cluster.put(key, "mid","0");
							}							
							continue;
						}
						
						// If a client host is up AND we have a sid&mic from it, everything is fine
						// BUT, if a sid/mic is 0, we need to setup him.
						
						// unfortunatly, not so fine as we thought :)
						
						boolean found_s = false;
						boolean found_m = false;
						
						for(String line : modules.split("\n") ) {
							if ( !cluster.get(key,"tsid").equals("0") && line.startsWith( cluster.get(key,"tsid") ) ) found_s = true;
							if ( !cluster.get(key,"tmid").equals("0") && line.startsWith( cluster.get(key,"tmid") ) ) found_m = true;
						} 

						// test if we lost connection .. being pingable is not everything that could happen!

						if ( !found_s ) cluster.put(key,"sid","0");
						if ( !found_m ) cluster.put(key,"mid","0");
						
				
						if ( cluster.get(key,"sid").equals("0") || cluster.get(key,"mid").equals("0") ) {
				
							log("Cluster:run: client "+key+" detected and not integrated...trying to setup connection");
				
							// we do the same as in init, except loading the config for a client, so no more comments here to compactify the source
					
							String rsid = "";
							String rmid = "";
							r = remote( cluster.get(key), "detect_tunnel");
							if ( r.contains("libpipewire") ) {
								if ( r.contains( "port="+cluster.get(key,"sport")+" ") ) {
									String[] lines = r.split("\n");
									for(String line : lines ) {
										if ( line.contains( "port="+cluster.get(key,"sport")+" ") ) {
											String[] opts = line.split("[\\s\t]");
											rsid = opts[0];
										}
									}
								} else rsid = remote( cluster.get(key), "remote_speaker");  // load tunnel module on remoteside
						
								if ( r.contains("port="+cluster.get(key,"mport")+" ") ) {
									String[] lines = r.split("\n");
									for(String line : lines ) {
										if ( line.contains( "port="+cluster.get(key,"mport")+" ") ) {
											String[] opts = line.split("[\\s\t]");
											rmid = opts[0];
										}
									}
								} else rmid = remote( cluster.get(key), "remote_micro");
								
								r = local( cluster.get(key), "detect_tunnel" );
								if ( rsid.trim().matches("^[0-9]+$") ) {
									cluster.put( key, "sid", rsid );
									if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":"+cluster.get(key,"sport")+" sink_name="+ cluster.get( key, "name") ) ) {
										cluster.put( key, "tsid", local( cluster.get(key), "tunnel_speaker").trim() );
									} else {
										String[] lines = r.split("\n");
										for(String line : lines ) {
											if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":"+cluster.get(key,"sport")+" sink_name="+ cluster.get( key, "name") ) ) {
												String[] opts = line.split("[\\s\t]");
												cluster.put( key, "tsid", opts[0] );
											}
										}
									}
									local( cluster.get(key), "link_speaker_left" );
									local( cluster.get(key), "link_speaker_right" );
									local( cluster.get(key), "link_speaker_left" );
									local( cluster.get(key), "link_speaker_right" );
								}
							
								if (rmid.trim().matches("^[0-9]+$") ) {
									cluster.put( key, "mid", rmid );
									if ( !r.contains("server=tcp:"+ cluster.get( key, "ip") +":"+cluster.get(key,"mport")+" source_name="+ cluster.get( key, "name") ) ) {
										cluster.put( key, "tmid", local( cluster.get(key), "tunnel_micro").trim() );
									} else {
										String[] lines = r.split("\n");
										for(String line : lines ) {
											if ( line.contains("server=tcp:"+ cluster.get( key, "ip") +":"+cluster.get(key,"mport")+" source_name="+ cluster.get( key, "name") ) ) {
												String[] opts = line.split("[\\s\t]");
												cluster.put( key, "tmid", opts[0] );
											}
										}
									}
									local( cluster.get(key), "link_micro_left" );
									local( cluster.get(key), "link_micro_right" );
									local( cluster.get(key), "link_micro_left" );
									local( cluster.get(key), "link_micro_right" );
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
