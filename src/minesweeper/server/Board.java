package minesweeper.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

/**
 * This data structure represents a minesweeper board.
 * 
 * Thread safety argument:
 * The board is thread safe because the public methods that each thread can access is synchronized 
 * to "this" so only one thread can modify the board at a time.  
 * 
 * (The board contains cells but the threads/users do not modify the cells directly.  Only the 
 * board modifies the cells.  There is guaranteed to be no rep exposure of any Cell.)
 * 
 */
public class Board {
    private List<List<Cell>> Board = Collections.synchronizedList(new LinkedList<List<Cell>>());
    private int sizeX; //number of columns
    private int sizeY; //number of rows
    private final boolean debug;
    
    /**
     * This constructs a minesweeper board of size sizeX*sizeY
     * where each cell has a 0.25% probability of containing a bomb
     * 
     * @param debug, state
     * @param sizeX width of the board
     * @param sizeY height of the board
     */
    public Board(boolean debug, int sizeX, int sizeY){
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.debug = debug;
        createBoard();
        checkRep();
    }
    
    /**
     * This constructs a minesweeper board given a text file
     * 
     * @param debug, state
     * @param file, text file with a specified format (indicated in the pset directions)
     */
    public Board(boolean debug, File file){
        this.debug = debug;
        createBoard(file);
        checkRep();
    }
    
    /**
     * One of two helper methods to create a board.  
     * This method creates a board given if the constructor without a file is called.
     */
    private void createBoard(){
        //System.out.println("Create Board " + sizeX + ", " + sizeY);
        for(int i = 0; i < sizeX; i++){
            List<Cell> column = Collections.synchronizedList(new LinkedList<Cell>());
            for(int j = 0; j < sizeY; j++){
                column.add(new Cell(addRandomizedBomb()));
            }
            Board.add(column);
        }
    }
    
    /**
     * One of two helper methods to create a board.  
     * This method creates a board given if the constructor with a file is called.
     */
    private void createBoard(File file){

        //read in file
        ArrayList<String> linesInFile = new ArrayList<String>();
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(file));
          //add all lines to linesInFile
            String line = reader.readLine();
            while (line != null) {
                linesInFile.add(line);
                line = reader.readLine();
            }
            reader.close();
            
            String firstLine = linesInFile.get(0);
            int spaceLoc = firstLine.indexOf(" ");
            sizeX = Integer.valueOf(firstLine.substring(0, spaceLoc));
            sizeY = Integer.valueOf(firstLine.substring(spaceLoc+1, firstLine.length()));
//            System.out.println("Create Board with File "+ sizeX + ", " + sizeY);
            linesInFile.remove(0); //now only the contents of the board remain

            //double check the board has the correct number of rows
            if(linesInFile.size() != sizeY){
                throw new RuntimeException("File improperly formatted."); 
            }
            
            //builds the placeholders for the board
            //adds the appropriate number of lists (the columns) to the Board
            for(int x = 0; x < sizeX; x++){
                List<Cell> column = Collections.synchronizedList(new LinkedList<Cell>());
                Board.add(column);   
            }

            int col = 0;
            for(String curLine: linesInFile){
                String lineNoSpace = curLine.replace(" ", "");
                //double check the size of the line, makes sure the text file is formatted correctly
                if (lineNoSpace.length() != sizeX) {
                    throw new RuntimeException("File improperly formatted.");
                } else {
                    char[] lineOfChars = lineNoSpace.toCharArray();
                    
                    for(char curChar: lineOfChars){
                        if(curChar == '1'){
                            Board.get(col).add(new Cell(true));
                        }else if(curChar == '0'){
                            Board.get(col).add(new Cell(false));
                        }else{
                            throw new RuntimeException("File improperly formatted. A char other than 0 or 1 is found in the board");
                        }
                        col++;
                    }
                    col = 0;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Couldn't read in file.");
        } 
    }
    
    private boolean addRandomizedBomb(){
        Random randNumGenerator = new Random(); 
        int randNum = randNumGenerator.nextInt(4); //generates random integer from 0 (inclusive) to 4 (exclusive)
        if(randNum == 0) return true; // 1/4 chance of being 0
        return false;
    }
    
    public synchronized String look(){
        String board = "";
        String cellState;
        for(int j = 0; j < sizeY; j++){
            for(int i = 0; i < sizeX; i++){
                cellState = Board.get(i).get(j).look();
                switch(cellState){
                case "untouched": board += "- ";
                    break;
                case "flagged":   board += "F ";
                    break;
                case "dug":       board += getNeighboringBombNum(i,j) + " ";
                    break;
                }
            }
            //get rid of an extra space at the end of the line
            //substring(start, end), end is inclusive
            board = board.substring(0, board.length()-1);
            board +="\n";
        }
        board = board.replace("0", " ");
        return board;
    }
    
    private boolean isValidPoint(int i, int j) {
        return (i >= 0 && j >= 0 && i < sizeX && j < sizeY);
    }
    
    //note: this method is only called on a space if the cell is already dug
    //      so the cell itself can not contain a bomb
    private int getNeighboringBombNum(int i, int j){
        int bombCount = 0;

        for(int x = i-1; x<= i+1; x++){
            for(int y = j-1; y <= j+1; y++){
                if(isValidPoint(x, y)) bombCount += Board.get(x).get(y).isBomb();
            }
        }
        return bombCount;
    }
 
    public synchronized String flag (int i, int j){
        if(isValidPoint(i, j) ) Board.get(i).get(j).flag();
        
        checkRep();
        return look();
    }
    
    public synchronized String deflag (int i, int j){
        if(isValidPoint(i, j) ) Board.get(i).get(j).deflag();
        checkRep();
        return look();
    }
    
    public synchronized String dig (int i, int j){
        boolean explosion = false;
        
        if(isValidPoint(i, j) ){
            Cell curCell = Board.get(i).get(j);
            if(curCell.isUntouched()){
                explosion = curCell.dig();
                if(getNeighboringBombNum(i,j) == 0) digNeighbors(i,j);
                if(explosion) return "BOOM!\r\n";
            }
        }
        checkRep();
        return look();
    }
    
    //if called, guaranteed that no neighboring bombs (b/c placement in dig method)
    private void digNeighbors(int i, int j){
        for(int x = i-1; x<= i+1; x++){
            for(int y = j-1; y <= j+1; y++){
                dig(x,y);
            }
        }
    }
    
    public boolean isDebug(){
        return debug;
    }
    
    public String getBoardSizeMessage(){
        return "Board: " + sizeX + " columns by " + sizeY + " rows.";
    }
    
    public void checkRep(){
        boolean checkSizeX = (Board.size() == sizeX);
        boolean checkSizeY = true;
        for(List<Cell> list: Board) if(list.size() != sizeY) checkSizeY = false;
        assertTrue("The board no longer matches the initialized sizes.  The board is incorrect.", checkSizeX && checkSizeY);
    }
    
}
