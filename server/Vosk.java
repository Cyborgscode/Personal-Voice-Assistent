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
	Model model;
	Recognizer recognizer;
	
	public Vosk(PVA pva) {
		this.pva = pva;
	}
	
	void log(String x) { System.out.println(x); }

	boolean ignore = false;
	boolean switching = false;

	public void interrupt() {
		if (!isInterrupted() && ignore==false) {
			ignore = true;
			log("Vosk:interrupt():interrupting myself");
	                super.interrupt();
	        } else log("Vosk:interrupt():already interrupted");
	}
	
	public boolean switchModel(String newmodel) {
		log("VOSK:switchModel:"+ newmodel);
		modelPath = newmodel;
		try {
			switching = true;
//			recognizer.close();
//			model.close();
			model = new Model(modelPath);
			recognizer = new Recognizer(model, 16000);
			log("VOSK:switched model");
			switching = false;
			return true;
		} catch (Exception e) {
			log("VOSK:FAILED TO SWITCH MODEL");
			e.printStackTrace();
			return false;
		}
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

			model = new Model(modelPath);
			recognizer = new Recognizer(model, 16000);

/*			
			// Register a shutdown hook for graceful termination
	                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
		                microphone.close();
        		        System.out.println("Recording stopped.");
        	        }));
*/
			while ( true ) {
				if (isInterrupted() || ignore) {
					log("VOSK: aborting task");
					microphone.close();
					recognizer.close();
					return;
				}

				// AudioLoop : if recognizer does not find anything in the buffer, nothing happens.
				
				while ((bytesRead = microphone.read(data, 0, CHUNK_SIZE)) != -1) {
					if (isInterrupted() || ignore) {
						log("VOSK: aborting task in microphone loop");
						microphone.close();
						recognizer.close();
						return;
					}
					if ( !switching &&  recognizer.acceptWaveForm(data, bytesRead) ) {
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
