import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.control.Button;
import javafx.application.Platform;

public class LifeSimulation2 extends Application {
    private static final int CHART_WIDTH = 500, CHART_HEIGHT = 800, CELL_SIZE = 16;
    private static String OUTPUT_DIR = "simulation_results";
    private static int max = 9, lower, low, up, upper, steps;
    // 3D visualization fields
    private static final double CELL_3D_SIZE = 10;
    private Group cellGroup;
    private Landscape2 currentLandscape;
    private Canvas simulationCanvas;
    public boolean isSimulationRunning;
    public Thread visualSimulationThread;
    private Map<String, LineChart<Number, Number>> charts;
    private Label lowerLabel, lowLabel, upLabel, upperLabel;
    private Slider chanceSlider;
    private Label chanceLabel;
    private ComboBox<String> gridTypeCombo;
    private Spinner<Integer> dimensionSpinner;
    private final Map<String, Double> cellAverages = new HashMap<>();
    public boolean chartsSavingPending;
    private Slider lowerSlider, lowSlider, upSlider, upperSlider;
    private Map<String, double[]> simulationCache = new ConcurrentHashMap<>();
    private Button startSimulationButton;
    private Label averageLivingCellsLabel;
    private HBox controlPanel;
    
    public enum GridType {
        SQUARE, TRIANGLE, HEXAGON
    }
    
    private void updateCellAverages() {
        // Calculate averages over simulation steps
        for (int i = 0; i < currentLandscape.getRows(); i++) {
            for (int j = 0; j < currentLandscape.getCols(); j++) {
                for (int k = 0; k < currentLandscape.getDepth(); k++) {
                    String key = i + "," + j + "," + k;
                    double currentAvg = cellAverages.getOrDefault(key, 0.0);
                    boolean isAlive = currentLandscape.getCell(i, j, k).getAlive();
                    
                    // Update running average (90% old value, 10% new value for smoothing)
                    cellAverages.put(key, currentAvg * 0.9 + (isAlive ? 1.0 : 0.0) * 0.1);
                }
            }
        }
    }
    
    public static void setOutputDirectory(String dir) {
        OUTPUT_DIR = dir;
    }
    private void update3DView() {
        // Clear the 3D visualization from the GUI
        cellGroup.getChildren().clear();
        
        // Update 3D visualization based on current parameters
        switch (currentLandscape.getGridType()) {
            case TRIANGLE:
                update3DTriangleView();
                break;
            case HEXAGON:
                update3DHexagonView();
                break;
            case SQUARE:
            default:
                update3DSquareView();
        }
    }
    
    private void save3DCharts() {
        // Save the 3D charts with the current parameters
        for (String key : charts.keySet()) {
            LineChart<Number, Number> chart = charts.get(key);
            chart.setTitle("3D Chart - m: " + key + ", lower: " + lower + ", low: " + low + ", up: " + up + ", upper: " + upper);
            saveChart(chart, OUTPUT_DIR + "/3D_Chart_" + key + ".png");
        }
    }
    
    private Canvas createSimulationCanvas(int width, int height) {
        Canvas canvas = new Canvas(width * CELL_SIZE, height * CELL_SIZE);
        
        // Ensure the canvas has a valid size
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            System.err.println("Canvas has invalid dimensions: " + canvas.getWidth() + "x" + canvas.getHeight());
            return canvas; // or handle the error as needed
        }
        
        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Check if GraphicsContext is null
        if (gc == null) {
            System.err.println("GraphicsContext is null. Canvas may not be initialized properly.");
            return canvas; // or handle the error as needed
        }
        
