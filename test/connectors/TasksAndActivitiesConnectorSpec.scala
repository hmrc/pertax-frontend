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

import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, urlEqualTo}
import play.api.Application
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse
import viewmodels.{Task, TaskStatus}

class TasksAndActivitiesConnectorSpec extends ConnectorSpec with WireMockHelper {

  private val url = s"/pta-tasks-and-events/${generatedNino.nino}/tasks"

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.pta-tasks-and-events.port"                  -> server.port(),
      "microservice.services.pta-tasks-and-events.timeoutInMilliseconds" -> 5000
    )
  )

  private def connector: TasksAndActivitiesConnector = app.injector.instanceOf[TasksAndActivitiesConnector]

  "getTasks" must {
    "return tasks from a successful object response" in {
      val response =
        """
          |{
          |  "tasks": [
          |    {
          |      "title": "You owe £500 for tax year 2026 to 2027",
          |      "status": "incomplete",
          |      "href": "/tax-you-paid",
          |      "hintText": "You should pay this now"
          |    },
          |    {
          |      "title": "Refund claimed",
          |      "status": "completed",
          |      "href": "/tax-you-paid/refund"
          |    }
          |  ]
          |}
          |""".stripMargin

      stubGet(url, OK, Some(response))

      val result = connector.getTasks(generatedNino).value.futureValue

      result mustBe Right(
        Seq(
          Task(
            "You owe £500 for tax year 2026 to 2027",
            TaskStatus.Incomplete,
            "/tax-you-paid",
            Some("You should pay this now")
          ),
          Task("Refund claimed", TaskStatus.Completed, "/tax-you-paid/refund", None)
        )
      )
      server.verify(getRequestedFor(urlEqualTo(url)))
    }

    "return an empty sequence when the service returns no tasks" in {
      stubGet(url, OK, Some("""{"tasks": []}"""))

      val result = connector.getTasks(generatedNino).value.futureValue

      result mustBe Right(Seq.empty)
    }

    "return tasks from an array response" in {
      val response =
        """
          |[
          |  {
          |    "title": "You owe £500 for tax year 2026 to 2027",
          |    "href": "/tax-you-paid"
          |  }
          |]
          |""".stripMargin

      stubGet(url, OK, Some(response))

      val result = connector.getTasks(generatedNino).value.futureValue

      result mustBe Right(Seq(Task("You owe £500 for tax year 2026 to 2027", TaskStatus.Incomplete, "/tax-you-paid", None)))
    }

    List(BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND).foreach { statusCode =>
      s"return an UpstreamErrorResponse when $statusCode is returned" in {
        stubGet(url, statusCode, Some("""{"reason":"failed"}"""))

        val result = connector.getTasks(generatedNino).value.futureValue

        result mustBe a[Left[UpstreamErrorResponse, _]]
      }
    }

    "return an UpstreamErrorResponse when the response cannot be parsed" in {
      stubGet(url, OK, Some("""{"tasks": [{"title": "Missing href"}]}"""))

      val result = connector.getTasks(generatedNino).value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.exists(_.statusCode == BAD_GATEWAY) mustBe true
    }
  }
}
