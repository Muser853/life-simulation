import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableFloatArray;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.ObservableFaceArray;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

public class LifeSimulation0 extends Application {
    private static int CHART_WIDTH = 1000, CHART_HEIGHT = 2000, MIN_SIZE = 1, SLEEP_TIME = 1, CELL_SIZE = 8, steps = 1024, max = 9;
    private static String OUTPUT_DIR = "simulation_results";
    private int frameCount = 0;
    private File videoDir;
    private AtomicBoolean isRecording = new AtomicBoolean(false); // recording status
    private boolean isSimulationRunning = false;
    private Thread visualSimulationThread;
    private Map<String, LineChart<Number, Number>> charts;
    private Landscape currentLandscape;
    private Canvas simulationCanvas;
    private VBox mainLayout;
    private Map<String, double[]> simulationCache = new HashMap<>();

    private synchronized void startRecording() { // synchronized
        if (isRecording.get()) return; // avoid repetitive starts
        isRecording.set(true);
        frameCount = 0;

        // create video directory
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        videoDir = new File("video_frames_" + timestamp);
        if (!videoDir.exists()) videoDir.mkdir();

        // start recording thread
        Thread recordingThread = new Thread(() -> {
            while (isRecording.get()) {
                try {
                    // capture current GUI snapshot
                    WritableImage snapshot = simulationCanvas.snapshot(null, null);
                    BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);

                    //save frame
                    File frameFile = new File(videoDir, String.format("frame_%05d.png", frameCount));
                    ImageIO.write(bufferedImage, "png", frameFile);
                    frameCount++;

                    // control frame rate
                    Thread.sleep(1000 / 30);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        recordingThread.setDaemon(true);
        recordingThread.start();
    }

    private synchronized void stopRecording() { // synchronized for thread safety
        if (!isRecording.get()) return; // avoid repetitive stops
        isRecording.set(false);

        // usibng FFmpeg to synthesize video
        try {
            String outputVideo = "output_video_" + System.currentTimeMillis() + ".mp4";
            ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-framerate", "30",
                "-i", videoDir.getAbsolutePath() + "/frame_%05d.png", // input frame
                "-c:v", "libx264", // video encoder
                "-pix_fmt", "yuv420p", // pixel format
                outputVideo
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.waitFor(); // wait for FFmpeg to finish

            System.out.println("Video saved to: " + outputVideo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long simulateSingleRun(Landscape landscape) {
        for (int step = 0; step < steps; step++)
            landscape.advance();
        
        return landscape.countLivingCells();
    }
    
    private double[] simulateForChance(int m, int n, double chance) {
        String cacheKey = m + "," + n + "," + chance;
        if (simulationCache.containsKey(cacheKey))
            return simulationCache.get(cacheKey);

        long totalLivingCells = 0, totalSquared = 0;
        int r = m * n;
        for (int rep = 0; rep < r; rep++) {
            Landscape landscape = new Landscape(m, n, chance);
            long livingCells = simulateSingleRun(landscape);
            totalLivingCells += livingCells;
            totalSquared += livingCells * livingCells;
        }
        double avg = totalLivingCells / ((double)r * steps);
        double stdv = Math.sqrt((totalSquared / ((double)r * steps)) - (avg * avg));
        
        double[] result = new double[]{avg, stdv};
        simulationCache.put(cacheKey, result); // Cache the result
        return result;
    }

    private void updateChart(String key, double chance, double[] data) {
        double avg = data[0];
        double stdv = data[1];
        LineChart<Number, Number> chart = charts.get(key);
        chart.setLegendVisible(true);

        XYChart.Series<Number, Number> mainSeries = chart.getData().get(0);
        XYChart.Series<Number, Number> upperSeries = chart.getData().get(1);
        XYChart.Series<Number, Number> lowerSeries = chart.getData().get(2);

        double lower = avg - stdv;
        double upper = avg + stdv;
        lowerSeries.getData().add(new XYChart.Data<>(chance, lower));
        upperSeries.getData().add(new XYChart.Data<>(chance, upper));
        mainSeries.getData().add(new XYChart.Data<>(chance, avg));
        mainSeries.setName("Average");
        upperSeries.setName("Avg + Std");
        lowerSeries.setName("Avg - Std");
    }

    private void show3DStage(Group root3D) {
        Stage stage = new Stage();
        Scene scene = new Scene(root3D, 800, 600, true);
        scene.setCamera(new PerspectiveCamera(true));

        Button saveButton = new Button("Save 3D Model");
        saveButton.setOnAction(_ -> save3DModel(root3D, "model.stl"));

        StackPane root = new StackPane(root3D);
        HBox controls = new HBox(saveButton);
        controls.setPadding(new Insets(10));
        root.getChildren().add(controls);
        scene.setRoot(root);

        stage.setScene(scene);
        stage.show();
    }

    private byte[] floatToByteArray(float value) {
        ByteBuffer buffer = ByteBuffer.allocate(4).putFloat(value);
        buffer.flip();
        return buffer.array();
    }

    private byte[] intToByteArray(int value) {
        return new byte[]{
            (byte) (value >> 0),
            (byte) (value >> 8),
            (byte) (value >> 16),
            (byte) (value >> 24)
        };
    }

    private void save3DModel(Group root, String filename) {
        try (FileOutputStream fos = new FileOutputStream(filename);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            byte[] header = "LifeSimulation1 STL Export".getBytes();
            bos.write(header);
            bos.write(new byte[80 - header.length]); // fill in 0s

            int triangleCount = 0;
            for (Node node : root.getChildren()) {
                if (node instanceof MeshView) {
                    MeshView meshView = (MeshView) node;
                    TriangleMesh mesh = (TriangleMesh) meshView.getMesh();
                    triangleCount += mesh.getFaces().size() / 6;
                }
            }
            bos.write(intToByteArray(triangleCount));

            // Write the number of triangles (4 bytes)
            for (Node node : root.getChildren()) {
                if (node instanceof MeshView) {
                    MeshView meshView = (MeshView) node;
                    TriangleMesh mesh = (TriangleMesh) meshView.getMesh();
                    ObservableFloatArray observablePoints = mesh.getPoints();
                    float[] points = new float[observablePoints.size()];

                    for (int i = 0; i < observablePoints.size(); i++)
                        points[i] = observablePoints.get(i);

                    for (int i = 0; i < mesh.getFaces().size(); i += 6) {
                        // calculate normal vector
                        int v0 = mesh.getFaces().get(i);
                        int v1 = mesh.getFaces().get(i + 2);
                        int v2 = mesh.getFaces().get(i + 4);
                        float[] normal = calculateNormal(
                            points[v0 * 3], points[v0 * 3 + 1], points[v0 * 3 + 2],
                            points[v1 * 3], points[v1 * 3 + 1], points[v1 * 3 + 2],
                            points[v2 * 3], points[v2 * 3 + 1], points[v2 * 3 + 2]
                        );

                        bos.write(floatToByteArray(normal[0]));
                        bos.write(floatToByteArray(normal[1]));
                        bos.write(floatToByteArray(normal[2]));

                        for (int j = 0; j < 3; j++) {
                            int vertexIndex = mesh.getFaces().get(i + j * 2);
                            bos.write(floatToByteArray(points[vertexIndex * 3]));
                            bos.write(floatToByteArray(points[vertexIndex * 3 + 1]));
                            bos.write(floatToByteArray(points[vertexIndex * 3 + 2]));
                        }
                        bos.write(new byte[]{0, 0});
                    }
                }
            }
            bos.flush();
            System.out.println("Saved 3D model to: " + filename);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void createSurfaceVisualization(Group root3D, Map<String, Map<Double, double[]>> allData) {
        TriangleMesh mesh = new TriangleMesh();
        ObservableFloatArray points = mesh.getPoints();
        ObservableFaceArray faces = mesh.getFaces();

        int gridSize = max - MIN_SIZE + 1;
        float[] vertices = new float[gridSize * gridSize * 3];
        int vertexIndex = 0;

        // Populate vertices (x, y, z)
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                double avgLivingCells = allData.get(m + "," + n).values().stream()
                    .mapToDouble(data -> data[0])
                    .average()
                    .orElse(0.0);

                vertices[vertexIndex++] = (m - MIN_SIZE) * 100f; // x-coordinate
                vertices[vertexIndex++] = (n - MIN_SIZE) * 100f; // y-coordinate
                vertices[vertexIndex++] = (float) (avgLivingCells * 10); // z-coordinate (scaled)
            }
        }
        points.addAll(vertices);

        // Populate faces (triangles)
        for (int i = 0; i < gridSize - 1; i++) {
            for (int j = 0; j < gridSize - 1; j++) {
                int p00 = i * gridSize + j;
                int p10 = p00 + 1;
                int p01 = p00 + gridSize;
                int p11 = p01 + 1;

                // First triangle
                faces.addAll(p00, 0, p10, 0, p01, 0);
                // Second triangle
                faces.addAll(p10, 0, p11, 0, p01, 0);
            }
        }

        MeshView meshView = new MeshView(mesh);
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(Color.BLUE);
        material.setSpecularColor(Color.WHITE);
        meshView.setMaterial(material);
        root3D.getChildren().add(meshView);
    }

    private float[] calculateNormal(float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        float[] v1 = {x2 - x1, y2 - y1, z2 - z1};
        float[] v2 = {x3 - x1, y3 - y1, z3 - z1};
        float[] normal = {
            v1[1] * v2[2] - v1[2] * v2[1],
            v1[2] * v2[0] - v1[0] * v2[2],
            v1[0] * v2[1] - v1[1] * v2[0]
        };
        float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        normal[0] /= length;
        normal[1] /= length;
        normal[2] /= length;
        return normal;
    }

    private Group initialize3DRoot() {
        Group root = new Group();
        AmbientLight light = new AmbientLight(Color.WHITE);
        root.getChildren().add(light);
        return root;
    }

    // construct 3d visualization
    private void create3DVisualizations(Group root3D, Map<String, Map<Double, double[]>> allData) {
        // 3d scatter plots
        for (Map.Entry<String, Map<Double, double[]>> entry : allData.entrySet()) {
            String key = entry.getKey();
            int m = Integer.parseInt(key.split(",")[0]);
            int n = Integer.parseInt(key.split(",")[1]);
            for (Map.Entry<Double, double[]> dataEntry : entry.getValue().entrySet()) {
                double chance = dataEntry.getKey();
                double avg = dataEntry.getValue()[0];
                double var = dataEntry.getValue()[1];

                Sphere sphere = new Sphere(2);
                sphere.setTranslateX(m * 10);
                sphere.setTranslateY(n * 10);
                sphere.setTranslateZ((float) avg);
                sphere.setMaterial(new PhongMaterial(Color.color(1 - var/2, var/2, 0)));
                root3D.getChildren().add(sphere);
                double[][] zValues = generateZValuesForSurface(chance, allData);
                MeshView meshView = createSurfaceMesh(zValues, MIN_SIZE, MIN_SIZE);
                root3D.getChildren().add(meshView);
            }
        }// generate 3d curvature surface        
    }

    // generate z values for curvature surface
    private double[][] generateZValuesForSurface(double chance, Map<String, Map<Double, double[]>> allData) {
        int size = max - MIN_SIZE + 1;
        double[][] zValues = new double[size][size];
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                String key = m + "," + n;
                Map<Double, double[]> data = allData.get(key);
                if (data != null) {
                    double[] result = data.get(chance);
                    if (result != null) {
                        zValues[m - MIN_SIZE][n - MIN_SIZE] = result[0];
                    }
                }
            }
        }
        return zValues;
    }

    // construct curvature surface network
    private MeshView createSurfaceMesh(double[][] zValues, int startX, int startY) {
        int width = zValues.length;
        int height = zValues[0].length;
        TriangleMesh mesh = new TriangleMesh();

        float[] vertices = new float[width * height * 3];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                double x = startX + i;
                double y = startY + j;
                double z = zValues[i][j];
                vertices[i * height * 3 + j * 3] = (float) x;
                vertices[i * height * 3 + j * 3 + 1] = (float) y;
                vertices[i * height * 3 + j * 3 + 2] = (float) z;
            }
        }
        mesh.getPoints().setAll(vertices);

        // Construct triangle surfaces
        ShortBuffer indices = ShortBuffer.allocate((width - 1) * (height - 1) * 6);
        for (int i = 0; i < width - 1; i++) {
            for (int j = 0; j < height - 1; j++) {
                int a = i * height + j;
                int b = i * height + (j + 1);
                int c = (i + 1) * height + j;
                int d = (i + 1) * height + (j + 1);
                // First triangle
                indices.put((short) a);
                indices.put((short) b);
                indices.put((short) d);
                // Second triangle
                indices.put((short) a);
                indices.put((short) d);
                indices.put((short) c);
            }
        }
        indices.flip();

        int[] indicesArray = new int[indices.remaining()];
        for (int i = 0; indices.hasRemaining(); i++) 
            indicesArray[i] = indices.get() & 0xFFFF; // Convert to unsigned integer
        
        mesh.getFaces().setAll(indicesArray);

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(new PhongMaterial(Color.BLUE));
        return meshView;
    }

    private void saveSimulationState(String prefix) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();
        
        String filename = dirName + "/" + prefix + ".png";
        WritableImage image = simulationCanvas.snapshot(null, null);
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(filename));
        System.out.println("Saved simulation state to: " + filename);
    }