        gc.setFill(Color.WHITE);
        gc.setStroke(Color.BLACK);
        return canvas;
    }
    
    private void drawLandscape(Canvas canvas, Landscape2 landscape) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    
        // Check the grid type and call the appropriate drawing method
        switch (landscape.getGridType()) {
            case TRIANGLE:
                draw2DTriangleGrid(gc, landscape);
                break;
            case HEXAGON:
                draw2DHexagonGrid(gc, landscape);
                break;
            case SQUARE:
            default:
                draw2DSquareGrid(gc, landscape);
        }
    }
    
    // New method to draw 2D triangle grid
    private void draw2DTriangleGrid(GraphicsContext gc, Landscape2 landscape) {
        double triangleHeight = CELL_SIZE * Math.sqrt(3) / 2; // Height of equilateral triangle
        
        for (int i = 0; i < landscape.getRows(); i++) {
            for (int j = 0; j < landscape.getCols(); j++) {
                Cell cell = landscape.getCell(i, j, 0); // Assuming 2D landscape, depth is 0
                boolean isUpTriangle = ((i + j) % 2 == 0);
                
                gc.setFill(cell.getAlive() ? Color.BLACK : Color.WHITE);
                double xCenter = j * CELL_SIZE + (double) CELL_SIZE / 2;
                double yCenter = i * triangleHeight + triangleHeight / 2;

                // Draw triangle
                double[] xPoints = new double[3];
                double[] yPoints = new double[3];

                xPoints[0] = xCenter; // Top vertex
                if (isUpTriangle) {
                    yPoints[0] = yCenter - (triangleHeight / 2);
                    xPoints[1] = xCenter - (CELL_SIZE / 2); // Bottom left
                    xPoints[2] = xCenter + (CELL_SIZE / 2); // Bottom right
                    yPoints[1] = yCenter + (triangleHeight / 2);
                    yPoints[2] = yCenter + (triangleHeight / 2);
                } else {
                    yPoints[0] = yCenter + (triangleHeight / 2);
                    xPoints[1] = xCenter - (CELL_SIZE / 2); // Bottom left
                    xPoints[2] = xCenter + (CELL_SIZE / 2); // Bottom right
                    yPoints[1] = yCenter - (triangleHeight / 2);
                    yPoints[2] = yCenter - (triangleHeight / 2);
                }
                
                gc.fillPolygon(xPoints, yPoints, 3);
                gc.strokePolygon(xPoints, yPoints, 3);
            }
        }
    }

    // New method to draw 2D hexagon grid
    private void draw2DHexagonGrid(GraphicsContext gc, Landscape2 landscape) {
        double hexWidth = CELL_SIZE;
        double hexHeight = CELL_SIZE * Math.sqrt(3);
        
        for (int i = 0; i < landscape.getRows(); i++) {
            for (int j = 0; j < landscape.getCols(); j++) {
                Cell cell = landscape.getCell(i, j, 0); // Assuming 2D landscape, depth is 0
                
                gc.setFill(cell.getAlive() ? Color.BLACK : Color.WHITE);
                double xCenter = j * (hexWidth * 0.75) + (i % 2 == 0 ? 0 : hexWidth * 0.375);
                double yCenter = i * (hexHeight * 0.5);
                
                // Draw the hexagon
                double[] xPoints = new double[6];
                double[] yPoints = new double[6];
                
                for (int p = 0; p < 6; p++) {
                    double angle = 2 * Math.PI / 6 * p;
                    xPoints[p] = xCenter + (hexWidth / 2) * Math.cos(angle);
                    yPoints[p] = yCenter + (hexWidth / 2) * Math.sin(angle);
                }
                gc.fillPolygon(xPoints, yPoints, 6);
                gc.strokePolygon(xPoints, yPoints, 6);
            }
        }
    }
    
    // New method to draw 2D square grid
    private void draw2DSquareGrid(GraphicsContext gc, Landscape2 landscape) {
        for (int i = 0; i < landscape.getRows(); i++) {
            for (int j = 0; j < landscape.getCols(); j++) {
                gc.setFill(landscape.getCell(i, j, 0).getAlive() ? Color.BLACK : Color.WHITE);
                gc.fillRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                gc.strokeRect(j * CELL_SIZE, i * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
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
    
    private void saveSimulationState(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists()) {
            directory.mkdir();
        }
        
        String filename = dirName + "/" + prefix + ".png";
        WritableImage image = simulationCanvas.snapshot(null, null);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(filename));
            System.out.println("Saved simulation state to: " + filename);
        } catch (IOException e) {
            System.err.println("Error saving simulation state: " + e.getMessage());
        }
    }
    private void saveAllCharts(Map<String, LineChart<Number, Number>> charts) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String dirName = OUTPUT_DIR + "_" + timestamp;
        File directory = new File(dirName);
        if (!directory.exists()) directory.mkdir();
        
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String dimensions = entry.getKey().replace(',', 'x');
            String filename = dirName + "/landscape_" + dimensions + ".png";
            saveChart(entry.getValue(), filename);
        }
    }
    @Override
    public void start(Stage stage) {
        initializeSimulationParameters();
    
    // Initialize the 3D cell group
        cellGroup = new Group();
        setupUIComponents();
        setupStage(stage);
    // Run simulations after setting up the UI
        runSimulations();
    // Initial visualization
        drawLandscape(simulationCanvas, currentLandscape);
    }
    private void initializeSimulationParameters() {
        lower = 2;
        low = 3;
        up = 3;
        upper = 3;
        currentLandscape = new Landscape2(max, max, max, 0.5, lower, low, up, upper, 
                                         Landscape2.GridType.SQUARE, 3);
        simulationCanvas = createSimulationCanvas(max, max);
        charts = new HashMap<>();
    }
    private void setupUIComponents() {
        // Create sliders for parameters
        lowerSlider = createSlider(0, 10, lower, t -> {
            try {
                updateLower(t);
            } catch (IOException e) {e.printStackTrace();}
        });
        lowSlider = createSlider(0, 10, low, t -> {
            try {
                updateLow(t);
            } catch (IOException e) {e.printStackTrace();}
        });
        upSlider = createSlider(0, 10, up, t -> {
            try {
                updateUp(t);
            } catch (IOException e) {e.printStackTrace();}
        });
        upperSlider = createSlider(0, 10, upper, t -> {
            try {
                updateUpper(t);
            } catch (IOException e) {e.printStackTrace();}
        });
        chanceSlider = createSlider(0, 1, 0.5, t -> {
            try {
                updateChance(t);
            } catch (IOException e) {e.printStackTrace();}
        });
        // Create dropdown for grid type
        gridTypeCombo = new ComboBox<>();
        gridTypeCombo.getItems().addAll("Square", "Triangle", "Hexagon");
        gridTypeCombo.setValue("Square");
        gridTypeCombo.setOnAction(_ -> {
            GridType selected = getSelectedGridType();
            try {
                Landscape2.GridType landscapeGridType = Landscape2.GridType.valueOf(selected.name());
                resetSimulationWithGridType(landscapeGridType);
            } catch (IOException e) {e.printStackTrace();}
        });
        // Create spinner for dimensions
        dimensionSpinner = new Spinner<>(3, 5, 3);
        dimensionSpinner.setEditable(true);
        dimensionSpinner.valueProperty().addListener((_, _, newVal) -> {
            try {
                resetSimulationWithDimensions(newVal);
            } catch (IOException e) {e.printStackTrace();}
        });
        // Create labels for sliders
        lowerLabel = new Label("Lower: " + lower);
        lowLabel = new Label("Low: " + low);
        upLabel = new Label("Up: " + up);
        upperLabel = new Label("Upper: " + upper);
        chanceLabel = new Label("Chance: 0.5");

        // Create Start Simulation button
        startSimulationButton = new Button("Start Simulation");
        startSimulationButton.setOnAction(_ -> startSimulation());

        // Create label for average living cells
        averageLivingCellsLabel = new Label("Average Living Cells: 0");

        // Create a horizontal box for controls
        controlPanel = new HBox(10); // Add spacing between controls
        controlPanel.getChildren().addAll(lowerLabel, lowerSlider, lowLabel, lowSlider, 
                                           upLabel, upSlider, upperLabel, upperSlider, 
                                           chanceLabel, chanceSlider, gridTypeCombo, 
                                           dimensionSpinner, startSimulationButton, 
                                           averageLivingCellsLabel);

        // Create buttons for grid type selection
        Button squareButton = new Button("Square Grid");
        squareButton.setOnAction(_ -> {
            try {
                resetSimulationWithGridType(Landscape2.GridType.SQUARE);
            } catch (IOException e1) {e1.printStackTrace();}
        });

        Button triangleButton = new Button("Triangle Grid");
        triangleButton.setOnAction(_ -> {
            try {
                resetSimulationWithGridType(Landscape2.GridType.TRIANGLE);
            } catch (IOException e1) {e1.printStackTrace();}
        });

        Button hexagonButton = new Button("Hexagon Grid");
        hexagonButton.setOnAction(_ -> {
            try {
                resetSimulationWithGridType(Landscape2.GridType.HEXAGON);
            } catch (IOException e1) {e1.printStackTrace();}
        });

        // Add buttons to control panel
        controlPanel.getChildren().addAll(squareButton, triangleButton, hexagonButton);

        // Initialize charts
        initializeCharts();
    }
    private void initializeCharts() {
        charts = new HashMap<>();
        // Example of initializing a chart for a specific key
        for (int i = 0; i < 10; i++) { // Adjust the range as needed
            LineChart<Number, Number> chart = createChart("Chart " + i);
            charts.put("Chart " + i, chart);
        }
    }
    private LineChart<Number, Number> createChart(String title) {
        // Create a new LineChart with appropriate axes
        // Example: X and Y axes can be NumberAxis
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        return chart;
    }
    private Slider createSlider(double min, double max, double initialValue, java.util.function.Consumer<Double> onChange) {
        Slider slider = new Slider(min, max, initialValue);
        slider.valueProperty().addListener((_, _, newVal) -> onChange.accept(newVal.doubleValue()));
        return slider;
    }
    private void updateLower(double value) throws IOException {
        lower = (int) value;
        lowerLabel.setText("Lower: " + lower);
        updateCharts();
        updateVisualization();
    }
    private void updateLow(double value) throws IOException {
        low = (int) value;
        lowLabel.setText("Low: " + low);
        updateCharts();
        updateVisualization();
    }
    private void updateUp(double value) throws IOException {
        up = (int) value;
        upLabel.setText("Up: " + up);
        updateCharts();
        updateVisualization();
    }
    private void updateUpper(double value) throws IOException {
        upper = (int) value;
        upperLabel.setText("Upper: " + upper);
        updateCharts();
        updateVisualization();
    }
    private void updateChance(double value) throws IOException {
        chanceLabel.setText("Chance: " + String.format("%.2f", value));
        resetSimulationWithChance(value);
    }
    private void setupStage(Stage stage) {
        // Set up the scene and stage
        VBox layout = new VBox();
        layout.getChildren().addAll(simulationCanvas, controlPanel);
        
        // Add charts to the layout
        for (LineChart<Number, Number> chart : charts.values()) layout.getChildren().add(chart);

        Scene scene = new Scene(layout, CHART_WIDTH, CHART_HEIGHT);
        stage.setTitle("Life Simulation - 3D Visualization and Analysis");
        stage.setScene(scene);
        stage.show();
    }
    
    private GridType getSelectedGridType() {
        String type = gridTypeCombo.getValue();
        switch (type) {
            case "Triangle": return GridType.TRIANGLE;
            case "Hexagon": return GridType.HEXAGON;
            default: return GridType.SQUARE;
        }
    }
    private void updateCharts() {
        // Update the charts with the latest data
        for (String key : charts.keySet()) {
            LineChart<Number, Number> chart = charts.get(key);
            chart.getData().clear();
            // Create a new series for the current chart
            XYChart.Series<Number, Number> mainSeries = new XYChart.Series<>();
            XYChart.Series<Number, Number> upperSeries = new XYChart.Series<>();
            XYChart.Series<Number, Number> lowerSeries = new XYChart.Series<>();
            
            // Example: Populate the series with data (replace with actual data logic)
            double averageLivingCells = getAverageLivingCellsForKey(key);
            double stdDev = getMaxAverageLivingCellsForKey(key); // Assuming this method returns the std deviation
            mainSeries.getData().add(new XYChart.Data<>(steps, averageLivingCells));
            upperSeries.getData().add(new XYChart.Data<>(steps, averageLivingCells + stdDev));
            lowerSeries.getData().add(new XYChart.Data<>(steps, averageLivingCells - stdDev));
            
            // Add the series to the chart
            chart.getData().add(mainSeries);
            chart.getData().add(upperSeries);
            chart.getData().add(lowerSeries);
            
            // Update the y-axis upper bound
            NumberAxis yAxis = (NumberAxis) chart.getYAxis();
            double currentMax = getMaxAverageLivingCellsForKey(key); // Get the maximum average living cells
            yAxis.setUpperBound(currentMax); // Set upper bound to max average living cells
        }
    }
    
    // Method to get the average living cells for a specific key
    private double getAverageLivingCellsForKey(String key) {
        // Implement logic to calculate the average living cells for the given key
        // This is a placeholder; replace with actual calculation logic
        return Math.random() * 100; // Example: Random value for demonstration
    }
    
    // Method to get the maximum average living cells for a specific key
    private double getMaxAverageLivingCellsForKey(String key) {
        // Implement logic to calculate the maximum average living cells for the given key
        // This is a placeholder; replace with actual calculation logic
        return Math.random() * 100; // Example: Random value for demonstration
    }
    
    // Add methods to reset simulation with new parameters
    private void resetSimulationWithChance(double chance) throws IOException {
        isSimulationRunning = false;
        currentLandscape = new Landscape2(max, max, max, chance, lower, low, up, upper, 
                                         Landscape2.GridType.SQUARE, dimensionSpinner.getValue());
        drawLandscape(simulationCanvas, currentLandscape);
        update3DView();
        cellAverages.clear(); // Reset the averages when changing landscape
    }
    
    private void resetSimulationWithGridType(Landscape2.GridType gridType) throws IOException {
        isSimulationRunning = false;
        double chance = chanceSlider.getValue();
        currentLandscape = new Landscape2(max, max, max, chance, lower, low, up, upper, 
                                         gridType, dimensionSpinner.getValue());
        drawLandscape(simulationCanvas, currentLandscape);
        update3DView();
        cellAverages.clear(); // Reset the averages when changing landscape
    }
    
    private void resetSimulationWithDimensions(int dimensions) throws IOException {
        isSimulationRunning = false;
        double chance = chanceSlider.getValue();
        currentLandscape = new Landscape2(max, max, max, chance, lower, low, up, upper, 
                                         Landscape2.GridType.SQUARE, dimensions);
        drawLandscape(simulationCanvas, currentLandscape);
        update3DView();
        cellAverages.clear(); // Reset the averages when changing landscape
    }
    private void update3DSquareView() {
        PhongMaterial aliveMat = new PhongMaterial(Color.BLUE);
        
        for (int i = 0; i < currentLandscape.getRows(); i++) {
            for (int j = 0; j < currentLandscape.getCols(); j++) {
                for (int k = 0; k < currentLandscape.getDepth(); k++) {
                    Cell cell = currentLandscape.getCell(i, j, k);
                    if (cell.getAlive()) {
                        // Use average living status to adjust size
                        String key = i + "," + j + "," + k;
                        double avg = cellAverages.getOrDefault(key, 0.5);
                        double size = CELL_3D_SIZE * (1 / (avg + 0.5));
                        size = Math.min(size, 1.5 * CELL_3D_SIZE); // Cap max size
                        
                        Box box = new Box(size, size, size);
                        box.setTranslateX(i * size);
                        box.setTranslateY(j * size);
                        box.setTranslateZ(k * size);
                        box.setMaterial(aliveMat);
                        
                        cellGroup.getChildren().add(box);
                    }
                }
            }
        }
    }
    
    private void update3DTriangleView() {
        PhongMaterial aliveMaterial = new PhongMaterial(Color.BLUE);
        
        for (int i = 0; i < currentLandscape.getRows(); i++) {
            for (int j = 0; j < currentLandscape.getCols(); j++) {
                for (int k = 0; k < currentLandscape.getDepth(); k++) {
                    Cell cell = currentLandscape.getCell(i, j, k);
                    if (cell.getAlive()) {
                        boolean isUpTriangle = ((i + j) % 2 == 0);
                        
                        // Get average living status for scaling
                        String key = i + "," + j + "," + k;
                        double avgLiving = cellAverages.getOrDefault(key, 0.5);
                        
                        // Scale size inversely with average
                        double scale = 1.0 / (avgLiving + 0.5);
                        scale = Math.min(scale, 1.5); // Cap max scale
                        
                        // Create a 3D triangular prism
                        TriangleMesh triangleMesh = new TriangleMesh();
                        
                        // Define the coordinates for top and bottom triangles
                        float triangleSize = (float)(CELL_3D_SIZE * scale);
        
                        // Define vertices coordinates
                        float[] points = {
                            0, 0, 0,                                         // Point 0: Top triangle, vertex 1
                            triangleSize * 0.5f, triangleSize * 0.866f, 0,   // Point 1: Top triangle, vertex 2
                            triangleSize, 0, 0,                              // Point 2: Top triangle, vertex 3
                            0, 0, triangleSize,                                    // Point 3: Bottom triangle, vertex 1
                            triangleSize * 0.5f, triangleSize * 0.866f, triangleSize, // Point 4: Bottom triangle, vertex 2
                            triangleSize, 0, triangleSize                          // Point 5: Bottom triangle, vertex 3
                        };
                        triangleMesh.getPoints().addAll(points);
                        
                        // Define texture coordinates (not used but required)
                        float[] texCoords = {
                            0, 0,
                            0.5f, 0,
                            1, 0,
                            0, 1,
                            0.5f, 1,
                            1, 1
                        };
                        triangleMesh.getTexCoords().addAll(texCoords);
                        
                        // Define faces as triangles:
                        int[] faces = {
                            0, 0, 1, 1, 2, 2,       // Top face
                            3, 3, 5, 5, 4, 4,       // Bottom face
                            0, 0, 3, 3, 1, 1,       // Side 1 (part 1)
                            1, 1, 3, 3, 4, 4,       // Side 1 (part 2)
                            1, 1, 4, 4, 2, 2,       // Side 2 (part 1)
                            2, 2, 4, 4, 5, 5,       // Side 2 (part 2)
                            2, 2, 5, 5, 0, 0,       // Side 3 (part 1)
                            0, 0, 5, 5, 3, 3        // Side 3 (part 2)
                        };
                        triangleMesh.getFaces().addAll(faces);
                        
                        MeshView trianglePrism = new MeshView(triangleMesh);
                        trianglePrism.setMaterial(aliveMaterial);
                        
                        // Position the prism
                        double triangleHeight = CELL_3D_SIZE * Math.sqrt(3) / 2;
                        double xPos = j * CELL_3D_SIZE;
                        double yPos = i * triangleHeight;
                        double zPos = k * CELL_3D_SIZE;
                        
                        trianglePrism.setTranslateX(xPos);
                        trianglePrism.setTranslateY(yPos);
                        trianglePrism.setTranslateZ(zPos);
                        
                        // Rotate to face up or down based on triangle type
                        if (!isUpTriangle) {
                            Rotate rotate = new Rotate(180, Rotate.Z_AXIS);
                            trianglePrism.getTransforms().add(rotate);
                        }
                        
                        cellGroup.getChildren().add(trianglePrism);
                    }
                }
            }
        }
    }
    
    private void update3DHexagonView() {
        PhongMaterial aliveMaterial = new PhongMaterial(Color.BLUE);
        
        for (int i = 0; i < currentLandscape.getRows(); i++) {
            for (int j = 0; j < currentLandscape.getCols(); j++) {
                for (int k = 0; k < currentLandscape.getDepth(); k++) {
                    Cell cell = currentLandscape.getCell(i, j, k);
                    if (cell.getAlive()) {
                        // Get average living status for scaling
                        String key = i + "," + j + "," + k;
                        double avgLiving = cellAverages.getOrDefault(key, 0.5);
                        
                        // Scale size inversely with average
                        double scale = 1.0 / (avgLiving + 0.5);
                        scale = Math.min(scale, 1.5); // Cap max scale
                        
                        // Create a hexagonal prism using a custom TriangleMesh
                        TriangleMesh hexMesh = new TriangleMesh();
                        
                        float hexRadius = (float)(CELL_3D_SIZE * 0.5 * scale);
                        float height = (float)(CELL_3D_SIZE * scale);
                        // Define points for the hexagonal prism
                        float[] points = new float[42]; // 7 points per face (6 vertices + center) * 2 faces
        
                        // Top face points (center + 6 vertices)
                        points[0] = 0;             // Center point (x)
                        points[1] = 0;             // Center point (y)
                        points[2] = 0;             // Center point (z)
                        
                        // Bottom face points (center + 6 vertices)
                        points[21] = 0;             // Center point (x)
                        points[22] = 0;             // Center point (y)
                        points[23] = height;        // Center point (z)
                        
                        // Calculate the 6 vertices for top and bottom faces
                        for (int v = 0; v < 6; v++) {
                            double angle = 2 * Math.PI / 6 * v;
                            float x = (float)(hexRadius * Math.cos(angle));
                            float y = (float)(hexRadius * Math.sin(angle));
                            
                            // Top face vertices
                            points[3 + v * 3] = x;
                            points[4 + v * 3] = y;
                            points[5 + v * 3] = 0;
                            
                            // Bottom face vertices
                            points[24 + v * 3] = x;
                            points[25 + v * 3] = y;
                            points[26 + v * 3] = height;
                        }
                        
                        hexMesh.getPoints().addAll(points);
                        
                        // Define texture coordinates (not used but required)
                        float[] texCoords = new float[28]; // 14 points (2 coordinates each)
                        // Center point for top and bottom
                        texCoords[0] = 0.5f;
                        texCoords[1] = 0.5f;
                        texCoords[14] = 0.5f;
                        texCoords[15] = 0.5f;
                        
                        // Calculate the 6 vertices for top and bottom texture coordinates
                        for (int v = 0; v < 6; v++) {
                            double angle = 2 * Math.PI / 6 * v;
                            float x = (float)(0.5 + 0.5 * Math.cos(angle));
                            float y = (float)(0.5 + 0.5 * Math.sin(angle));
                            
                            // Top face vertices
                            texCoords[2 + v * 2] = x;
                            texCoords[3 + v * 2] = y;
                            
                            // Bottom face vertices
                            texCoords[16 + v * 2] = x;
                            texCoords[17 + v * 2] = y;
                        }
                        hexMesh.getTexCoords().addAll(texCoords);
                        // Define faces (triangles)
                        int[] faces = new int[72]; // 12 triangles (top + bottom + sides) * 6 indices each
                        // Top face - 6 triangles connecting center to each edge
                        for (int f = 0; f < 6; f++) {
                            int nextf = (f + 1) % 6;
                            faces[f * 6] = 0;         // Center point of top face
                            faces[f * 6 + 1] = 0;    // Texture coordinate
                            faces[f * 6 + 2] = 1 + f; // Current vertex
                            faces[f * 6 + 3] = 1 + f; // Texture coordinate
                            faces[f * 6 + 4] = 1 + nextf; // Next vertex
                            faces[f * 6 + 5] = 1 + nextf; // Texture coordinate
                        }
                        // Bottom face - 6 triangles connecting center to each edge
                        for (int f = 0; f < 6; f++) {
                            int nextf = (f + 1) % 6;
                            faces[36 + f * 6] = 7;         // Center point of bottom face
                            faces[36 + f * 6 + 1] = 7;    // Texture coordinate
                            faces[36 + f * 6 + 2] = 8 + nextf; // Next vertex (reversed order for outward facing)
                            faces[36 + f * 6 + 3] = 8 + nextf; // Texture coordinate
                            faces[36 + f * 6 + 4] = 8 + f; // Current vertex
                            faces[36 + f * 6 + 5] = 8 + f; // Texture coordinate
                        }
                        // Since the original array wasn't large enough, we'll use a separate array for sides
                        int[] sideFaces = new int[72]; // 12 additional triangles (6 quads) for sides
                        // Add the side faces - 6 quads (12 triangles)
                        for (int f = 0; f < 6; f++) {
                            int nextf = (f + 1) % 6;
                            
                            // First triangle of the quad
                            sideFaces[f * 12] = 1 + f;       // Top current vertex
                            sideFaces[f * 12 + 1] = 1 + f;   // Texture coordinate
                            sideFaces[f * 12 + 2] = 1 + nextf; // Top next vertex
                            sideFaces[f * 12 + 3] = 1 + nextf; // Texture coordinate
                            sideFaces[f * 12 + 4] = 8 + f;   // Bottom current vertex
                            sideFaces[f * 12 + 5] = 8 + f;   // Texture coordinate
                            
                            // Second triangle of the quad
                            sideFaces[f * 12 + 6] = 8 + f;     // Bottom current vertex
                            sideFaces[f * 12 + 7] = 8 + f;     // Texture coordinate
                            sideFaces[f * 12 + 8] = 1 + nextf; // Top next vertex
                            sideFaces[f * 12 + 9] = 1 + nextf; // Texture coordinate
                            sideFaces[f * 12 + 10] = 8 + nextf; // Bottom next vertex
                            sideFaces[f * 12 + 11] = 8 + nextf; // Texture coordinate
                        }
                        
                        hexMesh.getFaces().addAll(faces);
                        hexMesh.getFaces().addAll(sideFaces);
                        
                        // Create a mesh view to display the hexagonal prism
                        MeshView hexPrism = new MeshView(hexMesh);
                        hexPrism.setMaterial(aliveMaterial);
                        
                        // Position the hexagonal prism
                        double xOffset = j * CELL_3D_SIZE * 0.75;
                        // Offset every other row
                        if (i % 2 == 1) xOffset += CELL_3D_SIZE * 0.375;

                        double yOffset = i * CELL_3D_SIZE * 0.866; // Hexagon height = 2 * radius * sin(60°)
                        
                        hexPrism.setTranslateX(xOffset);
                        hexPrism.setTranslateY(yOffset);
                        hexPrism.setTranslateZ(k * CELL_3D_SIZE);
                        
                        cellGroup.getChildren().add(hexPrism);
                    }
                }
            }
        }
    }
    // Modify visualization to change cell size based on average living cell count
    private void updateVisualization() throws IOException {
        // This method updates current visualization based on average live cell counts without resetting the simulation
        updateCellAverages();
        drawLandscape(simulationCanvas, currentLandscape); // with current gridType and dimensions
        cellGroup.getChildren().clear();// Update 3D visualization based on cell averages
        
        // Select appropriate 3D visualization method based on grid type
        switch (currentLandscape.getGridType()) {
            case TRIANGLE:
                update3DTriangleView();
                break;
            case HEXAGON:
                update3DHexagonView();
                break;
            case SQUARE:
            default: update3DSquareView();
        }
        // Update chart titles to reflect current parameters
        double chance = chanceSlider.getValue();
        String gridTypeStr = gridTypeCombo.getValue();
        int dimensions = dimensionSpinner.getValue();
        
        for (String key : charts.keySet()) {
            LineChart<Number, Number> chart = charts.get(key);
            chart.setTitle("m: " + key + 
                          ", Grid: " + gridTypeStr + 
                          ", Dim: " + dimensions +
                          ", Chance: " + String.format("%.2f", chance) +
                          ", lower: " + lower + 
                          ", low: " + low + 
                          ", up: " + up + 
                          ", upper: " + upper);
        }
        saveAllCharts(charts);
        saveSimulationState("updated_visualization");
        save3DCharts();
        save3DModels();
    }
    private void save3DModels() throws IOException {
        // Iterate through the landscape and save each cell as a 3D model
        for (int i = 0; i < currentLandscape.getRows(); i++) {
            for (int j = 0; j < currentLandscape.getCols(); j++) {
                for (int k = 0; k < currentLandscape.getDepth(); k++) {
                    Cell cell = currentLandscape.getCell(i, j, k);
                    if (cell.getAlive()) {
                        // Save as STL
                        Box box = new Box(CELL_3D_SIZE, CELL_3D_SIZE, CELL_3D_SIZE);
                        String stlFilename = OUTPUT_DIR + "/cell_" + i + "_" + j + "_" + k + ".stl";
                        LandscapeDisplay.exportBoxToSTL(box, stlFilename);
                        
                        // Save as OBJ
                        String objFilename = OUTPUT_DIR + "/cell_" + i + "_" + j + "_" + k + ".obj";
                        LandscapeDisplay.exportMeshToOBJ(createMeshFromBox(box), objFilename);
                    }
                }
            }
        }
    }
    // Method to create a TriangleMesh from a Box for OBJ export
    private TriangleMesh createMeshFromBox(Box box) {
        TriangleMesh mesh = new TriangleMesh();
    
    // Define the 8 vertices of the box
        float w = (float) box.getWidth() / 2;
        float h = (float) box.getHeight() / 2;
        float d = (float) box.getDepth() / 2;
    
    // Points for the 8 corners of the box
        float[] points = {
        -w, -h, -d,  // 0: bottom-left-back
         w, -h, -d,  // 1: bottom-right-back
         w,  h, -d,  // 2: top-right-back
        -w,  h, -d,  // 3: top-left-back
        -w, -h,  d,  // 4: bottom-left-front
         w, -h,  d,  // 5: bottom-right-front
         w,  h,  d,  // 6: top-right-front
        -w,  h,  d   // 7: top-left-front
        };
        mesh.getPoints().addAll(points);
    
    // Texture coordinates (not really used but required)
        float[] texCoords = {
        0, 0,  // 0
        1, 0,  // 1
        1, 1,  // 2
        0, 1   // 3
        };
        mesh.getTexCoords().addAll(texCoords);
    
    // Define the 12 triangles (6 faces, 2 triangles each)
        int[] faces = {
        // Front face
        4, 0, 5, 1, 7, 3,
        5, 1, 6, 2, 7, 3,
        // Back face
        1, 0, 0, 1, 2, 3,
        0, 1, 3, 2, 2, 3,
        // Right face
        5, 0, 1, 1, 6, 3,
        1, 1, 2, 2, 6, 3,
        // Left face
        0, 0, 4, 1, 3, 3,
        4, 1, 7, 2, 3, 3,
        // Top face
        7, 0, 6, 1, 3, 3,
        6, 1, 2, 2, 3, 3,
        // Bottom face
        0, 0, 1, 1, 4, 3,
        1, 1, 5, 2, 4, 3
        };
        mesh.getFaces().addAll(faces);
        return mesh;
    }
    private double[] simulateForChance(int m, int n, double chance) {
        String cacheKey = m + "," + n + "," + chance;
        if (simulationCache.containsKey(cacheKey)) {
            return simulationCache.get(cacheKey);
        }
        long totalLivingCells = 0;
        int r = 2 * m * n; // Ensure r is calculated correctly
        for (int rep = 0; rep < r; rep++) {
            Landscape2 landscape = new Landscape2(m, n, max, chance, lower, low, up, upper, Landscape2.GridType.SQUARE, 3);
            for (int step = 0; step < 1024; step++) {
                landscape.advance();
            }
            totalLivingCells += landscape.countLivingCells();
        }
        double avg = (double) totalLivingCells / r; // Ensure division is done correctly
        double[] result = new double[]{avg};
        simulationCache.put(cacheKey, result);
        return result;
    }
    private void updateChart(String key, double chance, double[] data) {
        double avg = data[0];
    // Use a default value for standard deviation if not available
        double stdv = data.length > 1 ? data[1] : avg * 0.1; // Default to 10% of avg if not provided
    
        LineChart<Number, Number> chart = charts.get(key);

        // Ensure the chart has enough series before accessing them
        while (chart.getData().size() < 3) {
            chart.getData().add(new XYChart.Series<>());
        }

        XYChart.Series<Number, Number> mainSeries = chart.getData().get(0);
        XYChart.Series<Number, Number> upperSeries = chart.getData().get(1);
        XYChart.Series<Number, Number> lowerSeries = chart.getData().get(2);

        // Add data points for average, lower, and upper bounds
        mainSeries.getData().add(new XYChart.Data<>(chance, avg));
        double lower = avg - stdv;
        double upper = avg + stdv;
        lowerSeries.getData().add(new XYChart.Data<>(chance, lower));
        upperSeries.getData().add(new XYChart.Data<>(chance, upper));

        // Update the yAxis upper bound to the maximum average living cells
        NumberAxis yAxis = (NumberAxis) chart.getYAxis();
        double currentMax = getMaxAverageLivingCellsForKey(key); // Get the maximum average living cells
        yAxis.setUpperBound(currentMax); // Set upper bound to max average living cells
    }

    private void startSimulation() {
        if (visualSimulationThread != null && visualSimulationThread.isAlive()) return;

        visualSimulationThread = new Thread(() -> {
            isSimulationRunning = true;

            while (isSimulationRunning) {
                currentLandscape.advance();
                double averageLivingCells = currentLandscape.countLivingCells();
                Platform.runLater(() -> averageLivingCellsLabel.setText("Average Living Cells: " + averageLivingCells));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        visualSimulationThread.setDaemon(true);
        visualSimulationThread.start();
    }
    // Add this method to iterate over different chances and update the charts
    private void runSimulations() {
        double[] chances = {0.1, 0.2, 0.3, 0.4, 0.5}; // Example chance values
        for (double chance : chances) {
            double[] result = simulateForChance(max, max, chance);
            updateChart("Simulation with chance: " + chance, chance, result);
        }
    }
    public static void main(String[] args) {
        if (args.length > 1) {
            try {
                max = Integer.parseInt(args[0]);
                steps = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid arguments. Using default values 4");
            }
        }
        if (args.length > 2) setOutputDirectory(args[1]);
        launch(args);
    }
}