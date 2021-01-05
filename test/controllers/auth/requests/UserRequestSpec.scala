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

package controllers.auth.requests

import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, SelfAssessmentUser, WrongCredentialsSelfAssessmentUser}
import util.UserRequestFixture._
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.SaUtr

class UserRequestSpec extends PlaySpec {

  val saUtr: SaUtr = SaUtr("test utr")

  "isSa" must {
    val saUsers = Seq(
      ActivatedOnlineFilerSelfAssessmentUser(saUtr),
      NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr),
      WrongCredentialsSelfAssessmentUser(saUtr),
      NotEnrolledSelfAssessmentUser(saUtr)
    )

    saUsers.foreach { saType =>
      s"be true when a user is $saType" in {
        val userRequest = buildUserRequest(saUser = saType, request = FakeRequest())
        userRequest.isSa mustBe true
      }
    }

    "be false when a user is non-SA" in {
      val userRequest = buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = FakeRequest())
      userRequest.isSa mustBe false
    }
  }

  "isSaUserLoggedIntoCorrectAccount" must {
    val saUsersWithWrongCreds = Seq(
      NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr),
      WrongCredentialsSelfAssessmentUser(saUtr),
      NotEnrolledSelfAssessmentUser(saUtr)
    )

    saUsersWithWrongCreds.foreach { saType =>
      s"be false when a user is $saType" in {
        val userRequest = buildUserRequest(saUser = saType, request = FakeRequest())
        userRequest.isSaUserLoggedIntoCorrectAccount mustBe false
      }
    }

    "be false when a user is non SA" in {
      val userRequest = buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = FakeRequest())
      userRequest.isSaUserLoggedIntoCorrectAccount mustBe false
    }

    "be true when a user is logged in to the correct SA account" in {
      val userRequest =
        buildUserRequest(saUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr), request = FakeRequest())
      userRequest.isSaUserLoggedIntoCorrectAccount mustBe true
    }
  }
}
