/*

PVA is coded by Marius Schwarz since 2021

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/

import java.util.*;
import java.io.*;
import io.*;
import hash.*;
import java.util.Base64.*;
import java.nio.file.*;
import java.util.regex.*;
import utils.Tools;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; 

public class OllamaAITagging {

	static Dos dos = new Dos();

	static void log(String x) { System.out.println(x); }

// Change those 2 variables to fit your needs:
  
	static String model = "gemma3:12b";
	static String content = "Was ist in dem Bild zu sehen? Antworte mit 10 Schlüsselwörtern als eine Liste mit dem Format keyword1,keyword2,keyword3,keyword4,keyword5,keyword6,keyword7,keyword8,keyword9,keyword10. Nur die Schlüsselwörter, keine einleitenden Worte. Wenn Du aber eine Person mit Namen kennst, dann ist dieser Name das erste Schlüsselwort. Nenne den Namen nur, wenn Du absolut sicher bist. Gleiche Anweisung für identifizierte Regionen oder Städte. Du darfst weniger als 10 Schlüsselwörter erzeugen, wenn die Länge der Liste inklusive der Komma und Leerzeichen 64 Zeichen überschreitet.";

	static final int maxlen = 64;

	static boolean recursion = false;
	static boolean exifupdating = false;
	static boolean writetolist = false;
	static int filesprocessed = 0;
	static StringBuffer data = new StringBuffer(50000);
	
	static StringHash tagged = new StringHash();

	// JSON Object is given by LLM, but the org.json package can't be shipped with distros, so we need to do it ourself, so .. don't wonder it's messy ;)

	public static String parseJSON(String json) {
	
		json=json.replaceAll("\\\\.","");
		
		String[] pairs = json.split("(\",\"|},\")");
					
		String answere = "";
					
		for(String pair: pairs) {
			if ( pair.contains(":") ) {
				String[] data = pair.split(":",2);
				if ( data.length > 1 ) {	

					String key = data[0].replaceAll("\"","");
					String value = data[1];
					if ( key.endsWith("\"") ) key = key.substring(0,key.indexOf("\"")-1);
					if ( value.endsWith("\"") ) value = value.substring(0,value.indexOf("\"",1));
					if ( ( key.equals("response") || key.equals("content") ) && value.trim().length() > 1 ) {
						answere = value.substring(1).replaceAll("\\n","\n");
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


	static public String tagit(String bimages) {
	
		String answere = HTTP.post("/api/chat","{\"model\":\""+ model +"\",\"stream\": false,\"messages\":"+ 
				"[{\"role\": \"user\", \"model\":\"User\",\"date\":\""+
				LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") )+
				"\",\"content\":\""+ content +"\",\"images\": ["+ bimages +"]}]}");

		if ( answere != null ) {

			answere = filterAIThinking(parseJSON(answere).trim());
								
			if ( ! answere.isEmpty() ) {
				if ( answere.contains(":") ) answere = answere.substring( answere.indexOf(":")+1 );
				return answere;
			} else  return "answere is empty";

		} else return "no answere";
	}
	
	//  exiftool -if '$keywords =~ /harvey/i' -filename dir
	
	static void insideDir(String dirname) {
	
			File dir = new File( dirname );						
			String[] filenames = dir.list();
			for(String filename : filenames) {
				
				if ( dos.isFile( dirname +"/"+ filename ) ) {
				
					if ( filename.toLowerCase().matches("..*(png|jpg|gif|jpeg)") ) {

						String hash = SHA256.sha256( dirname+"/"+ filename );

						if ( tagged.get( hash ) == null ||tagged.get( hash ).trim().isEmpty() ) {								
						
							String tags = tagit( "\""+ Base64.getEncoder().encodeToString( dos.readFileRaw( dirname+"/"+ filename )) +"\"" ).replaceAll(", ",",");
							if ( exifupdating ) {
								while ( tags.length() > maxlen ) { tags = tags.substring( 0, tags.lastIndexOf(",") ); };
								log( filename +": "+ dos.readPipe("/usr/bin/exiftool -keywords=\""+ tags +"\" \""+ dirname +"/"+ filename + "\" -overwrite_original"));
							}
							filesprocessed++;
							log( filename+": "+ tags );
							
							tagged.put( hash , tags);
							data.append( hash +":"+ tags +"\n" );

							if ( writetolist && filesprocessed > 10 ) {
								filesprocessed = 1;
								dos.writeFile( System.getenv("HOME").trim() + "/.cache/pva/cache.picturetags", data.toString() );
							}
							
						} else log( filename+": "+ tagged.get( hash ) );
					} else log( filename+": skipped - not png,jpg,gif,jpeg");
				} else if ( recursion && dos.isDir( dirname +"/"+ filename ) ) {
					insideDir(  dirname +"/"+ filename );
				}
			}
	}

	static public void main(String[] args) {
	
			String target = "";
	
			for(String arg: args) {
				if ( arg.equals("-r") ) {
					recursion = true; // disfunctional atm
				} else if ( arg.equals("-e") ) {
					exifupdating = true;
				} else if ( arg.equals("-u") ) {
					writetolist = true;
				} else target = arg;
			}
			
			if ( target.isEmpty() ) {
				log("Syntax: [-r] [-u] [-e] file-dir-name");
				return;
			}

			if ( dos.fileExists( System.getenv("HOME").trim() + "/.cache/pva/cache.picturetags" ) ) {
				data.append( dos.readFile( System.getenv("HOME").trim() + "/.cache/pva/cache.picturetags" ) );
				String[] taglist = data.toString().split("\n");
			
				for(String line: taglist ) {
					System.out.println( line );
					args = line.split(":");
					tagged.put(args[0],args[1]);
				}
			}

			// init AI
			
			boolean aiportreachable = false;
	                String nt = dos.readPipe("env LANG=C netstat -lna");
                                                
                        if ( ! nt.isEmpty() ) {
                        	for(String a: nt.split("\n") )
                        		if ( a.matches( ".*:11434.*LISTEN.*" ) ) {

						// log("ollama up");
				
						HTTP.apihost = "localhost";
						HTTP.apiport = "11434";

						// init ollama
						HTTP.get("/api/tags");

						if ( dos.isDir( target ) ) {

							insideDir(target);

						} else {
						
							String tags = tagit( "\""+ misc.Base64.encodeBytes( dos.readFileRaw( target )) +"\"" ).replaceAll(", ",",");
							if ( exifupdating ) {
								while ( tags.length() > maxlen ) { tags = tags.substring( 0, tags.lastIndexOf(",") ); };
								log( dos.readPipe("/usr/bin/exiftool -keywords=\""+ tags +"\" \""+ target +"\" -overwrite_original"));
							}
							filesprocessed++;
							String hash = SHA256.sha256( target );
							if ( tagged.get( hash ) == null ||tagged.get( hash ).trim().isEmpty() ) {
								data.append( hash +":"+ tags +"\n");
							}	
							log( tags );
						}
					}
				
			} else log("Kein Ollama vorhanden");

			if ( writetolist && filesprocessed > 0 ) {
				dos.writeFile( System.getenv("HOME").trim() + "/.cache/pva/cache.picturetags", data.toString() );
			}

	}
}

