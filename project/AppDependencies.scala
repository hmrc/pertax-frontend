import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion      = "play-28"
  private val hmrcMongoVersion = "1.3.0"
  private val bootstrapVersion = "8.1.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"http-caching-client-$playVersion"  % s"11.0.0",
    "uk.gov.hmrc"       %% "tax-year"                           % "4.0.0",
    "io.lemonlabs"      %% "scala-uri"                          % "4.0.3",
    "org.jsoup"          % "jsoup"                              % "1.16.1",
    "org.typelevel"     %% "cats-core"                          % "2.10.0",
    "org.apache.commons" % "commons-text"                       % "1.10.0",
    "uk.gov.hmrc"       %% s"sca-wrapper-$playVersion"          % "1.6.0",
    "uk.gov.hmrc"       %% "mongo-feature-toggles-client"       % "0.3.0",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"         %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"         %% "mockito-scala-scalatest"       % "1.17.27",
    "org.scalatestplus"   %% "scalacheck-1-17"               % "3.2.17.0",
    "com.vladsch.flexmark" % "flexmark-all"                  % "0.62.2",
    "uk.gov.hmrc.mongo"   %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
