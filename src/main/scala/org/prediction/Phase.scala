package org.prediction

import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.graphframes.GraphFrame
import Aggregator.BORDA
import org.prediction.dblp.utils.DBLPReader.globalGraph

import scala.collection.immutable.IndexedSeq

/** Created by mohammedi on 4/3/17.
  */
case class Phase(startLearning: Int,
                 endLearning: Int,
                 startLabeling: Int,
                 endLabeling: Int) {

  val id = s"$startLearning:$endLearning-$startLabeling:$endLabeling"

  val spark: SparkSession = globalGraph.vertices.sparkSession

  import spark.implicits._

  val graphPhase: GraphFrame = GraphFrame(
    globalGraph.vertices,
    globalGraph.edges
      .filter(col("year") >= startLearning)
      .filter(col("year") <= endLearning)
      .select(col("src"), col("dst"))
  )
  graphPhase.cache()

  val labelingEdges: Dataset[Example] = globalGraph.edges
    .filter(col("year") >= startLabeling)
    .filter(col("year") <= endLabeling)
    .select($"src", $"dst")
    .except(graphPhase.edges.select($"src", $"dst"))
    .except(graphPhase.edges.select($"dst", $"src"))
    .select($"src" as "candidate1", $"dst" as "candidate2")
    .as[Example]
  labelingEdges.cache()

  val examples: Dataset[Example] = {
    val connectedComponents = graphPhase.connectedComponents.run()
    connectedComponents.createOrReplaceTempView("vertices")
    graphPhase.edges.select("src", "dst").createOrReplaceTempView("edges")
    val sqlContext = graphPhase.edges.sqlContext
    sqlContext
      .sql("""SELECT v1.id id1, v2.id id2
          |FROM vertices AS v1, vertices AS v2
          |WHERE NOT EXISTS (SELECT * FROM edges AS e
          |                  WHERE e.src = v1.id
          |                  AND e.dst = v2.id
          |                  OR e.src = v2.id
          |                  AND e.dst = v1.id )
          |AND EXISTS (SELECT * FROM labelingEdges AS e1
          |                  WHERE e.src = v1.id
          |                  AND e.dst = v2.id
          |                  OR e.src = v2.id
          |                  AND e.dst = v1.id )
          |AND v1.component = v2.component
          |AND v1.id <> v2.id
          |""".stripMargin)
      .map {
        case Row(id1: Long, id2: Long) =>
          if (id1 < id2) Example(id1, id2)
          else Example(id2, id1)
      }
      .distinct()
  }
  examples.cache()

  val positiveExamples: Dataset[Example] = examples
    .intersect(labelingEdges)
    .union(
      examples.intersect(
        labelingEdges
          .select($"candidate2" as "candidate1", $"candidate1" as "candidate2")
          .as[Example]))
  positiveExamples.cache()

  var classifications: Seq[(Attribute, Dataset[(Example, Double)])] = _

  var prediction: Dataset[(Example, Double)] = _

  def applyAttributes(attributes: Seq[Attribute]): Unit = {

    classifications = attributes
      .map { att =>
        val classification = att.score(graphPhase, examples)
        classification.cache()
        (att, classification.sort($"_2".desc))
      }

  }

  def applyAggregator(aggregator: Aggregator): Dataset[(Example, Double)] = {
    prediction = BORDA.agg(spark, classifications)
    prediction
  }

  def changeWeights(): Unit = {

    classifications.foreach {
      case (attribute, classification) =>
        attribute.changWeight(positiveExamples,
                                                  classification)
    }
  }

  def saveInformation(): Unit = {

    graphPhase.triplets
      .repartition(1)
      .write
      .json("output/" + id + "/phaseInfo/Learning/tripltes/")

    graphPhase.vertices
      .repartition(1)
      .write
      .json("output/" + id + "/phaseInfo/Learning/vertices/")

    examples.repartition(1).write.json("output/" + id + "/phaseInfo/examples/")

    labelingEdges.repartition(1).write.json("output/" + id + "/phaseInfo/labelingEdges/")
    labelingEdges.repartition(1).write.json("output/" + id + "/phaseInfo/labelingEdges/")

    positiveExamples
      .repartition(1)
      .write
      .json("output/" + id + "/phaseInfo/positiveExamples/")

    classifications.foreach {
      case (a, d) =>
        d.repartition(1)
          .write
          .json("output/" + id + "/attributes/" + a.id + "/")
    }

    prediction.repartition(1).write.json("output/" + id + "/aggregations/borda/")
  }
}

object Phase {

  def getPhases(start: Int,
                end: Int,
                learning: Int,
                labeling: Int): IndexedSeq[Phase] = {
    var index = start + learning + labeling + 1
    var nbPhases = 0
    while (index <= end) {
      nbPhases += 1
      index += learning - 1
    }
    for (i <- 0 until nbPhases) yield {
      val startLearning = (learning - 1) * i + start
      val endLearning = startLearning + learning
      val startLabeling = endLearning + 1
      val endLabeling = startLabeling + labeling
      Phase(startLearning, endLearning, startLabeling, endLabeling)
    }
  }
}
