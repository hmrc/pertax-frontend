import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val playVersion               = "play-30"
  private val cryptoVersion             = "8.4.0"
  private val scaWrapperVersion         = "4.5.0-SNAPSHOT"
  private val mongoFeatureClientVersion = "2.4.0-SNAPSHOT"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "tax-year"                                   % "6.0.0",
    "io.lemonlabs"      %% "scala-uri"                                  % "4.0.3",
    "org.jsoup"          % "jsoup"                                      % "1.21.2",
    "org.typelevel"     %% "cats-core"                                  % "2.13.0",
    "org.typelevel"     %% "cats-effect"                                % "3.6.3",
    "org.apache.commons" % "commons-text"                               % "1.14.0",
    "uk.gov.hmrc"       %% s"sca-wrapper-$playVersion"                  % scaWrapperVersion,
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-$playVersion" % mongoFeatureClientVersion,
    "uk.gov.hmrc"       %% s"crypto-json-$playVersion"                  % cryptoVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"sca-wrapper-test-$playVersion"                  % scaWrapperVersion,
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-test-$playVersion" % mongoFeatureClientVersion
  ).map(_ % "test")

  val all: Seq[ModuleID]  = compile ++ test
}
