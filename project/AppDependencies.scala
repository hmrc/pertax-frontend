import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"
  private val hmrcMongoVersion = "0.73.0"
  private val bootstrapVersion = "7.13.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc"       %% "play-partials"                    % s"8.3.0-$playVersion",
    "uk.gov.hmrc"       %% "http-caching-client"              % s"10.0.0-$playVersion",
    "uk.gov.hmrc"       %% "tax-year"                         % "3.0.0",
    "uk.gov.hmrc"       %% "domain"                           % s"8.0.0-$playVersion",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "io.lemonlabs"      %% "scala-uri"                        % "4.0.2",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"               % s"6.2.0-$playVersion",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"               % s"6.0.0-$playVersion",
    "uk.gov.hmrc"       %% "play-frontend-pta"                % "0.4.0",
    "org.jsoup"          % "jsoup"                            % "1.15.3",
    "org.typelevel"     %% "cats-core"                        % "2.9.0",
    "uk.gov.hmrc"       %% s"internal-auth-client-$playVersion" % "1.2.0",
    "org.apache.commons" % "commons-text"               % "1.6",
    "uk.gov.hmrc"       %% "sca-wrapper"                    % "1.0.15",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"       % "1.17.12",
    "org.scalatestplus"       %% "scalacheck-1-16"               % "3.2.14.0",
    "com.vladsch.flexmark"    %  "flexmark-all"                  % "0.62.2",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
