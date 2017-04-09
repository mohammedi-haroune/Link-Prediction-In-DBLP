package org.prediction.example

import org.apache.spark.sql.SparkSession
import org.prediction.Aggregator.BORDA
import org.prediction.Phase
import org.prediction.dblp.utils.DBLPReader.extractCoAuthorGraph
import org.prediction.Attribute.ALL_ATTRIBUTES
import org.apache.log4j.Logger
import org.apache.log4j.Level

/**
  * Created by mohammedi on 4/2/17.
  */
object Application {
  def main(args: Array[String]): Unit = {

    //.getLogger("org").setLevel(Level.OFF)
    //Logger.getLogger("akka").setLevel(Level.OFF)

    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("GraphFrames")
      .getOrCreate()

    val sc = spark.sparkContext
    val sqlContext = spark.sqlContext

    sc.setCheckpointDir("/path/to/spark/checkpoint")
    val path = "path/to/dbpl/file"

    extractCoAuthorGraph(spark, path, 1970, 1979)

    val phases = Phase.getPhases(1970, 1979, 3, 1)

    phases.foreach { phase =>
      phase.applyAttributes(ALL_ATTRIBUTES)
      phase.applyAggregator(BORDA)
      phase.changeWeights()
      phase.saveInformation()
    }

    spark.stop()
  }
}