    private void runVisualSimulation() {
        if (visualSimulationThread != null && visualSimulationThread.isAlive())
            return;
    
        visualSimulationThread = new Thread(() -> {
            isSimulationRunning = true;
    
            // Save initial state
            Platform.runLater(() -> {
                currentLandscape.draw(simulationCanvas.getGraphicsContext2D(), CELL_SIZE);
                try { saveSimulationState("initial_state"); } catch (IOException e) {}
            });
    
            // Run one step and save
            currentLandscape.advance();
            Platform.runLater(() -> {
                currentLandscape.draw(simulationCanvas.getGraphicsContext2D(), CELL_SIZE);
                try { saveSimulationState("after_one_step"); } catch (IOException e) {}
            });
    
            // Continue simulation
            for (int i = 2; i < steps && isSimulationRunning; i++) {
                currentLandscape.advance();
                Platform.runLater(() -> currentLandscape.draw(simulationCanvas.getGraphicsContext2D(), CELL_SIZE));
                try { Thread.sleep(SLEEP_TIME); } catch (InterruptedException e) { break; }
            }
            isSimulationRunning = false;
        });
        visualSimulationThread.setDaemon(true);
        visualSimulationThread.start();
    }
    
    private void saveChart(LineChart<Number, Number> chart, String filename) {
        File file = new File(filename);
        System.out.println("Saved chart to: " + file.getAbsolutePath());
    }
    private void saveAllCharts(Map<String, LineChart<Number, Number>> charts) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();
        
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String dimensions = entry.getKey().replace(',', 'x');
            String filename = dirName + "/landscape_" + dimensions + ".png";
            saveChart(entry.getValue(), filename);
        }
    }

    @Override
    public void start(Stage stage) {
    // Initialize components
        currentLandscape = new Landscape(max, max, 0.5);
        Canvas simulationCanvas = new Canvas(max * CELL_SIZE, max * CELL_SIZE);
        GraphicsContext gc = simulationCanvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        charts = new HashMap<>();
        ProgressBar progressBar = new ProgressBar(0);

        int totalTasks = (max - MIN_SIZE + 1)*(max - MIN_SIZE + 1)*21;
        AtomicInteger progress = new AtomicInteger(0);
        Platform.runLater(() -> progressBar.setProgress((double)progress.get()/totalTasks));

        progress.incrementAndGet();

    // Create control panel
        Button startSimButton = new Button("Start Simulation");
        Button stopSimButton = new Button("Stop Simulation");
        Button resetSimButton = new Button("Reset Simulation");
        Button saveButton = new Button("Save All");

    // Enhanced Slider Configuration
        Slider chanceSlider = new Slider(0, 1, 0.5);
        chanceSlider.setShowTickLabels(true);
        chanceSlider.setShowTickMarks(true);
        chanceSlider.setMajorTickUnit(0.25);
        chanceSlider.setMinorTickCount(4);
        chanceSlider.setBlockIncrement(0.05);
        chanceSlider.setPrefWidth(400);

    // Label to display the current chance value
        Label chanceValueLabel = new Label("Current Chance: 0.5");

    // Listener to update the label and 3D graphs
        chanceSlider.valueProperty().addListener((_, _, newValue) -> {
            double chance = Math.round(newValue.doubleValue() * 20) / 20.0;
            chanceValueLabel.setText(String.format("Current Chance: %.2f", chance));
            Platform.runLater(() -> update3DGraphs(chance));
        });

        startSimButton.setOnAction(_ -> {
            runVisualSimulation();
            startRecording();
        });
        stopSimButton.setOnAction(_ -> {
            isSimulationRunning = false;
            stopRecording();
        });
        resetSimButton.setOnAction(_ -> {
            isSimulationRunning = false;
            currentLandscape = new Landscape(max, max, 0.5);
            currentLandscape.draw(simulationCanvas.getGraphicsContext2D(), CELL_SIZE);
        });
        saveButton.setOnAction(_ -> {
            saveAllCharts(charts);
            try { saveSimulationState("current_state"); } catch (IOException e1) {}
        });
    // Layout setup
        HBox buttonPane = new HBox(10);
        buttonPane.getChildren().addAll(startSimButton, stopSimButton, resetSimButton, saveButton, chanceValueLabel);
        buttonPane.setPadding(new Insets(5));

        VBox sliderBox = new VBox(5);
        sliderBox.getChildren().addAll(chanceSlider);
        sliderBox.setPadding(new Insets(10, 10, 5, 10));
        sliderBox.setAlignment(Pos.CENTER);

        VBox simulationPane = new VBox(10);
        simulationPane.getChildren().addAll(simulationCanvas, buttonPane, sliderBox);
        simulationPane.setPadding(new Insets(10));

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10));

        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                final NumberAxis xAxis = new NumberAxis(0, 1, 0.05);
                final NumberAxis yAxis = new NumberAxis(0, 10, 1);
                xAxis.setLabel("Chance");
                yAxis.setLabel("Average Living Cells");

                LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
                chart.setTitle(m + "x" + n + " Landscape");
                chart.setCreateSymbols(false);
                chart.setPrefSize(CHART_WIDTH, CHART_HEIGHT);

                XYChart.Series<Number, Number> mainSeries = new XYChart.Series<>();
                XYChart.Series<Number, Number> upperSeries = new XYChart.Series<>();
                XYChart.Series<Number, Number> lowerSeries = new XYChart.Series<>(); 

                upperSeries.getNode().setStyle("-fx-stroke: gray; -fx-stroke-dash-array: 0.5 0.5;");
                lowerSeries.getNode().setStyle("-fx-stroke: gray; -fx-stroke-dash-array: 0.5 0.5;");
                mainSeries.getNode().setStyle("-fx-stroke: blue;");
                
                @SuppressWarnings("unchecked")
                XYChart.Series<Number, Number>[] seriesArray = 
                    new XYChart.Series[]{mainSeries, upperSeries, lowerSeries};
                chart.getData().addAll(seriesArray);

                String key = m + "," + n;
                charts.put(key, chart);
                    gridPane.add(chart, n , m );
            }
        }

        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setCenter(scrollPane);
        mainLayout.setTop(simulationPane);

    // Precompute the 3D model data
        Map<String, Map<Double, double[]>> allData = collectSimulationData();
        Group root3D = initialize3DRoot();
        create3DVisualizations(root3D, allData);
        show3DStage(root3D);

        startChartsSimulation();

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.setCamera(new PerspectiveCamera());
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setTitle("Life Simulation - Visualization and Analysis");
        stage.show();

    // Initial draw
        currentLandscape.draw(simulationCanvas.getGraphicsContext2D(), CELL_SIZE);
    }
    // update 3d graphs in response to the sliding bar
    private void update3DGraphs(double chance) {
        Group root3D = initialize3DRoot();
        
        // Initialize allData with simulation results from cache or new simulations
        Map<String, Map<Double, double[]>> allData = new HashMap<>();
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                String key = m + "," + n;
                Map<Double, double[]> chanceData = new HashMap<>();

                // Populate data for all possible chances (0.0 to 1.0 with step 0.05)
                for (double c = 0.0; c <= 1.0; c += 0.05) {
                    String cacheKey = m + "," + n + "," + c;
                    double[] data = simulationCache.getOrDefault(cacheKey, simulateForChance(m, n, c));
                    chanceData.put(c, data);
                }
                allData.put(key, chanceData);
            }
        }

        // Create surface visualization using complete allData
        createSurfaceVisualization(root3D, allData);

        // Show 3D stage without saving models every time
        show3DStage(root3D);
    }

    private Map<String, Map<Double, double[]>> collectSimulationData() {
        Map<String, Map<Double, double[]>> allData = new ConcurrentHashMap<>();
        CompletableFuture<?>[] futures = new CompletableFuture[max - MIN_SIZE + 1];
        for (int m = MIN_SIZE; m <= max; m++) {
            final int finalM = m;
            futures[m - MIN_SIZE] = CompletableFuture.runAsync(() -> {
                for (int n = MIN_SIZE; n <= finalM; n++) {
                    final int finalN = n;
                    String key = finalM + "," + finalN;
                    Map<Double, double[]> chanceData = new HashMap<>();
                    allData.put(key, chanceData);

                    IntStream.rangeClosed(0, 20).parallel().forEach(i -> {
                        double chance = i * 0.05;
                        double[] results = simulateForChance(finalM, finalN, chance);
                        chanceData.put(chance, results);
                        Platform.runLater(() -> {
                            synchronized (charts) {
                                updateChart(key, chance, results);
                            }
                        });
                    });
                }
            });
        }
        CompletableFuture.allOf(futures).join();
        return allData;
    }

    private void startChartsSimulation() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Map<String, XYChart.Series<Number, Number>> series = new HashMap<>();
                // initialize 3d root node
                Group root3D = initialize3DRoot();
                Map<Double, TriangleMesh> meshes = new HashMap<>();
                
                // Initialize 2D series for each combination
                for (int m = MIN_SIZE; m <= max; m++) {
                    for (int n = MIN_SIZE; n <= m; n++) {
                        String key = m + "," + n;
                        XYChart.Series<Number, Number> newSeries = new XYChart.Series<>();
                        newSeries.setName(m + "x" + n);
                        series.put(key, newSeries);
                        
                        Platform.runLater(() -> {
                            charts.get(key).getData().add(series.get(key));
                        });
                    }
                }
            // collect data for 2D&3D visualization
                Map<String, Map<Double, double[]>> allData = collectSimulationData();

            // generate 3d contents
                Platform.runLater(() -> {
                    create3DVisualizations(root3D, allData);
                // show 3d window
                    show3DStage(root3D);
                });
                // Initialize 3D meshes for each chance value
                for (int i = 0; i <= 20; i++) {
                    double chance = i * 0.05;
                    TriangleMesh mesh = new TriangleMesh();
                    mesh.getPoints().addAll(new float[(max - MIN_SIZE + 1) * (max - MIN_SIZE + 1) * 3]);
                    mesh.getTexCoords().addAll(0, 0, 1, 1); // Basic texture coordinates
                    meshes.put(chance, mesh);
                }

                // Collect data for both 2D and 3D visualizations
                Map<String, Map<Double, Double>> allData2D = new HashMap<>();
                
                // For each grid size combination
                for (int m = MIN_SIZE; m <= max; m++) {
                    for (int n = MIN_SIZE; n <= m; n++) {
                        final int finalM = m, finalN = n; 
                        String key = m + "," + n;
                        Map<Double, Double> chanceData = new HashMap<>();
                        allData2D.put(key, chanceData);
                        int r = 2 * finalM * finalM;
                        
                        // For each chance value
                        IntStream.rangeClosed(0, 20).parallel().forEach(i -> { 
                            double chance = i * 0.05;
                            long totalLivingCells = 0;
                            
                        
                            for (int rep = 0; rep < r; rep++) {
                                Landscape landscape = new Landscape(finalM, finalN, chance);
                                for (int step = 0; step < steps; step++) {
                                    landscape.advance();
                                    totalLivingCells += landscape.countLivingCells();
                                }
                            }
                            final double averageLivingCells = totalLivingCells / ((double)r * steps);
                            chanceData.put(chance, averageLivingCells);
                        
                            Platform.runLater(() -> {
                                updateChart(key, chance, new double[]{averageLivingCells, 0});
                            });
                        });
                    }
                }
                // Create 3D visualizations using collected data
                for (int i = 0; i <= 20; i++) {
                    double chance = i * 0.05;
                    TriangleMesh mesh = meshes.get(chance);
                    
                    // Update mesh points
                    float[] points = new float[(max - MIN_SIZE + 1) * (max - MIN_SIZE + 1) * 3];
                    int pointIndex = 0;
                    double maxValue = allData2D.values().stream()
                        .mapToDouble(map -> map.get(chance))
                        .max()
                        .orElse(0.001);

                    for (int m = MIN_SIZE; m <= max; m++) {
                        for (int n = MIN_SIZE; n <= m; n++) {
                            String key = m + "," + n;
                            double value = allData2D.get(key).get(chance);
                            
                            points[pointIndex++] = (m - MIN_SIZE) * 100f;
                            points[pointIndex++] = (n - MIN_SIZE) * 100f;
                            points[pointIndex++] = (float)(value / maxValue * 300);
                        }
                    }
                    mesh.getPoints().setAll(points);
                    
                    // Create faces
                    int gridSize = max - MIN_SIZE + 1;
                    for (int m = 0; m < gridSize - 1; m++) {
                        for (int n = 0; n < gridSize - 1; n++) {
                            int p00 = m * gridSize + n;
                            int p10 = p00 + 1;
                            int p01 = p00 + gridSize;
                            int p11 = p01 + 1;
                            
                            mesh.getFaces().addAll(
                                p00, 0, p01, 0, p10, 0,
                                p10, 0, p01, 0, p11, 0
                            );
                        }
                    }
                    // Create and add MeshView
                    MeshView meshView = new MeshView(mesh);
                    PhongMaterial material = new PhongMaterial();
                    material.setDiffuseColor(Color.hsb(chance * 360, 0.8, 0.9));
                    material.setSpecularColor(Color.WHITE);
                    material.setSpecularPower(32);
                    meshView.setMaterial(material);
                    
                    // Position the mesh
                    int row = i / 5;
                    int col = i % 5;
                    meshView.setTranslateX(col * 800);
                    meshView.setTranslateY(row * 800);
                    
                    // Add rotation
                    Rotate rotateX = new Rotate(-30, Rotate.X_AXIS);
                    Rotate rotateY = new Rotate(45, Rotate.Y_AXIS);
                    meshView.getTransforms().addAll(rotateX, rotateY);
                    
                    root3D.getChildren().add(meshView);
                }
            // Create and add 3D scene to the main layout
                Platform.runLater(() -> {
                    PerspectiveCamera camera = new PerspectiveCamera(true);
                    camera.setTranslateX(2000);
                    camera.setTranslateY(-1000);    
                    camera.setTranslateZ(-4000);
                    camera.setRotationAxis(Rotate.X_AXIS);
                    camera.setRotate(-20);

                    javafx.scene.SubScene subScene = new javafx.scene.SubScene(
                        root3D, 4000, 3200, true, javafx.scene.SceneAntialiasing.BALANCED);
                    subScene.setFill(Color.LIGHTGRAY);
                    subScene.setCamera(camera);

                    ScrollPane scrollPane3D = new ScrollPane(subScene);
                    scrollPane3D.setPrefViewportHeight(1200);
                    scrollPane3D.setFitToWidth(true);
                    
                    mainLayout.getChildren().add(scrollPane3D);
                });
                return null;
            }
        };
        Thread simulationThread = new Thread(task);
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    public static void main(String[] args) {
        if (args.length >= 2) {        
            max = Integer.parseInt(args[0]);
            steps = Integer.parseInt(args[1]);
        
            if (args.length >= 3)
                OUTPUT_DIR=args[2];
        }
        launch(args);
    }
}