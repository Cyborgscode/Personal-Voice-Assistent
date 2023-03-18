
package server.streaming;
import server.*;
import hash.StringHash;
import java.util.Enumeration;
import io.Dos;

public abstract class Streaming extends Thread {


	Dos dos = new Dos();

	String[] videos;

	StringHash infos;
	StringHash cmds;
	
	boolean endthis = false;
	int log = 0;
	PVA pva;

	void log(String x) { System.out.println(x); }

	private String replacePlaceHolders(StringHash infos, String cmd) {
	
		Enumeration en = infos.keys();
		while ( en.hasMoreElements() ) {
			String key = (String)en.nextElement();
			cmd = cmd.replaceAll("<"+ key +">", infos.get(key) );
		}
		return cmd;
	}


	public String remote(String cmd) {
		if ( infos != null ) {
			String ssh = "";
			if ( infos.get("key").equals("default") ) {
				ssh = "ssh <user>@<ip> \"<CMD>\"";
			} else {
				ssh = "ssh -i <key> <user>@<ip> \"<CMD>\"";
			} 		
			String r = dos.readPipe( replacePlaceHolders( infos, ssh.replaceAll("<CMD>", cmds.get( cmd ) ) ) );
			return r;
		}
		return "";
	}
	
	public void local(String cmd) {
	
		if ( log>1 ) System.out.println( replacePlaceHolders( infos, cmds.get( cmd ) ) );
		
		String r = dos.readPipe( replacePlaceHolders( infos, cmds.get( cmd ) ) );
				
		if ( log>0 ) System.out.println( "returns "+r );	
	}
	
	public void local(String cmd,String term) {
	
		if ( log>1 ) System.out.println( replacePlaceHolders( infos, cmds.get( cmd ) ).replaceAll("<TERM1>",term) );
		
		if ( log>0 ) System.out.println( "exec " );	
		
		try {
			pva.exec( replacePlaceHolders( infos, cmds.get( cmd ) ).replaceAll("<TERM1>",term).split("x:x"), true );
		} catch (Exception e) {
			System.out.println(" it crashed "+ e.toString() );
		}
	}

	public void next() {
		log("killing stream "+ infos.get("playing")+ " @ "+ infos.get("ip") );
		local("killstreamserver");
	}

	public void exit() {
		log("stopping streamserver "+ infos.get("ip") );	
		endthis = true;
		local("killstreamserver");
	}

	public abstract void run();

}
