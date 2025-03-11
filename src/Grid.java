public class Grid{
    //private int[][] grid;

//Start by allocating the 2D array in a single new statement, giving it 3 rows and 5 columns.
    /**
    public Grid(){
        grid = new int[3][5];
    }*/

//Note that the first set of square brackets corresponds to the rows of the matrix, and the second set of square brackets corresponds to the columns.

//Now let's try an alternative method of creating a 2-D array by allocating the row size and column size separately:
//Notice that by this method, the rows of grid are actually different lengths. However, be a little careful with this method: if you try to print out grid[0][0] between the instantiation of grid and the start of the for loop, what happens?

//To fix the null entries of grid, regardless of which way you instantiated the object, you have to go through every location in the grid and create an int to put in it. Do that now and give each location in the grid a random int (we're leaving design choices about what kind of random number up to you).
    /**public Grid(int[][] a){
        grid = a;

    }*/
//When working with 2-D arrays we often use nested for loops:

//When creating a 2-D array, you have to first allocate the 2-D grid of int references, then use a nested loop, as above, to allocate each int element grid[i][j] individually. Assign to each grid[i][j] a new int by sampling a random number (you can refer back to last week for more instructions on how to do this).

//If you try to print out grid, what happens? Presumably, you'll get something like int@somegarbage. Unfortunately, Java Arrays don't natively implement a 'pretty' toString, so you have to loop through Arrays yourself to print things out nicely.
//As a follow-up, if you try to print out grid[0][0], what gets printed out?

//Print out the contents of the 2D array as a 2D grid using a nested for loop. Make it look nice (i.e. we don't care how you format the output as long as you and we can tell what the output means).

//Checking if two matrices are equal
    public static void printer(int[][]a){
        for (int i = 0; i < a.length; i++){
            for (int j = 0; j < a[0].length; j++){
                System.out.println(a[i][j] + "");
            }
            System.out.println(" ");
        }
    }
    

    public static void main(String[] args){
        for (int i =0; i<args.length; i++){
            System.out.println(args[i]);
        }
        ;
        
        int[][] arr1 = new int[2][2];
        int[][] arr2 = new int[2][2];
        int[][] arr3;
        for(int i = 0; i < 2; i++){
            for(int j = 0; j < 2; j++){
                if (args[0].equals("false")){
                    arr1[i][j] = -i-j;
                    arr2[i][j] = -i-j;
                }else{
                    arr1[i][j] = i+j;
                    arr2[i][j] = i+j;
                }
                
            }
        } arr3 = arr1; 
        System.out.println(gridEquals(arr1, arr3)); 
        System.out.println(gridEquals(arr2, arr3));
        transpose(arr1);
    }
    public static  boolean gridEquals(int[][] a, int[][] b){
        for(int i=0;i<a.length;i++) {
            for(int j=0;j<a[0].length;j++) {
                if (a[i][j] != b[i][j]){
                    return false;
                }
            }
        }
        return true;
    }
    public static int [][] transpose(int[][] arr){
        
        int [][] ar = new int[arr.length][arr[0].length];
        printer(arr);
        for(int i=0; i<arr.length; i++){
            for(int j =0; j<arr[0].length; j++){
                ar[j][i] = arr[i][j];
            }
        }
        printer(ar);
        return ar;
    }
}