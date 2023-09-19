import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"
  private val hmrcMongoVersion = "1.2.0"
  private val bootstrapVersion = "7.20.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc"       %% "http-caching-client"              % s"10.0.0-$playVersion",
    "uk.gov.hmrc"       %% "tax-year"                         % "3.2.0",
    "uk.gov.hmrc"       %% "domain"                           % s"8.3.0-$playVersion",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "io.lemonlabs"      %% "scala-uri"                        % "4.0.3",
    "org.jsoup"          % "jsoup"                            % "1.15.4",
    "org.typelevel"     %% "cats-core"                        % "2.9.0",
    "uk.gov.hmrc"       %% s"internal-auth-client-$playVersion" % "1.4.0",
    "org.apache.commons" % "commons-text"               % "1.10.0",
    "uk.gov.hmrc"           %% "sca-wrapper"                      % "1.0.41",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"             %% "mockito-scala-scalatest"       % "1.17.14",
    "org.scalatestplus"       %% "scalacheck-1-17"               % "3.2.15.0",
    "com.vladsch.flexmark"    %  "flexmark-all"                  % "0.62.2",
    "uk.gov.hmrc.mongo"       %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
