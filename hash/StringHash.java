
/* 
 Author: Marius Schwarz

License: 

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/

package hash;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;

public class StringHash implements Serializable {

   /**
	 * 
	 */
		private static final long serialVersionUID = 9041448221409239248L;
	
	
		private Hashtable<String, String> text   = new Hashtable<String, String>();

        /**
         *  put()
         *
         *  Setzte den Text , der unter "Name" gespeichert werden soll
         *
         */

        public void put(String name,String value) {
                text.put(name,value);
        }

        /**
         *  get()
         *
         *  liest den Wert von Name aus und gibt Ihn als String zurück. Spart viel Tipparbeit im Code..
         *
         *  @param   name  Name der Variablen die ausgelesen werden soll.
         *
         *  @return  String Inhalt des hashes für den Namen "name" oder "" wenn es den Hash nicht gab.
         */

        public String get(String name) {
                if ( text.get(name) != null ) return (String)text.get(name);
                return "";
        }

        /**
         *  clear()
         *
         *  löscht den Hash auf.
         *
         *
         *
         */

        public void clear() {
                text.clear();
        }


        /**
         *   keys()
         *
         *  liest alle Keys aus dem Hash aus                         
        *
        * @return Enumeration Keys
        */

       @SuppressWarnings("rawtypes")
	public Enumeration keys() {
               return text.keys();
       }

       /**
        *   size()
        *
        *  gibt die Anzahl der Keys in einem Hash aus.
        *
        * @return int Anzahl der Keys
        */

       public int size() {
               return text.size();
       }

       /**
        *   remove()
        *
        *  Entfernt einen Key aus dem Hash.
        *
        *  @param   name  Name der Variablen die ausgelesen werden soll.
        *
        *
        */

       public void remove(String key) {
               text.remove(key);
       }

       @Override
	public String toString() {
               @SuppressWarnings("rawtypes")
			Enumeration en = text.keys();
               String ret = "";
               while (en.hasMoreElements()) {
                       String name     = (String)en.nextElement();
                       ret += "\""+ name +"\" = \""+text.get(name)+"\"\n";
                }
               return ret;
       }

}                                      
