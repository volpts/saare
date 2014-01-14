logLevel := Level.Warn

resolvers += "Typesafe Repository (Releases)" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.1")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.2")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-license-report" % "0.1")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.0")

resolvers += Resolver.url("bintray-sbt-plugin-releases", url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8")

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

lazy val fmppPlugin = uri("git://github.com/sumito3478/sbt-fmpp.git#priv")

lazy val root = project in file(".") dependsOn fmppPlugin
