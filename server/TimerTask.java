
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

		return dos.writeFile( System.getenv("HOME").trim()+"/.config/pva/timers.conf", sb.toString() );
	}

	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {

			while ( true ) {
				if (isInterrupted()) {
					return;
				}

				if ( pva.mailboxes.size() > 0 ) {
					// don't load, if not needed
					String mailtext = "";
					MailConnection mc = new MailConnection();
					for(int i=0; i < pva.mailboxes.size(); i++)	{
						MailboxData m = pva.mailboxes.get(i);
						mailtext += mc.checkmail(m);
					}
					
					boolean playing = true;

					// check if mediaplayers are running, to avoid interruption
					
					if ( !pva.config.get("mediaplayer","status").isEmpty() ) {

						String allplayerservices = dos.readPipe( pva.config.get("mediaplayer","find").replaceAll( pva.config.get("conf","splitter")," ") );
						if ( ! allplayerservices.isEmpty() ) {
							String[] lines = allplayerservices.split("\n");
							for(String service : lines ) {
								
								if ( service.contains("org.mpris.MediaPlayer2") ) {
									
									String 	vplayer = dos.readPipe( pva.config.get("mediaplayer","status")
												      .replaceAll("<TARGET>", 
													Tools.zwischen( service, "\"","\"") ).replaceAll( pva.config.get("conf","splitter")," ") );

									if ( ! vplayer.trim().isEmpty() && vplayer.trim().contains("Playing") ) {
										playing = false; // because a player is running.
									}
								}
							}
						}
					}
					
					if ( ! dos.readPipe( "pgrep -f "+ pva.config.get("audioplayer","pname").replaceAll( pva.config.get("conf","splitter")," ") ).trim().isEmpty()  && !pva.config.get("audioplayer","status").isEmpty() ) {
	
						String[] result = dos.readPipe( pva.config.get("audioplayer","status").replaceAll( pva.config.get("conf","splitter")," "),true).split("\n");
						for(String x : result ) {
							if ( x.contains("[paused]") ) break;
							if ( x.contains("TITLE") ) {
								playing = false; // because a player is running.
							}
						}
					}
					
					// voice notification should be played only if: no mediaplayer has status play OR DND mode shall be interrupted.
					
					if ( !mailtext.isEmpty() && ( playing || pva.config.get("conf","donotdisturb").equals("overwrite") ) ) {
						pva.say(mailtext,true);
					}
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


	

	
