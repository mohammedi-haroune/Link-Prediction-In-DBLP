package org.prediction.utils

import org.apache.spark.sql.SparkSession
import org.prediction.utils.DBLPReader.extractCoAuthorGraph
import Attribute.ALL_ATTRIBUTES
import Aggregator.BORDA
import org.apache.log4j.Logger
import org.apache.log4j.Level

/**
  * Created by mohammedi on 4/2/17.
  */
object Application {
  def main(args: Array[String]): Unit = {


    //Logger.getLogger("org").setLevel(Level.OFF)
    //Logger.getLogger("akka").setLevel(Level.OFF)


    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("GraphFrames")
      .getOrCreate()

    val sc = spark.sparkContext
    val sqlContext = spark.sqlContext

    sc.setCheckpointDir("/home/mohammedi/checkpointSpark")
    val path =
      "/home/mohammedi/IdeaProjects/PFEWeb/sparkApplication/input/dblp/data.txt"

    extractCoAuthorGraph(spark, path, 1970, 1979)

    val phases = Phase.getPhases(1970, 1979, 3, 1)

    phases.foreach { phase =>
      phase.applyAttributes(ALL_ATTRIBUTES)
      phase.applyAggregator(BORDA)
      phase.maximizationWeights()
      phase.saveInformation()
    }

    spark.stop()
  }
}
