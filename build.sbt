import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport.scalafmtOnCompile
import com.typesafe.sbt.digest.Import.digest
import com.typesafe.sbt.web.Import.pipelineStages
import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys._
import play.twirl.sbt.Import.TwirlKeys
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

val appName = "pertax-frontend"

lazy val plugins: Seq[Plugins] =
  Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

lazy val playSettings: Seq[Setting[_]] = Seq(
  pipelineStages := Seq(digest)
)

lazy val scoverageSettings = {
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;models.*;uk.gov.hmrc.pertax.auth.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}

val wartRemovedExcludedClasses = Seq(
  "app.Routes",
  "pertax.Routes",
  "prod.Routes",
  "uk.gov.hmrc.BuildInfo",
  "controllers.routes",
  "controllers.javascript",
  "controllers.ref"
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(
    playSettings,
    scoverageSettings,
    scalaSettings,
    publishingSettings,
    defaultSettings(),
    libraryDependencies ++= AppDependencies.all,
    routesGenerator := StaticRoutesGenerator,
    PlayKeys.playDefaultPort := 9232,
    wartremoverWarnings in (Compile, compile) ++= Warts.allBut(
      Wart.DefaultArguments,
      Wart.NoNeedForMonad,
      Wart.NonUnitStatements,
      Wart.Nothing,
      Wart.Product,
      Wart.Serializable,
      Wart.Any),
    wartremoverErrors in (Compile, compile) ++= Seq.empty,
    wartremoverExcluded ++= wartRemovedExcludedClasses,
    TwirlKeys.templateImports ++= Seq(
      "models._",
      "models.dto._",
      "uk.gov.hmrc.play.binders._",
      "uk.gov.hmrc.play.frontend.binders._",
      "controllers.bindable._",
      "uk.gov.hmrc.domain._",
      "util.TemplateFunctions._",
      "uk.gov.hmrc.http.HeaderCarrier"
    ),
    routesImport ++= Seq("uk.gov.hmrc.play.frontend.binders._", "controllers.bindable._", "uk.gov.hmrc.play.binders._"),
    scalafmtOnCompile := true,
    inConfig(TemplateTest)(Defaults.testSettings),
    inConfig(TemplateItTest)(Defaults.itSettings),
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false,
    scalafmtOnCompile := true,
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo,
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
    majorVersion := 1
  )
  .configs(IntegrationTest)

val allPhases = "tt->test;test->test;test->compile;compile->compile"
val allItPhases = "tit->it;it->it;it->compile;compile->compile"

lazy val TemplateTest = config("tt") extend Test
lazy val TemplateItTest = config("tit") extend IntegrationTest

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map { test =>
    Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }
