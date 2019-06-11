import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*; // for scanner
import java.net.*; // inetaddr

import java.nio.file.*;


public class FileClient extends UnicastRemoteObject implements ClientInterface {

    private enum State { 
        INVALID, READ_SHARED, WRITE_OWNED, RELEASE_OWNERSHIP; 
    }

    private File file; 
    private ServerInterface serverObject;
    private String localHost;
    private String cacheFile;
    
    private String fileName; 
    private String accessMode; 
    private State currentState; 

    public FileClient(ServerInterface serverObject, String localHost) throws Exception {
        this.cacheFile = "/tmp/" + System.getProperty("user.name") + ".txt";
        this.file = new File(this.cacheFile);
        if (!this.file.exists()) {
			this.file.createNewFile();
			this.file.setReadable(true);
		}

        System.out.println(System.getProperty("user.name"));
        this.serverObject = serverObject;
        this.localHost = localHost;
        this.currentState = State.INVALID;
        this.fileName = "";
    }

    public void userPrompt() throws Exception {
        Scanner input = new Scanner(System.in);
        String fileName; 
        String mode; 
        while (true) {

            // Prompt user for inputs 
            System.out.println("FileClient: Next file to open");
            System.out.print("File name: ");
            fileName = input.nextLine(); 
            System.out.print("How (r/w): ");
            mode = input.nextLine();

            // input mode is neither "r" nor "w" re-prompt
            if (!mode.equals("r") && !mode.equals("w")) {
                System.out.println("Invalid input for \"How (r/w)\"");
                continue;
            }

            if (!this.openFile(fileName, mode)) {
                continue;
            }

            openEmacs();
            completeSession();
        }
    }

    private boolean openFile(String fileName, String mode) {
        try {
            // Before file Replacement happens, 
            if(!this.fileName.equals(fileName)) {
                //files don't match, upload current file to server if state is writeowned
                if(this.currentState == State.WRITE_OWNED) {
                    FileContents currentContent = new FileContents(Files.readAllBytes(this.file.toPath()));
                    this.serverObject.upload(this.localHost, this.fileName, currentContent);
                    this.currentState = State.INVALID;		//set state to invalid so client can download desired file from server
                }
            }
            
            //check state of cache to determine if client downloads server file or not
            switch(this.currentState) {

                case INVALID:
                    //download requested file no matter what
                    this.downloadRequestedFile(fileName, mode);

                    break; 

                case READ_SHARED:
                    if (this.fileName.equals(fileName)) { 
                        
                        // file name is same use cache 
                        if (mode.equals("w")) {

                            // read shared state, re-download and make it writable
                            this.downloadRequestedFile(fileName, mode);

                        } 

                        // otherwise, do nothing just use cache

                    } else {

                        // should request the requested file 
                        this.downloadRequestedFile(fileName, mode);

                    }

                    break;	//do nothing
                
                case WRITE_OWNED:

                    if (!this.fileName.equals(fileName)) { 
                        
                        // request different file  
                        this.downloadRequestedFile(fileName, mode);

                    } // otherwise, do nothing just use same cache File
                    
                    break;
                    
                default:
                    return false;

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean downloadRequestedFile(String fileName, String mode) {
        try {
            FileContents result = this.serverObject.download(this.localHost, fileName, mode);
                
            // no file exists             
            if (result == null ) {
                System.out.println("The file does not exist");
                return false;
            }

	    this.file.setWritable(true, true);            // chmod 600
    
            // write requested file contents into the cache file.
            FileOutputStream tempFileWriter =  new FileOutputStream(this.file);
            tempFileWriter.write(result.get());
            tempFileWriter.close();

            this.fileName = fileName; 
            if (mode.equals("w")) {
                // already writable mode, do not have to change permission
		this.currentState = State.WRITE_OWNED;      // write owned state
                this.accessMode = mode;                      // access mode = w
            } else {
                this.file.setReadOnly();                    // chmod 400
                this.currentState = State.READ_SHARED;      // read shared state
                this.accessMode = mode;                      // access mode = r
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void openEmacs() {
        String[] command = new String[] {"emacs", this.cacheFile};
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void completeSession() {
        try {
            if (this.currentState == State.RELEASE_OWNERSHIP) {
                FileContents currentContent = new FileContents(Files.readAllBytes(this.file.toPath()));
                this.serverObject.upload(this.localHost, this.fileName, currentContent);
                this.currentState = State.READ_SHARED;		//set state to invalid so client can download desired file from server
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public boolean invalidate() throws RemoteException {
        if (this.currentState == State.READ_SHARED) {
            this.currentState = State.INVALID;
            return true;
        }
        return false;
        
    }

    public boolean writeback() throws RemoteException {
        if (this.currentState == State.WRITE_OWNED) {
            this.currentState = State.RELEASE_OWNERSHIP;
            return true;
        }
        return false;
    }
    
    public static void main(String[] args) {
        
        // Checking arguments 
        int port = 0;
        String localHost = "";
        try {
            if (args.length == 2) {
                // argument[1] = port#
                port = Integer.parseInt(args[1]);
                if (port < 5001 || port > 65535)
                    throw new Exception();
            } else {
                throw new Exception();
            }
            localHost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            System.err.println("usage: java Client serverIp port");
            System.exit(-1);
        }

        // argument[0] = server ip
        String serverIp = args[0];
        

        try {
            // Find server object 
            ServerInterface serverObject = (ServerInterface)
                 Naming.lookup("rmi://" + serverIp + ":" + port + "/fileserver");

            // start rmi for client object
            startRegistry(port);
            FileClient client = new FileClient(serverObject, localHost);
            Naming.rebind( "rmi://localhost:" + port + "/fileclient", client);
            
            // start the program
            client.userPrompt();

            
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    private static void startRegistry(int port) throws RemoteException {
		try {
			Registry registry = LocateRegistry.getRegistry(port);
			registry.list();
		} catch (RemoteException e) {
			Registry registry = LocateRegistry.createRegistry(port);
		}
	}

}
