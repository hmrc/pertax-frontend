import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val playVersion      = "play-30"
  private val hmrcMongoVersion = "2.3.0"
  private val bootstrapVersion = "9.5.0"
  private val cryptoVersion    = "8.1.0"
  private val webChatVersion   = "1.6.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "tax-year"                                   % "5.0.0",
    "io.lemonlabs"      %% "scala-uri"                                  % "4.0.3",
    "org.jsoup"          % "jsoup"                                      % "1.18.1",
    "org.typelevel"     %% "cats-core"                                  % "2.12.0",
    "org.apache.commons" % "commons-text"                               % "1.12.0",
    "uk.gov.hmrc"       %% s"sca-wrapper-$playVersion"                  % "2.1.0",
    "uk.gov.hmrc"       %% s"mongo-feature-toggles-client-$playVersion" % "1.8.0",
    "uk.gov.hmrc"       %% s"crypto-json-$playVersion"                  % cryptoVersion,
    "uk.gov.hmrc"       %% "digital-engagement-platform-chat-30"        % webChatVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"         %% s"bootstrap-test-$playVersion"  % bootstrapVersion,
    "org.mockito"         %% "mockito-scala-scalatest"       % "1.17.37",
    "org.scalatestplus"   %% "scalacheck-1-17"               % "3.2.18.0",
    "uk.gov.hmrc.mongo"   %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion
  ).map(_ % "test")

  val all: Seq[ModuleID]  = compile ++ test
}
