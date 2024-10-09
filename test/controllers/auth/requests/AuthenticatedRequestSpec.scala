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

package controllers.auth.requests

import models._
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.AffinityGroup.Individual
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Nino, SaUtrGenerator}

class AuthenticatedRequestSpec extends BaseSpec {

  val utr: String                          = new SaUtrGenerator().nextSaUtr.utr
  val fakeCredentials: Credentials         = Credentials("foo", "bar")
  val fakeConfidenceLevel: ConfidenceLevel = ConfidenceLevel.L200
  val saEnrolments: Set[Enrolment]         = Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", utr)), "Activated"))
  val userName: Option[UserName]           = Some(UserName(Name(Some("Firstname"), Some("Lastname"))))

  val nino: Nino = uk.gov.hmrc.domain.Nino("AA123456A")

  def authenticatedRequest(): AuthenticatedRequest[AnyContent] = AuthenticatedRequest[AnyContent](
    authNino = nino,
    nino = Some(nino),
    credentials = fakeCredentials,
    confidenceLevel = fakeConfidenceLevel,
    name = userName,
    trustedHelper = None,
    profile = None,
    saEnrolments,
    FakeRequest(),
    affinityGroup = Some(Individual),
    UserAnswers.empty
  )

  "AuthenticatedRequest" when {
    "the Nino is correct" in {

      val ninoResult = authenticatedRequest().nino

      ninoResult.isDefined shouldBe true
      ninoResult.get       shouldBe Nino("AA123456A")
    }

  }
}
