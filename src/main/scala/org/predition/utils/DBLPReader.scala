package org.predition.utils

import java.nio.file.{Files, Paths}
import org.apache.spark.sql.{Dataset, SparkSession}
import scala.collection.mutable

/**
  * Contient des méthodes qui permet de lire les données des fichiers dblp et d'extraire les informations relatives à la
  * procedure de prediction
  *
  * @author mohammedi
  */

object DBLPReader {
  /**
    * Extraire les liens de la relation Co-Author
    *
    * @example '''(2016, Hamza Labbaci, Youcef Aklouf)''' represete une relation co author entre '''Hamza Labbaci''' et
    *          '''Youcef AKlouf''' durant l'année '''2016'''
    * @note cette méthode est creé de tels façon pour ne pas refaire le même travail a chaque fois, c'est pour ça elle
    *       sauvgarde les relation créés la première fois dans un dossier '''years''' pour les lire dans les prochaines
    *       eventuelles invocations
    *       SVP de ne pas supprimer ce dossier pour qu'on puisse optimizer le temps d'exection pour vous
    * @param spark la session courate du spark cette session est utilisé pour manipulé les `Dataset`
    * @param path  le lien du fichier dblp téléchargé de [[https://aminer.org/citation]]
    * @return liens de la relation Co-Author sous la form (année, Couple(nom du 1er auteur, nom du 2eme auteur))
    * @author mohammedi
    */
  def extractCoAuthorRelations(spark: SparkSession, path: String): Dataset[(Int, Couple)] = {
    if (Files.notExists(Paths.get("years"))) {
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
    else spark.read.parquet("years").as[(Int, Couple)]
  }

}
