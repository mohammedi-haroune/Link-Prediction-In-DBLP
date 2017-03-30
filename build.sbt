

autoScalaLibrary := false
managedScalaInstance := false
ivyConfigurations += Configurations.ScalaTool

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "org.prediction",
      scalaVersion := "2.11.8",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Link Prediction In DBLP Network Datasets",
    libraryDependencies ++= Seq (
      "org.scala-lang" % "scala-library" % scalaVersion.value,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "scala-tool",
      "org.scalaj" %% "scalaj-http" % "2.3.0")
  )
