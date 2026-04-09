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
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{reset, when}
import org.scalatest.OptionValues
import play.api.Application
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.MimeTypes.FORM
import play.api.inject.bind
import play.api.mvc.*
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest}
import play.twirl.api.HtmlFormat
import services.EnrolmentStoreProxyService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.interstitial.MTDITClaimChoiceView
import views.html.iv.failure.TechnicalIssuesView

import java.util.UUID
import scala.concurrent.Future

class ClaimMtdFromPtaControllerSpec extends BaseSpec with OptionValues {

  private val mockEnrolmentStoreProxyService: EnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockView: MTDITClaimChoiceView                             = mock[MTDITClaimChoiceView]
  private val mockConfigDecorator: ConfigDecorator                       = mock[ConfigDecorator]
  private val mockTechnicalIssuesView: TechnicalIssuesView               = mock[TechnicalIssuesView]

  private val nino        = Nino("AA123456A")
  private val credentials = Credentials("providerId-123", "GovernmentGateway")
  private val handoffUrl  = "http://example.com/handoff"

  private val activatedSaUser: SelfAssessmentUserType =
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1234567890"))

  private val notEnrolledSaUser: SelfAssessmentUserType =
    NotEnrolledSelfAssessmentUser(SaUtr("1234567890"))

  private val wrongCredentialSaUser: SelfAssessmentUserType =
    WrongCredentialsSelfAssessmentUser(SaUtr("1234567890"))

  private val nonFilerSaUser: SelfAssessmentUserType =
    NonFilerSelfAssessmentUser

  private val startUrl  = "/mtd/claim-from-pta/start"
  private val submitUrl = "/mtd/claim-from-pta/submit"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockFeatureFlagService,
      mockEnrolmentStoreProxyService,
      mockView,
      mockConfigDecorator,
      mockTechnicalIssuesView
    )

    when(mockConfigDecorator.mtdClaimFromPtaHandoffUrl).thenReturn(handoffUrl)
    when(mockView.apply(any(), any())(any(), any())).thenReturn(HtmlFormat.empty)
    when(mockTechnicalIssuesView.apply(anyString())(any(), any(), any()))
      .thenReturn(HtmlFormat.empty)

    stubMtdStatusToggle(enabled = true)
    stubClaimToggle(enabled = true)
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
    ).overrides(
      bind[AuthJourney].toInstance(authJourneyWith(saUserType)),
      bind[EnrolmentStoreProxyService].toInstance(mockEnrolmentStoreProxyService),
      bind[MTDITClaimChoiceView].toInstance(mockView),
      bind[ConfigDecorator].toInstance(mockConfigDecorator),
      bind[TechnicalIssuesView].toInstance(mockTechnicalIssuesView)
    ).build()

  private def controllerFor(saUserType: SelfAssessmentUserType): ClaimMtdFromPtaController =
    appWith(saUserType).injector.instanceOf[ClaimMtdFromPtaController]

  private def startRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, startUrl)

  private def submitRequest(formValue: Option[String] = None): FakeRequest[AnyContent] =
    formValue match {
      case Some(v) =>
        FakeRequest[AnyContent](
          method = POST,
          uri = submitUrl,
          headers = FakeHeaders(Seq(CONTENT_TYPE -> FORM)),
          body = AnyContentAsFormUrlEncoded(Map("mtd-choice" -> Seq(v)))
        )

      case None =>
        FakeRequest[AnyContent](
          method = POST,
          uri = submitUrl,
          headers = FakeHeaders(),
          body = AnyContentAsFormUrlEncoded(Map("mtd-choice" -> Seq.empty))
        )
    }

  private def stubMtdStatusToggle(enabled: Boolean): Unit =
    when(mockFeatureFlagService.getAsEitherT(MTDUserStatusToggle))
      .thenReturn(EitherT(Future.successful(Right(FeatureFlag(MTDUserStatusToggle, isEnabled = enabled)))))

  private def stubClaimToggle(enabled: Boolean): Unit =
    when(mockFeatureFlagService.getAsEitherT(ClaimMtdFromPtaToggle))
      .thenReturn(EitherT(Future.successful(Right(FeatureFlag(ClaimMtdFromPtaToggle, isEnabled = enabled)))))

  private def stubMtdUserType(userType: MtdUser): Unit =
    when(mockEnrolmentStoreProxyService.getMtdUserType(any())(any(), any()))
      .thenReturn(EitherT(Future.successful(Right(userType))))

  "Calling ClaimMtdFromPtaController.start" must {

    "return NotFound when user is a non filer" in {
      val controller = controllerFor(nonFilerSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe NOT_FOUND
    }

    "redirect to advert when toggles are disabled" in {
      stubMtdStatusToggle(enabled = false) // fails ensureClaimMtdJourneyEnabled
      stubClaimToggle(enabled = false)
      stubMtdUserType(NotEnrolledMtdUser)

      val controller = controllerFor(activatedSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(
        result
      ) mustBe Some(controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url)
    }

    "return OK when enrolment-store returns NotEnrolledMtdUser for a ActivatedSaUser" in {
      stubMtdUserType(NotEnrolledMtdUser)

      val controller = controllerFor(activatedSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe OK
    }

    "return OK when enrolment-store returns NotEnrolledMtdUser for a NotEnrolledSaUser" in {
      stubMtdUserType(NotEnrolledMtdUser)

      val controller = controllerFor(notEnrolledSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe OK
    }

    "return OK when enrolment-store returns NotEnrolledMtdUser for a WrongCredSaUser" in {
      stubMtdUserType(NotEnrolledMtdUser)

      val controller = controllerFor(wrongCredentialSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe OK
    }

    "redirect to MTDIT advert page when enrolment-store returns any other MTD user type" in {
      stubMtdUserType(NonFilerMtdUser)

      val controller = controllerFor(activatedSaUser)

      val result = controller.start()(startRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe
        controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url
    }
  }

  "Calling ClaimMtdFromPtaController.submit" must {

    "redirect to handoff url when yes is selected" in {
      val controller = controllerFor(activatedSaUser)

      val result = controller.submit()(submitRequest(Some("true")))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe handoffUrl
    }

    "redirect to PTA home when no is selected" in {
      val controller = controllerFor(activatedSaUser)

      val result = controller.submit()(submitRequest(Some("false")))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.HomeController.index.url
    }

    "return BAD_REQUEST when nothing is posted" in {
      val controller = controllerFor(activatedSaUser)

      val result = controller.submit()(submitRequest())
      status(result) mustBe BAD_REQUEST
    }

    "return BAD_REQUEST when unexpected value is posted" in {
      val controller = controllerFor(activatedSaUser)

      val result = controller.submit()(submitRequest(Some("unexpected")))
      status(result) mustBe BAD_REQUEST
    }
  }
}
