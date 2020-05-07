/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.PdfGeneratorConnector
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.RendersErrors
import org.joda.time.LocalDate
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.print._

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class NiLetterController @Inject()(
  val pdfGeneratorConnector: PdfGeneratorConnector,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  printNiNumberView: PrintNationalInsuranceNumberView,
  pdfWrapperView: NiLetterPDfWrapperView,
  niLetterView: NiLetterView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with RendersErrors {

  def printNationalInsuranceNumber: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)) {
      implicit request =>
        if (request.personDetails.isDefined) {
          Ok(
            printNiNumberView(
              request.personDetails.get,
              LocalDate.now.toString("MM/YY"),
              configDecorator.saveNiLetterAsPdfLinkEnabled
            )
          )
        } else {
          error(INTERNAL_SERVER_ERROR)
        }
    }

  def saveNationalInsuranceNumberAsPdf: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request =>
        if (configDecorator.saveNiLetterAsPdfLinkEnabled) {
          if (request.personDetails.isDefined) {
            val applicationMinCss =
              Source
                .fromURL(controllers.routes.AssetsController.versioned("css/applicationMin.css").absoluteURL())
                .mkString
            val saveNiLetterAsPDFCss = Source
              .fromURL(controllers.routes.AssetsController.versioned("css/saveNiLetterAsPDF.css").absoluteURL())
              .mkString

            val htmlPayload = pdfWrapperView()
              .toString()
              .replace("<!-- minifiedCssPlaceholder -->", s"$saveNiLetterAsPDFCss$applicationMinCss")
              .replace(
                "<!-- niLetterPlaceHolder -->",
                niLetterView(request.personDetails.get, LocalDate.now.toString("MM/YY")).toString)
              .filter(_ >= ' ')
              .trim
              .replaceAll("  +", "")

            pdfGeneratorConnector.generatePdf(htmlPayload).map { response =>
              if (response.status != OK) {
                throw new BadRequestException("Unexpected response from pdf-generator-service : " + response.body)
              } else {
                Ok(response.bodyAsBytes.toArray)
                  .as("application/pdf")
                  .withHeaders("Content-Disposition" -> s"attachment; filename=${Messages(
                    "label.your_national_insurance_letter").replaceAll(" ", "-")}.pdf")
              }
            }
          } else {
            futureError(INTERNAL_SERVER_ERROR)

          }
        } else {
          Future.successful(
            InternalServerError(
              views.html.error(
                "global.error.InternalServerError500.title",
                Some("global.error.InternalServerError500.title"),
                List("global.error.InternalServerError500.message"))))
        }
    }
}
