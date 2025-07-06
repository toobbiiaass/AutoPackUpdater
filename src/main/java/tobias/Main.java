package tobias;

import com.google.gson.*;
import tobias.GUI.MainGui;
import tobias.Objects.*;
import tobias.Utils.JsonUtil;
import tobias.Utils.VersionUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {

    public static void main(String[] args) {
        MainGui gui = new MainGui();
        gui.setProgress(0);

        gui.DragAndDropGui(files -> {
            for (File file : files) {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    new SwingWorker<Void, Integer>() {
                        @Override
                        protected Void doInBackground() {
                            String currentVersion = gui.getSelectedCurrentVersion();
                            String targetVersion = gui.getSelectedTargetVersion();

                            int currentFormat = VersionUtil.versionToPackFormat(currentVersion);
                            int targetFormat = VersionUtil.versionToPackFormat(targetVersion);

                            gui.setProgress(10);
                            copyAndRenameZip(file, currentFormat, targetFormat, targetVersion);
                            gui.setProgress(100);
                            System.out.println("-------------------------------------------");
                            System.out.println("|                   DONE                  |");
                            System.out.println("-------------------------------------------");
                            return null;
                        }
                    }.execute();
                } else {
                    System.out.println("Not a ZIP file: " + file.getName());
                }
            }
        });
    }

    private static void copyAndRenameZip(File originalZip, int currentFormatFromUser, int targetFormat, String targetVersionString) {
        String originalName = originalZip.getName();
        String baseName = originalName.substring(0, originalName.lastIndexOf(".zip"));
        File updatedZip = new File(originalZip.getParent(), baseName + "_v" + targetVersionString + ".zip");

        boolean needsUpgrade = false;
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();

        try (
                ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(originalZip.toPath()));
                ZipOutputStream zipOutput = new ZipOutputStream(zipBuffer);
        ) {
            byte[] buffer = new byte[4096];

            List<String> existingFolders = getExistingZipDirectories(originalZip);
            List<String> foldersToEnsure = List.of(
                            Paths.get("assets", "minecraft", "textures", "gui", "sprites").toString(),
                            Paths.get("assets", "minecraft", "textures", "gui", "sprites", "hud").toString(),
                            Paths.get("assets", "minecraft", "textures", "gui", "sprites", "hud", "heart").toString(),
                            Paths.get("assets", "minecraft", "textures", "entity", "equipment", "humanoid").toString(),
                            Paths.get("assets", "minecraft", "textures", "entity", "equipment", "humanoid_leggings").toString()
                    ).stream()
                    .map(p -> p.replace("\\", "/"))
                    .collect(Collectors.toList());

            for (String folder : foldersToEnsure) {
                if (!existingFolders.contains(folder)) {
                    zipOutput.putNextEntry(new ZipEntry(folder));
                    zipOutput.closeEntry();
                }
            }
            List<RenameEntry> allFoldersToRename = null;
            List<RenameEntry> allItemsToRename = null;
            List<ParticleEntry> allParticleEntries = null;
            Map<String, GuiSplitCategory> allWidgetsIcons = null;
            Map<String, ArmorCategory> allArmors = null;
            Set<String> alreadyWrittenPaths = new HashSet<>();

            Map<String, byte[]> allZipEntries = new HashMap<>();


            boolean needNethGen = false;
            boolean createoffhand = false;

            File widgetsImage = null;
            File iconsImage = null;


            if (targetFormat > 1 && currentFormatFromUser <= 1) {
                //1.8 in 1.9
                Path pathParticles = Paths.get("src", "packformats", "packformat1To2", "0_particles.json");
                allParticleEntries = JsonUtil.loadParticles(pathParticles.toString());
                needNethGen = true;


            }
            if (targetFormat > 3 && currentFormatFromUser <= 3) {
                //1.11 in 1.13
                Path path = Paths.get("src", "packformats", "packformat2To3", "0_folders_rename.json");
                Path pathItems = Paths.get("src", "packformats", "packformat2To3", "1_items_rename.json");

                allFoldersToRename = JsonUtil.loadRenames(path.toString());
                allItemsToRename = JsonUtil.loadRenames(pathItems.toString());
            }
            if (targetFormat > 15 && currentFormatFromUser <= 15) {
                //1.20 in 1.20.2
                createoffhand = true;

                Path pathWidgets = Paths.get("src", "packformats", "packformat14To15", "0_widgetsIcons.json");
                allWidgetsIcons = JsonUtil.loadGuiSplits(pathWidgets.toString());
            }

            if (targetFormat > 34 && currentFormatFromUser <= 34) {
                //1.21 in 1.21.2

                Path pathArmor = Paths.get("src", "packformats", "packformat33To34", "0_bodyarmor.json");
                allArmors = JsonUtil.loadArmorMappings(pathArmor.toString());

            }

            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
                int len;
                while ((len = zipInput.read(buffer)) > 0) {
                    entryBuffer.write(buffer, 0, len);
                }

                byte[] entryData = entryBuffer.toByteArray();
                if (entry.getName().equalsIgnoreCase("pack.mcmeta")) {
                    entryData = updatePackMcmeta(entryData, currentFormatFromUser, targetFormat);
                    needsUpgrade = true;
                }

                String entryName = entry.getName();
                String newEntryName = entryName;
                //System.out.println(entryName);

                //Rename folders
                if(allFoldersToRename != null){
                    for (RenameEntry e : allFoldersToRename) {
                        String renamed = renameZipPath(entryName, e);
                        if (renamed != null) {
                            newEntryName = renamed;
                            break;
                        }
                    }
                }

                // Rename items
                if (allItemsToRename != null) {
                    newEntryName = renameItemNameInPath(newEntryName, allItemsToRename);
                }



                if(entryName.contains("particle") && entryName.contains("particles")){ //gen particles
                    if (entryName.endsWith("particles.png") && allParticleEntries != null) {
                        System.out.println("Splitting particle");
                        File tempImage = File.createTempFile("particles", ".png");
                        try (FileOutputStream fos = new FileOutputStream(tempImage)) {
                            fos.write(entryData);
                        }
                        Path particleTargetDir = Files.createTempDirectory("particles_");
                        objcutter(allParticleEntries, tempImage, particleTargetDir);

                        for (ParticleEntry p : allParticleEntries) {
                            File particleFile = new File(particleTargetDir.toFile(), p.name);
                            if (!particleFile.exists()) continue;

                            String basePath = entryName.substring(0, entryName.lastIndexOf('/') + 1);
                            String zipPath = basePath + p.name;

                            zipOutput.putNextEntry(new ZipEntry(zipPath));
                            try (FileInputStream fis = new FileInputStream(particleFile)) {
                                byte[] buf = new byte[4096];
                                int len2;
                                while ((len2 = fis.read(buf)) > 0) {
                                    zipOutput.write(buf, 0, len2);
                                }
                            }
                            zipOutput.closeEntry();
                        }
                        continue;
                    }
                }


                if ((entryName.contains("item") || entryName.contains("items"))
                        && !entryName.endsWith("/")
                        && entryName.endsWith(".png")) {
                    entryData = cleanTransparentPixels(entryData);
                }

                if (needNethGen &&
                        entryName.endsWith(".png") &&
                        entryName.contains("diamond") &&
                        !entry.isDirectory() &&
                        (entryName.contains("/item/") || entryName.contains("/items/") ||
                                entryName.contains("/textures/entity/") || entryName.contains("/textures/models/"))) {
                    System.out.println("Generating netherite piece");

                    byte[] netheriteImageData = recolorToNetherite(entryData);

                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    String netheriteFileName = fileName.replace("diamond", "netherite");

                    String netheritePath = "";
                    if(!entryName.contains("armor")){
                        netheritePath = Paths.get("assets", "minecraft", "textures", "item", netheriteFileName)
                                .toString()
                                .replace("\\", "/");
                    }else{
                        netheritePath = Paths.get("assets", "minecraft", "textures", "models","armor", netheriteFileName)
                                .toString()
                                .replace("\\", "/");
                    }


                    writeZipEntryIfNotExists(zipOutput, alreadyWrittenPaths, netheritePath, netheriteImageData);
                    allZipEntries.put(netheritePath, netheriteImageData);


                }

                if (createoffhand) {
                    if (entryName.endsWith("widgets.png")
                    && !entryName.contains("spectator")) {
                        widgetsImage = File.createTempFile("widgets", ".png");
                        try (FileOutputStream fos = new FileOutputStream(widgetsImage)) {
                            fos.write(entryData);
                        }
                        continue;
                    }

                    if (entryName.endsWith("icons.png")
                            && !entryName.contains("achievement")
                            && !entryName.contains("stats")
                            && !entryName.contains("map")
                            && !entryName.contains("invitation")) {
                        iconsImage = File.createTempFile("icons", ".png");
                        try (FileOutputStream fos = new FileOutputStream(iconsImage)) {
                            fos.write(entryData);
                        }
                        continue;
                    }

                }

                writeZipEntryIfNotExists(zipOutput, alreadyWrittenPaths, newEntryName, entryData);
                allZipEntries.put(entryName, entryData);
            }


            if (createoffhand && allWidgetsIcons != null) {
                if ((widgetsImage == null || !widgetsImage.exists()) && (iconsImage == null || !iconsImage.exists())) {
                    System.out.println("Didn't found widget.png and icons.ng");
                } else {
                    Path tempOutputDir = Files.createTempDirectory("widgets_output");

                    for (Map.Entry<String, GuiSplitCategory> entry2 : allWidgetsIcons.entrySet()) {
                        String key = entry2.getKey().toLowerCase();
                        GuiSplitCategory category = entry2.getValue();

                        File sourceImage = null;
                        if (key.contains("widget") && widgetsImage != null && widgetsImage.exists()) {
                            sourceImage = widgetsImage;
                        } else if (key.contains("icon") && iconsImage != null && iconsImage.exists()) {
                            sourceImage = iconsImage;
                        }

                        if (sourceImage == null) {
                            System.err.println("Error Splitting: " + key);
                            continue;
                        }

                        System.out.println("Splitting: " + key);
                        objcutterIconsAndWidgets(Map.of(entry2.getKey(), category), sourceImage, tempOutputDir, 256);
                    }

                    if (widgetsImage != null) widgetsImage.delete();
                    if (iconsImage != null) iconsImage.delete();

                    Files.walk(tempOutputDir)
                            .filter(Files::isRegularFile)
                            .forEach(path -> {
                                String relative = tempOutputDir.relativize(path).toString().replace("\\", "/");
                                try {
                                    zipOutput.putNextEntry(new ZipEntry(relative));
                                    Files.copy(path, zipOutput);
                                    zipOutput.closeEntry();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                    // HOTBAR OFFHAND
                    Path hotbarPath = tempOutputDir.resolve(Paths.get("assets", "minecraft", "textures", "gui", "sprites", "hud", "hotbar.png"));
                    if (Files.exists(hotbarPath)) {
                        Path hotbarOutDir = Files.createTempDirectory("hotbar_out");
                        OffHandCreator(hotbarPath.toFile(), hotbarOutDir);

                        for (String name : List.of("hotbar_offhand_left.png", "hotbar_offhand_right.png")) {
                            Path file = hotbarOutDir.resolve(name);
                            if (Files.exists(file)) {
                                String relativePath = Paths.get("assets", "minecraft", "textures", "gui", "sprites", "hud", name)
                                        .toString()
                                        .replace("\\", "/");

                                writeZipEntryIfNotExists(zipOutput, alreadyWrittenPaths, relativePath, Files.readAllBytes(file));
                            }
                        }
                    } else {
                        System.err.println("hotbar.png not found - no offhand generation");
                    }
                }
            }


            if (allArmors != null) {
                for (Map.Entry<String, ArmorCategory> armorCategoryEntry : allArmors.entrySet()) {
                    ArmorCategory category = armorCategoryEntry.getValue();

                    for (ArmorFileEntry fileEntry : category.files) {
                        String expectedPath = category.oldPath.replaceFirst("^/", "") + "/" + fileEntry.oldname;
                        for (Map.Entry<String, byte[]> writtenEntry : new HashMap<>(allZipEntries).entrySet()) {
                            if (writtenEntry.getKey().equalsIgnoreCase(expectedPath)) {
                                String newPath = category.newPath.replaceFirst("^/", "") + "/" + fileEntry.newname;
                                if (!alreadyWrittenPaths.contains(newPath)) {
                                    zipOutput.putNextEntry(new ZipEntry(newPath));
                                    zipOutput.write(writtenEntry.getValue());
                                    zipOutput.closeEntry();
                                    alreadyWrittenPaths.add(newPath);
                                }
                            }
                        }
                    }
                }
            }
            zipOutput.close();

            if (needsUpgrade) {
                try (FileOutputStream fos = new FileOutputStream(updatedZip)) {
                    zipBuffer.writeTo(fos);
                }
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void writeZipEntryIfNotExists(ZipOutputStream zipOutput, Set<String> alreadyWritten, String entryName, byte[] data) throws IOException {
        if (!alreadyWritten.contains(entryName)) {
            zipOutput.putNextEntry(new ZipEntry(entryName));
            zipOutput.write(data);
            zipOutput.closeEntry();
            alreadyWritten.add(entryName);
        }
    }

    private static void objcutterIconsAndWidgets(Map<String, GuiSplitCategory> dataMap, File imageP, Path outputDir, int baseWidth) {
        if (!imageP.exists()) {
            System.err.println("Image doesn't exist: " + imageP.getAbsolutePath());
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(imageP);
            if (image == null) {
                System.err.println("Error: Image not readable: " + imageP.getName());
                return;
            }
        } catch (IOException e) {
            throw new RuntimeException("Image not readable", e);
        }
        int multi = image.getWidth() / baseWidth;
        if (multi <= 0) {
            System.err.println("Invalid scaling factor for image: " + imageP.getName());
            return;
        }

        for (GuiSplitCategory category : dataMap.values()) {
            String newPath = category.getNewPath().replaceFirst("^/", "");

            for (GuiSplitCategory.GuiSplitFile f : category.getFiles()) {
                int x = f.getX() * multi;
                int y = f.getY() * multi;
                int width = f.getxLength() * multi;
                int height = f.getyLength() * multi;

                if (x + width > image.getWidth() || y + height > image.getHeight()) {
                    continue;
                }

                try {
                    BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    for (int dx = 0; dx < width; dx++) {
                        for (int dy = 0; dy < height; dy++) {
                            Color c = new Color(image.getRGB(x + dx, y + dy), true);
                            if (c.getAlpha() != 0) {
                                result.setRGB(dx, dy, c.getRGB());
                            }
                        }
                    }

                    String[] nameParts = f.getNewname().split(";");
                    String filename = nameParts[0];
                    String subfolder = nameParts.length > 1 ? nameParts[1] : "";

                    Path savePath = outputDir.resolve(Paths.get(newPath, subfolder));
                    Files.createDirectories(savePath);
                    File outputFile = savePath.resolve(filename).toFile();

                    ImageIO.write(result, "png", outputFile);
                } catch (IOException e) {
                    System.err.println("Error when saving " + f.getNewname() + ": " + e.getMessage());
                }
            }
        }
    }

    public static byte[] cleanTransparentPixels(byte[] imageData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(bis);
            if (image == null) return imageData;

            int width = image.getWidth();
            int height = image.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = image.getRGB(x, y);
                    int alpha = (rgba >> 24) & 0xff;

                    if (alpha <= 50) {
                        image.setRGB(x, y, 0x00000000);
                    }
                }
            }

            ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", tempOut);
            return tempOut.toByteArray();

        } catch (IOException e) {
            System.err.println("Error Transparenz: " + e.getMessage());
            return imageData;
        }
    }

    private static List<String> getExistingZipDirectories(File zipFile) {
        List<String> directories = new ArrayList<>();

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/");

                Path path = Paths.get(name);
                for (int i = 1; i < path.getNameCount(); i++) {
                    String dir = path.subpath(0, i).toString().replace("\\", "/") + "/";
                    if (!directories.contains(dir)) {
                        directories.add(dir);
                    }
                }

                if (entry.isDirectory() && !name.endsWith("/")) {
                    name += "/";
                }

                if (entry.isDirectory() && !directories.contains(name)) {
                    directories.add(name);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return directories;
    }

    private static void objcutter(List<ParticleEntry> particles, File imageP, Path outputFolder) {
        if (!imageP.exists()) {
            return;
        }

        BufferedImage image;
        try {
            image = ImageIO.read(imageP);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int multi = image.getWidth() / 128;
        for (ParticleEntry p : particles) {
            try {
                BufferedImage overlay = ImageIO.read(imageP);
                BufferedImage result = new BufferedImage(multi * 8, multi * 8, BufferedImage.TYPE_INT_ARGB);

                int x1 = p.x * multi;
                int y1 = p.y * multi;
                int xOutLine = x1 + (8 * multi);
                int yOutLine = y1 + (8 * multi);

                for (int x = x1; x < xOutLine; x++) {
                    for (int y = y1; y < yOutLine; y++) {
                        Color overlayColor = new Color(overlay.getRGB(x, y), true);
                        if (overlayColor.getAlpha() != 0) {
                            result.setRGB(x - x1, y - y1, overlay.getRGB(x, y));
                        }
                    }
                }
                File output = new File(outputFolder.toFile(), p.name);
                output.getParentFile().mkdirs();
                ImageIO.write(result, "png", output);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static String renameZipPath(String entryName, RenameEntry rename) {
        String[] oldParts = rename.old.startsWith("/") ? rename.old.substring(1).split("/") : rename.old.split("/");
        String[] newParts = rename.neww.startsWith("/") ? rename.neww.substring(1).split("/") : rename.neww.split("/");

        Path oldPath = Paths.get("", oldParts);
        Path newPath = Paths.get("", newParts);
        Path entryPath = Paths.get(entryName);

        if (entryPath.startsWith(oldPath)) {
            Path relative = oldPath.relativize(entryPath);
            Path newFullPath = newPath.resolve(relative);
            return newFullPath.toString().replace("\\", "/");
        }

        return null;
    }

    public static String renameItemNameInPath(String entryName, List<RenameEntry> renames) {
        for (RenameEntry e : renames) {
            if (entryName.contains(e.old)) {
                System.out.println(e.old +" to "+ e.neww);
                return entryName.replace(e.old, e.neww);
            }
        }
        return entryName;
    }

    public static byte[] updatePackMcmeta(byte[] entryData, int currentFormat, int targetFormat) {
        try {
            String jsonString = new String(entryData, StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonObject pack = json.getAsJsonObject("pack");

            if (pack != null && pack.has("pack_format")) {
                int existingFormat = pack.get("pack_format").getAsInt();

                if (targetFormat > existingFormat) {
                    pack.addProperty("pack_format", targetFormat);
                    pack.addProperty("description", "\u00A7bPack updated by vuacy tool");
                    return json.toString().getBytes(StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            System.err.println("Error while editing pack.mcmeta: " + e.getMessage());
        }

        return entryData;
    }

    public static byte[] recolorToNetherite(byte[] imageData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData)) {
            BufferedImage image = ImageIO.read(bis);
            if (image == null) return imageData;

            int width = image.getWidth();
            int height = image.getHeight();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = image.getRGB(x, y);
                    Color original = new Color(rgba, true);
                    int alpha = original.getAlpha();

                    if (alpha == 255) {
                        double L = 0.4126 * original.getRed() + 0.7152 * original.getGreen() + 0.0722 * original.getBlue();

                        int newR = (int) (71 * L / 255);
                        int newG = (int) (58 * L / 255);
                        int newB = (int) (65 * L / 255);

                        Color netherite = new Color(clamp(newR), clamp(newG), clamp(newB), alpha);
                        image.setRGB(x, y, netherite.getRGB());
                    }
                }
            }

            ByteArrayOutputStream tempOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", tempOut);
            return tempOut.toByteArray();

        } catch (IOException e) {
            System.err.println("Netherite recoloring error: " + e.getMessage());
            return imageData;
        }
    }
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public static void OffHandCreator(File hotbarImageFile, Path safeFile) {
        int toCutFromHotbar = 11;
        int verticalSpacingUnits = 1;

        try {
            BufferedImage hotbarImage = ImageIO.read(hotbarImageFile);

            int height = hotbarImage.getHeight();
            int multiplier = height / 22;

            int scaledCutWidth = toCutFromHotbar * multiplier;
            int spacing = verticalSpacingUnits * multiplier;
            int horizontalSpacing = 7 * multiplier;

            BufferedImage leftCut = hotbarImage.getSubimage(0, 0, scaledCutWidth, height);

            BufferedImage mirroredLeftCut = horizontalFlip(leftCut);

            int newWidth = (scaledCutWidth * 2) + horizontalSpacing;
            int newHeight = height + (2 * spacing);

            BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();

            g.drawImage(leftCut, 0, spacing, null);

            g.drawImage(mirroredLeftCut, scaledCutWidth, spacing, null);

            g.dispose();

            File output = safeFile.resolve("hotbar_offhand_left.png").toFile();
            ImageIO.write(result, "png", output);

            BufferedImage flipped = horizontalFlip(result);
            File flippedOutput = safeFile.resolve("hotbar_offhand_right.png").toFile();
            ImageIO.write(flipped, "png", flippedOutput);

            System.out.println("Offhand left and right were generated");

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Image processing failed.");
        }
    }

    private static BufferedImage horizontalFlip(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flipped = new BufferedImage(width, height, image.getType());
        Graphics2D g = flipped.createGraphics();

        AffineTransform transform = AffineTransform.getScaleInstance(-1, 1);
        transform.translate(-width, 0);
        g.drawImage(image, transform, null);

        g.dispose();
        return flipped;
    }
}