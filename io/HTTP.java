package io;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.net.Socket;


public class HTTP {
	
	public static String apihost = "127.0.0.1";
	public static String apiport = "80";
	
	
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

                try {
                        do {

//                                System.out.println("readPage: pre read " + dis.available() );

                                if ( dis.available() > 0 || firstread ) {

					got = dis.read(buffer,offset,len-offset);
//                                      System.out.println("readPage: read = "+got);
                                        offset += got;

                                        firstread = false;

                                        if ( offset >= len ) {

                                                result += new String(buffer);
                                                offset = 0;
                                        }
                                } else break;

                        } while ( got > 0);

//                      System.out.println("readPage: finish reading normally");

                } catch (Exception e) {
                        System.out.println("Timeout getPage()");
                        System.out.println( e.toString() );
                }

//              log("readPage: finished reading ");

                result += new String(buffer);
                if ( !header ) {
                        result = result.substring( result.indexOf("\r\n\r\n")+4 );
                }

                // datenstrom schliessen

                dis.close();

                return result;
        }
	
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
 
		    socket.setSoTimeout(10000);
		    socket.setReceiveBufferSize(200000);

			result = readPage(socket);
			
			return result;
			
		} catch (Exception e) {
			System.out.println( e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
			return result;
		}

			
			
	}

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
 
		    socket.setSoTimeout(2000);
		    socket.setReceiveBufferSize(200000);

			result = readPage(socket);
			
			return result;
			
		} catch (Exception e) {
			System.out.println( e.toString()+"\n"+ e.getCause() +"\n" +e.getMessage() );
			return result;
		}
	}

}
