import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"         %% "bootstrap-play-26"        % "1.13.0",
    "uk.gov.hmrc"         %% "play-partials"            % "6.11.0-play-26",
    "uk.gov.hmrc"         %% "url-builder"              % "3.4.0-play-26",
    "uk.gov.hmrc"         %% "http-caching-client"      % "9.1.0-play-26",
    "uk.gov.hmrc"         %% "play-language"            % "4.5.0-play-26",
    "uk.gov.hmrc"         %% "local-template-renderer"  % "2.10.0-play-26",
    "uk.gov.hmrc"         %% "play-ui"                  % "9.2.0-play-26",
    "uk.gov.hmrc"         %% "tax-year"                 % "1.1.0",
    "uk.gov.hmrc"         %% "domain"                   % "5.10.0-play-26",
    "uk.gov.hmrc"         %% "auth-client"              % "3.0.0-play-26",
    "org.reactivemongo"   %% "play2-reactivemongo"      % "0.18.6-play26",
    "io.lemonlabs"        %% "scala-uri"                % "2.2.3",
    "com.typesafe.play"   %% "play-json-joda"           % "2.6.10"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "hmrctest"             % "3.9.0-play-26",
    "org.scalatest"           %% "scalatest"            % "3.0.8",
    "org.mockito"              % "mockito-all"          % "2.0.2-beta",
    "org.scalatestplus.play"  %% "scalatestplus-play"   % "3.1.3",
    "org.pegdown"              % "pegdown"              % "1.6.0",
    "org.jsoup"                % "jsoup"                % "1.11.3",
    "com.github.tomakehurst"   % "wiremock-standalone"  % "2.17.0",
    "com.typesafe.play"       %% "play-test"            % PlayVersion.current
  ).map(_ % "test,it")

  val all: Seq[ModuleID] = compile ++ test
}
