
package server.streaming;

import server.*;
import hash.StringHash;
import java.util.Enumeration;
import io.Dos;

public class LiveStreaming extends Streaming {

	public LiveStreaming(PVA pva, StringHash infos, StringHash cmds, String[] videos) {
		
		this.pva = pva;
		this.infos = infos;
		this.cmds = cmds;
		this.videos = videos;
	}


	public void run() {
		try {
			remote("streamplayer");

			// DESKTOP STREAMING
			log("streaming desktop");
			infos.put("streaming","on");
			infos.put("playing","DESKTOP");
			local("camerastream","");
			remote("killplayer");
			infos.put("streaming","off");
				
			
		} catch (Exception localException) {
		}
		
	}
}
