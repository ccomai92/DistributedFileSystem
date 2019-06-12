import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*; // for scanner
import java.net.*; // inetaddr

import java.nio.file.*; // for File Class 

public class FileClient extends UnicastRemoteObject implements ClientInterface {

    // enum State that used to track the state of the cache files
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

        // initialize the local cache file path: /tmp/username.txt)
        this.cacheFile = "/tmp/" + System.getProperty("user.name") + ".txt";
        // System.out.println(System.getProperty("user.name"));

        // initialize the local cache file with the initialized path
        this.file = new File(this.cacheFile);

        // creating new file if it does not exist in the path
        if (!this.file.exists()) {
            this.file.createNewFile();
            this.file.setWritable(true, true); // chmod 600
        }

        // init serverObject for rmi calls
        this.serverObject = serverObject;

        // init the name of loal host
        this.localHost = localHost;

        // init current state (no file)
        this.currentState = State.INVALID;

        // no file in the cache, so no name
        this.fileName = "";
    }

    // interact with the user
    public void userPrompt() throws Exception {
        Scanner input = new Scanner(System.in);
        String fileName;
        String mode;

        while (true) {

            // Prompt user for inputs
            System.out.println("FileClient: Next file to open");

            // receive the name of requesting file
            System.out.print("File name: ");
            fileName = input.nextLine();

            // receiving the mode
            System.out.print("How (r/w): ");
            mode = input.nextLine();

            // input mode is neither "r" nor "w" re-prompt
            if (!mode.equals("r") && !mode.equals("w")) {
                System.out.println("Invalid input for \"How (r/w)\"");
                continue;
            }

            // if cannot open the file, or any error occurs, reprompt
            if (!this.openFile(fileName, mode)) {
                continue;
            }

            // open requested file on the Emacs according to the mode
            openEmacs();

            // complete the session after read/write operation
            completeSession();

            // prompt user for continuation
            System.out.println("Continue DFS? (y\n)");
            if (input.nextLine().toLowerCase().startsWith("y")) {
                return;
            }
        }
    }

    private boolean openFile(String fileName, String mode) {
        try {
            // Before file Replacement happens,

            // if local cache is not requested file,
            if (!this.fileName.equals(fileName)) {
                // files don't match, upload current file content to server if state is
                // writeowned
                if (this.currentState == State.WRITE_OWNED) {
                    FileContents currentContent = new FileContents(Files.readAllBytes(this.file.toPath()));
                    System.out.println("uplodaing");
                    boolean success;
                    success = this.serverObject.upload(this.localHost, this.fileName, currentContent);

                    System.out.println("upload success? " + success);

                    // set state to invalid so client can download desired file from server
                    this.currentState = State.INVALID;
                }
            }

            // check state of cache to determine if client downloads server file or not
            switch (this.currentState) {

            case INVALID:
                // download requested file no matter what
                if (!this.downloadRequestedFile(fileName, mode)) {
                    return false;
                }

                break;

            case READ_SHARED:

                // cache exists
                if (this.fileName.equals(fileName)) {

                    // requested writing mode
                    if (mode.equals("w")) {

                        // read shared state, re-download and make it writable
                        if (!this.downloadRequestedFile(fileName, mode)) {
                            return false;
                        }

                    }

                    // otherwise, do nothing just use cache

                } else { // cache does not match requested file

                    // should request the requested file
                    if (!this.downloadRequestedFile(fileName, mode)) {
                        return false;
                    }
                }

                break;

            case WRITE_OWNED:

                if (!this.fileName.equals(fileName)) {

                    // request different file
                    if (!this.downloadRequestedFile(fileName, mode)) {
                        return false;
                    }

                } // otherwise, do nothing just use same cache File

                break;

            default:
                System.out.println("Cannot open the requested file");
                // something happend,
                return false;

            }
        } catch (Exception e) {
            e.printStackTrace();
            // something happend
            return false;
        }
        return true;
    }

    private boolean downloadRequestedFile(String fileName, String mode) {
        try {

            // downalod specified file in mode
            FileContents result = this.serverObject.download(this.localHost, fileName, mode);

            // no file exists
            if (result == null) {
                System.out.println("The file does not exist in the server");
                return false;
            }

            // make the cache file writable so that client program can
            // write file content to the cache file
            this.file.setWritable(true, true); // chmod 600

            // write requested file contents into the cache file.
            FileOutputStream tempFileWriter = new FileOutputStream(this.file);
            tempFileWriter.write(result.get());
            tempFileWriter.close();

            // update the name of the cache file
            this.fileName = fileName;

            if (mode.equals("w")) {
                // already writable mode, do not have to change permission
                this.currentState = State.WRITE_OWNED; // write owned state
                this.accessMode = mode; // access mode = w
            } else {
                this.file.setReadOnly(); // chmod 400
                this.currentState = State.READ_SHARED; // read shared state
                this.accessMode = mode; // access mode = r
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private void openEmacs() {
        String[] command = new String[] { "emacs", this.cacheFile };
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
            // if serever notified to release the ownership,
            if (this.currentState == State.RELEASE_OWNERSHIP) {

                // upload changes to the server
                FileContents currentContent = new FileContents(Files.readAllBytes(this.file.toPath()));
                this.serverObject.upload(this.localHost, this.fileName, currentContent);

                // set state to read_shared
                this.currentState = State.READ_SHARED;
                this.file.setReadOnly(); // chmod 400

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // rmi call for the server to invalidate the cache
    public boolean invalidate() throws RemoteException {
        if (this.currentState == State.READ_SHARED) {
            this.currentState = State.INVALID;
            return true;
        }
        return false;

    }

    // rmi call for the server to request client to release
    // the write ownership
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

            // arg validation
            if (args.length == 2) {
                // argument[1] = port#
                port = Integer.parseInt(args[1]);
                if (port < 5001 || port > 65535)
                    throw new Exception();
            } else {
                throw new Exception();
            }

            // name of local host "cssmpi#"
            localHost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            System.err.println("usage: java Client serverIp port");
            System.exit(-1);
        }

        // argument[0] = server ip
        String serverIp = args[0];

        try {
            // Find server object
            ServerInterface serverObject = (ServerInterface) Naming
                    .lookup("rmi://" + serverIp + ":" + port + "/fileserver");

            // start rmi registry for client object
            startRegistry(port);
            FileClient client = new FileClient(serverObject, localHost);
            Naming.rebind("rmi://localhost:" + port + "/fileclient", client);

            // start the program
            client.userPrompt();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    // start rmi registry given by Dr. Fukuda
    private static void startRegistry(int port) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(port);
            registry.list();
        } catch (RemoteException e) {
            Registry registry = LocateRegistry.createRegistry(port);
        }
    }

}
