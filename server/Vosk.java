
package server;


import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.*;
import java.security.SecureRandom;
import java.io.IOException;
import javax.sound.sampled.*;

import org.vosk.LogLevel;
import org.vosk.Recognizer;
import org.vosk.LibVosk;
import org.vosk.Model;

import io.Dos;

class Vosk extends Thread {

	PVA pva;

	Dos dos = new Dos();
	String modelPath = "model"; // Default model path
	
	public Vosk(PVA pva) {
		this.pva = pva;
	}
	
	void log(String x) { System.out.println(x); }

	public void interrupt() {
                Thread.currentThread().interrupt();
	}
	
	public void run() {
		try {

			if ( !pva.config.get("vosk","model").equals("") ) {
				modelPath = pva.config.get("vosk","model");
			}

			if (! dos.fileExists(modelPath)) {
			    System.out.println("#### BIG MISTAKE #### NO LANGUAGE MODEL FOUND ####");
			    System.out.println("Please download a model for your language from https://alphacephei.com/vosk/models");
			    System.out.println("and place it in /etc/pva/conf.d/01-default.conf  under : vosk:\"model\",\"vosk-model-de-0.21\"");
			    return;
			}

			AudioFormat format = new AudioFormat(
		                AudioFormat.Encoding.PCM_SIGNED, // Encoding
		                16000.0f, // Sample rate (44.1kHz)
		                16,       // Sample size in bits
		                1,        // Channels (stereo)
		                2,        // Frame size (frame size = 16 bits/sample * 2 channels = 4 bytes)
		                16000.0f, // Frame rate (matches sample rate for PCM)
		                false     // Big-endian (false = little-endian)
		        );


			TargetDataLine microphone = AudioSystem.getTargetDataLine(format);

			int bytesRead;
			int CHUNK_SIZE = microphone.getBufferSize() / 5;
			byte[] data = new byte[CHUNK_SIZE];
			microphone.open(format,CHUNK_SIZE);
			microphone.start();

			Model model = new Model(modelPath);
			Recognizer recognizer = new Recognizer(model, 16000);
			
			// Register a shutdown hook for graceful termination
	                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
		                microphone.close();
        		        System.out.println("Recording stopped.");
        	        }));

			while ( true ) {
				if (isInterrupted()) {
					return;
				}

				// AudioLoop : if recognizer does not find anything in the buffer, nothing happens.
				
				while ((bytesRead = microphone.read(data, 0, CHUNK_SIZE)) != -1) {
					if (isInterrupted()) {
						return;
					}
					if ( recognizer.acceptWaveForm(data, bytesRead) ) {
						String text = recognizer.getResult().replace("'", "");
						//Execute the command
						if ( !text.trim().contains("\"text\" : \"\"" ) ) 
							pva.handleInput( text );

					} // nothing to do, if not rdy
				}
			
				// give the mic time to gather more data
				sleep(100L);
			}
			
		} catch (Exception localException) {
			// nothing we can do
		}
	}
}


	

	
