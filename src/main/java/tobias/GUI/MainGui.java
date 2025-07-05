package tobias.GUI;
import tobias.Utils.VersionUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainGui {
    public interface FileDropListener {
        void onFilesDropped(List<File> files);
    }

    private JProgressBar progressBar;
    private JComboBox<String> currentVersionSelector;
    private JComboBox<String> targetVersionSelector;
    String[] versionOptions = {
            "1.8.9", "1.9", "1.11", "1.13", "1.15",
            "1.16.2", "1.17", "1.18", "1.19", "1.19.3",
            "1.19.4", "1.20", "1.20.2", "1.20.3", "1.20.5",
            "1.21", "1.21.2", "1.21.4"
    };

    public void DragAndDropGui(FileDropListener listener) {
        JFrame frame = new JFrame("AutoPackUpdater by vuacy");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(450, 350);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setLayout(new BorderLayout());

        // Info-Bar oben
        JLabel infoLabel = new JLabel("ⓘ");
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        infoLabel.setForeground(Color.BLUE);
        infoLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        infoLabel.setToolTipText("More information");

        infoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(frame,
                        "This tool updates old Minecraft resource packs to a selected newer version.\n\n" +
                                "Just drag a .zip file into the window and select the current and target versions.\n\n" +
                                "It performs the following tasks automatically:\n" +
                                "• Converts and renames items and folders to match newer structures.\n" +
                                "• Updates the 'pack_format' so Minecraft can recognize the new version.\n" +
                                "• Removes fake transparent pixels from items (helps with 3D appearance in high-res packs).\n" +
                                "• From version 1.11+, it generates left and right offhand hotbars from the original one.\n" +
                                "• If you're updating a 1.8.9 pack, it will also generate Netherite items.\n",
                        "What does this tool do?",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });



        frame.add(infoLabel, BorderLayout.NORTH);



        JLabel dropLabel = new JLabel("Drag a pack as .zip here", SwingConstants.CENTER);
        dropLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        currentVersionSelector = new JComboBox<>(versionOptions);
        targetVersionSelector = new JComboBox<>();

        currentVersionSelector.setSelectedItem("1.8.9");
        updateTargetVersions("1.8.9");

        currentVersionSelector.addItemListener(e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                String selectedCurrentVersion = (String) currentVersionSelector.getSelectedItem();
                updateTargetVersions(selectedCurrentVersion);
            }
        });

        JPanel versionPanel = new JPanel();
        versionPanel.setLayout(new GridLayout(4, 1));
        versionPanel.add(new JLabel("What version is the pack currently using?"));
        versionPanel.add(currentVersionSelector);
        versionPanel.add(new JLabel("What version should the pack be updated to?"));
        versionPanel.add(targetVersionSelector);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.add(versionPanel);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(progressBar);

        frame.add(dropLabel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.setVisible(true);

        new DropTarget(dropLabel, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    List<File> zipFiles = droppedFiles.stream()
                            .filter(f -> f.getName().toLowerCase().endsWith(".zip"))
                            .collect(Collectors.toList());

                    listener.onFilesDropped(zipFiles);

                } catch (Exception e) {
                    System.err.println("Fehler beim Drop: " + e.getMessage());
                }
            }
        });
    }
    private void updateTargetVersions(String selectedCurrentVersion) {

        List<String> validTargets = Arrays.stream(versionOptions)
                .filter(v -> VersionUtil.versionToPackFormat(v) > VersionUtil.versionToPackFormat(selectedCurrentVersion))
                .collect(Collectors.toList());

        if (validTargets.isEmpty()) {
            targetVersionSelector.setModel(new DefaultComboBoxModel<>(new String[]{"– no target –"}));
            targetVersionSelector.setEnabled(false);
        } else {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(validTargets.toArray(new String[0]));
            targetVersionSelector.setModel(model);
            targetVersionSelector.setSelectedItem(validTargets.get(0));
            targetVersionSelector.setEnabled(true);
        }
    }
    public String getSelectedCurrentVersion() {
        return (String) currentVersionSelector.getSelectedItem();
    }
    public String getSelectedTargetVersion() {
        return (String) targetVersionSelector.getSelectedItem();
    }
    public void setProgress(int value) {
        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> progressBar.setValue(value));
        }
    }
}