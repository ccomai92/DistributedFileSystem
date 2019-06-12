import java.util.Vector;

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


//    // For the style preference.
//    // put state here instead of outside FileServer and FileClient
//    private enum State {
//        NOT_SHARED, READ_SHARED, WRITE_SHARED, OWNERSHIP_CHANGE;
//    }
}