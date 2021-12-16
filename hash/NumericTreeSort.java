
package hash;

/**
 * 
 * Diese Klasse stellt einen IMMER sortierten StringArray dar.
 *
 *  internal   Alle Statements, die mit einem geschlossenen Blockkommentar
 *             am Anfang der Zeile markiert sind, haben Debug-Charakter und
 *             sind für den Produktionsbetrieb zu entfernen.
 *
 * @version    $Revision: 1.0 $ ($Date: 2013/12/23 10:20:00 $)
 * @author     Marius Schwarz
 *
 *  
 */


public class NumericTreeSort {

        int counter = 0; // zustand leer
   
        NumericTreeSort links = null;           // Die Ojekte darf man nur erstellen, wenn man sie braucht, sonst gibts nen netten Speicheraufbläher.
        NumericTreeSort rechts = null;

        Long key ;String value="";

        public void add(long s,String v) {
                // s = Long der zu sortieren ist
                // v = die damit assoziierte Zeichenkette ( wenn man z.b. 2 Spalten hat und eine sortieren will, soll sich die andere ja auch umstellen )

                counter ++;

                if ( key == null  ) {
                        key = new Long( s );
                        value = v;
                        return;
                }

                if ( key.compareTo(new Long(s) )> 0) {
                        if ( links == null ) links = new NumericTreeSort();
                        links.add(s,v);
                } else  {
                        if ( rechts == null ) rechts = new NumericTreeSort();
                        rechts.add(s,v);
                }
                return;
        }
        
        public String getKeys_internal() {
                String result = "";
                if ( links != null ) {
                        result = links.getKeys_internal();
                        result += ","+key;
                } else  result = ""+key;

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
                key = null;String v="";
                links = null;
                rechts = null;
        }


        public static void main(String[] args) {
                NumericTreeSort t = new NumericTreeSort();
                t.add( 1000,"1");
                t.add( 1001,"2");
                t.add( 1003,"3");
                t.add( 12,"4");
                t.add( 999,"4");
                t.add( 1234,"5");
                System.out.println("Elemente in meinem Tree            : "+t.size());
                System.out.println("Dies sind die Elemente             : "+t.getKeys_internal());
                System.out.println("Dies sind die Assoziierten Elemente: "+t.getValues_internal());
                
        }

}

