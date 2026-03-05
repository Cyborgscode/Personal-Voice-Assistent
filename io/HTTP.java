package io;

import java.io.DataInputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.*;
import java.security.SecureRandom;

import java.io.IOException;

import java.util.Date;

public class HTTP {

	public static String apihost = "127.0.0.1";
	public static String apiport = "80";
	public static int timeout = 15000;

	public static String toUnicode(String data) {
	
	    StringBuilder b = new StringBuilder();

	    for (char c : data.toCharArray()) {
        	if (c >= 128)
	            b.append("\\u").append(String.format("%04X", (int) c));
	        else
	            b.append(c);
	    }

	    return b.toString();
	}
	
	public static String fromUnicode(String data) {
		if (data == null || !data.contains("\\u")) return data;

		StringBuilder b = new StringBuilder();
		int len = data.length();

		for (int i = 0; i < len; i++) {
			char c = data.charAt(i);

			// Prüfe auf Start eines Unicode-Escapes
			if (c == '\\' && i + 5 < len && data.charAt(i + 1) == 'u') {
				try {
					// Extrahiere die 4 Hex-Stellen nach BACKSLASH + u
					String hex = data.substring(i + 2, i + 6);
					int code = Integer.parseInt(hex, 16);
					b.append((char) code);
					i += 5; // Springe über den verarbeiteten Block
				} catch (NumberFormatException e) {
					// Falls kein valider Hex-Code folgt, Zeichen normal übernehmen
					b.append(c);
				}
			} else {
				b.append(c);
			}
		}

		return b.toString();
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

	private static SSLSocket createSSLSocketDefault(String host,int port) {
	
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
		    	sslContext.init(null, trustAllCerts, new SecureRandom());
    			
			SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();

			SSLSocket socket = (SSLSocket) factory.createSocket(host, port );
			socket.setEnabledProtocols(new String[] { "TLSv1.2","TLSv1.3" });
				
			return socket;
		} catch (Exception e) {
			// Egal welche Exception kommt, ist das gleiche Ergebnis
			return null;
		}
	}

	
	public static String getPageSSL(String host,String link) {
		
		return getPageSSL(host, link, 443, "" );
	}
	
	public static String getPageSSL(String host,String link,String header) {
		
		return getPageSSL(host, link, 443 , header);
	}
	
	static void log(String x) {
		
		System.out.println( (new Date())+ " : "+ x);
		return;
	}

	static private String readPage(Socket socket) throws IOException {
		return readPage(socket,false);
	}

	static private String readPage(Socket socket, boolean header) throws IOException {
	
		DataInputStream dis = new DataInputStream(socket.getInputStream() );            
			
		String result = "";

		// Daten einlesen und ausgeben

		int len = 100000;
		byte[] buffer = new byte[len];
		int offset = 0;
		int got = 0;
		boolean firstread = true;
		Date date1 = new Date();


		try { 
			do {
					
//				log("readPage: pre read" + dis.available() );
	
				if ( dis.available() > 0 || firstread ) {
					got = dis.read(buffer,offset,len-offset);
//					log("readPage: read = "+got);
					offset += got;
						
					firstread = false;
					
					if ( offset == len ) {
						
						result += new String(buffer);
						offset = 0;
					}
				} else break;
	
			} while ( got > 0 );
									
//			log("readPage: finish reading normally");
										
		} catch (Exception e) {
			Date date2 = new Date();
                        log("Timeout getPage() nach "+  ((date2.getTime()-date1.getTime())/1000) +" sekunden");
                        log( e.toString() );
			log( e.toString() );
		}
				
//		log("readPage: finished reading ");
							
		result += new String(buffer);
		if ( !header ) {
			result = result.substring( result.indexOf("\r\n\r\n")+4 );
		}
				
		// datenstrom schliessen

		dis.close();
		
		return result;
	}

	public static String getPageSSL(String host,String link,int port,String header ) {
	
		return getPageSSL( host, link, port, header, false);
	}

