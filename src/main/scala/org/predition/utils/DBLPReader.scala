package org.predition.utils

import java.nio.file.{Files, Paths}

import org.apache.spark.sql.{Dataset, SparkSession}

import scala.collection.mutable

/**
  * Created by mohammedi on 3/29/17.
  * contient des méthode qui permet de lire les données des fichiers dblp et 
  * d'extraire les informations relatives à la procedure de prediction, ces
  * information represente pour le moment la relation co author
  */

object DBLPReader {

  def main(args: Array[String]): Unit = {
    //Logger.getLogger("org").setLevel(Level.OFF)
    //Logger.getLogger("akka").setLevel(Level.OFF)
    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("Link Prediction")
      .getOrCreate()


    read(spark, "/home/mohammedi/IdeaProjects/PFEWeb/sparkApplication/input/dblp/data.txt")
  }

  def read(spark: SparkSession, path: String): Dataset[(Int, Couple)] = {
    import spark.implicits._
    if (Files.notExists(Paths.get("years"))) prepareData(spark, path)
    else spark.read.parquet("years").as[(Int, Couple)]
  }

  def prepareData(spark: SparkSession, path: String): Dataset[(Int, Couple)] = {
    import spark.implicits._

    val map: mutable.Map[Int, mutable.MutableList[Couple]] = mutable.Map()

    def notAlone(authors: String) = authors.substring(2).split(',').filterNot(_.matches("[ ]*")).length > 1

    val content = spark.read.textFile(path)

    val authorsWithYears = content
    .filter(line => line.startsWith("#t") || (line.startsWith("#@") && notAlone(line)))
    .filter(!_.isEmpty())
    .mapPartitions { iterator =>
      val result = mutable.MutableList[(Int, Couple)]()
      while (iterator.hasNext) {
        var line = iterator.next()
        var authors = ""
        while (iterator.hasNext && line.startsWith("#@")) {
          authors = line.substring(2)
          line = iterator.next()
        }
        if (iterator.hasNext) {
          if (line.startsWith("#t")) {
            val year = line.substring(2).trim.toInt
            val authorsList = authors.split(',').filterNot(_.matches("[ ]*"))
            for (index1 <- 0 until (authorsList.length - 1); index2 <- index1 + 1 until authorsList.length) {
              val c = (year, Couple(authorsList(index1), authorsList(index2)))
              result += c
            }
          }
        }
      }
      result.toIterator
    }
    authorsWithYears.write.parquet("years")
    authorsWithYears
  }
}
