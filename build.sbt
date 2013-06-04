// BASIC METADATA //

organization := "com.versal"

name := "cannonball"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.10.1"

// JELLYFISH //

libraryDependencies += "com.versal" %% "jellyfish" % "0.1.0"

libraryDependencies <+= scalaVersion { v => compilerPlugin("org.scala-lang.plugins" % "continuations" % v) }

scalacOptions += "-P:continuations:enable"

// XSBT-WEB-PLUGIN //

libraryDependencies += "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "provided,container" artifacts (Artifact("javax.servlet", "jar", "jar"))

libraryDependencies += "org.eclipse.jetty" %"jetty-webapp" % "8.1.4.v20120524" % "provided,container" artifacts (Artifact("jetty-webapp", "jar", "jar"))

seq(webSettings :_*)

// HSQLDB //

libraryDependencies += "org.hsqldb" % "hsqldb" % "2.2.9"
