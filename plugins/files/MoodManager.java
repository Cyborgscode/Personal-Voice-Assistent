package plugins.files;

import plugins.Plugin;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;

public class MoodManager extends Plugin {

    private int moodLevel = 0;
    private String name = "MoodManager";
    private StringHash lastMoodAnswere = new StringHash();
    
    private String getFilter = ":mood:status:level:text:suffix";
    private String setFilter = ":impuls:textkey:";
    private TwoKeyHash validValues = new TwoKeyHash();

    public void init(PVA pva) {
        this.pva = pva;
        validValues.put("impuls", "wert", "ok");
    }

    public StringHash getPluginInfo() {
        StringHash info = new StringHash();
        info.put("hasThread", "yes");
        info.put("hasCodes", "yes");
        info.put("name", name);
        return info;
    }

    public String[] getActionCodes() {
        return new String[]{"MOOD_QUERY", "MOOD_INCREASE", "MOOD_DECREASE","MOOD_IMPULS"};
    }

    // Korrigierte Signatur gemäß PluginInterface
    public boolean execute(Command cf, String rawtext) {
        if (cf.command.equals("MOOD_QUERY")) {
		say( getMoodDescription());
		return true;
        }
         // 2. Lob verarbeiten (Mood geht hoch)
        if (cf.command.equals("MOOD_INCREASE")) {
		// Kurze Bestätigung aus der Text-DB
		if ( (Math.abs(moodLevel+10)/25 % 4) != ( Math.abs(moodLevel)/25 % 4 ) && moodLevel != 0 )  say(pva.texte.get(pva.config.get("conf", "lang_short"), "MOOD_THANKS"));
		this.setVar("impuls", "10"); 
		return true;
        }

        // 3. Beleidigung/Kritik verarbeiten (Mood geht runter)
        if (cf.command.equals("MOOD_DECREASE")) {
		if ( (Math.abs(moodLevel-10)/25 % 4)  != (Math.abs(moodLevel)/25 % 4) && moodLevel != 0 ) say(pva.texte.get(pva.config.get("conf", "lang_short"), "MOOD_GRUMBLE"));
		this.setVar("impuls", "-10");
        	return true;
        }
        
        if (cf.command.equals("MOOD_IMPULS")) {
		// Hier landet alles, was das System "fühlt"
		// rawtext ist hier der String-Wert des Impulses (z.B. "5" oder "-10")
		
		log("mood-impulse: "+ rawtext);
		
		this.setVar("impuls", rawtext);
		log("MoodManager: Externer Impuls empfangen: " + rawtext + " (Neuer Level: " + moodLevel + ")");
		return true;
	}
        
        return false;
    }

    private String getMoodDescription() {
    	return getMoodedText("MOOD");
    }

    private String getMoodedText(String textdb_key) {

        String lang = pva.config.get("conf", "lang_short");
        String suffix = "";
        String err = "textdb_key="+ textdb_key+" moodLevel="+moodLevel;

        if (moodLevel >= 76)      suffix = "_MOOD_IN_LOVE";
        else if (moodLevel >= 51) suffix = "_MOOD_ENTHUSIASTIC";
        else if (moodLevel >= 26) suffix = "_MOOD_HAPPY";
        else if (moodLevel >= 1)  suffix = "_MOOD_FRIENDLY";
        else if (moodLevel == 0)  suffix = "_MOOD_NEUTRAL";
        else if (moodLevel >= -25) suffix = "_MOOD_GRUMPY";
        else if (moodLevel >= -50) suffix = "_MOOD_ANNOYED";
        else if (moodLevel >= -75) suffix = "_MOOD_SARCASTIC";
        else                       suffix = "_MOOD_LMAA";

        String base = textdb_key + suffix;
        
        if (lastMoodAnswere.get(base).isEmpty()) lastMoodAnswere.put(base, "0");
        int last = Integer.parseInt(lastMoodAnswere.get(base));
        
        int rand;
        int count = 0;
        do {
            rand = (int)(Math.random() * 3) + 1;
            count++;
        } while (rand == last && count < 5);

        String key = base + "_" + rand;
        String result = pva.texte.get(lang, key);


        if (result.isEmpty()) {
            err += " no "+key;
            result = pva.texte.get(lang, base);
            
//          log("key="+ base + " => "+ result);
            
            lastMoodAnswere.put(base, "0");
        } else {
            lastMoodAnswere.put(key, String.valueOf(rand));
        }

        if (result.isEmpty()) {
            err += " no "+base;
            result = pva.texte.get(lang, textdb_key);
            lastMoodAnswere.put(base, "0");
        }

	log(err);
        return result;
    }

	public synchronized void run() {
	    	while (true) {
			try {
	                	if (moodLevel == 0) wait();
	        	        if (moodLevel > 0) {
	        	            moodLevel--;
               			    wait(30 * 60 * 1000);
	        	        } else if (moodLevel < 0) {
               			    moodLevel++;
	        	            wait(5 * 60 * 1000);
               			}
			} catch (InterruptedException e) { break; }
		}
	}

	public synchronized boolean setVar(String name, String value) {
	        if (!setFilter.contains(":" + name + ":")) return false;
	        
	        log("MoodManager:setVar("+name+","+value+")");
	        
	        if (name.equals("impuls")) {
			try {
		                int oldMood = moodLevel;
		                moodLevel += Integer.parseInt(value);
		                if (moodLevel > 100) moodLevel = 100;
	                	if (moodLevel < -100) moodLevel = -100;
//	                	if (oldMood == 0 && moodLevel != 0) notify();
               			log("mood-impulse: newLevel = "+ moodLevel);
	                	
		                return true;
			} catch (Exception e) { return false; }
	        }
	        vars.put(name,value);
	        return true;
	}

	public String getVar(String name) {
	        if (!getFilter.contains(":" + name + ":")) return null;
	        if (name.equals("level")) return String.valueOf(moodLevel);
	        if (name.equals("suffix")) {
			if (moodLevel >= 76)  return "_MOOD_IN_LOVE";
			if (moodLevel >= 51)  return "_MOOD_ENTHUSIASTIC";
			if (moodLevel >= 26)  return "_MOOD_HAPPY";
			if (moodLevel >= 1)   return "_MOOD_FRIENDLY";
			if (moodLevel == 0)   return "_MOOD_NEUTRAL";
			if (moodLevel >= -25) return "_MOOD_GRUMPY";
			if (moodLevel >= -50) return "_MOOD_ANNOYED";
			if (moodLevel >= -75) return "_MOOD_SARCASTIC";
			return "_MOOD_LMAA";
		}

	        log("MoodManager:getVar("+name+")");

		if (name.equals("text")) {
			return getMoodedText( vars.get("textkey") );
		}
        return null;
    }
}

