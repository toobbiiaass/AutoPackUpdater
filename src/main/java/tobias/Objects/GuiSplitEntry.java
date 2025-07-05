package tobias.Objects;

public class GuiSplitEntry {
    public int id;
    public String oldname;
    public String newname;
    public int x;
    public int y;
    public int xLength;
    public int yLength;

    @Override
    public String toString() {
        return "[" + id + "] " + oldname + " → " + newname + " @ (" + x + "," + y + ") size " + xLength + "x" + yLength;
    }
}
