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

import connectors.FrontEndDelegationConnector
import controllers.auth.{AuthorisedActions, PertaxRegime}
import controllers.helpers.HomePageCachingHelper
import error.LocalErrorHandler
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import services._
import services.partials.MessageFrontendService

import scala.concurrent.Future

class UserResearchDismissalController @Inject()(
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val userDetailsService: UserDetailsService,
  val messageFrontendService: MessageFrontendService,
  val delegationConnector: FrontEndDelegationConnector,
  val pertaxDependencies: PertaxDependencies,
  val pertaxRegime: PertaxRegime,
  val localErrorHandler: LocalErrorHandler,
  val homePageCachingHelper: HomePageCachingHelper
) extends PertaxBaseController with AuthorisedActions {

  def dismissUrBanner: Action[AnyContent] = verifiedAction(Nil) { implicit request =>
    homePageCachingHelper.storeUserUrDismissal()
    Future.successful(NoContent)
  }
}
