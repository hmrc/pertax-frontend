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

package models.enrolments

import play.api.libs.json.{JsResult, JsString, JsSuccess, JsValue, Reads, Writes}

trait IdentityProviderType

object IdentityProviderTypeFormat {
  final val reads: Reads[IdentityProviderType] = new Reads[IdentityProviderType] {
    override def reads(json: JsValue): JsResult[IdentityProviderType] =
      json.as[String] match {
        case s if s == SCP.toString       => JsSuccess(SCP)
        case s if s == ONE_LOGIN.toString => JsSuccess(ONE_LOGIN)
        case _                            => JsSuccess(SCP)
      }
  }

  val writes: Writes[IdentityProviderType] = new Writes[IdentityProviderType] {
    override def writes(o: IdentityProviderType): JsValue = JsString(o.toString)
  }
}

case object ONE_LOGIN extends IdentityProviderType {
  override def toString: String = "ONE_LOGIN"
}

case object SCP extends IdentityProviderType {
  override def toString: String = "SCP"
}
