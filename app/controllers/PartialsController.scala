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

import javax.inject.Inject

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import error.LocalErrorHandler
import models.Breadcrumb
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import util.LocalPartialRetriever

import scala.concurrent.Future

class PartialsController @Inject() (
  val messagesApi: MessagesApi,
  val auditConnector: PertaxAuditConnector,
  val authConnector: PertaxAuthConnector,
  val delegationConnector: FrontEndDelegationConnector,
  val partialRetriever: LocalPartialRetriever,
  val configDecorator: ConfigDecorator,
  val localErrorHandler: LocalErrorHandler
) extends PertaxBaseController {
  
  def mainContentHeader(name: Option[String], lastLogin: Option[Long], itemText: List[String], itemUrl: List[String],
                        showBetaBanner: Option[Boolean], deskProToken: Option[String], langReturnUrl: Option[String],
                        lang: Option[String], showLastItem: Boolean): Action[AnyContent] = Action.async {
    implicit request =>
      Future.successful {

        val breadcrumb: Breadcrumb = (itemText zip itemUrl).dropRight(if(showLastItem) 0 else 1)

        Ok(views.html.integration.mainContentHeader(name, lastLogin.map(new DateTime(_)), breadcrumb, showBetaBanner.getOrElse(false),
          deskProToken, langReturnUrl.filter(x => configDecorator.welshLangEnabled), configDecorator))
      }
  }
}
