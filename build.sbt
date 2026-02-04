/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.*
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.*

val appName = "pertax-frontend"

ThisBuild / majorVersion := 2
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / scalafmtOnCompile := true

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*Routes.*;view.*",
    ScoverageKeys.coverageMinimumStmtTotal := 83,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort := 9232,
    scoverageSettings,
    scalaSettings,
    libraryDependencies ++= AppDependencies.all,
    scalacOptions ++= Seq(
      "-Werror",
      "-Wconf:msg=unused import&src=html/.*:s",
      "-Wconf:msg=unused import&src=xml/.*:s",
      "-Wconf:msg=unused&src=.*RoutesPrefix\\.scala:s",
      "-Wconf:msg=unused&src=.*Routes\\.scala:s",
      "-Wconf:msg=unused&src=.*ReverseRoutes\\.scala:s",
      "-Wconf:msg=Flag.*repeatedly:s",
      "-Wconf:msg=Setting -Wunused set to all redundantly:s",
      "-Wconf:msg=unused implicit parameter&src=.*Enumerable\\.scala:s"
    ),
    routesImport ++= Seq("uk.gov.hmrc.play.bootstrap.binders._", "controllers.bindable._", "models.admin._"),
    TwirlKeys.templateImports ++= Seq(
      "models._",
      "models.dto._",
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
  .configs(A11yTest)
  .settings(inConfig(A11yTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings) *)
  .settings(headerSettings(A11yTest) *)
  .settings(automateHeaderSettings(A11yTest))

Test / parallelExecution := true
Test / Keys.fork := true
Test / scalacOptions --= Seq("-Wdead-code", "-Wvalue-discard")

lazy val it = project
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(
    libraryDependencies ++= AppDependencies.test,
    DefaultBuildSettings.itSettings()
  )
