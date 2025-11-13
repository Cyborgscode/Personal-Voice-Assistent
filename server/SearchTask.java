
package server;

import io.*;
import utils.Tools;
import java.util.Date;
import java.util.Enumeration;
import data.*;

class SearchTask extends Thread {

	PVA pva;
	Command cf;
	
	Dos dos = new Dos();

	public SearchTask(PVA pva,Command cf) {
		this.pva = pva;
		this.cf  = cf;
	}
	
	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {
			String result = pva.suche( pva.config.get("path","music"), "*",pva.config.get("conf","musicfilepattern") );
			// if we got aborted, we don't overwrite the old cache
			if ( ! this.interrupted() ) {
				dos.writeFile( pva.getHome()+"/.cache/pva/cache.musik", result );
				pva.say( pva.texte.get( pva.config.get("conf","lang_short"), cf.command) );
			}			
			
		} catch (Exception localException) {
		}
	}
}
