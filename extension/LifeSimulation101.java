import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableFloatArray;
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
import javafx.stage.Stage;
import javax.imageio.ImageIO;

public class LifeSimulation101 extends Application {
    private static int CHART_WIDTH = 1000, CHART_HEIGHT = 2000, MIN_SIZE = 1, SLEEP_TIME = 1, CELL_SIZE = 8, steps = 1024, max = 9;
    private static String OUTPUT_DIR = "simulation_results";
    private boolean isSimulationRunning = false;
    private Thread visualSimulationThread;
    private Map<String, LineChart<Number, Number>> charts;
    private Landscape currentLandscape;
    private Canvas simulationCanvas;
    private Map<String, double[]> simulationCache = new HashMap<>();

    private static final int DEFAULT_CHART_WIDTH = 1000;
    private static final int DEFAULT_CHART_HEIGHT = 2000;
    private static final int DEFAULT_MIN_SIZE = 1;
    private static final int DEFAULT_CELL_SIZE = 8;

    public LifeSimulation101(int chartWidth, int chartHeight, int minSize, int cellSize) {
        CHART_WIDTH = chartWidth > 0 ? chartWidth : DEFAULT_CHART_WIDTH;
        CHART_HEIGHT = chartHeight > 0 ? chartHeight : DEFAULT_CHART_HEIGHT;
        MIN_SIZE = minSize > 0 ? minSize : DEFAULT_MIN_SIZE;
        CELL_SIZE = cellSize > 0 ? cellSize : DEFAULT_CELL_SIZE;
    }

    // No-argument constructor
    public LifeSimulation101() {
        // You can initialize default values here if needed
    }

    @Override
    public void init() {
        // Initialization logic here
        currentLandscape = new Landscape(max, max, 0.5);
        simulationCanvas = createSimulationCanvas(max, max);
        charts = new HashMap<>();
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
        int r = 2 * m * n;
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

    private Canvas createSimulationCanvas(int width, int height) {
        Canvas canvas = new Canvas(width * CELL_SIZE, height * CELL_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        return canvas;
    }

    private void updateChart(String key, double chance, double[] data) {
        double avg = data[0];
        double stdv = data[1];
        LineChart<Number, Number> chart = charts.get(key);

        // Ensure the chart has enough series before accessing them
        while (chart.getData().size() < 3) {
            chart.getData().add(new XYChart.Series<>());
        }

        XYChart.Series<Number, Number> mainSeries = chart.getData().get(0);
        XYChart.Series<Number, Number> upperSeries = chart.getData().get(1);
        XYChart.Series<Number, Number> lowerSeries = chart.getData().get(2);

        double lower = avg - stdv;
        double upper = avg + stdv;

        lowerSeries.getData().add(new XYChart.Data<>(chance, lower));
        upperSeries.getData().add(new XYChart.Data<>(chance, upper));

        // Check if the main series already has the current chance value
        if (mainSeries.getData().isEmpty() || !mainSeries.getData().get(mainSeries.getData().size() - 1).getXValue().equals(chance)) {
            mainSeries.getData().add(new XYChart.Data<>(chance, avg));
        }
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

    // construct 3D visualization
    private void create3DVisualizations(Group root3D, Map<String, Map<Double, double[]>> allData) {
        // 3D scatter plots
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
                sphere.setMaterial(new PhongMaterial(Color.color(1 - var / 2, var / 2, 0)));
                root3D.getChildren().add(sphere);
                double[][] zValues = generateZValuesForSurface(chance, allData);
                MeshView meshView = createSurfaceMesh(zValues, MIN_SIZE, MIN_SIZE);
                root3D.getChildren().add(meshView);
            }
        } // generate 3D curvature surface        
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

    private void drawLandscape(Canvas canvas, Landscape landscape) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int i = 0; i < landscape.getRows(); i++) {
            for (int j = 0; j < landscape.getCols(); j++) {
                gc.setFill(landscape.getCell(i, j).getAlive() ? Color.BLACK : Color.WHITE);
                gc.fillRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                gc.strokeRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
    }

    private void saveSimulationState(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists())
            directory.mkdir();

        String filename = dirName + "/" + prefix + ".png";
        WritableImage image = simulationCanvas.snapshot(null, null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(filename));
            System.out.println("Saved simulation state to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving simulation state: " + e.getMessage());
        }
    }

