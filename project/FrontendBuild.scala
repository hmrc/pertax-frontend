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
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.20.0", // includes the global object and error handling, as well as the FrontendController classes
    "uk.gov.hmrc" %% "play-partials" % "6.1.0", // includes code for retrieving partials, e.g. the Help with this page form
    "uk.gov.hmrc" %% "url-builder" % "2.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "7.1.0",
    "uk.gov.hmrc" %% "play-language" % "3.4.0",
    "uk.gov.hmrc" %% "local-template-renderer" % "1.4.0-1-g72270fd",
    "uk.gov.hmrc" %% "play-ui" % "7.14.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
        "org.scalatest" %% "scalatest" % "3.0.0" % scope,
        "org.mockito" % "mockito-all" % "2.0.2-beta" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "org.jsoup" % "jsoup" % "1.10.2" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
      )
    }.test
  }

  def apply() = compile ++ Test()
}


