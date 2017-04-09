package org.prediction

import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.graphframes.GraphFrame
import scala.collection.mutable

/**
  * Created by mohammedi on 3/26/17.
  *
  */
/** Represente un attriubt avec ces différente caractéristique
  * un attribut est définie essentiellement par sa fonction (score) qui donne une certaine similarité (de type [[Double]])
  * entre deux candidates (exemples)
  *
  * @param id     l'identificateur de l'attribut (pour le moment c'est les premier lettre de son nom)
  * @param score  la fonction qui calcule la valeur de l'attribut pour un ensemble de couple dans un graph donné
  */
case class Attribute(
    id: String,
    score: (GraphFrame, Dataset[Example]) => Dataset[(Example, Double)]) {

  /**
    * l'historique de changement de poids cela nous permet de tracer l'évolution des différentes valeurs de
    * poids en terms de temps
    */
  val history: mutable.Map[Int, Double] = mutable.Map()

  private var weight: Double = 0.5

  def getWeight: Double = weight

  def changeWeight(nb: Int, newWeight: Double): Unit = {
    history.put(nb, weight)
    weight = newWeight
  }

  /**
    * Permet de changer la valeur de l'attribut et de sauvegarder la valeur ancienne dans [[history]]
    */
  def changWeight(positives: Dataset[Example],
                  classification: Dataset[(Example, Double)]): Unit = {

    val spark = positives.sparkSession
    import spark.implicits._

    val nbPositives = positives.count()

    val firstNbPositives =
      classification.limit(nbPositives.toInt).select($"_1".as[Example])

    val firstNbPositivesInverse = firstNbPositives.map {
      case Example(c1, c2) => Example(c2, c1)
    }

    val TruePositives = positives
      .intersect(firstNbPositives)
      .union(positives.intersect(firstNbPositivesInverse))

    val negatives = classification
      .map(_._1)
      .except(firstNbPositives)
      .except(firstNbPositivesInverse)

    val nbExamples = classification.count()

    val nbTruePositives = TruePositives.count()

    val nbFalsePositive = nbPositives - nbTruePositives

    val nbNegatives = nbExamples - nbPositives

    val nbFalseNegatives = negatives.intersect(positives).count()

    val nbTrueNegatives = negatives.count() - nbFalseNegatives

    val precision = nbTruePositives.toDouble / nbPositives.toDouble

    val falsePositveRate = nbFalsePositive.toDouble / nbNegatives.toDouble

    scala.tools.nsc.io
      .File("attributesInformation.txt")
      .appendAll(
        s"For the attribute $id" +
          s"P + N = $nbExamples, " +
          s"P = $nbPositives, " +
          s"TP = $nbTruePositives, " +
          s"FP = $nbFalsePositive, " +
          s"N = $nbNegatives, " +
          s"TN = $nbTrueNegatives, " +
          s"FN = $nbFalseNegatives, " +
          s"Precision = $precision, " +
          s"FalsePositiveRate = $falsePositveRate\n")
  }

}

object Attribute {

  val cn = new Attribute("cn", commonNeighbors)
  val jc = new Attribute("jc", jaccardCoefficient)
  val aac = new Attribute("aac", adamicAdarCoeificient)
  val ra = new Attribute("ra", resourceAllocation)
  val acc = new Attribute("aac", agregationOfClusteringCoeficient)
  val pr = new Attribute("pr", agregateOfPageRank)

  /**
    * Associer chaque nom d'attribut a son objet [[org.prediction.Attribute]], cette [[scala.collection.Map]] contient tous
    * les attributs implémentés pour le moment (les attributs citées dans l'article de monsieur Kanawati)
    */
  val ALL_ATTRIBUTES = Seq(cn, acc, pr, jc)

