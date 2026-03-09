package plugins.files;

import plugins.Plugin;
import server.PVA;
import hash.StringHash;
import data.Command;
import java.util.*;
import utils.Tools;
import io.Dos;
import java.io.*;
import java.util.regex.Pattern;

public class TimerPlugin extends Plugin {

	private PVA pva;
	private final Object lock = new Object();
	private final TreeMap<Long, String> activeTimers = new TreeMap<>();
	private List<Long> lastCandidates = new ArrayList<>();
	private final String CONF_PATH = System.getenv("HOME").trim() + "/.config/pva/timers.conf";
	private String name = "TimerPlugin";
	private StringHash info = new StringHash();
	private Dos dos = new Dos();
	private String[] za = null;

	public void init(PVA pva) {
		this.pva = pva;
		info.put("name", name);
		info.put("hasThread", "yes");
		info.put("hasCodes", "yes");
		loadTimers();
	}

	public StringHash getPluginInfo() { return info; }
	public String getVar(String name) { return vars.get(name); }
	public boolean setVar(String name, String value) { vars.put(name, value); return true; }

	public String[] getActionCodes() {
		return new String[]{"MAKE_TIMER", "MAKE_TIMERRELATIVE", "TIMER_SET", "TIMER_CANCEL", "TIMER_CANCEL_RELATIVE", "TIMER_CONFIRM_INDEX", "TIMER_LIST", "SHOWTIMER", "TIMER_CLEAR_ALL"};
	}

	public boolean execute(Command cf, String rawtext) {
	
		String text_raw = rawtext.toLowerCase();

		// log("execute:"+ cf.toString() + " "+ text_raw);

		if (this.za == null) {
			// avoid RACE Condition if plugins start fast than the core.
			this.za = pva.getZa();
		}

		if (cf.command.equals("MAKE_TIMER")) return parseAbsoluteTimer(text_raw, cf.filter);
		if (cf.command.equals("MAKE_TIMERRELATIVE")) return parseRelativeTimer(text_raw, cf.filter);
		
		if (cf.command.equals("TIMER_SET")) {
			try {
				long ts = Long.parseLong(rawtext.trim());
				synchronized (lock) {
					// Plugins know what we want.. no guessing.
					String data = cf.filter + ":" + cf.negative;
					activeTimers.put(ts, data);
					lock.notifyAll();
				}
				saveTimers();
			} catch (Exception e) {}
			return true;
		}
		
		if (cf.command.equals("TIMER_CANCEL"))            return handleAbsoluteCancel(rawtext, cf.filter);
		if (cf.command.equals("TIMER_CANCEL_RELATIVE"))   return handleRelativeCancel(rawtext, cf.filter);
		if (cf.command.equals("TIMER_CONFIRM_INDEX"))     return handleIndexConfirm(rawtext, cf.filter);
		if (cf.command.equals("TIMER_LIST")) { listAll(); return true; }
		if (cf.command.equals("SHOWTIMER")) { listAll();  return true; }
		
		if (cf.command.equals("TIMER_CLEAR_ALL")) {
			synchronized (lock) { 
				activeTimers.clear(); 
				lastCandidates.clear();
				saveTimers(); 
				lock.notifyAll(); 
			}
			say(getT("TIMER_ALL_DELETED"));
			return true;
		}
		return false;
	}

	public void run() {
		while (!isInterrupted()) {
			synchronized (lock) {
				long now = System.currentTimeMillis();
				while (!activeTimers.isEmpty() && activeTimers.firstKey() <= now) {
					Map.Entry<Long, String> entry = activeTimers.pollFirstEntry();
					dispatchTimer(entry.getValue());
					saveTimers();
				}
				long waitTime = activeTimers.isEmpty() ? 0 : Math.max(1, activeTimers.firstKey() - System.currentTimeMillis());
				try {
					if (waitTime == 0) lock.wait();
					else lock.wait(waitTime);
				} catch (InterruptedException e) { break; }
			}
		}
	}

	private void dispatchTimer(String data) {
		String[] parts = data.split(":", 2);
		if (parts.length == 2) {
			// Intent Re-Injection: parts[0]=Action, parts[1]=Payload
			pva.AsyncSendIntent(new Command(name, parts[0], "", "TIMER_EXPIRED"), parts[1]);
		} else {
			// Plain text reminder
			say(getT("MAKETIMERRESPONSE").replaceAll("<TERM>", data));
		}
		pva.AsyncSendIntent(new Command(name, "MOOD_IMPULS", "", ""), "2");
	}
	
	private class TimeResult {
	
		public Calendar c;
		public String subject;
		
		public TimeResult(Calendar c, String subject) { this.c = c; this.subject = subject; };
	
	}	

