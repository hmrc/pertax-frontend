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

import config.ConfigDecorator
import com.google.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever

import scala.concurrent.Future

class PublicController @Inject()(val messagesApi: MessagesApi)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  templateRenderer: TemplateRenderer)
    extends PertaxBaseController {

  def sessionTimeout: Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Ok(views.html.public.sessionTimeout())
    }
  }

  def redirectToExitSurvey(origin: Origin): Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Redirect(configDecorator.getFeedbackSurveyUrl(origin))
    }
  }

  def redirectToTaxCreditsService(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Redirect(configDecorator.tcsServiceRouterUrl, MOVED_PERMANENTLY)
    }
  }

  def redirectToPersonalDetails(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Redirect(routes.AddressController.personalDetails())
    }
  }
}
