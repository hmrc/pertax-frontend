/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.Configuration
import testUtils.BaseSpec
import uk.gov.hmrc.domain.SaUtrGenerator
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{MalformedURLException, URL}

class ConfigDecoratorSpec extends BaseSpec {
  val saUtr: String = new SaUtrGenerator().nextSaUtr.utr

  "Converting urls to sso" must {
    "return a properly encoded sso url when calling transformUrlForSso" in {
      config.transformUrlForSso(new URL("http://example.com/some/path?key=val")) mustBe
        "http://localhost:9553/bas-gateway/ssoout/non-digital?continue=http%3A%2F%2Fexample.com%2Fsome%2Fpath%3Fkey%3Dval"
    }

    "return a properly formatted sa302 url when calling sa302Url" in {
      config.sa302Url(saUtr, "1516") mustBe
        s"/self-assessment-file/1516/ind/$saUtr/return/viewYourCalculation/reviewYourFullCalculation"
    }

    "return a properly formatted SA Account Summary Url url when calling ssoToSaAccountSummaryUrl" in {
      config.ssoToSaAccountSummaryUrl(saUtr, "1516") mustBe
        s"http://localhost:9553/bas-gateway/ssoout/non-digital?continue=http%3A%2F%2Flocalhost%3A9237%2Fself-assessment%2Find%2F$saUtr%2Ftaxreturn%2F1516%2Foptions"
    }
  }

  "Calling toPortalUrl" must {

    trait LocalSetup {

      def portalBaseUrlToTest: Option[String]

      lazy val configDecorator: ConfigDecorator =
        new ConfigDecorator(inject[Configuration], inject[ServicesConfig]) {
          override lazy val portalBaseUrl: String = portalBaseUrlToTest.getOrElse("")
        }
    }

    "return a URL if portalBaseUrl is present and fully qualified" in new LocalSetup {

      val portalBaseUrlToTest: Option[String] = Some("http://portal.service")

      configDecorator.toPortalUrl("/some/path").toString mustBe "http://portal.service/some/path"
    }

    "fail with a MalformedURLException if portalBaseUrl is not present" in new LocalSetup {

      val portalBaseUrlToTest: Option[Nothing] = None

      a[MalformedURLException] must be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

    "fail with a MalformedURLException if portalBaseUrl is not fully qualified" in new LocalSetup {

      val portalBaseUrlToTest: Option[String] = Some("/")

      a[MalformedURLException] must be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

    "fail with a MalformedURLException if portalBaseUrl is protocol-relative" in new LocalSetup {

      val portalBaseUrlToTest: Option[String] = Some("//portal.service")

      a[MalformedURLException] must be thrownBy {
        configDecorator.toPortalUrl("/some/path")
      }
    }

  }

  "featureBannerTcsServiceClosure" must {
    "return Enabled when the configuration is set to 'enabled'" in {
      val config = new ConfigDecorator(
        runModeConfiguration = Configuration("feature.bannerTcsServiceClosure" -> "enabled"),
        servicesConfig = inject[ServicesConfig]
      )

      config.featureBannerTcsServiceClosure mustBe BannerTcsServiceClosure.Enabled
    }

    "return Disabled when the configuration is set to 'disabled'" in {
      val config = new ConfigDecorator(
        runModeConfiguration = Configuration("feature.bannerTcsServiceClosure" -> "disabled"),
        servicesConfig = inject[ServicesConfig]
      )

      config.featureBannerTcsServiceClosure mustBe BannerTcsServiceClosure.Disabled
    }

    "return DontCheck when the configuration is set to 'dont-check'" in {
      val config = new ConfigDecorator(
        runModeConfiguration = Configuration("feature.bannerTcsServiceClosure" -> "dont-check"),
        servicesConfig = inject[ServicesConfig]
      )

      config.featureBannerTcsServiceClosure mustBe BannerTcsServiceClosure.DontCheck
    }

    "throw an exception for invalid configuration values" in {
      val config = new ConfigDecorator(
        runModeConfiguration = Configuration("feature.bannerTcsServiceClosure" -> "invalid"),
        servicesConfig = inject[ServicesConfig]
      )

      an[IllegalArgumentException] must be thrownBy config.featureBannerTcsServiceClosure
    }
  }
}
