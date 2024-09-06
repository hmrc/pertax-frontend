/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._

case class AdditonalFactors(factorType: String, phoneNumber: Option[String] = None, name: Option[String] = None) {
  val FIVE                       = 5
  def trimmedPhoneNumber: String = phoneNumber.fold("")(_.trim.takeRight(FIVE))
}

case class UsersGroupResponse(
  identityProviderType: IdentityProviderType,
  obfuscatedUserId: Option[String],
  email: Option[String],
  lastAccessedTimestamp: Option[String],
  additionalFactors: Option[List[AdditonalFactors]]
) {
  def isIdentityProviderSCP: Boolean      = identityProviderType == SCP
  def isIdentityProviderOneLogin: Boolean = identityProviderType == ONE_LOGIN
}

object AdditonalFactors {
  implicit val format: Format[AdditonalFactors] = Json.format[AdditonalFactors]
}

object UsersGroupResponse {
  implicit val reads: Reads[UsersGroupResponse] = (
    (JsPath \ "identityProviderType")
      .readNullable[JsValue]
      .map(_.fold(SCP: IdentityProviderType)(_.as[IdentityProviderType](IdentityProviderTypeFormat.reads))) and
      (JsPath \ "obfuscatedUserId").readNullable[String] and
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "lastAccessedTimestamp").readNullable[String] and
      (JsPath \ "additionalFactors").readNullable[List[AdditonalFactors]]
  )(UsersGroupResponse.apply _)

  implicit val writes: Writes[UsersGroupResponse] = new Writes[UsersGroupResponse] {
    override def writes(o: UsersGroupResponse): JsValue =
      Json.obj(
        "identityProviderType"  -> Json.toJson(o.identityProviderType)(IdentityProviderTypeFormat.writes),
        "obfuscatedUserId"      -> o.obfuscatedUserId,
        "email"                 -> o.email,
        "lastAccessedTimestamp" -> o.lastAccessedTimestamp,
        "additionalFactors"     -> o.additionalFactors
      )
  }
  implicit val format: Format[UsersGroupResponse] =
    Format(reads, writes)
}
