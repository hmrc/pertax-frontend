/*
 * Copyright 2017 HM Revenue & Customs
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
import controllers.auth.{AuthorisedActions, PertaxRegime}
import controllers.helpers.HomePageCachingHelper
import error.LocalErrorHandler
import play.api.i18n.MessagesApi
import services._
import util.LocalPartialRetriever

import scala.concurrent.Future


class UserResearchDismissalController @Inject() (
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val userDetailsService: UserDetailsService,
  val delegationConnector: FrontEndDelegationConnector,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val localErrorHandler: LocalErrorHandler,
  val homePageCachingHelper: HomePageCachingHelper
  ) extends PertaxBaseController with AuthorisedActions {


  def dismissUrBanner = ProtectedAction(Nil) { implicit request =>
    homePageCachingHelper.storeUserUrDismissal()
    Future.successful(NoContent)
  }
}