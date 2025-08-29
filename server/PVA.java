/*

PVA is coded by Marius Schwarz since 2021

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/

package server;

import java.util.*;
import java.io.*;
import io.*;
import hash.*;
import data.*;
import java.nio.file.*;
import java.util.regex.*;
import com.mpatric.mp3agic.*;
import server.Server;
import plugins.Plugins;
import utils.Tools;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; 

public class PVA {

	static String keyword = "carola";

	static long debug = 0;

	static public TwoKeyHash config   	= new TwoKeyHash();
	static TwoKeyHash alternatives    	= new TwoKeyHash();
	static public TwoKeyHash texte    	= new TwoKeyHash();
	static TwoKeyHash context         	= new TwoKeyHash();
	static Vector<Reaction> reactions 	= new Vector<Reaction>();
	static Vector<Command> commands   	= new Vector<Command>();
	static Vector<Contact> contacts   	= new Vector<Contact>();
	static Vector<MailboxData> mailboxes  	= new Vector<MailboxData>();
	static StringHash timers	  	= new StringHash();
	static Vector<String> categories  	= new Vector<String>();
	static TimerTask tt;
	static IMAPTask it;
	static SearchTask st;
	static MetacacheTask mt;
	static Plugins pls;
	static Server server;
	static AIMessages aimsgs = new AIMessages();

	static String text = "";
	static String text_raw = "";
	static int mbxid = 1;
	static boolean reaction = false;
	static Dos dos = new Dos();
	static String[] za = null;
	static int c = 0;
	static String metadata_enabled  = "";
	
	static void log(String x) { System.out.println(x); }
	static String[] timedata;
	
        static String makeDate(long time) {

		Calendar d = Calendar.getInstance();
		d.setTime( new Date(time) );

                String[] months = config.get("conf","months").split(":");
                
                String minutes = ""+d.get(Calendar.MINUTE);
                if ( minutes.length()==1 ) minutes = "0"+minutes;
                
                return d.get(Calendar.DAY_OF_MONTH)+"."+( months[ d.get(Calendar.MONTH) ] )+" "+d.get(Calendar.HOUR_OF_DAY)+":"+ minutes;
        }

	private static String pa_outputid = "";
	
	private static boolean initPulseAudio() {
	
		if ( pa_outputid.isEmpty() ) {
			String[] outputs =  dos.readPipe("env LANG=C pactl list source-outputs").split("\n");
			String det = ""; // don't fill the real idString with possible wrong ids, we need to get sure!
			for(String o : outputs ) {
				if ( o.startsWith("Source Output #") ) {
					det = o.replaceAll("^.*#","").trim();
				}
				if ( o.toLowerCase().contains("node.name = \"alsa_capture.python3.") ) {
					pa_outputid  = det;
					break;
				}
			}
		} 
		if ( pa_outputid.isEmpty() ) return false;
		return true;
	}
	

	public static void say(String text, boolean wait) throws IOException {
		if ( !config.get("conf","cantalk" ).equals("no") ) {
			
			if ( pa_outputid.isEmpty() ) {
				// just in Case we did not get a valid result before!
				initPulseAudio();
			}
		
			if ( !pa_outputid.isEmpty() ) {
				// Disable output of recording node, to prevent pva from hearing itself
				// log ( "pactl set-source-output-mute "+ pa_outputid +" 1" );
				dos.readPipe("pactl set-source-output-mute "+ pa_outputid +" 1");
			}
			
			dos.writeFile( getHome()+"/.cache/pva/lastoutput", text );
			
			exec( (config.get("app","say").replace("%VOICE", config.get("conf","lCFang_short") )+config.get("conf","splitter")+  text ).split(config.get("conf","splitter")), wait);
						
			if ( !pa_outputid.isEmpty() ) {
				// enable the node again... or we will never hear from our pva again ;)
				// log ( "pactl set-source-output-mute "+ pa_outputid +" 0" );
				dos.readPipe("pactl set-source-output-mute "+ pa_outputid +" 0");
			}
		
		}
	}

        public static void say(String text) throws IOException {
		say( text, true );
	}

	// JSON Object is given by LLM, but the org.json package can't be shipped with distros, so we need to do it ourself, so .. don't wonder it's messy ;)

	public static String parseJSON(String json,String model) {
	
		json=json.replaceAll("\\\\.","");
		
		String[] pairs = json.split("(\",\"|},\")");
					
		String answere = "";
					
		for(String pair: pairs) {
//			log( "pair = "+ pair);
										
			if ( pair.contains(":") ) {
				String[] data = pair.split(":",2);
				if ( data.length > 1 ) {	

					String key = data[0].replaceAll("\"","");
					String value = data[1];
												
//					log("key="+ key +"\nvalue="+ value );

					if ( key.endsWith("\"") ) key = key.substring(0,key.indexOf("\"")-1);
					if ( value.endsWith("\"") ) value = value.substring(0,value.indexOf("\"",1)-1);

//					log("key="+ key +"\nvalue="+ value );
												
					if ( ( key.equals("response") || key.equals("content") ) && value.trim().length() > 1 ) {
						answere = value.substring(1).replaceAll("\\n","\n");
						aimsgs.addMessage(new AIMessage("assistant", model, answere ));
					}
				}
				if ( answere == null ) answere = "";
			} 
		}

		return answere;
		
	}

	static String filterAIThinking(String answere) {
	
		if ( answere.contains("003cthink003e") ) {
			int abis = answere.indexOf("003c/think003e")+"003c/think003e".length();
			if ( abis >= 0 ) 
				answere = answere.substring( abis );
		}
		return answere.replaceAll("\\*\\*","").replaceAll("\\*","\"");
	}
	
	// Speakers tend to pronounce 1746 as a year, not 1.746.
	// we translate this to a numberformat most speakers understand better.

	static String makeNumber(long c) {
		
		String b = "";
		String number = ""+c;
		for(int i=number.length();(i-3)>0;i=i-3) {
			// log("i="+i);
			b = "."+ number.substring(i-3,i) + b;
			// log("b="+b);
		}
		int modulo = number.length()%3;
		if ( modulo != 0 ) {
			b = number.substring(0, modulo)+ b;
		} else if ( b.length() > 0 ) {
			b = b.substring(1);
		} else b = number;
	
		return b;
	}

	static void exec(String cmd) throws IOException {
		exec(cmd, false );
	}

	static void exec(String cmd, boolean wait) throws IOException {
//	log( cmd );
		try {
			Process p = Runtime.getRuntime().exec( cmd );
			if ( wait ) {
				p.waitFor();
			}
		} catch (Exception e) {
				// we don't care 
		}
		reaction = true;
	}

	static void exec(String[] cmds) throws IOException {
		exec( cmds, false );
	}

	static public void exec(String[] cmds, boolean wait ) throws IOException {
		if ( cmds == null || cmds.length == 0 ) {
			log("EMPTY Command given to Exec()");
			return;
		}
		String x = "";
		for(String cmd : cmds) {
			x += cmd+"#|#";
			if (debug > 2 ) log( "argument:"+cmd );
			if ( cmd == null ) {
				log("exec():Illegal Argument NULL detected");
				reaction = false;
				return;
			} 
			if ( cmd.isEmpty() && cmds.length == 1 ) {
				// this can happen, if i.e. raisevolume is executed, but not configured!
				if ( debug > 2 ) log("exec():empty Argument \"\" detected");
				reaction = false;
				return;
			} 
		}
		try {
			Process p = Runtime.getRuntime().exec( cmds );
			if ( wait ) {
				p.waitFor();
			}
		} catch (Exception e) {
			// we don't care 
			log("we had a crash in exec("+x+"):\n"+e);
			e.printStackTrace();
		}
		reaction = true;
	}

	static boolean wort(String gesucht) {
	
//		log( "wort: text="+ text +" gesucht="+ gesucht);
	
		if ( text.contains(gesucht.toLowerCase()) ) return true;
		return false;
	}
	
	static boolean oder(String gesucht) {
		String[] args = gesucht.toLowerCase().split("\\|");
		for(String arg: args) {
//			System.out.println(text +" arg="+arg);
			if ( text.contains( arg ) ) 
				return true;	
				
		}
		return false;
	}

	static boolean und(String gesucht) {
		String[] args = gesucht.toLowerCase().split("\\|");
		for(String arg: args) {
			if ( ! text.contains( arg ) ) {
				return false;	
			}
		}
		return true;
	}

	static String replaceVariables(String term) {

//		log("LOOP-Start: "+ term );
		String rpl = "";
		String srx = Tools.zwischen( term, "{","}");
		if ( srx != null && srx.length() > 0 ) {
//			log( srx );

			String[] rargs = srx.split(":");
			// we need at least 3 Arguments to this or it won't work  

			if ( rargs.length == 3 ) {
				if ( rargs[0].equals("config") )
					rpl = config.get( rargs[1], rargs[2] );
										
				if ( rargs[0].equals("texte") ) 				
					rpl = texte.get( rargs[1], rargs[2] );

				if (rpl == null ) rpl = "";
										
//				log("replacement: |"+ rpl +"|" );

		
			} else rpl = ""; // avoid unsolvable loop-of-death 
								
		} else rpl = ""; // avoid unsolvable loop-of-death 

		term = term.replace("{"+srx+"}",rpl.replace("%VOICE", config.get("conf","lang_short")) );
								
//		log( term );
						
	 	return term;
	 }
	 
	static void handleMediaplayer(String servicename, String cmd) {

		try {
			String 	vplayer = dos.readPipe( config.get("mediaplayer","status").replaceAll("<TARGET>", servicename).replaceAll( config.get("conf","splitter")," ") );

			if ( debug > 1 ) log(  "handleMediaplayer: "+ config.get("mediaplayer","status").replaceAll("<TARGET>", servicename).replaceAll( config.get("conf","splitter")," " ) +"\nResult:"+ vplayer );
				
			if ( ! vplayer.trim().isEmpty() && vplayer.trim().contains("Playing") ) {
		
				// variant       double 0.8
		
				Double f = 0.5;
		
				String volume = dos.readPipe( config.get("mediaplayer","getvolume").replaceAll("<TARGET>", servicename).replaceAll( config.get("conf","splitter")," ") );
										
				if ( !volume.trim().isEmpty() && volume.contains("double") ) {
					volume = volume.substring( volume.indexOf("double")+6 ).trim();
					f = Double.parseDouble( volume );
		
					if ( cmd.equals("DECVOLUME") ) {
						f = f - 0.25;
						if ( f < 0.0 ) f = 0.0;
									
						dos.readPipe( config.get("mediaplayer","lowervolume").replaceAll("<TARGET>", servicename).replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
						reaction = true;
					}

					if ( cmd.equals("INCVOLUME") ) {
						f = f + 0.25;
						if ( f > 2.0 ) f = 2.0;
							
						dos.readPipe( config.get("mediaplayer","raisevolume").replaceAll("<TARGET>", servicename).replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
						reaction = true;
					}				
								
					if ( cmd.equals("DECVOLUMESMALL") ) {
						f = f - 0.05;
						if ( f < 0.0 ) f = 0.0;
						dos.readPipe( config.get("mediaplayer","lowervolume").replaceAll("<TARGET>", servicename).replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
						reaction = true;
					}
			
					if ( cmd.equals("INCVOLUMESMALL") ) {
						f = f + 0.05;
						if ( f > 2.0 ) f = 2.0;

						dos.readPipe( config.get("mediaplayer","raisevolume").replaceAll("<TARGET>", servicename).replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
						reaction = true;
					}
				}	
			} 
		
						
			if ( cmd.equals("VIDEOPLAYBACKPLAY")  ) {
				exec(config.get("mediaplayer","play").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}
		
			if ( cmd.equals("VIDEOPLAYBACKSTOP")  ) {
				exec(config.get("mediaplayer","stop").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}
		
			if ( cmd.equals("VIDEOPLAYBACKPAUSE")  ) {

				exec(config.get("mediaplayer","pause").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}
		
			if ( cmd.equals("VIDEOPLAYBACKTOGGLE")  ) {
				exec(config.get("mediaplayer","toggle").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}
		
			if ( cmd.equals("VIDEONEXTTRACK")  ) {
				exec(config.get("mediaplayer","nexttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
				if ( wort("übernächstes") ) exec(config.get("mediaplayer","nexttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}
		
			if ( cmd.equals("VIDEOPREVTRACK") ) {
		
				exec(config.get("mediaplayer","lasttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
						
				if ( wort("vorletztes") ) exec(config.get("mediaplayer","lasttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));;
			}
	
			if ( cmd.equals("VIDEONTRACKSFORWARDS") ) {
		
				for(int i=0;i<za.length;i++) 
					if ( text_raw.matches( ".* "+za[i]+" .*" ) )
						for(int j=0;j<i;j++ )
							exec(config.get("mediaplayer","nexttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
								
			}
	
			if ( cmd.equals("VIDEONTRACKSBACKWARDS") ) {
			
				for(int i=0;i<za.length;i++) 
					if ( text_raw.matches( ".* "+za[i]+" .*" ) )
						for(int j=0;j<i;j++ )
							exec(config.get("mediaplayer","lasttrack").replaceAll("<TARGET>", servicename).split(config.get("conf","splitter")));
			}

			if ( cmd.equals("VIDEOPLAYBACKFULLSCREENON") ) {

				dos.readPipe( config.get("mediaplayer","fullscreen").replaceAll("<TARGET>", servicename).replaceAll(config.get("conf","splitter")," ") );
				reaction = true;
			} 

			if ( cmd.equals("VIDEOPLAYBACKFULLSCREENOFF") ) {
				dos.readPipe( config.get("mediaplayer","windowmode").replaceAll("<TARGET>", servicename).replaceAll(config.get("conf","splitter")," ") );
				reaction = true;
			} 
			

		} catch (IOException e) {
			System.out.println(e.getMessage());
		}


	}

	static String formatMetadata(String filename, ID3v1 id3v1Tag ) {
	
		if ( id3v1Tag != null ) {

			return ( filename 
			+ config.get("conf","splitter") + id3v1Tag.getTrack() 
			+ config.get("conf","splitter") + id3v1Tag.getArtist() 
			+ config.get("conf","splitter") + id3v1Tag.getTitle() 
			+ config.get("conf","splitter") + id3v1Tag.getAlbum() 
			+ config.get("conf","splitter") + id3v1Tag.getYear() 
			+ config.get("conf","splitter") + id3v1Tag.getGenre() 
			+ config.get("conf","splitter") + id3v1Tag.getGenreDescription() 
			+ config.get("conf","splitter")	+ id3v1Tag.getComment() ).replaceAll("(\n|\r)"," ") + "\n";
			
		} else return "";

	}

	static String formatMetadata(String filename, ID3v2 id3v2Tag ) {
	
		if ( id3v2Tag != null ) {
			return ( filename.replace("'","\'") 
			+ config.get("conf","splitter") + id3v2Tag.getTrack() 
			+ config.get("conf","splitter") + id3v2Tag.getArtist() 
			+ config.get("conf","splitter") + id3v2Tag.getTitle() 
			+ config.get("conf","splitter") + id3v2Tag.getAlbum() 
			+ config.get("conf","splitter") + id3v2Tag.getYear() 
			+ config.get("conf","splitter") + id3v2Tag.getGenre() 
			+ config.get("conf","splitter") + id3v2Tag.getGenreDescription()
			+ config.get("conf","splitter") + id3v2Tag.getComment() 
			+ config.get("conf","splitter") + id3v2Tag.getComposer() 
			+ config.get("conf","splitter") + id3v2Tag.getPublisher() 
			+ config.get("conf","splitter") + id3v2Tag.getOriginalArtist() ).replaceAll("(\n|\r)"," ") + "\n" ;

		} else return "";
			
	}

	static String createMetadata(String start) {

		String[] files = start.split(config.get("conf","splitter"));
		StringBuffer data = new StringBuffer(3000000);
		
		TwoKeyHash tk = new TwoKeyHash();
		tk.put("proc","counter","0");
				
		final int max = 200;
				
                if ( files != null) {     
                        for(int i =0; i < files.length; i++ ) {
                        	// System.out.println("processing file "+ entries[i].toString() +" matches ("+type+")$ ??? "+  entries[i].toString().matches(".*("+type+")$") );
                        	if ( files[i].toLowerCase().matches(".*mp3$") ) {

				
					try {
						if ( Integer.parseInt( tk.get("proc","counter") ) > max ) do {
			                                Thread.sleep(50L);
			                        } while ( Integer.parseInt( tk.get("proc","counter") ) > max );

						if ( debug > 4 ) log("["+ tk.get("proc","counter") +" len="+ data.length() +"] Analyse mp3 "+i+"/"+ files.length+" ... "+ files[i] );
	                                        new AnalyseMP3(data, files[i], tk ).start();
	                                } catch ( Exception e ) {
	                                        log(e.toString());
	                                        e.printStackTrace();
	                                }

				}
			}
		} 

		if ( debug > 4 ) log("waiting for sub-processes to finish");
		try {
			int count = 0;
			if ( Integer.parseInt( tk.get("proc","counter") ) > 0 ) do {
				StringHash finfo = tk.get("files");
				Enumeration en = finfo.keys();
				if ( debug > 4 ) log("waiting for processes to finish.. counter="+ tk.get("proc","counter"));
				count = 0;
				while ( en.hasMoreElements() ) {
					String key = (String)en.nextElement();
					String value = finfo.get( key );
					if ( value.equals("0") )  {
						if ( debug > 4 ) log( key +"="+ value);
						count ++;
					}
				}
					
				
                                Thread.sleep(100L);
                        } while ( Integer.parseInt( tk.get("proc","counter") ) > 0 && count > 0 );

		} catch ( Exception e ) {
	                                        log(e.toString());
	                                        e.printStackTrace();
	        }
		
		return data.toString();
	}

	static String[] buildFileArray(String str,String rpl) {

		String[] x = str.split(" ");
		String[] y = String.join("\\\\ ",rpl.split(" ")).split(config.get("conf","splitter"));
		String[] z = new String[x.length-1+y.length];
	
		int idx = 0;								
		for(int i=0;i<x.length;i++) {
			if ( x[i].matches(".*(%U|%F).*")) {
				for ( int j = 0;j< y.length;j++) {
					z[idx++] = y[j];
				}
			} else {
				z[idx++] = x[i];
			}
		} 
		return z;
	}

	static AppResult __searchExternalApps(String path,String suchwort) {

		// we want to prefer matches with the Nameentry above the keywordentries, so we need to track both matches seperatly

		NumericTreeSort namematches = new NumericTreeSort();
		NumericTreeSort keywordmatches = new NumericTreeSort();

		if ( debug > 2 ) log("_searchExternalApps: searchpath="+ path +" searchterm="+ suchwort);

		File file            = new File(path);
                File[] entries       = file.listFiles();
		suchwort = suchwort.trim().toLowerCase();

		if ( debug > 3 ) log("_searchExternalApps: Name= Name["+config.get("conf","lang")+"]= Name["+config.get("conf","lang_short")+"]=");

                if ( entries != null ) {     
                        for(int i =0; i < entries.length; i++ ) {
				if ( debug > 2 ) log("_searchExternalApps: processing file "+ entries[i].toString() );
                        	try {
	                        	if ( entries[i].isDirectory() && !entries[i].getName().startsWith(".") ) {

						// directoryname contains searchword(s) so we add it entirely
						// log("add "+ entries[i].getCanonicalPath() +" mit *");
						
						// We have to assume, that the result is the best result we can get from this subdirectory and import it in our active NumericTreeSort
						// if it's the best we ever get, it will be on top of all results in our actual directory also
						
						AppResult r = __searchExternalApps( entries[i].getCanonicalPath() , suchwort);
						if ( r != null ) {
							if ( !r.namematches.isEmpty() ) { 
								namematches.add( r.namerelevance, r.namematches);
							} 
							if ( !r.keywordmatches.isEmpty() ) { 
								keywordmatches.add( r.keywordrelevance, r.keywordmatches);
							}
						}

	                        	} else if ( entries[i].toString().toLowerCase().endsWith(".desktop") ) {

						String[] content = dos.readFile( entries[i].getCanonicalPath() ).split("\n");
						
						// those hitflags are required because the order of "Exec=" and i.e. "Name=" is not fix. The flags indicate a match, even if Exec= wasn't found jet.
						// they also represent the relevance factor of that entry > -1 
						long nhit = -1;
						long khit = -1;
						String app_exe = "";
						for(String line: content) {
							line=line.trim();
							if ( line.startsWith("Exec=") ) {
								app_exe = line.substring( line.indexOf("=")+1 );
								
							}
							
							if ( debug > 3 ) log("_searchExternalApps: "+ entries[i].toString()+":"+ line  );
							
							if ( line.startsWith("Name=") || line.startsWith("Name["+config.get("conf","lang")+"]=") || line.startsWith("Name["+config.get("conf","lang_short")+"]=") ) {
								String name = line.substring( line.indexOf("=")+1 );
								if ( name.toLowerCase().contains(suchwort) ) {
									nhit = suchwort.length()*100/name.length(); // we can't know, if "Exec=" stands before or after Name*= in the desktopfile!
								}
								if ( debug > 3 ) log("_searchExternalApps: name="+name.toLowerCase()+" suchwort="+suchwort+" hit="+ nhit );
							}
							if ( line.startsWith("Keywords=") || line.startsWith("Keywords["+config.get("conf","lang")+"]=") || line.startsWith("Keywords["+config.get("conf","lang_short")+"]=") ) {
								String name = line.substring( line.indexOf("=")+1 ).toLowerCase();
								String[] keys = name.split(";");
								for(String key: keys) {
								
									if ( key.toLowerCase().contains(suchwort) || key.toLowerCase().equals( suchwort ) ) {
										khit = suchwort.length()*100/name.length(); // we can't know, if "Exec=" stands before or after Name*= in the desktopfile!
									}
								}
							}
							
							// if line starts with [ we hit a desktop file with multiply entries in it and need to reset the search. 
							
							if ( line.startsWith("[") ) {
								if ( nhit > -1 ) {
									if ( debug > 2 ) log( "found namematch for "+ suchwort +" in " +  entries[i].toString());

									if ( ! app_exe.trim().isEmpty() ) 
										namematches.add( nhit , app_exe.trim() );

									nhit = -1;
								}
								if ( khit > -1 ) {
									if ( debug > 2 ) log( "found keywordmatch for "+ suchwort +" in " +  entries[i].toString());

									if ( ! app_exe.trim().isEmpty() ) 
										keywordmatches.add( khit , app_exe.trim() );

									khit = -1;

								}
							} 
						}
				
						if ( nhit > -1 ) {
							// if the app is already in the list, ignore it.
							
							if ( debug > 2 ) log( "found namematch for "+ suchwort +" in " +  entries[i].toString());
						
							if ( ! app_exe.trim().isEmpty() ) 
								namematches.add( nhit , app_exe.trim() );

							nhit = -1;
						}
						if ( khit > -1 ) {
							if ( debug > 2 ) log( "found keywordmatch for "+ suchwort +" in " +  entries[i].toString());
						
							if ( ! app_exe.trim().isEmpty() ) 
								keywordmatches.add( khit , app_exe.trim() );
							
							khit = -1;
						}
					}
				} catch(IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		
		String n = "";
		String k = "";
		long nr = 0;
		long kr = 0;
		
		// If we have matches, the try-part does succeed, if it fails, we did not have a match and we use "" as default
		// n and nr depend on each other, so it's 100% safe to assume that, if one fails, both fail. 
		
		try {	String[] r = namematches.getValues_internal().split(","); n = r[r.length-1]; nr = Long.parseLong( namematches.getKeys_internal().split(",")[r.length-1] );    } catch (Exception e) { } 
		try {	String[] r = keywordmatches.getValues_internal().split(","); k = r[r.length-1]; kr = Long.parseLong( keywordmatches.getKeys_internal().split(",")[r.length-1] ); } catch (Exception e) { }
		
		// we now return our best match result.

		return new AppResult( n, k, nr, kr );
	}
	
	static String _searchExternalApps(String path,String suchwort) {
	
		// We prefer App Matches from "Name=" entries above "Keywords=" matches
	
		AppResult r = __searchExternalApps(path,suchwort);
		if ( r != null ) {
		
			if ( debug > 2 ) log("_searchExternalApps: "+ r.namematches +" or "+ r.keywordmatches);
			
			if ( ! r.namematches.isEmpty() ) {
				return r.namematches;
			}
			return r.keywordmatches;
		
		}
		return "";
	}
	
	public static String getDesktop() {
		return System.getenv("DESKTOP_SESSION").trim();
	}

	public static String getHome() {
		return System.getenv("HOME").trim();
	}

	static String searchExternalApps(String suchwort) {
	
		String filename ="";
		filename += _searchExternalApps("/usr/share/applications/", suchwort );
		if ( filename.isEmpty() ) 
			// TODO: Add more folderselection based on languages. Is there a database? How does gnome knows it on First Launch?
			if ( config.get("conf","lang").equals("de_DE") ) {
				filename += _searchExternalApps(getHome() + "/Schreibtisch", suchwort );
			} else  filename += _searchExternalApps(getHome() + "/Desktop", suchwort );
	
		return filename;
	}
	
	static String cacheSuche(String start,String suchwort,String type) {


		suchwort = suchwort.trim().toLowerCase();

		if ( debug > 4 ) log("cacheSuche:suchwort = "+ suchwort);
		
		if ( suchwort.isEmpty() ) return "";
		
		String[] files = start.split(config.get("conf","splitter"));
		String filename ="";

                if ( files != null ) {     
                        for(int i =0; i < files.length; i++ ) {
                        	if ( debug > 4 ) log("processing file "+ files[i].toString() +" matches ("+type+")$ ??? "+  files[i].toString().matches(".*("+type+")$") );
                        	if ( files[i].toLowerCase().endsWith(type) || files[i].toLowerCase().matches(".*("+type+")$") ) {

					if ( files[i].toLowerCase().matches( suchwort ) ) {
						if ( debug > 4 ) log("cacheSuche:filename:found="+ files[i]);
						filename += files[i]+config.get("conf","splitter");
					}

				}
			}
		}
		
		if ( type.contains("mp3") ) {
			if ( dos.fileExists( getHome()+"/.cache/pva/cache.metadata") ) {
				start = dos.readFile(getHome()+"/.cache/pva/cache.metadata");
				files = start.split("\n");
				if ( files != null ) {     
		                        for(int i =0; i < files.length; i++ ) {
		                   		if ( debug > 4 ) log("cacheSuche:mp3:metacache:search in file = "+ files[i] );
						if ( files[i].toLowerCase().matches( suchwort ) ) {
							if ( debug > 4 ) log("cacheSuche:metadata:found="+ files[i]);
							filename += files[i].split(config.get("conf","splitter"))[0]+config.get("conf","splitter");
						}
					}
				}
			}
		}
		
		return filename;
	}

	static String _suche(String start,String suchwort,String type) {
/*
		if (isInterrupted()) 
			return "";
*/
			
		File file = new File(start);
                File[] entries = file.listFiles();
		String filename ="";
		suchwort = suchwort.trim().toLowerCase();
		if ( suchwort.isEmpty() ) return "";
