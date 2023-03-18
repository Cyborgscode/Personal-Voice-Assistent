
package server.streaming;

import server.*;
import hash.StringHash;
import java.util.Enumeration;
import io.Dos;

public class DesktopStreaming extends Streaming {


	public DesktopStreaming(PVA pva, StringHash infos, StringHash cmds, String[] videos) {

		this.pva = pva;
		this.infos = infos;
		this.cmds = cmds;
		this.videos = videos;
	}

	public void run() {
		try {
			remote("streamplayer");

			log("streaming desktop");
	
			infos.put("streaming","on");
			infos.put("playing","DESKTOP");
			local("desktopstream","");
			remote("killplayer");
			infos.put("streaming","off");
							
		} catch (Exception localException) {
		}
		
	}
}
