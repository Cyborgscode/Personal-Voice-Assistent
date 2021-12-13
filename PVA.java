
import java.util.*;
import java.io.*;
import org.json.*;
import io.*;
import hash.*;
import data.*;
import java.nio.file.*;
import java.util.regex.*;

public class PVA {

	static String keyword = "carola";

	static TwoKeyHash config          = new TwoKeyHash();
	static TwoKeyHash alternatives    = new TwoKeyHash();
	static TwoKeyHash texte           = new TwoKeyHash();
	static TwoKeyHash context         = new TwoKeyHash();
	static Vector<Reaction> reactions = new Vector<Reaction>();
	static Vector<Command> commands   = new Vector<Command>();
	static Vector<Contact> contacts   = new Vector<Contact>();

	static String text = "";
	static boolean reaction = false;
	static Dos dos = new Dos();
	static String[] za = null;

	static void log(String x) { System.out.println(x); }

	static void exec(String cmd) throws IOException {
//	log( cmd );
		Runtime.getRuntime().exec( cmd );
		reaction = true;
	}

	static void exec(String[] cmds) throws IOException {
		for(String cmd : cmds) {
//			log( "argument:"+cmd );
			if ( cmd == null ) {
				log("exec():Illegal Argument NULL detected");
				reaction = false;
				return;
			}
		}
		Runtime.getRuntime().exec( cmds );
		reaction = true;
	}

	static boolean wort(String gesucht) {
		if ( text.contains(gesucht.toLowerCase()) ) return true;
		return false;
	}
	
