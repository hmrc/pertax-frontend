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

import controllers.auth.requests.UserRequest
import models.{Breadcrumb, PersonDetails}
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

abstract class PertaxBaseController(cc: MessagesControllerComponents) extends FrontendController(cc) with I18nSupport {

  val baseBreadcrumb: Breadcrumb =
    List("label.account_home" -> routes.HomeController.index.url)

  def displayName(optPersonDetails: Option[PersonDetails])(implicit request: UserRequest[AnyContent]): String = {
    def defaultName = "Personal tax account"
    (request.trustedHelper, optPersonDetails) match {
      case (Some(th), _) => th.principalName
      case (_, None)     => defaultName
      case (_, Some(pd)) => pd.person.shortName.getOrElse(defaultName)
    }
  }

}
