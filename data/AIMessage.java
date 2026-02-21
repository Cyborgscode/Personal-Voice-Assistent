package data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; 

public class AIMessage {

	public String role = "";
	public String model = "";
	public String date = "";
	public String content = "";	
	
	public AIMessage( String r, String m, String c ) {

		this.role = r;
		this.model = m;
		this.date = LocalDateTime.now().format( DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss") );
		this.content = c;
	
	}
	
	public String toJSON() {
	
//		{"role": "user", "model": "User", "date": "2024/08/06 21:25:01", "content": "Hallo"}
		
		return 	"{\"role\":\""+ role +"\",\"model\":\""+ model +"\",\"date\":\""+ date +"\",\"content\":\""+ content +"\"}";		
	
	}

}

