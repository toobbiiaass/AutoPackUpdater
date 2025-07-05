package tobias.Objects;

import java.util.List;

public class GuiSplitCategory {
    private String oldPath;
    private String newPath;
    private List<GuiSplitFile> files;

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public List<GuiSplitFile> getFiles() {
        return files;
    }

    public static class GuiSplitFile {
        private int id;
        private String oldname;
        private String newname;
        private int x;
        private int y;
        private int xLength;
        private int yLength;

        public int getId() {
            return id;
        }

        public String getOldname() {
            return oldname;
        }

        public String getNewname() {
            return newname;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getxLength() {
            return xLength;
        }

        public int getyLength() {
            return yLength;
        }
    }
}
