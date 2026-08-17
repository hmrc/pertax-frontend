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
import play.api.libs.json.{JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Reads}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.{readEitherOf, readRaw}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import viewmodels.{Task, TaskStatus}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class TasksAndActivitiesConnector @Inject() (
  httpClientV2: HttpClientV2,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator
) extends Logging {

  def getTasks(nino: Nino)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, UpstreamErrorResponse, Seq[Task]] = {
    val url = configDecorator.tasksAndActivitiesTasksUrl(nino)

    EitherT(
      httpClientResponse
        .read(
          httpClientV2
            .get(url"$url")
            .transform(_.withRequestTimeout(configDecorator.tasksAndActivitiesTimeoutInMilliseconds.milliseconds))
            .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
        )
        .value
        .map {
          case Right(response) => parseTasks(response)
          case Left(error)     => Left(error)
        }
    )
  }

  private def parseTasks(response: HttpResponse): Either[UpstreamErrorResponse, Seq[Task]] =
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
      .map(_.tasks.map(_.toTask))
}

private final case class TasksAndActivitiesResponse(tasks: Seq[TasksAndActivitiesTask])

private object TasksAndActivitiesResponse {
  implicit val reads: Reads[TasksAndActivitiesResponse] = Reads {
    case json: JsObject => (json \ "tasks").validate[Seq[TasksAndActivitiesTask]].map(TasksAndActivitiesResponse(_))
    case json: JsArray  => json.validate[Seq[TasksAndActivitiesTask]].map(TasksAndActivitiesResponse(_))
    case _              => JsError("Expected Tasks and Activities response to be an object or array")
  }
}

private final case class TasksAndActivitiesTask(
  title: String,
  href: String,
  status: TaskStatus,
  hintText: Option[String]
) {
  def toTask: Task = Task(title, status, href, hintText)
}

private object TasksAndActivitiesTask {
  implicit val reads: Reads[TasksAndActivitiesTask] = Reads { json =>
    for {
      title    <- readString(json, "title")
      href     <- readString(json, "href").orElse(readString(json, "url"))
      status   <- readStatus(json)
      hintText <- (json \ "hintText").validateOpt[String]
    } yield TasksAndActivitiesTask(title, href, status, hintText)
  }

  private def readString(json: JsValue, fieldName: String): JsResult[String] =
    (json \ fieldName).validate[String]

  private def readStatus(json: JsValue): JsResult[TaskStatus] =
    (json \ "status").validateOpt[TaskStatus].map(_.getOrElse(TaskStatus.Incomplete))

  implicit val taskStatusReads: Reads[TaskStatus] = Reads {
    case JsString(value) =>
      value.trim.toLowerCase match {
        case "incomplete" | "open" | "todo" | "to-do" | "not-started" => JsSuccess(TaskStatus.Incomplete)
        case "completed" | "complete" | "done"                        => JsSuccess(TaskStatus.Completed)
        case _                                                        => JsError(s"Unsupported task status: $value")
      }
    case _               => JsError("Expected task status to be a string")
  }
}
