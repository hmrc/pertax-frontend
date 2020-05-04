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

import controllers.controllershelpers.ControllerLikeHelpers
import models.Breadcrumb
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.{FrontendController, Utf8MimeTypes}

import scala.concurrent.{ExecutionContext, Future}

abstract class PertaxBaseController(cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
    extends FrontendController(cc) with I18nSupport with ControllerLikeHelpers {

  implicit class SessionKeyRemover(result: Future[Result]) {
    def removeSessionKey(key: String)(implicit request: Request[_]): Future[Result] = result.map {
      _.withSession(request.session - key)
    }
  }

  val baseBreadcrumb: Breadcrumb =
    List("label.account_home" -> routes.HomeController.index().url)

}

trait PertaxBaseControllerTrait extends PertaxBaseController
