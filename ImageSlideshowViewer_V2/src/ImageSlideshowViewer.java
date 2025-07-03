import javax.swing.*;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * Advanced Image Slideshow Viewer with comprehensive features
 * Features: Local/URL loading, drag-drop, zoom, fullscreen, slideshow, transitions,
 * wallpaper setting, metadata display, and basic editing
 */
public class ImageSlideshowViewer extends JFrame {
    // Core components
    private ImagePanel imagePanel;
    private JPanel controlPanel, metadataPanel;
    private JLabel statusLabel, metadataLabel;
    private JButton playBtn, prevBtn, nextBtn, fullscreenBtn, loadBtn, urlBtn;
    private JSlider intervalSlider, zoomSlider;
    private JComboBox<String> transitionCombo;

    // Image management
    private List<ImageFile> imageFiles = new ArrayList<>();
    private int currentIndex = 0;
    private Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

    // Slideshow controls
    private Timer slideshowTimer;
    private boolean isPlaying = false;
    private int intervalSeconds = 3;
    private String transitionType = "Fade";

    // Display state
    private double zoomFactor = 1.0;
    private boolean isFullscreen = false;
    private Point dragStart;
    private Point imageOffset = new Point(0, 0);

    // Editing state
    private BufferedImage originalImage, editedImage;
    private int rotation = 0;
    private boolean flipH = false, flipV = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Could not set look and feel: " + e.getMessage());
            }
            new ImageSlideshowViewer().setVisible(true);
        });
    }

    public ImageSlideshowViewer() {
        setTitle("Advanced Image Slideshow Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        initComponents();
        setupLayout();
        setupEventHandlers();
        setupDragDrop();
        setupKeyBindings();

        slideshowTimer = new Timer(intervalSeconds * 1000, e -> nextImage());
    }

    private void initComponents() {
        // Image display panel
        imagePanel = new ImagePanel();
        imagePanel.setBackground(Color.BLACK);
        imagePanel.setPreferredSize(new Dimension(800, 500));

        // Control buttons
        loadBtn = new JButton("Load Folder");
        urlBtn = new JButton("Load URL");
        prevBtn = new JButton("Previous");
        playBtn = new JButton("Play");
        nextBtn = new JButton("Next");
        fullscreenBtn = new JButton("Fullscreen");

        // Sliders
        intervalSlider = new JSlider(1, 30, intervalSeconds);
        intervalSlider.setMajorTickSpacing(5);
        intervalSlider.setPaintTicks(true);
        intervalSlider.setPaintLabels(true);

        zoomSlider = new JSlider(10, 500, 100);
        zoomSlider.setMajorTickSpacing(100);
        zoomSlider.setPaintTicks(true);

        // Transition options
        transitionCombo = new JComboBox<>(new String[]{"Fade", "Slide Left", "Slide Right"});

        // Status and metadata
        statusLabel = new JLabel("Ready - Load images to start");
        metadataLabel = new JLabel("<html><body style='width: 200px'>No image loaded</body></html>");

        // Panels
        controlPanel = new JPanel(new FlowLayout());
        metadataPanel = new JPanel(new BorderLayout());
        metadataPanel.setPreferredSize(new Dimension(220, 0));
        metadataPanel.setBorder(BorderFactory.createTitledBorder("Image Info"));
        metadataPanel.add(new JScrollPane(metadataLabel), BorderLayout.CENTER);
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Control panel
        controlPanel.add(loadBtn);
        controlPanel.add(urlBtn);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(prevBtn);
        controlPanel.add(playBtn);
        controlPanel.add(nextBtn);
        controlPanel.add(fullscreenBtn);
        controlPanel.add(new JLabel(" Interval:"));
        controlPanel.add(intervalSlider);
        controlPanel.add(new JLabel(" Transition:"));
        controlPanel.add(transitionCombo);
        controlPanel.add(new JLabel(" Zoom:"));
        controlPanel.add(zoomSlider);

        // Editing panel
        JPanel editPanel = new JPanel(new FlowLayout());
        JButton cropBtn = new JButton("Crop");
        JButton rotateBtn = new JButton("Rotate");
        JButton flipHBtn = new JButton("Flip H");
        JButton flipVBtn = new JButton("Flip V");
        JButton wallpaperBtn = new JButton("Set Wallpaper");
        JButton resetBtn = new JButton("Reset");

        editPanel.add(cropBtn);
        editPanel.add(rotateBtn);
        editPanel.add(flipHBtn);
        editPanel.add(flipVBtn);
        editPanel.add(wallpaperBtn);
        editPanel.add(resetBtn);

        // Setup editing button actions
        cropBtn.addActionListener(e -> cropImage());
        rotateBtn.addActionListener(e -> rotateImage());
        flipHBtn.addActionListener(e -> flipImage(true, false));
        flipVBtn.addActionListener(e -> flipImage(false, true));
        wallpaperBtn.addActionListener(e -> setAsWallpaper());
        resetBtn.addActionListener(e -> resetImage());

        // Create a combined south panel for editing controls and status
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(editPanel, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        southPanel.add(statusPanel, BorderLayout.SOUTH);

        // Main layout
        add(controlPanel, BorderLayout.NORTH);
        add(imagePanel, BorderLayout.CENTER);
        add(metadataPanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);  // This was missing!
    }

    private void setupEventHandlers() {
        loadBtn.addActionListener(e -> loadFromFolder());
        urlBtn.addActionListener(e -> loadFromURL());
        prevBtn.addActionListener(e -> previousImage());
        nextBtn.addActionListener(e -> nextImage());
        playBtn.addActionListener(e -> toggleSlideshow());
        fullscreenBtn.addActionListener(e -> toggleFullscreen());

        intervalSlider.addChangeListener(e -> {
            intervalSeconds = intervalSlider.getValue();
            if (isPlaying) {
                slideshowTimer.setDelay(intervalSeconds * 1000);
            }
        });

        zoomSlider.addChangeListener(e -> {
            zoomFactor = zoomSlider.getValue() / 100.0;
            imagePanel.repaint();
        });

        transitionCombo.addActionListener(e ->
                transitionType = (String) transitionCombo.getSelectedItem());

        // Mouse wheel zoom
        imagePanel.addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoomFactor = Math.min(zoomFactor * 1.1, 5.0);
            } else {
                zoomFactor = Math.max(zoomFactor / 1.1, 0.1);
            }
            zoomSlider.setValue((int)(zoomFactor * 100));
            imagePanel.repaint();
        });

        // Image dragging
        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
            }
        });

        imagePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    imageOffset.x += e.getX() - dragStart.x;
                    imageOffset.y += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    imagePanel.repaint();
                }
            }
        });
    }

    private void setupDragDrop() {
        new DropTarget(this, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent e) {
                e.acceptDrag(DnDConstants.ACTION_COPY);
            }
            @Override
            public void dragOver(DropTargetDragEvent e) {}
            @Override
            public void dropActionChanged(DropTargetDragEvent e) {}
            @Override
            public void dragExit(DropTargetEvent e) {}

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable t = e.getTransferable();
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    loadFiles(files);
                    e.dropComplete(true);
                } catch (Exception ex) {
                    showError("Drag & Drop Error", ex.getMessage());
                    e.dropComplete(false);
                }
            }
        });
    }

    private void setupKeyBindings() {
        JRootPane root = getRootPane();

        // Navigation keys
        root.registerKeyboardAction(e -> previousImage(),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> nextImage(),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> toggleSlideshow(),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> toggleFullscreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> System.exit(0),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Zoom controls
        root.registerKeyboardAction(e -> zoomIn(),
                KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> zoomIn(),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> zoomIn(),
                KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.SHIFT_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> zoomOut(),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> resetZoom(),
                KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Ctrl+Plus and Ctrl+Minus for zoom (common shortcuts)
        root.registerKeyboardAction(e -> zoomIn(),
                KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> zoomIn(),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> zoomOut(),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> resetZoom(),
                KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }


    // Zoom control methods
    private void zoomIn() {
        zoomFactor = Math.min(zoomFactor * 1.2, 5.0); // Max zoom 5x
        zoomSlider.setValue((int)(zoomFactor * 100));
        imagePanel.repaint();
    }

    private void zoomOut() {
        zoomFactor = Math.max(zoomFactor / 1.2, 0.1); // Min zoom 0.1x
        zoomSlider.setValue((int)(zoomFactor * 100));
        imagePanel.repaint();
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        zoomSlider.setValue(100);
        imageOffset = new Point(0, 0); // Reset pan offset as well
        imagePanel.repaint();
    }

    private void loadFromFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    loadImagesFromFolder(folder);
                    return null;
                }
                @Override
                protected void done() {
                    if (!imageFiles.isEmpty()) {
                        currentIndex = 0;
                        displayCurrentImage();
                    }
                }
            };
            worker.execute();
        }
    }

    private void loadFromURL() {
        String url = JOptionPane.showInputDialog(this, "Enter image URL:", "Load from URL",
                JOptionPane.QUESTION_MESSAGE);
        if (url != null && !url.trim().isEmpty()) {
            SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
                @Override
                protected BufferedImage doInBackground() throws Exception {
                    return ImageIO.read(new URL(url.trim()));
                }
                @Override
                protected void done() {
                    try {
                        BufferedImage img = get();
                        if (img != null) {
                            imageFiles.clear();
                            imageFiles.add(new ImageFile(url, img));
                            currentIndex = 0;
                            displayCurrentImage();
                        }
                    } catch (Exception e) {
                        showError("URL Load Error", "Failed to load image from URL: " + e.getMessage());
                    }
                }
            };
            worker.execute();
        }
    }

    private void loadFiles(List<File> files) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (File file : files) {
                    if (file.isDirectory()) {
                        loadImagesFromFolder(file);
                    } else if (isImageFile(file)) {
                        try {
                            BufferedImage img = ImageIO.read(file);
                            if (img != null) {
                                imageFiles.add(new ImageFile(file.getAbsolutePath(), img));
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to load: " + file.getName());
                        }
                    }
                }
                return null;
            }
            @Override
            protected void done() {
                if (!imageFiles.isEmpty()) {
                    currentIndex = 0;
                    displayCurrentImage();
                }
            }
        };
        worker.execute();
    }

    private void loadImagesFromFolder(File folder) {
        try {
            Files.walk(folder.toPath())
                    .filter(Files::isRegularFile)
                    .filter(p -> isImageFile(p.toFile()))
                    .forEach(path -> {
                        try {
                            BufferedImage img = ImageIO.read(path.toFile());
                            if (img != null) {
                                imageFiles.add(new ImageFile(path.toString(), img));
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to load: " + path.getFileName());
                        }
                    });

            imageFiles.sort((a, b) -> a.path.compareToIgnoreCase(b.path));

        } catch (Exception e) {
            showError("Folder Load Error", "Failed to load images from folder: " + e.getMessage());
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".tiff");
    }

    private void displayCurrentImage() {
        if (imageFiles.isEmpty()) return;

        ImageFile imgFile = imageFiles.get(currentIndex);
        originalImage = imgFile.image;
        resetEditingState();
        applyEdits();

        updateMetadata(imgFile);
        statusLabel.setText(String.format("Image %d of %d - %s",
                currentIndex + 1, imageFiles.size(),
                Paths.get(imgFile.path).getFileName().toString()));

        if (transitionType.equals("Fade")) {
            fadeTransition();
        } else {
            slideTransition();
        }
    }

    private void fadeTransition() {
        Timer fadeTimer = new Timer(20, null);
        final float[] alpha = {0.0f};

        fadeTimer.addActionListener(e -> {
            alpha[0] += 0.1f;
            if (alpha[0] >= 1.0f) {
                alpha[0] = 1.0f;
                fadeTimer.stop();
            }
            imagePanel.setAlpha(alpha[0]);
            imagePanel.repaint();
        });

        fadeTimer.start();
    }

    private void slideTransition() {
        // Simple slide transition by resetting position
        imageOffset = new Point(0, 0);
        imagePanel.setAlpha(1.0f);
        imagePanel.repaint();
    }

    private void updateMetadata(ImageFile imgFile) {
        StringBuilder meta = new StringBuilder("<html><body style='width: 180px'>");

        try {
            // Basic info
            File file = imgFile.path.startsWith("http") ? null : new File(imgFile.path);
            meta.append("<b>File:</b><br>").append(Paths.get(imgFile.path).getFileName().toString()).append("<br><br>");

            if (originalImage != null) {
                meta.append("<b>Dimensions:</b><br>")
                        .append(originalImage.getWidth()).append(" × ").append(originalImage.getHeight()).append("<br><br>");
            }

            if (file != null && file.exists()) {
                meta.append("<b>Size:</b><br>").append(formatFileSize(file.length())).append("<br><br>");
                meta.append("<b>Modified:</b><br>")
                        .append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified())))
                        .append("<br><br>");

                // EXIF data
                String exif = extractExifData(file);
                if (!exif.isEmpty()) {
                    meta.append("<b>EXIF:</b><br>").append(exif);
                }
            }

        } catch (Exception e) {
            meta.append("Error reading metadata");
        }

        meta.append("</body></html>");
        metadataLabel.setText(meta.toString());
    }

    private String extractExifData(File file) {
        StringBuilder exif = new StringBuilder();
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(iis);
                IIOMetadata metadata = reader.getImageMetadata(0);

                if (metadata != null) {
                    String[] names = metadata.getMetadataFormatNames();
                    for (String name : names) {
                        displayMetadata(metadata.getAsTree(name), exif, 0);
                    }
                }
                reader.dispose();
            }
        } catch (Exception e) {
            // EXIF extraction failed - common for many images
        }

        return exif.length() > 0 ? exif.toString() : "No EXIF data available";
    }

    private void displayMetadata(Node node, StringBuilder sb, int level) {
        if (level > 3 || sb.length() > 500) return; // Limit depth and length

        NamedNodeMap map = node.getAttributes();
        if (map != null) {
            for (int i = 0; i < Math.min(map.getLength(), 5); i++) {
                Node attr = map.item(i);
                if (attr.getNodeName().contains("DateTime") ||
                        attr.getNodeName().contains("Model") ||
                        attr.getNodeName().contains("Make")) {
                    sb.append(attr.getNodeName()).append(": ").append(attr.getNodeValue()).append("<br>");
                }
            }
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            displayMetadata(child, sb, level + 1);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void previousImage() {
        if (imageFiles.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imageFiles.size()) % imageFiles.size();
        displayCurrentImage();
    }

    private void nextImage() {
        if (imageFiles.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imageFiles.size();
        displayCurrentImage();
    }

    private void toggleSlideshow() {
        if (imageFiles.isEmpty()) return;

        isPlaying = !isPlaying;
        playBtn.setText(isPlaying ? "Pause" : "Play");

        if (isPlaying) {
            slideshowTimer.setDelay(intervalSeconds * 1000);
            slideshowTimer.start();
        } else {
            slideshowTimer.stop();
        }
    }

    private void toggleFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (!isFullscreen) {
            dispose();
            setUndecorated(true);
            device.setFullScreenWindow(this);
            isFullscreen = true;
            controlPanel.setVisible(false);
            metadataPanel.setVisible(false);
        } else {
            device.setFullScreenWindow(null);
            setUndecorated(false);
            isFullscreen = false;
            controlPanel.setVisible(true);
            metadataPanel.setVisible(true);
            setVisible(true);
        }
    }

    // Image editing methods
    private void cropImage() {
        if (originalImage == null) return;

        // Simple crop dialog
        String input = JOptionPane.showInputDialog(this,
                "Enter crop dimensions (x,y,width,height):", "100,100,400,300");
        if (input != null) {
            try {
                String[] parts = input.split(",");
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int w = Integer.parseInt(parts[2].trim());
                int h = Integer.parseInt(parts[3].trim());

                if (x >= 0 && y >= 0 && x + w <= originalImage.getWidth() && y + h <= originalImage.getHeight()) {
                    editedImage = originalImage.getSubimage(x, y, w, h);
                    imagePanel.repaint();
                }
            } catch (Exception e) {
                showError("Crop Error", "Invalid crop parameters");
            }
        }
    }

    private void rotateImage() {
        rotation = (rotation + 90) % 360;
        applyEdits();
        imagePanel.repaint();
    }

    private void flipImage(boolean horizontal, boolean vertical) {
        if (horizontal) flipH = !flipH;
        if (vertical) flipV = !flipV;
        applyEdits();
        imagePanel.repaint();
    }

    private void resetImage() {
        resetEditingState();
        applyEdits();
        imagePanel.repaint();
    }

    private void resetEditingState() {
        rotation = 0;
        flipH = false;
        flipV = false;
        editedImage = null;
        zoomFactor = 1.0;
        zoomSlider.setValue(100);
        imageOffset = new Point(0, 0);
    }

    private void applyEdits() {
        if (originalImage == null) return;

        editedImage = deepCopy(originalImage);

        // Apply rotation
        if (rotation != 0) {
            editedImage = rotateImageTransform(editedImage, rotation);
        }

        // Apply flips
        if (flipH || flipV) {
            editedImage = flipImageTransform(editedImage, flipH, flipV);
        }
    }

    private BufferedImage deepCopy(BufferedImage original) {
        BufferedImage copy = new BufferedImage(original.getWidth(), original.getHeight(), original.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        return copy;
    }

    private BufferedImage rotateImageTransform(BufferedImage img, int degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (int) Math.floor(w * cos + h * sin);
        int newH = (int) Math.floor(h * cos + w * sin);

        BufferedImage result = new BufferedImage(newW, newH, img.getType());
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        AffineTransform at = new AffineTransform();
        at.translate((newW - w) / 2.0, (newH - h) / 2.0);
        at.rotate(radians, w / 2.0, h / 2.0);
        g.setTransform(at);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return result;
    }

    private BufferedImage flipImageTransform(BufferedImage img, boolean horizontal, boolean vertical) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage result = new BufferedImage(w, h, img.getType());
        Graphics2D g = result.createGraphics();

        AffineTransform at = new AffineTransform();
        if (horizontal) {
            at.concatenate(AffineTransform.getScaleInstance(-1, 1));
            at.concatenate(AffineTransform.getTranslateInstance(-w, 0));
        }
        if (vertical) {
            at.concatenate(AffineTransform.getScaleInstance(1, -1));
            at.concatenate(AffineTransform.getTranslateInstance(0, -h));
        }

        g.setTransform(at);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return result;
    }

    private void setAsWallpaper() {
        if (editedImage == null) return;

        try {
            // Save temp image
            File tempFile = File.createTempFile("wallpaper", ".png");
            ImageIO.write(editedImage, "png", tempFile);

            // Set as wallpaper (Windows)
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("reg add \"HKEY_CURRENT_USER\\Control Panel\\Desktop\" /v Wallpaper /t REG_SZ /d \"" +
                        tempFile.getAbsolutePath() + "\" /f");
                Runtime.getRuntime().exec("RUNDLL32.EXE user32.dll,UpdatePerUserSystemParameters");
                JOptionPane.showMessageDialog(this, "Wallpaper set successfully!");
            } else {
                JOptionPane.showMessageDialog(this, "Wallpaper setting is currently supported on Windows only.\nImage saved to: " + tempFile.getAbsolutePath());
            }
        } catch (Exception e) {
            showError("Wallpaper Error", "Failed to set wallpaper: " + e.getMessage());
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    // Custom image panel with smooth rendering
    private class ImagePanel extends JPanel {
        private float alpha = 1.0f;

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (editedImage == null) {
                g.setColor(Color.GRAY);
                g.drawString("No image loaded", getWidth()/2 - 50, getHeight()/2);
                return;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            // Calculate scaled dimensions
            double imgW = editedImage.getWidth() * zoomFactor;
            double imgH = editedImage.getHeight() * zoomFactor;

            // Center image
            int x = (int) ((getWidth() - imgW) / 2 + imageOffset.x);
            int y = (int) ((getHeight() - imgH) / 2 + imageOffset.y);

            g2d.drawImage(editedImage, x, y, (int) imgW, (int) imgH, null);
            g2d.dispose();
        }
    }

    // Image file container
    private static class ImageFile {
        final String path;
        final BufferedImage image;

        ImageFile(String path, BufferedImage image) {
            this.path = path;
            this.image = image;
        }
    }
}