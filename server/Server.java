package server;

import java.io.*;
import java.util.Date;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.*;
import javax.net.ssl.*;
import java.net.InetAddress;
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
//                server.setNeedClientAuth(true); // in case you wanne build a trustchain for your clients, you need special signed clientcerts from your servercert && a working trustmanager.
        } 

        public Server( PVA pva ) throws IOException {
        
        	this.pva = pva;
                server = makeSSLSocket(pva);
        } 

	private ServerSocketFactory getFactory() {
	
		try {
		       	String p = dos.readFile(System.getenv("HOME").trim()+"/.config/pva/kspass.txt").trim();
  
  			// for some reason, toCharArray() didn't work, so we do it manually:
                
                        char[] password = new char[p.length()];
                        for(int i=0;i<p.length();i++) password[i] = p.charAt(i); 

                        sslContext = SSLContext.getInstance("TLS");

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
  					null,
                                        new java.security.SecureRandom() );

			return sslContext.getServerSocketFactory();
                       
		} catch (Exception e) {
			// nothing we can do about this
			return null;
		}
	}
	
        private SSLServerSocket makeSSLSocket(int port) {

                try {
                	ServerSocketFactory ssocketFactory = getFactory();
                        SSLServerSocket socket = (SSLServerSocket) ssocketFactory.createServerSocket(port);     
                        return socket;

                } catch (Exception e) {
                        System.out.println(e);
                        e.printStackTrace();
                }
                return null;
        }

        private SSLServerSocket makeSSLSocket(PVA pva) {

                try {
                	ServerSocketFactory ssocketFactory = getFactory();
                	
                	int port = Integer.parseInt( pva.config.get("network","port") );
                	int backlog = 200;
                	
                        SSLServerSocket socket = (SSLServerSocket) ssocketFactory.createServerSocket(port, backlog, InetAddress.getByName( pva.config.get("network","bindaddress") ) );
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

                        while ( true ) {
                                if ( Thread.currentThread().isInterrupted() ) {
                                        log("Exiting");
                                        return;
                                }
                                try {
					
					Socket socket = server.accept();
			                socket.setKeepAlive(true);
        
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
						
					try {
							
						if ( !socket.isClosed() ) socket.close();
						
					} catch (Exception e) { 
						// Nothing to do
					}
						
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

