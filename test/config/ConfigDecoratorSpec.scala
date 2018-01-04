/*
 * Copyright 2018 HM Revenue & Customs
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

package config

import java.net.{MalformedURLException, URL}

import org.mockito.Matchers.{eq => meq}
import play.api.Configuration
import play.api.i18n.Langs
import util.BaseSpec


class ConfigDecoratorSpec extends BaseSpec {



  "Converting urls to sso" should {

    trait LocalSetup {

      lazy val configDecorator = new ConfigDecorator(injected[Configuration], injected[Langs]) {
        override lazy val portalBaseUrl = "http://portal.service"
        override lazy val companyAuthFrontendHost = "http://company-auth-frontend.service"
      }
    }

    "return a properly encoded sso url when calling ssoifyUrl" in new LocalSetup {

      configDecorator.ssoifyUrl(new URL("http://example.com/some/path?key=val")) shouldBe
        "http://company-auth-frontend.service/ssoout/non-digital?continue=http%3A%2F%2Fexample.com%2Fsome%2Fpath%3Fkey%3Dval"
    }

    "return a properly formatted sa302 url when calling sa302Url" in new LocalSetup {

      configDecorator.sa302Url("1111111111", "1516") shouldBe
        "http://company-auth-frontend.service/ssoout/non-digital?continue=http%3A%2F%2Fportal.service%2Fself-assessment-file%2F1516%2Find%2F1111111111%2Freturn%2FviewYourCalculation%2FreviewYourFullCalculation"
    }

    "return a properly formatted sa302 url when calling ssoToSaAccountSummaryUrl" in new LocalSetup {

      configDecorator.ssoToSaAccountSummaryUrl("1111111111", "1516") shouldBe
        "http://company-auth-frontend.service/ssoout/non-digital?continue=http%3A%2F%2Fportal.service%2Fself-assessment%2Find%2F1111111111%2Ftaxreturn%2F1516%2Foptions"
    }
  }

  "Calling toPortalUrl" should {

    trait LocalSetup {

      def portalBaseUrlToTest: Option[String]

      lazy val configDecorator = new ConfigDecorator(injected[Configuration], injected[Langs]) {
        override lazy val portalBaseUrl = portalBaseUrlToTest.getOrElse("")
      }
    }

    "return a URL if portalBaseUrl is present and fully qualified" in new LocalSetup {

      val portalBaseUrlToTest = Some("http://portal.service")

      configDecorator.toPortalUrl("/some/path").toString shouldBe "http://portal.service/some/path"
    }

    "fail with a MalformedURLException if portalBaseUrl is not present" in new LocalSetup {

      val portalBaseUrlToTest = None

      a [MalformedURLException] should be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

    "fail with a MalformedURLException if portalBaseUrl is not fully qualified" in new LocalSetup {

      val portalBaseUrlToTest = Some("/")

      a [MalformedURLException] should be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

    "fail with a MalformedURLException if portalBaseUrl is protocol-relative" in new LocalSetup {

      val portalBaseUrlToTest = Some("//portal.service")

      a [MalformedURLException] should be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

  }
}
