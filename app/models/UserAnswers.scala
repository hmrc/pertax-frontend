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

package models

import play.api.libs.Files.logger
import play.api.libs.json._
import queries.{Gettable, Settable}
import routePages.QuestionPage
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import scala.util.{Failure, Success, Try}

final case class UserAnswers(
  id: String,
  data: JsObject = Json.obj(),
  lastUpdated: Instant = Instant.now
) {

  def get[A](page: QuestionPage[A])(implicit rds: Reads[A]): Option[A] =
    Reads.optionNoError(Reads.at(page.path)).reads(data).getOrElse(None)

  def set[A](page: Settable[A], value: A)(implicit writes: Writes[A]): Try[UserAnswers] =
    page.cleanupBeforeSettingValue(this).flatMap { ua =>
      val updatedData = ua.data.setObject(page.path, Json.toJson(value)) match {
        case JsSuccess(jsValue, _) =>
          Success(jsValue)
        case JsError(errors)       =>
          logger.error(s"Failed to set value at path ${page.path}: $errors. Value: $value")
          Failure(JsResultException(errors))
      }

      updatedData.flatMap { d =>
        val updatedAnswers = copy(data = d)
        page.cleanup(updatedAnswers)
      }
    }

  def setOrException[A](page: QuestionPage[A], value: A)(implicit writes: Writes[A]): UserAnswers =
    set(page, value) match {
      case Success(ua) => ua
      case Failure(ex) => throw ex
    }

  def remove[A](page: QuestionPage[A]): Try[UserAnswers] =
    data.removeObject(page.path) match {
      case JsSuccess(jsValue, _) =>
        Success(this copy (data = jsValue))
      case JsError(_)            =>
        throw new RuntimeException("Unable to remove page: " + page)
    }

  def isDefined(gettable: Gettable[_]): Boolean =
    Reads.optionNoError(Reads.at[JsValue](gettable.path)).reads(data) match {
      case JsSuccess(Some(_), _) => true
      case _                     =>
        Reads
          .optionNoError(Reads.at[JsValue](gettable.path))
          .reads(data)
          .map(_.isDefined)
          .getOrElse(false)
    }
}

object UserAnswers {
  def unapply(userAnswers: UserAnswers): Some[(String, JsObject, Instant)] = Some(
    (userAnswers.id, userAnswers.data, userAnswers.lastUpdated)
  )

  val empty: UserAnswers             = empty("")
  def empty(id: String): UserAnswers = new UserAnswers(id, Json.obj())

  val reads: Reads[UserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").read[String] and
        (__ \ "data").read[JsObject] and
        (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
    )(UserAnswers.apply _)
  }

  val writes: OWrites[UserAnswers] = {

    import play.api.libs.functional.syntax._

    (
      (__ \ "_id").write[String] and
        (__ \ "data").write[JsObject] and
        (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
    )(unlift(UserAnswers.unapply))
  }

  implicit val format: OFormat[UserAnswers] = OFormat(reads, writes)
}
