
/* 
 Author: Marius Schwarz

License: 

This software is free. You can copy it, use it or modify it, as long as the result is also published on this condition.
You only need to refer to this original version in your own readme / license file. 

*/

package hash;

import java.util.Hashtable;
import java.util.Enumeration;

public class ThreeKeyHash {

        private Hashtable<String, Object> root = new Hashtable<String, Object>();

        public void put(String key,String key2,String key3,String value) {

                if ( root.get(key)!=null ) {
                        TwoKeyHash sh = (TwoKeyHash)root.get(key);
                        sh.put(key2,key3,value);
                        return;
                }
                TwoKeyHash sh = new TwoKeyHash();
                root.put(key,sh);
                sh.put(key2,key3,value);
                return;
        }

	public boolean remove(String key,String key2,String key3) {
                if ( root.get(key)!=null ) {
                        TwoKeyHash sh = (TwoKeyHash)root.get(key);
                        return sh.remove(key2, key3);
                }
                return false;
        }

        public String get(String key,String key2,String key3) {
                if ( root.get(key)!=null ) {
                        TwoKeyHash sh = (TwoKeyHash)root.get(key);
                        return sh.get(key2,key3);
                }
                return "";
        }

        public TwoKeyHash get(String key) {
                if ( root.get(key)!=null ) {
                        return (TwoKeyHash)root.get(key);
                }
                return null;
        }

        public Enumeration<String> keys() {
                return root.keys();
        }

        public void clear() {
                root.clear();
        }
}
    
