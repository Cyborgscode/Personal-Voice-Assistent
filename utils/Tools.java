package utils;

public class Tools {

	static public String zwischen(String buffer,String vor,String nach) {

                int i1 = buffer.indexOf(vor)+vor.length();
                int i2 = buffer.indexOf(nach,i1);

                if ( i1 >= 0 && i2 > i1 ) {

                        return buffer.substring(i1,i2);

                }

                return null;
        }

	// JSON Object is given by LLM, but the org.json package can't be shipped with distros, so we need to do it ourself, so .. don't wonder it's messy ;)

	public static String parseJSON(String json) {
	
		json=json.replaceAll("\\\\.","");
		
		String[] pairs = json.split("(\",\"|},\")");
					
		String answere = "";
					
		for(String pair: pairs) {
//			log( "pair = "+ pair);
										
			if ( pair.contains(":") ) {
				String[] data = pair.split(":",2);
				if ( data.length > 1 ) {	

					String key = data[0].replaceAll("\"","");
					String value = data[1];
												
//					log("key="+ key +"\nvalue="+ value );

					if ( key.endsWith("\"") ) key = key.substring(0,key.indexOf("\"")-1);
					if ( value.endsWith("\"") ) value = value.substring(0,value.indexOf("\"",1));

//					log("key="+ key +"\nvalue="+ value );
												
					if ( ( key.equals("response") || key.equals("content") ) && value.trim().length() > 1 ) {
						answere = value.substring(1).replaceAll("\\n","\n");
					}
				}
				if ( answere == null ) answere = "";
			} 
		}

		return answere;
		
	}

	static public String filterAIThinking(String answere) {
	
		if ( answere.contains("003cthink003e") ) {
			int abis = answere.indexOf("003c/think003e")+"003c/think003e".length();
			if ( abis >= 0 ) 
				answere = answere.substring( abis );
		}
		return answere.replaceAll("\\*\\*","").replaceAll("\\*","\"");
	}



}
