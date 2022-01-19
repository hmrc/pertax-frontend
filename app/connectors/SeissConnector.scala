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

package connectors

import com.google.inject.Inject
import config.ConfigDecorator
import models.{SeissModel, SeissRequest}
import play.api.i18n.Lang.logger
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeissConnector @Inject() (http: HttpClient, implicit val ec: ExecutionContext, configDecorator: ConfigDecorator) {

  def getClaims(utr: String)(implicit hc: HeaderCarrier): Future[List[SeissModel]] = {
    val seissRequest = SeissRequest(utr)
    http.POST[SeissRequest, List[SeissModel]](
      s"${configDecorator.seissUrl}/self-employed-income-support/get-claims",
      seissRequest
    )
  }.recover { case exception =>
    logger.warn("[SeissConnector][getClaims] Seiss error", exception)
    List.empty[SeissModel]
  }
}
