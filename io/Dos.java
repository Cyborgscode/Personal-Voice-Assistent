
package io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import hash.StringHash;


public class Dos {

        private String zwischen(String buffer,String vor,String nach) {

                int i1 = buffer.indexOf(vor)+vor.length();
                int i2 = buffer.indexOf(nach,i1);

                if ( i1 >= 0 && i2 > i1 ) {

                        return buffer.substring(i1,i2);

                }

                return null;
        }

       public boolean exec(String s) {
                   try {
                          Process p = java.lang.Runtime.getRuntime().exec(s);
                          if ( p != null ) {
	                          p.waitFor();
	                          p.destroy();
	                  
                         	return true;
                         } else return false;
                   } catch (Exception e) {
                          System.out.println("Command Execution Error for : "+s+
                        		  "\n"+e.toString());
                          return false;
                   }
       }

       public boolean createRUNFile(String id) {
    	   
    	   	if ( fileExists(id+".run" ) ) return false;
    	   	return writeFile(id+".run","processrunning");
       }
       
       public void deleteRUNFile(String id) {
    	   
    	   if ( fileExists(id+".run") ) {
    		   File temp = new File( id+".run" );
    		   temp.delete();
    	   }
       }
       
        public String readPipe(String s) {
                return readPipe(s,null);
        }

        public String readPipe(String s,boolean regExOverride) {
                return readPipe(s,null,regExOverride);
        }

        public String readPipe(String s,String stdin) {
                return readPipe(s,stdin,false);
        }


        public String readPipe(String s,String stdin, boolean regExOverride) {

                String output = "";

                Process process;
                try {
                        Runtime task = java.lang.Runtime.getRuntime();
                                      
//                        System.out.println( "s="+s );
                                              
                        if ( s.indexOf(" ")>0)  {

                                StringHash params = new StringHash();
                                int c = 0;
                                while ( s.indexOf("\"")>=0 ) {
  //                                    System.out.println( "in="+s ) ;
                                        String arg = zwischen( s , "\"", "\"" );
                                        params.put("<ARG"+c+">", arg );
                                        if ( ! regExOverride ) {
                                                s = s.replaceAll( "\""+arg+"\"", "<ARG"+c+">" );
                                        } else  s = s.replace( "\""+arg+"\"", "<ARG"+c+">" );
//                                      System.out.println( "out="+s ) ;
                                        c++;
                                }
                                while ( s.indexOf("'")>=0 && s.indexOf("'", s.indexOf("'")+1)>s.indexOf("'") ) {
//	                                System.out.println( "in="+s ) ;
                                        String arg = zwischen( s , "'", "'" );
                                        params.put("<ARG"+c+">", arg );
                                        if ( ! regExOverride ) {
                                                s = s.replaceAll( "'"+arg+"'", "<ARG"+c+">" );
                                        } else  s = s.replace( "'"+arg+"'", "<ARG"+c+">" );
//                                      System.out.println( "out="+s ) ;
                                        c++;
                                }

                                String[] cmds = s.split(" ");
                                for(int i=0;i<cmds.length;i++) {
                                	if ( cmds[i].trim().isEmpty() ) cmds[i]="--new-window";
                                        if ( ! regExOverride ) {
                                                for(int a=0;a<=c;a++) cmds[i] = cmds[i]. replaceAll( "<ARG"+a+">", params.get( "<ARG"+a+">" ) ).replaceAll("xx2xx","'");
                                        } else  for(int a=0;a<=c;a++) cmds[i] = cmds[i]. replace( "<ARG"+a+">", params.get( "<ARG"+a+">" ) ).replaceAll("xx2xx","'");

                                      // System.out.println(i +". => "+cmds[i] );
                                }
                                process = task.exec(cmds);
                        } else process = task.exec(s);

                        if ( stdin != null ) {

                                BufferedWriter out = new BufferedWriter( new OutputStreamWriter(process.getOutputStream()) );
                                out.write(stdin+"\n");
                                out.flush();
                                out.close();
                        }

                        String lines ="";
                        BufferedReader ins = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        while ((lines = ins.readLine())!=null) {
                                output += lines+"\n";
                        }

                        ins = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        while ((lines = ins.readLine())!=null) {
                                output += lines+"\n";
                        }

                        while ((lines = ins.readLine())!=null) {
                                output += lines+"\n";
                        }

                        process.destroy();
                } catch (Exception e) {
	                e.printStackTrace();
                        return "Exception:"+ e.toString();

                }
                return output;
        }

        public boolean writeFile(String name, byte[] inhalt) {

            if ( name.indexOf("..") >=0 ) {
                    System.out.println("Directory Traversel Attack detected : "+name);
                    return false;
            }

            try {

                    File temp = new File(name);
                    if ( temp != null ) {
                            FileOutputStream out = new FileOutputStream( temp );
                            if ( out != null ) {
                                    out.write( inhalt );
                                    out.close();
                                    return true;
                            }
                    }
            } catch (Exception e) {

                    System.err.println( "File "+name+" konnte nicht angelegt werden!" );
                    System.err.println( e.toString() );
                    e.printStackTrace();

            }
            return false;

    }

    public boolean writeFile(String name,String inhalt) {

            return writeFile(name, inhalt.getBytes() );
    }

    public String readFile(String filename) {

            try{
                    File file = new File(filename);
                    if ( file != null ) {
	                    DataInputStream in = new DataInputStream(new FileInputStream(filename));
	                    byte[] b = new byte[(int) file.length()];
	                    in.read(b);
	                    in.close();
	                    return new String(b);
	             } else System.out.println("FileNotFoundError: " + filename);
            } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
            }

            return "";
    }
    
    @SuppressWarnings("unused")
	public boolean fileExists(String filename) {
        File file = new File(filename);
        if ( file!= null) return file.exists();
        return false;
    }

    public boolean isLink(String filename) {
        File file = new File(filename);
        return isLink(file);
    }

    public boolean isLink(File file) {
        try {
                if (!file.exists()) return true;

                String cnnpath = file.getCanonicalPath();
                String abspath = file.getAbsolutePath();
                if ( ! abspath.startsWith( "/opt/root" ) ) {
                        abspath = "/opt/root" + abspath;
                }

//             System.out.println("isLink: "+file+"\n c:"+ cnnpath +"\n a:"+ abspath );

                return !abspath.equals(cnnpath);
        } catch(IOException ex) {
              System.err.println(ex);
              return true;
        }
    }


    public byte[] readFileRaw(String filename) {

        try{
                File file = new File(filename);
                DataInputStream in = new DataInputStream(new FileInputStream(filename));
                byte[] b = new byte[(int) file.length()];
                in.read(b);
                in.close();
                return b;
        } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
        }

        return new byte[0];
    }

}
