/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.{FrontEndDelegationConnector, PayApiConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import error.RendersErrors
import javax.inject.Inject
import models.PaymentRequest
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, UserDetailsService}
import uk.gov.hmrc.time.CurrentTaxYear

class PaymentsController @Inject()(
  val messagesApi: MessagesApi,
  val messageFrontendService: MessageFrontendService,
  val delegationConnector: FrontEndDelegationConnector,
  val citizenDetailsService: CitizenDetailsService,
  val pertaxRegime: PertaxRegime,
  val userDetailsService: UserDetailsService,
  val pertaxDependencies: PertaxDependencies,
  val payApiConnector: PayApiConnector)
    extends PertaxBaseController with AuthorisedActions with CurrentTaxYear with RendersErrors {

  def makePayment: Action[AnyContent] = verifiedAction(baseBreadcrumb) { implicit pertaxContext =>
    enforceSaAccount { saAccount =>
      val paymentRequest = PaymentRequest(configDecorator, saAccount.utr.toString())
      for {
        response <- payApiConnector.createPayment(paymentRequest)
      } yield {
        response match {
          case Some(createPayment) => Redirect(createPayment.nextUrl)
          case None                => error(BAD_REQUEST)
        }
      }
    }

  }
}
