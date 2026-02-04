/*
	This code was 98%  engineered by gemma3.. it failed miserable for the last 2% without the plugin would not work at all :D

	The plugin tries to open the users Firefox history, but this only works, if firefox is not running. 

	The places.sqlite database is locked when firefox runs. 

	So, take this as an example and not as a sensible working plugin .. a working config is in the users config directory

*/

package plugins.files;

import plugins.Plugin;
import io.Dos;
import server.PVA;
import hash.StringHash;
import hash.TwoKeyHash;
import data.Command;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Properties;

public class FirefoxHistoryPlugin extends Plugin {

    private String getFilter = ":firefoxhistory:";
    private String setFilter = ":firefoxhistory:";
    private TwoKeyHash validValues = new TwoKeyHash();
    private Dos dos = new Dos();

    public FirefoxHistoryPlugin() {
        System.out.println("Class FirefoxHistoryPlugin Constructor called");
    }

    public void init(PVA pva) {
        this.pva = pva;
        info.put("hasThread", "yes");
	info.put("hasCodes","yes");  
        info.put("codes", "GETFIREFOXHISTORY");
        info.put("name", "FirefoxHistoryPlugin");
        vars.put("firefox_profile", "");
        validValues.put("firefox_profile", "default", "ok"); // Placeholder - actual profile selection needs logic
    }

    public StringHash getPluginInfo() {
        return this.info;
    }

    public String getVar(String name) {
        if (getFilter.contains(":" + name + ":")) {
            return vars.get(name);
        }
        return "";
    }

    public boolean setVar(String name, String value) {
        if (getFilter.contains(":" + name + ":") && validValues.get(name, value).equals("ok")) {
            vars.put(name, value);
            return true;
        }
        return false;
    }

    public String[] getActionCodes() {
        return "GETFIREFOXHISTORY".split(":");
    }

    public boolean execute(Command cf, String rawtext) {
        try {
            if (cf.command.equals("GETFIREFOXHISTORY")) {
                String profilePath = getFirefoxProfilePath();
                if (profilePath != null && !profilePath.isEmpty()) {
                    vars.put("firefox_profile", profilePath);
                    List<String> history = readFirefoxHistory(profilePath);
                    if (history != null && !history.isEmpty()) {
                        pva.say(pva.texte.get(pva.config.get("conf","lang_short"), "firefoxhistory.history_prompt")); //Multilingual prompt
                        for (String url : history) {
                            pva.say(url);
                        }
                    } else {
                        pva.say(pva.texte.get(pva.config.get("conf","lang_short"), "firefoxhistory.no_history")); //Multilingual message
                    }
                } else {
                    pva.say(pva.texte.get(pva.config.get("conf","lang_short"), "firefoxhistory.no_profile")); //Multilingual message
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            // pva.say(pva.texte.get(pva.config.get("conf","lang_short"), "firefoxhistory.error")); //Multilingual error message
            return true; // Prevent blocking other plugin executions
        }
    }

    private String getFirefoxProfilePath() {
        String firefoxProfileDir = "/home/" + System.getProperty("user.name") + "/.mozilla/firefox";
	
	String[] ini = dos.readFile(firefoxProfileDir+"/installs.ini").split("\n");
	for(String line : ini ) {
		if ( line.startsWith("Default") ) {
			return line.split("=")[1];
		}
	}
	return null;
	
    }

    private List<String> readFirefoxHistory(String profilePath) throws SQLException {
        List<String> history = new ArrayList<>();
        String historyPath = "/home/" + System.getProperty("user.name") + "/.mozilla/firefox/" + profilePath + "/places.sqlite";

	System.out.println("Class FirefoxHistoryPlugin: path="+historyPath);

	Properties config = new Properties();
	config.setProperty("open_mode", "1");  //1 == readonly
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + historyPath, config)) {
            String sql = "SELECT url FROM moz_places ORDER BY last_visit_date DESC LIMIT 3";
            try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
                while (resultSet.next()) {
                    history.add(resultSet.getString("url"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reading history from SQLite: " + e.getMessage());
            return null;
        }
        return history;
    }

    public void run() {
        // No continuous operation needed for this plugin.
    }
}

