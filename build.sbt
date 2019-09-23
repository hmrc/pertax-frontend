import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport.scalafmtOnCompile
import com.typesafe.sbt.digest.Import.digest
import com.typesafe.sbt.web.Import.pipelineStages
import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys._
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
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
    scalafmtOnCompile := true,
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo,
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
    majorVersion := 1,
    routesImport ++= Seq("uk.gov.hmrc.play.frontend.binders._", "controllers.bindable._", "uk.gov.hmrc.play.binders._"),
    TwirlKeys.templateImports ++= Seq(
      "models._",
      "models.dto._",
      "uk.gov.hmrc.play.binders._",
      "uk.gov.hmrc.play.frontend.binders._",
      "controllers.bindable._",
      "uk.gov.hmrc.domain._",
      "util.TemplateFunctions._",
      "uk.gov.hmrc.http.HeaderCarrier"
    )
  )

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map { test =>
    Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }
