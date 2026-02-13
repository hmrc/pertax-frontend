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

package models

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.domain.Nino

case class AttorneyRelationshipResponse(
  principal: AttorneyPerson,
  attorney: AttorneyPerson,
  serviceScopes: Set[ServiceScope]
)

object AttorneyRelationshipResponse {
  implicit val OFormat: OFormat[AttorneyRelationshipResponse] = Json.format[AttorneyRelationshipResponse]
}

case class ServiceScope(scope: String, status: String)

object ServiceScope {
  implicit val formats: Format[ServiceScope] = Json.format[ServiceScope]
}

case class AttorneyPerson(nino: Nino, firstName: Option[String] = None, lastName: Option[String] = None)

object AttorneyPerson {
  implicit val formats: Format[AttorneyPerson] = Json.format[AttorneyPerson]
}
