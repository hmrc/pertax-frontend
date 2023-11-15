import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"
  private val hmrcMongoVersion = "1.3.0"
  private val bootstrapVersion = "7.22.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "http-caching-client"              % s"10.0.0-$playVersion",
    "uk.gov.hmrc"       %% "tax-year"                         % "3.3.0",
    "uk.gov.hmrc"       %% "domain"                           % s"8.3.0-$playVersion",
    "io.lemonlabs"      %% "scala-uri"                        % "4.0.3",
    "org.jsoup"          % "jsoup"                            % "1.16.1",
    "org.typelevel"     %% "cats-core"                        % "2.10.0",
    "org.apache.commons" % "commons-text"                     % "1.10.0",
    "uk.gov.hmrc"           %% "sca-wrapper"                  % "1.0.45",
    "uk.gov.hmrc"       %% "mongo-feature-toggles-client"     % "0.3.0",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"       % "1.17.27",
    "org.scalatestplus"       %% "scalacheck-1-17"               % "3.2.17.0",
    "com.vladsch.flexmark"    %  "flexmark-all"                  % "0.62.2",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
