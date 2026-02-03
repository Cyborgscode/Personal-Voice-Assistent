**HOW TO WRITE A PLUGIN**

Hint: as plugins are loaded on startup and keep theire memory over the runtime of PVA, you can store runtime infos in your plugin.
But as soon as you want to store it permanently, you need to save it to disk yourself. You can only do this in the user's home, so get 
yourself informed what ~/.config, ~/.cache, ~/.local, ~/.var are. 

A) Place your classfile into the plugins/files/ directory.

B) Follow the rules to compile your plugin:

You need to implement this interface:

	public StringHash getPluginInfo();
	public String  getVar(String name);
	public boolean setVar(String name,String value);
	public String[] getActionCodes();
	public boolean execute(String actioncode, String rawtext);
	public void run();
	public void init(PVA pva);
  
**Methodes explained**

**getPluginInfo()** returns a StringHash filled with vital infos **AS LEAST THESE**:

		info.put("hasThread","yes"); // Tells the loader to run run() 
		info.put("hasCodes","yes");  // Tells main task, that we take unhandled actioncodes
		info.put("name","LoadTask"); // be nice, create a unique name
  
  You can store more infos in there, but the Pluginloader won't readout more ATM
  
**getVar() / setVar()**  is used to manipulate the internal variables of a plugin. 

It needs to be checked by the plugin IF a variable is public. See LoadTask plugin as example.
  
**init(PVA pva)** sets all the infos or whatever you need to do for your plugin. It's called when the class is loaded. 
  
YOU MUST take PVA as argument. 

You can ignore PVA if you do not need anything of it, which is HIGHLY UNLIKELY. 

**run()**  implements the methode which is run, if "hasThread" is set to "yes". This methode gets called after init() so everything is ready if needed.
  
What you do here depends on your plugin. Not all Plugins need to run tasks, but some will need to i.e. to periodically do soemthing.
  
**getActionCodes()** returns an array of commandkeywords. 
  
You need to add those commandkeywords to your config, so PVA can add texts, responses etc. I.E. for LoadTask this is:
  
command:"schalte|warnung|aus","SILENCELOADWARNING",""
command:"schalte|warnung|ein","UNSILENCELOADWARNING",""

text:"de","HEALTHRESPONSETURNEDOFF","es erfolgen keine weiteren audiowarnungen mehr"
text:"de","HEALTHRESPONSETURNEDON","Sprachwarnungen eingeschaltet"

As all config directories are dropin style, just put everything in a unique named config file i.e. /etc/pva/conf.d/20-loadtask.conf

This way, you can make use of any inbuild functionality and don't need to buid something yourself.

**execute(command,text_raw)** returns, if this plugin processed this command or not.
 
Handling is easy: check if "command" is your commandkeyword and act accordingly.

You return "true" if you handled it.
You return "false" if you DID NOT HANDLE it OR if you handled a GLOBAL CODE, which i.e. could be a general silence call or reset. 

**ATM we do not have any global code defined**
