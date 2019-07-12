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

package controllers.auth

import javax.inject.Inject

import config.ConfigDecorator
import play.api.mvc.Results._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.frontend.auth.{AnyAuthenticationProvider, GovernmentGateway, Verify}

import scala.concurrent.Future

class PertaxAuthenticationProvider @Inject()(val configDecorator: ConfigDecorator) extends AnyAuthenticationProvider {

  override def ggwAuthenticationProvider: GovernmentGateway = new GovernmentGateway {

    override def redirectToLogin(implicit request: Request[_]) = ggRedirect

    override def continueURL: String = throw new RuntimeException("Unused")

    override def loginURL: String = throw new RuntimeException("Unused")
  }

  override def verifyAuthenticationProvider: Verify = new Verify {
    override def login: String = throw new RuntimeException("Unused")

    override def redirectToLogin(implicit request: Request[_]) = idaRedirect
  }

  def defaultOrigin = ggwAuthenticationProvider.defaultOrigin

  override def login: String = throw new RuntimeException("Unused")

  override def redirectToLogin(implicit request: Request[_]) = ggRedirect

  def postSignInRedirectUrl(implicit request: Request[_]) = {
    configDecorator.pertaxFrontendHost + controllers.routes.ApplicationController.uplift( Some( ContinueUrl(configDecorator.pertaxFrontendHost + request.path) ) ).url
  }

  private def idaRedirect(implicit request: Request[_]): Future[Result] = {
    lazy val idaSignIn = s"${configDecorator.citizenAuthHost}/${configDecorator.ida_web_context}/login"
    Future.successful(Redirect(idaSignIn).withSession(
      SessionKeys.loginOrigin -> defaultOrigin,
      SessionKeys.redirect -> postSignInRedirectUrl
    ))
  }

  private def ggRedirect(implicit request: Request[_]): Future[Result] = {
    lazy val ggSignIn = s"${configDecorator.companyAuthHost}/${configDecorator.gg_web_context}"
    Future.successful(Redirect(ggSignIn, Map(
      "continue" -> Seq(postSignInRedirectUrl),
      "accountType" -> Seq("individual"),
      "origin" -> Seq(defaultOrigin)
    )))
  }
}
