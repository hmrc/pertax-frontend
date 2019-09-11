import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

    val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-breadcrumb" % "1.0.0",
    "uk.gov.hmrc" %% "frontend-bootstrap" % "12.9.0", // includes the global object and error handling, as well as the FrontendController classes
    "uk.gov.hmrc" %% "play-partials" % "6.5.0", // includes code for retrieving partials, e.g. the Help with this page form
    "uk.gov.hmrc" %% "url-builder" % "3.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "8.1.0",
    "uk.gov.hmrc" %% "play-language" % "4.0.0",
    "uk.gov.hmrc" %% "local-template-renderer" % "2.3.0",
    "uk.gov.hmrc" %% "play-ui" % "7.33.0-play-25",
    "uk.gov.hmrc" %% "tax-year" % "0.4.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.16.2-play25",
    "uk.gov.hmrc" %% "domain" % "5.3.0"
  )

  val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.6.0-play-25",
        "org.scalatest" %% "scalatest" % "3.0.5",
        "org.mockito" % "mockito-all" % "2.0.2-beta",
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1",
        "org.pegdown" % "pegdown" % "1.6.0",
        "org.jsoup" % "jsoup" % "1.11.3",
        "com.typesafe.play" %% "play-test" % PlayVersion.current
      ).map(_ % "test,it")

  val all: Seq[ModuleID] = compile ++ test

}
