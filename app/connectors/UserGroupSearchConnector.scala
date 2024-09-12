/*
 * Copyright 2024 HM Revenue & Customs
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

package connectors

import cats.data.EitherT
import com.google.inject.Inject
import config.ConfigDecorator
import play.api.Logger
import play.api.http.Status.NON_AUTHORITATIVE_INFORMATION
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import com.google.inject.Singleton
import models.enrolments.UsersGroupResponse
import play.api.libs.Files.logger
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Singleton
class UsersGroupsSearchConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: ConfigDecorator,
  httpClientResponse: HttpClientResponse
) {

  implicit val baseLogger: Logger = Logger(this.getClass.getName)

  def getUserDetails(credId: String)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Option[UsersGroupResponse]] = {
    val url = s"${appConfig.usersGroupsSearchBaseURL}/users/$credId"
    httpClientResponse
      .read(
        httpClient
          .get(url"$url")
          .transform(_.withRequestTimeout(appConfig.enrolmentStoreProxyTimeoutInMilliseconds.milliseconds))
          .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      )
      .map(httpResponse =>
        httpResponse.status match {
          case NON_AUTHORITATIVE_INFORMATION =>
            Some(httpResponse.json.as[UsersGroupResponse])
          case status                        =>
            logger.error(
              s"UserGroupSearch returned status of $status for credId $credId." +
                s"\nError Message: ${httpResponse.body}"
            )
            None
        }
      )
  }
}
