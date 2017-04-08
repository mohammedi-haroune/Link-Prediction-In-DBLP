package org.prediction.utils

import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.functions.sum

/**
  * Created by mohammedi on 4/6/17.
  */
case class Aggregator(
    id: String,
    agg: (SparkSession,
          Seq[(Attribute, Dataset[(Example, Double)])]) => Dataset[(Example,
                                                                    Double)])

object Aggregator {

  def someChangeToAttributes(ALL_ATTRIBUTES: Seq[Attribute]): Unit = {
    ALL_ATTRIBUTES.foreach { att =>
      att.changeWeight(1, 0.47)
      att.changeWeight(2, 0.45)
      att.changeWeight(3, 0.40)
    }
  }

  //val KEMENY = Aggregator("kemeny", kemeny)

  val BORDA = Aggregator("borda", borda)

  def kemeny(spark: SparkSession,
             lists: Seq[(Attribute, Dataset[(Example, Double)])])
    : Dataset[Example] = {

    import spark.implicits._

    implicit val ord: Ordering[Example] = new Ordering[Example] {
      override def compare(x: Example, y: Example): Int =
        pref(spark, lists, x, y)
    }

    val initialAggregation = lists(0)._2.map(_._1)

    initialAggregation.rdd.sortBy(e => e).toDS()
  }

  private def pref(spark: SparkSession,
                   lists: Seq[(Attribute, Dataset[(Example, Double)])],
                   example1: Example,
                   example2: Example): Int = {

    import spark.implicits._

    val score = lists.map {
      case (attribute, dataset) =>
        val value1 =
          dataset.filter(_._1 == example1).map(_._2).first()
        val value2 =
          dataset.filter(_._1 == example2).map(_._2).first()
        if (value1 > value2) attribute.getWeight
        else 0
    }.sum

    val wdd = lists.map(_._1.getWeight).sum / 2

    if (score > wdd) -1
    else if (score < wdd) 1
    else 0
  }

  def borda(spark: SparkSession,
            lists: Seq[(Attribute, Dataset[(Example, Double)])])
    : Dataset[(Example, Double)] = {
    import spark.implicits._
    //val nbExample = lists(0)._2.count()

    val x = lists
      .map {
        case (attribute, dataset) =>
          dataset
            .sort($"_2".asc)
            .select($"_1".as[Example])
            .rdd
            .zipWithIndex()
            .toDS()
            .map {
              case (example, order) =>
                (example, (order + 1) * attribute.getWeight)
            }
      }
      .reduce((d1, d2) => d1.union(d2))
      .groupByKey(_._1)
      .agg(sum($"_2").as[Double])
      .sort($"sum(_2)".desc)

    /*
    x.repartition(1).write.json("output/borda/" + lists.toString.hashCode + "/")
    x.select($"key".as[Example])
     */
    x
  }

}
