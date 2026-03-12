/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.*
import models.MtdUserType.*
import models.admin.MTDUserStatusToggle
import org.mockito.ArgumentMatchers.any as anyArg
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.sca.models.TrustedHelper

import scala.concurrent.Future

class OtherServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator                       = mock[ConfigDecorator]
  private val mockFandFService: FandFService                             = mock[FandFService]
  private val mockTaiService: TaiService                                 = mock[TaiService]
  private val mockEnrolmentStoreProxyService: EnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockFeatureFlagService: FeatureFlagService                 = mock[FeatureFlagService]
  private val mockEnrolmentsHelper: util.EnrolmentsHelper                = mock[util.EnrolmentsHelper]

  private lazy val service: OtherServices =
    new OtherServices(
      mockConfigDecorator,
      mockFandFService,
      mockTaiService,
      mockEnrolmentStoreProxyService,
      mockFeatureFlagService,
      mockEnrolmentsHelper
    )

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFandFService)
    reset(mockTaiService)
    reset(mockEnrolmentStoreProxyService)
    reset(mockFeatureFlagService)
    reset(mockEnrolmentsHelper)
  }

  private def buildRequest(
    saUserType: SelfAssessmentUserType,
    enrolments: Set[Enrolment] = Set.empty,
    trustedHelper: Option[TrustedHelper] = None
  ): UserRequest[AnyContent] =
    UserRequest(
      authNino = generatedNino,
      saUserType = saUserType,
      credentials = Credentials("credId", "GovernmentGateway"),
      confidenceLevel = ConfidenceLevel.L200,
      trustedHelper = trustedHelper,
      enrolments = enrolments,
      profile = None,
      breadcrumb = None,
      request = FakeRequest(),
      userAnswers = UserAnswers.empty
    )

  private val mtdItsaEnrolment: Enrolment =
    Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "11111")), "Activated")

  private def stubMtdToggle(enabled: Boolean): Unit =
    when(mockFeatureFlagService.getAsEitherT(eqTo(MTDUserStatusToggle)))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](FeatureFlag(MTDUserStatusToggle, isEnabled = enabled)))

  "getSelfAssessmentOtherServiceTile" must {

    "return request-access SA tile for NotEnrolledSelfAssessmentUser when trusted helper disabled" in {
      val result = service
        .getSelfAssessmentOtherServiceTile(NotEnrolledSelfAssessmentUser(SaUtr("11")), isTrustedHelperUser = false)
        .futureValue

      result.map(_.link) mustBe Some(controllers.routes.SelfAssessmentController.requestAccess.url)
    }

    "return activate SA tile for NotYetActivatedOnlineFilerSelfAssessmentUser when trusted helper disabled" in {
      when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")

      val result = service
        .getSelfAssessmentOtherServiceTile(
          NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
          isTrustedHelperUser = false
        )
        .futureValue

      result.map(_.link) mustBe Some("a/url")
    }

    "return None for ActivatedOnlineFilerSelfAssessmentUser when trusted helper disabled" in {
      val result = service
        .getSelfAssessmentOtherServiceTile(
          ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
          isTrustedHelperUser = false
        )
        .futureValue

      result mustBe None
    }

    "return None when trusted helper enabled" in {
      when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")

      val result = service
        .getSelfAssessmentOtherServiceTile(NotEnrolledSelfAssessmentUser(SaUtr("11")), isTrustedHelperUser = true)
        .futureValue

      result mustBe None
    }
  }

  "getMtdOtherServiceTile" must {

    "return None when trusted helper is enabled" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)
      val result                                = service.getMtdOtherServiceTile(isTrustedHelperUser = true).futureValue
      result mustBe None
    }

    "return None when user has MTD ITSA enrolment" in {
      implicit val req: UserRequest[AnyContent] =
        buildRequest(NonFilerSelfAssessmentUser, enrolments = Set(mtdItsaEnrolment))

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(Some("Activated"))

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result mustBe None
    }

    "return advert tile when toggle enabled and backend returns NonFilerMtdUser" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
      stubMtdToggle(enabled = true)

      doReturn(EitherT.rightT[Future, UpstreamErrorResponse](NonFilerMtdUser))
        .when(mockEnrolmentStoreProxyService)
        .getMtdUserType(anyArg[Nino])(anyArg[HeaderCarrier], anyArg[UserRequest[_]])

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result.map(_.link) mustBe Some(
        controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
      )
    }

    "return claim tile when toggle enabled and backend returns NotEnrolledMtdUser" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
      stubMtdToggle(enabled = true)
      doReturn(EitherT.rightT[Future, UpstreamErrorResponse](NotEnrolledMtdUser))
        .when(mockEnrolmentStoreProxyService)
        .getMtdUserType(anyArg[Nino])(anyArg[HeaderCarrier], anyArg[UserRequest[_]])

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result.map(_.link) mustBe Some(controllers.routes.ClaimMtdFromPtaController.start.url)
    }

    "return advert tile when toggle enabled and backend returns WrongCredentialsMtdUser" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
      stubMtdToggle(enabled = true)
      doReturn(EitherT.rightT[Future, UpstreamErrorResponse](WrongCredentialsMtdUser("x", "y")))
        .when(mockEnrolmentStoreProxyService)
        .getMtdUserType(anyArg[Nino])(anyArg[HeaderCarrier], anyArg[UserRequest[_]])
      doReturn(EitherT.rightT[Future, UpstreamErrorResponse](WrongCredentialsMtdUser("x", "y")))
        .when(mockEnrolmentStoreProxyService)
        .getMtdUserType(anyArg[Nino])(anyArg[HeaderCarrier], anyArg[UserRequest[_]])

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result.map(_.link) mustBe Some(
        controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
      )
    }

    "return None when toggle enabled but backend fails" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
      stubMtdToggle(enabled = true)

      doReturn(EitherT.leftT[Future, MtdUser](UpstreamErrorResponse("boom", 500)))
        .when(mockEnrolmentStoreProxyService)
        .getMtdUserType(anyArg[Nino])(anyArg[HeaderCarrier], anyArg[UserRequest[_]])

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result mustBe None
    }

    "return advert tile when toggle disabled and does not call enrolment-store-proxy" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(None)
      stubMtdToggle(enabled = false)

      val result = service.getMtdOtherServiceTile(isTrustedHelperUser = false).futureValue
      result.map(_.link) mustBe Some(
        controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
      )

      verifyNoInteractions(mockEnrolmentStoreProxyService)
    }
  }

  "getMarriageAllowanceOtherServiceTile" must {
    "return None when trusted helper is active and does not call TAI" in {
      implicit val req: UserRequest[AnyContent] = buildRequest(NonFilerSelfAssessmentUser)

      val result =
        service.getMarriageAllowanceOtherServiceTile(generatedNino, isTrustedHelperUser = true).futureValue

      result mustBe None
      verify(mockTaiService, times(0)).getTaxComponentsList(any(), any())(any(), any())
    }
  }

  "getOtherServices" must {
    "return expected list for Activated SA and MTD enrolment present" in {
      when(mockConfigDecorator.annualTaxSaSummariesTileLinkShow).thenReturn("ats/")
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("trustedHelper/")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))

      implicit val req: UserRequest[AnyContent] =
        buildRequest(
          ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
          enrolments = Set(mtdItsaEnrolment)
        )

      when(mockEnrolmentsHelper.mtdEnrolmentStatus(any())).thenReturn(Some("Activated"))

      val result = service.getOtherServices.futureValue

      result.exists(_.gaLabel.contains("Self Assessment")) mustBe false
      result.exists(_.gaLabel.contains("Making Tax Digital for Income Tax")) mustBe false

      result.map(_.link) must contain(
        controllers.interstitials.routes.InterstitialController.displayChildBenefitsSingleAccountView.url
      )
      result.map(_.link) must contain("ats/")
      result.map(_.link) must contain("trustedHelper/")
    }
  }
}
