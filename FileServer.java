import com.sun.javaws.exceptions.InvalidArgumentException;

import java.io.*;
import java.net.PortUnreachableException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Queue;
import java.util.Vector;


public class FileServer extends UnicastRemoteObject implements ServerInterface {
    private Vector<File> files = null;
    private int port = 0;

    // Better to exclude constructor which we would never use.
//    public FileServer() throws RemoteException {
//        this.files = new Vector<Cache>();
//    }

    public FileServer(int port) throws RemoteException {
        this.port = port;
        this.files = new Vector<>();
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        try {
            if (args.length != 1) {
                String[] message = new String[1];
                message[0] = "new String[0] = usage: java Server port";
                throw new InvalidArgumentException(message);
            }
            if (port < 5001 || port > 65535)
                throw new PortUnreachableException("port range should be 5001 ~ 65535");
        } catch (PortUnreachableException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        try {
            startRegistry(port);
            FileServer serverObject = new FileServer(port);
            Naming.rebind("rmi://localhost:" + port + "/fileserver", serverObject);
            System.out.println("Server ready.");


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

    public FileContents download(String client, String filename, String mode) throws RemoteException {

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
        if (fileContent == null) {
            file = new File(filename, this.port);
            this.files.add(file);
        }

        // todo:
        return file.download(client, mode);
    }

    public boolean upload(String client, String filename, FileContents contents) throws RemoteException {
        System.out.println("upload invoked");
        File file = null;

        // file the file to upload
        for (File f : files) {
            if(filename.equals(f.filename)) {
                file = f;
                break;
            }
        }

        return (file != null) && file.upload(client, contents);
    }

    enum State {
        NOT_SHARED, READ_SHARED, WRITE_SHARED, OWNERSHIP_CHANGE;
    }

    private class File {

        private State state;
        private String filename;
        private byte[] bytes = null;
        private Vector<String> readers = null;
        private String owner = null;
        private int port = 0;

        public File(String filename, int port) {
            this.state = State.NOT_SHARED;
            this.filename = filename;
            readers = new Vector<String>();
            owner = null;
            this.port = port;

            // read file contents from the local disk
            bytes = readFile();
        }

        /**
         * @return
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
         * @param client
         * @param mode
         * @return
         */
        public FileContents download(String client, String mode) {

            try {
                // todo: more invalid file check is required. (mode) (filename) (low)
                // state transition
                //Ownership change state, when the ownership is released,
                // todo: need to implement notify mechanism
                while (state == State.OWNERSHIP_CHANGE) {
                }

                State previousState = state;
                int error = 0;
                switch (state) {
                    case NOT_SHARED:
                        if (mode.equals("r")) {
                            state = State.READ_SHARED;
                            readers.add(client);
                        } else if (mode.equals("w")) {
                            state = State.WRITE_SHARED;
                            if (owner != null)
                                throw new SyncFailedException("Critical error. previous owner exist in NOT_SHARED file");
                            else
                                owner = client;
                        }
                        break;
                    case READ_SHARED:
                        removeReader(client);
                        if (mode.equals("r"))
                            readers.add(client);
                        else if (mode.equals("w")) {
                            state = State.WRITE_SHARED;
                            if (owner != null)
                                throw new SyncFailedException("Critical error. previous owner exist in READ_SHARED file");
                            else
                                owner = client;
                        }
                        break;
                    case WRITE_SHARED:
                        removeReader(client);
                        if (mode.equals("r"))
                            readers.add(client);
                        else if (mode.equals("w")) {
                            state = State.OWNERSHIP_CHANGE;
                            ClientInterface currentOwner = (ClientInterface) Naming.lookup("rmi://" + owner + ":" + port + "/fileclient");

                            // if it is the owner, it will send always true....
                            // todo: suspend at this moment (wait), and once gets the owner ship,
                            currentOwner.writeback();

                            wait();

                            // wait around here, and once owner client upload the file,
                            //change the owner.
                            owner = client;
                        }
                        break;
                }

                // retrieve file contents from cache
                FileContents contents = new FileContents(bytes);
                if (previousState == State.OWNERSHIP_CHANGE) {
                    // todo: need to update new owner.
                }
                return contents;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        /**
         * @param client
         * @param contents
         * @return
         */
        public boolean upload(String client, FileContents contents) {
            // todo: validation check

            try {
                // invalidate all readers' cache
                ClientInterface clientInterface = null;
                for (String reader : readers) {

                    // RMI registration;

                    clientInterface = (ClientInterface) Naming.lookup("rmi://" + reader + ":" + port + "/fileclient");
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
                        // write file to the system.
                        writeFile();
                        break;
                    case OWNERSHIP_CHANGE:
                        state = State.WRITE_SHARED;
                        owner = client;
                        notify();
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        public String getName(String name) {
            return this.filename;
        }
    }
}
