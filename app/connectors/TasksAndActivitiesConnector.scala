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

import cats.data.EitherT
import com.google.inject.Inject
import config.ConfigDecorator
import play.api.Logging
import play.api.http.Status.BAD_GATEWAY
import play.api.i18n.Lang
import play.api.libs.json.{Format, JsError, JsString, Json, OFormat, Reads, Writes}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import viewmodels.{ActivityType, Task, TaskStatus}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TasksAndActivitiesConnector @Inject() (
  httpClientV2: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator
) extends Logging {

  def getTasks(nino: Nino, lang: Lang)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, Seq[Task]] = {
    val url     = configDecorator.tasksAndActivitiesUrl
    val request = TasksAndActivitiesRequest(nino.nino, List("p800"))

    EitherT(
      httpClientResponse
        .read(
          httpClientV2
            .post(url"$url")
            .withBody(Json.toJson(request))
            .transform(_.withRequestTimeout(configDecorator.tasksAndActivitiesTimeoutInMilliseconds.milliseconds))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
        )
        .value
        .map {
          case Right(response) => parseTasks(response, lang)
          case Left(error)     => Left(error)
        }
    )
  }

  private def parseTasks(response: HttpResponse, lang: Lang): Either[UpstreamErrorResponse, Seq[Task]] =
    Try(response.json).toEither.left
      .map { error =>
        logger.error("Unable to read Tasks and Activities response as JSON", error)
        UpstreamErrorResponse("Unable to read Tasks and Activities response as JSON", BAD_GATEWAY, BAD_GATEWAY)
      }
      .flatMap { json =>
        json.validate[TasksAndActivitiesResponse].asEither.left.map { errors =>
          logger.error(s"Unable to parse Tasks and Activities response: ${JsError.toJson(errors)}")
          UpstreamErrorResponse("Unable to parse Tasks and Activities response", BAD_GATEWAY, BAD_GATEWAY)
        }
      }
      .map(_.cards.map(_.toTask(lang)))
}

private final case class TasksAndActivitiesRequest(userId: String, serviceList: List[String])

private object TasksAndActivitiesRequest:
  implicit val format: OFormat[TasksAndActivitiesRequest] = Json.format[TasksAndActivitiesRequest]

private final case class TasksAndActivitiesResponse(cards: Seq[TasksAndActivitiesCard])

private object TasksAndActivitiesResponse:
  implicit val format: OFormat[TasksAndActivitiesResponse] = Json.format[TasksAndActivitiesResponse]

private final case class TasksAndActivitiesCard(activityType: ActivityType, en: CardContent, cy: CardContent):
  def toTask(lang: Lang): Task =
    val content = if lang.code == "cy" then cy else en
    Task(
      title = content.header,
      status = TaskStatus.Incomplete,
      href = content.url,
      hintText = Option(content.body).filter(_.trim.nonEmpty),
      dateText = content.hint,
      activityType = activityType
    )

private object TasksAndActivitiesCard:
  import ActivityTypeJson.format

  implicit val format: OFormat[TasksAndActivitiesCard] = Json.format[TasksAndActivitiesCard]

private final case class CardContent(header: String, body: String, url: Option[String], hint: Option[String])

private object CardContent:
  implicit val format: OFormat[CardContent] = Json.format[CardContent]

private object ActivityTypeJson:
  implicit val format: Format[ActivityType] = Format(
    Reads {
      case JsString("task")  => play.api.libs.json.JsSuccess(viewmodels.ActivityType.Task)
      case JsString("event") => play.api.libs.json.JsSuccess(viewmodels.ActivityType.Event)
      case JsString(value)   => JsError(s"Invalid activity type: $value")
      case _                 => JsError("Expected activity type to be a string")
    },
    Writes {
      case ActivityType.Task  => JsString("task")
      case ActivityType.Event => JsString("event")
    }
  )
