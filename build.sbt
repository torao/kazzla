name := "kazzla"

version := "0.1"

scalaVersion := "2.9.1"

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"	// MessagePack

scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation")

javacOptions ++= Seq("-encoding", "UTF-8")

// scaladocOptions ++= Seq("-encoding", "UTF-8", "-doc-title", "Kazzla 0.1")

libraryDependencies ++= Seq(
	"log4j" % "log4j" % "1.2.16",
	"org.msgpack" % "msgpack" % "0.6.6",
	"org.scalatest" %% "scalatest" % "1.8" % "test"
)

// Scala 2.9.1, sbt 0.11.3
// addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")