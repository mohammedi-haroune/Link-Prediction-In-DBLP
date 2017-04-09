# Link-Prediction-In-DBLP

## How to execute the project in your own computer

clone the github project `git clone https://github.com/mohammedi-haroune/Link-Prediction-In-DBLP.git`

download the InteliJ IDEA IDE https://www.jetbrains.com/idea/#chooseYourEdition

From the menu bar : 

File --> New --> Project from Existing Source... ---> choose the directory --> choose SBT from the list --> Finish

## Running the Example

Replace the `/path/to/spark/checkpoint` by any directory in your computer (this is required by Spark)

Replace the `path/to/dbpl/file` by the path to the dblp file in you computer 

This file must be downloaded from https://aminer.org/citation

Right click and Run

The result will be saved in a directory named `output` in your project directory, 
and the attributes informatiosn (weights changes) in a file named `attributesInformation.txt`

