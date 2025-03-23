import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Random;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Landscape2 {
    public enum GridType {
        SQUARE, TRIANGLE, HEXAGON
    }
    private GridType gridType;
    private Cell[][][] landscape;
    private int rows, columns, depth, dimensions, lower, low, up, upper;
    private double initialChance;
    private Map<List<Integer>, Cell> highDimLandscape;

    public Landscape2(int rows, int columns, int depth, double initialChance, 
                     int lower, int low, int up, int upper, GridType gridType, int dimensions) {
        this.rows = rows;
        this.columns = columns;
        this.depth = depth;
        this.dimensions = Math.max(3, dimensions); // Minimum 3D
        this.initialChance = initialChance;
        this.lower = lower;
        this.low = low;
        this.up = up;
        this.upper = upper;
        this.gridType = gridType;
    
        initializeLandscape();
    }

    public Landscape2(int rows, int columns, int depth, double initialChance, 
                     int lower, int low, int up, int upper) {
        this(rows, columns, depth, initialChance, lower, low, up, upper, GridType.SQUARE, 3);
    }
    
    private void initializeLandscape() {
        if (dimensions > 3) {
            initializeHighDimLandscape(); // Call the method to initialize high-dimensional landscape
        } else {
            landscape = new Cell[rows][columns][depth];
            reset();
        }
    }

    public int countLivingCells() {
        if (dimensions <= 3) {
            // Current implementation for 3D
            int count = 0;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < columns; col++) {
                    for (int z = 0; z < depth; z++) {
                        if (landscape[row][col][z].getAlive()) {
                            count++;
                        }
                    }
                }
            }
            return count;
        } else {
            // Implementation for higher dimensions
            int count = 0;
            for (Cell cell : highDimLandscape.values()) {
                if (cell.getAlive()) {
                    count++;
                }
            }
            return count;
        }
    }

    public void reset() {
        Random rand = new Random();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                for (int k = 0; k < depth; k++)
                    landscape[i][j][k] = new Cell(rand.nextDouble() < initialChance);
            }
        }
    }
    public int getRows() {
        return rows;
    }
    public int getCols() {
        return columns;
    }
    public int getDepth() {
        return depth;
    }
    public Cell getCell(int row, int col, int z) {
        return landscape[row][col][z];
    }
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < depth; k++) {
            sb.append("Layer ").append(k).append(":\n");
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++)
                    sb.append(landscape[i][j][k].toString());
                    
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    // Modified to handle different grid types
    public ArrayList<Cell> getNeighbors(int row, int col, int z) {
        switch(gridType) {
            case TRIANGLE:
                return getTriangleNeighbors(row, col, z);
            case HEXAGON:
                return getHexagonNeighbors(row, col, z);
            case SQUARE:
            default:
                return getSquareNeighbors(row, col, z);
        }
    }
    private ArrayList<Cell> getSquareNeighbors(int row, int col, int z) {
        ArrayList<Cell> neighbors = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    if (i == 0 && j == 0 && k == 0) continue; // Skip the cell itself
                    int newRow = row + i;
                    int newCol = col + j;
                    int newZ = z + k;
                    if (newRow >= 0 && newRow < rows && 
                        newCol >= 0 && newCol < columns && 
                        newZ >= 0 && newZ < depth) {
                        neighbors.add(landscape[newRow][newCol][newZ]);
                    }
                }
            }
        }
        return neighbors;
    }
    private ArrayList<Cell> getTriangleNeighbors(int row, int col, int z) {
        ArrayList<Cell> neighbors = new ArrayList<>();
        boolean isUpTriangle = ((row + col) % 2 == 0);
        
        // Triangle grids have different connectivity patterns for up vs down triangles
        // For up triangles (pointing up), neighbors are at:
        // - Left, right, below (same z)
        // - Plus relevant cells in z+1 and z-1 layers
        if (isUpTriangle) {
            // Check same layer (z) neighbors
            addCellIfValid(neighbors, row, col-1, z);     // Left
            addCellIfValid(neighbors, row, col+1, z);     // Right
            addCellIfValid(neighbors, row+1, col, z);     // Bottom
            
            // Add neighbors from layer above (z-1) if it exists
            if (z > 0) {
                addCellIfValid(neighbors, row-1, col, z-1);   // Top in layer above
                addCellIfValid(neighbors, row, col-1, z-1);   // Top-left in layer above
                addCellIfValid(neighbors, row, col+1, z-1);   // Top-right in layer above
            }
            // Add neighbors from layer below (z+1) if it exists
            if (z < depth-1) {
                addCellIfValid(neighbors, row+1, col-1, z+1); // Bottom-left in layer below
                addCellIfValid(neighbors, row+1, col, z+1);   // Bottom in layer below
                addCellIfValid(neighbors, row+1, col+1, z+1); // Bottom-right in layer below
            }
        } else {
            // For down triangles (pointing down), neighbors are at:
            // - Left, right, above (same z)
            // Plus relevant cells in z+1 and z-1 layers
            
            addCellIfValid(neighbors, row, col-1, z);     // Left
            addCellIfValid(neighbors, row, col+1, z);     // Right
            addCellIfValid(neighbors, row-1, col, z);     // Top
            
            // Add neighbors from layer above (z-1) if it exists
            if (z > 0) {
                addCellIfValid(neighbors, row-1, col-1, z-1); // Top-left in layer above
                addCellIfValid(neighbors, row-1, col, z-1);   // Top in layer above
                addCellIfValid(neighbors, row-1, col+1, z-1); // Top-right in layer above
            }
            
            // Add neighbors from layer below (z+1) if it exists
            if (z < depth-1) {
                addCellIfValid(neighbors, row, col-1, z+1);   // Bottom-left in layer below
                addCellIfValid(neighbors, row+1, col, z+1);   // Bottom in layer below
                addCellIfValid(neighbors, row, col+1, z+1);   // Bottom-right in layer below
            }
        }
        return neighbors;
    }
    
    private ArrayList<Cell> getHexagonNeighbors(int row, int col, int z) {
        ArrayList<Cell> neighbors = new ArrayList<>();
        
        // In a hexagonal grid, each cell has 6 neighbors in the same plane
        // Even rows: neighbors are at (row-1,col-1), (row-1,col), (row,col-1), (row,col+1), (row+1,col-1), (row+1,col)
        // Odd rows: neighbors are at (row-1,col), (row-1,col+1), (row,col-1), (row,col+1), (row+1,col), (row+1,col+1)
        
        boolean isEvenRow = (row % 2 == 0);
        
        if (isEvenRow) {
            // Even row hexagons
            addCellIfValid(neighbors, row-1, col-1, z); // Top-left
            addCellIfValid(neighbors, row-1, col, z);   // Top-right
            addCellIfValid(neighbors, row, col-1, z);   // Left
            addCellIfValid(neighbors, row, col+1, z);   // Right
            addCellIfValid(neighbors, row+1, col-1, z); // Bottom-left
            addCellIfValid(neighbors, row+1, col, z);   // Bottom-right
        } else {
            // Odd row hexagons
            addCellIfValid(neighbors, row-1, col, z);   // Top-left
            addCellIfValid(neighbors, row-1, col+1, z); // Top-right
            addCellIfValid(neighbors, row, col-1, z);   // Left
            addCellIfValid(neighbors, row, col+1, z);   // Right
            addCellIfValid(neighbors, row+1, col, z);   // Bottom-left
            addCellIfValid(neighbors, row+1, col+1, z); // Bottom-right
        }
        // Add neighbors from layer above (z-1) if it exists
        if (z > 0) addCellIfValid(neighbors, row, col, z-1);   // Direct above
        // Add neighbors from layer below (z+1) if it exists
        if (z < depth-1) addCellIfValid(neighbors, row, col, z+1);   // Direct below
        return neighbors;
    }
    // Helper method to add a cell to neighbors if coordinates are valid
    private void addCellIfValid(ArrayList<Cell> neighbors, int row, int col, int z) {
        if (row >= 0 && row < rows && col >= 0 && col < columns && z >= 0 && z < depth) {
            neighbors.add(landscape[row][col][z]);
        }
    }
    public void advance() {
        // Check if the dimensions are greater than 3
        if (dimensions > 3) {
            // If so, call the advanceHighDim() method
            advanceHighDim();
            return;
        }
        // Create a temporary grid to store the next state
        Cell[][][] tempGrid = new Cell[rows][columns][depth];

        // Initialize the temp grid with new cells first
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                for (int k = 0; k < depth; k++) {
                    // Create new cells with current state
                    tempGrid[i][j][k] = new Cell(landscape[i][j][k].getAlive());
                }
            }
        }
        // Now calculate the next state
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                for (int k = 0; k < depth; k++) {
                    // Get the neighbors of the current cell
                    ArrayList<Cell> neighbors = getNeighbors(i, j, k);
                    // Update the state of the current cell based on its neighbors
                    tempGrid[i][j][k].updateState(neighbors, lower, low, up, upper);
                }
            }
        }
        // Set the landscape to the temporary grid
        landscape = tempGrid;
    }

    // Modified to draw a specific layer or projection
    public void draw(Graphics g, int scale, int layer) {
        for (int x = 0; x < getRows(); x++) {
            for (int y = 0; y < getCols(); y++) {
                g.setColor(getCell(x, y, layer).getAlive() ? Color.BLACK : Color.gray);
                g.fillOval(x * scale, y * scale, scale, scale);
            }
        }
    }
    // Initialize higher dimensional landscape
    private void initializeHighDimLandscape() {
        highDimLandscape = new HashMap<>();
        generateCoordinates(new ArrayList<>(), 0);
    }
    
    private void generateCoordinates(List<Integer> coords, int dim) {
        if (dim == dimensions) {
            boolean isAlive = Math.random() < initialChance;
            highDimLandscape.put(new ArrayList<>(coords), new Cell(isAlive));
            return;
        }
        
        int size = getSize(dim);
        for (int i = 0; i < size; i++) {
            coords.add(i);
            generateCoordinates(coords, dim+1);
            coords.remove(coords.size()-1);
        }
    }
    // Get a cell from higher dimensional space
    public Cell getHighDimCell(List<Integer> coords) {
        if (dimensions <= 3) {
            // Use standard 3D array if within normal dimensions
            if (coords.size() >= 3) return landscape[coords.get(0)][coords.get(1)][coords.get(2)];
            return null;
        } else return highDimLandscape.get(coords);
    }
    // Advance the simulation for higher dimensional space
    public void advanceHighDim() {
        Map<List<Integer>, Cell> nextGen = new HashMap<>();
        
        for (Map.Entry<List<Integer>, Cell> entry : highDimLandscape.entrySet()) {
            List<Integer> coords = entry.getKey();
            Cell current = entry.getValue();
            ArrayList<Cell> neighbors = getHighDimNeighbors(coords);
            boolean alive = current.getAlive();
            int liveNeighbors = 0;
            for (Cell neighbor : neighbors) if (neighbor.getAlive()) liveNeighbors++;

            boolean nextAlive = alive 
                ? (liveNeighbors >= lower && liveNeighbors <= upper) 
                : (liveNeighbors >= low && liveNeighbors <= up);

            nextGen.put(coords, new Cell(nextAlive));
        }
        highDimLandscape = nextGen;
    }
    public GridType getGridType() {
        return gridType;
    }
    public int getDimensions() {
        return dimensions;
    }
    private ArrayList<Cell> getHighDimNeighbors(List<Integer> coords) {
        ArrayList<Cell> neighbors = new ArrayList<>();
        generateNeighborCoordinates(neighbors, coords, new ArrayList<>(), 0);
        return neighbors;
    }

    private void generateNeighborCoordinates(ArrayList<Cell> neighbors, List<Integer> center, List<Integer> current, int dim) {
        if (dim == dimensions) {
            if (!current.equals(center)) {
                Cell cell = getHighDimCell(current);
                if (cell != null) neighbors.add(cell);
            }
            return;
        }
        int pos = center.get(dim);
        for (int offset = -1; offset <= 1; offset++) {
            int newPos = pos + offset;
            if (newPos < 0 || newPos >= getSize(dim)) continue;
        
            current.add(newPos);
            generateNeighborCoordinates(neighbors, center, current, dim+1);
            current.remove(current.size()-1);
        }
    }
    private int getSize(int dim) {
        return (dim < 3) ? new int[]{rows, columns, depth}[dim] : Math.max(rows, Math.max(columns, depth));
    }
}