	static boolean oder(String gesucht) {
		String[] args = gesucht.toLowerCase().split("\\|");
		for(String arg: args) {
//			System.out.println(text+ " arg="+arg);
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

	static String zwischen(String buffer,String vor,String nach) {

                int i1 = buffer.indexOf(vor)+vor.length();
                int i2 = buffer.indexOf(nach,i1);

                if ( i1 >= 0 && i2 > i1 ) {

                        return buffer.substring(i1,i2);

                }

                return null;
        }

	static String[] buildFileArray(String str,String rpl) {

		String[] x = str.split(" ");
		String[] y = String.join("\\\\ ",rpl.split(" ")).split("x:x");
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

	static String _searchExternalApps(String path,String suchwort) {

		File file = new File(path);
                File[] entries = file.listFiles();
		String filename ="";
		suchwort = suchwort.trim().toLowerCase();

                if ( entries != null ) {     
                        for(int i =0; i < entries.length; i++ ) {
//                        	System.out.println("processing file "+ entries[i].toString() );
                        	try {
	                        	if ( entries[i].isDirectory() && !entries[i].getName().startsWith(".") ) {

						// directoryname contains searchword(s) so we add it entirely
						// log("add "+ entries[i].getCanonicalPath() +" mit *");
						filename += _searchExternalApps( entries[i].getCanonicalPath() , suchwort);
	                        	
	                        	} else if ( entries[i].toString().toLowerCase().endsWith(".desktop") ) {

						String[] content = dos.readFile( entries[i].getCanonicalPath() ).split("\n");
						boolean hit = false;
						String app_exe = "";
						for(String line: content) {
							line=line.trim();
							if ( line.startsWith("Exec=") ) {
								app_exe = line.substring( line.indexOf("=")+1 );
								
							}
							if ( line.startsWith("Name=") || line.startsWith("Name["+config.get("conf","lang")+"]=") || line.startsWith("Name["+config.get("conf","lang_short")+"]=") ) {
								String name = line.substring( line.indexOf("=")+1 );
								if ( name.toLowerCase().contains(suchwort) ) {
									hit = true; // we can't know, if "Exec=" stands before or after Name*= in the desktopfile!
								}
							}
							if ( line.startsWith("Keywords=") || line.startsWith("Keywords["+config.get("conf","lang")+"]=") || line.startsWith("Keywords["+config.get("conf","lang_short")+"]=") ) {
								String name = line.substring( line.indexOf("=")+1 ).toLowerCase();
								String[] keys = name.split(";");
								for(String key: keys) {
								
									if ( key.contains(suchwort) || key.equals( suchwort ) ) {
										hit = true; // we can't know, if "Exec=" stands before or after Name*= in the desktopfile!
									}
								}
							}
						
						}
				
						if ( hit ) filename = app_exe.trim(); // This way, we garantee, that it's found, it's it really present. it's possible that someone made a mistake and did not write Exec= into the file. 
			
					}
				} catch(IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}

		return filename;
	
	
	}

	static String searchExternalApps(String suchwort) {
	
		String filename ="";
		filename += _searchExternalApps("/usr/share/applications/", suchwort );
		if ( filename.isEmpty() ) 
			if ( config.get("conf","lang").equals("de_DE") ) {
				filename += _searchExternalApps("/home/"+ dos.readPipe("whoami").trim()+"/Schreibtisch", suchwort );
			} else  filename += _searchExternalApps("/home/"+ dos.readPipe("whoami").trim()+"/Desktop", suchwort );
	
		return filename;
	}


	static String cacheSuche(String start,String suchwort,String type) {
		suchwort = suchwort.trim().toLowerCase();
		String[] files = start.split("x:x");
		String filename ="";
//	log(suchwort);
                if ( files != null ) {     
                        for(int i =0; i < files.length; i++ ) {
                        	// System.out.println("processing file "+ entries[i].toString() +" matches ("+type+")$ ??? "+  entries[i].toString().matches(".*("+type+")$") );
                        	if ( files[i].toString().toLowerCase().endsWith(type) || files[i].toString().toLowerCase().matches(".*("+type+")$") ) {
	                        		if ( suchwort.contains(" ") ) {
	                        			String[] args = suchwort.split(" ");
	                        			boolean found = true;
	                        			for( String arg : args ) {
								arg = arg.trim();
								//System.out.println("subsuchwort="+ arg);
								if ( !arg.isEmpty() && !files[i].toString().toLowerCase().contains( arg ) ) {
									found = false;
								}
							}
							if ( found ) filename += files[i]+"x:x";
	                        			
	                        		} else if ( suchwort.equals("*") || files[i].toString().toLowerCase().contains(suchwort) ) {
							filename += files[i]+"x:x";
						}
				}
			}
		}
		
		return filename;
	}


	static String _suche(String start,String suchwort,String type) {
		File file = new File(start);
                File[] entries = file.listFiles();
		String filename ="";
		suchwort = suchwort.trim().toLowerCase();
//	log(suchwort);
                if ( entries != null ) {     
                        for(int i =0; i < entries.length; i++ ) {
                        	// System.out.println("processing file "+ entries[i].toString() +" matches ("+type+")$ ??? "+  entries[i].toString().matches(".*("+type+")$") );
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
	                        		if ( suchwort.contains(" ") ) {
	                        			String[] args = suchwort.split(" ");
	                        			boolean found = true;
	                        			for( String arg : args ) {
								arg = arg.trim();
								//System.out.println("subsuchwort="+ arg);
								if ( !arg.isEmpty() && !entries[i].toString().toLowerCase().contains( arg ) ) {
									found = false;
								}
							}
							if ( found ) filename += entries[i]+"x:x";
	                        			
	                        		} else if ( suchwort.equals("*") || entries[i].toString().toLowerCase().contains(suchwort) ) {
							filename += entries[i]+"x:x";
						}
				
					}
				} catch(IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		
		return filename;
	
	
	}

	static String suche(String start,String suchwort,String type) {
	
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
				
					sb.append( key +":\""+ k +"\",\""+ v +"\"\n" );

				}
			}
			
			Enumeration en2 = alternatives.keys();
			while ( en2.hasMoreElements() ) {
				String key = (String)en2.nextElement();
				StringHash sub = alternatives.get( key );
				Enumeration en3 = sub.keys();
				while ( en3.hasMoreElements() ) {
					
					String k = (String)en3.nextElement();
					String v = sub.get( k );
				
					sb.append( "alternatives:\""+ key +"\",\""+ k +"\",\""+ v +"\"\n" );

				}
			}

			en2 = texte.keys();
			while ( en2.hasMoreElements() ) {
				String key = (String)en2.nextElement();
				StringHash sub = texte.get( key );
				Enumeration en3 = sub.keys();
				while ( en3.hasMoreElements() ) {
					
					String k = (String)en3.nextElement();
					String v = sub.get( k );
				
					sb.append( "text:\""+ key +"\",\""+ k +"\",\""+ v +"\"\n" );

				}
			}

			en2 = context.keys();
			while ( en2.hasMoreElements() ) {
				String key = (String)en2.nextElement();
				StringHash sub = context.get( key );
				Enumeration en3 = sub.keys();
				while ( en3.hasMoreElements() ) {
					
					String k = (String)en3.nextElement();
					String v = sub.get( k );
				
					sb.append( "contextreplacements:\""+ key +"\",\""+ k +"\",\""+ v +"\"\n" );

				}
			}

			for(int i=0; i < reactions.size(); i++)	{

				Reaction r = (Reaction)reactions.get(i);

				sb.append( "reaction:\""+ r.positives +"\",\""+ r.negatives +"\",\""+ r.answere +"\"\n" );


			}

			for(int i=0; i < commands.size(); i++)	{

				Command c = commands.get(i);

				sb.append( "command:\""+ c.words +"\",\""+ c.command +"\",\""+ c.filter +"\",\""+ c.negative +"\"\n" );


			}


//			log ( sb.toString() );

//		return true;
		return dos.writeFile("./pva.conf", sb.toString() );
	}


	static public void main(String[] args) {

		try {

			// read in config, if no custom config is present, load defaults.
			String[] conflines = null;
			if ( dos.fileExists("./pva.conf") ) {
				conflines = dos.readFile("./pva.conf").split("\n");
			} else  conflines = dos.readFile("./pva.conf.default").split("\n");
			
			// our config is a three dimentional array 
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
								reactions.add( new Reaction( level2[0].trim() , level2[1].trim() , level2[2].trim() ) );
							} else  reactions.add( new Reaction( level2[0].trim() , "" , level2[1].trim() ) );
							
						} else if ( level1[0].trim().equals("command") ) {

							if ( level2.length == 4 ) {
								commands.add( new Command( level2[0].trim() , level2[1].trim() , level2[2].trim(), level2[3].trim() ) );
							} else if ( level2.length == 3 ) { 
								commands.add( new Command( level2[0].trim() , level2[1].trim() , level2[2].trim(), "" ) );
							} else  commands.add( new Command( level2[0].trim() , level2[1].trim() , "" , "") );

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

			// for speed resons, we got often used content in variables.
			keyword = config.get("conf","keyword");

			// now the part that extracts the text spoken from the JSON Object given to us.
			if ( args.length > 0 ) {
				JSONObject jo = new JSONObject(args[0]);		
				text = jo.getString("text");
			} else {
				// defaulttext for debugreasons
				text = keyword +" ich möchte queen hören";
			}

			// generate german words for numbers 1-99

			String[] bloecke = "zwanzig:dreißig:vierzig:fünfzig:sechzig:siebzig:achzig:neunzig".split(":");
			String[] ziffern = "ein:zwei:drei:vier:fünf:sechs:sieben:acht:neun".split(":");
			String zahlen = "ein:zwei:drei:vier:fünf:sechs:sieben:acht:neun:zehn:elf:zwölf:dreizehn:vierzehn:fünfzehn:sechzehn:siebzehn:achtzehn:neunzehn:";
			for(String zig : bloecke ) {
				zahlen += zig+":";
				for(String zahl : ziffern ) 
					zahlen += zahl+"und"+zig+":";
			}
			
			za = zahlen.replaceAll(":$","").split(":");

//			for( String x: za) log( x );

			// read in vcard cache
			// without this cache it would take several minutes to import addressbooks
			// from time to time you should refresh it, by just deleteing it
						
			String vcards = dos.readFile( "./vcards.cache" );
			if ( vcards.isEmpty() ) {
				exec( (config.get("app","say")+"x:xIch lese die Addressbücher ein, das kann einige Minuten dauern" ).split("x:x"));
				StringHash adb = config.get("addressbook");
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
							String href = zwischen(line,"href=\"","\"");
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
				vcards = "";
				Enumeration<Contact> een = contacts.elements();
				while ( een.hasMoreElements() ) {
					Contact c = een.nextElement();
					vcards += c.exportVcard()+"XXXXXX---NEXT_ELEMENT---XXXXXX\n";
				}
				dos.writeFile("./vcards.cache", vcards);
			} else {
				log("Loading cache...");
				String[] x = vcards.split("XXXXXX---NEXT_ELEMENT---XXXXXX\n");
				for(String card : x) { 
					Contact vcf = new Contact();
					vcf.importVcard( card );
					contacts.addElement( vcf );	
				}
								
			}

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
			
			for(int i=0; i < reactions.size(); i++)	{

				Reaction r = (Reaction)reactions.get(i);

				if ( und( r.positives ) && ( r.negatives.isEmpty() || !und( r.negatives ) ) ) {
					
					exec( (config.get("app","say")+"x:x" + r.answere.replaceAll("%KEYWORD", keyword )).split("x:x"));
					
				}
			}

			// now the part that interessts you most .. the context depending parser
			
			// before we start the static methods:
			// word("term") means exactly this term in the variable "text"
			// oder("term|term2") means one of these terms
			// und("term|term2") means all of these terms, but the order is irrelevant  und("eins|zwei|drei") works on "drei zwei eins" same as on "sprecher nummer drei hatte um eins einen termin mit zwei leuten"
			// in later versions of this app, we will have a regexp database that will do this work as far as possible
			
			
			if ( wort(keyword) ) {
			
				// remove everything BEFORE the keyword, because the keyword can be at any position in a recording
				// also remove the keyword itself
				
				// ADVISE: remove any keyword that led to your reaction
				// in the sentence "carola i want listen to queen"  anything thats not part of the searchterm "queen" needs to go or your search is pointless :)
			
				text = text.substring( text.indexOf(keyword) ).replaceAll( keyword , "");

				String text_raw = text; // RAW Copy of what has been spoken. This is needed in case a filter is applied, but we need some of the filtered words to make decisions.

				// It can be very helpfull to output the sentence vosk heared to see, whats going wrong.

				log(text);

				// parse commands from config
				
				Command cf = new Command("DUMMY","","",""); // cf = commandFound
				
				for(int i=0; i < commands.size(); i++)	{

					Command cc = commands.get(i);
	
//					log("matching "+ cc.words +" against "+ text);
	
					if ( und( cc.words ) && ( cc.negative.isEmpty() || !und( cc.negative ) ) ) {
						
						cf = cc;

						// Replace context related words i.e. the APP krita is often misunderstood in german for Kreta ( the greek island )
						// it's ok replace it for STARTAPPS, but i may not ok to replace it i.e. in a MAP Context!
						
						StringHash r = context.get( cc.command );
						if ( r != null ) {
							Enumeration en = r.keys();
							while ( en.hasMoreElements() ) {
								String a = (String)en.nextElement();
								String b = r.get( a );
								
//								log( "replace:"+a+" => "+b);
								
								text = text.replaceAll(a,b);
							}
						}

						// Apply special filter i.e. for binding words like "with"/"mit" 
						// if filter words are defined, lets remove them now. this simplyfies processing in the actual function.

						if ( ! cf.filter.isEmpty() ) {
							log( "replace: ("+ cf.filter +") => ");
							text = text.replaceAll("("+ cf.filter +")" , "");
						}
							
//						log( "replace: ("+ cf.words +") => ");

						// delete the command from the phrase
							
						text = text.replaceAll( "("+cf.words+")", "" );
							
						break;
					
					}
				}
				
				log ( "found "+ cf.command +": "+text );

			
				// The so called Star Trek part :) 
				
				if ( wort("autorisierung") ) {
					if ( oder("scout|code|kurt|kot") && !oder("neu|neuer")  ) {
						if ( wort( config.get("code","alpha")) ) {
							String cmd = dos.readFile("cmd.last");
							if ( cmd.equals("exit") ) {
								exec( (config.get("app","say")+"x:xIch beende mich").split("x:x"));	
								String[] e = dos.readPipe("pgrep -i -l -a python").split("\n");
								for(String a : e ) {
									if ( a.contains("pva.py") ) {
										String[] b = a.split(" ");
										exec("kill "+ b[0] );
									}
								}
							}
							if ( cmd.equals("compile") ) {
								exec( (config.get("app","say")+"x:xIch kompiliere mich neu").split("x:x"));	
								System.out.println( dos.readPipe("javac --release 8 PVA.java") );
							}
							return;
						} else 	exec( (config.get("app","say")+"x:xDer Sicherheitscode ist falsch!").split("x:x"));	
					}
					if ( oder("neu|neuer") && oder("scout|code|kurt|kot") ) {
						text = text.replaceAll("(autorisierung|neuer|neu|scout|code|kurt|kot)","").trim();
						exec( (config.get("app","say")+"x:x"+ text).split("x:x"));	
					}
				}
				
				
				// repeat last cmd.. 

				if ( cf.command.equals("REPEATLASTCOMMAND") ) {
					String read = dos.readFile("cmd.last").trim();
					if ( !read.isEmpty() && !read.equals( cf.words ) ) text = read;
				}
				
				// create caches
				
				if ( cf.command.equals("RECREATECACHE") ) {
		
					dos.writeFile("cache.musik", suche( config.get("path","music"), "*",".mp3|.aac" ) );
					
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), cf.command) ).split("x:x"));	

				}

				if ( cf.command.equals("SWAPALTERNATIVES") ) {

					String subtext = text.replaceAll(cf.filter,"").replaceAll(cf.words,"").trim();
					
					StringHash sub = alternatives.get(subtext);
					if ( sub != null ) {
						Enumeration en1 = sub.keys(); // This Enumeration has only one Entry 
						String changeapp = (String)en1.nextElement();
						String changevalue = sub.get(changeapp);
						
						config.put("app",changeapp,changevalue);
						exec(( config.get("app","say")+"x:x"+  texte.get( config.get("conf","lang_short"), cf.command+"1").replaceAll("<TERM1>", changeapp.replace("say","Sprachausgabe")).replaceAll("<TERM2>",subtext)).split("x:x"));										

						saveConfig();
						
					} else {
						exec(( config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), cf.command+"2").replaceAll("<TERM1>", subtext ) ).split("x:x"));
					}
				}

							
				// selfcompiling is aware of errors  and this reads them out loud.
								
