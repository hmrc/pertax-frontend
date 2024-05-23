import sbt.*
import play.sbt.PlayImport.*

object AppDependencies {

  private val playVersion      = "play-30"
  private val hmrcMongoVersion = "1.8.0"
  private val bootstrapVersion = "8.6.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"http-caching-client-$playVersion"          % "11.2.0",
    "uk.gov.hmrc"       %% "tax-year"                                   % "4.0.0",
    "io.lemonlabs"      %% "scala-uri"                                  % "4.0.3",
    "org.jsoup"          % "jsoup"                                      % "1.17.2",
    "org.typelevel"     %% "cats-core"                                  % "2.10.0",
    "org.apache.commons" % "commons-text"                               % "1.11.0",
    "uk.gov.hmrc"       %% s"sca-wrapper-$playVersion"                  % "1.8.0",
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-$playVersion" % "1.3.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"         %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"         %% "mockito-scala-scalatest"       % "1.17.31",
    "org.scalatestplus"   %% "scalacheck-1-17"               % "3.2.18.0",
    "uk.gov.hmrc.mongo"   %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test")

  val all: Seq[ModuleID]  = compile ++ test
}
