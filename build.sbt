import play.routes.compiler.StaticRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val appName = "pertax-frontend"

lazy val plugins: Seq[Plugins] = Seq(
  play.sbt.PlayScala,
  SbtAutoBuildPlugin,
  SbtGitVersioning,
  SbtDistributablesPlugin,
  SbtArtifactory
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(
    PlayKeys.playDefaultPort := 9232,
    scalaSettings,
    publishingSettings,
    defaultSettings(),
    libraryDependencies ++= AppDependencies.all,
    routesGenerator := StaticRoutesGenerator,
    scalafmtOnCompile := true,
    majorVersion := 1,
    coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;models.*;uk.gov.hmrc.pertax.auth.*;.*(AuthService|BuildInfo|Routes).*",
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo,
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/",
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
