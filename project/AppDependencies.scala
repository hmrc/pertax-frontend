import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"
  private val hmrcMongoVersion = "0.73.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % "6.3.0",
    "uk.gov.hmrc"       %% "play-partials"                    % s"8.3.0-$playVersion",
    "uk.gov.hmrc"       %% "http-caching-client"              % s"9.6.0-$playVersion",
    "uk.gov.hmrc"       %% "tax-year"                         % "3.0.0",
    "uk.gov.hmrc"       %% "time"                             % "3.19.0",
    "uk.gov.hmrc"       %% "domain"                           % s"8.0.0-$playVersion",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "io.lemonlabs"      %% "scala-uri"                        % "4.0.2",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"               % s"3.32.0-$playVersion",
    "uk.gov.hmrc"       %% "play-frontend-pta"                % "0.3.0",
    "org.jsoup"          % "jsoup"                            % "1.15.3",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"         % "3.5.0",
    "org.typelevel"     %% "cats-core"                        % "2.8.0",
    "uk.gov.hmrc"       %% s"internal-auth-client-$playVersion" % "1.2.0",
    "org.apache.commons" % "commons-text"               % "1.6",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"          %% "scalatest"                     % "3.2.14",
    "com.typesafe.play"      %% "play-test"                     % current,
    "org.scalatestplus.play" %% "scalatestplus-play"            % "5.1.0",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion,
    "org.scalatestplus"      %% "mockito-3-4"                   % "3.2.10.0",
    "org.mockito"             % "mockito-core"                  % "4.8.0",
    "org.scalacheck"         %% "scalacheck"                    % "1.17.0",
    "com.github.tomakehurst"  % "wiremock-standalone"           % "2.27.2",
    "com.vladsch.flexmark"    % "flexmark-all"                  % "0.62.0"
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
