
package server;

import io.*;
import utils.Tools;
import java.util.Date;
import data.*;

class LoadTask extends Thread {

	PVA pva;

	Dos dos = new Dos();

	public LoadTask(PVA pva) {
		this.pva = pva;
	}
	
	Float lastState = Float.parseFloat("0");

	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {
			String load = "";
			int time = 0;

			while ( true ) {
				if (isInterrupted()) {
					return;
				}
        
        // hint, dos.readFile() does not work for /proc/loadavg

        Float f = Float.parseFloat( dos.readPipe("cat /proc/loadavg").split(" ")[0].trim() );
				long  c = Long.parseLong( dos.readPipe("grep -c processor /proc/cpuinfo").trim() );
				
//				log("load="+f+" laststate="+lastState+" time="+time);
				
				if ( f > c && ( lastState < c || time > 60 ) ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSEHELPHELP") );
						time = 0;
				} else if ( f > ( c*80/100) && ( lastState < ( c*80/100) || time > 60 ) ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSE80P") );
						time = 0;
				} else if ( f < ( c/2 ) && lastState > (c/2) ) {
						pva.say( pva.texte.get( pva.config.get("conf","lang_short"), "HEALTHRESPONSEOK") );
				}	

				lastState = f;
				time++;
				sleep(1000L);

			}
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}
}
