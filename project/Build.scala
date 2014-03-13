/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
import sbt._
import Keys._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Build
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */

object KazzlaBuild extends Build {

	override lazy val settings = super.settings ++ Seq(
		version := "0.1",
		scalaVersion := "2.10.3",
		resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",	// MessagePack
		scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation"),
		javacOptions ++= Seq("-encoding", "UTF-8"),
		libraryDependencies ++= Seq(
			"org.slf4j" % "slf4j-log4j12" % "1.7.6",
			"org.msgpack" % "msgpack" % "0.6.10",
			"io.netty" % "netty-all" % "4.0.17.Final"
		),
		retrieveManaged := true
	)

	lazy val root = project.in(file("."))
		.aggregate(share, node, service)

	lazy val share = project.in(file("share")).settings(
		name := "kazzla-share",
		unmanagedBase := baseDirectory.value / "../lib"
	)

	lazy val service = project.in(file("service")).settings(
		name := "kazzla-service",
		unmanagedBase := baseDirectory.value / "../lib"
	).dependsOn(share)

	lazy val node = project.in(file("node")).settings(
		name := "kazzla-node",
		unmanagedBase := baseDirectory.value / "../lib"
	).dependsOn(share)
}
