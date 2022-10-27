/*
 * Copyright 2022 HM Revenue & Customs
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

import controllers.auth.InternalAuthAction
import config.ConfigDecorator
import org.mockito.ArgumentMatchers.any
import play.api.http.Status
import play.api.http.Status._
import play.api.libs.json.{JsBoolean, Json}
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import services.admin.FeatureFlagService
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import models.admin.{AddressTaxCreditsBrokerCallToggle, FeatureFlag, TaxcalcToggle}
import org.mockito.Mockito.{reset, when}
import play.api.Configuration
import play.api.i18n.Langs
import uk.gov.hmrc.internalauth.client.{IAAction, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class FeatureFlagsAdminControllerSpec extends BaseSpec {
  implicit val cc        = injected[ControllerComponents]
  val nino               = Fixtures.fakeNino
  val expectedPermission =
    Permission(
      resource = Resource(
        resourceType = ResourceType("ddcn-live-admin-frontend"),
        resourceLocation = ResourceLocation("*")
      ),
      action = IAAction("ADMIN")
    )

  lazy val mockStubBehaviour      = mock[StubBehaviour]
  lazy val mockFeatureFlagService = mock[FeatureFlagService]
  lazy val mockAuditConnector     = mock[AuditConnector]
  lazy val fakeInternalAuthAction =
    new InternalAuthAction(
      new ConfigDecorator(injected[Configuration], injected[Langs], injected[ServicesConfig]),
      BackendAuthComponentsStub(mockStubBehaviour)
    )

  val sut = new FeatureFlagsAdminController(fakeInternalAuthAction, mockFeatureFlagService, cc, mockAuditConnector)(ec)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockStubBehaviour, mockFeatureFlagService, mockAuditConnector)
    when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
    when(mockStubBehaviour.stubAuth(Some(expectedPermission), Retrieval.username))
      .thenReturn(Future.successful(Retrieval.Username("Test")))
  }

  "GET /get" must {
    "returns a list of toggles" when {
      "all is well" in {
        when(mockFeatureFlagService.getAll).thenReturn(Future.successful(List(FeatureFlag(TaxcalcToggle, true))))

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

        when(mockFeatureFlagService.getAll).thenReturn(Future.successful(List(FeatureFlag(TaxcalcToggle, true))))

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
        when(mockFeatureFlagService.set(any(), any())).thenReturn(Future.successful(true))

        val result = sut.putAll(
          FakeRequest()
            .withHeaders("Authorization" -> "Token some-token")
            .withJsonBody(
              Json.toJson(List(FeatureFlag(TaxcalcToggle, true), FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))
            )
        )

        status(result) mustBe NO_CONTENT
      }
    }

    "returns internal server error" when {
      "there is an error" in {
        when(mockFeatureFlagService.set(any(), any())).thenReturn(Future.successful(false))

        val result = sut.putAll(
          FakeRequest()
            .withHeaders("Authorization" -> "Token some-token")
            .withJsonBody(
              Json.toJson(List(FeatureFlag(TaxcalcToggle, true), FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))
            )
        )

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
