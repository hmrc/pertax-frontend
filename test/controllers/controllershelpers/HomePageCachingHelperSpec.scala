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

package controllers.controllershelpers

import controllers.auth.requests.UserRequest
import models.{NonFilerSelfAssessmentUser, UserAnswers, UserName}
import org.mockito.ArgumentMatchers.any
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import routePages.HasUrBannerDismissedPage
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class HomePageCachingHelperSpec extends BaseSpec {

  private val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]

  val cachingHelper: HomePageCachingHelper = new HomePageCachingHelper(mockJourneyCacheRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
  }

  "Calling HomePageCachingHelper.hasUserDismissedBanner" must {

    "return true if cached value returns true" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasUrBannerDismissedPage, true)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Boolean = cachingHelper.hasUserDismissedBanner.futureValue

      result mustBe true
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return false if cached value returns false" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasUrBannerDismissedPage, false)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Boolean = cachingHelper.hasUserDismissedBanner.futureValue

      result mustBe false
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return false if cache returns no record for HasUrBannerDismissedPage" in {
      val userAnswers: UserAnswers = UserAnswers.empty("id")
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Boolean = cachingHelper.hasUserDismissedBanner.futureValue

      result mustBe false
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }
  }

  "Calling HomePageCachingHelper.StoreUserUrDismissal" must {

    "store the dismissal flag and return the updated UserAnswers" in {
      val initialUserAnswers: UserAnswers = UserAnswers.empty("id")
      val updatedUserAnswers: UserAnswers = initialUserAnswers.setOrException(HasUrBannerDismissedPage, true)

      implicit val userRequest: UserRequest[_] =
        UserRequest(
          Fixtures.fakeNino,
          Some(Fixtures.fakeNino),
          Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
          NonFilerSelfAssessmentUser,
          Credentials("", "GovernmentGateway"),
          ConfidenceLevel.L200,
          None,
          None,
          Set(),
          None,
          None,
          FakeRequest(),
          initialUserAnswers
        )

      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val result: UserAnswers = cachingHelper.storeUserUrDismissal().futureValue

      result mustBe updatedUserAnswers
      verify(mockJourneyCacheRepository, times(1)).set(updatedUserAnswers)
    }
  }
}