				if ( cf.command.equals("LASTERROR") ) {
					exec( (config.get("app","say")+"x:x"+ dos.readFile("lasterror.txt").replaceAll("PVA.java:","Zeile ")).split("x:x"));							
				}

				// sometime we address carola itself i.e. shut yourself down:

				if ( cf.command.equals("RECOMPILE") || cf.command.equals("RECOMPILEWITHERRORREPORT") ) {

					String result = dos.readPipe("javac --release 8 PVA.java");
					if ( result.contains("error") ) {
						exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "RECOMPILE1") ).split("x:x"));	
						dos.writeFile("lasterror.txt",result);
						if (  cf.command.equals("RECOMPILEWITHERRORREPORT") )
							exec( (config.get("app","say")+"x:x"+result.replaceAll("PVA.java:","Zeile ")).split("x:x"));	
					} else {
						exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "RECOMPILE2") ).split("x:x"));			
					}
					System.out.println( result.trim() );		
				}				
				

				if ( cf.command.equals("EXIT") ) {
					dos.writeFile("cmd.last","exit");
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "EXIT") ).split("x:x"));							
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
					
					// bindewörter wie mit an usw. entfernen + alle keywords
				
					String subtext = text.replaceAll("(anrufen|sprechen|möchte|reden|rufe|mit|ich|an|mobil|arbeit|festnetz)","").trim();
					
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
									if ( ( oder("mobil|handy") && numbers.contains("cell") ) ||
									     ( wort("arbeit") && numbers.contains("work") ) ||
									     ( wort("festnetz") && numbers.contains("home") ) ||
									     ( !wort("festnetz") && !wort("arbeit") && !oder("mobil|handy") ) 
									   ) {
										if ( !stop ) {
											exec( (config.get("app","phone")+"x:xtel:"+ parts[1]).split("x:x"));
											stop = true;
										}
										
									} else exec( (config.get("app","say")+"x:xich weiß nicht, welche Nummer ich anrufen soll.").split("x:x"));
								} else {
									exec( (config.get("app","phone")+"x:xtel:"+ parts[1] ).split("x:x"));
								}
							} else log("keine telefonapp konfiguriert");
						}
			

					} else {
						exec( (config.get("app","say")+"x:xIch habe die Telefonnumer von "+ subtext + " nicht gefunden").split("x:x"));							
					}
					reaction = true; // make sure, any case is handled.					
				}
				
				// make screenshot
			
				if ( cf.command.equals("MAKESCREENSHOT") ) {
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "MAKESCREENSHOT") ).split("x:x"));	
					exec( config.get("app","screenshot").split("x:x"));	
				}		
				
				// "what time is it?"				
				if ( cf.command.equals("REPORTTIME") ) {
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "REPORTTIME").replaceAll("<TERM1>", dos.readPipe("date +%H")).replaceAll("<TERM2>", dos.readPipe("date +%M")) ).split("x:x"));	
				}			
				// "whats the systemload"			
				if ( cf.command.equals("REPORTLOAD") ) {
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "REPORTLOAD").replaceAll("<TERM1>", dos.readPipe("cat /proc/loadavg").split(" ")[0] ) ).split("x:x"));	
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
						String text = "";
						for(int i=0;i<7;i++) {
							String line = bericht[i];
	
							if ( line.startsWith(" ") ) line = line.substring(15);
							
							if ( i == 0) text += line0.replaceAll("<TERM1>", line );
							if ( i == 2) text += line2.replaceAll("<TERM1>", line );
							if ( i == 3) text += line3.replaceAll("<TERM1>", line.replace("+","").replace("(",  texte.get( config.get("conf","lang_short"), "upto") ).replace(")","")
										                             .replaceAll("°C", texte.get( config.get("conf","lang_short"), "°C")  ) ) ;
							if ( i == 4) text += line4.replaceAll("<TERM1>", line.replaceAll("km/h", texte.get( config.get("conf","lang_short"), "km/h") ).replaceAll("m/s", texte.get( config.get("conf","lang_short"), "m/s") ) );
							if ( i == 6) text += line6.replaceAll("<TERM1>", line.replace("mm", texte.get( config.get("conf","lang_short"), "mm") ) );
						}
						
						exec( (config.get("app","say")+"x:x"+text).split("x:x"));
						reaction = true;
				} 
				if ( cf.command.equals("CURRENTWEATHERNEXT") ) {
							
						// TODAY
				
						String wetter = dos.readPipe("curl wttr.in/"+config.get("conf","location")+"?1TAqM -H \"Accept-Language: "+ config.get("conf","lang").replace("_","-")+"\" ");
						String[] bericht = wetter.split("\n");
						String text = "";
						
						String datum = (new Date()).toString();
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
						exec( (config.get("app","say")+"x:x"+text).split("x:x"));
						reaction = true;
				
				}
				if ( cf.command.equals("CURRENTWEATHERTOMORROW") ) {
				
						// TOMORROW
				
						String wetter = dos.readPipe("curl wttr.in/"+config.get("conf","location")+"?2TAqM -H \"Accept-Language: "+ config.get("conf","lang").replace("_","-")+"\" ");
						String[] bericht = wetter.split("\n");
						String text = "";
						
						String datum = (new Date()).toString();

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

						exec( (config.get("app","say")+"x:x"+text).split("x:x"));
						reaction = true;
				}
			
				// kill process ... appname
			
				if ( cf.command.equals("STOPAPP") ) {

					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "STOPAPP").replaceAll("<TERM1>", text.replaceAll("("+ cf.words +")","") ).trim()).split("x:x"));
					
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
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "OPENSOURCECODE") ).split("x:x"));
				}
				if ( cf.command.equals("OPENCONFIG") ) {
					exec( (config.get("app","txt") + " ./pva.conf").split(" ") );
					exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "OPENCONFIG") ).split("x:x"));
				}
			
				if ( cf.command.equals("OPENAPP") || cf.command.equals("STARTAPP") ) {

					String with_key = texte.get( config.get("conf","lang_short"), "OPENAPP-KEYWORD");

					String with = "";

					text = text.toLowerCase().trim();
					if ( text.contains(  with_key ) ) {
						with = text.substring( text.indexOf( with_key )+ with_key.length() ).trim();
						text = text.substring( 0, text.indexOf( with_key ) );
					}
						
					// in case our keywords are the first argument, we need to swap text+with : "öffne bilder mit gimp" which is more human like
						
					if ( text.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER")  ) ) {
						log(" tausche Suchbegriff(e) "+ text + " mit " + with ); // not important logline

						String a = with; with = text;text = a;
					}

					String exe = ""; // LEAVE BLANC IF NO APP WAS FOUND
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
							if ( with.isEmpty() ) {
								exec( exe.replaceAll("(%U|%F)","").split(" ") );
							} else {
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-PICS") ) ) {
									exec( buildFileArray(exe,dos.readFile("search.pics.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-MUSIC") ) ) {
									exec( buildFileArray(exe,dos.readFile("search.music.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-VIDEOS") ) ) {
									exec( buildFileArray(exe,dos.readFile("search.videos.cache")));
								}
								if ( with.matches( texte.get( config.get("conf","lang_short"), "OPENAPP-FILTER-DOCS") ) ) {
									exec( buildFileArray(exe,dos.readFile("search.docs.cache")));
								}
							}
					}

					if ( reaction ) {
					
						if ( cf.command.equals("OPENAPP") ) {
							exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "OPENAPP-RESPONSE").replaceAll("<TERM1>", text) ).split("x:x"));
						} else  exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "STARTAPP-RESPONSE").replaceAll("<TERM1>", text) ).split("x:x"));
						
					} else 	exec( (config.get("app","say")+"x:x"+ texte.get( config.get("conf","lang_short"), "OPENAPP-RESPONSE-FAIL").replaceAll("<TERM1>", text) ).split("x:x"));
			
							
										
				}

				if ( cf.command.equals("PLAYAUDIO") ) {
					if ( !cf.filter.isEmpty() ) cf.filter = "|"+ cf.filter;
					
					String subtext = text.replaceAll("("+keyword+"|"+ cf.words + cf.filter +")","").trim();
					log("Ich suche nach Musik : "+ subtext);

					String suchergebnis = "";
					if ( dos.fileExists("cache.musik") ) {
						log("Suche im Cache");
						suchergebnis = cacheSuche( dos.readFile("cache.musik"), subtext,".mp3|.aac" );												
					} else {
						log("Suche im Filesystem");
						suchergebnis = suche( config.get("path","music"), subtext,".mp3|.aac" );
					}

					// String suchergebnis = suche( config.get("path","music"), subtext,".mp3|.aac" );
					// System.out.println("suche: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	

						dos.writeFile("search.music.cache",suchergebnis);

						int c = 0;

						TreeSort ts = new TreeSort();
						StringHash d = new StringHash();

						if ( !oder("noch|dazu") ) exec(config.get("audioplayer","clear").split("x:x"));
						exec(config.get("audioplayer","stop").split("x:x"));

						String[] files = suchergebnis.split("x:x");
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
				                		suchergebnis += erg[uyz].replaceAll("xx1xx", ",")+"x:x";
				                		d.put( keys[uyz] , "drin");
								c++;
				                	}
				                }

						if ( c > 500 ) {
							log("Ich habe "+c+" Titel gefunden");
							exec( (config.get("app","say")+"x:xIch habe "+ c +" Titel gefunden, dies kann nicht stimmen.").split("x:x"));
							return	;				
						}

						if ( c > 1 ) {
							log("Ich habe "+c+" Titel gefunden");
							exec( (config.get("app","say")+"x:xIch habe "+ c +" Titel gefunden").split("x:x"));
						}
						
						
						args = suchergebnis.split("x:x");
						for( String filename : args) {
								// log("Füge "+ filename +" hinzu ");
								//log( dos.readPipe( config.get("audioplayer","enqueue").replaceAll("x:x"," ") +" '"+ filename.replaceAll("'","xx2xx") +"'",true) );
								dos.readPipe( config.get("audioplayer","enqueue").replaceAll("x:x"," ") +" '"+ filename.replaceAll("'","xx2xx") +"'",true);
						}

						exec( config.get("audioplayer","playpl").split("x:x"));
						exec( config.get("audioplayer","play").split("x:x"));
					} else {
						exec( (config.get("app","say")+"x:xIch habe leider nichts gefunden, was zu "+ subtext +" paßt!").split("x:x"));
						log("keine treffer");
					}
					
				}
				if ( und("was|ist|da|zu|hören") || und("was|ist|gerade|zu|hören") || und("was|du|spielst|gerade") || und("was|du|spielst|da") || und("was|höre|ich")) {
					String[] result = dos.readPipe( config.get("audioplayer","status").replaceAll("x:x"," "),true).split("\n");
					for(String x : result ) 
						if ( x.contains("TITLE") ) {
							log("Ich spiele gerade: "+ x);
							exec( (config.get("app","say")+"x:x"+ x ).split("x:x"));
						}
					
					reaction = true;
				}
				if ( und("hilfe|zu") ) {
					String subtext = text.replaceAll("(hilfe|zu)","").trim();
					if ( wort("q m m p") ) {
						log(dos.readPipe("qmmp --help"));
						reaction = true;
					}
					
				}
				if ( und("ich|möchte|sehen") ) {

					String subtext = text.replaceAll("("+keyword+"|ich|möchte|sehen)","").trim();
					System.out.println("Ich suche nach Videos : "+ subtext);
					String suchergebnis = suche( config.get("path","video"), subtext, ".mp4|.mpg|.mkv|.avi|.flv" );
//					System.out.println("suche: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	

						dos.writeFile("search.video.cache",suchergebnis);					

						TreeSort ts = new TreeSort();

						String[] files = suchergebnis.split("x:x");
						for(String file : files ) {
							Path p = Paths.get( file );
							ts.add( p.getFileName().toString(), file );
						}
						String[] erg  = ts.getValues();
						suchergebnis = "";                				
				                for(int uyz=0;uyz<ts.size();uyz++) suchergebnis += erg[uyz]+"x:x";

						dos.readPipe("killall "+ config.get("videoplayer","pname"));
						exec( (config.get("videoplayer","enqueue") +"x:x"+suchergebnis).split("x:x") );
						System.out.println("Fertig mit hinzufügen ");

					}
					
				}

				if ( wort("schreibe") && oder("email") ){
					String subtext = text.replaceAll("(schreibe|email)","").trim();
					String[] worte = subtext.split(" ");
					String to = "";
					String subject = "";
					String body = "";
					
					for ( int i=0;i<worte.length;i++ ) {
						if ( worte[i].trim().equals("an") ) {
							to = worte[i+1];
							i=i+1;
						}
						if ( worte[i].trim().equals("betreff") ) {
							for(int j=i+1;j<worte.length;j++) {
								if ( worte[j].trim().equals("inhalt") ) {
									i=j; break;
								} 

								subject += worte[j]+" ";
							}
						}
						if ( worte[i].trim().equals("inhalt") ) {
							for(int j=i+1;j<worte.length;j++) {
								body += worte[j]+" ";
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
							log( dos.readPipe( ( config.get("app","mail") +" -compose \"to="+ parts[1] +",subject="+subject+",body="+body+"\"") ) );
							reaction = true;
						} 
					} else {
						exec( (config.get("app","say")+"x:xDie Emailadresse von "+ subtext + " ist unbekannt").split("x:x"));							
					}

				}
				
				if ( wort("suche") && oder("email|telefon|telefonnummer") ){
					String subtext = text.replaceAll("(telefonnummer|emailadresse|suche|email|telefon|von)","").trim();
					if ( wort("email") ) {
						String ergebnis = sucheNachEmail( subtext );
						if ( !ergebnis.trim().isEmpty() ) {
							String[] lines = ergebnis.split("\n");
							for(String numbers: lines ) {
								log(numbers);
								String[] parts = numbers.replaceAll("\"","").split("=");
								parts[1] = parts[1].trim();
								log("Die Emailadresse von "+ subtext + " ist " + parts[1]);
								exec( (config.get("app","say")+"x:xDie Emailadresse von "+ subtext + " ist " + parts[1]).split("x:x"));							
							} 
						} else {
							exec( (config.get("app","say")+"x:xDie Emailadresse von "+ subtext + " ist unbekannt").split("x:x"));							
						}
					} else {
						String ergebnis = sucheNachTelefonnummer( subtext );
						if ( !ergebnis.trim().isEmpty() ) {
							String[] lines = ergebnis.split("\n");
							for(String numbers: lines ) {
								log(numbers);
								String[] parts = numbers.replaceAll("\"","").split("=");
								parts[1] = parts[1].trim();
								if ( numbers.contains("cell") ) 
									exec( (config.get("app","say")+"x:xDie Mobilfunknummer von "+ subtext + " ist " + einzelziffern(parts[1])).split("x:x"));							
								if ( numbers.contains("home") ) 
									exec( (config.get("app","say")+"x:xDie festnetznummer von "+ subtext + " ist " + einzelziffern(parts[1])).split("x:x"));							
								if ( numbers.contains("work") ) 
									exec( (config.get("app","say")+"x:xDie Nummer von "+ subtext + " auf der Arbeit ist " + einzelziffern(parts[1])).split("x:x"));							
							} 
						} else {
							exec( (config.get("app","say")+"x:xDie Telefonnummer von "+ subtext + " ist unbekannt").split("x:x"));							
						}
					}
				}
				
				if ( oder("web suche|websuche") ) {
				
					String subtext = text.replaceAll("("+keyword+"|websuche|web suche|nach)","").trim();
					text = ""; // damit nichts mit suche anschlägt.
					//exec(( config.get("app","say")+"x:xIch suche nach "+ subtext ).split("x:x"));
					System.out.println("Ich suche nach "+ subtext);

					String sm  = config.get("searchengines", config.get("app","searchengine") );
					
					System.out.println("Ich suche mit "+ sm );
					
					exec( ( config.get("app","web")+" "+sm.replaceAll("<query>", subtext.replaceAll(" ","+") )  ).split(" "));

				}
				if (  oder("suche|such") && oder("bild|bilder") ) {

					String subtext = text.replaceAll("("+keyword+"|nach|sucher|suche|such|bilder|bild|jpg|png|gif|jpeg)","").trim();
					//exec(( config.get("app","say")+"x:xIch suche nach "+ subtext ).split("x:x"));
					System.out.println("Ich suche nach "+ subtext);
					
					String suchergebnis = "";					
					
					suchergebnis = suche( config.get("path","pics"), subtext, ".jpg|.png|.gif|.jpeg|.svg" );

					if (!suchergebnis.isEmpty() ) {

						dos.writeFile("search.pics.cache",suchergebnis );
						
						String[] files = suchergebnis.split("x:x");
						for(String filename : files ) {

							System.out.println(" gefunden : "+ filename);
							if ( oder("öffnen|öffne") || files.length == 1 ) { 
								exec( (config.get("app","gfx")+"x:x"+ filename).split("x:x") );
							}
						}
						String anzahl = ""+files.length;
						if ( files.length == 1 ) anzahl = "einen";
						if ( !oder("öffnen|öffne") && files.length > 1 ) {
							exec(( config.get("app","say")+"x:xIch habe "+ anzahl +" Treffer, was soll ich damit machen?").split("x:x"));
						} else  exec(( config.get("app","say")+"x:xIch öffne "+ anzahl +" Treffer").split("x:x"));
						
						System.out.println("Fertig mit Suchen ");

					} else exec(( config.get("app","say")+"x:xIch habe nichts gefunden, was zu "+ subtext +" paßt.").split("x:x"));
				}						
						
				if ( wort("suche") && oder("dokument|pdf|text") ) {

					String subtext = text.replaceAll("("+keyword+"|suche|dokument|pdf|text|nach|öffnen|öffne)","").trim();
					//exec(( config.get("app","say")+"x:xIch suche nach "+ subtext ).split("x:x"));
					System.out.println("Ich suche nach "+ subtext);
					
					String suchergebnis = "";					
					
					if ( wort("pdf") ) {
						suchergebnis = suche( config.get("path","docs") , subtext, ".pdf" );
					} else if ( !wort("pdf") ) {
						suchergebnis = suche( config.get("path","docs"), subtext, ".txt|.pdf|.odp|.ods|.odt" );
					} 
					// System.out.println("suchergebnis: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	
					
						dos.writeFile("search.docs.cache",suchergebnis);
					
						String[] files = suchergebnis.split("x:x");
						for(String filename : files ) {

							System.out.println(" gefunden : "+ filename);
							if ( oder("öffnen|öffne") || files.length == 1 ) { 
								if ( filename.endsWith(".txt") ) {
									exec( (config.get("app","txt")+"x:x"+ filename).split("x:x") );
								}
								if ( filename.endsWith(".pdf") ) {
									exec( (config.get("app","pdf")+"+x:x"+ filename).split("x:x") );
								}
								if ( filename.endsWith(".ods") ) {
									exec( (config.get("app","office") +"x:x"+ filename).split("x:x") );
								}
								if ( filename.endsWith(".odt") ) {
									exec( (config.get("app","office") +"x:x"+ filename).split("x:x") );
								}
								if ( filename.endsWith(".odp") ) {
									exec( (config.get("app","office") +"x:x"+ filename).split("x:x") );
								}
							}

						}
						String anzahl = ""+files.length;
						if ( files.length == 1 ) anzahl = "einen";
						if ( !oder("öffnen|öffne") && files.length > 1 ) {
							exec(( config.get("app","say")+"x:xIch habe "+ anzahl +" Treffer, was soll ich damit machen?").split("x:x"));
						} else  exec(( config.get("app","say")+"x:xIch öffne "+ anzahl +" Treffer").split("x:x"));
						
						System.out.println("Fertig mit Suchen ");
					}
					
				}
			
				if ( und("lies") && oder("pdf|text") ) {

					String subtext = text.replaceAll("(lies|pdf|text|vor|laut|einen|ersten|zweiten|dritten|von)","").trim();
					//exec(( config.get("app","say")+"x:xIch suche nach "+ subtext ).split("x:x"));
					System.out.println("Ich suche nach "+ subtext);
					
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
					
						dos.writeFile("search.docs.cache",suchergebnis);
					
						String[] files = suchergebnis.split("x:x");
						int c = 1;
						for(String filename : files ) {

							System.out.println(" gefunden : "+ filename);
							if ( files.length == 1 ||
							
								( wort("ersten") && c==1 ) ||
								( wort("zweiten") && c==2 ) ||
								( wort("dritten") && c==3 )
							
							 ) { 
							 
							 	System.out.println("Lese "+ filename);
								if ( filename.endsWith(".txt") ) {
									exec( (config.get("app","say")+"x:x"+ filename).split("x:x") );
								}
								if ( filename.endsWith(".pdf") ) {
									String txt =  dos.readPipe("pdftotext -nopgbrk "+ filename+ " /tmp/."+keyword+".say");
									System.out.println( txt );
//									dos.writeFile("/tmp/."+keyword+".say", txt);
									exec( (config.get("app","say")+"x:x/tmp/."+keyword+".say").split("x:x") );
								}
							}
							c++;
						}

						if ( files.length > 1 ) {
							exec(( config.get("app","say")+"x:xIch habe "+ files.length +" Texte gefunden, das sind leider zu viele!").split("x:x"));
						} 
						System.out.println("Fertig mit suchen nach Text");
					}
					
				}

				if ( und("spiel|musik") || wort("spielmusik") ) {
					if ( !wort("zufällig") ) {
						exec(config.get("audioplayer","play").split( config.get("conf","splitter") ));
					} else {	
						log("Ich katalogisiere Musik ...");
						String suchergebnis = "";
						if ( dos.fileExists("cache.musik") ) {
							suchergebnis = dos.readFile("cache.musik");
						} else {
							suchergebnis = suche( config.get("path","music"), "*",".mp3|.aac" );
							dos.writeFile("cache.musik", suchergebnis);
						}
						// System.out.println("suche: "+ suchergebnis );
						if (!suchergebnis.isEmpty() ) {	

							if ( !oder("noch|dazu") ) exec(config.get("audioplayer","clear").split( config.get("conf","splitter") ));
							exec(config.get("audioplayer","stop").split( config.get("conf","splitter") ));
	
							String[] files = suchergebnis.split( config.get("conf","splitter") );
														
							int i = (int)( Math.random() * files.length);

							dos.readPipe( config.get("audioplayer","enqueue").replaceAll( config.get("conf","splitter")," ") +" '"+ files[i].replaceAll("'","xx2xx") +"'",true);
	
							exec( config.get("audioplayer","playpl").split( config.get("conf","splitter") ));
							Thread.sleep(1000);
							log(  "starte Musik" );
							exec( config.get("audioplayer","play").split( config.get("conf","splitter") ));
						} 
					}
				}
				
				if ( und("füge|lieder|hinzu") || und("füge|titel|hinzu") ) {
					log("Ich katalogisiere Musik ...");
					String suchergebnis = "";
					if ( dos.fileExists("cache.musik") ) {
						suchergebnis = dos.readFile("cache.musik");
					} else {
						suchergebnis = suche( config.get("path","music"), "*",".mp3|.aac" );
						dos.writeFile("cache.musik", suchergebnis);
					}
					// System.out.println("suche: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {	

						exec(config.get("audioplayer","stop").split( config.get("conf","splitter") ));

						String[] files = suchergebnis.split( config.get("conf","splitter") );

						for(int i=0;i<za.length;i++) 
							if ( wort( za[i] ) )
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
				if ( oder("stoppen|stoppe|halte|an|aus|anhalten|stop") ) {
				
					if ( wort("musik") ) 
						exec(config.get("audioplayer","stop").split("x:x"));
				}
				
				if ( wort("leiser") && oder("mache|mach") ) {
					exec(config.get("audioplayer","lowervolume").split("x:x"));
					exec(config.get("audioplayer","lowervolume").split("x:x"));
					exec(config.get("audioplayer","lowervolume").split("x:x"));
					exec(config.get("audioplayer","lowervolume").split("x:x"));
					exec(config.get("audioplayer","lowervolume").split("x:x"));
				}
				if ( wort("lauter") && oder("mache|mach") ) {
					exec(config.get("audioplayer","raisevolume").split("x:x"));
					exec(config.get("audioplayer","raisevolume").split("x:x"));
					exec(config.get("audioplayer","raisevolume").split("x:x"));
					exec(config.get("audioplayer","raisevolume").split("x:x"));
					exec(config.get("audioplayer","raisevolume").split("x:x"));
				}				
				
				if ( und("leiser|musik") ) {
					if ( wort("viel") ) {
						exec(config.get("audioplayer","lowervolume").split("x:x"));
						exec(config.get("audioplayer","lowervolume").split("x:x"));
						exec(config.get("audioplayer","lowervolume").split("x:x"));
						exec(config.get("audioplayer","lowervolume").split("x:x"));
						exec(config.get("audioplayer","lowervolume").split("x:x"));
					} else {
						exec(config.get("audioplayer","lowervolume").split("x:x"));
					}
				}
				if ( und("lauter|musik") ) {
					if ( wort("viel") ) {
						exec(config.get("audioplayer","raisevolume").split("x:x"));
						exec(config.get("audioplayer","raisevolume").split("x:x"));
						exec(config.get("audioplayer","raisevolume").split("x:x"));
						exec(config.get("audioplayer","raisevolume").split("x:x"));
						exec(config.get("audioplayer","raisevolume").split("x:x"));
					} else {
						exec(config.get("audioplayer","raisevolume").split("x:x"));
					}
				}
				if ( und("nächster|titel") || und("nächstes|stück") || und("nächstes|lied") ) {
					exec(config.get("audioplayer","nexttrack").split("x:x"));
					if ( wort("übernächstes") ) exec(config.get("audioplayer","nexttrack").split("x:x"));
				}
				if ( und("letzter|titel") || und("letztes|stück") || und("letztes|lied") || und("ein|lied|zurück") ) {

					exec(config.get("audioplayer","lasttrack").split("x:x"));
					
					if ( wort("vorletztes") ) exec(config.get("audioplayer","lasttrack").split("x:x"));;
				}
				if ( oder("lieder|lied") && wort("weiter") ) {
				
					for(int i=0;i<za.length;i++) 
						if ( wort( za[i] ) )
							for(int j=0;j<=i;j++ )
								exec(config.get("audioplayer","nexttrack").split("x:x"));
							
				}
				if ( oder("lieder|lied") && wort("zurück") ) {
				
					for(int i=0;i<za.length;i++) 
						if ( wort( za[i] ) )
							for(int j=0;j<=i;j++ )
								exec(config.get("audioplayer","lasttrack").split("x:x"));
							
				}
				if ( wort("springe") && oder("vorwärts|vor") ) {
	
					if ( wort("sekunden") ) {			
						for(int i=0;i<za.length;i++) 
							if ( wort( za[i] ) )
								exec( (config.get("audioplayer","forward")+i ).split("x:x") );
					} else 	exec( (config.get("audioplayer","forward")+"20").split("x:x") );
							
				}
				if ( wort("springe") && wort("zurück") ) {
				
					if ( wort("sekunden") ) {			
						for(int i=0;i<za.length;i++) 
							if ( wort( za[i] ) )
								exec( (config.get("audioplayer","backward")+i ).split("x:x") );
					} else exec( (config.get("audioplayer","backward")+"20").split("x:x") );
				}

				if ( wort("ton") && oder("aus|an") ) {
					exec(config.get("audioplayer","togglemute").split("x:x"));
				}
				if ( !reaction ) {
					if ( text.replace(""+keyword+"","").trim().isEmpty() ) {
						System.out.println("Ich glaube, Du hast nichts gesagt!");
						if ( (int)Math.random()*1 == 1 ) {
							exec(( config.get("app","say")+"x:xIch glaube, Du hast nichts gesagt!").split("x:x"));
						} else  exec(( config.get("app","say")+"x:xJa, bitte?").split("x:x"));
					} else {
						text = text.replace(keyword,"").trim();
						if ( text.length()>40 ) text = text.substring(0,40)+" usw. usw. ";
						System.out.println("Ich habe "+ text +" nicht verstanden");
						exec(( config.get("app","say")+"x:xIch habe "+ text +" nicht verstanden").split("x:x"));
					}
				} else {
					// If we had a reaction, it was a valid cmd.
					
					dos.writeFile("cmd.last", text);
				}
			} else {
				System.out.println("Nicht für mich gedacht:" + text);
			}
		} catch (Exception e) {
				
			e.printStackTrace();
			System.out.println(e);
		
		}
	}
}

class Command {

	public String words = "";
	public String command = "";
	public String filter = "";
	public String negative = "";
	
	public Command (String w,String c,String f,String n) {
		this.words = w;
		this.command = c;
		this.filter = f;
		this.negative = n;
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
