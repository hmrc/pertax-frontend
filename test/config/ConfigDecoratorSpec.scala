/*
 * Copyright 2017 HM Revenue & Customs
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

import java.net.MalformedURLException

import org.mockito.Matchers.{eq => meq}
import play.api.Configuration
import play.api.i18n.Langs
import util.BaseSpec


class ConfigDecoratorSpec extends BaseSpec {

  trait LocalSetup {

    def portalBaseUrlToTest: Option[String]

    lazy val configDecorator = new ConfigDecorator(injected[Configuration], injected[Langs]) {
      override lazy val portalBaseUrl = portalBaseUrlToTest.getOrElse("")
    }
  }

  "Calling toPortalUrl" should {

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
