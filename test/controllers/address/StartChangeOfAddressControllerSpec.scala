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

package controllers.address

import cats.data.EitherT
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.{NonFilerSelfAssessmentUser, PersonDetails}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CitizenDetailsService
import testUtils.BaseSpec
import testUtils.Fixtures.buildPersonDetailsWithPersonalAndCorrespondenceAddress
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.{ExecutionContext, Future}

class StartChangeOfAddressControllerSpec extends BaseSpec {
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val personDetails: PersonDetails                     = buildPersonDetailsWithPersonalAndCorrespondenceAddress

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = request))

        override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      }
  }

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(new FakeAuthAction),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
  }

  private lazy val controller: StartChangeOfAddressController = app.injector.instanceOf[StartChangeOfAddressController]

  def currentRequest[A]: Request[A] = FakeRequest("GET", "/test").asInstanceOf[Request[A]]

  "onPageLoad" must {
    "return 200 and correct content when passed ResidentialAddrType" in {
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      doc.getElementsByTag("h1").toString.contains("Change your main address") mustBe true
    }

    "return 200 and correct content when passed PostalAddrType" in {
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)
      status(result) mustBe OK
      val doc: Document          = Jsoup.parse(contentAsString(result))
      doc.getElementsByTag("h1").toString.contains("Change your postal address") mustBe true
    }

  }
}
