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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import controllers.bindable.Origin
import views.html.public.SessionTimeoutView

import scala.concurrent.{ExecutionContext, Future}

class PublicController @Inject() (cc: MessagesControllerComponents, sessionTimeoutView: SessionTimeoutView)(implicit
  configDecorator: ConfigDecorator,
  ec: ExecutionContext
) extends PertaxBaseController(cc) {

  def governmentGatewayEntryPoint: Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Redirect(routes.HomeController.index).withNewSession.addingToSession(
        configDecorator.authProviderKey -> configDecorator.authProviderGG
      )
    }
  }

  def sessionTimeout: Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Ok(sessionTimeoutView())
    }
  }

  def redirectToExitSurvey(origin: Origin): Action[AnyContent] = Action.async { _ =>
    Future.successful {
      Redirect(configDecorator.getFeedbackSurveyUrl(origin))
    }
  }

  def redirectToTaxCreditsService(): Action[AnyContent] = Action.async { _ =>
    Future.successful {
      Redirect(configDecorator.tcsServiceRouterUrl, MOVED_PERMANENTLY)
    }
  }

  def redirectToYourProfile(): Action[AnyContent] = Action.async { _ =>
    Future.successful {
      Redirect(controllers.address.routes.PersonalDetailsController.onPageLoad)
    }
  }
}
