import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt._
import play.sbt.PlayImport.ehcache

object AppDependencies {

  private val playVersion = "play-28"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % "5.20.0",
    "uk.gov.hmrc"       %% "play-partials"                    % s"8.3.0-$playVersion",
    "uk.gov.hmrc"       %% "url-builder"                      % s"3.6.0-$playVersion",
    "uk.gov.hmrc"       %% "http-caching-client"              % s"9.6.0-$playVersion",
    "uk.gov.hmrc"       %% "play-language"                    % s"4.13.0-$playVersion",
    "uk.gov.hmrc"       %% "play-ui"                          % s"9.8.0-$playVersion",
    "uk.gov.hmrc"       %% "tax-year"                         % "3.0.0",
    "uk.gov.hmrc"       %% "time"                             % "3.19.0",
    "uk.gov.hmrc"       %% "domain"                           % s"8.0.0-$playVersion",
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % "0.71.0",
    "io.lemonlabs"      %% "scala-uri"                        % "2.2.3",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"               % s"3.32.0-play-28",
    "uk.gov.hmrc"       %% "play-frontend-pta"                % "0.3.0",
    "org.jsoup"          % "jsoup"                            % "1.15.3",
    "uk.gov.hmrc"       %% "reactive-circuit-breaker"         % "3.5.0",
    "org.typelevel"     %% "cats-core"                        % "2.8.0",
    "uk.gov.hmrc"       %% s"internal-auth-client-$playVersion" % "1.2.0",
    ehcache
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"          %% "scalatest"                     % "3.2.8",
    "com.typesafe.play"      %% "play-test"                     % current,
    "org.scalatestplus.play" %% "scalatestplus-play"            % "4.0.3",
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % "0.68.0",
    "org.scalatestplus"      %% "mockito-3-4"                   % "3.2.3.0",
    "org.mockito"             % "mockito-core"                  % "3.6.28",
    "org.scalacheck"         %% "scalacheck"                    % "1.15.1",
    "com.github.tomakehurst"  % "wiremock-standalone"           % "2.27.2",
    "com.vladsch.flexmark"    % "flexmark-all"                  % "0.36.8"
  ).map(_ % "test,it")

  val all: Seq[ModuleID]  = compile ++ test
}
