package hash;


public class TreeSort {

        int counter = 0; // zustand leer

        TreeSort links = null;          // Die Ojekte darf man nur erstellen, wenn man sie braucht, sonst gibts nen netten Speicheraufbläher.
        TreeSort rechts = null;

        String key ="";String value="";

        public void add(String s,String v) {
                // s = String der zu sortieren ist
                // v = die damit assoziierte Zeichenkette ( wenn man z.b. 2 Spalten hat und eine sortieren will, soll sich die andere ja auch umstellen )

                counter ++;

                if ( key.equals("") ) {
                        key = s;
                        value = v;
                        return;
                }

                if ( key.compareTo(s)>0) {
                        if ( links == null ) links = new TreeSort();
                        links.add(s,v);
                } else  {
                        if ( rechts == null ) rechts = new TreeSort();
                        rechts.add(s,v);
                }
                return;
        }


        public String getKeys_internal() {
                String result = "";
                if ( links != null ) {
                        result = links.getKeys_internal();
                        result += ","+key;
                } else  result = key;

                if ( rechts != null ) result += ","+rechts.getKeys_internal();
                return result;

        }
        
        public String getValues_internal() {
        	String result = "";
        	if ( links != null ) {
                result = links.getValues_internal();
                result += ","+value;
        	} else  result = value;

        	if ( rechts != null ) result += ","+rechts.getValues_internal();
        	return result;
        }

        public String[] getKeys()   { return getKeys_internal().split(",");   }
        public String[] getValues() { return getValues_internal().split(","); }
        public String   get(int index) {
        	if ( index < counter && index>= 0 ) return getValues_internal().split(",")[index];
        	return "";
        }

        public int size() {
        	return counter;
        }

	public void reset() {
        if ( links!=null ) links.reset();
        if ( rechts!=null ) rechts.reset();
        counter = 0;
        key ="";
        links = null;
        rechts = null;
	}




	public static void main(String[] args) {
        TreeSort t = new TreeSort();
        t.add("Hamster","1");
        t.add("Kugelblitz","2");
        t.add("_classic","3");
        t.add("Kugelblitz2","4");
        t.add("Kugelblitz3","4");
        t.add("kugelblitz4","5");
        t.add("kugelblitz4","6");
        t.add("kugelblitz4","7");
        System.out.println("Elemente in meinem Tree            :"+t.size());
        System.out.println("Dies sind die Elemente             : "+t.getKeys_internal());
        System.out.println("Dies sind die Assoziierten Elemente: "+t.getValues_internal());

	}


}
