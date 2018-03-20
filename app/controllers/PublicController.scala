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

import javax.inject.Inject

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.auth.AuthenticationProviderIds
import util.LocalPartialRetriever

import scala.concurrent.Future

class PublicController @Inject() (
  val messagesApi: MessagesApi,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val delegationConnector: FrontEndDelegationConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator
) extends PertaxBaseController {
  
  def verifyEntryPoint = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Redirect(routes.ApplicationController.index).withNewSession.addingToSession(
          SessionKeys.authProvider -> AuthenticationProviderIds.VerifyProviderId
        )
      }
  }

  def governmentGatewayEntryPoint = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Redirect(routes.ApplicationController.index).withNewSession.addingToSession(
          SessionKeys.authProvider -> AuthenticationProviderIds.GovernmentGatewayId
        )
      }
  }
  
  def sessionTimeout = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Ok(views.html.public.sessionTimeout())
      }
  }

  def redirectToExitSurvey(origin: Origin) = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Redirect(configDecorator.getFeedbackSurveyUrl(origin))
      }
  }

  def redirectToTaxCreditsService(): Action[AnyContent] = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Redirect(configDecorator.tcsServiceRouterUrl, MOVED_PERMANENTLY)
      }
  }

  def redirectToPersonalDetails(): Action[AnyContent] = PublicAction {
    implicit pertaxContext =>
      Future.successful {
        Redirect(routes.AddressController.personalDetails)
      }
  }
}
