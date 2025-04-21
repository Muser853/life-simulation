import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Landscape2 {
    public enum GridType {
        SQUARE, TRIANGLE, HEXAGON
    }
    private final GridType gridType;
    public static Cell[][][] landscape;
    public final int rows, columns, depth, dimensions;
    private long[] dimensionMasks;
    private int[] bitsPerDimension, maxCoordinates;
    private Map<Long, Cell> highDimLandscape;

    public Landscape2(int... dimensionsSizes) {
        this(
            dimensionsSizes[0], 
            dimensionsSizes[1], 
            dimensionsSizes.length > 2 ? dimensionsSizes[2] : 1, 
            GridType.SQUARE, // Default grid type
            dimensionsSizes.length
        );
    }
    public Landscape2(int rows, int columns, int depth, GridType gridType, int dimensions) {
        this.dimensions = Math.max(3, dimensions); // Minimum 3D
        this.gridType = gridType;
        this.highDimLandscape = new HashMap<>(); // Explicit initialization
        this.rows = rows;
        this.columns = columns;
        this.depth = depth;
        
        if (dimensions <= 3) {
            landscape = new Cell[rows][columns][depth];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k] = new Cell();
                    }
                }
            }
        } else {
            initializeHighDimLandscape();
        }
    }
    // private void initializeBitmaskConfig() {
    //     bitsPerDimension = new int[dimensions];
    //     dimensionMasks = new long[dimensions];
    //     int totalBits = 0;
    //     for (int i = 0; i < dimensions; i++) {
    //         int size = maxCoordinates[i];
    //         bitsPerDimension[i] = (int) (Math.log(size) / Math.log(2)) + 1;
    //         dimensionMasks[i] = (1L << bitsPerDimension[i]) - 1;
    //         totalBits += bitsPerDimension[i];
    //         if (totalBits > 64) {
    //             throw new IllegalArgumentException("Too many dimensions or too large size");
    //         }
    //     }
    // }
    private long createCoordinateMask(List<Integer> coords) {
        long mask = 0;
        int offset = 0;
        for (int i = 0; i < dimensions; i++) {
            int coord = coords.get(i);
            mask |= ((long) coord) << offset;
            offset += bitsPerDimension[i];
        }
        return mask;
    }
    private int getCoordinate(long mask, int dim) {
        int offset = 0;
        for (int i = 0; i < dim; i++) {
            offset += bitsPerDimension[i];
        }
        return (int) ((mask >> offset) & dimensionMasks[dim]);
    }

    private int getBitOffset(int dim) {
        int offset = 0;
        for (int i = 0; i < dim; i++) {
            offset += bitsPerDimension[i];
        }
        return offset;
    }
    public Cell getHighDimCell(List<Integer> coords) {
        long mask = createCoordinateMask(coords);
        return highDimLandscape.get(mask);
    }

    public Landscape2(int rows, int columns) {
        this(rows, columns, 1, GridType.SQUARE, 2);
    }
    public void initializeLandscape(int lower, int low, int up, int upper) {
        if (dimensions > 3) {
            initializeHighDimLandscape();
        } else {
            landscape = new Cell[rows][columns][depth];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k].setBounds(lower, low, up, upper);
                    }
                }
            }
        }
    }
    public void setInitialLivingCells(int count) {
        clearAllCells(); // Clear all cells first
        List<Cell> allCells = new ArrayList<>();
        if (dimensions <= 3) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        allCells.add(landscape[i][j][k]);
                    }
                }
            }
        } else {
            allCells.addAll(highDimLandscape.values());
        }
        Collections.shuffle(allCells);
        for (int i = 0; i < Math.min(count, allCells.size()); i++) {
            allCells.get(i).setAlive(true);
        }
    }

    private void clearAllCells() {
        if (dimensions <= 3) {
            for (var cellLayer : landscape) {
                for (var row : cellLayer) {
                    for (var cell : row) {
                        cell.setAlive(false);
                    }
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                cell.setAlive(false);
            }
        }
    }

    public int countLivingCells() {
        int count = 0;
        if (dimensions <= 3) {
            for (Cell[][] cells : landscape) {
                for (Cell cell[] : cells) {
                    for (Cell cel : cell) {
                        if (cel.getAlive()) 
                            count++;
                    }   
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                if (cell.getAlive()) 
                    count++;
            }
        }
        return count;
    }
    public void updateLower(int lower) {
        if (dimensions <=3) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k].updateLower(lower);
                    }
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                cell.updateLower(lower);
            }
        }
    }

    public void updateLow(int low) {
        if (dimensions <=3) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k].updateLow(low);
                    }
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                cell.updateLow(low);
            }
        }
    }
    
    public void updateUp(int up) {
        if (dimensions <=3) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k].updateUp(up);
                    }
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                cell.updateUp(up);
            }
        }
    }

    public void updateUpper(int upper) {
        if (dimensions <=3) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < columns; j++) {
                    for (int k = 0; k < depth; k++) {
                        landscape[i][j][k].updateUpper(upper);
                    }
                }
            }
        } else {
            for (Cell cell : highDimLandscape.values()) {
                cell.updateUpper(upper);
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
    public Cell getCell(List<Integer> coords) {
        if (coords.size() != dimensions) {
            throw new IllegalArgumentException("Coordinates must have " + dimensions + " dimensions");
        }
        if (dimensions <= 3) {
            return landscape[coords.get(0)][coords.get(1)][coords.get(2)];
        } else {
            long mask = createCoordinateMask(coords);
            return highDimLandscape.get(mask);
        }
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
            addCellIfValid(neighbors, row-1, col-1, z); // Top-left
            addCellIfValid(neighbors, row-1, col, z);   // Top-right
            addCellIfValid(neighbors, row, col-1, z);   // Left
            addCellIfValid(neighbors, row, col+1, z);   // Right
            addCellIfValid(neighbors, row+1, col-1, z); // Bottom-left
            addCellIfValid(neighbors, row+1, col, z);   // Bottom-right
        } else {
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
    private void addCellIfValid(ArrayList<Cell> neighbors, int row, int col, int z) {
        if (row >= 0 && row < rows && col >= 0 && col < columns && z >= 0 && z < depth)
            neighbors.add(landscape[row][col][z]);
    }
    public void advance() {
        // Check if the dimensions are greater than 3
        if (dimensions > 3) {
            // If so, call the advanceHighDim() method
            advanceHighDim();
            return;
        }
        // Now calculate the next state
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                for (int k = 0; k < depth; k++) {
                    // Get the neighbors of the current cell
                    ArrayList<Cell> neighbors = getNeighbors(i, j, k);
                    // Update the state of the current cell based on its neighbors
                    landscape[i][j][k].updateState(neighbors);
                }
            }
        }
    }
    // Modified to draw a specific layer or projection
    public void draw(Graphics g, int scale, int layer) {
        List<Integer> base = new ArrayList<>();
        for (int dim = 0; dim < dimensions; dim++) 
            base.add(0); // Initialize all coordinates to 0
        base.set(2, layer); // Set layer as third dimension (z-axis)
        for (int x = 0; x < getSize(0); x++) {
            for (int y = 0; y < getSize(1); y++) {
                List<Integer> coords = new ArrayList<>(base);
                coords.set(0, x);
                coords.set(1, y);
                Cell cell = getCell(coords);
                g.setColor(cell.getAlive() ? Color.BLACK : Color.GRAY);
                g.fillOval(x * scale, y * scale, scale, scale);
            }
        }
    }
        public int getSize(int dim) {
            if (dim >= dimensions)
                throw new IllegalArgumentException("Invalid dimension index");
            return maxCoordinates[dim];
        }
    private void initializeHighDimLandscape() {
        highDimLandscape = new HashMap<>();
        generateCoordinates(new ArrayList<>(), 0);
    }
    private void generateCoordinates(List<Integer> coords, int dim) {
        if (dim == dimensions) {
            long mask = createCoordinateMask(coords); // Convert to mask
            highDimLandscape.put(mask, new Cell(true)); // Use mask as key
            return;
        }
        int size = getSize(dim);
        for (int i = 0; i < size; i++) {
            coords.add(i);
            generateCoordinates(coords, dim+1);
            coords.remove(coords.size()-1);
        }
    }
    private List<Integer> decodeCoordinates(long mask) {
        List<Integer> coords = new ArrayList<>();
        for (int i = 0; i < dimensions; i++) {
            coords.add(getCoordinate(mask, i));
        }
        return coords;
    }
    // Advance the simulation for higher dimensional space
    private void advanceHighDim() {
        Map<Long, Cell> nextGen = new HashMap<>();
        for (Map.Entry<Long, Cell> entry : highDimLandscape.entrySet()) {
            List<Integer> coords = decodeCoordinates(entry.getKey());
            Cell current = entry.getValue();
            ArrayList<Cell> neighbors = getHighDimNeighbors(coords);
            boolean alive = current.getAlive();
            int liveNeighbors = 0;
            for (Cell neighbor : neighbors) 
                if (neighbor.getAlive())
                    liveNeighbors++;
            boolean nextAlive = alive 
                ? (liveNeighbors >= current.getLower() && liveNeighbors <= current.getUpper()) 
                : (liveNeighbors >= current.getLow() && liveNeighbors <= current.getUp());
            nextGen.put(entry.getKey(), new Cell(nextAlive)); // Preserve original key
        }
        highDimLandscape.clear();
        highDimLandscape.putAll(nextGen);
    }
    public GridType getGridType() {
        return gridType;
    }
    public int getDimensions() {
        return dimensions;
    }
    public ArrayList<Cell> getNeighbors(int row, int col, int z) {
        if (dimensions > 3) {
            List<Integer> coords = new ArrayList<>();
            coords.add(row); coords.add(col); coords.add(z);
            for (int dim = 3; dim < dimensions; dim++) {
                coords.add(0); // Assume other dimensions as 0
            }
            return getHighDimNeighbors(coords);
        } else {
            switch(gridType) {
                case TRIANGLE:
                    return getTriangleNeighbors(row, col, z);
                case HEXAGON:
                    return getHexagonNeighbors(row, col, z);
                case SQUARE:
                default: return getSquareNeighbors(row, col, z);
            }
        }
    }
    private ArrayList<Cell> getHighDimNeighbors(List<Integer> coords) {
        long currentMask = createCoordinateMask(coords);
        ArrayList<Cell> neighbors = new ArrayList<>();

        for (int dim = 0; dim < dimensions; dim++) {
            int coord = getCoordinate(currentMask, dim);
            for (int delta : new int[]{-1, 1}) {
                int newCoord = coord + delta;
                if (newCoord < 0 || newCoord >= maxCoordinates[dim])
                    continue;
                int offset = getBitOffset(dim);
                long maskDelta = (long)newCoord << offset;
                long neighborMask = currentMask & ~((dimensionMasks[dim] << offset));
                neighborMask |= maskDelta;
                Cell neighbor = highDimLandscape.get(neighborMask);
                if (neighbor != null) neighbors.add(neighbor);
            }
        }
        return neighbors;
    }
    /*TEST: private boolean isValidCoordinate(List<Integer> coords) {
        for (int i = 0; i < coords.size(); i++) {
            if (coords.get(i) < 0 || coords.get(i) >= maxCoordinates[i]) {
                return false;
            }
        }
        return true;
    }
    private void resetHighDimLandscape() {
        highDimLandscape.clear();
        generateCoordinates(new ArrayList<>(), 0, 0L);
    }

    private void generateCoordinates(List<Integer> coords, int currentDim, long currentMask) {
        if (currentDim == dimensions) {
            highDimLandscape.put(currentMask, new Cell(true));
            return;
        }
        int max = maxCoordinates[currentDim];
        for (int i = 0; i < max; i++) {
            coords.add(i);
            int offset = getBitOffset(currentDim);
            long newMask = currentMask | ((long)i << offset);
            generateCoordinates(coords, currentDim + 1, newMask);
            coords.remove(coords.size() - 1);
        }
    }
    private void generateNeighborCoordinates(ArrayList<Cell> neighbors, List<Integer> center, List<Integer> current, int dim) {
        if (dim == dimensions) {
            if (!current.equals(center)) {
                Cell cell = getHighDimCell(current);
                if (cell != null) 
                    neighbors.add(cell);
            }
            return;
        }
        int pos = center.get(dim);
        for (int offset = -1; offset <= 1; offset++) {
            int newPos = pos + offset;
            if (newPos < 0 || newPos >= getSize(dim)) 
                continue;
            current.add(newPos);
            generateNeighborCoordinates(neighbors, center, current, dim+1);
            current.remove(current.size()-1);
        }
    }*/
}