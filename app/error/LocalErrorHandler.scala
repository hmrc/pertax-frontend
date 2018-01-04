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

package error

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuthConnector}
import controllers.auth.{AuthorisedActions, PertaxRegime}
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{CitizenDetailsService, UserDetailsService}
import uk.gov.hmrc.play.frontend.filters.CookieCryptoFilter
import util.LocalPartialRetriever


@Singleton
class LocalErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  val userDetailsService: UserDetailsService,
  val citizenDetailsService: CitizenDetailsService,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val pertaxRegime: PertaxRegime,
  val delegationConnector: FrontEndDelegationConnector,
  val authConnector: PertaxAuthConnector,
  val materializer: Materializer,
  val sessionCookieCryptoFilter: CookieCryptoFilter
) extends HttpErrorHandler with AuthorisedActions with I18nSupport {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {

    if(statusCode==BAD_REQUEST || statusCode==NOT_FOUND) {
      AuthorisedAction() { implicit pertaxContext =>
        renderError(statusCode)
      }.apply(request).run()(materializer)
    }
    else {
      PublicAction { implicit pertaxContext =>
        renderError(statusCode)
      }.apply(request).run()(materializer)
    }
  }

  def onServerError(request: RequestHeader, exception: Throwable) =
    onClientError(request, INTERNAL_SERVER_ERROR, "")

}
