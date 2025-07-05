package tobias.Objects;

public class RenameEntry {
    public String old;
    public String neww; // wir verwenden hier "newName" weil "new" ein Schlüsselwort ist

    // Gson braucht einen Default-Konstruktor
    public RenameEntry() {}

    @Override
    public String toString() {
        return "Old: " + old + " → New: " + neww;
    }
}
