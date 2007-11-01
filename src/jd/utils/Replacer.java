package jd.utils;

import java.util.Calendar;
import java.util.Vector;
import java.util.regex.Pattern;

import jd.controlling.JDController;
import jd.plugins.DownloadLink;
import jd.plugins.Plugin;

/**
 * Diese Klasse stellt Methoden zur Verfügung um in einen String mitPlatzhaltern
 * werte einzusetzen
 */
public class Replacer {

    public static String[][] KEYS = new String[][] { 
                new String[] { "LAST_FINISHED_PACKAGE.PASSWORD", "Last finished Package: Password" }, 
                new String[] { "LAST_FINISHED_PACKAGE.FILELIST", "Last finished Package: Filelist" }, 
                new String[] { "SYSTEM.DATE", "Current Date" },
                new String[] { "SYSTEM.TIME", "Current Time" }, 
                new String[] { "LAST_FINISHED_PACKAGE.PACKAGENAME",       "Last finished Package: Packagename" },
                new String[] { "LAST_FINISHED_PACKAGE.COMMENT", "Last finished Package: Comment" },
                new String[] { "LAST_FINISHED_PACKAGE.DOWNLOAD_DIRECTORY", "Last finished Package: Download Directory" },
                new String[] { "LAST_FINISHED_FILE.DOWNLOAD_PATH", "Last finished File: Filepath" }, 
                new String[] { "LAST_FINISHED_FILE.INFOSTRING", "Last finished File: Plugin given informationstring" },
                new String[] { "LAST_FINISHED_FILE.HOST", "Last finished File: Hoster" },
                new String[] { "LAST_FINISHED_FILE.NAME", "Last finished File: Filename" },
                new String[] { "LAST_FINISHED_FILE.FILESIZE", "Last finished File: Filesize" },
                new String[] { "LAST_FINISHED_FILE.AVAILABLE", "Last finished File: is Available (Yes,No)" },
                new String[] { "JD.LAST_CHANGE_DATE", "jDownloader: Compiledate" },
                new String[] { "JD.LAST_CHANGE_TIME", "jDownloader: Compiletime" },
                new String[] { "SYSTEM.IP", "Current IP Address" },
                new String[] { "SYSTEM.DATE", "Current Date" }, 
                new String[] { "SYSTEM.TIME", "Current Time" }, 
                new String[] { "JD.REVISION", "jDownloader: Revision/Version" }, 
                new String[] { "SYSTEM.JAVA_VERSION", "Used Java Version" },
                new String[] { "JD.HOME_DIR", "jDownloader: Homedirectory/Installdirectory" }


                                  };

    public static String insertVariables(String str) {
        for (int i = 0; i < KEYS.length; i++) {
       
            if(str.indexOf("%" + KEYS[i][0] + "%")>=0){
                Plugin.getLogger().finer("%" + KEYS[i][0] + "%"+" --> "+getReplacement(KEYS[i][0]));
            str = replace(str,"%" + KEYS[i][0] + "%", getReplacement(KEYS[i][0]));
            }
        }
        return str;
    }

    private static String getReplacement(String key) {
        JDController controller = JDUtilities.getController();
        DownloadLink dLink = controller.getLastFinishedDownloadLink();
        if(key.equalsIgnoreCase("LAST_FINISHED_PACKAGE.PASSWORD")){
            return dLink.getFilePackage().getPassword();
            
        }
        if(key.equalsIgnoreCase("LAST_FINISHED_PACKAGE.FILELIST")){
            Vector<DownloadLink> files = controller.getPackageFiles(dLink);
            return files.toString();
            
        }
        if(key.equalsIgnoreCase("LAST_FINISHED_PACKAGE.PACKAGENAME")){
            if(dLink.getFilePackage().getName()==null||dLink.getFilePackage().getName().length()==0)return  dLink.getName();
            return dLink.getFilePackage().getName();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_PACKAGE.COMMENT")){
            return dLink.getFilePackage().getComment();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_PACKAGE.DOWNLOAD_DIRECTORY")){
            return dLink.getFilePackage().getDownloadDirectory();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.DOWNLOAD_PATH")){
            return dLink.getFileOutput();
            
        }
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.INFOSTRING")){
            return dLink.getFileInfomationString();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.HOST")){
            return dLink.getHost();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.NAME")){
            return dLink.getName();
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.FILESIZE")){
            return dLink.getDownloadMax()+"";
            
        }
        
        if(key.equalsIgnoreCase("LAST_FINISHED_FILE.AVAILABLE")){
            return dLink.isAvailable()?"YES":"NO";
            
        }
        
     
        if(key.equalsIgnoreCase("JD.LAST_CHANGE_DATE")){
          
            return JDUtilities.getLastChangeDate();
            
        }
        if(key.equalsIgnoreCase("SYSTEM.IP")){
            
            return JDUtilities.getIPAddress();
            
        }
        
     if(key.equalsIgnoreCase("SYSTEM.DATE")){
         Calendar c = Calendar.getInstance();
            return JDUtilities.fillInteger(c.get(Calendar.DATE),2,"0")+"."+JDUtilities.fillInteger((c.get(Calendar.MONTH)+1),2,"0")+"."+c.get(Calendar.YEAR);
            
        }
     
     if(key.equalsIgnoreCase("SYSTEM.TIME")){
         Calendar c = Calendar.getInstance();
            return JDUtilities.fillInteger(c.get(Calendar.HOUR),2,"0")+":"+JDUtilities.fillInteger(c.get(Calendar.MINUTE),2,"0")+":"+JDUtilities.fillInteger(c.get(Calendar.SECOND),2,"0");
            
        }
  if(key.equalsIgnoreCase("JD.LAST_CHANGE_TIME")){
            
            return JDUtilities.getLastChangeTime();
            
        }
  if(key.equalsIgnoreCase("JD.REVISION")){
      
      return JDUtilities.getRevision();
      
  }
  
  if(key.equalsIgnoreCase("SYSTEM.JAVA_VERSION")){
      
      return JDUtilities.getJavaVersion().toString();
      
  }
  
  if(key.equalsIgnoreCase("JD.HOME_DIR")){
      
      return JDUtilities.getJDHomeDirectory().getAbsolutePath();
      
  }
  return "";

    }
    
    private static String replace(String in,String remove, String replace) {
        if (in==null || remove==null || remove.length()==0) return in;
        StringBuffer sb = new StringBuffer();
        int oldIndex = 0;
        int newIndex = 0;
        int remLength = remove.length();
        while ( (newIndex = in.indexOf(remove,oldIndex)) > -1) {
              
                sb.append(in.substring(oldIndex,newIndex));
                sb.append(replace);
              
                oldIndex = newIndex + remLength;
        }
        int inLength = in.length();
        
        if(oldIndex<inLength) sb.append(in.substring(oldIndex,inLength));
        return sb.toString();
        }
}