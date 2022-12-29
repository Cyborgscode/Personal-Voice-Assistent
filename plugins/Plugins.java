
package plugins;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import server.PVA;
import hash.StringHash;

public class Plugins {

	PVA pva;

        NewClassLoader loader = new NewClassLoader( this.getClass().getClassLoader() );
	int pluginsfound = 0;
	
	private Plugin[] myPlugins     = new Plugin[1000];
        private long[]   myPlugindates = new long[1000];
        
        public Plugins(PVA pva) {
        	this.pva = pva;
	        this.init();
        }

	public void init() {
	       	loadClasses();
	}        
       
        private boolean loadClasses() {

		System.out.println("loading plugin classes");

                File file = new File("./plugins/files/");
                File[] entries = file.listFiles();
                pluginsfound = 0;

                if ( entries != null ) {
                        for(int i =0; i < entries.length; i++ )
                                if ( entries[i].toString().endsWith(".class") && !entries[i].toString().contains("$") && pluginsfound < 1000 ) {
					System.out.println(" filename raw = "+ entries[i].getAbsolutePath() );

                                        String name = entries[i].getAbsolutePath().
                                                                 replaceAll("^.*plugins/files/","plugins/files/").
                                                                 replaceAll(".class","").
                                                                 replaceAll("/",".");

                                        try {
                                        	System.out.println("Plugins:loadClasses:name = "+ name);
                                        
                                                Plugin r = (Plugin)loader.loadClass( name ) .newInstance();

                                                if ( r != null ) {
                                                        myPlugindates[pluginsfound] = entries[i].lastModified();
                                                        myPlugins[pluginsfound++] = (Plugin)r;
                                                        
                                                        r.init(pva);
                                                        
                                                        if ( r.getPluginInfo().get("hasThread").equals("yes") ) {
								System.out.println("Plugins:loadClasses: running now "+ name);
                                                        	r.start();
                                                        }
                                                        
                                                }

                                        } catch (Exception e) {
                                                System.out.println( "Class "+ name +" not loaded" +"\n"+e );
                                                e.printStackTrace();
                                        };

                                        System.out.println( "Plugin #"+ pluginsfound + ": " + name );
                                }
                        return true;
                }
                System.out.println( "loadClasses::no filelist::./plugins/classes/");
                return false;
        }

	public boolean handlePluginAction(String command,String textraw) {
		for(int i = 0; i< pluginsfound ;i++ ) {
		
			boolean wehandleit = false;
		
			Plugin p = (Plugin)myPlugins[i];
			StringHash info = p.getPluginInfo();
			if ( info.get("hasCodes").equals("yes") ) {
				String[] ac = p.getActionCodes();
				if ( ac.length == 0 ) {
//					System.out.println("handle Plugin "+ info.get("name") + " has no action codes -> ignored");
				} else if ( ac.length > 0 ) {
//					System.out.print("handle Plugin "+ info.get("name") + " can handle ... ");
					for(String x : ac ) {
//						System.out.print(" "+ x +" : ");
						if ( x.equals(command) ) wehandleit = true;
					}
//					System.out.println("");
					
					if ( wehandleit ) {
//						System.out.println("Plugin "+ info.get("name") + " called => execute("+command+","+ textraw +")");
						boolean rc = p.execute( command, textraw );
						if ( rc ) {
//							System.out.println("Plugin "+ info.get("name") + " handled it ");
							return rc;
						} else {
//							System.out.println("Plugin "+ info.get("name") + " DID NOT handle it, proceeding to next plugin ");
						}
					}
				}
			}
		}
		return false;
	}
	
}

