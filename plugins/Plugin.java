
package plugins;

import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;

abstract public class Plugin extends Thread implements PluginInterface {

	protected PVA pva;
	protected Dos dos = new Dos();
	
	protected void say(String text,String returnIntent,String extra) {
		String sender = getPluginInfo().get("name");
		if ( sender.isEmpty() ) sender="UNKOWN";
		log(sender+":say():"+ returnIntent+":"+extra);
		pva.AsyncSendIntent(new Command( sender , "ASYNCSPEAK", returnIntent, extra), text );
	}
	protected void say(String text) {
		String sender = getPluginInfo().get("name");
		if ( sender.isEmpty() ) sender="UNKOWN";
		log(sender+":say()");
		pva.AsyncSendIntent(new Command( sender , "ASYNCSPEAK", "", ""), text );
	}
	protected String getT(String key) {
		return pva.getText(key);
	}

	protected StringHash vars   = new StringHash();
	protected StringHash config = new StringHash();
	protected StringHash info = new StringHash();
		
	protected void log(String x) { System.out.println(x); }
	
	public void shutdown() { Thread.currentThread().interrupt( );return; }
}

