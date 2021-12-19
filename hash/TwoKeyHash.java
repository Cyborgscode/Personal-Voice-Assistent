
/* 
 Author: Marius Schwarz

License: 

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/

package hash;

import java.util.Hashtable;
import java.util.Enumeration;
import hash.StringHash;


public class TwoKeyHash {

        private Hashtable root = new Hashtable();

        public void put(String key,String key2,String value) {
                if ( root.get(key)!=null ) {
                        StringHash sh = (StringHash)root.get(key);
                        sh.put(key2,value);
                        return;
                }
                StringHash sh = new StringHash();
                root.put(key,sh);
                sh.put(key2,value);
                return;
        }

        public String get(String key,String key2) {
                if ( root.get(key)!=null ) {
                        StringHash sh = (StringHash)root.get(key);
                        return sh.get(key2);
                }
                return "";
        }

        public StringHash get(String key) {
                if ( root.get(key)!=null ) {
                        return (StringHash)root.get(key);
                }
                return null;
        }

        public Enumeration keys() {
                return root.keys();
        }

	public int size() {
                return root.size();
        }

	public boolean remove(String key) {
	
		StringHash x = (StringHash)root.remove(key);
		if ( x != null ) return true;
		return false;

	}

	public boolean remove(String key,String innerkey) {

		StringHash x = (StringHash)root.get(key);
		if ( x!=null) {
			x.remove(innerkey);
			return true;
		} 
		return false;
	}

        public void clear() {
                root.clear();
        }

}
    
