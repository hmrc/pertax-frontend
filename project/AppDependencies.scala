import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "play-28"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"         %% s"bootstrap-frontend-$playVersion"        % "5.16.0",
    "uk.gov.hmrc"         %% "play-partials"            % s"8.1.0-$playVersion",
    "uk.gov.hmrc"         %% "url-builder"              % s"3.5.0-$playVersion",
    "uk.gov.hmrc"         %% "http-caching-client"      % s"9.4.0-$playVersion",
    "uk.gov.hmrc"         %% "play-language"            % s"4.13.0-$playVersion",
    "uk.gov.hmrc"         %% "local-template-renderer"  % s"2.16.0-$playVersion",
    "uk.gov.hmrc"         %% "play-ui"                  % s"9.7.0-$playVersion",
    "uk.gov.hmrc"         %% "tax-year"                 % "1.1.0",
    "uk.gov.hmrc"         %% "time"                     % "3.19.0",
    "uk.gov.hmrc"         %% "domain"                   % s"6.1.0-$playVersion",
    "org.reactivemongo"   %% "play2-reactivemongo"      % "0.19.4-play28",
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
