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

package controllers.auth

import config.ConfigDecorator
import connectors.FrontEndDelegationConnector
import controllers.{PaperlessPreferencesController, PertaxBaseController, PertaxDependencies}
import models._
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsSuccessResponse, UserDetailsService}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.renderer.{ActiveTab, ActiveTabHome}
import util.Fixtures._
import util.{BaseSpec, Fixtures, MockPertaxDependencies}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisedActionsSpec extends BaseSpec {

  "Calling AuthorisedActions.createPertaxContextAndExecute" should {

    trait LocalSetup {

      def authProviderType: String
      def similateUserDetailsFailure: Boolean
      def userDetailsLink: Option[String]
      def confidenceLevel: ConfidenceLevel
      def messageCountResponse: Option[Int]

      lazy val authContext = AuthContext(
        buildFakeAuthority(
          withPaye = true,
          withSa = true,
          userDetailsLink = userDetailsLink,
          confidenceLevel = confidenceLevel
        ),
        None
      )

      lazy val localActions = new PertaxBaseController with AuthorisedActions {
        override val citizenDetailsService = MockitoSugar.mock[CitizenDetailsService]
        override val userDetailsService: UserDetailsService = MockitoSugar.mock[UserDetailsService]
        override val pertaxDependencies: PertaxDependencies = MockPertaxDependencies
        override val messagesApi = injected[MessagesApi]
        override val pertaxRegime = injected[PertaxRegime]
        override lazy val delegationConnector = MockitoSugar.mock[FrontEndDelegationConnector]
        override val messageFrontendService = MockitoSugar.mock[MessageFrontendService]


        when(citizenDetailsService.personDetails(meq(Fixtures.fakeNino))(any())) thenReturn {
          Future.successful(PersonDetailsSuccessResponse(Fixtures.buildPersonDetails))
        }


        when(messageFrontendService.getUnreadMessageCount(any())) thenReturn {
          Future.successful(messageCountResponse)
        }

        if(similateUserDetailsFailure) {
          when(userDetailsService.getUserDetails(any())(any())) thenReturn {
            Future.successful(None)
          }
        }
        else {
          when(userDetailsService.getUserDetails(any())(any())) thenReturn {
            Future.successful(Some(UserDetails(authProviderType)))
          }
        }

        when(configDecorator.ssoUrl) thenReturn Some("ssoUrl")
        when(configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
        when(configDecorator.analyticsToken) thenReturn Some("N/A")
      }



      //Extract the context and map the future requst to it
      lazy val t = {
        val req = FakeRequest("GET", "/test")
        var ctx: Option[PertaxContext] = None
        val r = localActions.createPertaxContextAndExecute(true) { c =>
          ctx = Some(c)
          Future.successful(Ok)
        }(authContext, req)
        r.map(_ => (ctx, r))
      }

      lazy val pertaxContext: Option[PertaxContext] = await(t.map(_._1))
      lazy val result: Future[Result] = await(t.map(_._2))
    }


    "set number of unread messages to 0 when unreadable json response returned by the message partial service" in new LocalSetup {
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val messageCountResponse = None

      status(result) shouldBe OK

      pertaxContext.get.unreadMessageCount shouldBe None
    }

    "set number of unread messages to 0 when json with count set to 0 is returned by the message partial service" in new LocalSetup {
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val messageCountResponse = Some(0)

      status(result) shouldBe OK

      pertaxContext.get.unreadMessageCount shouldBe Some(0)
    }

    "set number of unread messages to 2 when json with count set to 2 is returned by the message partial service" in new LocalSetup {
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val messageCountResponse = Some(2)

      status(result) shouldBe OK

      pertaxContext.get.unreadMessageCount shouldBe Some(2)
    }

    "set number of unread messages to 99+ when json with count set to 100 is returned by the message partial service" in new LocalSetup {
      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val messageCountResponse = Some(100)

      status(result) shouldBe OK

      pertaxContext.get.unreadMessageCount shouldBe Some(100)
    }

    "Create a non-gg user when the the auth-provider is not GG" in new LocalSetup {

      lazy val authProviderType = UserDetails.VerifyAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L0
      lazy val messageCountResponse = None

      status(result) shouldBe OK
      pertaxContext.get.user.get.isGovernmentGateway shouldBe false
      pertaxContext.get.user.get.isHighGovernmentGateway shouldBe false
    }

    "Create a low gg user when the the auth-provider is GG and CL is 0" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L0
      lazy val messageCountResponse = None

      status(result) shouldBe OK
      pertaxContext.get.user.get.isGovernmentGateway shouldBe true
      pertaxContext.get.user.get.isHighGovernmentGateway shouldBe false
    }

    "Create a low gg user when the the auth-provider is GG and CL is 50" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L50
      lazy val messageCountResponse = None

      status(result) shouldBe OK
      pertaxContext.get.user.get.isGovernmentGateway shouldBe true
      pertaxContext.get.user.get.isHighGovernmentGateway shouldBe false
    }

    "Create a low gg user when the the auth-provider is GG and CL is 100" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L100
      lazy val messageCountResponse = None

      status(result) shouldBe OK
      pertaxContext.get.user.get.isGovernmentGateway shouldBe true
      pertaxContext.get.user.get.isHighGovernmentGateway shouldBe false
    }

    "Create a high gg user when the the auth-provider is GG and CL is 200" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L200
      lazy val messageCountResponse = None

      status(result) shouldBe OK
      pertaxContext.get.user.get.isGovernmentGateway shouldBe true
      pertaxContext.get.user.get.isHighGovernmentGateway shouldBe true
    }

    "Not create a context and still return 500 when user-details link is missing" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = false
      lazy val userDetailsLink = None
      lazy val confidenceLevel = ConfidenceLevel.L0
      lazy val messageCountResponse = None

      status(result) shouldBe INTERNAL_SERVER_ERROR
      await(pertaxContext) shouldBe None
    }

    "Not create a context and still return 500 when user-details service fails" in new LocalSetup {

      lazy val authProviderType = UserDetails.GovernmentGatewayAuthProvider
      lazy val similateUserDetailsFailure = true
      lazy val userDetailsLink = Some("/userDetailsLink")
      lazy val confidenceLevel = ConfidenceLevel.L0
      lazy val messageCountResponse = None

      status(result) shouldBe INTERNAL_SERVER_ERROR
      await(pertaxContext) shouldBe None
    }
  }

  "Calling AuthorisedActions.enforceMinimumUserProfile" should {

    trait LocalSetup {

      def ggUser: Boolean
      def highGg: Boolean
      def saUser: Boolean
      def allowSaPreview: Boolean

      lazy val context = PertaxContext(FakeRequest("GET", "/test"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(PertaxUser(buildFakeAuthContext(withSa = saUser),
        if(ggUser) UserDetails(UserDetails.GovernmentGatewayAuthProvider) else UserDetails(UserDetails.VerifyAuthProvider),
        None, highGg)))

      lazy val localActions = new PertaxBaseController with AuthorisedActions {
        override val pertaxDependencies: PertaxDependencies = MockPertaxDependencies
        override val citizenDetailsService = MockitoSugar.mock[CitizenDetailsService]
        override val delegationConnector = MockitoSugar.mock[FrontEndDelegationConnector]
        override val userDetailsService = MockitoSugar.mock[UserDetailsService]
        override val messagesApi = injected[MessagesApi]
        override val pertaxRegime = MockitoSugar.mock[PertaxRegime]
        override val messageFrontendService = MockitoSugar.mock[MessageFrontendService]

        when(configDecorator.allowSaPreview) thenReturn allowSaPreview
      }
    }

    "Execute the block for a Verify user" in new LocalSetup {
      override lazy val ggUser = false
      override lazy val highGg = false
      override lazy val saUser = false
      override lazy val allowSaPreview = false
      val r = localActions.enforceMinimumUserProfile(Ok)(context)
      status(r) shouldBe OK
    }

    "Execute the block for a High GG user" in new LocalSetup {
      override lazy val ggUser = true
      override lazy val highGg = true
      override lazy val saUser = false
      override lazy val allowSaPreview = false
      val r = localActions.enforceMinimumUserProfile(Ok)(context)
      status(r) shouldBe OK
    }

    "Redirect to IV for a Low GG user with an SA account" in new LocalSetup {
      override lazy val ggUser = true
      override lazy val highGg = false
      override lazy val saUser = true
      override lazy val allowSaPreview = false
      val r = localActions.enforceMinimumUserProfile(Ok)(context)
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/do-uplift?redirectUrl=%2Ftest")
    }

    "Execute the block for a Low GG user with an SA account if allowSaPreview is true" in new LocalSetup {
      override lazy val ggUser = true
      override lazy val highGg = false
      override lazy val saUser = true
      override lazy val allowSaPreview = true
      val r = localActions.enforceMinimumUserProfile(Ok)(context)
      status(r) shouldBe OK
    }

    "Redirect to IV for a Low GG user" in new LocalSetup {
      override lazy val ggUser = true
      override lazy val highGg = false
      override lazy val saUser = false
      override lazy val allowSaPreview = false
      val r = localActions.enforceMinimumUserProfile(Ok)(context)
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/do-uplift?redirectUrl=%2Ftest")
    }
  }

  "Calling AuthorisedActions.withActiveTab" should {

    trait LocalSetup {

      def activeTab: Option[ActiveTab]

      val basePertaxContext: PertaxContext = PertaxContext(FakeRequest("GET", "/test"), mockLocalPartialRetreiver, MockitoSugar.mock[ConfigDecorator])

      val authorisedActions: AuthorisedActions = injected[PaperlessPreferencesController] //Could use any controller that implements AuthorisedActions

      //Extract the context
      val pc: Future[Option[PertaxContext]] = {
        var ctx: Option[PertaxContext] = None
        val r = authorisedActions.withActiveTab(activeTab) { c =>
          ctx = Some(c)
          Future.successful(Ok)
        }(basePertaxContext)
        r.map(_ => ctx)
      }

      lazy val pertaxContextFromBlock: PertaxContext = await(pc).get
    }

    "run the block supplied with no activeTab set in the its PertaxContext when no activeTab is set" in new LocalSetup {

      override def activeTab = None

      pertaxContextFromBlock.activeTab shouldBe None
    }

    "run the block supplied with the correct activeTab set in the its PertaxContext when an activeTab is set" in new LocalSetup {

      override def activeTab = Some(ActiveTabHome)

      pertaxContextFromBlock.activeTab shouldBe Some(ActiveTabHome)
    }

  }
}
