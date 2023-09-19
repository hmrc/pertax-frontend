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

package controllers.admin

import config.ConfigDecorator
import controllers.auth.InternalAuthAction
import models.admin.{AddressTaxCreditsBrokerCallToggle, FeatureFlag, TaxcalcToggle}
import org.mockito.ArgumentMatchers.any
import play.api.Configuration
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.{JsBoolean, Json}
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, status}
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class FeatureFlagsAdminControllerSpec extends BaseSpec {
  implicit val cc: ControllerComponents = injected[ControllerComponents]
  val nino: Nino                        = Fixtures.fakeNino
  val expectedPermission: Permission    =
    Permission(
      resource = Resource(
        resourceType = ResourceType("ddcn-live-admin-frontend"),
        resourceLocation = ResourceLocation("*")
      ),
      action = IAAction("ADMIN")
    )

  lazy val mockStubBehaviour: StubBehaviour = mock[StubBehaviour]
  lazy val fakeInternalAuthAction           =
    new InternalAuthAction(
      new ConfigDecorator(injected[Configuration], injected[ServicesConfig]),
      BackendAuthComponentsStub(mockStubBehaviour)
    )

  val sut = new FeatureFlagsAdminController(fakeInternalAuthAction, mockFeatureFlagService, cc)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockStubBehaviour)
    when(mockStubBehaviour.stubAuth(Some(expectedPermission), Retrieval.username))
      .thenReturn(Future.successful(Retrieval.Username("Test")))
  }

  "GET /get" must {
    "returns a list of toggles" when {
      "all is well" in {
        when(mockFeatureFlagService.getAll).thenReturn(
          Future.successful(List(FeatureFlag(TaxcalcToggle, isEnabled = true)))
        )

        val result = sut.get(
          FakeRequest().withHeaders("Authorization" -> "Token some-token")
        )

        status(result) mustBe OK
        contentAsString(result) mustBe """[{"name":"taxcalc","isEnabled":true}]"""
      }
    }

    "returns an exception" when {
      "The user is not authorised" in {
        reset(mockStubBehaviour)
        when(mockStubBehaviour.stubAuth(Some(expectedPermission), Retrieval.username))
          .thenReturn(Future.failed(UpstreamErrorResponse("Unauthorized", Status.UNAUTHORIZED)))

        when(mockFeatureFlagService.getAll).thenReturn(
          Future.successful(List(FeatureFlag(TaxcalcToggle, isEnabled = true)))
        )

        val result = sut.get(
          FakeRequest().withHeaders("Authorization" -> "Token some-token")
        )

        whenReady(result.failed) { e =>
          e mustBe a[UpstreamErrorResponse]
        }
      }
    }
  }

  "PUT /put" must {
    "returns no content" when {
      "all is well" in {
        when(mockFeatureFlagService.set(any(), any())).thenReturn(Future.successful(true))

        val result = sut.put(TaxcalcToggle)(
          FakeRequest()
            .withHeaders("Authorization" -> "Token some-token")
            .withJsonBody(JsBoolean(true))
        )

        status(result) mustBe NO_CONTENT
        contentAsString(result) mustBe ""
      }
    }
  }

  "PUT /puAll" must {
    "returns no content" when {
      "all is well" in {
        when(mockFeatureFlagService.setAll(any())).thenReturn(Future.successful(()))

        val result = sut.putAll(
          FakeRequest()
            .withHeaders("Authorization" -> "Token some-token")
            .withJsonBody(
              Json.toJson(
                List(
                  FeatureFlag(TaxcalcToggle, isEnabled = true),
                  FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)
                )
              )
            )
        )

        status(result) mustBe NO_CONTENT
      }
    }

    "returns internal server error" when {
      "there is an error" in {
        when(mockFeatureFlagService.setAll(any())).thenReturn(Future.failed(new RuntimeException("Random exception")))

        val result = intercept[RuntimeException] {
          await(
            sut.putAll(
              FakeRequest()
                .withHeaders("Authorization" -> "Token some-token")
                .withJsonBody(
                  Json.toJson(
                    List(
                      FeatureFlag(TaxcalcToggle, isEnabled = true),
                      FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)
                    )
                  )
                )
            )
          )
        }
        result.getMessage mustBe "Random exception"
      }
    }
  }
}
