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
import controllers.bindable.StrictContinueUrl
import error.LocalErrorHandler
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import util.LocalPartialRetriever

import scala.concurrent.Future

class LanguageController @Inject() (
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val partialRetriever: LocalPartialRetriever,
  val delegationConnector: FrontEndDelegationConnector,
  val messagesApi: MessagesApi,
  val configDecorator: ConfigDecorator,
  val localErrorHandler: LocalErrorHandler
) extends PertaxBaseController {

  def enGb(redirectUrl: StrictContinueUrl): Action[AnyContent] = changeLang(redirectUrl=redirectUrl, language="en")
  def cyGb(redirectUrl: StrictContinueUrl): Action[AnyContent] = changeLang(redirectUrl=redirectUrl, language="cy")

  def changeLang(redirectUrl: StrictContinueUrl, language: String): Action[AnyContent] = PublicAction { implicit pertaxContext =>
      Future.successful(Redirect(redirectUrl.url).withLang(Lang(language)))
  }
}