//	log(suchwort);
                if ( entries != null ) {     
                        for(int i =0; i < entries.length; i++ ) {
                        	// System.out.println("processing file "+ entries[i].toString() +" matches ("+type+")$ ??? "+  entries[i].toString().matches(".*("+type+")$") +" searchterm="+ suchwort );

                        	try {
	                        	if ( entries[i].isDirectory() && !entries[i].getName().startsWith(".") ) {
		                        	boolean found = true;
	                        		if ( suchwort.contains(" ") ) {						
			                        	String[] args = suchwort.split(" ");
		                        		for( String arg : args ) {
								arg = arg.trim();
								if ( !arg.isEmpty() && !entries[i].getName().toLowerCase().contains( arg ) ) found = false;
							}
						} else if ( !suchwort.equals("*") && !entries[i].getName().toLowerCase().contains( suchwort ) ) found = false;
							
						if ( found ) {
							// directoryname contains searchword(s) so we add it entirely
							// log("add "+ entries[i].getCanonicalPath() +" mit *");
							filename += _suche( entries[i].getCanonicalPath() , "*", type);
						} else {
							// continue search in this directory
							// log("add "+ entries[i].getCanonicalPath() +" mit "+ suchwort);
							filename += _suche( entries[i].getCanonicalPath() , suchwort, type);
						}	                        	
	                        	
	                        	} else if ( entries[i].toString().toLowerCase().endsWith(type) || entries[i].toString().toLowerCase().matches(".*("+type+")$") ) {

						String name = entries[i].toString().toLowerCase().replaceAll("-"," ").replaceAll("'","\'");

	                        		if ( suchwort.equals("*") ) {
		                        		filename += entries[i]+config.get("conf","splitter");
               						// log("545: found "+ entries[i] +" mit "+ suchwort );
	                        		} else {

	                        			String[] args = suchwort.split(" ");
	                        			boolean found = true;
	                        			for( String arg : args ) {
								arg = arg.trim();
								// System.out.println("subsuchwort="+ arg);
								if ( !arg.isEmpty() && !name.contains( " "+arg+" " ) &&  !name.startsWith( arg ) ) {
									found = false;
								}
							}
							if ( name.contains( suchwort ) ) found = true;
							
							if ( found ) {
								// log("560: found "+ entries[i] +" mit "+ suchwort );
								filename += entries[i]+config.get("conf","splitter");
							}
						}
				
					}
				} catch(IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		
		return filename;
	}

	public static String suche(String start,String suchwort,String type) {
	
		if ( start.contains(":") ) {

			String[] args = start.split(":");
			String filename = "";
			for( String path : args ) {
				filename += _suche( path,suchwort,type);
			}
			return filename;

		} else return _suche(start,suchwort,type);
	}

	static String sucheNachEmail(String name) {
	
		Enumeration<Contact> en = contacts.elements();
		while ( en.hasMoreElements() ) {
			Contact c = en.nextElement();
			if ( c.searchForName( name ) ) return c.getEmails();
        	}
		return "";
	}

	static String sucheNachTelefonnummer(String name) {
	
		Enumeration<Contact> en = contacts.elements();
		while ( en.hasMoreElements() ) {
			Contact c = en.nextElement();
//			log(c.getFullname());
			if ( c.searchForName( name ) ) return c.getPhones();
        	}
		return "";
	}

	static String einzelziffern(String nummer) {
		String x ="";
		for(int i=0;i<nummer.length();i++)
			x += nummer.charAt(i) +" ";
		return x;
	}

	static boolean saveConfig() {

		StringBuffer sb = new StringBuffer(50000); // yes, we think big ;)
	
			Enumeration en1 = config.keys();
			while ( en1.hasMoreElements() ) {
				String key = (String)en1.nextElement();
				StringHash sub = config.get( key );
				Enumeration en2 = sub.keys();
				while ( en2.hasMoreElements() ) {
					
					String k = (String)en2.nextElement();
					String v = sub.get( k );
					if ( key.equals("app") )
						sb.append( key +":\""+ k +"\",\""+ v +"\"\n" );
					if ( key.equals("chatgpt") && ( k.equals("model") || k.equals("mode") ) )
						sb.append( key +":\""+ k +"\",\""+ v +"\"\n" );
					if ( key.equals("ai") && ( k.equals("model") || k.equals("mode") ) )
						sb.append( key +":\""+ k +"\",\""+ v +"\"\n" );
					if ( key.equals("conf") && ( k.equals("lang_short") || k.equals("lang") || k.equals("keyword") || k.equals("hibernate") ) )
						sb.append( key +":\""+ k +"\",\""+ v +"\"\n" );

				}
			}

		return dos.writeFile( getHome()+"/.config/pva/pva.conf", sb.toString() );
	}

	static boolean checkMediaPlayback() {
		boolean playing = true;

		if ( !config.get("mediaplayer","status").isEmpty() ) {
			String allplayerservices = dos.readPipe( config.get("mediaplayer","find").replaceAll(config.get("conf","splitter")," ") );
			if ( ! allplayerservices.isEmpty() ) {
				String[] lines = allplayerservices.split("\n");
				for(String service : lines ) {
							
					if ( service.contains("org.mpris.MediaPlayer2") ) {
						
						String 	vplayer = dos.readPipe( config.get("mediaplayer","status")
										      .replaceAll("<TARGET>", 
										Tools.zwischen( service, "\"","\"") ).replaceAll( config.get("conf","splitter")," ") );

						if ( ! vplayer.trim().isEmpty() && vplayer.trim().contains("Playing") ) {
							playing = false; // because a player is running.
						}
					}
				}
			}
		}
				
		if ( ! dos.readPipe( "pgrep -f "+ config.get("audioplayer","pname").replaceAll( config.get("conf","splitter")," ") ).trim().isEmpty()  && !config.get("audioplayer","status").isEmpty() ) {
		String[] result = dos.readPipe( config.get("audioplayer","status").replaceAll(config.get("conf","splitter")," "),true).split("\n");
			for(String x : result ) {
				if ( x.contains("[paused]") ) break;
				if ( x.contains("TITLE") ) {
					playing = false; // because a player is running.
				}
			}
		}
		return playing;
	}

	static private Command parseCommand(String textToParse) {

		// Filter out "?" , just in case a third party app sends this to the pva port. it leads to an endless loop in EXEC() and/or readPipe()
		
		if ( textToParse.contains("?") ) textToParse = textToParse.replace("?","");

		text = textToParse;

		Command cf = new Command("DUMMY","","",""); // cf = commandFound
		for(int i=0; i < commands.size(); i++)	{

			Command cc = commands.get(i);
	
			if (debug > 0 ) log("matching \""+ cc.words +"\" against \""+ text +"\" match_und="+ und( cc.words ) +" match_matches="+ text.matches( ".*"+ cc.words +".*" ) +" negative="+ cc.negative+" !und="+ !und( cc.negative ));
	
			if ( 
				( 
					( !cc.words.contains(".*") && und( cc.words ) ) ||  
					( cc.words.contains(".*") && text.matches( ".*"+ cc.words +".*" ) ) ||
					( cc.words.startsWith("REGEXP:") && text.matches( cc.words.replace("REGEXP:","") ) )  // FULL REXEXP MODE 
				) 
				&& ( cc.negative.isEmpty() || !oder( cc.negative ) ) ) {
				
				Vector terms = new Vector();
				
				cf = cc;
						
				// Replace context related words i.e. the APP krita is often misunderstood in german for Kreta ( the greek island )
				// it's ok to replace it for STARTAPPS, but i may not ok to replace it i.e. in a MAP Context!
						
				StringHash r = context.get( cc.command );
				if ( r != null ) {
					Enumeration en = r.keys();
					while ( en.hasMoreElements() ) {
						String a = (String)en.nextElement();
						String b = r.get( a );
						
//						log( "replace:"+a+" => "+b);
						
						text = text.replaceAll(a,b);
					}
				}
				// make a raw copy .. it's needed for filterwords to be removed from the "search term" but still recognized as optional arguments 

				text_raw = text;

				// Apply special filter i.e. for binding words like "with"/"mit" 
				// if filter words are defined, lets remove them now. this simplyfies processing in the actual function.
				if ( ! cf.filter.isEmpty() ) {
					log( "replace: ("+ cf.filter +") => ");
					text = text.replaceAll("("+ cf.filter +")" , "");
				}
							
//						log( "replace: ("+ cf.words +") => ");

				// delete the command from the phrase
				
				if ( cc.words.startsWith("REGEXP:") && text.matches( cc.words.replace("REGEXP:","") ) ) {
									
					text = "";

				} else if ( !cc.words.contains(".*") && und( cc.words ) ) {
					// Remove via classic REGEX (arg1|arg2|arg3|...) => ""
					text = text.replaceAll( "("+cf.words+")", "" );
				} else {
				
					// Before we remove all RegExpressions, we search for lonely " .* " , invert the result to have only the parts that are represented by the ".*" are left.
					// not an easy task .. :(
					
					String rp = text;
					
					log(rp);
					
					String[] parts;
					if ( cf.words.endsWith(" .*") ) {
						parts = (cf.words+" ").split( Pattern.quote(" .* ") );
					} else if (cf.words.startsWith(".* ")) {
						parts = (" "+cf.words).split( Pattern.quote(" .* ") );
					} else parts = cf.words.split( Pattern.quote(" .* ") );
					
					for(String x: parts) {
						if ( !x.isEmpty() ) {
							if ( rp.startsWith(x) ) {
								rp=rp.replaceAll(x+" ","x:x");
							} else if ( rp.endsWith(x) ) {
								rp=rp.replaceAll(" "+x,"x:x");
							} else  rp = rp.replaceAll(" "+x+" ","x:x");
							if ( debug > 1 ) log("replace "+x+" : result "+ rp);
						}
					}
					
					rp = rp.trim();
					if ( rp.startsWith("x:x") ) rp = rp.substring(3);
				
					while ( rp.contains("  ") ) 
						rp = rp.replaceAll("[  ]+"," ");
				
					log(rp);
				
					String[] ra = rp.trim().split("x:x");
					for(String x: ra) 
						terms.add( x );
				
//					log("ra.length = "+ra.length +" and Vector.size = "+ terms.size());
					
					cf.terms = terms;
				
					// Remove REGEX
					text = text.replaceAll( cf.words ,"");
				}
				
//				log("found match: "+ cc.words +" text="+ text);
					
				break;
			
			}
		}
	
		return cf;
 					
	}

	static public void main(String[] args) {

		try {
			String configstoload = getHome()+"/.config/pva/pva.conf";

			NumericTreeSort ss = new NumericTreeSort();

			String debugLevel = System.getenv("DEBUG");
			if ( debugLevel == null || debugLevel.isEmpty() ) debugLevel = "0";
			debug = Long.parseLong(debugLevel);
			

			File configdir = new File( getHome() + "/.config/pva/conf.d");
	                File[] entries = configdir.listFiles();
	       	        if ( entries != null ) {     
        	                for(int i =0; i < entries.length; i++ ) {
        	                        if ( !entries[i].isDirectory() && !entries[i].getName().startsWith(".") && entries[i].getName().endsWith(".conf") ) {
        	                        	if ( entries[i].getName().matches("^[0-9]+-.*") ) {
							ss.add( Long.parseLong(entries[i].getName().split("-")[0]), entries[i].getAbsolutePath() );
						} else  ss.add( 0, entries[i].getAbsolutePath() );
					}
				}
			}
			
			if ( !ss.getValues_internal().trim().isEmpty() )
				configstoload = ss.getValues_internal().replaceAll(",",":")+ ":"+ configstoload ;
			
			ss.reset();

			configdir = new File("/etc/pva/conf.d");
	                entries = configdir.listFiles();
	       	        if ( entries != null ) {     
        	                for(int i =0; i < entries.length; i++ ) {
        	                        if ( !entries[i].isDirectory() && !entries[i].getName().startsWith(".") && entries[i].getName().endsWith(".conf") ) {
        	                        	if ( entries[i].getName().matches("^[0-9]+-.*") ) {
							ss.add( Long.parseLong(entries[i].getName().split("-")[0]), entries[i].getAbsolutePath() );
						} else  ss.add( 0, entries[i].getAbsolutePath() );
					}
				}
			}

			if ( !ss.getValues_internal().trim().isEmpty() )
				configstoload = ss.getValues_internal().replaceAll(",",":")+ ":"+ configstoload ;

			String[] cfiles = configstoload.split(":");
			for(String conffile: cfiles) {
				// read in config, if no custom config is present, load defaults.
				String[] conflines = null;

                        	if ( debug > 0 ) log( "load config: "+ conffile );
				
				if ( dos.fileExists( conffile ) ) {
					conflines = dos.readFile(conffile).split("\n");
				} else  conflines = dos.readFile("./pva.conf.default").split("\n");
				
				// our config is a two dimentional array 
				// in PHP this would look like $config[$key1][$key2] = $value

				for(String line : conflines) {
					if ( !line.trim().startsWith("#") && !line.trim().isEmpty() && line.contains(",") && line.contains(":") ) {
						try {
							String[] level1 = line.split(":",2);
							String[] level2 = level1[1].trim().replaceAll("^\"","").replaceAll("\"$","").trim().split("\",\"");
	
							// UPS! the above construct replaces |"key",""| into => |key","| which splits into just "key" with no value left, that's why we need to check for shorter Level2-Stringarrays
					
							if ( level1[0].trim().equals("alternatives") ) {
	
								alternatives.put(level2[0].trim() , level2[1].trim() , level2[2].trim());
	
							} else if ( level1[0].trim().equals("text") ) {
	
								texte.put(level2[0].trim() , level2[1].trim() , level2[2].trim());
	
							} else if ( level1[0].trim().equals("contextreplacements") ) {
	
								if ( level2.length == 3 ) {
									context.put( level2[0].trim() , level2[1].trim() , level2[2].trim() );
								} else  context.put( level2[0].trim() , level2[1].trim() , "" );
								
							} else if ( level1[0].trim().equals("reaction") ) {
								
								if ( level2.length == 3 ) {
								
									// overwrite "old" values by deleting them before adding the exact same combination
									// this approach has limits, but it will work in 99% of cases.
									
									if ( conffile.contains("-overwrite") ) for(int i=0; i < reactions.size(); i++)	{
										Reaction r = (Reaction)reactions.get(i);
										if ( r.positives.equals( level2[0].trim() ) && r.negatives.equals( level2[1].trim() ) ) {
											reactions.remove( r );													
										}
									}
									reactions.add( new Reaction( level2[0].trim() , level2[1].trim() , level2[2].trim() ) );
								} else {
									// overwrite "old" values by deleting them before adding the exact same combination
									// this approach has limits, but it will work in 99% of cases.
									
									if ( conffile.contains("-overwrite") ) for(int i=0; i < reactions.size(); i++)	{
										Reaction r = (Reaction)reactions.get(i);
										if ( r.positives.equals( level2[0].trim() ) && r.negatives.equals( "" ) ) {
											reactions.remove( r );		
										}
									}

									reactions.add( new Reaction( level2[0].trim() , "" , level2[1].trim() ) );
								}
								
							} else if ( level1[0].trim().equals("command") ) {
	
								if ( level2.length == 4 ) {
									commands.add( new Command( level2[0].trim() , level2[1].trim() , level2[2].trim(), level2[3].trim() ) );
								} else if ( level2.length == 3 ) { 
									commands.add( new Command( level2[0].trim() , level2[1].trim() , level2[2].trim(), "" ) );
								} else  commands.add( new Command( level2[0].trim() , level2[1].trim() , "" , "") );
	
							} else if ( level1[0].trim().equals("mailbox") ) {
							
								if ( level2.length == 8 ) {
								
									// log("mailbox.username="+ level2[1].trim() +" mailbox.secure = "+ level2[4].trim() );
								
									mailboxes.add( 
										new MailboxData( mbxid++, level2[0].trim(),
											     level2[1].trim(),
											     level2[2].trim(),
											     level2[3].trim(),
											     Boolean.parseBoolean(level2[4].trim()),Integer.parseInt(level2[5].trim()),
											     Boolean.parseBoolean(level2[6].trim()),Integer.parseInt(level2[7].trim()) )
									);
								} else {
									log("ERROR:syntaxerror:config:"+line);
								}
							
							} else { 
									
								if ( level2.length == 2 ) {
	
									config.put( level1[0].trim() , level2[0].trim() , level2[1].trim() );
	
								} else  config.put( level1[0].trim() , level2[0].trim() , "" );
	
							}
						} catch (Exception e) {
							log("ERROR:syntaxerror:config:"+line);
							log(e.getMessage());
							e.printStackTrace();
							return;
						} 
					}
				}
			}

			if ( dos.fileExists(  getHome()+"/.config/pva/timers.conf" ) ) {
				timedata = dos.readFile( getHome()+"/.config/pva/timers.conf" ).split("\n");
				for(String data: timedata) {
					if ( !data.trim().isEmpty() ) {
						String[] x = data.split(":");
						timers.put(x[0],x[1]);
					}
				}
			} else {
				timedata = "".split(":"); // create empty array
			}

			// check if we have metadata support is enabled
			
			metadata_enabled = config.get("conf","metadatabase");

			if ( metadata_enabled.equals("true") ) {
				
				if ( dos.fileExists( getHome()+"/.cache/pva/cache.metadata" ) ) {

					StringHash unique = new StringHash();
					String[] cachedata = dos.readFile( getHome()+"/.cache/pva/cache.metadata" ).trim().split("\n");
					for(String line : cachedata ) {
						String[] metacols = line.split( config.get("conf","splitter") );
						
						// log("found "+ metacols[7] );
						
						if ( metacols[7] != "null" && metacols[7] != "unknown" && metacols[7] != "Unknown" ) {
							// add Genre to list 
							
							if ( unique.get( metacols[7] ).equals("") ) {
								unique.put( metacols[7] , "1");
								categories.add( metacols[7] );
//								log( "add "+ metacols[7] );
							}
						}
					}
				}
			
			}

			// read in vcard cache
			// without this cache it would take several minutes to import addressbooks
			// from time to time you should refresh it, by just deleteing it
						
			String vcards = dos.readFile( getHome()+"/.cache/pva/vcards.cache" );
			if ( vcards.isEmpty() ) {
				StringHash adb = config.get("addressbook");
				if ( adb != null ) {
					say( texte.get( config.get("conf","lang_short"), "READPHONEBOOK") );

					Enumeration en = adb.keys();
					while ( en.hasMoreElements() ) {
						String key = (String) en.nextElement();
						String value = adb.get(key);
						log( key );
						String[] url = key.split("/");
						String basedomain = url[0]+"//"+url[2];
						String[] html = dos.readPipe("curl "+ key +" --anyauth -u \""+ value +"\" 2>/dev/null").split("\n");
						int c = 1, gesamt=0;
						for(String line : html )
							if ( line.contains("vcf\"><img") ) 
								gesamt++;
								
						for(String line : html ){
						
							if ( line.contains("vcf\"><img") ) {
								log("import Eintrag "+ c +"/"+gesamt);
								String href = Tools.zwischen(line,"href=\"","\"");
								String[] vcard = dos.readPipe("curl "+  basedomain+href +" --anyauth -u \""+ value +"\" 2>/dev/null").split("\n");
								Contact vcf = new Contact();
								for(String vline : vcard ) {
									vcf.parseInput( vline );
								}
								contacts.addElement( vcf );	
								c++;
							}
						
						}
				
					}
				}
	
				vcards = "";
				Enumeration<Contact> een = contacts.elements();
				if ( een != null ) while ( een.hasMoreElements() ) {
					Contact c = een.nextElement();
					vcards += c.exportVcard()+"XXXXXX---NEXT_ELEMENT---XXXXXX\n";
				}
				dos.writeFile(getHome()+"/.cache/pva/vcards.cache", vcards);
			} else {
				// log("Loading cache...");
				String[] x = vcards.split("XXXXXX---NEXT_ELEMENT---XXXXXX\n");
				for(String card : x) { 
					Contact vcf = new Contact();
					vcf.importVcard( card );
					contacts.addElement( vcf );	
				}
								
			}

			// for speed resons, we got often used content in variables.
			keyword = config.get("conf","keyword");

			// init AI
						
			StringHash ai = config.get("ai");
			if ( ai != null ) {

                boolean aiportreachable = false;
	            String nt = dos.readPipe("env LANG=C netstat -lna");
                                                
        	    if ( ! nt.isEmpty() && ai != null )
					for(String a: nt.split("\n") )
						if ( ai.get("port")!=null && a.matches( ".*:"+ ai.get("port") +".*LISTEN.*" ) ) aiportreachable = true;

				if ( aiportreachable ) {
					HTTP.apihost = ai.get("host");
					HTTP.apiport = ai.get("port");
					HTTP.get("/api/tags");
	
					String answere = HTTP.post("/api/generate","{\"model\": \""+ ai.get("model")+"\", \"prompt\": \"\\nGenerate a title following these rules:\\n    - The title should be based on the prompt at the end\\n    - Keep it in the same language as the prompt\\n    - The title needs to be less than 30 characters\\n    - Use only alphanumeric characters and spaces\\n    - Just write the title, NOTHING ELSE\\n\\n```PROMPT\\nHallo\\n```\", \"stream\": false}");
					log("INIT AI: "+ answere );	
					aimsgs.addMessage(new AIMessage("user", "User", "Hallo" ));
				} else {
					 log("AI Init failed - no ai service detected");
				}

			}

			// here MAIN really starts

			PVA pva = new PVA();

			log("start TimerTask");
			
			tt = new TimerTask(pva);
			tt.start();
			
			log("start IMAPTask");
			
			it = new IMAPTask(pva);
			it.start();

			log("start PluginLoader");
			
			pls = new Plugins(pva);

			log("PVA:main:init audio");

			if ( initPulseAudio() ) {
				// enable input source for sure, in case we got restarted in mid blockade of the mic input
				dos.readPipe("pactl set-source-output-mute "+ pa_outputid +" 0");
			}
			
			log("start server");
						
	                server = new Server( Integer.parseInt( config.get("network","port") ) , pva );
	                server.startServing();

			// Wait until be receive ctrl+c or the EXIT command is given
		
			pls.shutdown();
			
			log("PVA:main:shutdown");
			
			tt.interrupt();
			it.interrupt();
			Thread.currentThread().interrupt();

			log("PVA:main:shutdown2");
			
			// as this does not work all ... hardcore exit :D  Some subthreads seem to block the JVM shutdown, but there is no hint which one it is.
			
			String pid = dos.readPipe("/usr/bin/pgrep -u "+  System.getenv("USER").trim() +" -f server.PVA");
			if ( !pid.trim().isEmpty() ) {
				String[] lines = pid.trim().split("\n");
				for(String line : lines ) {
					log("kill -9 "+ line);
					dos.readPipe("kill -9 "+ line);
				}
			} else log("no pid to kill");

			log("PVA:main:shutdown3"); // will never be visible in the logs ... is everything works

		} catch (Exception e) {
				
			e.printStackTrace();
			System.out.println(e);
		
		}
	}

	public void handleInput(String extText) throws IOException,InterruptedException  {

			reaction = false;

			// JSON Object is given by vosk, but the org.json package can't be shipped with distros, so we need to do it ourself, so .. don't wonder ;)
			
			// Format to parse "{text:"spoken text"}"
		
			// log("handleInput: text="+ extText);
		
			if ( extText.contains(":") ) {
				if ( extText.split(":").length > 1 ) {
					text = Tools.zwischen( extText.split(":")[1],"\"","\"");
					if ( text == null ) text = "";
				} else text = "";
			} else {
				text = extText;
			}
			
			// REJECT ANY attempt to inject Code
			// 
			// no regex check here, as someone could try to trick this with escapes 

			if ( text.contains(";") || text.contains("|") || text.contains("'") || text.contains("\"") ) {
				log("Exploit detected on input: "+ text);
				return;
			}
		
//			log("handleInput: text="+ text );
		
			// RAW Copy of what has been spoken. This is needed in case a filter is applied, but we need some of the filtered words to make decisions.

			String text_raw = text; 

			// Exit if there is nothing to process

			if ( text.trim().isEmpty() ) return;


			if ( debug > 1 ) log("raw="+ text);

			if ( debug > 2 ) log("LANG="+ config.get("conf","lang") );

			// generate words for numbers 1-99
			// use $HOME/.config/pva/conf.d/02-numbers-language.conf to overwrite the german defaults, or, systemwide, /etc/pva/99-numbers-language.conf

			String[] bloecke = config.get("conf","blocks").split(":");
			String[] ziffern = config.get("conf","numerics").split(":");
			String zahlen = config.get("conf","numbers");
			for(String zig : bloecke ) {
				zahlen += zig+":";
				for(String zahl : ziffern ) 
					zahlen += zahl+ config.get("conf","numericsbindword") +zig+":";
			}
			
			za = zahlen.replaceAll(":$","").split(":");

//			for( String x: za) log( x );

//			log("TEXT:" + text);			

			// now some error corrections for bugs in vosk or the way you speak to your pc ;) 

			StringHash rep = config.get("replacements");
			if ( rep != null && rep.size() > 0  ) {

				text=text.trim();			
				Enumeration en = rep.keys();
				while(en.hasMoreElements() ) {
					String key = (String)en.nextElement();
					String value = rep.get(key);
					
					Matcher m = Pattern.compile("(?m)"+key ).matcher(text);
					
					if ( m.find() ) {
						text = text.replaceAll( key, value );
					}
				}
				text=text.trim();
			}
			
			// some context less reactions
			
			Vector<Reaction> temp = new Vector<Reaction>();

			for(int i=0; i < reactions.size(); i++)	{

				Reaction r = (Reaction)reactions.get(i);
			
				if ( und( r.positives ) && ( r.negatives.isEmpty() || !und( r.negatives ) ) ) {
					
					temp.add( r );
					
				}
			}
			
			boolean nightprotection = false;
			if ( config.get("conf","nightprotection").trim().toLowerCase().equals("on") ) {
				try {
					String[] times = config.get("conf","nighttime").split("-");
					if ( times.length == 2 ) {
						String[] nightprotection_start = times[0].split(":");
						String[] nightprotection_end   = times[1].split(":");

						long sh = Long.parseLong( nightprotection_start[0] );
						long sm = Long.parseLong( nightprotection_start[1] );
						long eh = Long.parseLong( nightprotection_end[0] );
						long em = Long.parseLong( nightprotection_end[1] );
						Calendar cnow = Calendar.getInstance(); 
						
						long h = cnow.get(Calendar.HOUR_OF_DAY);
						long m = cnow.get(Calendar.MINUTE);
				
						if ( h >= sh && m >= sm && ( h < eh || ( h == eh && m <= em ) ) )  
							nightprotection = true;		
		
					} else log("sorry, you messed up the time intervall for nightprotection! "+ config.get("conf","nighttime"));
				} catch (Exception e) {
					log("Sorry, you messed up the time intervall for nightprotection! "+ e);
					e.printStackTrace();
				}
			}

			if ( temp.size() > 0 && !nightprotection ) {
				Reaction r = null;
				String lastr = dos.readFile(getHome()+"/.cache/pva/reaction.last");
				// lastr == "" means, it did not exist				
				do {
					r = temp.get( (int)( Math.random() * temp.size() ) );
				} while ( r.answere.equals( lastr ) && temp.size()>1 );
			
				if ( checkMediaPlayback() ) 
					say( r.answere.replaceAll("%KEYWORD", keyword ),true);

				dos.writeFile( getHome()+"/.cache/pva/reaction.last" , r.answere );
			} else if (  temp.size() > 0 && nightprotection ) log("Silentmode active - Reaction surpressed");
			
			StringHash ai = config.get("ai");
			boolean aiportreachable = false;
			String nt = dos.readPipe("env LANG=C netstat -lna");
						
			if ( ! nt.isEmpty() && ai!=null) 
				for(String a: nt.split("\n") )
					if ( a.matches( ".*:"+ ai.get("port") +".*LISTEN.*" ) ) aiportreachable = true;
					
			if ( !wort(keyword) ) {

				// we need a sentence detection against the noise
				if ( ai != null ) {
				
					if ( ai.get("enable").equals("true") && aiportreachable ) {
						// check a: no reaction happend + freetalk mode + more than 3 words are used
						// OR
						// check b: keyword mode is enabled and keyword is in textblock
					
						if ( ( temp.size() == 0 && ai.get("mode").equals("freetalk") && text.trim().split(" ").length > 3 ) ||
						     ( ai.get("mode").equals("keyword") && wort( ai.get("keyword") ) ) ) {
						
							if ( ai.get("mode").equals("keyword") && wort( ai.get("keyword") ) ) 
								text = text.substring( text.indexOf(ai.get("keyword"))+ ai.get("keyword").length() ).trim();
						
							if ( ( ( ai.get("mode").equals("freetalk") && checkMediaPlayback() ) || !ai.get("mode").equals("freetalk") ) && text.trim().length()>0 ) {
					
//								log("ai:send:" + text);

								HTTP.apihost = ai.get("host");
								HTTP.apiport = ai.get("port");
								
								aimsgs.addMessage(new AIMessage("user", "User", text ));
								
//								log("messages = "+ aimsgs.toJSON() );

								String answere = HTTP.post("/api/chat","{\"model\":\""+ ai.get("model")+"\",\"stream\": false,\"messages\":"+ aimsgs.toJSON() +"}");
								if ( answere != null ) {
																	
									// Filter für Denkprozesse
									
									answere = filterAIThinking(parseJSON(answere,ai.get("model")).trim());

									log("we got back:" + answere);
								
									say( answere,true );
									reaction = true;
								}
							}
						} 
					} else if ( ai.get("bin") == null ) log("no config for ai  found");
				} else if ( debug > 2 ) log("no ai config");
			}
			
			StringHash cgpt = config.get("chatgpt");
			if ( !wort(keyword) ) {

				// we need a sentence detection against the noise
				
				if ( cgpt != null ) {
					if ( cgpt.get("enable").equals("true") && cgpt.get("bin") != null ) {
						// check a: no reaction happend + freetalk mode + more than 3 words are used
						// OR
						// check b: keyword mode is enabled and keyword is in textblock
					
						if ( ( temp.size() == 0 && cgpt.get("mode").equals("freetalk") && text.trim().split(" ").length > 3 ) ||
						     ( cgpt.get("mode").equals("keyword") && wort( cgpt.get("keyword") ) ) ) {
						
							if ( cgpt.get("mode").equals("keyword") && wort( cgpt.get("keyword") ) ) 
								text = text.substring( text.indexOf(cgpt.get("keyword"))+ cgpt.get("keyword").length() ).trim();
						
							if ( ( ( cgpt.get("mode").equals("freetalk") && checkMediaPlayback() ) || !cgpt.get("mode").equals("freetalk") ) && text.trim().length()>0 ) {
					
								log("chatgpt:send:" + text);
								String answere = dos.readPipe( config.get("chatgpt","bin") +" \""+ text +"\"" );
								if ( answere != null ) {
									answere = answere.trim();
									log("we got back:" + answere);
								
									say( answere,true );
									reaction = true;
								}
							}
						} // else say(  texte.get( config.get("conf","lang_short"), "CHATGPTNOTENOUGHWORDSTOPROCESS"), true );
					} else if ( cgpt.get("bin") == null ) log("no config for chatgpt found");
				} else if ( debug > 2 ) log("no chatgpt");
			}

			// now the part that interessts you most .. the context depending parser
			
			// before we start the static methods:
			// wort("term") means exactly this term in the variable "text"
			// oder("term|term2") means one of these terms
			// und("term|term2") means all of these terms, but the order is irrelevant  und("eins|zwei|drei") works on "drei zwei eins" same as on "sprecher nummer drei hatte um eins einen termin mit zwei leuten"
			// in later versions of this app, we will have a regexp database that will do this work as far as possible

			if ( wort(keyword) ) {
			
				// remove everything BEFORE the keyword, because the keyword can be at any position in a recording
				// also remove the keyword itself
				
				// ADVISE: remove any keyword that led to your reaction
				// in the sentence "carola i want listen to queen"  anything thats not part of the searchterm "queen" needs to go or your search is pointless :)
			
				text = text.substring( text.indexOf(keyword) + keyword.length() );

				// It can be very helpfull to output the sentence vosk heared to see, whats going wrong.

				log(text);

				// parse commands from config
				
				text = text.trim();
				
				Command cf = parseCommand( text );
				
				log ( "found "+ cf.command +": "+text );

				if ( cf.command.startsWith("EXEC:") ) {

					// Format: EXEC:[/path/to/]exec[<SPLITTER>argument1<SPLITTER>argument2...]
					// Example: "EXEC:pulse.outx:xqmmpx:xhdmi" translates to "pulse.out qmmp hdmi"
				
					// extract cmd
				
					String[] ecmd = cf.command.split(":",2);
					if ( ecmd.length > 1 ) {
						// Execute cmd without reprocessing

						String term = ecmd[1];
						
						// log( "EXEC:"+ term );
												
						while ( term.indexOf("{") >= 0 ) 
							term = replaceVariables( term );
						
						// replace Terms, IF a Vector cf.terms are present and filled
						
						if ( cf.terms.size() > 0 ) 
							for(int i=0;i<cf.terms.size();i++)
								term = term.replaceAll( "%"+i , (String)cf.terms.get(i) );						
						
						log( term );
	
						exec( term.split(config.get("conf","splitter")),true);
				
					} else {
						say( texte.get( config.get("conf","lang_short"), "SYNTAXERROR") );
					}
				}

				if ( cf.command.equals("AISWAP") ) {

					String[] aliases = config.get("ai","aliases").split(",");
					if ( cf.terms.size() > 0 ) {
						String newmode = ((String)cf.terms.get(0)).trim();
						
						for(String a : aliases ) {
							String[] opts = a.split("=");
							if ( opts[0].trim().equals( newmode )  ) {
								log( "ai:swap: "+ config.get("ai","mode") + " => "+ opts[1].trim() ) ;
								config.put("ai","mode", opts[1].trim() );
								reaction = true;
							}
						}
				
					} else {
						say( texte.get( config.get("conf","lang_short"), "SYNTAXERROR") );
					}
				}
				
				if ( cf.command.equals("CHATGPTSWAP") ) {

					String[] aliases = config.get("chatgpt","aliases").split(",");
					if ( cf.terms.size() > 0 ) {
						String newmode = ((String)cf.terms.get(0)).trim();
						
						for(String a : aliases ) {
							String[] opts = a.split("=");
							if ( opts[0].trim().equals( newmode )  ) {
								log( "chatgpt:swap: "+ config.get("chatgpt","mode") + " => "+ opts[1].trim() ) ;
								config.put("chatgpt","mode", opts[1].trim() );
								reaction = true;
							}
						}
				
					} else {
						say( texte.get( config.get("conf","lang_short"), "SYNTAXERROR") );
					}
				}


			
				// The so called Star Trek part :) 
				if ( cf.command.equals("AUTHORIZE") ) {

						if ( wort( config.get("code","alpha")) ) {
							String cmd = dos.readFile(getHome()+"/.cache/pva/cmd.last");
							if ( cmd.equals("exit") ) {

								say( texte.get( config.get("conf","lang_short"), "QUIT") );	

								// shutdown normally, kill python STT process 
								server.interrupt();

								String[] e = dos.readPipe("pgrep -i -l -a python").split("\n");
								for(String a : e ) {
									if ( a.contains("pva.py") ) {
										String[] b = a.split(" ");
										exec("kill "+ b[0] );
									}
								}
							}
							if ( cmd.equals("autoexit") ) {

								// shutdown normally, kill python STT process 
								
								server.interrupt();
								
								String[] e = dos.readPipe("pgrep -i -l -a python").split("\n");
								for(String a : e ) {
									if ( a.contains("pva.py") ) {
										String[] b = a.split(" ");
										exec("kill "+ b[0] );
									}
								}
							}
							if ( cmd.equals("compile") ) {
								say( texte.get( config.get("conf","lang_short"), "RECOMPILING") );	
								System.out.println( dos.readPipe("compile.sh") );
							}
							return;
						} else if ( wort( config.get("code","beta")) ) {
							String cmd = dos.readFile(getHome()+"/.cache/pva/cmd.last");
							if ( cmd.equals("unlockscreen") ) {
								say( texte.get( config.get("conf","lang_short"), "UNLOCKSCREENRESPONSE") );
								if ( getDesktop().equals("cinnamon") ) {
									dos.readPipe("/usr/bin/cinnamon-screensaver-command -e");
								} else if ( getDesktop().equals("gnome") ) {
									dos.readPipe("/usr/bin/gnome-screensaver-command -e");
								}
							}
							return;
						} else 	say( texte.get( config.get("conf","lang_short"), "FALSEAUTHCODE") );
					
				}
				
				if ( cf.command.equals("SHOWTIMER") ) {

					String mytext = "";
	
					for(String data: timedata) {
						if ( !data.trim().isEmpty() ) {
							String[] x = data.split(":");
							
							text += texte.get( config.get("conf","lang_short"), "SHOWTIMERRESPONSE")
								     .replaceAll("<TERM1>", makeDate( Long.parseLong( x[0] ) ) )
								     .replaceAll("<TERM2>", x[1])+"\n";	
						}
					}
					
					if ( mytext.isEmpty() ) {
						say( config.get( config.get("conf","lang_short"), "SHOWTIMERRESPONSENOTHING") );
					} else {
						log( mytext );
						say( mytext );
					}

				}
				
				// MAKETIMER 
				// Example: "<KEYWORD> erinnere mich um achtzehn uhr dreißig an linux am dienstag"
				// Example: "<KEYWORD> reminde me at <TIME> to <SUBJECT>"
				
				if ( cf.command.equals("MAKETIMER") ) {				
				
					String[] wochentage   = texte.get( config.get("conf","lang_short"), "WEEKDAYS").split("\\|");
					String[] words_future = texte.get( config.get("conf","lang_short"), "FUTUREKEYWORDS").split("\\|");
	
					boolean timer = false;
					Calendar rightNow = Calendar.getInstance();
	
					Pattern pattern = Pattern.compile( "("+ texte.get( config.get("conf","lang_short"), "MAKETIMERAT") +")" );
					String[] stepone = pattern.split( text_raw.replaceAll( keyword, "").trim() , 2 );
					if ( stepone.length == 2) {
					
						pattern = Pattern.compile( "("+ texte.get( config.get("conf","lang_short"), "MAKETIMERFOR") +")" );
						String[] steptwo = pattern.split( stepone[1], 2 );
						if ( steptwo.length == 2) {
							String time = steptwo[0].trim();
							String subject = steptwo[1].trim();

							// analyse time
							// we assume "today" , which is the absence of any match
					
							// log("time="+time +" subject="+ subject);
					
							int when = 0, hour = -1, minutes = 0, weekday = 0;

					
							for(int i=0;i< words_future.length;i++) 
								if ( text_raw.contains( words_future[i] ) ) 
									when = i;

							for(int i=0;i< wochentage.length;i++) 
								if ( text_raw.contains( wochentage[i] ) && !subject.contains( wochentage[i] ) ) 
									weekday = i+1; // JAVAs crude sonntag = 1 rule..

							
							String[] times = time.split( texte.get( config.get("conf","lang_short"), "MAKETIMERTIMEWORD") );
							if ( times.length == 1 ) {
								// only a full hour is given
								for(int i=0;i<za.length && i<24;i++) {
									if ( times[0].trim().matches( za[i] ) ) 
										hour = i;
								}
							} 
							
							if ( times.length == 2 ) {
								// HOUR + MINUTES
								for(int i=0;i<za.length && i<24;i++) {
									if ( times[0].trim().matches( za[i] ) ) 
										hour = i;
								}
								for(int i=0;i<za.length && i<60;i++) {
									if ( times[1].trim().matches( za[i] ) ) 
										minutes = i;
								}

							}
					
							log( hour + ":" + minutes );
							
							if ( hour > -1 ) timer = true;
							
//							log( rightNow.get(Calendar.DAY_OF_WEEK) +" sonntag="+ Calendar.SUNDAY);

//							log ( rightNow.getTime().toString() );
							
							if ( rightNow.get(Calendar.DAY_OF_WEEK) >= weekday && weekday != 0 ) {
								// add one week
								rightNow.add(Calendar.DAY_OF_MONTH, 7 );
								rightNow.set(Calendar.DAY_OF_WEEK, weekday );
							}
							
							rightNow.set(Calendar.HOUR_OF_DAY, hour );
							rightNow.set(Calendar.MINUTE, minutes );
							rightNow.set(Calendar.SECOND, 0 );
							rightNow.set(Calendar.MILLISECOND, 0 );

//							log ( rightNow.getTime().toString() );
							
							if ( when > 0 ) 
								for(int i=0;i<when;i++) {
									rightNow.add(Calendar.DAY_OF_MONTH, 1 );
								}
							
							log ( rightNow.getTime().toString() );
							timers.put( ""+rightNow.getTimeInMillis(), subject );
							tt.saveTimers();
						}
					}
	
					if ( timer ) {

						say( texte.get( config.get("conf","lang_short"), "MAKETIMEROK").replaceAll( "<TERM1>", makeDate( rightNow.getTimeInMillis() ) ));
					} else  say( texte.get( config.get("conf","lang_short"), "MAKETIMERERROR") );
				
				}
				
				if ( cf.command.equals("MAKETIMERRELATIVE") ) {				
				
					boolean timer = false;
					Calendar rightNow = Calendar.getInstance();

					Pattern pattern = Pattern.compile( "("+ texte.get( config.get("conf","lang_short"), "MAKETIMERATRELATIVE") +")" );
					String[] stepone = pattern.split( text_raw.replaceAll( keyword, "").trim() , 2 );
					if ( stepone.length == 2) {
						pattern = Pattern.compile( "("+ texte.get( config.get("conf","lang_short"), "MAKETIMERFOR") +")" );
						String[] steptwo = pattern.split( stepone[1], 2 );
						if ( steptwo.length == 2) {
							String time = steptwo[0].trim();
							String subject = steptwo[1].trim();

							// analyse time
							// we assume "today" , which is the absence of any match
					
							// log("time="+time +" subject="+ subject);
					
							int when = 0, multiply = 1;
	
							if ( text_raw.contains( texte.get( config.get("conf","lang_short"), "TIMEHOURS") ) ) {
								multiply = 60;
							} else 	if ( text_raw.contains( texte.get( config.get("conf","lang_short"), "TIMEDAYS") ) ) {
								multiply = 60*24;
							}
						
							// log( "multiply="+ multiply);

							for(int i=0;i<za.length;i++)
								if ( text_raw.matches( ".* "+za[i]+" .*" ) || text_raw.contains(" "+za[i]+" ") ) 
									when = i+1;

							if ( when > 0 ) {	
								for ( int i=0; i < when*multiply; i++ )
									rightNow.add(Calendar.MINUTE, 1 );
								rightNow.set(Calendar.SECOND, 0 );
								rightNow.set(Calendar.MILLISECOND, 0 );

								log ( rightNow.getTime().toString() );
								timers.put( ""+rightNow.getTimeInMillis(), subject );
								tt.saveTimers();
								timer = true;
							}
						}
					}
	
					if ( timer ) {

						say( texte.get( config.get("conf","lang_short"), "MAKETIMEROK").replaceAll( "<TERM1>", makeDate( rightNow.getTimeInMillis() ) ));
					} else  say( texte.get( config.get("conf","lang_short"), "MAKETIMERERROR") );
				
				}
				
				// repeat last cmd.. 

				boolean writeLastArg = true;

				if ( cf.command.equals("REPEATLASTCOMMAND") ) {
					String read = dos.readFile(getHome()+"/.cache/pva/cmd.last").trim();
					if ( !read.isEmpty() && !read.equals( cf.words ) ) {
						cf = parseCommand( read );
						writeLastArg = false;
					}
				}

				if ( cf.command.equals("REPEATLASTOUTPUT") ) {
					say( dos.readFile( getHome()+"/.cache/pva/lastoutput" ) );
				}
				
				// create caches
				
				if ( cf.command.equals("RECREATECACHE") ) {
		
					st = new SearchTask(new PVA(),cf);
					st.start();
					if (st != null ) {
						reaction = true;
					} else {
						say( texte.get( config.get("conf","lang_short"), "SUBPROCESSNOTRUNNING" ) );
					}

				}

				if ( cf.command.equals("ABORTMETACACHE") ) {
					if ( st == null || ! st.isAlive() ) {
						say( texte.get( config.get("conf","lang_short"), "EVERYTHINGISOK" ) );

					} else {
						st.stop();
						st = null;
						say( texte.get( config.get("conf","lang_short"), "ABORTEDCREATIONOFMETADATA" ) );
					}
					
				}	
				
				if ( cf.command.equals("SWAPNAME") ) {

					String subtext = text.replaceAll(cf.filter,"").replaceAll(cf.words,"").trim();
					
					if ( subtext.length() > 5 ) {
					
						config.put("conf", "keyword", subtext );
						
						// as it's now static server, we need to overwrite all changes
						
						keyword = subtext;
						
						say( texte.get( config.get("conf","lang_short"), cf.command+"RESPONSE").replaceAll("<TERM1>", subtext ) );

						saveConfig();
						
					} else if ( ! subtext.isEmpty() ) { 
					
						say( texte.get( config.get("conf","lang_short"), cf.command+"RESPONSE1").replaceAll("<TERM1>", subtext ) );
					
					} else {
						subtext = "nichts";
						say( texte.get( config.get("conf","lang_short"), cf.command+"RESPONSE2").replaceAll("<TERM1>", subtext ) );
					}
				}


				if ( cf.command.equals("SWAPALTERNATIVES") ) {

					String subtext = text.replaceAll(cf.filter,"").replaceAll(cf.words,"").trim();
					
					StringHash sub = alternatives.get(subtext);
					if ( sub != null ) {
						Enumeration en1 = sub.keys(); // This Enumeration has only one Entry 
						String changeapp = (String)en1.nextElement();
						String changevalue = sub.get(changeapp);
						log( "SWAP:"+ changeapp +" with "+ changevalue);
						String[] apps = changeapp.split("\\|");
						String[] values = changevalue.split("\\|");
						if ( apps.length == values.length ) {
							for(int i=0;i< apps.length;i++) {
								log( "SWAP:"+ apps[i] +" mit "+ values[i] );
								if ( apps[i].contains(":") ) {
									String[] arr = apps[i].split(":");
									if ( arr.length > 1 ) {
										config.put(arr[0],arr[1],values[i]);
									} else log("config error on alternative: "+ apps[i] );
								} else {		
									config.put("app",apps[i],values[i]);
								}
							}
						} else log( "apps="+ apps.length +" <> values="+ values.length);
						say( texte.get( config.get("conf","lang_short"), cf.command+"1").replaceAll("<TERM1>", changeapp.replace("say","Sprachausgabe")).replaceAll("<TERM2>",subtext));

						saveConfig();
						
					} else {
						say( texte.get( config.get("conf","lang_short"), cf.command+"2").replaceAll("<TERM1>", subtext ) );
					}
				}

							
				// selfcompiling is aware of errors  and this reads them out loud.
								
				if ( cf.command.equals("LASTERROR") ) {
					say( dos.readFile(getHome()+"/.cache/pva/lasterror.txt").replaceAll("PVA.java:","Zeile "));
				}

				// sometime we address carola itself i.e. shut yourself down:

				if ( cf.command.equals("RECOMPILE") || cf.command.equals("RECOMPILEWITHERRORREPORT") ) {

					String result = dos.readPipe("javac --release 8 PVA.java");
					if ( result.contains("error") ) {
						say( texte.get( config.get("conf","lang_short"), "RECOMPILE1") );
						dos.writeFile(getHome()+"/.cache/pva/lasterror.txt",result);
						if (  cf.command.equals("RECOMPILEWITHERRORREPORT") )
							say( result.replaceAll("PVA.java:","Zeile "));
					} else {
						say( texte.get( config.get("conf","lang_short"), "RECOMPILE2") );
					}
					System.out.println( result.trim() );		
				}				
				

				if ( cf.command.equals("EXIT") ) {
					dos.writeFile(getHome()+"/.cache/pva/cmd.last","exit");
					say( texte.get( config.get("conf","lang_short"), "EXIT") );
										
					return;
				}
				if ( cf.command.equals("AUTOEXIT") ) {
					dos.writeFile(getHome()+"/.cache/pva/cmd.last","autoexit");
					return;
				}
			
				// this is more a convinient way to debug addressbooks
			
				if ( cf.command.equals("LISTPHONEBOOK") ) {
					Enumeration<Contact> en = contacts.elements();
					while ( en.hasMoreElements() ) {
						Contact c = en.nextElement();
						log(c.getFullname());

					} 
					// if you see this somewhere
					reaction = true;
					// if reactions is false, carola code thinks it did not understand what to do
					// if exec() is used, reaction is set to true automatically, but not all functions use exec() so, here we need to set it manually.
				} 
				
				// "call someone"
				
				// "rufe klaus an" "klaus anrufen" "ich möchte mit klaus sprechen" "ich möchte mit klaus reden"
				// "rufe klaus festnetz an" "klaus mobil anrufen" ... festnetz + mobil + arbeit sind im Addressbuch gespeicherte Telefonnummern
				// ist nichts spezielle angegeben, nimmt er einfach die erste die er findet .. 
				
				if ( cf.command.equals("MAKEPHONECALL") ) {
				
					log("Telefonbuchsuche");
					
					// bindewörter wie mit an usw. entfernen + alle keywords / OUTSOURCED TO CONFIG
				
					String subtext = text.trim();
					
					// subtext contains our searchterm
					
					boolean stop = false;
					String ergebnis = sucheNachTelefonnummer( subtext );
					if ( !ergebnis.trim().isEmpty() ) {
						String[] lines = ergebnis.split("\n");
						for(String numbers: lines ) {
							String[] parts = numbers.replaceAll("\"","").split("=");
							parts[1] = parts[1].trim().replaceAll(" ","");
//							log(numbers +" => "+ einzelziffern(parts[1]) );
				
							if ( config.get("app","phone")!=null ){
								if ( lines.length > 1 ) {
									if ( ( text_raw.matches( texte.get( config.get("conf","lang_short"), "PHONEMOBILE" ) ) && numbers.contains("cell") ) ||
									     ( text_raw.matches( texte.get( config.get("conf","lang_short"), "PHONEWORK" ) ) && numbers.contains("work") ) ||
									     ( text_raw.matches( texte.get( config.get("conf","lang_short"), "PHONELANDLINE" ) ) && numbers.contains("home") ) ||
									     (   !text_raw.matches(texte.get( config.get("conf","lang_short"), "PHONEMOBILE" ) ) 
									      && !text_raw.matches(texte.get( config.get("conf","lang_short"), "PHONEWORK" ) ) 
									      && !text_raw.matches(texte.get( config.get("conf","lang_short"), "PHONELANDLINE" ) )
									     ) 
									   ) {
										if ( !stop ) {
											exec( (config.get("app","phone")+ parts[1]).split(config.get("conf","splitter")));
											stop = true;
										}
										
									} else say( texte.get( config.get("conf","lang_short"), "CANTDECIDEWHOMTOCALL") );
								} else {
									exec( (config.get("app","phone")+ parts[1] ).split(config.get("conf","splitter")));
								}
							} else log("keine telefonapp konfiguriert");
						}
			

					} else {
						say( texte.get( config.get("conf","lang_short"), "NUMBERNOTFOUND").replaceAll("<TERM1>", subtext ) );
					}
					reaction = true; // make sure, any case is handled.					
				}
				
				// make screenshot
			
				if ( cf.command.equals("MAKESCREENSHOT") ) {
					say( texte.get( config.get("conf","lang_short"), "MAKESCREENSHOT") );
					exec( config.get("app","screenshot").split(config.get("conf","splitter")));	
				}		
				
				// "what time is it?"				
				if ( cf.command.equals("REPORTTIME") ) {
					say( texte.get( config.get("conf","lang_short"), "REPORTTIME").replaceAll("<TERM1>", dos.readPipe("date +%H")).replaceAll("<TERM2>", dos.readPipe("date +%M")) );
				}			
				// "whats the systemload"			
				if ( cf.command.equals("REPORTLOAD") ) {
					say( texte.get( config.get("conf","lang_short"), "REPORTLOAD").replaceAll("<TERM1>", dos.readPipe("cat /proc/loadavg").split(" ")[0] ) );
				}						
			
				// "hows the weather" + options like now + today + tomorrow
				if ( cf.command.equals("CURRENTWEATHERNOW") ) {
				
						// NOW
						
						String line0 = texte.get( config.get("conf","lang_short"), "WEATHERLINE0");
						String line2 = texte.get( config.get("conf","lang_short"), "WEATHERLINE2");
						String line3 = texte.get( config.get("conf","lang_short"), "WEATHERLINE3");
						String line4 = texte.get( config.get("conf","lang_short"), "WEATHERLINE4");
						String line6 = texte.get( config.get("conf","lang_short"), "WEATHERLINE6");
						
						String wetter = dos.readPipe("curl wttr.in/"+config.get("conf","location")+"?0TAqM -H \"Accept-Language: "+ config.get("conf","lang").replace("_","-")+"\" ");
						String[] bericht = wetter.split("\n");
						text = "";
						for(int i=0;i<7;i++) {
							String line = bericht[i];
	
							if ( line.startsWith(" ") ) line = line.substring(15);
							
							if ( i == 0) text += line0.replaceAll("<TERM1>", line );
							if ( i == 2) text += line2.replaceAll("<TERM1>", line );
							if ( i == 3) text += line3.replaceAll("<TERM1>", line.replace("+","").replace("(",  " "+ texte.get( config.get("conf","lang_short"), "upto") +" " ).replace(")","")
										                             .replaceAll("°C", texte.get( config.get("conf","lang_short"), "°C")  ) ) ;
							if ( i == 4) text += line4.replaceAll("<TERM1>", line.replaceAll("km/h", texte.get( config.get("conf","lang_short"), "km/h") ).replaceAll("m/s", texte.get( config.get("conf","lang_short"), "m/s") ) );
							if ( i == 6) text += line6.replaceAll("<TERM1>", line.replace("mm", texte.get( config.get("conf","lang_short"), "mm") ) );
						}
						
						say( text );
						reaction = true;
				} 
				if ( cf.command.equals("CURRENTWEATHERNEXT") ) {
							
						// TODAY
				
						String wetter = dos.readPipe("curl wttr.in/"+config.get("conf","location")+"?1TAqM -H \"Accept-Language: "+ config.get("conf","lang").replace("_","-")+"\" ");
						String[] bericht = wetter.split("\n");
						text = "";
						
						String datum = (new java.util.Date()).toString();
						int h = Integer.parseInt( datum.split(" ")[3].split(":")[0] )/6;
						String[] phase = texte.get( config.get("conf","lang_short"), "TIMEOFDAYARRAY").split(":");
						
						for(int j=h;j<=h+1 && j<4;j++) {						
							for(int i=0;i< bericht.length;i++) {
								String line = bericht[i];
//								log(h+":"+i+":"+j+":"+line);
								if ( j == h && i == 0) {
									 text +=  texte.get( config.get("conf","lang_short"), "WEATHERNEXT").replaceAll("<TERM1>",line).replaceAll("<TERM2>", phase[j] );
								} else if ( i == 0 ) text += texte.get( config.get("conf","lang_short"), "WEATHERNEXT2").replaceAll("<TERM1>",phase[j] )+" ";
									 
								if ( i == 11 ) text += line.split("│")[j+1].substring(15);
								if ( i == 12 ) text += line.split("│")[j+1].substring(15).replace("+","").replace("(", texte.get( config.get("conf","lang_short"), "upto") ).replace(")","")
											   				 .replaceAll("°C",texte.get( config.get("conf","lang_short"), "°C"));
								if ( i == 15 ) {
									String me = line.split("│")[j+1].substring(15);
									if ( !me.startsWith("0.0 mm | 0%") ) text += " mit "+ me.replace("mm", texte.get( config.get("conf","lang_short"), "mmwith") ).replace("%", texte.get( config.get("conf","lang_short"), "%HUMIDITY")  );
								}
							}
						}
						text = text.replaceAll("     ","").replace(" | ","");
						say( text );
						reaction = true;
				
				}
				if ( cf.command.equals("CURRENTWEATHERTOMORROW") ) {
				
						// TOMORROW
				
						String wetter = dos.readPipe("curl wttr.in/"+config.get("conf","location")+"?2TAqM -H \"Accept-Language: "+ config.get("conf","lang").replace("_","-")+"\" ");
						String[] bericht = wetter.split("\n");
						text = "";
						
						String datum = (new java.util.Date()).toString();

						String[] phase = texte.get( config.get("conf","lang_short"), "TIMEOFDAYARRAY").split(":");

						for(int j=0;j<=3;j++) {						
							for(int i=0;i< bericht.length;i++) {
								String line = bericht[i];

								if ( j == 0 && i == 0) {
 									 text +=  texte.get( config.get("conf","lang_short"), "WEATHERTOMORROW").replaceAll("<TERM1>",line).replaceAll("<TERM2>", phase[j] );
								} else if ( i == 0 ) text += texte.get( config.get("conf","lang_short"), "WEATHERNEXT2").replaceAll("<TERM1>",phase[j] ) +" ";
									 
								if ( i == 21 ) text += line.split("│")[j+1].substring(15);
								if ( i == 22 ) text += line.split("│")[j+1].substring(15).replace("+","").replace("(",texte.get( config.get("conf","lang_short"), "upto")).replace(")","").replaceAll("°C", texte.get( config.get("conf","lang_short"), "°C") );
								if ( i == 25 ) {
									String me = line.split("│")[j+1].substring(15);
									if ( !me.startsWith("0.0 mm | 0%") ) text += " mit "+ me.replace("mm", texte.get( config.get("conf","lang_short"), "mmwith") ).replace("%", texte.get( config.get("conf","lang_short"), "%HUMIDITY") );
								}
							}
						}
						text = text.replaceAll("     ","").replace(" | ","");

						say( text );
						reaction = true;
				}
			
				// kill process ... appname
			
				if ( cf.command.equals("STOPAPP") ) {

					say( texte.get( config.get("conf","lang_short"), "STOPAPP").replaceAll("<TERM1>", text.replaceAll("("+ cf.words +")","") ).trim());
					
					if ( wort("firefox") )	exec("killall GeckMain");
					if ( wort("chrome") )	exec("killall google-chrome");
					if ( wort("chromium") )	exec("killall chromium-freeworld chromium-privacy-browser");
					if ( wort("wetter") )	exec("killall gnome-weather");
					if ( oder("karte|karten|kartenapp") )	exec("killall gnome-maps");
					if ( oder("film|video") ) 	exec("killall "+ config.get("videoplayer_short","pname"));
					if ( oder("musik|audio") ) 	exec("killall "+ config.get("audioplayer_short","pname") );
					if ( und("runes|of|magic") )	exec("killall gfclient.exe");

				}

				// start apps by name

				if ( cf.command.equals("OPENSOURCECODE") ) {
					exec( (config.get("app","txt") + " ./PVA.java").split(" ") );
					say( texte.get( config.get("conf","lang_short"), "OPENSOURCECODE") );
				}
				if ( cf.command.equals("OPENCONFIG") ) {
					exec( (config.get("app","txt") +" "+ getHome() +"/.config/pva/pva.conf").split(" ") );
					say( texte.get( config.get("conf","lang_short"), "OPENCONFIG") );
				}
			
				if ( cf.command.equals("OPENAPP") || cf.command.equals("STARTAPP") ) {

					String with_key = texte.get( config.get("conf","lang_short"), "OPENAPP-KEYWORD");

					String with = "";

					text = text.toLowerCase().trim();
					if ( text_raw.contains(  with_key ) ) {
						with = text.substring( text.indexOf( with_key )+ with_key.length() ).trim();
						if ( text.indexOf( with_key ) > 0 ) {
							text = text.substring( 0, text.indexOf( with_key ) );
						}
					}
						
					// in case our keywords are the first argument, we need to swap text+with : "öffne bilder mit gimp" which is more human like
						
					if ( text_raw.contains(  with_key ) && text.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER")  ) ) {
						log(" tausche Suchbegriff(e) "+ text + " mit " + with ); // not important logline

						String a = with; with = text;text = a;
					}

					String exe = ""; // LEAVE BLANC IF APP WAS NOT FOUND
					StringHash apps = config.get("app");
					Enumeration<String> keys = apps.keys();
					while ( keys.hasMoreElements() ) {
						String key = keys.nextElement();
						if ( wort(key) ) {
							exe = config.get("app", key);
							while ( exe.startsWith("%") && exe.endsWith("%") ) {
								exe = exe.substring(1,exe.length()-1);
								exe = config.get("app", exe);
							}
						}
					}

					// we don't wanne execute internal + external apps, if we found one internally. The FLAG REACTION is set, if something got executed via exec().

					if ( exe.isEmpty() ) 
						exe = searchExternalApps( text );

					if ( !exe.isEmpty() ) {
							if ( debug > 1 ) log( "OPENAPP:exe="+exe);
							if ( with.isEmpty() ) {
								String[] startapps = exe.split( config.get("conf","splitter") );
								for(String executeme : startapps ) {
									exec( executeme.replaceAll("(%U|%F)","").split(" ") );
								}
							} else {
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-PICS") ) ) {
									exec( buildFileArray(exe,dos.readFile(getHome()+"/.cache/pva/search.pics.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-MUSIC") ) ) {
									exec( buildFileArray(exe,dos.readFile(getHome()+"/.cache/pva/search.music.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-VIDEOS") ) ) {
									exec( buildFileArray(exe,dos.readFile(getHome()+"/.cache/pva/search.videos.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-DOCS") ) ) {
									exec( buildFileArray(exe,dos.readFile(getHome()+"/.cache/pva/search.docs.cache")));
								}
							}
					}

					if ( reaction ) {
					
						if ( cf.command.equals("OPENAPP") ) {
							say( texte.get( config.get("conf","lang_short"), "OPENAPP-RESPONSE").replaceAll("<TERM1>", text) );
						} else  say( texte.get( config.get("conf","lang_short"), "STARTAPP-RESPONSE").replaceAll("<TERM1>", text) );
						
					} else 	say( texte.get( config.get("conf","lang_short"), "OPENAPP-RESPONSE-FAIL").replaceAll("<TERM1>", text) );
										
				}

				if ( cf.command.equals("PLAYAUDIO") ) {
					if ( !cf.filter.isEmpty() ) cf.filter = "|"+ cf.filter;
					
//					String subtext = text.replaceAll("("+keyword+"|"+ cf.words + cf.filter +")","").trim();

					String subtext = text.trim(); // make a raw copy 
					
					log("Ich suche nach Musik : "+ subtext);
					
					boolean uncertainresult = false;

					String suchergebnis = "";
					
					if (  metadata_enabled.equals("true")
						&& dos.fileExists( getHome()+"/.cache/pva/cache.metadata" ) 
						&& dos.fileExists( getHome()+"/.cache/pva/music.stats") ) 
						
						{
						
						for(int i=0; i < categories.size(); i++) {

							// genres are unique in this vector, no performance issues expected
							
							String s = (String)categories.get(i);
							if ( s.trim().toLowerCase().equals( subtext.toLowerCase() ) ) {
							
								// readin stats file
							
								HashMap<String, Long> sm = new HashMap<String, Long>();
								String[] filenames = dos.readFile(getHome()+"/.cache/pva/music.stats").split("\n");
								for(String filename : filenames ) {
									if ( sm.get( filename ) != null ) {
										sm.put(filename, new Long( sm.get(filename).longValue() +1 ) );
									} else {
										sm.put(filename, new Long(1) );
									}
								}

								// sort it on occurance of a filename 
								
								NumericTreeSort ns = new NumericTreeSort();
								for (String key : sm.keySet()) {
									ns.add( sm.get( key ), key );
								}
								filenames = ns.getValues();		

								// Load the Metacache into memory
				
								StringHash metacache = new StringHash();
								String[] cachedata = dos.readFile( getHome()+"/.cache/pva/cache.metadata" ).trim().split("\n");
								for(String line : cachedata ) {
									String[] metacols = line.split( config.get("conf","splitter") );
									metacache.put( metacols[0], metacols[7] );
								}
								
								// now check if the filenames in the favoriteslist match the category, it's what we are looking for.
									
								for(String filename: filenames ) {
									if ( metacache.get( filename ).trim().toLowerCase().equals( s.trim().toLowerCase() ) ) {
										// MATCH .. play this file :D
										suchergebnis += filename + config.get("conf","splitter");
									}
								}
							}
						}
					}

					// keep searching with different methods ...

					if ( suchergebnis.isEmpty() ) {
						if ( !subtext.trim().isEmpty() && dos.fileExists(getHome()+"/.cache/pva/cache.musik") ) {
							log("Suche im Cache");
							suchergebnis = cacheSuche( dos.readFile(getHome()+"/.cache/pva/cache.musik"), ".*"+subtext+".*", config.get("conf","musicfilepattern") );
							if ( suchergebnis.isEmpty() ) {
								uncertainresult = true;
								String[] searchargs = subtext.split(" ");
			                       			String pattern = ".*";
			                       			for( String arg : searchargs ) {
			                       				if ( !arg.trim().isEmpty() ) 
										pattern += arg.trim() +".*";
								}
								if ( debug > 4 ) log("searchpattern = "+pattern );
								suchergebnis += cacheSuche( dos.readFile(getHome()+"/.cache/pva/cache.musik"), pattern, config.get("conf","musicfilepattern") );
							}
							if ( suchergebnis.isEmpty() ) {
								uncertainresult = true;
								String[] searchargs = subtext.split(" ");
			                       			String pattern = "(";
			                       			int pc = 0;
			                       			for( String arg : searchargs ) {
		                       					if ( !arg.trim().isEmpty() ) {
										pattern += arg.trim() +"|";
										pc++;
									}
								}
								if ( pattern.endsWith("|") ) pattern = pattern.substring(0,pattern.length()-1);
								pattern += "){1,}.*";
								String arg = "";
								for(int i=0;i<pc;i++) arg += pattern;
								if ( debug > 4 ) log("searchpattern = .*"+arg );
								suchergebnis += cacheSuche( dos.readFile(getHome()+"/.cache/pva/cache.musik"), ".*"+ arg, config.get("conf","musicfilepattern") );
							}
							if ( suchergebnis.isEmpty() ) {
								uncertainresult = true;
								String[] searchargs = subtext.split(" ");
			                       			String pattern = ".*(";
			                       			for( String arg : searchargs ) {
			                       				if ( !arg.trim().isEmpty() ) 
										pattern += arg.trim() +"|";
								}
								if ( pattern.endsWith("|") ) pattern = pattern.substring(0,pattern.length()-1);
								pattern += ").*";
								if ( debug > 4 ) log("searchpattern = "+pattern );
								suchergebnis += cacheSuche( dos.readFile(getHome()+"/.cache/pva/cache.musik"), pattern, config.get("conf","musicfilepattern") );
							}
			
						} else {
							log("Suche im Filesystem");
							suchergebnis = suche( config.get("path","music"), subtext, config.get("conf","musicfilepattern") );
						}
					}

					if (!suchergebnis.isEmpty() ) {	

						dos.writeFile(getHome()+"/.cache/pva/search.music.cache",suchergebnis);

						c = 0;

						TreeSort ts = new TreeSort();
						StringHash d = new StringHash();

						if ( ! text_raw.matches( texte.get( config.get("conf","lang_short"), "PLAYAUDIOADD") ) ) exec(config.get("audioplayer","clear").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","stop").split(config.get("conf","splitter")));

						String[] files = suchergebnis.split(config.get("conf","splitter"));
						for(String file : files ) {
							Path p = Paths.get( file );
							ts.add( p.getFileName().toString().replaceAll(",", "xx1xx"), file.replaceAll(",", "xx1xx") );
						}

						String[] erg  = ts.getValues();
						String[] keys  = ts.getKeys();
						suchergebnis = "";                				
				                for(int uyz=0;uyz<ts.size();uyz++) {
//				                	log("found: "+ keys[uyz].replaceAll("xx1xx", ",") );
				                	if ( d.get( keys[uyz] ).equals("") ) {
				                		suchergebnis += erg[uyz].replaceAll("xx1xx", ",")+config.get("conf","splitter");
				                		d.put( keys[uyz] , "drin");
								c++;
				                	}
				                }

						if ( c > 500 ) {
							log("Ich habe "+c+" Titel gefunden");
							say( texte.get( config.get("conf","lang_short"), "PLAYAUDIOFOUNDN").replaceAll("<TERM1>", makeNumber(c) ) );
							return	;				
						}

						if ( c > 1 ) {
							log("Ich habe "+c+" Titel gefunden");
							if ( ! uncertainresult ) {
								say( texte.get( config.get("conf","lang_short"), "PLAYAUDIOFOUND").replaceAll("<TERM1>", ""+c ));
							} else {
								say( texte.get( config.get("conf","lang_short"), "PLAYAUDIOFOUNDNOTHINGCONTINUE").replaceAll("<TERM1>", ""+subtext ).replaceAll("<TERM2>", ""+c ) );
							}
						}
						
						String[] args = suchergebnis.split(config.get("conf","splitter"));
						for( String filename : args) {
								// log("Füge "+ filename +" hinzu ");
								//log( dos.readPipe( config.get("audioplayer","enqueue").replaceAll(config.get("conf","splitter")," ") +" '"+ filename.replaceAll("'","xx2xx") +"'",true) );
								
								// add filename to list of results searched for statistical analysis
								// the "filename" is used as a key to the .cache/pva/cache.metadata db
								// which is used to guess favorites of genres as "jazz" later 
								
								if ( c < 25 ) dos.writeFile(getHome()+"/.cache/pva/music.stats", 
									       dos.readFile(getHome()+"/.cache/pva/music.stats") + filename +"\n"
									      );
								
								dos.readPipe( config.get("audioplayer","enqueue").replaceAll(config.get("conf","splitter")," ") +" '"+ filename.replaceAll("'","xx2xx") +"'",true);
						}

						exec( config.get("audioplayer","playpl").split(config.get("conf","splitter")));
						exec( config.get("audioplayer","play").split(config.get("conf","splitter")));
					} else {
						say( texte.get( config.get("conf","lang_short"), "PLAYAUDIOFOUNDNOTHING").replaceAll("<TERM1>", subtext ) );
						log("keine treffer");
					}
					
				}
				if ( cf.command.equals("LISTENTO") ) {
					String[] result = dos.readPipe( config.get("audioplayer","status").replaceAll(config.get("conf","splitter")," "),true).split("\n");
					for(String x : result ) 
						if ( x.contains("TITLE") ) {
							log("Ich spiele gerade: "+ x);
							say( x );
						}
					
					reaction = true;
				}
				
				// TODO: check if this section is really necessary
				
				if ( cf.command.equals("HELP") ) {
					String subtext = text.replaceAll("(hilfe|zu)","").trim();
					if ( wort("q m m p") ) {
						log(dos.readPipe("qmmp --help"));
						reaction = true;
					}
					
				}
				if ( cf.command.equals("PLAYVIDEO") ) {

					String subtext = text.trim();
					System.out.println("Ich suche nach Videos : "+ subtext);
					String suchergebnis = suche( config.get("path","video"), subtext, ".mp4|.mpg|.mkv|.avi|.flv" );

//					System.out.println("suche: "+ suchergebnis );

 					if (!suchergebnis.isEmpty() ) {	

						dos.writeFile(getHome()+"/.cache/pva/search.video.cache",suchergebnis);					

						TreeSort ts = new TreeSort();

						String[] files = suchergebnis.split(config.get("conf","splitter"));
						for(String file : files ) {
							Path p = Paths.get( file );
							ts.add( p.getFileName().toString(), file );
						}
						String[] erg  = ts.getValues();
						suchergebnis = "";                				
				                for(int uyz=0;uyz<ts.size();uyz++) suchergebnis += erg[uyz]+config.get("conf","splitter");

						dos.readPipe("killall "+ config.get("videoplayer","pname"));
						exec( (config.get("videoplayer","enqueue") +config.get("conf","splitter")+suchergebnis).split(config.get("conf","splitter")) );
						log("Fertig mit hinzufügen ");

					}
					
				}

				if ( cf.command.equals("COMPOSEEMAIL") ){

					String subtext = text.trim();
					String[] worte = subtext.split(" ");
					String to = "";
					String subject = "";
					String body = "";
					
					for ( int i=0;i<worte.length;i++ ) {
						if ( worte[i].trim().equals( texte.get( config.get("conf","lang_short"), "COMPOSETO") ) ) {
							to = worte[i+1];
							i=i+1;
						}
						if ( worte[i].trim().equals( texte.get( config.get("conf","lang_short"), "COMPOSESUBJECT") ) ) {
							for(int j=i+1;j<worte.length;j++) {
								if ( worte[j].trim().equals( texte.get( config.get("conf","lang_short"), "COMPOSEBODY")) ) {
									i=j; break;
								} 

								subject += worte[j]+" ";
							}
						}
						if ( worte[i].trim().equals( texte.get( config.get("conf","lang_short"), "COMPOSEBODY") ) ) {
							for(int j=i+1;j<worte.length;j++) {
								body += worte[j].replaceAll( texte.get( config.get("conf","lang_short"), "COMPOSEBLOCK") ,"\n\n" )
										.replaceAll( texte.get( config.get("conf","lang_short"), "COMPOSECOMMA") ,", " )
										.replaceAll( texte.get( config.get("conf","lang_short"), "COMPOSEPOINT") ,". " )
										 +" ";
							}
							i = worte.length; break;
						}
					}

					subject = subject.trim();
					body = body.trim();

					log("ich suche "+to);
					String ergebnis = sucheNachEmail( to );
					if ( !ergebnis.trim().isEmpty() ) {
						String[] lines = ergebnis.split("\n");
						for(String numbers: lines ) {
							log(numbers);
							String[] parts = numbers.replaceAll("\"","").split("=");
							parts[1] = parts[1].trim();
							log("Die Emailadresse von "+ subtext + " ist " + parts[1]);
							log( dos.readPipe( ( config.get("app","mail") +" -compose \"to='"+ parts[1] +"',subject='"+subject+"',body='"+body+"'\"") ) );
							reaction = true;
						} 
					} else {
						say( texte.get( config.get("conf","lang_short"), "COMPOSEERROR").replaceAll("<TERM1>", subtext) );
					}

				}
				
				if ( cf.command.equals("SEARCH4EMAIL") ){
					String subtext = text.trim();
					String ergebnis = sucheNachEmail( subtext );
					if ( !ergebnis.trim().isEmpty() ) {
						String[] lines = ergebnis.split("\n");
						for(String numbers: lines ) {
							log(numbers);
							String[] parts = numbers.replaceAll("\"","").split("=");
							parts[1] = parts[1].trim();
							log("Die Emailadresse von "+ subtext + " ist " + parts[1]);
							say( texte.get( config.get("conf","lang_short"), "EMAILFOUNDR1")
												  .replaceAll("<TERM1>", subtext ).replaceAll("<TERM2>", parts[1]) );
						} 
					} else {
						say( texte.get( config.get("conf","lang_short"), "EMAILFOUNDR2").replaceAll("<TERM1>", subtext ) );
					}
				}
				
				if ( cf.command.equals("SEARCH4PHONENUMBER") ){
					String subtext = text.trim();
					String ergebnis = sucheNachTelefonnummer( subtext );
					if ( !ergebnis.trim().isEmpty() ) {
						String[] lines = ergebnis.split("\n");
						for(String numbers: lines ) {
							log(numbers);
							String[] parts = numbers.replaceAll("\"","").split("=");
							parts[1] = parts[1].trim();
							if ( numbers.contains("cell") ) 
								say( texte.get( config.get("conf","lang_short"), "PHONENUMBERFOUNDR1")
												          .replaceAll("<TERM1>", subtext ).replaceAll("<TERM2>", einzelziffern(parts[1]) ));
							if ( numbers.contains("home") ) 
								say( texte.get( config.get("conf","lang_short"), "PHONENUMBERFOUNDR2")
												          .replaceAll("<TERM1>", subtext ).replaceAll("<TERM2>", einzelziffern(parts[1]) ));
							if ( numbers.contains("work") ) 
								say( texte.get( config.get("conf","lang_short"), "PHONENUMBERFOUNDR3")
												          .replaceAll("<TERM1>", subtext ).replaceAll("<TERM2>", einzelziffern(parts[1]) ));
						} 
					} else {
						say( texte.get( config.get("conf","lang_short"), "PHONENUMBERFOUNDR0").replaceAll("<TERM1>", subtext ) );
					}
				}
				
				if ( cf.command.equals("WEBSEARCH") ) {
				
					String subtext = text.trim();

					System.out.println("Ich suche nach "+ subtext);

					String sm  = config.get("searchengines", config.get("app","searchengine") );
					
					System.out.println("Ich suche mit "+ sm );
					
					exec( ( config.get("app","web")+" "+sm.replaceAll("<query>", subtext.replaceAll(" ","+") )  ).split(" "));

				}
				if (  cf.command.equals("PICSEARCH") ) {

					String subtext = text.replaceAll("(jpg|png|gif|jpeg)","").trim();

					log("Ich suche nach "+ subtext);
					
					String suchergebnis = "";					
					
					suchergebnis = suche( config.get("path","pics"), subtext, ".jpg|.png|.gif|.jpeg|.svg" );

					if (!suchergebnis.isEmpty() ) {

						dos.writeFile(getHome()+"/.cache/pva/search.pics.cache",suchergebnis );
						
						String[] files = suchergebnis.split(config.get("conf","splitter"));
						for(String filename : files ) {

							System.out.println(" gefunden : "+ filename);
							// if the user gave the option to open the found files OR there is only one result, we open the default app with this file. 
							// This is the OLD way to open files from a searchresult. The NEW way is to say i.E. "open pics with gimp"
							
							if ( text_raw.matches( texte.get( config.get("conf","lang_short"), "OPENRESULTWITHAPP" )  ) || files.length == 1 ) { 
								exec( (config.get("app","gfx")+config.get("conf","splitter")+ filename).split(config.get("conf","splitter")) );
							}
						}
						String anzahl = ""+files.length;
						if ( files.length == 1 ) anzahl = "einen";
						if ( !text_raw.matches( texte.get( config.get("conf","lang_short"), "OPENRESULTWITHAPP" ) ) && files.length > 1 ) {
							say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT1" ).replaceAll("<TERM1>", ""+anzahl) );
						} else  say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT2" ).replaceAll("<TERM1>", ""+anzahl) );
						
						log("Fertig mit Suchen ");

					} else say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT3" ).replaceAll("<TERM1>", ""+ subtext ) );
				}						
						
				if ( cf.command.equals("DOCSEARCH") ) {

					String subtext = text.trim();

					log("Ich suche nach "+ subtext);
					
					String suchergebnis = "";					
					
					if ( text_raw.contains("pdf") ) {
						suchergebnis = suche( config.get("path","docs"), subtext, ".pdf" );
					} else if ( !text_raw.contains("pdf") ) {
						suchergebnis = suche( config.get("path","docs"), subtext, ".txt|.pdf|.odp|.ods|.odt" );
					} 
					// System.out.println("suchergebnis: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	
					
						dos.writeFile(getHome()+"/.cache/pva/search.docs.cache",suchergebnis);
					
						String[] files = suchergebnis.split(config.get("conf","splitter"));
						for(String filename : files ) {

							log(" gefunden : "+ filename);
							if ( text_raw.matches( texte.get( config.get("conf","lang_short"), "OPENRESULTWITHAPP" )  ) || files.length == 1 ) { 
								if ( filename.endsWith(".txt") ) {
									exec( (config.get("app","txt")+config.get("conf","splitter")+ filename).split(config.get("conf","splitter")) );
								}
								if ( filename.endsWith(".pdf") ) {
									exec( (config.get("app","pdf")+"+x:x"+ filename).split(config.get("conf","splitter")) );
								}
								if ( filename.endsWith(".ods") ) {
									exec( (config.get("app","office") +config.get("conf","splitter")+ filename).split(config.get("conf","splitter")) );
								}
								if ( filename.endsWith(".odt") ) {
									exec( (config.get("app","office") +config.get("conf","splitter")+ filename).split(config.get("conf","splitter")) );
								}
								if ( filename.endsWith(".odp") ) {
									exec( (config.get("app","office") +config.get("conf","splitter")+ filename).split(config.get("conf","splitter")) );
								}
							}

						}
						String anzahl = ""+files.length;
						if ( files.length == 1 ) anzahl = "einen";
						if ( !text_raw.matches( texte.get( config.get("conf","lang_short"), "OPENRESULTWITHAPP" ) ) && files.length > 1 ) {
							say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT1" ).replaceAll("<TERM1>", ""+anzahl) );
						} else  say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT2" ).replaceAll("<TERM1>", ""+anzahl) );
						
						log("Fertig mit Suchen ");
						
					} else say( texte.get( config.get("conf","lang_short"), "PICSEARCHRESULT3" ).replaceAll("<TERM1>", ""+ subtext ) );
					
				}
			
				if ( cf.command.equals("DOCREAD") ) {

					String subtext = text.trim();

					log("Ich suche nach "+ subtext);
					
					String suchergebnis = "";					

					if ( wort("text") ) {					
						suchergebnis = suche( config.get("path","docs"), subtext, ".txt" );
					} else if ( wort("pdf") ) {
						suchergebnis = suche( config.get("path","docs"), subtext, ".pdf" );
					} else {
						suchergebnis = suche( config.get("path","docs"), subtext, ".txt|.pdf" );
					} 
					// System.out.println("suchergebnis: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	
					
						dos.writeFile(getHome()+"/.cache/pva/search.docs.cache",suchergebnis);
					
						String[] files = suchergebnis.split(config.get("conf","splitter"));
						int c = 1;
						for(String filename : files ) {

							System.out.println(" gefunden : "+ filename);
							
							// TODO: rebuild with number array
							
							if ( files.length == 1 ||
							
								( wort("ersten") && c==1 ) ||
								( wort("zweiten") && c==2 ) ||
								( wort("dritten") && c==3 )
							
							 ) { 
							 
							 	System.out.println("Lese "+ filename);
								if ( filename.endsWith(".txt") ) {
									say( filename );
								}
								if ( filename.endsWith(".pdf") ) {
									String txt =  dos.readPipe("pdftotext -nopgbrk "+ filename+ " /tmp/."+keyword+".say").replace("%VOICE", config.get("conf","lang_short") );
									// log( txt );
									// dos.writeFile("/tmp/."+keyword+".say", txt);
									// special construct : "say filename" reads in the files content 

									say( "x:x/tmp/."+keyword+".say");
								}
							}
							c++;
						}

						if ( files.length > 1 ) {
							say( texte.get( config.get("conf","lang_short"), "DOCREADRESPONSE" ).replaceAll("<TERM1>", ""+ files.length ));
						} 
						System.out.println("Fertig mit suchen nach Text");
					}
					
				}

				if ( cf.command.equals( "PLAYMUSIC") ) {
					if ( ! text_raw.matches( texte.get( config.get("conf","lang_short"), "PLAYMUSICRANDOM" ) ) ) {
						exec(config.get("audioplayer","play").split( config.get("conf","splitter") ));
					} else {	
						log("Ich katalogisiere Musik ...");
						String suchergebnis = "";
						if ( dos.fileExists(getHome()+"/.cache/pva/cache.musik") ) {
							suchergebnis = dos.readFile(getHome()+"/.cache/pva/cache.musik");
						} else {
							suchergebnis = suche( config.get("path","music"), "*", config.get("conf","musicfilepattern") );
							dos.writeFile(getHome()+"/.cache/pva/cache.musik", suchergebnis);
						}
						// System.out.println("suche: "+ suchergebnis );
						if (!suchergebnis.isEmpty() ) {	

							if ( !text_raw.matches( texte.get( config.get("conf","lang_short"), "PLAYMUSICADD" ) ) ) 
								exec(config.get("audioplayer","clear").split( config.get("conf","splitter") ));

							exec(config.get("audioplayer","stop").split( config.get("conf","splitter") ));
	
							String[] files = suchergebnis.split( config.get("conf","splitter") );
														
							int i = (int)( Math.random() * files.length);

							dos.readPipe( config.get("audioplayer","enqueue").replaceAll( config.get("conf","splitter")," ") +" '"+ files[i].replaceAll("'","xx2xx") +"'",true);
	
							exec( config.get("audioplayer","playpl").split( config.get("conf","splitter") ));
							Thread.sleep(1000);
							log( "starte Musik" );
							exec( config.get("audioplayer","play").split( config.get("conf","splitter") ));
						} 
					}
				}
				
				if ( cf.command.equals("ADDTITLE") ) {
					log("Ich katalogisiere Musik ...");
					String suchergebnis = "";
					if ( dos.fileExists(getHome()+"/.cache/pva/cache.musik") ) {
						suchergebnis = dos.readFile(getHome()+"/.cache/pva/cache.musik");
					} else {
						suchergebnis = suche( config.get("path","music"), "*", config.get("conf","musicfilepattern") );
						dos.writeFile(getHome()+"/.cache/pva/cache.musik", suchergebnis);
					}
					// System.out.println("suche: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	

						exec(config.get("audioplayer","stop").split( config.get("conf","splitter") ));

						String[] files = suchergebnis.split( config.get("conf","splitter") );

						for(int i=0;i<za.length;i++) 
							if ( text_raw.matches( ".* "+za[i]+" .*" ) )
								for(int j=0;j<=i;j++ ) {
													
									int p = (int)( Math.random() * files.length);
						 			dos.readPipe( config.get("audioplayer","enqueue").replaceAll( config.get("conf","splitter")," ") +" '"+ files[p].replaceAll("'","xx2xx") +"'",true);
	
								}
						exec( config.get("audioplayer","playpl").split( config.get("conf","splitter") ));
						Thread.sleep(1000);
						log(  "starte Musik" );
						exec( config.get("audioplayer","play").split( config.get("conf","splitter") ));
					} 
				}

				if ( cf.command.equals("STOPAPP") ) {
				
					if ( text_raw.contains(  texte.get( config.get("conf","lang_short"), "KEYWORDMUSIC" )  )  ) 
						exec(config.get("audioplayer","stop").split(config.get("conf","splitter")));
				}
				
				// Check 
			
				String aplayer = dos.readPipe( "pgrep -f "+ config.get("audioplayer","pname").replaceAll( config.get("conf","splitter")," ") );
				if ( ! aplayer.trim().isEmpty() ) {
					aplayer = dos.readPipe( config.get("audioplayer","status").replaceAll( config.get("conf","splitter")," ") );
				}

				if ( ! aplayer.trim().isEmpty() && ! aplayer.contains("paused") ) {
					
					if ( cf.command.equals("DECVOLUME") ) {
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
					}
					if ( cf.command.equals("INCVOLUME") ) {
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
					}				
					
					if ( cf.command.equals("DECVOLUMESMALL") ) {
						exec(config.get("audioplayer","lowervolume").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("INCVOLUMESMALL") ) {
						exec(config.get("audioplayer","raisevolume").split(config.get("conf","splitter")));
					}

				} 

				if ( cf.command.equals("AUDIONEXTTRACK")  ) {
					exec(config.get("audioplayer","nexttrack").split(config.get("conf","splitter")));
					if ( wort("übernächstes") ) exec(config.get("audioplayer","nexttrack").split(config.get("conf","splitter")));
				}

				if ( cf.command.equals("AUDIOPREVTRACK") ) {

					exec(config.get("audioplayer","lasttrack").split(config.get("conf","splitter")));
					
					if ( wort("vorletztes") ) exec(config.get("audioplayer","lasttrack").split(config.get("conf","splitter")));;
				}

				if ( cf.command.equals("AUDIONTRACKSFORWARDS") ) {

					for(int i=0;i<za.length;i++) 
						if ( text_raw.matches( ".* "+za[i]+" .*" ) )
							for(int j=0;j<i;j++ )
								exec(config.get("audioplayer","nexttrack").split(config.get("conf","splitter")));
							
				}

				if ( cf.command.equals("AUDIONTRACKSBACKWARDS") ) {

					for(int i=0;i<za.length;i++) 
						if ( text_raw.matches( ".* "+za[i]+" .*" ) )
							for(int j=0;j<i;j++ )
								exec(config.get("audioplayer","lasttrack").split(config.get("conf","splitter")));
							
				}

				if ( cf.command.equals("AUDIOSKIPFORWARD") ) {
	
					exec( (config.get("audioplayer","forward")+"20").split(config.get("conf","splitter")) );
	
				} 
	
				if ( cf.command.equals("AUDIOSKIPFORWARDN") ) {
			
					for(int i=0;i<za.length;i++) 
						if ( text_raw.matches( ".* "+za[i]+" .*" ) )
							exec( (config.get("audioplayer","forward")+i ).split(config.get("conf","splitter")) );
				}

				if ( cf.command.equals("AUDIOSKIPBACKWARD") ) {
	
					exec( (config.get("audioplayer","backward")+"20").split(config.get("conf","splitter")) );
	
				}

				if ( cf.command.equals("AUDIOSKIPBACKWARDN") ) {
					for(int i=0;i<za.length;i++) 
						if ( text_raw.matches( ".* "+za[i]+" .*" ) )
							exec( (config.get("audioplayer","backward")+i ).split(config.get("conf","splitter")) );
				}

				if ( cf.command.equals("AUDIOTOGGLE") ) {
					exec(config.get("audioplayer","togglemute").split(config.get("conf","splitter")));
				}
				if ( cf.command.equals("AUDIOPAUSE") ) {
					exec(config.get("audioplayer","pause").split(config.get("conf","splitter")));
				}


				// if there is no videoplayerstatuscmd configured, we may run in mediaplayermode, and skip the direct videoplayer component, as it's part of the mediaplayer part anyway
				
				if ( !config.get("videoplayer","status").isEmpty() ) {

					String vplayer = dos.readPipe( "pgrep -f "+ config.get("videoplayer","pname").replaceAll( config.get("conf","splitter")," ") );
					if ( ! vplayer.trim().isEmpty() && !config.get("videoplayer","status").isEmpty() ) {
						vplayer = dos.readPipe( config.get("videoplayer","status").replaceAll( config.get("conf","splitter")," ") );
					}

					if ( ! vplayer.trim().isEmpty() && vplayer.trim().contains("Playing") ) {
	
						// variant       double 0.8
	
						Double f = 0.5;
	
						String volume = dos.readPipe( config.get("videoplayer","getvolume").replaceAll( config.get("conf","splitter")," ") );
						
						if ( !volume.trim().isEmpty() ) {
							volume = volume.substring( volume.indexOf("double")+6 ).trim();
							f = Double.parseDouble( volume );
							//log("vol="+ f);
						}
	
						if ( cf.command.equals("DECVOLUME") ) {
							f = f - 0.25;
							if ( f < 0.0 ) f = 0.0;
							// log( config.get("videoplayer","lowervolume").replaceAll("<TERM>", ""+ f ).replace("'","\"").replaceAll(config.get("conf","splitter")," ") );
							//exec(config.get("videoplayer","lowervolume").replaceAll("<TERM>", ""+ f ).split(config.get("conf","splitter")));
							// dbus-send via exec() does only work for Getting value, not for Setting one ... no idea why.
							
							dos.readPipe( config.get("videoplayer","lowervolume").replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
							reaction = true;;
						}
						if ( cf.command.equals("INCVOLUME") ) {
							f = f + 0.25;
							if ( f > 2.0 ) f = 2.0;
							// exec(config.get("videoplayer","raisevolume").replaceAll("<TERM>", ""+ f ).split(config.get("conf","splitter")));
							
							dos.readPipe( config.get("videoplayer","raisevolume").replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
							reaction = true;;
						}				
						
						if ( cf.command.equals("DECVOLUMESMALL") ) {
							f = f - 0.05;
							if ( f < 0.0 ) f = 0.0;
							// exec(config.get("videoplayer","lowervolume").replaceAll("<TERM>", ""+ f ).split(config.get("conf","splitter")));
							dos.readPipe( config.get("videoplayer","lowervolume").replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
							reaction = true;;
						}
	
						if ( cf.command.equals("INCVOLUMESMALL") ) {
							f = f + 0.05;
							if ( f > 2.0 ) f = 2.0;
							// exec(config.get("videoplayer","raisevolume").replaceAll("<TERM>", ""+ f ).split(config.get("conf","splitter")));
							dos.readPipe( config.get("videoplayer","raisevolume").replaceAll("<TERM>", ""+ f ).replaceAll(config.get("conf","splitter")," ") );
							reaction = true;;
						}
	
					} 
	
					
					if ( cf.command.equals("VIDEOPLAYBACKPLAY")  ) {
						exec(config.get("videoplayer","play").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("VIDEOPLAYBACKSTOP")  ) {
						exec(config.get("videoplayer","stop").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("VIDEOPLAYBACKPAUSE")  ) {
						exec(config.get("videoplayer","pause").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("VIDEOPLAYBACKTOGGLE")  ) {
						exec(config.get("videoplayer","toggle").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("VIDEONEXTTRACK")  ) {
						exec(config.get("videoplayer","nexttrack").split(config.get("conf","splitter")));
						if ( wort("übernächstes") ) exec(config.get("videoplayer","nexttrack").split(config.get("conf","splitter")));
					}
	
					if ( cf.command.equals("VIDEOPREVTRACK") ) {
	
						exec(config.get("videoplayer","lasttrack").split(config.get("conf","splitter")));
						
						if ( wort("vorletztes") ) exec(config.get("videoplayer","lasttrack").split(config.get("conf","splitter")));;
					}

					if ( cf.command.equals("VIDEONTRACKSFORWARDS") ) {
	
						for(int i=0;i<za.length;i++) 
							if ( text_raw.matches( ".* "+za[i]+" .*" ) ) 
								for(int j=0;j<i;j++ ) 
									exec(config.get("videoplayer","nexttrack").split(config.get("conf","splitter")));
								
					}
	
					if ( cf.command.equals("VIDEONTRACKSBACKWARDS") ) {
						log("springe videos zurück: "+ text);
						for(int i=0;i<za.length;i++) 
							if ( text_raw.matches( ".* "+za[i]+" .*" ) )
								for(int j=0;j<i;j++ )
									exec(config.get("videoplayer","lasttrack").split(config.get("conf","splitter")));
					}
	
				}
	
				// now we handle all mediaplayers
				// IF you configure both, a dbus serviceable videoplayer AND the mediaplayer api, you will end up with some funny effects by "toogle" cmds.
				
				if ( !config.get("mediaplayer","find").isEmpty() ) {

					String allplayerservices = dos.readPipe( config.get("mediaplayer","find").replaceAll(config.get("conf","splitter")," ") );
					if ( ! allplayerservices.isEmpty() ) {
						String[] lines = allplayerservices.split("\n");
						for(String service : lines ) {
						
							if ( service.contains("org.mpris.MediaPlayer2") ) {
								handleMediaplayer( Tools.zwischen( service, "\"","\""),  cf.command ) ;
							}
						}
					}
				}
	
				if ( cf.command.equals("UNLOCKSCREEN") ) {
					if ( getDesktop().equals("cinnamon") || getDesktop().equals("gnome") ) {
						dos.writeFile(getHome()+"/.cache/pva/cmd.last","unlockscreen");
						say( texte.get( config.get("conf","lang_short"), "UNLOCKSCREEN") );
						return;
					}
					
					say( texte.get( config.get("conf","lang_short"), "UNLOCKSCREENRESPONSEERROR") );
					
				}
				
				if ( cf.command.equals("LOCKSCREEN") ) {
					if ( getDesktop().equals("cinnamon") ) {
						dos.readPipe("/usr/bin/cinnamon-screensaver-command -l");
						say( texte.get( config.get("conf","lang_short"), "LOCKSCREEN") );
						return;
					}
					if ( getDesktop().equals("gnome") ) {
						dos.readPipe("/usr/bin/gnome-screensaver-command -l");
						say( texte.get( config.get("conf","lang_short"), "LOCKSCREEN") );
						return;
					}
					
					say( texte.get( config.get("conf","lang_short"), "LOCKSCREENRESPONSEERROR") );
					
				}
				
				if ( cf.command.equals("MAKEMETACACHE") ) {
				
					if ( mt == null || ! mt.isAlive() ) {
				
						mt = new MetacacheTask(new PVA());
						mt.start();
						if (mt != null ) {
							reaction = true;
						} else {
							say( texte.get( config.get("conf","lang_short"), "SUBPROCESSNOTRUNNING" ) );
						}
					}
				}

				// LLM Support
				
				if ( cf.command.equals("AICLEARHISTORY") ) {
				
					aimsgs.clear();
					reaction = true;
					say( texte.get( config.get("conf","lang_short"), "AIHISTORYCLEARED" ) );

				}

				if ( cf.command.equals("AISWAPMODEL") ) {
				
					if ( ai != null && ai.get("enable").equals("true") && aiportreachable ) {

						String[] aliases = config.get("ai","models").split(",");
						if ( cf.terms.size() > 0 ) {
							String newmode = ((String)cf.terms.get(0)).trim();
							
							for(String a : aliases ) {
								String[] opts = a.split("=");
								if ( opts[0].trim().equals( newmode )  ) {
									log( "ai:swapmodel: "+ config.get("ai","model") + " => "+ opts[1].trim() ) ;
									config.put("ai","model", opts[1].trim() );
									reaction = true;
								}
							}
					
						} else {
							say( texte.get( config.get("conf","lang_short"), "SYNTAXERROR") );
						}
					}
		
				}
				
				if ( cf.command.equals("AIIDENTIFYMODEL") ) {
				
					if ( ai.get("imagemodel").isEmpty() ) {

						say( texte.get( config.get("conf","lang_short"), "AIRESPONSEMODELSOLO" ).replaceAll("<TERM1>", ai.get("model") ));
						
					} else {
						
						say( texte.get( config.get("conf","lang_short"), "AIRESPONSEMODEL" ).replaceAll("<TERM1>", ai.get("model")).replaceAll("<TERM2>", ai.get("imagemodel")) );

					}
					
				}				
					
				if ( cf.command.equals("AIIDENTIFYCAM") || cf.command.equals("AIIDENTIFYCAMFREE") || cf.command.equals("AIIDENTIFYDESKTOP") || cf.command.equals("AIIDENTIFYFULLDESKTOP") ) {

					if ( ai != null && ai.get("enable").equals("true") && aiportreachable ) {

						HTTP.apihost = ai.get("host");
						HTTP.apiport = ai.get("port");
						if ( !ai.get("apitimeout").isEmpty() )
							HTTP.timeout = Integer.parseInt( ai.get("apitimeout") );

						String bimages = "";
						String content = "";
						
						dos.readPipe("rm -f /tmp/webcam.jpg /tmp/webcam-cropped.jpg");
						
						// log("text="+text_raw.replace(""+keyword+"",""));
						
						aimsgs.clear();
						
						if (  cf.command.equals("AIIDENTIFYCAM") ) {

								dos.readPipe("fswebcam -d "+ ai.get("device") +" -r "+ ai.get("resolution") +" --jpeg "+ ai.get("quality") +" --no-banner /tmp/webcam.jpg");
								bimages = "\""+ dos.readPipe("base64 -w 0 /tmp/webcam.jpg").trim() +"\"";
								content = ai.get("languageprompt")+texte.get( config.get("conf","lang_short"), "AIIDENTIFYIMAGE" );

						} else if (  cf.command.equals("AIIDENTIFYCAMFREE") ) {

								dos.readPipe("fswebcam -d "+ ai.get("device") +" -r "+ ai.get("resolution") +" --jpeg "+ ai.get("quality") +" --no-banner /tmp/webcam.jpg");
								bimages = "\""+ dos.readPipe("base64 -w 0 /tmp/webcam.jpg").trim() +"\"";
								content = ai.get("languageprompt")+text_raw.replace(""+keyword+"","");
								
						} else if ( cf.command.equals("AIIDENTIFYDESKTOP") ) {

								dos.readPipe("gnome-screenshot -f /tmp/webcam.jpg");

								if ( ai.get("crop").isEmpty() ) {
						
									bimages = "\""+ dos.readPipe("base64 -w 0 /tmp/webcam.jpg").trim() +"\"";
								
								} else {
									dos.readPipe("convert \"/tmp/webcam.jpg\"  -crop "+ ai.get("crop") +" /tmp/webcam-cropped.jpg");
									bimages = "\""+ dos.readPipe("base64 -w 0 /tmp/webcam-cropped.jpg").trim() +"\"";
								}
									
								content = ai.get("languageprompt")+text_raw.replace(""+keyword+"","");
						} else if ( cf.command.equals("AIIDENTIFYFULLDESKTOP") ) {

								dos.readPipe("gnome-screenshot -f /tmp/webcam.jpg");

								bimages = "\""+ dos.readPipe("base64 -w 0 /tmp/webcam.jpg").trim() +"\"";
								content = ai.get("languageprompt")+text_raw.replace(""+keyword+"","");
						}


						dos.readPipe("rm -f /tmp/webcam.jpg /tmp/webcam-cropped.jpg");

						String model = ai.get("model");
						if ( !ai.get("imagemodel").isEmpty() ) model = ai.get("imagemodel");

						String answere = HTTP.post("/api/chat","{\"model\":\""+ model +"\",\"stream\": false,\"messages\":"+ 
							"[{\"role\": \"user\", \"model\":\"User\",\"date\":\""+
								LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") )+
							"\",\"content\":\""+ content +"\",\"images\": ["+ bimages +"]}]}");

						if ( debug > 1 ) log("we got back(2):" + answere);
						
						if ( answere != null ) {
								
							answere = filterAIThinking(parseJSON(answere,model).trim());
							
							if ( ! answere.isEmpty() ) {
								if ( debug > 1 ) log("we got back:" + answere);
								say( answere,true );
								reaction = true;
							}
						}
				
					} else if ( !aiportreachable ) {
					
						say( texte.get( config.get("conf","lang_short"), "AIUNAVAILABLE" ) );	
					
					}
				}

				if ( cf.command.equals("AIIDENTIFYIMAGE") ) {

					if ( ai != null && ai.get("enable").equals("true") && aiportreachable ) {

						HTTP.apihost = ai.get("host");
						HTTP.apiport = ai.get("port");
						if ( !ai.get("apitimeout").isEmpty() )
							HTTP.timeout = Integer.parseInt( ai.get("apitimeout") );
						
						String[] pics = dos.readFile(getHome()+"/.cache/pva/search.pics.cache").split(config.get("conf","splitter"));
						
						String bimages = "";
						
						for(String image: pics) {
							bimages += ",\""+ dos.readPipe("base64 -w 0 "+ image).trim() +"\"";
						}
						// remove leading ","
						bimages = bimages.substring(1);

						String model = ai.get("model");
						if ( !ai.get("imagemodel").isEmpty() ) model = ai.get("imagemodel");

						String answere = HTTP.post("/api/chat","{\"model\":\""+ model +"\",\"stream\": false,\"messages\":"+ 
							"[{\"role\": \"user\", \"model\":\"User\",\"date\":\""+
								LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") )+
							"\",\"content\":\""+ texte.get( config.get("conf","lang_short"), "AIIDENTIFYIMAGE" ) +"\",\"images\": ["+ bimages +"]}]}");

						if ( answere != null ) {
								
							answere = filterAIThinking(parseJSON(answere, model )).trim();
							
							if ( ! answere.isEmpty() ) {
								if ( debug > 1 ) log("we got back(3):" + answere);
								
								say( answere,true );
								reaction = true;
							}

						}
				
					} else if ( !aiportreachable ) {
					
						say( texte.get( config.get("conf","lang_short"), "AIUNAVAILABLE" ) );	
					
					}
				}

				if ( !reaction ) {
					
//					log("no internal reaction yet, lets test plugins to handle it.");	
					
					reaction = pls.handlePluginAction(cf, text);
				}
	
				if ( !reaction && ai != null && ai.get("enable").equals("true") && ai.get("mode").equals("gapfiller") && aiportreachable ) {
					if ( checkMediaPlayback() && text.trim().length()>0 ) {
					
						log("ai:send:" + text);
								
						HTTP.apihost = ai.get("host");
						HTTP.apiport = ai.get("port");
						if ( !ai.get("apitimeout").isEmpty() )
							HTTP.timeout = Integer.parseInt( ai.get("apitimeout") );
						
						aimsgs.addMessage(new AIMessage("user", "User", text ));
								
//						log("messages = "+ aimsgs.toJSON() );

						String answere = HTTP.post("/api/chat","{\"model\":\""+ ai.get("model")+"\",\"stream\": false,\"messages\":"+ aimsgs.toJSON() +"}");
						if ( answere != null ) {
								
							answere = filterAIThinking(parseJSON(answere,ai.get("model"))).trim();							
							if ( ! answere.isEmpty() ) {

								if ( debug > 1 ) log("we got back(4):" + answere);

								say( answere,true );
								reaction = true;
							}


						} else {
							log("ai:error:call to api failed with NULL");
						}
					}

				} else if ( debug > 2 ) log("no ai support");


				if ( !reaction && cgpt != null && cgpt.get("enable").equals("true") && cgpt.get("bin") != null && cgpt.get("mode").equals("gapfiller") ) {
					if ( checkMediaPlayback() && text.trim().length()>0 ) {
					
						log("chatgpt:send:" + text);
								
						String answere = dos.readPipe( config.get("chatgpt","bin") +" \""+ text +"\"" );
	
						if ( answere != null ) {
							answere = answere.trim();
							
							if ( debug > 1 ) log("we got back:" + answere);
								
							say( answere,true );
							
							reaction = true;
						} else {
							log("chatgpt:error:call to binary failed with NULL");
						}
					}

				} else if ( debug > 2 ) log("no chatgpt");

				
								
				if ( !reaction ) {
					if ( text.replace(""+keyword+"","").trim().isEmpty() ) {
						log("Ich glaube, Du hast nichts gesagt!");
						if ( (int)Math.random()*1 == 1 ) {
							say( texte.get( config.get("conf","lang_short"), "EMPYTRESPONSE1" ));
							
						} else  say( texte.get( config.get("conf","lang_short"), "EMPYTRESPONSE2" ));
					} else {
						text = text.replace(keyword,"").trim();
						if ( text.length()>40 ) text = text.substring(0,40)+" usw. usw. ";
						text = texte.get( config.get("conf","lang_short"), "NEGATIVERESPONSE" ).replaceAll("<TERM1>", text);
						log( text );
						say( text );
					}
				} else {
					// If we had a reaction, it was a valid cmd.
					
					if ( writeLastArg ) 
						dos.writeFile(getHome()+"/.cache/pva/cmd.last", text_raw);
				}
			} else {
				if ( ! reaction ) log("Nicht für mich gedacht:" + text);
			}
	}

static private class AnalyseMP3 extends Thread {

        private StringBuffer data;
        private String filename;
        private TwoKeyHash tk;
        
        public AnalyseMP3(StringBuffer data,String filename,TwoKeyHash tk) {
                this.data = data;
                this.filename = filename;
                this.tk = tk;
        }

        public void run() {
		tk.put( "proc","counter", ""+ ( Integer.parseInt( tk.get("proc","counter") ) +1 ) );
		tk.put( "files", filename, "0" );
//        	if ( debug > 4 ) log("["+ tk.get("proc","counter") +"] Analyse mp3 ... "+ filename );
        				try {
						Mp3File mp3file = new Mp3File( filename, false );
						if (mp3file.hasId3v1Tag()) {
							ID3v1 id3v1Tag = mp3file.getId3v1Tag();
							if ( id3v1Tag != null ) 
								data.append( formatMetadata( filename, id3v1Tag ) );
						}

						if (mp3file.hasId3v2Tag()) {
							ID3v2 id3v2Tag = mp3file.getId3v2Tag();
							if ( id3v2Tag != null ) 
								data.append( formatMetadata( filename, id3v2Tag ) );
						}
					} catch (UnsupportedTagException e) {
						// Silently ignore faulty files
					} catch (InvalidDataException e) {
						// Silently ignore faulty files
					} catch (IOException e) {
						// Silently ignore faulty files
					}
		tk.put( "proc","counter", ""+ ( Integer.parseInt( tk.get("proc","counter") ) - 1 ) );
		tk.put( "files", filename, "1" );
	}
}

}


class Reaction {

	public String positives = "";
	public String negatives = "";
	public String answere = "";
	
	public Reaction (String p,String n,String a) {
		this.positives = p;
		this.negatives = n;
		this.answere = a;
	}
}

class AppResult {

	public String namematches = "";
	public String keywordmatches ="";
	public long namerelevance = 0;
	public long keywordrelevance = 0;
	
	public AppResult( String n, String k, long nr, long kr ) {
	
		this.namematches = n;
		this.namerelevance = nr;
		this.keywordmatches = k;
		this.keywordrelevance = kr;

	}	
}

class AIMessages {

	Vector aimsgs = new Vector<AIMessage>();

	public void addMessage(AIMessage a) {
		aimsgs.add( a );
	}
	
	public String toJSON() {
	
		String res = "[";
		
		for(int i=0; i < this.aimsgs.size(); i++) {
			
			 AIMessage msg = (AIMessage)this.aimsgs.get(i);

			 res += msg.toJSON();
			 
			 if ( i < (this.aimsgs.size()-1) ) res += ",";
		}
	
		res += "]";
		
		return res;
	}
	
	public void clear() {
		this.aimsgs.clear();
	}
	
}
	
class AIMessage {

	public String role = "";
	public String model = "";
	public String date = "";
	public String content = "";	
	
	public AIMessage( String r, String m, String c ) {

		this.role = r;
		this.model = m;
		this.date = LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
		this.content = c;
	
	}
	
	public String toJSON() {
	
//		{"role": "user", "model": "User", "date": "2024/08/06 21:25:01", "content": "Hallo"}
		
		return 	"{\"role\":\""+ role +"\",\"model\":\""+ model +"\",\"date\":\""+ date +"\",\"content\":\""+ content +"\"}";		
	
	}

}
