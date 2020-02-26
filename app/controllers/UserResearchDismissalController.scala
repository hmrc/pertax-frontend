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

import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.controllershelpers.HomePageCachingHelper
import error.LocalErrorHandler
import com.google.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import services._
import services.partials.MessageFrontendService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import util.LocalPartialRetriever

class UserResearchDismissalController @Inject()(
  val messagesApi: MessagesApi,
  val citizenDetailsService: CitizenDetailsService,
  val messageFrontendService: MessageFrontendService,
  val localErrorHandler: LocalErrorHandler,
  val homePageCachingHelper: HomePageCachingHelper,
  authJourney: AuthJourney,
  auditConnector: AuditConnector,
  authConnector: AuthConnector)(implicit partialRetriever: LocalPartialRetriever, configDecorator: ConfigDecorator)
    extends PertaxBaseController {

  def dismissUrBanner: Action[AnyContent] = authJourney.authWithPersonalDetails { implicit request =>
    homePageCachingHelper.storeUserUrDismissal()
    NoContent
  }
}
