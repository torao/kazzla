name := "kazzla"

version := "0.1"

scalaVersion := "2.10.3"

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"	// MessagePack

scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation")

javacOptions ++= Seq("-encoding", "UTF-8")

// scaladocOptions ++= Seq("-encoding", "UTF-8", "-doc-title", "Kazzla 0.1")

libraryDependencies ++= Seq(
	"org.slf4j" % "slf4j-log4j12" % "1.7.6"
)

// Scala 2.9.1, sbt 0.11.3
// addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

retrieveManaged := true