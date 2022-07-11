package server;

import java.io.*;
import java.util.Date;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import io.Dos;

public class Server {

        private boolean debug = false;

        private final SSLServerSocket server;
        private SSLContext sslContext;
	Dos dos = new Dos();
	
        Thread ich;
	PVA pva;
        
        public Server( int port , PVA pva ) throws IOException {
        
        	this.pva = pva;
                server = makeSSLSocket(port);
//                server.setNeedClientAuth(true);
        } 

        private SSLServerSocket makeSSLSocket(int port) {

                try {
                
                	String p = dos.readFile(System.getenv("HOME").trim()+"/.config/pva/kspass.txt").trim();
//                	log("p= |"+ p +"|");
  
  			// for some reason, toCharArray() didn't work, so we do it manually:
                
                        char[] password = new char[p.length()];
                        for(int i=0;i<p.length();i++) password[i] = p.charAt(i); 

//			log("|PASSWORD| = |"+ password[10] +"|");

                        sslContext = SSLContext.getInstance("TLS");

//                        TrustManager[] myTMs = new TrustManager [] { new MyX509TrustManager() };

                        KeyStore ks = KeyStore.getInstance("JKS");
                        FileInputStream fis = null;
                        try {
                                fis = new FileInputStream( System.getenv("HOME").trim()+"/.config/pva/.keystore");
                                ks.load(fis, password);
                        } finally {
                                if (fis != null) fis.close();
                        }

                        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
                        kmf.init(ks,password); 


                        sslContext.init(kmf.getKeyManagers(), 
  //                                      myTMs, 
  					null,
                                        new java.security.SecureRandom() );

                        ServerSocketFactory ssocketFactory = sslContext.getServerSocketFactory();

                        SSLServerSocket socket = (SSLServerSocket) ssocketFactory.createServerSocket(port);     
                        return socket;

                } catch (Exception e) {
                        System.out.println(e);
                        e.printStackTrace();
                }
                return null;

        }

        public void log(String x) {
                System.out.println((new Date())+": Server: "+x);
        }

        protected void startServing()  throws Exception {

                ich = Thread.currentThread();

//                System.out.println("serverprocessid "+ich);

                        while ( true ) {
                                if ( Thread.currentThread().isInterrupted() ) {
                                        log("Exiting");
                                        return;
                                }
//                                 log("ready");
                                try {
					
					Socket socket = server.accept();
			                socket.setKeepAlive(true);
        
//			                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//			                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        
//			                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

					ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
					DataInputStream in = new DataInputStream(socket.getInputStream());


					StringBuffer text = new StringBuffer(50000);
					String part = "";
											
		                        if ( ich.isInterrupted() ) {
		                                log( "process got terminated -> childprocess: "+ ich.isInterrupted() );
						break;
		                        }
		                        
		                        byte[] buffer = new byte[50000];
					int len = 0;
					do {

						len = in.read(buffer);
						if ( len > 0 ) {
							text.append( new String( buffer, 0, len) );
						}

					} while ( len > 0 );
						
	                       		// log("text="+ text.toString() );

					if ( !text.equals("") ) pva.handleInput(text.toString());
					
// 					in.close();
//					out.close();	

                                } catch ( IOException e ) {
                                        log(e.toString());
                                        e.printStackTrace();
                                }
                        }
        }


}


