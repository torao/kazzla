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
		organization := "com.kazzla",
		version := "0.1-SNAPSHOT",
		scalaVersion := "2.10.3",
		resolvers ++= Seq(
			"Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"	// MessagePack
		),
		scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation"),
		javacOptions ++= Seq("-encoding", "UTF-8"),
		libraryDependencies ++= Seq(
			"org.slf4j"   % "slf4j-log4j12" % "1.7.6",
			"org.msgpack" % "msgpack"       % "0.6.10",
			"io.netty"    % "netty-all"     % "4.0.17.Final",
			"com.kazzla"  %% "asterisk"     % "0.1-SNAPSHOT",
			"org.specs2"  %% "specs2"       % "2.3.10"       % "test"
		),
		retrieveManaged := true
	)

	lazy val root = project.in(file("."))
		.aggregate(share, node, service)

	lazy val share = project.in(file("share")).settings(
		name := "kazzla-share"
	)

	lazy val service = project.in(file("service")).settings(
		name := "kazzla-service"
	).dependsOn(share)

	lazy val node = project.in(file("node")).settings(
		name := "kazzla-node"
	).dependsOn(share)
}
