import java.io.*;
import java.net.PortUnreachableException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

/**
 * FileServer for css434 final project
 *
 * @auhtor Haram Kwon, Kris Kwon
 * @version 0.1
 */
public class FileServer extends UnicastRemoteObject implements ServerInterface {
    private Vector<File> files = null;
    private int port = 0;


    /**
     * @param port
     * @throws RemoteException
     */
    public FileServer(int port) throws RemoteException {
        this.port = port;
        this.files = new Vector<>();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                String[] message = new String[1];
                message[0] = "new String[0] = usage: java Server port";
                throw new IllegalArgumentException(
                        "new String[0] = usage: java Server port");
            }

            // Now get the port number
            int port = Integer.parseInt(args[0]);

            if (port < 5001 || port > 65535) {
                throw new PortUnreachableException(
                        "port range should be 5001 ~ 65535");
            }

            startRegistry(port);
            FileServer serverObject = new FileServer(port);
            Naming.rebind(
                    "rmi://localhost:" + port + "/fileserver", serverObject);
            System.out.println("Server ready.");

        } catch (PortUnreachableException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    /**
     * Start the rmi registry
     *
     * @param port the port number for the server
     * @throws RemoteException rmiregistry start failed
     */
    private static void startRegistry(int port) throws RemoteException {
        try {
            Registry registry = LocateRegistry.getRegistry(port);
            registry.list();
        } catch (RemoteException e) {
            Registry registry = LocateRegistry.createRegistry(port);
        }
    }

    /**
     * Client invoke this function to download the file.
     *
     * @param client the client it is downloading the file
     * @param filename the filename client requested
     * @param mode r/w (read/write)
     * @return the content of the file
     * @throws RemoteException
     */
    public FileContents download(String client, String filename, String mode)
            throws RemoteException {

        // todo: filename error checking should be done here

        File file = null;
        byte[] fileContent = null;

        // Scan the cached file list.
        for (File f : files) {
            if (f.filename.equals(filename)) {
                file = f;
            }
        }

        // file not found, and add file to the list
        if (file == null) {
            file = new File(filename, this.port);
            this.files.add(file);
        }

        // todo:
        return file.download(client, mode);
    }

    /**
     * Client invoke this funtion to upload the file to the server.
     *
     * @param client
     * @param filename
     * @param contents
     * @return
     * @throws RemoteException
     */
    public boolean upload(String client, String filename, FileContents contents)
            throws RemoteException {
        System.out.println("upload invoked");
        File file = null;

        // file the file to upload
        for (File f : files) {
            if (filename.equals(f.filename)) {
                System.out.println(
                        "Trying to find " + filename + " now: " + f.filename);
                file = f;
                break;
            }
        }

        System.out.println("escaped for loop");
        System.out.println("filename: " + file.filename);

        return (file != null) && file.upload(client, contents);
    }

    // States of the files
    enum State {
        NOT_SHARED, READ_SHARED, WRITE_SHARED, OWNERSHIP_CHANGE
    }

    /**
     *
     */
    private class File {

        private State state;
        private String filename;
        private byte[] bytes = null;
        private Vector<String> readers = null;
        private String owner = null;
        private int port = 0;

        // Two monitors for handling the clients write access.
        private Object monitor1 = null;
        private Object monitor2 = null;

        /**
         * File constructor for each file.
         *
         * @param filename name of the file.
         * @param port part for rmi registry.
         */
        public File(String filename, int port) {
            this.state = State.NOT_SHARED;
            this.filename = filename;
            readers = new Vector<String>();
            owner = null;
            this.port = port;
            monitor1 = new Object();
            monitor2 = new Object();

            // read file contents from the local disk
            bytes = readFile();
        }

        /**
         * Read file from the file strogae and
         * gives back the contents of the file in byte[] form.
         *
         * @return contents of the file.
         */
        private byte[] readFile() {
            byte[] bytes = null;
            try {
                FileInputStream file = new FileInputStream(filename);
                bytes = new byte[file.available()];
                file.read(bytes);
                file.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            System.out.println("file read from " + filename + ": " +
                    bytes.length + " bytes");
            return bytes;
        }

        /**
         * write the cached byte[] (or filecontents) to the local storage.
         *
         * @return
         */
        private boolean writeFile() {
            try {
                FileOutputStream file = new FileOutputStream(filename);
                file.write(bytes);
                file.flush();
                file.close();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        /**
         * remove reader if exist
         */
        private void removeReader(String client) {
            readers.remove(client);
        }

        /**
         * Gives the file contents to the client.
         * (helperf function for FileServer.download)
         *
         * @param client the clinet
         * @param mode r/w (read/write)
         * @return
         */
        public FileContents download(String client, String mode) {
            try {
                if (mode.equals("r")) {
                    System.out.println("read mode");
                } else if (mode.equals(("w"))) {
                    System.out.println("write mode");
                } else {
                    System.err.println("mode error with " + mode);
                    return null;
                }

                // Thread control for OWNERSHIP change (invoke monitor)
                synchronized (monitor1) {
                    if (state == State.OWNERSHIP_CHANGE) {
                        // todo: delete later
                        System.out.println("wait state for ownershiop change");
                        monitor1.wait();
                        System.out.println("Wait state released");
                    }
                }

                // Save the previous state
                State previousState = state;

                // do the file action according to file state.
                switch (state) {
                    case NOT_SHARED:
                        System.out.println("download state not shared");
                        if (mode.equals("r")) {
                            state = State.READ_SHARED;
                            readers.add(client);
                        } else if (mode.equals("w")) {

                            state = State.WRITE_SHARED;
                            if (owner != null)
                                throw new SyncFailedException(
                                                "Critical error. " +
                                                        "previous owner " +
                                                        "exist in " +
                                                        "NOT_SHARED file");
                            else
                                owner = client;
                        }
                        break;
                    case READ_SHARED:
                        System.out.println("download state read shared");
                        removeReader(client);
                        if (mode.equals("r"))
                            readers.add(client);
                        else if (mode.equals("w")) {
                            state = State.WRITE_SHARED;
                            if (owner != null)
                                throw new SyncFailedException(
                                        "Critical error. previous owner " +
                                                "exist in READ_SHARED file");
                            else
                                owner = client;
                        }

                        break;
                    case WRITE_SHARED:
                        System.out.println("download state write shared");
                        removeReader(client);
                        if (mode.equals("r"))
                            readers.add(client);
                        else if (mode.equals("w")) {
                            state = State.OWNERSHIP_CHANGE;
                            ClientInterface currentOwner =
                                    (ClientInterface) Naming.lookup(
                                            "rmi://" + owner + ":" +
                                                    port + "/fileclient");

                            System.out.println(
                                    "from " + owner +
                                            " write back is requested");

                            // requesting write back from the client
                            currentOwner.writeback();

                            synchronized (monitor2) {
                                monitor2.wait();
                            }

                            // wait around here, and once
                            // owner client upload the file,
                            //change the owner.
                            owner = client;
                        }
                        break;
                }

                // retrieve file contents from cache
                FileContents contents = new FileContents(bytes);

                if (previousState == State.WRITE_SHARED) {
                    synchronized (monitor1) {
                        monitor1.notify();
                    }
                }

                return contents;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * give the file content to the client.
         * (helper function for FileServer.download)
         *
         * @param client the client w
         * @param contents
         * @return
         */
        public boolean upload(String client, FileContents contents) {
            System.out.println("upload is called");

            try {
                // invalidate all readers' cache
                ClientInterface clientInterface = null;
                for (String reader : readers) {

                    // RMI registration;
                    clientInterface = (ClientInterface) Naming.lookup(
                            "rmi://" + reader + ":" + port + "/fileclient");
                    if (clientInterface != null) {
                        clientInterface.invalidate();
                    }
                }

                // clear readers (subscribers)
                readers.removeAllElements();

                State prev_state = state;

                // save file contents
                bytes = contents.get();
                System.out.println("bytes written = " + new String(bytes));

                // state transition
                switch (state) {
                    case WRITE_SHARED:
                        state = State.NOT_SHARED;
                        owner = null;
                        writeFile();
                        break;
                    case OWNERSHIP_CHANGE:
                        state = State.WRITE_SHARED;
                        owner = client;
                        synchronized (monitor2) {
                            monitor2.notify();
                        }
                        break;
                }


                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
