import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"         %% "play-breadcrumb"          % "1.0.0",
    "uk.gov.hmrc"         %% "frontend-bootstrap"       % "12.9.0",
    "uk.gov.hmrc"         %% "play-partials"            % "6.9.0-play-25",
    "uk.gov.hmrc"         %% "url-builder"              % "3.1.0",
    "uk.gov.hmrc"         %% "http-caching-client"      % "8.5.0-play-25",
    "uk.gov.hmrc"         %% "play-language"            % "4.0.0",
    "uk.gov.hmrc"         %% "local-template-renderer"  % "2.7.0-play-25",
    "uk.gov.hmrc"         %% "play-ui"                  % "7.33.0-play-25",
    "uk.gov.hmrc"         %% "tax-year"                 % "0.6.0",
    "org.reactivemongo"   %% "play2-reactivemongo"      % "0.16.2-play25",
    "uk.gov.hmrc"         %% "domain"                   % "5.6.0-play-25"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "hmrctest"           % "3.9.0-play-25",
    "org.scalatest"           %% "scalatest"          % "3.0.5",
    "org.mockito"              % "mockito-all"        % "2.0.2-beta",
    "org.scalatestplus.play"  %% "scalatestplus-play" % "2.0.1",
    "org.pegdown"              % "pegdown"            % "1.6.0",
    "org.jsoup"                % "jsoup"              % "1.11.3",
    "com.typesafe.play"       %% "play-test"          % PlayVersion.current
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}
