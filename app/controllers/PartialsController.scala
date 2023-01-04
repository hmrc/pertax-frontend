/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.Inject
import config.ConfigDecorator
import error.LocalErrorHandler
import models.Breadcrumb
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import views.html.integration.MainContentHeaderView

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

class PartialsController @Inject() (
  val localErrorHandler: LocalErrorHandler,
  cc: MessagesControllerComponents,
  mainContentHeaderView: MainContentHeaderView
)(implicit configDecorator: ConfigDecorator, ex: ExecutionContext)
    extends PertaxBaseController(cc) {

  def mainContentHeader(
    name: Option[String],
    lastLogin: Option[Long],
    itemText: List[String],
    itemUrl: List[String],
    showBetaBanner: Option[Boolean],
    deskProToken: Option[String],
    langReturnUrl: Option[String],
    lang: Option[String],
    showLastItem: Boolean
  ): Action[AnyContent] = Action.async { implicit request =>
    Future.successful {

      val breadcrumb: Breadcrumb = (itemText zip itemUrl).dropRight(if (showLastItem) 0 else 1)

      Ok(
        mainContentHeaderView(
          name,
          lastLogin.map(x => LocalDateTime.ofInstant(Instant.ofEpochMilli(x), ZoneId.of("Europe/London"))),
          breadcrumb,
          showBetaBanner.getOrElse(false),
          deskProToken,
          langReturnUrl.filter(x => configDecorator.welshLangEnabled)
        )
      )
    }
  }
}
