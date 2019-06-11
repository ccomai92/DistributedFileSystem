import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.net.*;
import java.io.*;
import java.util.*;




public class FileServer extends UnicastRemoteObject implements ServerInterface {
    enum State {
        NOT_SHARED, READ_SHARED, WRITE_SHARED, OWNERSHIP_CHANGE;
    }

    private Vector<Cache> cacheList;  
    
    public FileServer() throws RemoteException {
        this.cacheList = new Vector<Cache>();
    }
    
    public FileContents download(String client, String filename, String mode) throws RemoteException {
        System.out.println("download invoked");
        byte[] temp = new byte[10];
        for (int i = 0; i < 10; i++) {
            temp[i] = (byte) i; 
        }
        System.out.println("Sending: " + temp);
        return new FileContents(temp);
    }

    public boolean upload(String client, String filename, FileContents contents) throws RemoteException {
        System.out.println("upload invoked");
        return true;
    }


    public static void main(String[] args) {
        int port = 0;
		try {
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
				if (port < 5001 || port > 65535)
					throw new Exception();
			} else
				throw new Exception();
		} catch (Exception e) {
			System.err.println("usage: java Server port");
			System.exit(-1);
		}

        try {
			startRegistry(port);
			FileServer serverObject = new FileServer();
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

}
