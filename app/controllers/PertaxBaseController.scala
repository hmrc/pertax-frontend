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
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

abstract class PertaxBaseController(cc: MessagesControllerComponents) extends FrontendController(cc) with I18nSupport {

  protected val baseBreadcrumb: Breadcrumb                =
    List("label.account_home" -> routes.HomeController.index.url)

  private val emptyStringToNone: String => Option[String] = s =>
    if (s.isEmpty) {
      None
    } else {
      Some(s)
    }

  final protected def personalDetailsNameOrDefault(
    optPersonDetails: Option[PersonDetails]
  )(implicit request: UserRequest[AnyContent]): String = {
    def defaultName = Messages("label.your_account")
    (request.trustedHelper, optPersonDetails) match {
      case (_, Some(pd)) => pd.person.shortName.flatMap(emptyStringToNone).getOrElse(defaultName)
      case (Some(th), _) => th.principalName
      case _             => defaultName

    }
  }

  final protected def personalDetailsNameOrTrustedHelperName(
    optPersonDetails: Option[PersonDetails]
  )(implicit request: UserRequest[AnyContent]): Option[String] =
    optPersonDetails match {
      case pd @ Some(_) => pd.flatMap(_.person.shortName)
      case _            => request.trustedHelper.map(_.principalName)
    }

}
