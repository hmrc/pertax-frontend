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

package controllers

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.*
import models.MtdUserType.*
import models.admin.{ClaimMtdFromPtaToggle, MTDUserStatusToggle}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.OptionValues
import play.api.Application
import play.api.inject.bind
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.twirl.api.HtmlFormat
import services.EnrolmentStoreProxyService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.interstitial.MTDITClaimChoiceView

import java.util.UUID
import scala.concurrent.Future

class ClaimMtdFromPtaControllerSpec extends BaseSpec with OptionValues {

  private val mockEnrolmentStoreProxyService: EnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockView: MTDITClaimChoiceView                             = mock[MTDITClaimChoiceView]
  private val mockConfigDecorator: ConfigDecorator                       = mock[ConfigDecorator]

  private val nino        = Nino("AA123456A")
  private val credentials = Credentials("providerId-123", "GovernmentGateway")
  private val handoffUrl  = "http://example.com/handoff"

  private val correctSaUser: SelfAssessmentUserType =
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1234567890"))

  private val wrongSaUser: SelfAssessmentUserType =
    NonFilerSelfAssessmentUser

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService, mockEnrolmentStoreProxyService, mockView, mockConfigDecorator)

    when(mockConfigDecorator.mtdClaimFromPtaHandoffUrl).thenReturn(handoffUrl)

    when(mockView.apply(any())(any(), any())).thenReturn(HtmlFormat.empty)

    when(mockFeatureFlagService.getAsEitherT(MTDUserStatusToggle))
      .thenReturn(EitherT(Future.successful(Right(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))))

    when(mockFeatureFlagService.getAsEitherT(ClaimMtdFromPtaToggle))
      .thenReturn(EitherT(Future.successful(Right(FeatureFlag(ClaimMtdFromPtaToggle, isEnabled = true)))))
  }

  private def authJourneyWith(saUserType: SelfAssessmentUserType): AuthJourney =
    new AuthJourney {
      override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
        new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                authNino = nino,
                saUser = saUserType,
                credentials = credentials,
                request = request
              )
            )
        }
    }

  private def appWith(saUserType: SelfAssessmentUserType): Application =
    localGuiceApplicationBuilder(
      extraConfigValues = Map(
        "mongodb.uri" -> s"mongodb://localhost:27017/pertax-frontend-${UUID.randomUUID()}"
      )
    )
      .overrides(
        bind[AuthJourney].toInstance(authJourneyWith(saUserType)),
        bind[EnrolmentStoreProxyService].toInstance(mockEnrolmentStoreProxyService),
        bind[MTDITClaimChoiceView].toInstance(mockView),
        bind[ConfigDecorator].toInstance(mockConfigDecorator)
      )
      .build()

  "Calling ClaimMtdFromPtaController.start" must {

    "return NotFound when user is not logged into correct SA account" in {
      val appLocal   = appWith(wrongSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result = controller.start()(FakeRequest(GET, "/mtd/claim-from-pta/start"))
      status(result) mustBe NOT_FOUND
    }

    "return NotFound when toggles are disabled" in {
      when(mockFeatureFlagService.getAsEitherT(MTDUserStatusToggle))
        .thenReturn(EitherT(Future.successful(Right(FeatureFlag(MTDUserStatusToggle, isEnabled = false)))))

      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result = controller.start()(FakeRequest(GET, "/mtd/claim-from-pta/start"))
      status(result) mustBe NOT_FOUND
    }

    "return OK when enrolment-store returns NotEnrolledMtdUser" in {
      when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
        .thenReturn(EitherT(Future.successful(Right(NotEnrolledMtdUser))))

      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result = controller.start()(FakeRequest(GET, "/mtd/claim-from-pta/start"))
      status(result) mustBe OK
    }

    "redirect to MTDIT advert page when enrolment-store returns any other MTD user type" in {
      when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
        .thenReturn(EitherT(Future.successful(Right(NonFilerMtdUser))))

      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result = controller.start()(FakeRequest(GET, "/mtd/claim-from-pta/start"))
      status(result) mustBe SEE_OTHER
      redirectLocation(
        result
      ).value mustBe controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
    }
  }

  "Calling ClaimMtdFromPtaController.submit" must {

    "redirect to handoff url when yes is selected" in {
      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result =
        controller.submit()(
          FakeRequest(POST, "/mtd/claim-from-pta/submit")
            .withFormUrlEncodedBody("mtd-choice" -> models.ClaimMtdFromPtaChoiceFormProvider.yes)
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe handoffUrl
    }

    "redirect to PTA home when no is selected" in {
      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result =
        controller.submit()(
          FakeRequest(POST, "/mtd/claim-from-pta/submit")
            .withFormUrlEncodedBody("mtd-choice" -> models.ClaimMtdFromPtaChoiceFormProvider.no)
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.HomeController.index.url
    }

    "return NotFound when nothing is posted" in {
      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result = controller.submit()(FakeRequest(POST, "/mtd/claim-from-pta/submit"))
      status(result) mustBe NOT_FOUND
    }

    "return NotFound when unexpected value is posted" in {
      val appLocal   = appWith(correctSaUser)
      val controller = appLocal.injector.instanceOf[ClaimMtdFromPtaController]

      val result =
        controller.submit()(
          FakeRequest(POST, "/mtd/claim-from-pta/submit")
            .withFormUrlEncodedBody("mtd-choice" -> "unexpected")
        )

      status(result) mustBe NOT_FOUND
    }
  }
}
