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

import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, postRequestedFor, urlEqualTo}
import play.api.Application
import play.api.i18n.Lang
import models.TagColour
import testUtils.WireMockHelper
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, UpstreamErrorResponse}
import viewmodels.{ActivityType, Task, TaskStatus}

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
      |      "activityType": "task",
      |      "en": {
      |        "header": "You owe HMRC £500.",
      |        "body": "You owe tax for tax year 2026 to 2027",
      |        "url": "/tax-you-paid",
      |        "hint": "Due 31 January 2027",
      |        "dateTag": "govuk-tag--orange"
      |      },
      |      "cy": {
      |        "header": "Mae arnoch £500 i CThEF.",
      |        "body": "Mae arnoch dreth ar gyfer blwyddyn dreth 2026 i 2027",
      |        "url": "/treth-a-dalwyd-gennych",
      |        "hint": "Yn ddyledus 31 Ionawr 2027",
      |        "dateTag": "govuk-tag--orange"
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
            Some("/tax-you-paid"),
            Some("You owe tax for tax year 2026 to 2027"),
            Some("Due 31 January 2027"),
            ActivityType.Task,
            Some(TagColour.Orange)
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
            Some("/treth-a-dalwyd-gennych"),
            Some("Mae arnoch dreth ar gyfer blwyddyn dreth 2026 i 2027"),
            Some("Yn ddyledus 31 Ionawr 2027"),
            ActivityType.Task,
            Some(TagColour.Orange)
          )
        )
      )
    }

    "forward the user's authorisation to the Tasks and Activities service" in {
      stubPost(url, OK, Some(requestBody), Some(response))
      val authorisedHeaderCarrier = HeaderCarrier(
        authorization = Some(Authorization("Bearer test-token"))
      )

      connector.getTasks(generatedNino, Lang("en"))(authorisedHeaderCarrier, ec).value.futureValue

      server.verify(
        postRequestedFor(urlEqualTo(url))
          .withHeader(AUTHORIZATION, equalTo("Bearer test-token"))
      )
    }

    "return an empty sequence when the service returns no cards" in {
      stubPost(url, OK, Some(requestBody), Some("""{"cards": []}"""))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result mustBe Right(Seq.empty)
    }

    "return event metadata and allow the URL to be absent" in {
      val eventResponse =
        """
          |{
          |  "cards": [
          |    {
          |      "activityType": "event",
          |      "en": {
          |        "header": "Your tax calculation",
          |        "body": "You paid too little tax.",
          |        "hint": "Updated 1 July 2026",
          |        "dateTag": "govuk-tag--green"
          |      },
          |      "cy": {
          |        "header": "Eich cyfrifiad treth",
          |        "body": "Gwnaethoch dalu rhy ychydig o dreth.",
          |        "hint": "Diweddarwyd 1 Gorffennaf 2026",
          |        "dateTag": "govuk-tag--green"
          |      }
          |    }
          |  ]
          |}
          |""".stripMargin
      stubPost(url, OK, Some(requestBody), Some(eventResponse))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result mustBe Right(
        Seq(
          Task(
            title = "Your tax calculation",
            status = TaskStatus.Incomplete,
            href = None,
            hintText = Some("You paid too little tax."),
            dateText = Some("Updated 1 July 2026"),
            activityType = ActivityType.Event,
            dateTag = Some(TagColour.Green)
          )
        )
      )
    }

    "ignore unsupported date tag values" in {
      val unsupportedTagResponse = response.replace("govuk-tag--orange", "arbitrary-css-class")
      stubPost(url, OK, Some(requestBody), Some(unsupportedTagResponse))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result.map(_.head.dateTag) mustBe Right(None)
    }

    "accept responses with no date tag" in {
      val responseWithoutDateTag = response.replace(",\n        \"dateTag\": \"govuk-tag--orange\"", "")
      stubPost(url, OK, Some(requestBody), Some(responseWithoutDateTag))

      val result = connector.getTasks(generatedNino, Lang("en")).value.futureValue

      result.map(_.head.dateTag) mustBe Right(None)
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
