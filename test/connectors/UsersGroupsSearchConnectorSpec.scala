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

package connectors

import models.enrolments.{AdditionalFactors, SCP, UsersGroupResponse}
import play.api.Application
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class UsersGroupsSearchConnectorSpec extends ConnectorSpec with WireMockHelper {

  override lazy val app: Application =
    app(Map("microservice.services.users-groups-search.port" -> server.port()))

  lazy val connector: UsersGroupsSearchConnector =
    app.injector.instanceOf[UsersGroupsSearchConnector]

  val credId = "123456789"

  val usersGroupSearchResponse: UsersGroupResponse = UsersGroupResponse(
    identityProviderType = SCP,
    obfuscatedUserId = Some("********6037"),
    email = Some("email1@test.com"),
    lastAccessedTimestamp = Some("2022-01-16T14:40:05Z"),
    additionalFactors = Some(List(AdditionalFactors("sms", Some("07783924321"))))
  )

  val usersGroupSearchResponseSAEnrolment: UsersGroupResponse =
    usersGroupSearchResponse.copy(obfuscatedUserId = Some("********1243"))

  def additionalFactorsJson(additionalFactors: List[AdditionalFactors]): JsArray =
    additionalFactors.foldLeft[JsArray](Json.arr()) { (a, b) =>
      val jsObject = if (b.factorType == "totp") {
        Json.obj(
          ("factorType", JsString(b.factorType)),
          ("name", JsString(b.name.getOrElse("")))
        )
      } else {
        Json.obj(
          ("factorType", JsString(b.factorType)),
          ("phoneNumber", JsString(b.phoneNumber.getOrElse("")))
        )
      }
      a.append(jsObject)
    }

  def usergroupsResponseJson(
    usersGroupResponse: UsersGroupResponse = usersGroupSearchResponse
  ): JsObject = {
    val compulsaryJson = Json.obj(
      "identityProviderType"  -> usersGroupResponse.identityProviderType.toString,
      "obfuscatedUserId"      -> usersGroupResponse.obfuscatedUserId,
      "email"                 -> usersGroupResponse.email.get,
      "lastAccessedTimestamp" -> Some(usersGroupResponse.lastAccessedTimestamp)
    )
    usersGroupResponse.additionalFactors.fold(compulsaryJson) { additionFactors =>
      compulsaryJson ++ Json.obj(
        ("additionalFactors", additionalFactorsJson(additionFactors))
      )
    }
  }

  "getUserDetails" when {
    val PATH =
      s"/users-groups-search/users/$credId"
    s"no errors occur" must {
      "return the user details" in {
        stubGet(
          PATH,
          NON_AUTHORITATIVE_INFORMATION,
          Some(usergroupsResponseJson().toString())
        )
        val result = connector.getUserDetails(credId).value.futureValue
        result mustBe a[Right[_, _]]
        result.getOrElse("nonsense") mustBe Some(usersGroupSearchResponse)
      }
    }

    "a non error, non NON_AUTHORITATIVE_INFORMATION status is returned" should {
      "return None" in {
        stubGet(PATH, OK, Some(""))

        val result = connector.getUserDetails(credId).value.futureValue
        result mustBe a[Right[_, _]]
        result.getOrElse("nonsense") mustBe None
      }
    }

    "a BAD_REQUEST is returned" should {
      "return an UpstreamErrorResponse" in {
        stubGet(PATH, BAD_REQUEST, Some(""))

        val result = connector.getUserDetails(credId).value.futureValue
        result mustBe a[Left[UpstreamErrorResponse, _]]
      }
    }
  }
}
