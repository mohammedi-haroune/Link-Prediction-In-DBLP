    # Link-Prediction-In-DBLP

    ## How to execute the project in your own computer

    1. Download the github project ot your computer or clone it by executing the following command in a command prompt

    `git clone https://github.com/mohammedi-haroune/Link-Prediction-In-DBLP.git`

    2. Download the [InteliJ IDEA IDE](https://www.jetbrains.com/idea/#chooseYourEdition)

    3. From the menu bar : 

    **File ==> New ==> Project from Existing Source... ===> choose the directory ==> choose SBT from the list ==> Finish**

    ## Running the Example

    In the `Application.main` method : 

    1. Replace the `/path/to/spark/checkpoint` by any directory in your computer (this is required by Spark)

    2. Replace the `path/to/dbpl/file` by the path to the dblp file in you computer 

    > This file must be downloaded from https://aminer.org/citation, please choose a version with the first format (V1-V4, V7, V8) or download the [lastest version](https://static.aminer.org/lab-datasets/citation/dblp.v8.tgz)

    3. Right click and Run

    The result will be saved in a directory named `output` in your project directory, 
    and the attributes informatiosn (weights changes) in a file named `attributesInformation.txt`


    ```
    project   
    │
    └───output
        |
        └───phaseId(startLearning:EndLearning-startLabeling:EndLabeling)
            |
            └───Attributes            
            |   |
            |   └─── {a directory for each attribute classification}
            |
            └───Aggregations
            |   |
            |   └─── {a directory for each Aggregation result}
            |
            └───PhaseInfo
                |
                └─── {a directory describing the current phase}
            

    ```

here's a screenshot showing the example output :

![screenshot](https://github.com/mohammedi-haroune/Link-Prediction-In-DBLP/blob/master/outputScreenShot.png)
