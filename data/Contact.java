/* 

Author: Marius Schwarz

License: 

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/


package data;

import java.util.*;
import hash.*;


public class Contact {

	String[] addresse = {};
	String[] name = {};
	String fn = "";
	String bday = "";
	StringHash emails = new StringHash();
	StringHash phones = new StringHash();
	
	String vcard = "";

	public String[] getAddress() { return addresse; }
	public String[] getName() { return name; }
	public String getFullname() { return fn; }
	public String getBirthday() { return bday; }
	public String getEmails() { return emails.toString(); };
	public String getPhones() { return phones.toString(); };
	
	public String exportVcard() { return vcard; }
	public void importVcard(String vcard) { 
		String[] lines = vcard.split("\n");
		for(String line : lines) parseInput(line);
	}
		
	
	public boolean parseInput(String line) {

		if ( line.trim().isEmpty() || line.startsWith(" ") ) return false;
	
		String[] args = line.split(":",2);
		String    key = args[0];
		String  value = args[1];
	
		if ( key.contains("ADR") && key.contains("TYPE=HOME") ) {
			addresse = value.split(";");
			vcard += line+"\n";
		}
				
		if ( key.equals("N") )  {
			name = value.split(";");
			vcard += line+"\n";
		}

		if ( key.equals("FN") )  {
			fn = value;
			vcard += line+"\n";
		}
	
		if ( key.equals("BDAY") )  {
			bday = value;
			vcard += line+"\n";
		}

		if ( key.contains("TEL") && key.contains("TYPE=VOICE") ) {
			if ( key.contains("TYPE=WORK") )  {
				phones.put( "work", value );
				vcard += line+"\n";
			}
			if ( key.contains("TYPE=HOME") )  {
				phones.put( "home", value );
				vcard += line+"\n";
			}
			if ( key.contains("TYPE=VOICE") )  {
				phones.put( "cell", value );
				vcard += line+"\n";
			}
		}
		if ( key.contains("EMAIL") && key.contains("TYPE=INTERNET") ) {
			if ( key.contains("TYPE=WORK") )  {
				emails.put( "work", value );
				vcard += line+"\n";
			}
			if ( key.contains("TYPE=HOME") )  {
				emails.put( "home", value );
				vcard += line+"\n";
			}
			if ( key.contains("TYPE=VOICE") )  {
				emails.put( "cell", value );
				vcard += line+"\n";
			}
		}
		return true;
	}

	public boolean searchForName(String text) {
		if ( text.trim().isEmpty() ) return false;
			
//		System.out.println( "Contacts:searchForName:fn="+ fn.toLowerCase() +" "+ text.toLowerCase() );
		if ( fn.toLowerCase().contains( text.toLowerCase() ) ) return true;
		for(String a : addresse ) 
			if ( !a.isEmpty() && a.toLowerCase().contains(text.toLowerCase() ) ) return true;
		return false;
	}

	public boolean searchForEmail(String text) {
		if ( text.trim().isEmpty() ) return false;
	
		Enumeration en = emails.keys();
		while ( en.hasMoreElements() ) {
			String value = emails.get( (String)en.nextElement() );
			if ( value.toLowerCase().contains(text.toLowerCase()) ) return true;
		}

		return false;
	}

	public boolean searchForPhone(String text) {
		if ( text.trim().isEmpty() ) return false;
	
		Enumeration en = phones.keys();
		while ( en.hasMoreElements() ) {
			String value = phones.get( (String)en.nextElement() );
			if ( value.toLowerCase().contains(text.toLowerCase()) ) return true;
		}

		return false;
	}


}
