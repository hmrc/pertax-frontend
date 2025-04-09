import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val playVersion               = "play-30"
  private val cryptoVersion             = "8.2.0"
  private val webChatVersion            = "1.6.0"
  private val scaWrapperVersion         = "2.7.0"
  private val mongoFeatureClientVersion = "1.10.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "tax-year"                                   % "5.0.0",
    "io.lemonlabs"      %% "scala-uri"                                  % "4.0.3",
    "org.jsoup"          % "jsoup"                                      % "1.18.3",
    "org.typelevel"     %% "cats-core"                                  % "2.13.0",
    "org.typelevel"     %% "cats-effect"                                % "3.5.4",
    "org.apache.commons" % "commons-text"                               % "1.12.0",
    "uk.gov.hmrc"       %% s"sca-wrapper-$playVersion"                  % scaWrapperVersion,
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-$playVersion" % mongoFeatureClientVersion,
    "uk.gov.hmrc"       %% s"crypto-json-$playVersion"                  % cryptoVersion,
    "uk.gov.hmrc"       %% "digital-engagement-platform-chat-30"        % webChatVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"sca-wrapper-test-$playVersion"                  % scaWrapperVersion,
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-test-$playVersion" % mongoFeatureClientVersion,
    "org.mockito"       %% "mockito-scala-scalatest"                         % "1.17.37",
    "org.scalatestplus" %% "scalacheck-1-17"                                 % "3.2.18.0"
  ).map(_ % "test")

  val all: Seq[ModuleID]  = compile ++ test
}
