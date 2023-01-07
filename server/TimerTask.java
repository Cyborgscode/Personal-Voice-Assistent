
package server;

import io.*;
import utils.Tools;
import java.util.Date;
import java.util.Enumeration;
import data.*;

class TimerTask extends Thread {

	PVA pva;

	Dos dos = new Dos();

	public TimerTask(PVA pva) {
		this.pva = pva;
	}
	
	public boolean saveTimers() {

		StringBuffer sb = new StringBuffer(50000); // yes, we think big ;)
	
		Enumeration en1 = pva.timers.keys();
		while ( en1.hasMoreElements() ) {
			String key = (String)en1.nextElement();
			String val = pva.timers.get( key );
			sb.append(  key +":"+ val +"\n" );
		}

		// Update maintask's second database

                pva.timedata = sb.toString().split("\n");
		
		return dos.writeFile( System.getenv("HOME").trim()+"/.config/pva/timers.conf", sb.toString() );
	}

	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {

			while ( true ) {
				if (isInterrupted()) {
					return;
				}
	
				long now = new Date().getTime();	
				boolean update = false;			
				for(String data: pva.timedata) {
					if ( !data.trim().isEmpty() ) {
						String[] x = data.split(":");
						
						log("now="+ now +" timer="+ (new Date( Long.parseLong(x[0] ) ) ).toString() );
						
						if ( Long.parseLong(x[0]) < now ) {
							pva.timers.remove(x[0]);
							String text = pva.texte.get( pva.config.get("conf","lang_short"), "MAKETIMERRESPONSE") .replaceAll("<TERM>", x[1]);
							log( text );
							pva.say( text , true );
						 	update = true;
						}
					}
				}
				if ( update ) saveTimers();


				sleep(60000L);

			}
		} catch (Exception localException) {
		}
	}
}
