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

package connectors

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.ws.{WSClient, WSResponse}
import services.http.WsAllMethods
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future

@ImplementedBy(classOf[FrontendPdfGeneratorConnector])
trait PdfGeneratorConnector {
  val serviceURL: String
  def getWsClient: WSClient

  def generatePdf(html: String): Future[WSResponse] = {
    getWsClient.url(serviceURL).post(Map("html" -> Seq(html)))
  }
}

@Singleton
class FrontendPdfGeneratorConnector @Inject() (wsHttp: WsAllMethods) extends PdfGeneratorConnector with ServicesConfig {
  val pdfServiceUrl: String = baseUrl("pdf-generator-service")
  val serviceURL = pdfServiceUrl + "/pdf-generator-service/generate"
  def getWsClient:WSClient = wsHttp.wsClient
}
