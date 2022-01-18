/*
 * Copyright 2022 HM Revenue & Customs
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

package models

import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

sealed trait ActivatePaperlessResponse
case object ActivatePaperlessActivatedResponse extends ActivatePaperlessResponse
case object ActivatePaperlessNotAllowedResponse extends ActivatePaperlessResponse
case class ActivatePaperlessRequiresUserActionResponse(redirectUrl: String) extends ActivatePaperlessResponse

object ActivatePaperlessResponse extends Logging {

  implicit lazy val httpReads: HttpReads[ActivatePaperlessResponse] =
    new HttpReads[ActivatePaperlessResponse] {
      override def read(method: String, url: String, response: HttpResponse): ActivatePaperlessResponse =
        response.status match {
          case r if r >= 200 && r < 300 =>
            ActivatePaperlessActivatedResponse

          case PRECONDITION_FAILED =>
            val redirectUrl = response.json \ "redirectUserTo"
            logger.warn(
              "Precondition failed when getting paperless preference record from preferences-frontend-service"
            )
            ActivatePaperlessRequiresUserActionResponse(redirectUrl.as[String])

          case r =>
            logger.warn(s"Unexpected $r response getting paperless preference record from preferences-frontend-service")
            ActivatePaperlessNotAllowedResponse
        }
    }

}
