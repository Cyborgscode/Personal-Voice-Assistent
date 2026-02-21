package data;

import java.util.Vector;

public class AIMessages {

	Vector aimsgs = new Vector<AIMessage>();

	public void addMessage(AIMessage a) {
		aimsgs.add( a );
	}
	
	public String toJSON() {
	
		String res = "[";
		
		for(int i=0; i < this.aimsgs.size(); i++) {
			
			 AIMessage msg = (AIMessage)this.aimsgs.get(i);

			 res += msg.toJSON();
			 
			 if ( i < (this.aimsgs.size()-1) ) res += ",";
		}
	
		res += "]";
		
		return res;
	}
	
	public void clear() {
		this.aimsgs.clear();
	}
	
}

