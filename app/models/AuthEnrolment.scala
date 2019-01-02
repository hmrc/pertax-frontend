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

package models

import play.api.libs.json._
import uk.gov.hmrc.domain.SaUtr

case class AuthEnrolment(state: String, saUtr: SaUtr) {
  def isNotYetActivated = state == AuthEnrolment.NotYetActivated
}

object AuthEnrolment {

  val NotYetActivated = "NotYetActivated"

  implicit val reads: Reads[AuthEnrolment] = Reads[AuthEnrolment] { js =>

    val authEnrolment = for {
      irSaEnrolment <- js.as[JsArray].value.filter(e => (e \ "key").as[JsString] == JsString("IR-SA")).headOption.map(_.as[JsObject])
      saEnrolmentState = irSaEnrolment.value("state").as[JsString].value
      identifiers <- (irSaEnrolment \ "identifiers").asOpt[JsArray]
      utr <- identifiers.value.filter(e => (e \ "key").as[JsString] == JsString("UTR")).headOption.map(ident => (ident \ "value").as[JsString].value)
    } yield AuthEnrolment(saEnrolmentState, SaUtr(utr))

    authEnrolment.fold[JsResult[AuthEnrolment]](JsError("Couldn't extract an IR-SA enrolment record from AuthEnrolment JSON"))(JsSuccess(_))
  }
}
