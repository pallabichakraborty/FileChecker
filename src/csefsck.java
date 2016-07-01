import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

/*
 * Program Name: csefsck
 * Description: This program examines if the custom file system on which this program is run is consistent as per the file system specification
 * Developed By: Pallabi Chakraborty
 * References
 * File Editing - http://stackoverflow.com/questions/13741751/modify-the-content-of-a-file-using-java
 *                http://alvinalexander.com/java/edu/qanda/pjqa00009.shtml
 * 
 * Program Changes History
 * ----------------------------------------------------------------------------------------------------------------------------------------------
 * Date of Change                                                         Description of the Changes
 * ----------------------------------------------------------------------------------------------------------------------------------------------
 * 16-Nov-2015                                                            Initial Draft
 * 19-Nov-2015                                                            Added logic of isIndexBlock in directoryLinkMap. This will
 *                                                                        help in differentiating a index block from data block
 * 20-Nov-2015                                                            Methods added to fix the inconsistency issues                                                       
 * ----------------------------------------------------------------------------------------------------------------------------------------------
 */
public class csefsck {

	/********************************* Constants used in the Program *********************************/
	//Variable for holding the standard Device Id for the file system
	static int stdDeviceId = 20;
	//Variable to store the common pattern of the file naming of the file blocks
	static String basicFileName = "fusedata";
	//Variable to store the file number of the Super Block
	static int superBlockFileNum = 0;
	//Variable to store the charset to use for decoding
	static Charset charset = Charset.forName("ISO-8859-1");
	//Variable to store maximum block size
	static int blockSize=4096;
	//Variable to store the pointer size
	static int pointerSize=10;
	
	/********************************* Super Block Related Variables *********************************/
	//Variable to store the current directory where the program and the file system is stored.
	static File currentDirectory;
	//Variable to store the maximum number of blocks possible in the file system
    static int maxBlocks = 0;
    //Variable to store starting block number of the free list 
    static int freeStart = 0;
    //Variable to store ending block number of the free list
    static int freeEnd = 0;
    //Variable to store the file block number of the root directory
    static int root = 0;
    //Variable to store the file device id
    static int deviceId=0;
    
    /********************************* Operational Variables *********************************/
    //ArrayList to store the directory level mapping of all the directories in the file system
    //Contains String array - One row and 4 columns String[1][4]. First column contains the parent block number, second contains the block type - f or d
    // Third contains the block name/identifier and the fourth contains the actual block number
    static ArrayList<String[][]> directoryLinkList = new ArrayList<String[][]>();
    //HashSet to store the list of all the directories in the file system
    static HashSet<String> directoryList = new HashSet<String>();
    //Flag to indicate that there is a device ID inconsistency
    static boolean isDeviceIDInconsistent=false;
    

