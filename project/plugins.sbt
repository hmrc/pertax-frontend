resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.0.11")
addSbtPlugin("uk.gov.hmrc"        % "sbt-auto-build"           % "3.21.0")
addSbtPlugin("uk.gov.hmrc"        % "sbt-distributables"       % "2.5.0")
addSbtPlugin("org.playframework"  % "sbt-plugin"               % "3.0.3")
addSbtPlugin("com.typesafe.sbt"   % "sbt-digest"               % "1.1.4")
addSbtPlugin("io.github.irundaia" % "sbt-sassify"              % "1.5.2")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.5.2")
addSbtPlugin("uk.gov.hmrc"        % "sbt-accessibility-linter" % "0.39.0")

