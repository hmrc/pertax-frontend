/*
 * Copyright 2023 HM Revenue & Customs
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
import error.ErrorRenderer
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.BadRequestException
import views.html.print._

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class NiLetterController @Inject() (
  val pdfGeneratorConnector: PdfGeneratorConnector,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  printNiNumberView: PrintNationalInsuranceNumberView,
  pdfWrapperView: NiLetterPDfWrapperView,
  niLetterView: NiLetterView
)(implicit configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def printNationalInsuranceNumber: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request =>
        if (request.personDetails.isDefined) {
          Future.successful(
            Ok(
              printNiNumberView(
                request.personDetails.get,
                LocalDate.now.format(DateTimeFormatter.ofPattern("MM/YY")),
                configDecorator.saveNiLetterAsPdfLinkEnabled,
                request.nino
              )
            )
          )
        } else {
          errorRenderer.futureError(INTERNAL_SERVER_ERROR)
        }
    }

  def saveNationalInsuranceNumberAsPdf: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      implicit request =>
        if (configDecorator.saveNiLetterAsPdfLinkEnabled) {
          if (request.personDetails.isDefined) {
            val source               = Source
              .fromURL(
                controllers.routes.AssetsController.versioned("css/saveNiLetterAsPDF.css").absoluteURL(secure = true)
              )
            val saveNiLetterAsPDFCss =
              try source.mkString
              finally source.close()

            val niLetter    =
              niLetterView(
                request.personDetails.get,
                LocalDate.now.format(DateTimeFormatter.ofPattern("MM/YY")),
                request.nino
              ).toString()
            val htmlPayload = pdfWrapperView()
              .toString()
              .replace("<!-- minifiedCssPlaceholder -->", s"$saveNiLetterAsPDFCss")
              .replace("<!-- niLetterPlaceHolder -->", niLetter)
              .filter(_ >= ' ')
              .trim
              .replaceAll("  +", "")

            pdfGeneratorConnector.generatePdf(htmlPayload).map {
              case response if response.status == OK =>
                Ok(response.bodyAsBytes.toArray)
                  .as("application/pdf")
                  .withHeaders(
                    "Content-Disposition" -> s"attachment; filename=${Messages("label.your_national_insurance_letter")
                      .replaceAll(" ", "-")}.pdf"
                  )
              case response                          =>
                throw new BadRequestException("Unexpected response from pdf-generator-service : " + response.body)
            }
          } else {
            errorRenderer.futureError(INTERNAL_SERVER_ERROR)
          }
        } else {
          errorRenderer.futureError(INTERNAL_SERVER_ERROR)
        }
    }
}
