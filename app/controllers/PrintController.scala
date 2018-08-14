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

import java.util.Calendar

import javax.inject.Inject
import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import error.LocalErrorHandler
import play.api.i18n.MessagesApi
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, UserDetailsService}
import util.LocalPartialRetriever
import connectors.PdfGeneratorConnector

import scala.concurrent.Future
import org.joda.time.LocalDate
import play.api.mvc.Action
import uk.gov.hmrc.http.BadRequestException

class PrintController @Inject() (val messagesApi: MessagesApi,
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
  val pdfGeneratorConnector: PdfGeneratorConnector
  ) extends PertaxBaseController with AuthorisedActions {

  def printNationalInsuranceNumber = VerifiedAction(baseBreadcrumb) {
    implicit pertaxContext =>
      enforcePersonDetails {
        payeAccount =>
          personDetails =>
            val dateString: String = LocalDate.now.toString("MM/YY")
            Future.successful(Ok(views.html.print.printNationalInsuranceNumber(personDetails,dateString)))
      }
  }

  def removeScriptTags(html: String) = html.replaceAll("<script[\\s\\S]*?/script>", "")

  def saveNationalInsuranceNumberLetterAsPdf = VerifiedAction(baseBreadcrumb) {

    implicit pertaxContext =>
      enforcePersonDetails {
        payeAccount =>
          personDetails =>
            val dateString: String = LocalDate.now.toString("MM/YY")

            val body = """<!DOCTYPE html>
            <html>
              <body>
                <h1 style="color: red">My First Heading</h1>
                <h1 style="color: red">My Second Heading</h1>
                 <p style="color: red;">My first paragraph.</p>
              </body>
            </html>"""

            pdfGeneratorConnector.generatePdf(removeScriptTags(body.toString)).map { response =>
              if (response.status != OK)
                throw new BadRequestException(response.body)
              else
                Ok(response.bodyAsBytes.toArray).as("application/pdf")
                  .withHeaders("Content-Disposition" -> s"attachment; filename=result.pdf")
            }
      }
  }

}
