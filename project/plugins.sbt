import sbt.addSbtPlugin

resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(
  Resolver.ivyStylePatterns
)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scoverage"     % "sbt-scoverage"            % "2.0.7")
addSbtPlugin("org.scalastyle"   %% "scalastyle-sbt-plugin"    % "1.0.0")
addSbtPlugin("uk.gov.hmrc"       % "sbt-auto-build"           % "3.13.0")
addSbtPlugin("uk.gov.hmrc"       % "sbt-distributables"       % "2.2.0")
addSbtPlugin("com.typesafe.play" % "sbt-plugin"               % "2.8.19")
addSbtPlugin("com.typesafe.sbt"  % "sbt-digest"               % "1.1.4")
addSbtPlugin("org.irundaia.sbt"  % "sbt-sassify"              % "1.5.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"             % "2.5.0")
addSbtPlugin("uk.gov.hmrc"       % "sbt-accessibility-linter" % "0.35.0")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
