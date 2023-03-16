// Utility class for command handling

class Command {

	public String words = "";
	public String command = "";
	public String filter = "";
	public String negative = "";
	public Vector terms = null;
	
	public Command (String w,String c,String f,String n) {
		this.words = w;
		this.command = c;
		this.filter = f;
		this.negative = n;
		this.terms = new Vector();
	}
	public Command (String w,String c,String f,String n,Vector t) {
		this.words = w;
		this.command = c;
		this.filter = f;
		this.negative = n;
		if ( t != null ) {
			this.terms = t;
		} else	this.terms = new Vector();
	}
}
