
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

public class pva {

	public static void main(String[] args) {
		String modelPath = "model"; // Default model path
		Dos dos = new Dos();

		try {
			// Model Path parsing

			for(int i=0;i<args.length;i++) {
				if ( args[i].equals("-m") ) {
					i++; // skip -m
					modelPath = args[i];
				}
			}
			
			if (! dos.fileExists(modelPath)) {
			    System.out.println("Please download a model for your language from https://alphacephei.com/vosk/models");
			    System.out.println("and use -m /path/file to tell us what to use");
			    System.exit(0);
			}

			// set 16k hz samplerate, which works way better than 44k1 hz!

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
			
			//Process Audio in a loop
			
			while ((bytesRead = microphone.read(data, 0, CHUNK_SIZE)) != -1) {
				if ( recognizer.acceptWaveForm(data, bytesRead) ) {
					String text = recognizer.getResult().replace("'", "");
					//Execute the command
					if ( !text.trim().contains("\"text\" : \"\"" ) ) sendData( text );
					// System.out.println("readPipe("+command+") => "+ dos.readPipe(command, true) );
				} // nothing to do, if not rdy
			}

        } catch (Exception e) {
		e.printStackTrace();
		System.exit(1);
        }
    }

private static TrustManager[] trustAllCerts = new TrustManager[]{
    new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }
        public void checkClientTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
        }
        public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
        }
    }
};

private static SSLSocket createSSLSocket(String host,int port) {

        try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                        
                SSLSocketFactory factory = (SSLSocketFactory) sslContext.getSocketFactory();

                SSLSocket socket = (SSLSocket) factory.createSocket(host, port );
                socket.setEnabledProtocols(new String[] { "TLSv1.2","TLSv1.3" });

                return socket;
        } catch (Exception e) {
                // Egal welche Exception kommt, ist das gleiche Ergebnis
                return null;
        }
}

	static public void sendData(String data) {
		try {
			SSLSocket socket = createSSLSocket("127.0.0.1", 39999);
			// Socket socket = new Socket("127.0.0.1", 39999);
			Writer out = new OutputStreamWriter(socket.getOutputStream());
			out.write(data);
			out.flush();
			socket.close();
		} catch (UnknownHostException e) {
			// nichts zu machen, wenn der 127.0.0.1 nicht auflösen kann!
			e.printStackTrace();
		} catch (IOException e) {
			// nichts zu machen, wenn der 127.0.0.1 nicht auflösen kann!
			e.printStackTrace();
		}
	}
}