	private TimeResult getAbsoluteTime(String text_raw) {

		String[] wochentage = getT("WEEKDAYS").split("\\|");
		String[] words_future = getT("FUTUREKEYWORDS").split("\\|");
		Calendar rightNow = Calendar.getInstance();

		Pattern pattern = Pattern.compile("(" + getT("MAKETIMERAT") + ")");
		String[] stepone = pattern.split(text_raw.replaceAll(pva.config.get("conf", "keyword"), "").trim(), 2);
		if (stepone.length == 2) {
		
			pattern = Pattern.compile("(" + getT("MAKETIMERFOR") + ")");
			String[] steptwo = pattern.split(stepone[1], 2);
			if (steptwo.length == 2) {
				String time = steptwo[0].trim();
				String subject = steptwo[1].trim();
				
				// log("abs: time="+time +" "+ subject);
				
				int when = 0, hour = -1, minutes = 0, weekday = 0;

				for (int i = 0; i < words_future.length; i++)
					if (text_raw.contains(words_future[i])) when = i;
				for (int i = 0; i < wochentage.length; i++)
					if (text_raw.contains(wochentage[i]) && !subject.contains(wochentage[i])) weekday = i + 1;

				String[] times = time.split(getT("MAKETIMERTIMEWORD"));
				if (times.length >= 1) {
					for (int i = 0; i < za.length && i < 24; i++)
						if (times[0].trim().matches(za[i])) hour = i;
				}
				if (times.length == 2) {
					for (int i = 0; i < za.length && i < 60; i++)
						if (times[1].trim().matches(za[i])) minutes = i;
				}

				if (hour > -1) {
					if (rightNow.get(Calendar.DAY_OF_WEEK) >= weekday && weekday != 0) {
						rightNow.add(Calendar.DAY_OF_MONTH, 7);
						rightNow.set(Calendar.DAY_OF_WEEK, weekday);
					}
					rightNow.set(Calendar.HOUR_OF_DAY, hour);
					rightNow.set(Calendar.MINUTE, minutes);
					rightNow.set(Calendar.SECOND, 0);
					rightNow.set(Calendar.MILLISECOND, 0);
					if (when > 0) rightNow.add(Calendar.DAY_OF_MONTH, when);

					if (rightNow.getTimeInMillis() < System.currentTimeMillis()) {
						rightNow.add(Calendar.DAY_OF_MONTH, 1);
					}
					return new TimeResult( rightNow, subject );
				}
			}
		}
		return null;
	}

	private boolean parseAbsoluteTimer(String text_raw, String filter) {

		TimeResult x = getAbsoluteTime(text_raw);
		if ( x != null ) {
			Calendar rightNow = x.c;
			String subject = x.subject;
		
			String data = (filter.isEmpty()||filter.equals("NONE")) ? subject : filter + ":" + subject;
			synchronized (lock) { activeTimers.put(rightNow.getTimeInMillis(), data); lock.notifyAll(); }
			saveTimers();
			vars.put("last_timer_id", String.valueOf(rightNow.getTimeInMillis()));
			say(getT("MAKETIMEROK").replaceAll("<TERM1>", makeDate(rightNow.getTimeInMillis())));
		} else {
			say(getT("MAKETIMERERROR"));
		}
		return true;
	}

	private String makeDate(long time) {

		Calendar d = Calendar.getInstance();
		d.setTime( new Date(time) );
		String[] months = pva.config.get("conf","months").split(":");
		String minutes = ""+d.get(Calendar.MINUTE);
		if ( minutes.length()==1 ) minutes = "0"+minutes;
			
		return d.get(Calendar.DAY_OF_MONTH)+"."+( months[ d.get(Calendar.MONTH) ] )+" "+d.get(Calendar.HOUR_OF_DAY)+":"+ minutes;
	}

	private TimeResult getRelativeTime(String text_raw) {
	
		Calendar rightNow = Calendar.getInstance();

		// BASE UNIT is MINUTES !

		Pattern pattern = Pattern.compile("(" + getT("MAKETIMERATRELATIVE") + ")");
		String[] stepone = pattern.split(text_raw.replaceAll(pva.config.get("conf", "keyword"), "").trim(), 2);
		if (stepone.length == 2) {
			pattern = Pattern.compile("(" + getT("MAKETIMERFOR") + ")");
			String[] steptwo = pattern.split(stepone[1], 2);
			if (steptwo.length == 2) {
				String time = steptwo[0].trim();
				String subject = steptwo[1].trim();

				// analyse time
				// we assume "today" , which is the absence of any match
					
				// log("time="+time +" subject="+ subject);
					
				int when = 0, multiply = 1;
	
				if ( text_raw.contains( getT("TIMEHOURS") ) ) {
					multiply = 60;
				} else 	if ( text_raw.contains( getT("TIMEDAYS") ) ) {
					multiply = 60*24;
				}
						
				// log( "multiply="+ multiply);

				for(int i=0;i<za.length;i++)
					if ( text_raw.matches( ".* "+za[i]+" .*" ) || text_raw.contains(" "+za[i]+" ") ) 
						when = i+1;

				if ( when > 0 ) {	
					for ( int i=0; i < when*multiply; i++ )
						rightNow.add(Calendar.MINUTE, 1 );
					rightNow.set(Calendar.SECOND, 0 );
					rightNow.set(Calendar.MILLISECOND, 0 );

					return new TimeResult(rightNow, subject);
				}
			}
		}
		return null;
	}
	
