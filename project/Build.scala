import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.releaseSettings
import org.qirx.sbtrelease.UpdateVersionInFiles

object EmbeddedSpraryBuild extends Build {

  val embeddedSprayName = "embedded-spray"

  def projectName(name: String) = embeddedSprayName + "-" + name

  // for all projects
  val defaultSettings = Seq(
    organization := "org.qirx",
    onlyScalaSourcesIn(Compile),
    onlyScalaSourcesIn(Test))

  val containerProjectSettings = defaultSettings ++ Seq(
    publishArtifact := false,
    unmanagedSourceDirectories in Compile := Seq(),
    unmanagedSourceDirectories in Test := Seq())

  val publishSettings = releaseSettings ++ Seq(
    publishTo <<= version(rhinoflyRepo),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"))

  val standardProjectSettings = defaultSettings ++ publishSettings

  val exampleProjectSettings = defaultSettings

  lazy val root =
    Project(projectName("root"), file("."))
      .settings(containerProjectSettings: _*)
      .aggregate(embeddedSpray, examples)

  lazy val embeddedSpray =
    Project(embeddedSprayName, file("embedded-spray"))
      .settings(standardProjectSettings: _*)
      .settings(
        UpdateVersionInFiles(file("README.md")))
      .settings(
        libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor" % "2.2.3",
          "io.spray" % "spray-can" % "1.2.0",
          "io.spray" % "spray-routing" % "1.2.0",
          "io.spray" %% "spray-json" % "1.2.5",
          "io.spray" % "spray-client" % "1.2.0" % "test",
          "org.specs2" %% "specs2" % "2.3.7" % "test" exclude ("com.chuusai", "shapeless_2.10.3")
          ))

  lazy val examples =
    Project(projectName("examples"), file("examples"))
      .settings(containerProjectSettings: _*)
      .aggregate(resourceServer)

  lazy val resourceServer =
    Project(projectName("example-resource-server"), file("examples/resource-server"))
      .settings(exampleProjectSettings: _*)

  def rhinoflyRepo(version: String) = {
    val repo = if (version endsWith "SNAPSHOT") "snapshot" else "release"
    Some("Rhinofly Internal " + repo.capitalize + " Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-" + repo + "-local")
  }

  def onlyScalaSourcesIn(c: Configuration) =
    unmanagedSourceDirectories in c := Seq((scalaSource in c).value)
}