  def jaccardCoefficient(
      graph: GraphFrame,
      exemples: Dataset[Example]): Dataset[(Example, Double)] = {
    val spark = exemples.sparkSession
    import spark.implicits._
    val neighbors = getNeighbors(graph)
    val x = exemples
      .join(neighbors, $"candidate1" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors1"))
    val y = exemples
      .join(neighbors, $"candidate2" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors2"))
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .as[(Long, Long, Array[Long], Array[Long])]
      .map {
        case (c1, c2, n1, n2) =>
          (Example(c1, c2),
           n1.intersect(n2).length.toDouble / n1.union(n2).length.toDouble)
      }
    result
  }

  def adamicAdarCoeificient(
      graph: GraphFrame,
      exemples: Dataset[Example]): Dataset[(Example, Double)] = {
    val spark = exemples.sparkSession
    import spark.implicits._
    val degrees = graph.degrees
    val neighbors = getNeighbors(graph)
    val x = exemples
      .join(neighbors, $"candidate1" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors1"))
    val y = exemples
      .join(neighbors, $"candidate2" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors2"))
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .as[(Long, Long, Array[Long], Array[Long])]
      .map {
        case (c1, c2, n1, n2) =>
          (Example(c1, c2),
           n1.intersect(n2)
             .map(id => 1 / 2.0)
             .sum)
      }
    result
  }

  def resourceAllocation(
      graph: GraphFrame,
      exemples: Dataset[Example]): Dataset[(Example, Double)] = {
    val spark = exemples.sparkSession
    import spark.implicits._
    val degrees = graph.degrees
    val neighbors = getNeighbors(graph)
    val x = exemples
      .join(neighbors, $"candidate1" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors1"))
    val y = exemples
      .join(neighbors, $"candidate2" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors2"))
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .as[(Long, Long, Array[Long], Array[Long])]
      .map {
        case (c1, c2, n1, n2) =>
          (Example(c1, c2),
           n1.intersect(n2)
             .map(
               id =>
                 1 / degrees
                   .where($"id" === id)
                   .select("degree")
                   .as[Double]
                   .first())
             .sum)
      }
    result
  }

  def agregationOfClusteringCoeficient(
      graph: GraphFrame,
      examples: Dataset[Example]): Dataset[(Example, Double)] = {

    val spark = examples.sparkSession

    import spark.implicits._

    val triangleCount = graph.triangleCount.run()

    val x = examples
      .join(triangleCount, $"id" === $"candidate1")
      .select($"candidate1", $"candidate2", $"count" as "count1")
    val y = examples
      .join(triangleCount, $"id" === $"candidate2")
      .select($"candidate1", $"candidate2", $"count" as "count2")
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .select($"candidate1", $"candidate2", $"count1" * $"count2")
      .map {
        case Row(c1: Long, c2: Long, productOfnbTringles: Long) =>
          (Example(c1, c2), productOfnbTringles.toDouble)
      }
    result
  }

  def agregateOfPageRank(
      graph: GraphFrame,
      examples: Dataset[Example]): Dataset[(Example, Double)] = {
    val spark = examples.sparkSession

    import spark.implicits._

    val pageRank =
      graph.pageRank.maxIter(10).run().vertices.select($"id", $"pagerank")

    val x = examples
      .join(pageRank, $"id" === $"candidate1")
      .select($"candidate1", $"candidate2", $"pagerank" as "pr1")
    val y = examples
      .join(pageRank, $"id" === $"candidate2")
      .select($"candidate1", $"candidate2", $"pagerank" as "pr2")
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .select($"candidate1", $"candidate2", $"pr1" * $"pr2")
      .map {
        case Row(c1: Long, c2: Long, productOfPageRank: Double) =>
          (Example(c1, c2), productOfPageRank)
      }
    result
  }

  def commonNeighbors(
      graph: GraphFrame,
      exemples: Dataset[Example]): Dataset[(Example, Double)] = {
    val spark = exemples.sparkSession
    import spark.implicits._
    val neighbors = getNeighbors(graph)
    val x = exemples
      .join(neighbors, $"candidate1" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors1"))
    val y = exemples
      .join(neighbors, $"candidate2" === $"id")
      .select($"candidate1", $"candidate2", $"neighbors".as("neighbors2"))
    val result = x
      .join(y, Seq("candidate1", "candidate2"))
      .as[(Long, Long, Array[Long], Array[Long])]
      .map {
        case (c1, c2, n1, n2) =>
          (Example(c1, c2), n1.intersect(n2).length.toDouble)
      }
    result
  }

  private def getNeighbors(graph: GraphFrame): DataFrame = {
    val spark = graph.vertices.sparkSession
    graph.vertices.createOrReplaceTempView("vertices")
    graph.edges.createOrReplaceTempView("edges")
    spark.sqlContext.sql("""select id, collect_set(neighbors) as neighbors
        |from
        |(select dst as id, src as neighbors
        |from edges
        |union
        |select src as id, dst as neighbors
        |from edges)
        |group by id
      """.stripMargin)
  }
}
