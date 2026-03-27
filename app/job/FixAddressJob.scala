/*
 * Copyright 2026 HM Revenue & Customs
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

package job

import com.google.inject.{Inject, Singleton}
import controllers.tempAddressFix.FixControllerHelper
import org.quartz.*
import play.api.Logging
import play.api.libs.typedmap.TypedMap
import play.api.mvc.request.{RemoteConnection, RequestTarget}
import play.api.mvc.{AnyContentAsEmpty, Headers, Request, RequestHeader}
import repositories.TempAddressFixRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class FixAddressJob @Inject() (
  tempAddressFixRepository: TempAddressFixRepository,
  fixControllerHelper: FixControllerHelper
)(implicit ec: ExecutionContext)
    extends Job
    with Logging {
  override def execute(context: JobExecutionContext): Unit = {
    logger.debug("Starting scheduled job")
    val requestHeader                                     = new RequestHeader {
      override def connection: RemoteConnection = RemoteConnection("127.0.0.1", secure = false, None)
      override def method: String               = "GET"
      override def target: RequestTarget        = RequestTarget("/internal-job", "/internal-job", Map.empty)
      override def version: String              = "HTTP/1.1"
      override def headers: Headers             = Headers("User-Agent" -> "pertax-frontend")
      override def attrs: TypedMap              = TypedMap.empty
    }
    implicit val request: Request[AnyContentAsEmpty.type] = Request(requestHeader, AnyContentAsEmpty)
    implicit val hc: HeaderCarrier                        = HeaderCarrier()

    tempAddressFixRepository.findTodo.map {
      case None         => ()
      case Some(record) =>
        logger.info(s"Starting check for ${record.obscuredId}")
        fixControllerHelper
          .processRecord(record.nino)
          .bimap(
            error =>
              logger
                .warn(
                  s"Error during schedule job for nino ${record.obscuredId}: ${error.errorMessage} (${record.status})"
                ),
            status => logger.info(s"Schedule job finished for ${record.obscuredId} with status $status")
          )
    }
  }
}