	public static void main(String[] args) throws IOException 
	{
		// Find the absolute path of the current directory
		currentDirectory = new File(new File(".").getAbsolutePath());
		// System.out.println(currentDirectory.getAbsolutePath());
		// System.out.println(currentDirectory.getCanonicalPath());

		// Accessing the super block to get the file system details
        List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + superBlockFileNum), charset);

        //Split the comma separated file into groups of key value pairs
        //Super Block format: {creationTime: 1429434844, mounted: 5, devId:20, freeStart:1, freeEnd:25, root:26, maxBlocks:10000}
		for (String line : lines) 
		{
			String[] wordblocks = line.replace("{", "").replace("}", "").split(",");
			for (String block : wordblocks) 
			{
				String[] attributeValue = block.split(":");
				
				// The DeviceID is correct
                if (attributeValue[0].trim().equals("devId")) 
                {
                	deviceId=Integer.parseInt(attributeValue[1].trim());
                	utilities.checkDeviceId(deviceId);
                }
                // Perform the CreationTime Future DateTime Check
                else if (attributeValue[0].trim().equals("creationTime")) 
                {
                	utilities.futureDateTimeCheck(Long.parseLong(attributeValue[1].trim()), attributeValue[0].trim(),basicFileName + "." + superBlockFileNum);
                }
                // Read the value of maximum blocks
                else if (attributeValue[0].trim().equals("maxBlocks")) 
                {
                    maxBlocks = Integer.parseInt(attributeValue[1].trim());
                }
               // Read the block referring to address for free block lists
                else if (attributeValue[0].trim().equals("freeStart")) 
                {
                    freeStart = Integer.parseInt(attributeValue[1].trim());
                }
                // Read the block referring to address for free block lists
                else if (attributeValue[0].trim().equals("freeEnd")) 
                {
                    freeEnd = Integer.parseInt(attributeValue[1].trim());
                }
                // Read the block referring to address of the root directory
                else if (attributeValue[0].trim().equals("root")) 
                {
                    root = Integer.parseInt(attributeValue[1].trim());
                }
			}
		}
		
		//Fix Device Id inconsistency
		if(isDeviceIDInconsistent==true)
		{
			utilities.fixInconsistentDeviceId(Paths.get(currentDirectory.toString(), basicFileName + "." + superBlockFileNum).toString(),deviceId) ;
		}
		
		// Mapping the root with all the child directories details
		utilities.directoryLinkMap(root);
		
		/*
        //Check the mapped data
		for (int i = 0; i < directoryLinkList.size(); i++) 
		{
			String[][] myString = new String[1][4]; 
			myString =directoryLinkList.get(i); 
			for (int j = 0; j < 4; j++) 
			{
				System.out.print(myString[0][j]); 
			} 
			System.out.print("\n");

		}*/
		
		// Fetch the distinct folders in a HashSet directoryList
		utilities.fetchDistinctDirectories();
         

		// Validate that the free block list is accurate this includes
        // a.Making sure the free block list contains ALL of the free blocks
        // b.Make sure than there are no files/directories stored on items listed in the free block list
		 utilities.checkFreeBlockList(maxBlocks, freeStart, freeEnd, root);
		 
		// Checking the dates in different directories
		 utilities.directoryDateChecks(directoryList);
		 
		 // Each directory contains . and .. and their block numbers are correct
		 utilities.checkCurrentParentDirectory();

		 // Running link map again as there may have been changes due to the check in the checkCurrentParentDirectory check
		 //Initializing directoryLinkList to null
		directoryLinkList.clear();
		utilities.directoryLinkMap(root);
		 
		// Each directory’s link count matches the number of links in the filename_to_inode_dict
		 utilities.checkLinkCount();
		 
		// If the data contained in a location pointer is an array, that indirect is one
		 utilities.checkIndirectIndexPointerCounts();
		 
		 //That the size is valid for the number of block pointers in the location array. The three possibilities are:
         //a.    size<blocksize if  indirect=0 and size>0
         //b.    size<blocksize*length of location array if indirect!=0
         //c.    size>blocksize*(length of location array-1) if indirect !=0
		 utilities.checkFileSize();

      //Releasing resources
		directoryLinkList=null;
		directoryList=null;
		
	}
	
	/******************************************Utilities Class: Start************************************************************/
	private static class utilities
	{
		/*
		 * Method to check for the valid device Id
		 * Takes the device Id to be compared as an input parameter
		 * Does not return any value, does a System Out with the error in case the device Id does not comply
		 */
		public static void checkDeviceId(int deviceId) 
		{
	        if (deviceId != stdDeviceId) 
	        {
	            System.out.println("Error: Device ID Inconsistency: Device ID is " + deviceId + "; this should be "+stdDeviceId);
	            //Setting the flag to true
	            isDeviceIDInconsistent=true;
	        }
	    }
		
		/*
		 * Method to fix the device Id issue in case there is an inconsistency
		 * Accepts the file name, the device Id found in the super block entry
		 * Does not return any value, fixes the issue and prints a message stating the file entry has been fixed.
		 */
		public static void fixInconsistentDeviceId(String superblockfile,int deviceId) 
		{
			List<String> filelines = new ArrayList<String>();
			String line = null;
			
			try {
				File superblock = new File(superblockfile);
				FileReader fr = new FileReader(superblock);
				BufferedReader br = new BufferedReader(fr);
				//Replace the devId key value pair with the correct data
				while ((line = br.readLine()) != null) 
				{
					line = line.replace("devId:"+deviceId, "devId:"+stdDeviceId);
					filelines.add(line);
				}
				
				fr.close();
				br.close();

				FileWriter fw = new FileWriter(superblock);
				BufferedWriter out = new BufferedWriter(fw);
				
				for(String s : filelines)
				{
					out.write(s);
				}
	
				out.flush();
				out.close();
				
				System.out.println("INFO: Device Id reinitialized to standard device ID "+stdDeviceId+" in the super block entry");
			} 
			catch (Exception ex) 
			{
				ex.printStackTrace();
			}
		}
		
		/* 
		 * Method to format a unix date time ref. Epoch to a human presentable format
		 * Takes a long format epoch ref. unix time
		 * Returns a date time formatted String of the format MM/dd/yyyy HH:mm:ss
		 */
		public static String dateFormatter(long unixDateTime) {
			//Convert the date time to seconds level
	        Date date = Date.from(Instant.ofEpochSecond(unixDateTime / 1000));
	        //Apply formatting as per requirement, the timezone is as per system timezone
	        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	        String formattedDate = df.format(date);
	        return formattedDate;
	    }
		
		/* 
		 * Method to check if a particular datetime is a future date time or not
		 * Takes the long version of the date time, the variable name containing the date time and the file name containing the date time
		 * Does not return any value however does output a message on the console in case of occurence of a future date time
		 */
		public static void futureDateTimeCheck(long datetime, String timeVariable, String fileName) 
		{
			//Set the input date time in date format
	        Date cmpDateTime = new Date();
            cmpDateTime.setTime((long) datetime);
            //Retrieve the system date time
	        Date currentDate = new Date();
	        currentDate.setTime((long) System.currentTimeMillis());

	        // Method to calculate the days difference between two dates
	        // System.out.println("Current Date Time:"+dateFormatter(currentDate.getTime()));
	        // System.out.println("Date Time being Compared:"+dateFormatter(cmpDateTime.getTime()));
	        float diffInDays = (float) ((currentDate.getTime() - cmpDateTime.getTime()));

	        // System.out.println("Difference between date time:"+diffInDays);
	        if (diffInDays < 0) {
	            System.out.println("Error: Date Time Inconsistency: Future Date Time Found in " + fileName + " for "
	                    + timeVariable + ":" + dateFormatter(datetime) + "; Current Date Time:"
	                    + dateFormatter(currentDate.getTime()));
	        }
	    }
		
		
		/*
		 * Method takes the directory list and prints an error in case of any Future date time found in the block entries
		 * Takes the HashSet containing the list of directories
		 * This does not return any value however in case of future date being found in any of the directory entry prints an error on the console
		 */
		public static void directoryDateChecks(HashSet<String> directoryList) {
			// All times are in the past, nothing in the future
			//Read every directory entry
			try {
				for (String directory : directoryList) {
					List<String> lines;
					lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + directory),charset);

					for (String line : lines) {
						//Split the lines in the blocks into pairs of key values
						String[] wordblocks = line.replace("{", "").replace("}", "").split(",");
						for (String block : wordblocks) {
							//Splitting key value pairs so that the individual values can be retrieved
							String[] attributeValue = block.split(":");

							//Check for the permissible variables with date and time and print an error in case of any inconsistency
							if (attributeValue[0].trim().equals("atime") || attributeValue[0].trim().equals("ctime")
									|| attributeValue[0].trim().equals("mtime")) 
							{
								futureDateTimeCheck(Long.parseLong(attributeValue[1].trim()), attributeValue[0].trim(),basicFileName + "." + directory);
							}
						}


					}

					for (int i = 0; i < directoryLinkList.size(); i++) 
					{
						String[][] directoryLink = new String[1][4];
						directoryLink = directoryLinkList.get(i);
						//System.out.println(directoryLink[0][0]+" "+directoryLink[0][1]+" "+directoryLink[0][3]+" "+Integer.parseInt(directory));
						if(Integer.parseInt(directoryLink[0][0])==Integer.parseInt(directory) && directoryLink[0][1].equals("f"))
						{

							List<String> filelines;
							filelines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + directoryLink[0][3]),charset);

							for (String line : filelines) {
								//Split the lines in the blocks into pairs of key values
								String[] wordblocks = line.replace("{", "").replace("}", "").split(",");
								for (String block : wordblocks) 
								{
									//Splitting key value pairs so that the individual values can be retrieved
									String[] attributeValue = block.split(":");


									//Check for the permissible variables with date and time and print an error in case of any inconsistency
									if (attributeValue[0].trim().equals("atime") || attributeValue[0].trim().equals("ctime")
											|| attributeValue[0].trim().equals("mtime")) 
									{

										futureDateTimeCheck(Long.parseLong(attributeValue[1].trim()), attributeValue[0].trim(),basicFileName + "." + directoryLink[0][3]);
									}
								}
							}
						}
					}

				}
			} catch (IOException e) {

				e.printStackTrace();
			}

		}
		
		/*
		 * Method to generate a mapping data structure which contains the parent and the child folders/files details.
		 * Takes the highest level parent block number as in parameter.
		 * Does not return any value however however populates the array list with the details of the blocks
		 */
		public static void directoryLinkMap(int fileNumber) 
		{

	        try {
	        	//Read all the lines in the directory block
	        	//Directory entry example- {size:1033, uid:1000, gid:1000, mode:16877, atime:1323630836, ctime:1323630836, mtime:1529544887, linkcount:1, filename_to_inode_dict: {d:.:26, d:..:26}}
	            List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + fileNumber), charset);
	            for (String line : lines) {
	            	//Check for the word filename_to_inode_dict, as the details of the linked files are provided under this key value
	                int startPoint = line.indexOf("filename_to_inode_dict");
	                //Remove the word filename_to_inode_dict and enclosing brackets and trim the spaces.
	                String[] linkData = line.substring(startPoint).replace("filename_to_inode_dict:", "").replace("{", "").replace("}", "").trim().split(",");
	                //Data generated similar to d:.:26
	                for (String data : linkData) 
	                {
	                	//2-D array to store the values which will be loaded to directoryLinkList
                        String[][] fileDetails = new String[1][4];
                        //Array to store the data generated when split to get the directory key and block number as value.
	                    String[] fileData = data.split(":");
                        //Data spilt into fileData[0]=d fileData[1]=. fileData[2]=26
	                    fileDetails[0][0] = Integer.toString(fileNumber);
	                    fileDetails[0][1] = fileData[0].trim();
	                    fileDetails[0][2] = fileData[1].trim();
	                    fileDetails[0][3] = fileData[2].trim();
	                    //Add the generated array to the Arraylist
	                    directoryLinkList.add(fileDetails);

	                    //If the entry is a directory then recursively run the method for all the child directories
	                    if (fileData[0].trim().equals("d")&& !(fileData[1].trim().equals(".") || fileData[1].trim().equals(".."))) 
	                    {
	                    	//fileData[2] contains the directory block number
	                        directoryLinkMap(Integer.parseInt(fileData[2].trim()));
	                    } 
	                    //If the entry is a file
	                    else if (fileData[0].trim().equals("f")) 
	                    {
	                    	//Declaring variable to store the file data/index block number
	                        int fileLocation = 999999999;
	                        //Declaring variable to verify if a file block number is data file or index block
	                        boolean isIndexBlock=false;
	                        
	                    	// Read the file with fileData[2] providing the file block number
	                        // File sample entry -{size:14500, uid:1, gid:1, mode:33261, linkcount:1, atime:1323630836, ctime:1323630836, mtime:1323630836, indirect:1 location:28}
	                        List<String> filelines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + fileData[2].trim()),charset);
                            for (String fileline : filelines) 
                            {
                            	//Split the data using comma delimiter
	                            String[] filelinedata = fileline.split(",");
	                            for (String linedata : filelinedata) 
	                            {
	                            	//Targeting on the split data containing indirect key, e.g. indirect:1 location:28}
                                    if (linedata.contains("indirect")) 
                                    {
                                    	//Split to get the indirect key value pair separated from the file data/index block number
	                                    String[] fileAddressValue = linedata.trim().split(" ");

	                                    for (String value : fileAddressValue) 
	                                    {
                                            //Removing the additional enclosing brackets and splitting to get individual key value pairs
	                                        String[] attributeValue = value.replace("{", "").replace("}", "").trim().split(":");
                                            //Getting the file location data after split. Split will break the data similar to attributeValue[0]=location, attributeValue[1]=28
	                                        if (attributeValue[0].trim().equals("location")) {
	                                            fileLocation = Integer.parseInt(attributeValue[1].trim());
	                                        }
	                                    }
                                        
	                                    //Populating the array to populate the file details, as file data does not have individual identifier thus putting file block number in the identifier column
	                                    fileDetails = new String[1][4];
	                                    fileDetails[0][0] = fileData[2].trim();
	                                    fileDetails[0][1] = "f";
	                                    fileDetails[0][2] = fileData[2].trim();
	                                    fileDetails[0][3] = Integer.toString(fileLocation);
	                                    directoryLinkList.add(fileDetails);
                                        
	                                    //Check if the location is pointer to a index block
	                                    List<String> fileblocks = Files.readAllLines(Paths.get(currentDirectory.toString(),basicFileName + "." + Integer.toString(fileLocation)), charset);
	                                    for (String block : fileblocks) 
	                                    {
	                                    	isIndexBlock=false;
                                            //Split the data in the file data/index block using comma delimiter. Data in the index block of the form “X, Y, Z” to indicate that X, Y and Z are in the list
	                                        String[] fileBlockData = block.trim().split(",");
	                                        
	                                        //Check if the file block is a index block or data block
	                                        for (String fileblock : fileBlockData) 
	                                        {
	                                        	//Check if the data is a number and if number 
	                                            if (fileblock.matches("[-+]?\\d*\\.?\\d+") && Integer.parseInt(fileblock) < maxBlocks) {
	                                            	isIndexBlock=true;
	                                            }
	                                            else
	                                            {
	                                            	isIndexBlock=false;
	                                            	break;
	                                            }
	                                        }
	                                        
	                                        //If the pointer is a index block, then get the details of all the data blocks from the index block
	                                        if(isIndexBlock==true)
	                                        {
	                                        	for (String fileblock : fileBlockData) 
	                                        	{

	                                        		String[][] fileBlockDetails = new String[1][4];
	                                        		fileBlockDetails[0][0] = Integer.toString(fileLocation);
	                                        		fileBlockDetails[0][1] = "f";
	                                        		fileBlockDetails[0][2] = Integer.toString(fileLocation);
	                                        		fileBlockDetails[0][3] = fileblock;
                                                    directoryLinkList.add(fileBlockDetails);
                                                    //Releasing variable
	                                        		fileBlockDetails = null;
	                                        	}


	                                        }
	                                        //Releasing variable
	                                        fileBlockData = null;
	                                    }
	                                }
	                            }
	                        }
	                    }
	                    //Releasing variable
	                    fileDetails = null;
	                    fileData = null;
	                }
	            }

	            
	             

	        } catch (IOException e) {

	            e.printStackTrace();
	        }
	        
	        
	    }
		
		/*
		 *  Method to generate a list of directories available in the file system
		 *  Does not accept or return  any parameter. 
		 *  Generates the list of all directories using directoryLinkList as the reference.
		 *  Populates the HashSet directoryList with the details
		 */
		public static void fetchDistinctDirectories() {
	        for (int i = 0; i < directoryLinkList.size(); i++) {
	            String[][] directoryLink = new String[1][4];
	            directoryLink = directoryLinkList.get(i);
	            if (directoryLink[0][1].equals("d")) {
	                directoryList.add(directoryLink[0][0]);
	            }
	        }

	    }
		
       /*
        * Method to verify the authenticity of the free block list
        * Accepts maximum number of blocks in the filesystem, starting block number containing the free block list,
        * Ending block number containing the free block list and the block number of the root directory
        * Does not return any value, however prints any inconsistency in the free block list on the console
        */
	    public static void checkFreeBlockList(int maxBlocks, int freeStart, int freeEnd, int root) 
	    {
	    	//Declaring array to be used to maintain the status of every block in the file system
	        // First column block number, Second column actual status of block,
	        // Third column as per free block list details
	        int[][] blockList = new int[maxBlocks][3];

	        // Prepopulate the array
	        for (int i = 0; i < maxBlocks; i++) {
	            // For the actual status, default taken as empty=0, for population
	            // from free block list, default taken as filled=1
	            blockList[i][0] = i;
	            blockList[i][1] = 0;
	            blockList[i][2] = 1;
	        }

	        // Setting the file number for the superblock to filled
	        blockList[superBlockFileNum][1] = 1;
	        // System.out.println(blockList[0][0]);
	        
	        // Setting the actual status for all the blocks containing the free block list as filled in the block status
	        for (int i = freeStart; i <= freeEnd; i++) {
	            blockList[i][1] = 1;
	        }
	        // Setting the file number for the root block as filled
	        blockList[root][1] = 1;

	        // Update the block status array using the Directory List List
	        //All the blocks which are present in directoryLinkList are pointers hence not free, set the actual status to not free
	        for (int i = 0; i < directoryLinkList.size(); i++) 
	        {
	            String[][] directoryLink = new String[1][4];
	            directoryLink = directoryLinkList.get(i);
	            //Set the actual status to 1 i.e. not free
	            blockList[Integer.parseInt(directoryLink[0][3])][1] = 1;
	        }

	        // Update block status as per the free block list data using the free block list file blocks
	        for (int i = freeStart; i <= freeEnd; i++) {
                //Read all the blocks containing the free block list
	            try {
	                List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + i),charset);
	                for (String line : lines) {
	                	//free space list format - “X, Y, Z” to indicate that X, Y and Z are in the list
	                	//Splitting the block numbers using comma delimiter
	                    String[] freeblocks = line.split(",");
	                    for (String block : freeblocks) {
	                    	//If the block number is in the free block list setting the block to 0 state i.e. free state
	                        blockList[Integer.parseInt(block.trim())][2] = 0;
	                    }
	                }
	            } catch (IOException e) {

	                e.printStackTrace();
	            }

	        }

	        //Comparing all the blocks in the file system to check the consistency of the file system
	        for (int i = 0; i < maxBlocks; i++) {
	        	//In ideal circumstances, the actual status of a block should be in accordance with the free block list. If data present not available in free block list else present
	            //If actual status does not match with the free block list, then this means there is an inconsistency in the file block
	        	if (blockList[i][1] != blockList[i][2]) 
	            {
	            	//If the block points to a file/directory however is also present in the free block list
	                if (blockList[i][1] == 1 && blockList[i][2] == 0) 
	                {
	                    System.out.println("Error: Free Blocks Inconsistency: Block " + blockList[i][0] + " points to a file/directory however is present in free block list");
	                    //Fix Free Block Inconsistencies
	           		    fixInconsistentFreeBlockList(blockList[i][0],true, true) ;
	                } 
	                //If the block is actually free but is not present in the free block list
	                else if (blockList[i][1] == 0 && blockList[i][2] == 1) 
	                {
	                    System.out.println("Error: Free Blocks Inconsistency: Block " + blockList[i][0]  + " does not points to a file/directory however is not present in free block list");
	                    //Fix Free Block Inconsistencies
	           		    fixInconsistentFreeBlockList(blockList[i][0],false, false) ;
	                }
	            }

	        }

	    }
	    
	    
	    /*
	     * Method to correct the free block list for all the inconsistencies
	     * Accepts block Number, boolean hasData which indicates if it actually has data and  boolean for if the block is present in the free block list
	     * Corrects the inconsistency by adding or removing from 
	     */
	    public static void fixInconsistentFreeBlockList(int blockNum,boolean hasData, boolean isPresentFreeBlkList) 
	    {
	    	//Calculating the block in which the block number to be found
	    	// Free Block List number = (block number /(blocksize/pointersize))+1
	    	int freeBlockListNum=Math.floorDiv(blockNum, Math.floorDiv(blockSize, pointerSize))+1;

	    	//If the block has data and is also present in the free block list then it needs to be removed from the free block list
	    	if(hasData==true && isPresentFreeBlkList==true)
	    	{
	    		List<String> filelines = new ArrayList<String>();
	    		String line = null;
	    		File freeBlockListblock=null;

	    		try {
                    //Variable to indicate if the block number could be found so that it could be replaced
	    			boolean blockFound=false;

	    			freeBlockListblock = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + freeBlockListNum).toString());
	    			FileReader fr = new FileReader(freeBlockListblock);
	    			BufferedReader br = new BufferedReader(fr);
	    			//Replace the devId key value pair with the correct data
	    			while ((line = br.readLine()) != null) 
	    			{
	    				if(line.matches(".*\\b"+blockNum+"\\b.*"))
	    				{
	    					blockFound=true;
	    					line = line.replaceAll("\\b, "+blockNum+"\\b", "");
	    				}

	    				filelines.add(line);
	    			}

	    			//In case if the block is not found in the expected free block list block then traverse through all the free block list blocks
	    			if(blockFound==false)
	    			{
	    				for (int i = freeStart; i <= freeEnd; i++) 
	    				{
	    					if(blockFound==false)
	    					{
	    						freeBlockListblock = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + i).toString());
	    						fr = new FileReader(freeBlockListblock);
	    						br = new BufferedReader(fr);
	    						//Replace the devId key value pair with the correct data
	    						while ((line = br.readLine()) != null) 
	    						{
	    							if(line.matches(".*\\b"+blockNum+"\\b.*"))
	    							{
	    								blockFound=true;
	    								line = line.replaceAll("\\b, "+blockNum+"\\b", "");
	    							}

	    							filelines.add(line);
	    						}
	    					}
	    				}
	    			}

	    			fr.close();
	    			br.close();

	    			if(blockFound==true)
	    			{

	    				FileWriter fw = new FileWriter(freeBlockListblock);
	    				BufferedWriter out = new BufferedWriter(fw);

	    				for(String s : filelines)
	    				{
	    					out.write(s);
	    				}

	    				out.flush();
	    				out.close();

	    				System.out.println("INFO: Entry for block "+blockNum+" removed from the free block list");

	    			}
	    		} 
	    		catch (Exception ex) 
	    		{
	    			ex.printStackTrace();
	    		}

	    	}
	    	//In case a block number does not have data and also is not there in the free block list then it needs to be appended in the free block list
	    	else if(hasData==false && isPresentFreeBlkList==false)
	    	{
	    		{

	    			BufferedWriter bw = null;

	    			try {

	    				bw = new BufferedWriter(new FileWriter(Paths.get(currentDirectory.toString(), basicFileName + "." + freeBlockListNum).toString(), true));
	    				bw.write(", "+blockNum);

	    				bw.flush();
	    			} 
	    			catch (IOException ioe) 
	    			{
	    				ioe.printStackTrace();
	    			} 
	    			finally {                       
	    				if (bw != null) 
	    				{
	    					try 
	    					{
	    						bw.close();
	    					} 
	    				    catch (IOException ioe2) 
	    				    {

	    					}
	    				}
	    					
	    			} 

	    		} 
	    		System.out.println("INFO: Entry for block "+blockNum+" added to the free block list");
	    	}

	    }

	    
	    /*
	     * Method to check if the . and .. entries have been added and are as per the requirements for all directories
	     * Does not accept any in parameter
	     * Reads the HashSet directoryList, which contains the list of all directories of the file system and checks for inconsistency
	     * Does not return any value, however if there is any issues in the . and .. entries, then prints error on the console
	     */
	    public static void checkCurrentParentDirectory() 
	    {
	    	//Taking the list of all the directories in the file system for comparison
	    	for (String directory : directoryList) 
	    	{
	    		//Variable to indicate if the . entry is present for a directory or not
	    		boolean isCurrentDirectoryPresent = false;
	    		//Variable to indicate if the .. entry is present or not
	    		boolean isParentDirectoryPresent = false;
	    		//Variable to indicate if the current directory block number is correct or not
	    		boolean isCurrentDirectoryValid = true;
	    		//Variable to indicate if the Parent directory block number is correct or not
	    		boolean isParentDirectoryValid = true;
	    		//Variable to store the current directory value in case it is found inconsistent
	    		int currentDirectory = 0;
	    		//Variable to store the parent directory value in case it is found inconsistent
	    		int parentDirectory = 0;
	    		//Variable to store the computed parent directory
	    		int actParentDirectory = 0;

	    		//Taking the Array List with the directory details along with the linked directory details
	    		for (int i = 0; i < directoryLinkList.size(); i++) 
	    		{
	    			String[][] directoryLink = new String[1][4];
	    			directoryLink = directoryLinkList.get(i);

	    			//Check if the directory entry same as compared directory present with a . entry linked it
	    			//if present then the condition is satisfied
	    			if (Integer.parseInt(directoryLink[0][0]) == Integer.parseInt(directory)
	    					&& directoryLink[0][2].trim().equals(".")) 
	    			{
	    				isCurrentDirectoryPresent = true;

	    				//In case the . entry is available then check if the . entry points to the block number equal to the compared directory block number
	    				//If found then found consistent else found inconsistent
	    				if (Integer.parseInt(directoryLink[0][0]) == Integer.parseInt(directory)
	    						&& directoryLink[0][2].trim().equals(".")
	    						&& Integer.parseInt(directoryLink[0][3]) != Integer.parseInt(directory)) 
	    				{
	    					isCurrentDirectoryValid = false;
	    					currentDirectory = Integer.parseInt(directoryLink[0][3]);
	    				}
	    			}

	    			//Check if the directory entry same as compared directory present with a .. entry linked it
	    			//if present then the condition is satisfied
	    			if (Integer.parseInt(directoryLink[0][0]) == Integer.parseInt(directory)
	    					&& directoryLink[0][2].trim().equals("..")) 
	    			{
	    				isParentDirectoryPresent = true;

	    				//If the .. entry is found and the block being compared is the root, then the root block number to be present
	    				if (Integer.parseInt(directoryLink[0][0]) == Integer.parseInt(directory)
	    						&& directoryLink[0][2].trim().equals("..")
	    						&& Integer.parseInt(directoryLink[0][0]) == root) 
	    				{
	    					if (Integer.parseInt(directoryLink[0][3]) != root) 
	    					{
	    						isParentDirectoryValid = false;
	    						parentDirectory = Integer.parseInt(directoryLink[0][3]);
	    					}

	    				} 
	    				//If the block number being compared is not root then check in the directory linking array for the entry which has a pointer to the block number being compared.
	    				// The .. entry should point to the folder which has a pointer to the block being compared
	    				else 
	    				{
	    					boolean parentFound = false;
	    					

	    					for (int j = 0; j < directoryLinkList.size(); j++) 
	    					{
	    						String[][] directoryLinkForParentCmp = new String[1][4];
	    						directoryLinkForParentCmp = directoryLinkList.get(j);

	    						//Other than root block, no directory can have itself as its parent block
	    						if (directoryLink[0][2].trim().equals("..")
	    								&& Integer.parseInt(directoryLink[0][3])==Integer.parseInt(directoryLink[0][0])) 
	    						{
	    							parentFound = false;
	    						}
	    						
	    						//Check if the parent folder is found valid
	    						if (Integer.parseInt(directoryLinkForParentCmp[0][0]) == Integer.parseInt(directoryLink[0][3])
	    								&& Integer.parseInt(directoryLinkForParentCmp[0][3]) == Integer.parseInt(directoryLink[0][0]) 
	    								&& directoryLink[0][2].trim().equals("..")
	    								&& parentFound == false
	    								&& Integer.parseInt(directoryLink[0][3])!=Integer.parseInt(directoryLink[0][0])) 
	    						{
	    							parentFound = true;
	    						}
	    						
	    						//System.out.println(Integer.parseInt(directoryLinkForParentCmp[0][3])+" "+Integer.parseInt(directoryLink[0][0])+" "+directoryLink[0][2].trim() );
	    						
                                //If the parent is not found, then find the actual parent block number
	    						if(Integer.parseInt(directoryLinkForParentCmp[0][3]) == Integer.parseInt(directoryLink[0][0]) && directoryLink[0][2].trim().equals(".."))
	    						{
	    							actParentDirectory=Integer.parseInt(directoryLinkForParentCmp[0][0]);
	    						}

	    					}
	    					
	    					
	    					if (parentFound == false) {
	    						isParentDirectoryValid = false;
	    						 parentDirectory = Integer.parseInt(directoryLink[0][3]);
	    					}
	    					
	    					
	    				}
	    			}
	    			//If parent details are not found, then ascertaining the parent details
	    			else
	    			{
	    				if(Integer.parseInt(directory)==root)
	    				{
	    					actParentDirectory=root;
	    				}
	    				else
	    				{
	    					for (int j = 0; j < directoryLinkList.size(); j++) 
	    					{
	    						String[][] directoryLinkForParentCmp = new String[1][4];
	    						directoryLinkForParentCmp = directoryLinkList.get(j);


	    						if(Integer.parseInt(directoryLinkForParentCmp[0][3]) == Integer.parseInt(directoryLink[0][0]) && directoryLink[0][2].trim().equals(".."))
	    						{
	    							actParentDirectory=Integer.parseInt(directoryLinkForParentCmp[0][0]);
	    						}

	    					}
	    				}


	    			}

	    		}

	    		//Print error if . entry is not available
	    		if (isCurrentDirectoryPresent == false) {
	    			System.out.println("Error: Directory Entry Error: Block " + basicFileName + "." + directory + " does not have \".\" entry");
	    			//Fix the issue by adding the directory details
	    			addCurrentDirectoryEntry(Integer.parseInt(directory));
	    		}

	    		//Print error if .. entry is not available
	    		if (isParentDirectoryPresent == false) {
	    			System.out.println("Error: Directory Entry Error: Block " + basicFileName + "." + directory + " does not have \"..\" entry");
	    			//Fix the issue by adding directory details
	    			addParentDirectoryEntry(Integer.parseInt(directory),actParentDirectory);
	    		}

	    		//Print error if . entry does not point to the block number equal to the block number being compared
	    		if (isCurrentDirectoryValid == false) {
	    			System.out.println("Error: Directory Entry Error: Block " + basicFileName + "." + directory + " has a wrong entry for current folder block address of " + currentDirectory);
	    			//Fix the issue by modifying directory details
	    			replaceCurrentDirectoryEntry(Integer.parseInt(directory), currentDirectory);
	    		}

	    		//Print error if .. entry does not point to the parent block number
	    		if (isParentDirectoryValid == false) {
	    			System.out.println("Error: Directory Entry Error: Block " + basicFileName + "." + directory + " has a wrong entry for parent folder block address of " + parentDirectory);
	    			//Fix the issue by modifying directory details
	    			replaceParentDirectoryEntry(Integer.parseInt(directory),  actParentDirectory, parentDirectory);
	    		}
	    	}
	    }

	    /*
	     * Method to add current directory . entry in case it is not present
	     * Accepts the block number which does not have the current directory details
	     * Does not return any value however appends the . entry in the directory represented by the block number
	     */
		public static void addCurrentDirectoryEntry(int blockNumber)
	    {
	    	File file = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + blockNumber).toString());
	    	Scanner scanner;
			try {
				scanner = new Scanner(file).useDelimiter("\n");
				String line = scanner.next();
				//Append the entry in the end of all the directory entries
		    	String newLine = line.substring(0, line.length()-2) + ", d:.:"+blockNumber + line.substring(line.length()-2);
		    	FileWriter writer = new FileWriter(file);
		    	writer.write(newLine);
		    	writer.close();
		    	scanner .close();
		    	System.out.println("INFO: Current directory details added in the block number "+blockNumber);
			} 
			catch (FileNotFoundException e) 
			{
				e.printStackTrace();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
	    	
			
	    }
		
		/*
	     * Method to add parent directory .. entry in case it is not present
	     * Accepts the block number which does not have the parent directory details
	     * Does not return any value however appends the .. entry in the directory represented by the block number
	     */
		public static void addParentDirectoryEntry(int blockNumber,int parentBlockNumber)
	    {
	    	File file = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + blockNumber).toString());
	    	Scanner scanner;
			try {
				scanner = new Scanner(file).useDelimiter("\n");
				String line = scanner.next();
				//Append the entry in the end of all the directory entries
		    	String newLine = line.substring(0, line.length()-2) + ", d:..:"+parentBlockNumber + line.substring(line.length()-2);
		    	FileWriter writer = new FileWriter(file);
		    	writer.write(newLine);
		    	writer.close();
		    	scanner .close();
		    	System.out.println("INFO: Parent directory details added in the block number "+blockNumber);
			} 
			catch (FileNotFoundException e) 
			{
				e.printStackTrace();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}

	    }
		
		/*
	     * Method to modify current directory . entry in case it is inconsistent
	     * Accepts the block number and the . entry value in the file
	     * Does not return any value however modifies the . entry in the directory represented by the block number
	     */
		public static void replaceCurrentDirectoryEntry(int blockNumber, int fileDirectoryValue)
		{
			List<String> filelines = new ArrayList<String>();
			String line = null;
			File blockData=null;

			try {

				blockData = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + blockNumber).toString());
				FileReader fr = new FileReader(blockData);
				BufferedReader br = new BufferedReader(fr);
				//Replace the Current directory block number with the actual current directory number
				while ((line = br.readLine()) != null) 
				{	line = line.replaceAll("\\bd:.:"+fileDirectoryValue, "d:.:"+blockNumber);
					

					filelines.add(line);
				} 
				
				fr.close();
				br.close();

				FileWriter fw = new FileWriter(blockData);
				BufferedWriter out = new BufferedWriter(fw);

				for(String s : filelines)
				{
					out.write(s);
				}

				out.flush();
				out.close();

				System.out.println("INFO: Entry for the . directory has been modified for the block "+blockNumber);


			}
			catch (Exception ex) 
			{
				ex.printStackTrace();
			}
		}

		/*
	     * Method to modify parent directory .. entry in case it is inconsistent
	     * Accepts the block number, the derived actual parent block number and the .. entry value in the file
	     * Does not return any value however modifies the .. entry in the directory represented by the block number
	     */
		public static void replaceParentDirectoryEntry(int blockNumber, int actParentDirectory, int parentDirectory)
		{
			List<String> filelines = new ArrayList<String>();
			String line = null;
			File blockData=null;
			boolean replaceDone=false;

			try {

				blockData = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + blockNumber).toString());
				FileReader fr = new FileReader(blockData);
				BufferedReader br = new BufferedReader(fr);
				//Replace the Parent directory block number with the actual Parent directory number
				while ((line = br.readLine()) != null) 
				{
					line = line.replaceAll("\\bd:..:"+parentDirectory+"\\b", "d:..:"+actParentDirectory);
                    filelines.add(line);
                    replaceDone=true;
				} 
				
				fr.close();
				br.close();

				FileWriter fw = new FileWriter(blockData);
				BufferedWriter out = new BufferedWriter(fw);

				for(String s : filelines)
				{
					out.write(s);
				}

				out.flush();
				out.close();

				if(replaceDone==true)
				{
					System.out.println("INFO: Entry for the .. directory has been modified for the block "+blockNumber+", please run again in case of any inconsistency ");
				}

			}
			catch (Exception ex) 
			{
				ex.printStackTrace();
			}
		}
	    
	    /*
	     * Method to check if the link counts provided in the directories are equal to the actual folder entries it points to in the inode
	     * Does not accept any parameter, works on the directory list and checks with the values in the folder child map generated for the file system
	     * Does not return any value however prints an error to console in case there crops up any inconsistency between the link counts and the 
	     * actual blocks being pointed to
	     */
	    public static void checkLinkCount() 
	    {

	    	//Variable to store the link count provided in the directory entry
	        int linkCount = 0;
	        //Variable to store the pointer counts provided in the inode entry
	        int inodeEntriesCount = 0;
	        try {
	        	//Retrieving the "linkcount" number for the directories
	            for (String directory : directoryList) 
	            {
	            	List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + directory),charset);
	                for (String line : lines) 
	                {
	                    String[] wordblocks = line.replace("{", "").replace("}", "").split(",");
	                    for (String block : wordblocks) 
	                    {
	                        String[] attributeValue = block.split(":");

	                        if (attributeValue[0].trim().equals("linkcount")) 
	                        {
	                            linkCount = Integer.parseInt(attributeValue[1].trim());
	                        }
	                    }
	                }

	                //Re-initializing the variable so that correct counts are set
	                inodeEntriesCount = 0;

	                for (int i = 0; i < directoryLinkList.size(); i++) 
	                {
	                    String[][] directoryLink = new String[1][4];
	                    directoryLink = directoryLinkList.get(i);

	                    //Incrementing the counter in case a matched pointer found
	                    if (Integer.parseInt(directoryLink[0][0]) == Integer.parseInt(directory)) 
	                    {
	                        inodeEntriesCount++;
	                    }
	                }

	                //If the link count is not equal to the number of pointers in the inode then print error on the console
	                if (linkCount != inodeEntriesCount) 
	                {
	                    System.out.println("Error: Directory Entry Error: Link Count Error in the block " + basicFileName
	                            + "." + directory + " linkCount in directory:" + linkCount + " however linked to "
	                            + inodeEntriesCount + " blocks");
	                    //Fix the link Counts
	                    replaceLinkCountEntry(Integer.parseInt(directory), inodeEntriesCount, linkCount);
	                }
	            }

	        } catch (IOException e) {

	            e.printStackTrace();
	        }

	    }
	    
	    /*
	     * Method to fix the Link Count inconsistency
	     * Aceepts the block number, actual number of inode entries and the link Count provided in the directory entry
	     * Does not return any value however fixes the linkcount to to number of inode entries of the directory
	     */
	    public static void replaceLinkCountEntry(int blockNumber, int actualLinkedBlocks, int linkCount)
		{
			List<String> filelines = new ArrayList<String>();
			String line = null;
			File blockData=null;

			try {

				blockData = new File(Paths.get(currentDirectory.toString(), basicFileName + "." + blockNumber).toString());
				FileReader fr = new FileReader(blockData);
				BufferedReader br = new BufferedReader(fr);
				//Updating the link count
				while ((line = br.readLine()) != null) 
				{
					line = line.replaceAll("\\blinkcount:"+linkCount+"\\b", "linkcount:"+actualLinkedBlocks);
                    filelines.add(line);
				} 
				
				fr.close();
				br.close();

				FileWriter fw = new FileWriter(blockData);
				BufferedWriter out = new BufferedWriter(fw);

				for(String s : filelines)
				{
					out.write(s);
				}

				out.flush();
				out.close();

				System.out.println("INFO: Entry for the Link Count has been modified for the block  "+blockNumber);


			}
			catch (Exception ex) 
			{
				ex.printStackTrace();
			}
		}
	    
	    /*
	     * Method to check if the indirect index value of the file is consistent
	     * Does not accept any parameter, uses the block map to check if indirect index has been correctly set
	     * Does not output any value however prints error in case the indirect count is set if there is an indirect index involved
	     */
	    public static void checkIndirectIndexPointerCounts() 
	    {
            //variable to store the number of blocks which are indirectly linked
	        int fileBlockCount = 0;

	        for (int i = 0; i < directoryLinkList.size(); i++) {

	            String[][] directoryLink = new String[1][4];
	            directoryLink = directoryLinkList.get(i);
	            //If the directory entry is found be a file then go head and ignore the entry
	            if (directoryLink[0][1].equals("f")) 
	            {
	            	//re-initialize the variable
	                fileBlockCount = 0;
	                //In case the indirect index found, then count the number of indirect files which are linked
	                for (int j = 0; j < directoryLinkList.size(); j++) 
	                {
	                    String[][] directoryAssocLink = new String[1][4];
	                    directoryAssocLink = directoryLinkList.get(j);

	                    if (Integer.parseInt(directoryAssocLink[0][0]) == Integer.parseInt(directoryLink[0][3])) 
	                    {
	                        fileBlockCount++;
	                    }
	                }

	                //If number of links found more than 0 then check if the indirect variable should be set to 1
	                if (fileBlockCount > 0) 
	                {

	                    try {
	                        List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + directoryLink[0][3]),charset);

	                        for (String fileline : lines) 
	                        {
	                            String[] filelinedata = fileline.split(",");
	                            for (String linedata : filelinedata) 
	                            {

	                                if (linedata.contains("indirect")) {
	                                    String[] fileAddressValue = linedata.trim().split(" ");

	                                    for (String value : fileAddressValue) 
	                                    {

	                                        String[] attributeValue = value.replace("{", "").replace("}", "").trim()
	                                                .split(":");

	                                        if (attributeValue[0].trim().equals("indirect")) 
	                                        {
                                               // If indirect=0 and there are indirect indexing available then output an error
	                                            if (Integer.parseInt(attributeValue[1].trim()) == 0) 
	                                            {
	                                                System.out.println("Error: File Entry Error: Indirect IndexCount Error: Block "
	                                                                + basicFileName + "." + directoryLink[0][0]
	                                                                + " has indirect set to "
	                                                                + Integer.parseInt(attributeValue[1].trim())
	                                                                + " however it points to " + fileBlockCount
	                                                                + " blocks");
	                                            }
	                                        }
	                                    }
	                                }
	                            }
	                        }

	                    } 
	                    catch (IOException e) 
	                    {

	                        e.printStackTrace();
	                    }

	                }
	            }
	        }
	    }
	    
	    /*
	     * Method to check the file size, and if it is as per the file system specifications
	     * Does not accept any parameter, uses the directory link map to check the number of indirect indexes which are present and number of locks indirectly linked
	     * Does not return any value however in case the size is not consistent then outputs an error
	     */
	    public static void checkFileSize()
	    {
	    	//Variable to store the value of "indirect" provided in the file entry
	    	int indirectCount=0;
	    	//Variable to store the size of the file as provided in the file entry
	    	int size=0;
	    	//Variable to store the number of blocks linked with a file using indirect index block
	    	int indexArraySize=0;
	    	
	    	//Running the check on all the directory map entries which are "f" i.e. file type
	    	for (int i = 0; i < directoryLinkList.size(); i++) 
	    	{

	    		String[][] directoryLink = new String[1][4];
	    		directoryLink = directoryLinkList.get(i);


	    		if (directoryLink[0][1].equals("f")) 
	    		{
	    			//Retrieve the size and indirect values for the files
	    			try 
	    			{
	    				indirectCount=0;
	    				size=0;
	    				indexArraySize=0;

	    				List<String> lines = Files.readAllLines(Paths.get(currentDirectory.toString(), basicFileName + "." + directoryLink[0][0]),charset);

	    				for (String fileline : lines) {
	    					String[] filelinedata = fileline.split(",");
	    					for (String linedata : filelinedata) {
	    						if (linedata.contains("size"))
	    						{
	    							String[] attributeValue = linedata.replace("{", "").replace("}", "").trim().split(":");
	    							size=Integer.parseInt(attributeValue[1].trim());
	    						}

	    						if (linedata.contains("indirect")) {
	    							String[] fileAddressValue = linedata.trim().split(" ");

	    							for (String value : fileAddressValue) {

	    								String[] attributeValue = value.replace("{", "").replace("}", "").trim().split(":");

	    								if (attributeValue[0].trim().equals("indirect")) 
	    								{

	    									indirectCount=Integer.parseInt(attributeValue[1].trim());

	    								}

	    							}
	    						}
	    					}
	    				}

	    				//If the indirect value is greater than 0 then check the number of blocks being pointed by the indirect indexes
	    				if(indirectCount>0)
	    				{

	    					for (int k = 0; k < directoryLinkList.size(); k++) 
	    					{

	    						String[][] directoryData = new String[1][4];
	    						directoryData = directoryLinkList.get(k);
	    						if(Integer.parseInt(directoryData[0][0])==Integer.parseInt(directoryLink[0][3]))
	    						{
	    							indexArraySize=indexArraySize+1;
	    						}
	    					}
	    				}

	    			} 
	    			catch (IOException e) 
	    			{

	    				e.printStackTrace();
	    			}
	    			
	    			//If the indirect count is 0 and size is greater than block size then print error
	    			if(indirectCount==0)
	    			{
	    				if(size>blockSize)
	    				{
	    					System.out.println("Error: File Entry Error: Size Mismatch for block "+basicFileName + "." + directoryLink[0][0]+" as size is "+size+" with indirect as "+indirectCount+" and indirect index pointing to "+indexArraySize+" blocks");
	    				}
	    			}
	    			//If indirect is set to a value not equal to 0 then if the size is lesser than the block size then output error
	    			//If indirect is set to a value not equal to 0 then if the size is not between blocksize*length of location array  and blocksize*(length of location array-1) then output error 
	    			else
	    			{
	    				if(size<blockSize)
	    				{
	    					System.out.println("Error: File Entry Error: Size Mismatch for block "+basicFileName + "." + directoryLink[0][0]+" as size is "+size+" with indirect as "+indirectCount+" and indirect index pointing to "+indexArraySize+" blocks");
	    				}
	    				else
	    				{
	    					if((size>blockSize*indexArraySize)||(size<blockSize*(indexArraySize-1)))
	    					{
	    						System.out.println("Error: File Entry Error: Size Mismatch for block "+basicFileName + "." + directoryLink[0][0]+" as size is "+size+" with indirect as "+indirectCount+" and indirect index pointing to "+indexArraySize+" blocks");
	    					}
	    				}
	    			}
	    		}

	    	}

	    }

	}
	
	/******************************************Utilities Class: End************************************************************/

}
