
package server;

import io.*;
import utils.Tools;
import java.util.Date;
import java.util.Enumeration;
import data.*;

class MetacacheTask extends Thread {

	PVA pva;
	Command cf;
	
	Dos dos = new Dos();

	public MetacacheTask(PVA pva) {
		this.pva = pva;
	}
	
	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {
					String suchergebnis = "";

					if ( dos.fileExists(pva.getHome()+"/.cache/pva/cache.musik") ) {
						suchergebnis = dos.readFile(pva.getHome()+"/.cache/pva/cache.musik");
					} else {
						suchergebnis = pva.suche( pva.config.get("path","music"), "*", pva.config.get("conf","musicfilepattern") );
						dos.writeFile(pva.getHome()+"/.cache/pva/cache.musik", suchergebnis);
					}
					// System.out.println("suche: "+ suchergebnis );
					if (!suchergebnis.isEmpty() ) {
						dos.writeFile( pva.getHome()+"/.cache/pva/cache.metadata", pva.createMetadata( suchergebnis ) );
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "MAKEMETACACHE") );
					
					
					} else {
				
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "MAKEMETACACHEERROR") );
					
					}


		} catch (Exception localException) {
		}
	}
}
