// EXPERIMENTAL CODE "FOR NOW"

package io;

import data.*;
import javax.mail.*;
import javax.mail.Flags;
import javax.mail.search.*;
import java.util.Properties;

public class MailConnection {

	public Dos dos = new Dos();

	public MailConnection() {};

	public String checkmail(MailboxData m) throws MessagingException {
		try{
			String text = "";

			String lastseen = dos.readFile( System.getenv("HOME").trim()+  "/.cache/pva/.pva."+m.id+".lastmsg");
			if ( lastseen.trim().isEmpty() ) lastseen = "0";

			Properties props = System.getProperties();
			if ( m.secure == true ) {
				props.setProperty("mail.store.protocol", "imaps");
			} else  props.setProperty("mail.store.protocol", "imap");

			Session session = Session.getInstance(props, null);
			Store store = session.getStore();
			store.connect(m.servername, m.username, m.password);

			Folder inbox = store.getFolder("Inbox");
			
			// System.out.println("No of Unread Messages : " + inbox.getUnreadMessageCount());
			if ( inbox.getUnreadMessageCount() > 0 && !inbox.isOpen() ) {
				inbox.open(Folder.READ_ONLY);
				
				Message[] mails = inbox.search( new FlagTerm(new Flags(Flags.Flag.SEEN),false));

				// System.out.println( "Mails im Konto:"+ mails.length );
			
				for(Message mail: mails) {
					Address[] in = mail.getFrom();
					int mailid = mail.getMessageNumber();

//					System.out.println( "MailID="+ mailid +" <= " + lastseen );
					
					if ( mailid > Integer.parseInt( lastseen ) ) {
					
						text += "neue Mail von ";
					
//						System.out.print("FROM:");
					        for (Address address : in) {
					            // System.out.print(address.toString());
					            
					            String[] parts = address.toString().split("<");
					            if ( parts.length == 2 ) {
					            	if ( parts[0].trim().length()>0 ) {
					            		text += parts[0];
					            	} else  text += parts[1];
					            } else      text += parts[0]; // we only have a mailaddress without a name
					            
//					            text += address.toString();
					        }

						text += " " + mail.getSubject();
						try {
							if ( m.readoutloud ) text += mail.getContent();
						} catch (Exception e) {
							// IOException most likely 
							text += "beim Lesen der Email ist ein Fehler aufgetreten.";
						}
							
						text += "\n";
						lastseen = ""+ mailid;
					}
				}
				
				inbox.close();
			}
			
			dos.writeFile(System.getenv("HOME").trim()+  "/.cache/pva/.pva."+m.id+".lastmsg",lastseen);
			
			if ( inbox.getUnreadMessageCount() > 0) {
				store.close();
				return text;
			}
			store.close();
		} catch (NoSuchProviderException e){
			e.printStackTrace();
		}
		return "";
	}
}