	private boolean parseRelativeTimer(String text_raw, String filter) {

		TimeResult x = getRelativeTime(text_raw);
		if ( x != null ) {
			Calendar rightNow = x.c;
			String subject = x.subject;
		
			String data = (filter.isEmpty()||filter.equals("NONE")) ? subject : filter + ":" + subject;
			synchronized (lock) { activeTimers.put(rightNow.getTimeInMillis(), data); lock.notifyAll(); }
			vars.put("last_timer_id", String.valueOf(rightNow.getTimeInMillis()));
			say(getT("MAKETIMEROK").replaceAll("<TERM1>", makeDate(rightNow.getTimeInMillis())));

		} else say(getT("MAKETIMERERROR"));

		return true;
	}

	private boolean handleAbsoluteCancel(String rawtext, String filter) {
		String input = rawtext.trim().toLowerCase();
		
		// compensate missing step2 in get* , not sure it works in all languagues
		
		input += " "+ getT("MAKETIMERFOR").split("\\|")[0] + " XXX";
		
		TimeResult x = getAbsoluteTime( input );
		if ( x != null ) {
		
			synchronized (lock) {
				
				long now = x.c.getTimeInMillis();
				long delta = 300000L; // Knallharte 5 Minuten Toleranz
				long minTS = now - delta;
				long maxTS = now + delta;

				lastCandidates.clear();
				// Wir suchen in der Map nach einem Treffer in diesem engen Fenster
				for (Long ts : activeTimers.keySet()) {
					if (ts >= minTS && ts <= maxTS) {
						lastCandidates.add(ts);
					}
				}

				// Fallback, wenn keine Zeit, aber nen Obkject  angegeben ist... (desperate move, yes)
								
				for (Map.Entry<Long, String> entry : activeTimers.entrySet()) {
					// Wir suchen im Value (z.B. "MATRIX_CHECK:Pizza") nach dem Wort "Pizza"
					if (entry.getValue().toLowerCase().contains(input)) {
						lastCandidates.add(entry.getKey());
					}
				}

				if (lastCandidates.size() == 1) {
					// Volltreffer: Eindeutiger absoluter Termin
					activeTimers.remove(lastCandidates.get(0));
					saveTimers();
					say(getT("TIMER_DELETED_OK"));
				} else if (lastCandidates.size() > 1) {
					// Falls jemand zwei Termine um "zwölf" hat (z.B. Pizza & Matrix)
					String msg = getT("TIMER_MULTI_MATCH").replaceAll("<TERM1>", "" + lastCandidates.size());
					for (int i = 0; i < lastCandidates.size() && i < 3; i++) {
						msg += getT("TIMER_LIST_ITEM").replaceAll("<TERM1>", "" + (i + 1))
													  .replaceAll("<TERM2>", makeDate(lastCandidates.get(i)));
					}
					say(msg + getT("TIMER_WHICH_ONE"));
				} else {
					// Fallback: Wenn unter der berechneten Zeit nichts liegt, 
					// schauen wir nach dem LIFO-Anker (Zuletzt gesetzt)
					String lastID = vars.get("last_timer_id");
					if (!lastID.isEmpty() && activeTimers.containsKey(Long.parseLong(lastID))) {
						activeTimers.remove(Long.parseLong(lastID));
						saveTimers();
						vars.remove("last_timer_id");
						say(getT("TIMER_DELETED_OK"));
					} else {
						say(getT("TIMER_NOT_FOUND"));
					}
				}
			} // synchronized end
		} else {
			say(getT("TIMER_NOT_FOUND"));
		}
		return true;
	}

