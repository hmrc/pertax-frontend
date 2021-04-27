/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{JsDefined, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.domain.SaUtr

sealed trait SelfAssessmentUserType

sealed trait SelfAssessmentUser extends SelfAssessmentUserType {
  def saUtr: SaUtr
}

object SelfAssessmentUserType {
  val cacheId = "SelfAssessmentUser"

  val activatedSa = ActivatedOnlineFilerSelfAssessmentUser.toString
  val notActivatedSa = NotYetActivatedOnlineFilerSelfAssessmentUser.toString
  val wrongCredsSa = WrongCredentialsSelfAssessmentUser.toString
  val notEnrolledSa = NotEnrolledSelfAssessmentUser.toString
  val nonFilerSa = NonFilerSelfAssessmentUser.toString

  implicit val writes = new Writes[SelfAssessmentUserType] {
    override def writes(o: SelfAssessmentUserType): JsValue = o match {
      case ActivatedOnlineFilerSelfAssessmentUser(utr) =>
        Json.obj("_type" -> JsString(activatedSa), "utr" -> JsString(utr.toString))
      case NotYetActivatedOnlineFilerSelfAssessmentUser(utr) =>
        Json.obj("_type" -> JsString(notActivatedSa), "utr" -> JsString(utr.toString))
      case WrongCredentialsSelfAssessmentUser(utr) =>
        Json.obj("_type" -> JsString(wrongCredsSa), "utr" -> JsString(utr.toString))
      case NotEnrolledSelfAssessmentUser(utr) =>
        Json.obj("_type" -> JsString(notEnrolledSa), "utr" -> JsString(utr.toString))
      case NonFilerSelfAssessmentUser =>
        Json.obj("_type" -> JsString(nonFilerSa))
    }
  }

  implicit val reads = new Reads[SelfAssessmentUserType] {
    override def reads(json: JsValue): JsResult[SelfAssessmentUserType] =
      (json \ "_type", json \ "utr") match {

        case (JsDefined(JsString(`activatedSa`)), JsDefined(JsString(utr))) =>
          JsSuccess(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)))
        case (JsDefined(JsString(`notActivatedSa`)), JsDefined(JsString(utr))) =>
          JsSuccess(NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr(utr)))
        case (JsDefined(JsString(`wrongCredsSa`)), JsDefined(JsString(utr))) =>
          JsSuccess(WrongCredentialsSelfAssessmentUser(SaUtr(utr)))
        case (JsDefined(JsString(`notEnrolledSa`)), JsDefined(JsString(utr))) =>
          JsSuccess(NotEnrolledSelfAssessmentUser(SaUtr(utr)))
        case (JsDefined(JsString(`nonFilerSa`)), _) =>
          JsSuccess(NonFilerSelfAssessmentUser)
        case _ => JsError("Could not read SelfAssessmentUserType")
      }
  }
}

case class ActivatedOnlineFilerSelfAssessmentUser(saUtr: SaUtr) extends SelfAssessmentUser
case class NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr: SaUtr) extends SelfAssessmentUser
case class WrongCredentialsSelfAssessmentUser(saUtr: SaUtr) extends SelfAssessmentUser
case class NotEnrolledSelfAssessmentUser(saUtr: SaUtr) extends SelfAssessmentUser
case object NonFilerSelfAssessmentUser extends SelfAssessmentUserType
