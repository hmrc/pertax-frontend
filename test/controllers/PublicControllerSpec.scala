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

package controllers

import com.kenshoo.play.metrics.{DisabledMetrics, DisabledMetricsFilter, Metrics, MetricsFilter}
import com.typesafe.config.Config
import config.{ConfigDecorator, LocalTemplateRenderer}
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import org.mockito.Matchers.{eq => meq}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Session
import play.api.test.Helpers._
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.filters.{CookieCryptoFilter, SessionCookieCryptoFilter}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures._
import util.{BaseSpec, LocalPartialRetriever}

class PublicControllerSpec extends BaseSpec  {

  val sessionCookieCryptoFilter = new SessionCookieCryptoFilter(injected[ApplicationCrypto])


  override lazy val app = new GuiceApplicationBuilder()
    .overrides(bind[MetricsFilter].to[DisabledMetricsFilter].eagerly)
    .overrides(bind[Metrics].to[DisabledMetrics].eagerly)
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
    .overrides(bind(classOf[CookieCryptoFilter]).toInstance(MockitoSugar.mock[SessionCookieCryptoFilter]))
    .overrides(bind[Config].toInstance(MockitoSugar.mock[Config]))
    .build()

  trait LocalSetup {
    lazy val controller = {
      val c = injected[PublicController]
      when(c.configDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback-survey?origin=PERTAX"
      when(c.configDecorator.tcsServiceRouterUrl) thenReturn "/tax-credits-service/renewals/service-router"
      when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(c.configDecorator.defaultOrigin) thenReturn Origin("PERTAX")
      when(c.configDecorator.analyticsToken) thenReturn Some("N/A")
      c
    }
  }

  "Calling PublicController.verifyEntryPoint" should {

    "return 200" in new LocalSetup {

      val r = controller.verifyEntryPoint(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account")
      session(r) shouldBe Session(Map("ap" -> "IDA"))
    }
  }

  "Calling PublicController.governmentGatewayEntryPoint" should {

    "return 200" in new LocalSetup {

      val r = controller.governmentGatewayEntryPoint(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account")
      session(r) shouldBe Session(Map("ap" -> "GGW"))
    }
  }

  "Calling PublicController.sessionTimeout" should {

    "return 200" in new LocalSetup {

      val r = controller.sessionTimeout(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe OK
    }
  }

  "Calling PublicController.redirectToExitSurvey" should {

    "return 303" in new LocalSetup {

      val r = controller.redirectToExitSurvey(Origin("PERTAX"))(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/feedback-survey?origin=PERTAX")
    }
  }

  "Calling PublicController.redirectToTaxCreditsService" should {

    "redirect to tax-credits-service/renewals/service-router" in new LocalSetup {

      val r = controller.redirectToTaxCreditsService()(buildFakeRequestWithAuth("GET"))
      status(r) shouldBe MOVED_PERMANENTLY
      redirectLocation(r) shouldBe Some("/tax-credits-service/renewals/service-router")
    }
  }

  "Calling PublicController.redirectToPersonalDetails" should {

    "redirect to /personal-details page" in new LocalSetup {
      val r = controller.redirectToPersonalDetails()(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(r) shouldBe Some("/personal-account/personal-details")
    }
  }

}
