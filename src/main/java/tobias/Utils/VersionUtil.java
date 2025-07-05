package tobias.Utils;

public class VersionUtil {

    public static int versionToPackFormat(String version) {
        return switch (version) {
            case "1.8.9" -> 1;
            case "1.9" -> 2;
            case "1.11" -> 3;
            case "1.13" -> 4;
            case "1.15" -> 5;
            case "1.16.2" -> 6;
            case "1.17" -> 7;
            case "1.18" -> 8;
            case "1.19" -> 9;
            case "1.19.3" -> 11;
            case "1.19.4" -> 13;
            case "1.20" -> 15;
            case "1.20.2" -> 18;
            case "1.20.3" -> 22;
            case "1.20.5" -> 32;
            case "1.21" -> 34;
            case "1.21.2" -> 42;
            case "1.21.4" -> 46;
            case "1.21.5" -> 55;
            case "1.21.6" -> 63;
            default -> 1;
        };
    }
}
