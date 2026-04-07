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
import controllers.auth.requests.UserRequest
import controllers.auth.AuthJourney
import controllers.interstitials.MtdAdvertInterstitialController
import models.MtdUserType.*
import models.*
import models.admin.{ClaimMtdFromPtaToggle, MTDUserStatusToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.mockito.stubbing.OngoingStubbing
import play.api.Application
import play.api.inject.bind
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.EnrolmentStoreProxyService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.sca.models.TrustedHelper
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import util.EnrolmentsHelper
import views.html.interstitial.MTDITAdvertPageView

import scala.concurrent.Future

class MtdAdvertInterstitialControllerSpec extends BaseSpec {

  val mockMtditAdvertPageView: MTDITAdvertPageView               = mock[MTDITAdvertPageView]
  val mockEnrolmentsHelper: EnrolmentsHelper                     = mock[EnrolmentsHelper]
  val mockEnrolmentStoreProxyService: EnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockMtditAdvertPageView)
    reset(mockEnrolmentsHelper)
    reset(mockEnrolmentStoreProxyService)

    when(mockMtditAdvertPageView.apply()(any(), any())).thenReturn(play.twirl.api.Html("MTD Advert Page"))
    when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(MTDUserStatusToggle)))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
    when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(ClaimMtdFromPtaToggle)))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(ClaimMtdFromPtaToggle, isEnabled = true)))
  }

  private def setupAuth(
    saUserType: Option[SelfAssessmentUserType] = None,
    enrolments: Set[Enrolment] = Set.empty,
    trustedHelper: Option[TrustedHelper] = None
  ): OngoingStubbing[ActionBuilder[UserRequest, AnyContent]] = {
    val actionBuilderFixture: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        saUserType match {
          case Some(sut) =>
            block(
              buildUserRequest(
                saUser = sut,
                request = request,
                enrolments = enrolments,
                trustedHelper = trustedHelper
              )
            )
          case None      =>
            block(
              buildUserRequest(
                request = request,
                enrolments = enrolments,
                trustedHelper = trustedHelper
              )
            )
        }

    }
    when(mockAuthJourney.authWithPersonalDetails).thenReturn(actionBuilderFixture)
  }

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[MTDITAdvertPageView].toInstance(mockMtditAdvertPageView),
      bind[EnrolmentsHelper].toInstance(mockEnrolmentsHelper),
      bind[EnrolmentStoreProxyService].toInstance(mockEnrolmentStoreProxyService)
    )
    .configure(configValues)
    .build()

  lazy val controller: MtdAdvertInterstitialController = app.injector.instanceOf[MtdAdvertInterstitialController]

  "displayMTDITPage" must {

    "return FORBIDDEN" when {
      "user is a Trusted Helper" in {
        setupAuth(trustedHelper = Some(TrustedHelper("principal", "attorney", "returnUrl", Some(generatedNino.nino))))

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe FORBIDDEN
      }
      "user already has an MTD enrolment" in {
        setupAuth()
        when(mockEnrolmentsHelper.mtdEnrolmentStatus(any()))
          .thenReturn(Some(ItsaEnrolmentEnrolled(Activated)))

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe FORBIDDEN
      }
    }

    "return OK with mtditAdvertPageView" when {
      "MTDUserStatusToggle is disabled" in {
        setupAuth()
        when(mockFeatureFlagService.getAsEitherT(ArgumentMatchers.eq(MTDUserStatusToggle)))
          .thenReturn(
            EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(MTDUserStatusToggle, isEnabled = false))
          )

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "MTD Advert Page"
      }
      List(UnknownMtdUser, NonFilerMtdUser, WrongCredentialsMtdUser("MTDITID123", "cred-456")).foreach { userType =>
        s"user is a ${userType.toString}" in {
          setupAuth()
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(MTDUserStatusToggle)))
            .thenReturn(Future.successful(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
          when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
            .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](userType))

          val result = controller.displayMTDITPage(FakeRequest())

          status(result) mustBe OK
          contentAsString(result) mustBe "MTD Advert Page"
        }
      }

      "user does NOT have an eligible SA state (NonFilerSelfAssessmentUser)" in {
        setupAuth(saUserType = Some(NonFilerSelfAssessmentUser))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(MTDUserStatusToggle)))
          .thenReturn(Future.successful(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
        when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](NotEnrolledMtdUser))

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "MTD Advert Page"
      }
      "ESP call fails" in {
        setupAuth()
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(MTDUserStatusToggle)))
          .thenReturn(Future.successful(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
        when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
          .thenReturn(EitherT.leftT[Future, MtdUser](UpstreamErrorResponse("Service Unavailable", 503)))

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "MTD Advert Page"
      }
    }
    "redirect to the ITSA merge page" must {
      "user is an EnrolledMtdUser" in {
        setupAuth()
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(MTDUserStatusToggle)))
          .thenReturn(Future.successful(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
        when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](EnrolledMtdUser("MTDITID123")))

        val result = controller.displayMTDITPage(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(
          controllers.interstitials.routes.InterstitialController.displayItsaMergePage.url
        )
      }
    }
    "redirect to Claim MTD journey" when
      List(
        ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1234567890")),
        WrongCredentialsSelfAssessmentUser(SaUtr("1234567890")),
        NotEnrolledSelfAssessmentUser(SaUtr("1234567890"))
      ).foreach { saUserType =>
        s"user is a ${saUserType.toString}" in {
          setupAuth(saUserType = Some(saUserType))
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(MTDUserStatusToggle)))
            .thenReturn(Future.successful(FeatureFlag(MTDUserStatusToggle, isEnabled = true)))
          when(mockFeatureFlagService.get(ArgumentMatchers.eq(ClaimMtdFromPtaToggle)))
            .thenReturn(Future.successful(FeatureFlag(ClaimMtdFromPtaToggle, isEnabled = true)))
          when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
            .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](NotEnrolledMtdUser))

          val result = controller.displayMTDITPage(FakeRequest())

          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(controllers.routes.ClaimMtdFromPtaController.start.url)
        }
      }
  }
}
