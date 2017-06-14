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

package controllers.helpers

import models.PertaxContext
import play.api.mvc.Result
import play.api.mvc.Results._
import services.{ActivatePaperlessActivatedResponse, ActivatePaperlessRequiresUserActionResponse, PreferencesFrontendService}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PaperlessInterruptHelper {

  def preferencesFrontendService: PreferencesFrontendService

  def enforcePaperlessPreference(block: => Future[Result])(implicit pertaxContext: PertaxContext, hc: HeaderCarrier): Future[Result] = {
    val preferencesResponse = pertaxContext.user.map { user =>
      preferencesFrontendService.getPaperlessPreference(user)
    } getOrElse Future.successful(ActivatePaperlessActivatedResponse)

    preferencesResponse flatMap {
      case ActivatePaperlessRequiresUserActionResponse(redirectUrl) => Future.successful(Redirect(redirectUrl))
      case _ => block
    }
  }
}
