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
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class NiLetterController @Inject() (
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  featureFlagService: FeatureFlagService,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer
)(implicit configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc) {

  def printNationalInsuranceNumber: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      Future.successful(MovedPermanently(configDecorator.ptaNinoSaveUrl))
    }

  def saveNationalInsuranceNumberAsPdf: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)).async {
      Future.successful(MovedPermanently(configDecorator.ptaNinoSaveUrl))
    }
}
