package plugins;

import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;

public interface PluginInterface {

	public StringHash getPluginInfo();
	public String  getVar(String name);
	public boolean setVar(String name,String value);
	public String[] getActionCodes();
	public boolean execute(Command cf, String rawtext);
	public void run();
	public void init(PVA pva);
}

