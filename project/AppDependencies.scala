import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"         %% "bootstrap-frontend-play-27"        % "5.1.0",
    "uk.gov.hmrc"         %% "play-partials"            % "8.1.0-play-27",
    "uk.gov.hmrc"         %% "url-builder"              % "3.5.0-play-27",
    "uk.gov.hmrc"         %% "http-caching-client"      % "9.4.0-play-27",
    "uk.gov.hmrc"         %% "play-language"            % "4.13.0-play-27",
    "uk.gov.hmrc"         %% "local-template-renderer"  % "2.16.0-play-27",
    "uk.gov.hmrc"         %% "play-ui"                  % "9.0.0-play-27",
    "uk.gov.hmrc"         %% "tax-year"                 % "1.1.0",
    "uk.gov.hmrc"         %% "time"                     % "3.19.0",
    "uk.gov.hmrc"         %% "domain"                   % "5.11.0-play-27",
    "org.reactivemongo"   %% "play2-reactivemongo"      % "0.18.6-play27",
    "io.lemonlabs"        %% "scala-uri"                % "2.2.3",
    "com.typesafe.play"   %% "play-json-joda"           % "2.6.10"
  )

  val test = Seq(
    "org.scalatest"           %% "scalatest"                % "3.2.8",
    "com.typesafe.play"       %% "play-test"                % current,
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "4.0.3",
    "org.scalatestplus"       %% "mockito-3-4"              % "3.2.3.0",
    "org.mockito"             %  "mockito-core"             % "3.6.28",
    "org.scalacheck"          %% "scalacheck"               % "1.15.1",
    "com.github.tomakehurst"  %  "wiremock-standalone"      % "2.27.2",
    "com.vladsch.flexmark"    % "flexmark-all"              % "0.36.8"
  ).map(_ % "test,it")

  val all: Seq[ModuleID] = compile ++ test
}