	public static String getPageSSL(String host,String link,int port,String header, boolean returnheaders ) {
		String result ="";
		 
		try {
		
//			log("createSSLSocket");
		    
		    SSLSocket socket = createSSLSocket(host, port);
		    if ( socket != null ) {
			
			    socket.setSoTimeout(timeout);
			    socket.setReceiveBufferSize(200000);
			
//	 		log("getOutputStream");
			
			    Writer out = new OutputStreamWriter(socket.getOutputStream());

//			log("Start sending Data");

			    out.write("GET "+link+" HTTP/1.1\r\n");
			    if ( port == 443 ) {
				    out.write("Host: "+host+"\r\n");
	    			    // log( "GET "+link+" HTTP/1.1\r\n" + "Host: "+host+"\r\n"+ header+"\r\n" );
			    } else {
			    	    out.write("Host: "+host+":"+port+"\r\n");
			    	    //log( "GET "+link+" HTTP/1.1\r\n" + "Host: "+host+":"+port+"\r\n"+ header+"\r\n" );
			    }
	    		    out.write("User-Agent: PVA/latest / PVA-WikiPlugin/1.0 (https://github.com/Cyborgscode/Personal-Voice-Assistent;)\r\n");
			    out.write("Accept-Encoding: identity\r\n");
			    if ( !header.contains("Accept:") ) 
				    out.write("Accept: */*\r\n");
			    out.write("Connection: keep-alive\r\n");
			    out.write("Content-Language: de-DE\r\n"); 
			    
			    if ( !header.isEmpty() ) 
			    	out.write( header+ "\r\n");
			    out.write("\r\n");
			    out.flush();
 
//				log("finish sending data");

			    result = readPage(socket,returnheaders); 
			
				socket.close();
			
//			log( "http.Got: "+ result );
			
			return result;
		     } 

		     return "";
			
		} catch (Exception e) {
				e.printStackTrace();
				log( "http.exception: "+ e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
				return e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() ;
		}
	}
	
	public static String getPage(String host,String link) {
		String result ="";
		 
		try {
		    Socket socket = new Socket( host, 80 );

		    Writer out = new OutputStreamWriter(socket.getOutputStream());

		    // log( "GET "+link+" HTTP/1.1\r\n" + "Host: "+host+"\r\n" );
		    
		    out.write("GET "+link+" HTTP/1.1\r\n");
		    out.write("Host: "+host+"\r\n");
		    out.write("\r\n");
		    out.flush();
 
		    socket.setSoTimeout(timeout);
		    socket.setReceiveBufferSize(200000);
		    			
		    result = readPage(socket);
		    socket.close();			
			// log( "http.Got: "+ result );
			
			return result;
			
		} catch (Exception e) {
				e.printStackTrace();
				log( "http.exception: "+ e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
				return e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() ;
		}
	}
	
	// backwards compat for pva

	public static String get(String uri) {
		String result ="";
		try {

		    Socket socket = new Socket(apihost, Integer.parseInt( apiport ));

		    Writer out = new OutputStreamWriter(socket.getOutputStream());

		    out.write("GET "+uri+" HTTP/1.1\r\n");
		    out.write("Host: "+apihost+":"+apiport+"\r\n");
		    out.write("User-Agent: PVA/latest\r\n");
		    out.write("Accept-Encoding: gzip, deflate\r\n");
		    out.write("Accept: */*\r\n");
		    out.write("Connection: keep-alive\r\n");
		    out.write("Content-Language: de-DE\r\n"); 
		    out.write("\r\n");
		    out.flush();
 
		    socket.setSoTimeout(timeout);
		    socket.setReceiveBufferSize(200000);

			result = readPage(socket);
			socket.close();
			return result;
			
		} catch (Exception e) {
			System.out.println( e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
			return result;
		}
	}

	// backwards compat for pva


	public static String post(String uri,String data) {
		String result ="";
		
		data = toUnicode(data);
		
		try {

		    Socket socket = new Socket(apihost, Integer.parseInt( apiport ));

		    Writer out = new OutputStreamWriter(socket.getOutputStream());

		    out.write("POST "+uri+" HTTP/1.1\r\n");
		    out.write("Host: "+apihost+":"+apiport+"\r\n");
		    out.write("User-Agent: PVA/latest\r\n");
		    out.write("Accept-Encoding: gzip, deflate\r\n");
		    out.write("Accept: */*\r\n");
		    out.write("Connection: keep-alive\r\n");
		    out.write("Content-Type: application/json; charset=utf-8\r\n");
		    out.write("Content-Length: "+ Integer.toString( data.getBytes().length ) +"\r\n");
		    out.write("Content-Language: de-DE\r\n");  
		    
		    out.write("\r\n");
		    out.write(data);
		    out.flush();
 
		    socket.setSoTimeout(timeout);
		    socket.setReceiveBufferSize(200000);
	
			result = readPage(socket);
			socket.close();
			return result;
			
		} catch (Exception e) {
			System.out.println( e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
			return result;
		}
	}

	public static String post(String host,String url, String data) {	

		return post(host,url,data,80);
		
	} 
		
	public static String post(String host,String url, String data, int port) {
		String result ="";
		 
		try {
			
		    Socket socket = new Socket( host, port );

		    Writer out = new OutputStreamWriter(socket.getOutputStream());

		    // log( "GET "+link+" HTTP/1.0\r\n" + "Host: "+host+"\r\n" );
		    
		    out.write("POST "+url+" HTTP/1.0\r\n");
		    out.write("Host: "+host+"\r\n");
		    out.write("Content-Type: application/x-www-form-urlencoded\r\n");
		    out.write("Content-Length: "+data.length()+"\r\n");
		    out.write("\r\n");
		    out.write( data );
		    out.flush();
 
		    socket.setSoTimeout(timeout);
		    socket.setReceiveBufferSize(200000);
		    
		    result = readPage(socket);
		    socket.close();
			// log( "http.Got: "+ result );
			
			return result;
			
		} catch (Exception e) {
				e.printStackTrace();
				log( "http.exception: "+ e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
				return e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() ;
		}
	}

	
	public static String postSSL(String host,String url, String data) {	

		return postSSL(host,url,data,443,"");
		
	} 
		
	public static String postSSL(String host,String url, String data, String header) {	

		return postSSL(host,url,data,443, header);
		
	} 
	public static String postSSL(String host,String url, String data, int port) {	

		return postSSL(host,url,data, port, "");
		
	} 
	public static String postSSL(String host,String url, String data, int port, String header) {
		String result ="";
		try {
			
			SSLSocket socket = createSSLSocket(host,port);
			if ( socket != null ) {

				socket.setSoTimeout(timeout);
				socket.setReceiveBufferSize(200000);
				
				Writer out = new OutputStreamWriter(socket.getOutputStream());

				// log( "POST "+ url +" HTTP/1.1\r\n" + "Host: "+host+"\r\n"+ header+"\r\n"+ "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "+data.length()+"\r\n");
		    
				out.write("POST "+url+" HTTP/1.1\r\n");
				if ( port == 443 ) {
					out.write("Host: "+host+"\r\n");
				} else  out.write("Host: "+host+":"+port+"\r\n");
				
				// log(header+"\r\n");
				
				if ( !header.isEmpty() ) 
					out.write(header+"\r\n");
					
				if ( !header.contains("Content-Type:") )
					out.write("Content-Type: application/x-www-form-urlencoded\r\n");
				out.write("Content-Length: "+data.length()+"\r\n");
	
				out.write("\r\n");
				out.write( data );
				out.flush();
		 
				result = readPage(socket);						
				socket.close();
//				log( "http.postSSL: "+ result );
			
				return result;
				
			} else return "";
			
		} catch (Exception e) {
				e.printStackTrace();
				log( "http.exception: "+ e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
				return e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() ;
		}
	}
	public static String putSSL(String host,String url, String data, String header) {
		return putSSL(host, url, data, 443, header);
	}
	
	public static String putSSL(String host,String url, String data, int port, String header) {
		String result ="";
		try {
			SSLSocket socket = createSSLSocket(host,port);
			if ( socket != null ) {

				socket.setSoTimeout(timeout);
				socket.setReceiveBufferSize(200000);
				
				Writer out = new OutputStreamWriter(socket.getOutputStream());

				// log( "PUT "+ url +" HTTP/1.1\r\n" + "Host: "+host+"\r\n"+ header+"\r\n"+ "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: "+data.length()+"\r\n\r\n"+data);
		    
				out.write("PUT "+url+" HTTP/1.1\r\n");
				if ( port == 443 ) {
					out.write("Host: "+host+"\r\n");
				} else  out.write("Host: "+host+":"+port+"\r\n");
				
				// log(header+"\r\n");
				
				if ( !header.isEmpty() ) 
					out.write(header+"\r\n");
					
				if (!header.toLowerCase().contains("content-type:")) {
					out.write("Content-Type: application/json; charset=utf-8\r\n");
				}
					
//				out.write("Content-Length: "+ data.length()+"\r\n");
				out.write("Content-Length: "+ data.getBytes("UTF-8").length+"\r\n");
	
				out.write("\r\n");
				out.write( data );
				out.flush();
		 
				result = readPage(socket);						
				socket.close();				
//				log( "http.postSSL: "+ result );
			
				return result;
				
			} else return "";
			
		} catch (Exception e) {
				e.printStackTrace();
				log( "http.exception: "+ e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
				return e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() ;
		}
	}
	
}