    private void runVisualSimulation() {
        if (visualSimulationThread != null && visualSimulationThread.isAlive())
            return;

        visualSimulationThread = new Thread(() -> {
            isSimulationRunning = true;

            // Save initial state
            Platform.runLater(() -> {
                drawLandscape(simulationCanvas, currentLandscape);
                saveSimulationState("initial_state");
            });
            // Run one step and save
            currentLandscape.advance();
            Platform.runLater(() -> {
                drawLandscape(simulationCanvas, currentLandscape);
                saveSimulationState("after_one_step");
            });
            // Continue simulation
            for (int i = 2; i < steps && isSimulationRunning; i++) {
                currentLandscape.advance();
                Platform.runLater(() -> drawLandscape(simulationCanvas, currentLandscape));
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e) {
                    break;
                }
            }
            isSimulationRunning = false;
        });
        visualSimulationThread.setDaemon(true);
        visualSimulationThread.start();
    }

    private void saveChart(LineChart<Number, Number> chart, String filename) {
        WritableImage image = chart.snapshot(null, null);
        File file = new File(filename);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Saved chart to: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving chart: " + e.getMessage());
        }
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

    private void startChartsSimulation() {
        // Iterate over all graph settings and initialize simulation data
        for (String key : charts.keySet()) {
            String[] dimensions = key.split(",");
            int m = Integer.parseInt(dimensions[0]);
            int n = Integer.parseInt(dimensions[1]);

            // Iterate over different chance values and update Chart
            for (double chance = 0.0; chance <= 1.0; chance += 0.1) {
                double[] data = simulateForChance(m, n, chance);
                updateChart(key, chance, data);
            }
        }
    }

    private void update3DGraphs(double chance) {
        Group root3D = initialize3DRoot();
        
        // Initialize allData with simulation results from cache or new simulations
        Map<String, Map<Double, double[]>> allData = new HashMap<>();
        
        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                String key = m + "," + n;
                Map<Double, double[]> chanceData = new HashMap<>();

                // Populate data for all possible chances (0.0 to 1.0 with step 0.1)
                for (double c = 0.0; c <= 1.0; c += 0.1) {
                    String cacheKey = m + "," + n + "," + c;
                    double[] data = simulationCache.getOrDefault(cacheKey, simulateForChance(m, n, c));
                    chanceData.put(c, data);
                }
                allData.put(key, chanceData);
            }
        }
        createSurfaceVisualization(root3D, allData);
        create3DVisualizations(root3D, allData);
        show3DStage(root3D);
    }

    @Override
    public void start(Stage stage) throws IOException {
        // Initialize module components
        currentLandscape = new Landscape(max, max, 0.5);
        simulationCanvas = createSimulationCanvas(max, max);
        charts = new HashMap<>();

        // Create horizontal slider with proper labels
        Slider chanceSlider = new Slider(0, 1, 0.5);
        chanceSlider.setShowTickLabels(true);
        chanceSlider.setShowTickMarks(true);
        chanceSlider.setMajorTickUnit(0.1);
        chanceSlider.setMinorTickCount(4);
        chanceSlider.setBlockIncrement(0.05);
        chanceSlider.setPrefWidth(400);

        // Add a label to display the current chance value
        Label chanceValueLabel = new Label("Current Chance: 0.5");

        // Create a grid pane for precise label alignment
        GridPane sliderGrid = new GridPane();
        sliderGrid.setHgap(0);

        chanceSlider.valueProperty().addListener((_, _, newValue) -> {
            double chance = Math.round(newValue.doubleValue() * 20) / 20.0;
            chanceValueLabel.setText(String.format("Current Chance: %.2f", chance));
            Platform.runLater(() -> update3DGraphs(chance));
        });

        // Create control panel
        Button startSimButton = new Button("Start Simulation");
        Button stopSimButton = new Button("Stop Simulation");
        Button resetSimButton = new Button("Reset Simulation");
        Button saveButton = new Button("Save All");

        startSimButton.setOnAction(_ -> {
            runVisualSimulation();
        });
        stopSimButton.setOnAction(_ -> {
            isSimulationRunning = false;
        });
        resetSimButton.setOnAction(_ -> {
            isSimulationRunning = false;
            currentLandscape = new Landscape(max, max, 0.5);
            drawLandscape(simulationCanvas, currentLandscape);
        });
        saveButton.setOnAction(_ -> {
            saveAllCharts(charts);
            saveSimulationState("current_state");
        });

        HBox controlPane = new HBox(10);
        controlPane.getChildren().addAll(startSimButton, stopSimButton, resetSimButton, saveButton, chanceValueLabel);
        controlPane.setPadding(new Insets(5));
        controlPane.setAlignment(Pos.CENTER_LEFT);

        // Create slider VBox with slider and its labels
        VBox sliderBox = new VBox(5);
        sliderBox.getChildren().addAll(chanceSlider, sliderGrid);
        sliderBox.setPadding(new Insets(10, 10, 5, 10));
        sliderBox.setAlignment(Pos.CENTER);

        // Construct main layout
        VBox simulationPane = new VBox(10);
        simulationPane.getChildren().addAll(simulationCanvas, controlPane, sliderBox);
        simulationPane.setPadding(new Insets(10));

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10));

        for (int m = MIN_SIZE; m <= max; m++) {
            for (int n = MIN_SIZE; n <= m; n++) {
                final NumberAxis xAxis = new NumberAxis(0, 1, 0.05);
                final NumberAxis yAxis = new NumberAxis(0, 4, 1);
                xAxis.setLabel("Chance");
                yAxis.setLabel("Average Living Cells");

                LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
                chart.setTitle(m + "x" + n + " Landscape");
                chart.setCreateSymbols(false);
                chart.setPrefSize(CHART_WIDTH, CHART_HEIGHT);

                String key = m + "," + n;
                charts.put(key, chart);
                gridPane.add(chart, n , m );
            }
        }

        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        VBox mainLayout = new VBox(16);
        mainLayout.getChildren().addAll(simulationPane, scrollPane);

        startChartsSimulation();

        Scene scene = new Scene(mainLayout, 1200, 800);
        scene.setCamera(new PerspectiveCamera());

        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setTitle("Life Simulation - Visualization and Analysis");
        stage.show();

        drawLandscape(simulationCanvas, currentLandscape);
    }

    public static void main(String[] args) {
        launch(args);
    }
}