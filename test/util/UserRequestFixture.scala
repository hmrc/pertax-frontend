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

package util

import controllers.auth.requests.UserRequest
import models._
import org.joda.time.DateTime
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.domain.{Nino, SaUtr}

object UserRequestFixture {

  def buildUserRequest(
    nino: Option[Nino] = Some(Fixtures.fakeNino),
    userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    lastLoginTime: Option[DateTime] = Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
    saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
    credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    personDetails: Option[PersonDetails] = Some(Fixtures.buildPersonDetails),
    messageCount: Option[Int] = None,
    request: Request[_] = FakeRequest()) =
    UserRequest(
      nino,
      userName,
      lastLoginTime,
      saUser,
      credentials,
      confidenceLevel,
      personDetails,
      None,
      messageCount,
      None,
      None,
      request)

}
