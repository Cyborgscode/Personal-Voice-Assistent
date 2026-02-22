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
import java.lang.NumberFormatException;

class Vosk extends Thread {

	PVA pva;

	Dos dos = new Dos();
	String modelPath = "model"; // Default model path
	Model model;
	Recognizer recognizer;
	
	int threshold = 150; // Alles unter X (RMS) wird ignoriert
	long silenceStart = -1;
	float  signalbooster = 1;
	float runtimebooster = 1;
	long silenceTimeout = 800; // Nach 800ms Stille erzwingen wir ein Ergebnis
	
	public Vosk(PVA pva) {
		this.pva = pva;
		try {
			threshold = Integer.parseInt( pva.config.get("vosk","threshold") );
		} catch (NumberFormatException n) {
			threshold = 150;
		}
		try {
			signalbooster = Float.parseFloat( pva.config.get("vosk","signalbooster") );
		} catch (NumberFormatException n) {
			signalbooster = 1;
			runtimebooster = signalbooster;
		}
		try {
			silenceTimeout = Long.parseLong( pva.config.get("vosk","silencetimeout") );
		} catch (NumberFormatException n) {
			silenceTimeout = 800;
		}
		log("VOSK:init(): boost="+ signalbooster +" threshold="+ threshold +" silence="+ silenceTimeout+"ms");
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
					
					boolean clipping = false;
					short sample;
					int boosted;
					long sum = 0;

				        for (int i = 0; i < bytesRead; i += 2) {

						// Wir wandeln zwei Bytes in einen Short (PCM 16-bit Little Endian)
						sample = (short) ((data[i + 1] << 8) | (data[i] & 0xff));
						sum += Math.abs(sample);
					}

					double rms = sum / (bytesRead / 2.0);
	
					// log("VOSK:loop(): rms="+rms+" > "+ threshold +"?");

				        if (rms > threshold) {

						if ( pva.config.get("vosk","DRCalgorithm").equals("DRC") ) {
						
							float maxSample = 0;
							int i;

							// 1. Den aktuellen Block auf den Spitzenwert prüfen
							for (i = 0; i < bytesRead; i += 2) {
								maxSample = Math.max(maxSample, Math.abs((short) ((data[i + 1] << 8) | (data[i] & 0xff))));
							}
	
							// 2. Clipping-Schutz: Wenn der aktuelle Booster zu laut wäre, sofort hart absenken
							if (maxSample * runtimebooster > 32767) {
								runtimebooster = 32767.0f / maxSample;
							} else {
								// 3. Recovery: Wenn kein Clipping droht, den Booster schrittweise erhöhen
								if (runtimebooster < signalbooster) {
									runtimebooster = Math.min(signalbooster, runtimebooster + 0.1f);
								}
							}

						} else if (  pva.config.get("vosk","DRCalgorithm").equals("SCD") ) {
											
							// an simple noise gate.. safes cpu power AND my nerves
						
							runtimebooster = signalbooster;
							
							do {
								clipping = false;
								for (int i = 0; i < bytesRead; i += 2) {
									sample = (short) ((data[i + 1] << 8) | (data[i] & 0xff));
									// Verstärkungsfaktor (z.B. 1.5 oder 2.0) Default: 1.0 
									boosted = (int)(sample * runtimebooster); 
									// detect Clipping 
									if (boosted > 32767 || boosted < -32768) {
										clipping = true;
										continue;
									}
								}
								if ( clipping ) {						
									runtimebooster = runtimebooster - 0.1f;
								
									// Give up. EMERG Exit, otherwise we end up in an endless loop!
								
									if ( runtimebooster < 0.1 ) clipping = false;
								}

							} while ( clipping );
						} else {
							// no noisegate at all , simple and plain audio boosting 
							runtimebooster = signalbooster;
						}
											
						if ( runtimebooster != 1.0f ) {
																		
						        for (int i = 0; i < bytesRead; i += 2) {

								// Wir wandeln zwei Bytes in einen Short (PCM 16-bit Little Endian)
								sample = (short) ((data[i + 1] << 8) | (data[i] & 0xff));
								
								// Verstärkungsfaktor (z.B. 1.5 oder 2.0) Default: 1.0 
								boosted = (int)(sample * runtimebooster); 
								// Clipping verhindern
								if (boosted > 32767) {
									boosted = 32767;
									clipping = true;
								}
								if (boosted < -32768) {
									boosted = -32768;
									clipping = true;
								}
								sample = (short)boosted;
								// Jetzt wieder zurück in den byte-Puffer schreiben (optional für bessere Erkennung)
								data[i] = (byte)(sample & 0xff);
								data[i+1] = (byte)((sample >> 8) & 0xff);
	
							}
						} else {
							// Clipping Detection in case no signal boost is active
						        for (int i = 0; i < bytesRead; i += 2) {

								// Wir wandeln zwei Bytes in einen Short (PCM 16-bit Little Endian)
								sample = (short) ((data[i + 1] << 8) | (data[i] & 0xff));
								// Verstärkungsfaktor (z.B. 1.5 oder 2.0) Default: 1.0 
								boosted = (int)(sample * runtimebooster); 
								// Clipping verhindern
								if (boosted > 32767 || boosted < -32768) 
									clipping = true;
							}
		
						
						}
						if ( clipping ) {
							log("Vosk: Audioclipping detected");
						}

						silenceStart = -1; // Es ist laut genug, Timer zurücksetzen
					
						if ( !switching &&  recognizer.acceptWaveForm(data, bytesRead) ) {
							String text = recognizer.getResult().replace("'", "");
							//Execute the command
							if ( !text.trim().contains("\"text\" : \"\"" ) ) 
								pva.handleInput( text );
	
						} // nothing to do, if not rdy
					} else {
						if (silenceStart == -1) silenceStart = System.currentTimeMillis();
            
						// Wenn es lange genug still war (Satzende?), erzwingen wir das Resultat
						if (System.currentTimeMillis() - silenceStart > silenceTimeout) {
							String finalResult = recognizer.getFinalResult().replace("'", "");
							if (!finalResult.trim().contains("\"text\" : \"\"")) {
								pva.handleInput(finalResult);
				         		}
				         	       	silenceStart = -1; // Zurücksetzen für den nächsten Satz
						}
					}
				}
		
				// give the mic time to gather more data
				sleep(100L);
			}
			
		} catch (Exception localException) {
			// nothing we can do
		}
	}
}
