resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(
  Resolver.ivyStylePatterns
)
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.3.1")
addSbtPlugin("uk.gov.hmrc"        % "sbt-auto-build"           % "3.24.0")
addSbtPlugin("uk.gov.hmrc"        % "sbt-distributables"       % "2.6.0")
addSbtPlugin("org.playframework"  % "sbt-plugin"               % "3.0.9")
addSbtPlugin("com.github.sbt"     % "sbt-digest"               % "2.0.0")
addSbtPlugin("uk.gov.hmrc"        % "sbt-sass-compiler"        % "0.12.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.5.4")
addSbtPlugin("uk.gov.hmrc"        % "sbt-accessibility-linter" % "1.0.0")
