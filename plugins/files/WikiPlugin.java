package plugins.files;

import plugins.Plugin;
import io.HTTP;
import io.Dos;
import utils.Tools;
import server.PVA;
import hash.StringHash;
import data.Command;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiPlugin extends Plugin {

	private String name = "WikiPlugin";
	private StringHash vars = new StringHash();

	public void init(PVA pva) {
		this.pva = pva;
	}

	public String getVar(String name) { 
		return vars.get(name); 
	}

	public boolean setVar(String name, String value) { 
		vars.put(name, value); 
		return true; 
	}

	public StringHash getPluginInfo() {
		StringHash info = new StringHash();
		info.put("hasThread", "no");
		info.put("hasCodes", "yes");
		info.put("name", name);
		return info;
	}

	public String[] getActionCodes() {
		return new String[]{"WIKI_SEARCH", "WIKI_SUMMARY_READY"};
	}

	public boolean execute(Command cf, String value) {
		if (cf.command.equals("WIKI_SEARCH")) {
			processWikiRequest(value,cf);
			return true;
		}

		if (cf.command.equals("WIKI_SUMMARY_READY")) {
			say( getT("WIKI_SEARCH_RESULT").replace("<TERM1>", value), cf.filter,cf.negative );
			return true;
		}
		return false;
	}

	private void processWikiRequest(String query,Command cf) {
		String lang = pva.config.get("conf", "lang_short");
		if (lang == null || lang.isEmpty()) lang = "de";

		try {
			String host = lang.toLowerCase() + ".wikipedia.org";
			String encoded = URLEncoder.encode(query.trim(), "UTF-8");
			// Konstruktion des API-Pfads ohne Host-Präfix
			
			String link = "/w/api.php?action=query&prop=extracts&exintro&explaintext&redirects=1&format=json&titles=" + encoded;
			
//			log("Wiki:WIKICALL("+link+")");		
			// Nutzung der spezifischen io.HTTP Signatur
			String response = HTTP.getPageSSL(host, link);
			
			if (response == null || response.isEmpty()) {
				triggerError(cf);
				return;
			}

//			log("Wiki:response="+ response);

			String payload = HTTP.fromUnicode(response);
			
//			log("Wiki:payload="+ payload);

			if (payload.contains("\"missing\":\"\"") || payload.contains("\"missing\":true")) {
				triggerError(cf);
			return;
}

			Matcher m = Pattern.compile("\"extract\":\"(.*?)\"").matcher(payload);
			if (m.find()) {
				String extract = m.group(1).replace("\\n", " ").replace("\\\"", "\"");
//				log("Wiki: extract="+extract);
				
				if (extract.length() < 10 ) {
					triggerError(cf);
				} else if (checkAmbiguous(extract)) {
					String options = extractOptions(extract);
					
//					log("Wiki: msg="+ getT("WIKI_AMBIGUOUS_MSG").replace("<OPTIONS>", options));
					
					say( getT("WIKI_AMBIGUOUS_MSG").replace("<OPTIONS>", options), cf.filter,cf.negative  );
					pva.AsyncSendIntent(new Command("WIKIPLUGIN", "MOOD_IMPULS", "", ""), "-2");
				} else {
					if (extract.length() > 4000) extract = extract.substring(0, 4000);
					pva.AsyncSendIntent(new Command("WIKIPLUGIN", "MOOD_IMPULS", "", ""), "5");
					pva.AsyncSendIntent(new Command("WIKIPLUGIN", "AI_SUMMARIZE", cf.filter, cf.negative), extract);
				}
			} else { triggerError(cf); }
		} catch (Exception e) { triggerError(cf); }
	}
	
	private boolean checkAmbiguous(String text) {
//		log("Wiki:checkAmbiguous("+text+")");
		return Pattern.compile(getT("WIKI_AMBIGUOUS_TRIGGER"), Pattern.CASE_INSENSITIVE).matcher(text).find();
	}

	private String extractOptions(String text) {
//		log("Wiki:extractOptions("+text+")");
		Pattern p = Pattern.compile("(?<=:|für|for|désigne|stands for)\\s+([^,.;\n]+(?:,[^,.;\n]+){0,2})");
		Matcher m = p.matcher(text);
		if (m.find()) return m.group(1).trim();
		return "etwas Spezifischeres";
	}

	private void triggerError(Command cf) {
		say( getT("WIKINOFERROR"), cf.filter,cf.negative  );
		pva.AsyncSendIntent(new Command("WIKIPLUGIN", "MOOD_IMPULS", "", ""), "-5");
	}
}

