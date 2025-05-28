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

package services

import com.google.inject.Inject
import connectors.FandFConnector
import controllers.auth.TrustedHelperResult
import controllers.auth.TrustedHelperResult.{Error, Found, NotFound}
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class FandfService @Inject() (
  fandFConnector: FandFConnector
) extends Logging {

  def getTrustedHelper()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustedHelperResult] =
    fandFConnector.getTrustedHelper().value.map {
      case Right(Some(helper)) => Found(helper)
      case Right(None)         => NotFound
      case Left(ex)            =>
        if (ex.statusCode == NOT_FOUND) {
          NotFound
        } else {
          logger.warn(s"Fandf call failed with ${ex.statusCode}: ${ex.message}")
          Error(ex)
        }
    }
}
