/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}

object UserRequestFixture {

  def buildUserRequest[A](
    nino: Option[Nino] = Some(Fixtures.fakeNino),
    userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
    credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
    personDetails: Option[PersonDetails] = Some(Fixtures.buildPersonDetails),
    trustedHelper: Option[TrustedHelper] = None,
    profile: Option[String] = None,
    messageCount: Option[Int] = None,
    request: Request[A]): UserRequest[A] =
    UserRequest(
      nino,
      userName,
      saUser,
      credentials,
      confidenceLevel,
      personDetails,
      trustedHelper,
      profile,
      messageCount,
      None,
      None,
      request)

}
