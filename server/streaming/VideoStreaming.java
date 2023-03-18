
package server.streaming;

import server.*;
import hash.StringHash;
import java.util.Enumeration;
import io.Dos;

public class VideoStreaming extends Streaming {

	public VideoStreaming(PVA pva, StringHash infos, StringHash cmds, String[] videos) {
		
		this.pva = pva;
		this.infos = infos;
		this.cmds = cmds;
		this.videos = videos;
	}


	public void run() {
		try {
			remote("streamplayer");

			if ( videos != null  ) {
		
				for(String file : videos ) {

					log("streaming "+ file);
			
					if (isInterrupted() || endthis ) {
						log("killing stream "+ infos.get("ip") );	
						remote("killplayer");
						local("killstreamserver");
						infos.put("streaming","off");
						break;
					}
					infos.put("streaming","on");
					infos.put("playing",file);
					local("streamserver",file);
					infos.put("streaming","off");
				}
	
				remote("killplayer");
			} else {
				log("not videos given :(");				
			}
			
		} catch (Exception localException) {
		}
		
	}
}
