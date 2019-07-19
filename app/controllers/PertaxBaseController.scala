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

import _root_.connectors.{PertaxAuditConnector, PertaxAuthConnector}
import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth.PublicActions
import controllers.helpers.ControllerLikeHelpers
import models.{Breadcrumb, PertaxContext}
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import uk.gov.hmrc.play.frontend.controller.Utf8MimeTypes
import util.LocalPartialRetriever

import scala.concurrent.Future

class PertaxDependencies @Inject ()(
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator)

abstract class PertaxBaseController extends Controller with Utf8MimeTypes with PublicActions with I18nSupport with ControllerLikeHelpers {
  val pertaxDependencies: PertaxDependencies

  def auditConnector: PertaxAuditConnector = pertaxDependencies.auditConnector
  def authConnector: PertaxAuthConnector = pertaxDependencies.authConnector
  def partialRetriever: LocalPartialRetriever = pertaxDependencies.partialRetriever
  def configDecorator: ConfigDecorator = pertaxDependencies.configDecorator

  implicit class SessionKeyRemover(result: Future[Result]) {
    def removeSessionKey(key: String)(implicit request: Request[_]) = result.map {_.withSession(request.session - key)}
  }

  val baseBreadcrumb: Breadcrumb =
    List("label.account_home" -> routes.HomeController.index().url)

  def showingWarningIfWelsh[T](block: PertaxContext => T)(implicit pertaxContext: PertaxContext, messages: Messages): T = {
    block(pertaxContext.withWelshWarning(messages.lang.code == "cy"))
  }
}


trait PertaxBaseControllerTrait extends PertaxBaseController