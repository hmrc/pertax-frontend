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

import java.util.Locale

import _root_.connectors.{PertaxAuditConnector, PertaxAuthConnector}
import config.ConfigDecorator
import models.{Breadcrumb, PertaxContext}
import play.api.i18n.{I18nSupport, Lang, Messages}
import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.controller.{FrontendController, UnauthorisedAction}
import util.LocalPartialRetriever

import scala.concurrent.Future

abstract class PertaxBaseController extends DelegationAwareActions with FrontendController with I18nSupport {

  def auditConnector: PertaxAuditConnector
  def authConnector: PertaxAuthConnector

  def partialRetriever: LocalPartialRetriever
  def configDecorator: ConfigDecorator

  val baseBreadcrumb: Breadcrumb =
    List("label.account_home" -> routes.ApplicationController.index().url)

  def PublicAction(block: PertaxContext => Future[Result]): Action[AnyContent] = {
    UnauthorisedAction.async {
      implicit request =>
        block(PertaxContext(request, partialRetriever, configDecorator))
    }
  }

  //FIXME - put this in a filter
  def getTrimmedData(request: Request[AnyContent]) = {
    request.body.asFormUrlEncoded.map { data =>
      data.map {
        case (key, vals) => (key, vals.map(_.trim))
      }
    }.getOrElse(Map[String,Seq[String]]())
  }

  def showingWarningIfWelsh[T](block: PertaxContext => T)(implicit pertaxContext: PertaxContext, messages: Messages): T = {
    block(pertaxContext.withWelshWarning(messages.lang.code == "cy"))
  }
}
