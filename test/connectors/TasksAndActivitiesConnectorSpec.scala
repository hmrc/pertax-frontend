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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlEqualTo}
import play.api.Application
import play.api.i18n.Lang
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse
import viewmodels.{Task, TaskStatus}

class TasksAndActivitiesConnectorSpec extends ConnectorSpec with WireMockHelper {

  private val url         = "/pta-tasks-and-events/retrieve-tasks-and-events"
  private val requestBody =
    s"""{"userId":"${generatedNino.nino}","serviceList":["p800"]}"""

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.pta-tasks-and-events.port"                  -> server.port(),
      "microservice.services.pta-tasks-and-events.timeoutInMilliseconds" -> 5000
    )
  )

  private def connector: TasksAndActivitiesConnector = app.injector.instanceOf[TasksAndActivitiesConnector]

  private val response =
    """
      |{
      |  "cards": [
      |    {
      |      "en": {
      |        "header": "You owe HMRC £500.",
      |        "body": "You owe tax for tax year 2026 to 2027",
      |        "url": "/tax-you-paid",
      |        "hint": null
      |      },
      |      "cy": {
      |        "header": "Mae arnoch £500 i CThEF.",
      |        "body": "Mae arnoch dreth ar gyfer blwyddyn dreth 2026 i 2027",
      |        "url": "/treth-a-dalwyd-gennych",
      |        "hint": null
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin

  "getTasks" must {
    "post the user and requested domains and return English card content" in {
      stubPost(url, OK, Some(requestBody), Some(response))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result mustBe Right(
        Seq(
          Task(
            "You owe HMRC £500.",
            TaskStatus.Incomplete,
            "/tax-you-paid",
            Some("You owe tax for tax year 2026 to 2027")
          )
        )
      )
      server.verify(postRequestedFor(urlEqualTo(url)))
    }

    "return Welsh card content when Welsh is selected" in {
      stubPost(url, OK, Some(requestBody), Some(response))

      val result = connector.getTasks(generatedNino, Lang("cy")).value.futureValue

      result mustBe Right(
        Seq(
          Task(
            "Mae arnoch £500 i CThEF.",
            TaskStatus.Incomplete,
            "/treth-a-dalwyd-gennych",
            Some("Mae arnoch dreth ar gyfer blwyddyn dreth 2026 i 2027")
          )
        )
      )
    }

    "return an empty sequence when the service returns no cards" in {
      stubPost(url, OK, Some(requestBody), Some("""{"cards": []}"""))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result mustBe Right(Seq.empty)
    }

    List(BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND).foreach { statusCode =>
      s"return an UpstreamErrorResponse when $statusCode is returned" in {
        stubPost(url, statusCode, Some(requestBody), Some("""{"reason":"failed"}"""))

        val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
      }
    }

    "return an UpstreamErrorResponse when the response cannot be parsed" in {
      stubPost(url, OK, Some(requestBody), Some("""{"cards": [{"en": {}}]}"""))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.exists(_.statusCode == BAD_GATEWAY) mustBe true
    }
  }
}
