import com.sun.javaws.exceptions.InvalidArgumentException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.PortUnreachableException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;


public class FileServer extends UnicastRemoteObject implements ServerInterface {
    private Vector<File> cacheList = null;
    private int port = 0;

    // Better to exclude constructor which we would never use.
//    public FileServer() throws RemoteException {
//        this.cacheList = new Vector<Cache>();
//    }

    public FileServer(int port) throws RemoteException {
        this.port = port;
        this.cacheList = new Vector<>();
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        try {
            if (args.length == 1)
                throw new InvalidArgumentException("usage: java Server port");
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
//            startRegistry(port);
            FileServer serverObject = new FileServer(port);
            Naming.rebind("rmi://localhost:" + port + "/server", serverObject);
            System.out.println("Server ready.");


        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

//    private static void startRegistry(int port) throws RemoteException {
//        try {
//            Registry registry = LocateRegistry.getRegistry(port);
//            registry.list();
//        } catch (RemoteException e) {
//            Registry registry = LocateRegistry.createRegistry(port);
//        }
//    }

    public FileContents download(String client, String filename, String mode) throws RemoteException {
//        System.out.println("download invoked");
//        byte[] temp = new byte[10];
//        for (int i = 0; i < 10; i++) {
//            temp[i] = (byte) i;
//        }
//        System.out.println("Sending: " + temp);
//        return new FileContents(temp);

        for(File file : cacheList) {
            if(file)
        }
    }

    public boolean upload(String client, String filename, FileContents contents) throws RemoteException {
        System.out.println("upload invoked");
        File file = null;

        for(int i = 0; i< cacheList.size(); ++i) {

        }
        return true;
    }

    enum State {
        NOT_SHARED, READ_SHARED, WRITE_SHARED, OWNERSHIP_CHANGE;
    }

    private class File {

        private State state;
        private String name;
        private byte[] bytes = null;
        private Vector<String> readers = null;
        private String owner = null;
        private int port = 0;

        public File(String name, String port) {
            this.state = State.NOT_SHARED;
            this.name = name;
            readers = new Vector<String>();
            owner = null;
            this.port = port;

            // read file contents from the local disk
            bytes = readFile();
        }

        private byte[] readFile() {
            byte[] bytes = null;
            try {
                FileInputStream file = new FileInputStream(name);
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
            System.out.println("file read from " + name + ": " +
                    bytes.length + " bytes");
            return bytes;
        }

        private boolean writeFile() {
            try {
                FileOutputStream file = new FileOutputStream(name);
                file.write(bytes);
                file.flush();
                file.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        public String getName(String name) {
            return this.name;
        }
    }
}