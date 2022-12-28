
package plugins;

import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;

abstract public class Plugin extends Thread implements PluginInterface {

	protected PVA pva;

	protected Dos dos = new Dos();

	protected StringHash vars   = new StringHash();
	protected StringHash config = new StringHash();
	protected StringHash info = new StringHash();
		
	protected void log(String x) { System.out.println(x); }
	
}

