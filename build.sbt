import com.typesafe.sbt.digest.Import.digest
import com.typesafe.sbt.web.Import.pipelineStages
import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys._
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "pertax-frontend"

scalaVersion := "2.12.15"

val silencerVersion = "1.7.8"

lazy val plugins: Seq[Plugins] =
  Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)

lazy val playSettings: Seq[Setting[_]] = Seq(
  pipelineStages := Seq(digest)
)

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;models.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .configs(IntegrationTest)
  .settings(
    inConfig(Test)(testSettings),
    inConfig(IntegrationTest)(itSettings),
    IntegrationTest / Keys.fork := false,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    playSettings,
    scoverageSettings,
    scalaSettings,
    publishingSettings,
    defaultSettings(),
    libraryDependencies ++= AppDependencies.all,
    PlayKeys.playDefaultPort := 9232,
    scalafmtOnCompile := true,
    majorVersion := 1,
    scalacOptions ++= Seq("-P:silencer:pathFilters=views;routes", "-Ypartial-unification"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    routesImport ++= Seq(
      "uk.gov.hmrc.play.bootstrap.binders._",
      "controllers.bindable._",
      "uk.gov.hmrc.play.binders._",
      "models.admin._"
    ),
    TwirlKeys.templateImports ++= Seq(
      "models._",
      "models.dto._",
      "uk.gov.hmrc.play.binders._",
      "uk.gov.hmrc.play.bootstrap.binders._",
      "controllers.bindable._",
      "uk.gov.hmrc.domain._",
      "util.TemplateFunctions._",
      "uk.gov.hmrc.http.HeaderCarrier",
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._"
    )
  )

lazy val testSettings = Seq(
  unmanagedSourceDirectories ++= Seq(
    baseDirectory.value / "test"
  ),
  fork := true
)

lazy val itSettings = Defaults.itSettings ++ Seq(
  unmanagedSourceDirectories := Seq(
    baseDirectory.value / "it"
  ),
  parallelExecution := false,
  fork := true
)

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt test:scalafmt it:scalafmt")
addCommandAlias("testAll", ";coverage ;test ;it:test ;coverageReport")
addCommandAlias("testAllWithScalafmt", ";scalafmtAll ;testAll")
