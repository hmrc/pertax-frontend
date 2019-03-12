import sbt._

object FrontendBuild extends Build with MicroService {
  val appName = "pertax-frontend"
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.sbt.PlayImport._
  import play.core.PlayVersion


  private val govukTemplateVersion =  "5.2.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-breadcrumb" % "1.0.0",
    "uk.gov.hmrc" %% "frontend-bootstrap" % "12.4.0", // includes the global object and error handling, as well as the FrontendController classes
    "uk.gov.hmrc" %% "play-partials" % "6.5.0", // includes code for retrieving partials, e.g. the Help with this page form
    "uk.gov.hmrc" %% "url-builder" % "3.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "8.1.0",
    "uk.gov.hmrc" %% "play-language" % "3.4.0",
    "uk.gov.hmrc" %% "local-template-renderer" % "2.3.0",
    "uk.gov.hmrc" %% "play-ui" % "7.33.0-play-25",
    "uk.gov.hmrc" %% "tax-year" % "0.4.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.16.2-play25"
  )

  trait TestDependencies {
    lazy val scope: String = "test,it"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.6.0-play-25" % scope,
        "org.scalatest" %% "scalatest" % "3.0.5" % scope,
        "org.mockito" % "mockito-all" % "2.0.2-beta" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "org.jsoup" % "jsoup" % "1.11.3" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}


