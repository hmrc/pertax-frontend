/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PdfGeneratorConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import error.LocalErrorHandler
import javax.inject.Inject
import org.joda.time.LocalDate
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, UserDetailsService}
import uk.gov.hmrc.http.BadRequestException
import util.LocalPartialRetriever

import scala.concurrent.Future
import scala.io.Source


class NiLetterController @Inject()(val messagesApi: MessagesApi,
                                   val citizenDetailsService: CitizenDetailsService,
                                   val userDetailsService: UserDetailsService,
                                   val messageFrontendService: MessageFrontendService,
                                   val delegationConnector: FrontEndDelegationConnector,
                                   val auditConnector: PertaxAuditConnector,
                                   val authConnector: PertaxAuthConnector,
                                   val partialRetriever: LocalPartialRetriever,
                                   val configDecorator: ConfigDecorator,
                                   val pertaxRegime: PertaxRegime,
                                   val localErrorHandler: LocalErrorHandler,
                                   val pdfGeneratorConnector: PdfGeneratorConnector) extends PertaxBaseController with AuthorisedActions {

  def printNationalInsuranceNumber: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      enforcePersonDetails {
        payeAccount =>
          personDetails =>
            Future.successful(Ok(views.html.print.printNationalInsuranceNumber(personDetails, LocalDate.now.toString("MM/YY"), configDecorator.saveNiLetterAsPdfLinkEnabled)))
      }
  }

  def saveNationalInsuranceNumberAsPdf: Action[AnyContent] = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      enforcePersonDetails {
        payeAccount =>
          personDetails =>
            val fontPath = s"""<link href="${configDecorator.frontendPath}/template/assets/stylesheets/fonts.css" media="all" rel="stylesheet" type="text/css" />"""
            val applicationMinCss = s"""<link rel="stylesheet" href="${configDecorator.platformFrontendHost}${controllers.routes.AssetsController.versioned("css/application.min.css").url}" />"""
            val minifiedCss = Source.fromURL(s"${configDecorator.platformFrontendHost}${controllers.routes.AssetsController.versioned("stylesheets/pertaxMain.css").url}")
              .mkString.replaceAll("media print", "media all")
            val htmlPayload = "<!doctype html><html><head></head><body>".concat(
              views.html.print.niLetter(personDetails, LocalDate.now.toString("MM/YY")).toString)
              .replace("</head>" , s"<style>${minifiedCss} html{background: #FFF !important;} * {font-family: nta !important;}</style>${fontPath}${applicationMinCss}</head>").filter(_ >= ' ').trim.replaceAll("  +", "")
              .concat("</body></html>").trim()
            pdfGeneratorConnector.generatePdf(htmlPayload).map { response =>
              if (response.status != OK) throw new BadRequestException("Unexpected response from pdf-generator-service : " + response.body)
              else Ok(response.bodyAsBytes.toArray).as("application/pdf")
                .withHeaders("Content-Disposition" -> s"attachment; filename=${Messages("label.your_national_insurance_letter").replaceAll(" ", "-")}.pdf")
            }
      }
  }
}
