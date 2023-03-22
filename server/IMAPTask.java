
package server;

import io.*;
import hash.StringHash;
import utils.Tools;
import java.util.Date;
import java.util.Enumeration;
import data.*;
import javax.mail.*;
import com.sun.mail.imap.IMAPFolder;
import javax.mail.event.*;
import java.util.Vector;
import java.io.IOException;


class IMAPTask extends Thread {

	PVA pva;

	Dos dos = new Dos();

        int freq = 2000;

	public IMAPTask(PVA pva) {
		this.pva = pva;
	}
	

	void log(String x) { System.out.println(x); }
	
	public void run() {
		try {

//			log("IMAPTASK started");

			if ( pva.mailboxes.size() > 0 ) {
				// don't load, if not needed

				Vector<IMAPConnection> connections = new Vector<IMAPConnection>();

				for(int i=0; i < pva.mailboxes.size(); i++)	{
					
					MailConnection mc = new MailConnection();
					MailboxData m = pva.mailboxes.get(i);
						
//					log("IMAPTASK connect # "+ i);
						
					Store store = mc.connect(m);
					if ( store != null ) {
					
//						log("IMAPTASK connect # "+ i +" : connected");
					
						Folder inbox = store.getFolder("Inbox");

						inbox.open(Folder.READ_WRITE);

//						log("IMAPTASK connect # "+ i +" : gotFolder");

						inbox.addMessageCountListener(new MessageCountListener() {
						            public void messagesAdded(MessageCountEvent e) {
//						                        System.out.println("Message Count Event Fired");
						                        Folder folder = (Folder)e.getSource();
						                        String mailtext = "";
						                        
						                        try {
						                        	mailtext = mc.checkmail(folder);
						                                mc.idleManager.watch(folder);
							                        
							                } catch (MessagingException me) {
							                	//WATCH FAILED, which is bad as without watch there is no notification
							                	log(" Watch failed");
							                	
							                }
						                        
						                        boolean playing = true;

									// check if mediaplayers are running, to avoid interruption
						
									if ( !pva.config.get("mediaplayer","status").isEmpty() ) {
			
										String allplayerservices = dos.readPipe( pva.config.get("mediaplayer","find").replaceAll( pva.config.get("conf","splitter")," ") );
										if ( ! allplayerservices.isEmpty() ) {
											String[] lines = allplayerservices.split("\n");
											for(String service : lines ) {
								
												if ( service.contains("org.mpris.MediaPlayer2") ) {
									
													String 	vplayer = dos.readPipe( pva.config.get("mediaplayer","status").replaceAll("<TARGET>", 
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
									
										try {
											if ( pva.config.get("imapfilter") != null ) {
												log("Found IMAPFilter");
												StringHash filter = pva.config.get("imapfilter");
												
												// Does only work reliable without readoutloud flag.
												String[] subjects = mailtext.split("\n");
												mailtext = ""; // clear text and addonly lines which do not match
												for(String line : subjects ) {

													line = line.replaceAll("neue Mail von ","");

													boolean iamallowed = true;													
													Enumeration en = filter.keys();
													while( en.hasMoreElements() ){
														String name = (String)en.nextElement();
														String regex = filter.get(name);
														// log("filter:"+name+":"+regex+":"+line);
														if ( line.matches(regex) ) 
															iamallowed = false;
															
														// log("processing filter: "+ name + " => "+ iamallowed);
													}
													
													if ( iamallowed ) mailtext += "neue Mail von "+ line +"\n";
												}
												if ( !mailtext.trim().isEmpty() )
													pva.say(mailtext,true);	
											} else {
												pva.say(mailtext,true);
											}
										} catch (IOException ie) {
											// Ignore.. we can't do much if say failed.
										}
									}

						            }

            						    public void messagesRemoved(MessageCountEvent e) {
							                System.out.println("Message Removed Event fired");
						            }
					        });
/*

        					inbox.addMessageChangedListener(new MessageChangedListener() {
						            public void messageChanged(MessageChangedEvent e) {
						                System.out.println("Message Changed Event fired");
						            }
					        });
*/

					        // Check mail once in "freq" MILLIseconds

					        
//					        log("IMAPTASK connect # "+ i +" : Connection add");
					        
					        connections.add( new IMAPConnection( store, inbox, mc ) );
					        
//     						log("IMAPTASK connect # "+ i +" : Connection added");
					        
					        mc.idleManager.watch( inbox );
					        
					}
				}
					        
			        for (; ; ) {
					if (isInterrupted()) {
						
						// if interrupted, close all connections
						
						Enumeration<IMAPConnection> en = connections.elements();
						while ( en.hasMoreElements() ) {
							IMAPConnection c = en.nextElement();
							c.mc.disconnect( c.store );
					       	}
						
						// exit
			       			return;
			       		}
			       		
			       		Enumeration<IMAPConnection> en = connections.elements();
					while ( en.hasMoreElements() ) {
						IMAPConnection c = en.nextElement();
																	
				       		if ( c.folder instanceof IMAPFolder ) {
				       		
//							log("IMAPTASK setting idle");
				       		
					                IMAPFolder f = (IMAPFolder) c.folder;
					                f.idle(true);
//					                System.out.println("IDLE done");
					         } else {
//							log("IMAPTASK sleep");
					                Thread.sleep(freq); // sleep for freq milliseconds

					                // This is to force the IMAP server to send us
					                // EXISTS notifications.
					                c.folder.getMessageCount();
       							log("IMAPTASK sleep ended");
	            				 }
				       	}
		       		
			        }
			}
		} catch (Exception localException) {
		}
	}
}	

class IMAPConnection {

	public Folder  folder;
	public Store   store;
	public MailConnection mc;
	
	public IMAPConnection( Store s, Folder f, MailConnection mc ) {
	
		this.folder = f;
		this.store = s;
		this.mc = mc;
	}	
}