	private boolean handleRelativeCancel(String rawtext, String filter) {
		String input = rawtext.trim().toLowerCase();
	
		// Same fix as for Abs

		input += " "+ getT("MAKETIMERFOR").split("\\|")[0] + " XXX";
			
		TimeResult x = getRelativeTime( input );
		if ( x != null ) {
		
			synchronized (lock) {
				long targetTS = x.c.getTimeInMillis();
				long diff = Math.abs(targetTS - System.currentTimeMillis());
				
				// Adaptive Range: Je weiter weg, desto unschärfer (VOSK-Puffer)
				// < 1h: 5 Min | < 12h: 30 Min | > 12h: 60 Min
				long delta = 300000L; 
				if (diff > 43200000L)      delta = 3600000L; 
				else if (diff > 3600000L)  delta = 1800000L;

				long minTS = targetTS - delta;
				long maxTS = targetTS + delta;

				lastCandidates.clear();
				// 1. Suche im Zeitfenster
				for (Long ts : activeTimers.keySet()) {
					if (ts >= minTS && ts <= maxTS) {
						lastCandidates.add(ts);
					}
				}

				// 2. Namens-Check (falls Zeitangabe allein nicht eindeutig war)
				// Wir suchen zusätzlich nach Subjekten im Input
				for (Map.Entry<Long, String> entry : activeTimers.entrySet()) {
					if (input.contains(entry.getValue().toLowerCase()) || entry.getValue().toLowerCase().contains(input)) {
						if (!lastCandidates.contains(entry.getKey())) {
							lastCandidates.add(entry.getKey());
						}
					}
				}

				if (lastCandidates.size() == 1) {
					activeTimers.remove(lastCandidates.get(0));
					saveTimers();
					say(getT("TIMER_DELETED_OK"));
				} else if (lastCandidates.size() > 1) {
					String msg = getT("TIMER_MULTI_MATCH").replaceAll("<TERM1>", "" + lastCandidates.size());
					for (int i = 0; i < lastCandidates.size() && i < 3; i++) {
						msg += getT("TIMER_LIST_ITEM").replaceAll("<TERM1>", "" + (i + 1))
													  .replaceAll("<TERM2>", makeDate(lastCandidates.get(i)));
					}
					say(msg + getT("TIMER_WHICH_ONE"));
				} else {
					// Fallback auf LIFO (Zuletzt gesetzt)
					String lastID = vars.get("last_timer_id");
					if (!lastID.isEmpty() && activeTimers.containsKey(Long.parseLong(lastID))) {
						activeTimers.remove(Long.parseLong(lastID));
						saveTimers();
						vars.remove("last_timer_id");
						say(getT("TIMER_DELETED_OK"));
					} else {
						say(getT("TIMER_NOT_FOUND"));
					}
				}
			} // synchronized end
		} 
		return true;
	}
	private boolean handleIndexConfirm(String rawtext, String filter) {

		// W.I.P. needs testing

		// log("HandleIndex:"+ rawtext + " filter="+ filter);
	
		synchronized (lock) {
			int idx = -1;
			// Semantische Prüfung via getT() Keys
			if (rawtext.contains(getT("WORD_FIRST"))) idx = 0;
			else if (rawtext.contains(getT("WORD_SECOND"))) idx = 1;
			else if (rawtext.contains(getT("WORD_THIRD"))) idx = 2;
			else if (rawtext.contains(getT("WORD_LAST"))) idx = lastCandidates.size() - 1;

			// Validierung des berechneten Index
			if (idx >= 0 && idx < lastCandidates.size()) {
				activeTimers.remove(lastCandidates.get(idx));
				lastCandidates.clear();
				saveTimers();
				say(getT("TIMER_DONE_OK"));
				lock.notifyAll();
			} else {
				// FEHLERFALL
				pva.AsyncSendIntent(new Command(name, "MOOD_IMPULS", "", ""), "-10");
				say(getT("TIMER_INDEX_INVALID")); 
				lastCandidates.clear(); // Reset state
			}
		}
		return true;
	}

	private void listAll() {
		StringBuilder sb = new StringBuilder();
		synchronized (lock) {
			if (activeTimers.isEmpty()) sb.append(getT("SHOWTIMERRESPONSENOTHING"));
			else {
				for (Map.Entry<Long, String> e : activeTimers.entrySet())
					sb.append(getT("SHOWTIMERRESPONSE").replaceAll("<TERM1>", makeDate(e.getKey())).replaceAll("<TERM2>", e.getValue())).append("\n");
			}
		}
		say(sb.toString());
	}

	private void loadTimers() {
		File f = new File(CONF_PATH);
		if (!f.exists()) return;
		try (BufferedReader br = new BufferedReader(new FileReader(f))) {
			String l;
			synchronized (lock) {
				while ((l = br.readLine()) != null) {
					String[] x = l.split(":", 2);
					if (x.length == 2) activeTimers.put(Long.parseLong(x[0]), x[1]);
				}
			}
		} catch (Exception e) {}
		
	}

	private void saveTimers() {
		StringBuilder sb = new StringBuilder();
		synchronized (lock) {
			for (Map.Entry<Long, String> e : activeTimers.entrySet())
				sb.append(e.getKey()).append(":").append(e.getValue()).append("\n");
		}
		dos.writeFile(CONF_PATH, sb.toString());
	}

	public void shutdown() { saveTimers(); }
}

