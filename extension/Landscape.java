import javafx.scene.canvas.GraphicsContext;
import java.util.ArrayList;
import java.util.Random;

public class Landscape {
    private Cell[][] landscape;
    private int rows, columns;
    private double initialChance;

    public Landscape(int rows, int columns) {
        this(rows, columns, 0.5);
    }

    public Landscape(int rows, int columns, double chance) {
        this.rows = rows;
        this.columns = columns;
        this.initialChance = chance;
        landscape = new Cell[rows][columns];
        reset();
    }

    public int countLivingCells() {
        int count = 0;
        for (Cell[] row : landscape) {
            for (Cell cell : row) {
                if (cell.getAlive()) count++;
            }
        }
        return count;
    }

    public void reset() {
        Random rand = new Random();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                landscape[i][j] = new Cell(rand.nextDouble() < initialChance);
            }
        }
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return columns;
    }

    public Cell getCell(int row, int col) {
        return landscape[row][col];
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Cell[] row : landscape) {
            for (Cell cell : row) {
                sb.append(cell.toString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public ArrayList<Cell> getNeighbors(int row, int col) {
        ArrayList<Cell> neighbors = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                int newRow = row + i, newCol = col + j;
                if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < columns)
                    neighbors.add(landscape[newRow][newCol]);
            }
        }
        return neighbors;
    }

    public void advance() {
        Cell[][] tempGrid = new Cell[rows][columns];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                tempGrid[i][j] = new Cell(landscape[i][j].getAlive());
            }
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++)
                tempGrid[i][j].updateState(getNeighbors(i, j));
        }
        landscape = tempGrid;
    }

    public void draw(GraphicsContext g, int scale) {
        // Clear the background with white color
        g.setFill(javafx.scene.paint.Color.WHITE);
        g.fillRect(0, 0, columns * scale, rows * scale);

        // Draw living cells in black
        g.setFill(javafx.scene.paint.Color.BLACK);
        for (int x = 0; x < rows; x++) {
            for (int y = 0; y < columns; y++) {
                if (landscape[x][y].getAlive()) {
                    // Ensure correct scaling and positioning of cells
                    g.fillOval(y * scale, x * scale, scale, scale);
                }
            }
        }
    }
}