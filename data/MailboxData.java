package data;

public class MailboxData {

	public int           id  = 0;		// Mailbox ID
	public String servername = "";
	public String username   = "";
	public String password   = "";
	public String commonname = "";
	public boolean      secure = true;
	public int            port = 143;
	public boolean readoutloud = false;
	public int    pullinterval = 60;
	
	public MailboxData( int id, String s, String u, String p, String c, boolean secure, int port, boolean rol, int pi ) {

		this.id = id;
		this.servername = s;
		this.username = u;
		this.password = p;
		this.commonname = c;
		this.secure = secure;
		this.port = port;
		this.readoutloud = rol;
		this.pullinterval = pi;

	}
	
	public MailboxData( int id, String s, String u, String p, String c) {

		this.id = id;
		this.servername = s;
		this.username = u;
		this.password = p;
		this.commonname = c;
		this.secure = true;
		this.port = 143;
		this.readoutloud = false;
		this.pullinterval = 60;
	}
	
	public MailboxData( int id, String s, String u, String p) {

		this.id = id;
		this.servername = s;
		this.username = u;
		this.password = p;
		this.commonname = "";
		this.secure = true;
		this.port = 143;
		this.readoutloud = false;
		this.pullinterval = 60;
	}
}
