import java.util.*;

public class Cache {
    
    private String fileName;
    private Vector<String> readers;
    private String owner; 
    private int state; 
    private FileContents content; 

    
    public Cache(String fileName, String reader, String owner, int state, FileContents content) {
        this.fileName = fileName;
        this.readers = new Vector<String>();
        this.owner = "";
        this.state = state;
        this.content = content;
    }

    
    





}